/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.inference

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.google.android.gms.tflite.java.TfLite
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.tensorflow.lite.InterpreterApi

/**
 * TranscriptionEngine backed by the Moonshine speech-to-text model running on the
 * Google Play Services TensorFlow Lite runtime (`play-services-tflite-java`).
 *
 * Moonshine's official TFLite port ships as two graphs:
 *   - `encoder.tflite`    — preprocessor + encoder (raw audio → acoustic features)
 *   - `decoder.tflite`    — autoregressive decoder (features + prev tokens → next token)
 *
 * Plus a SentencePiece tokenizer model (`tokenizer.spm`).
 *
 * Model assets are loaded from:
 *   - `assets/models/moonshine/encoder.tflite`
 *   - `assets/models/moonshine/decoder.tflite`
 *   - `assets/models/moonshine/tokenizer.spm`
 *
 * If any of these assets are missing, [initialize] logs a warning and leaves [isReady] as
 * false. Callers that invoke [transcribe] on an unready engine receive
 * [EngineNotReadyException].
 */
class MoonshineTfliteEngine(
	private val context: Context,
) : TranscriptionEngine {

	private val initMutex = Mutex()

	@Volatile private var ready: Boolean = false
	@Volatile private var encoder: InterpreterApi? = null
	@Volatile private var decoder: InterpreterApi? = null
	@Volatile private var encoderModelBuffer: MappedByteBuffer? = null
	@Volatile private var decoderModelBuffer: MappedByteBuffer? = null
	@Volatile private var tokenizerBytes: ByteArray? = null

	/** Reusable 30-second input buffer, allocated on first successful initialize(). */
	@Volatile private var paddedInputBuffer: ByteBuffer? = null

	override suspend fun initialize() {
		initMutex.withLock {
			if (ready) return

			try {
				awaitTask()
			} catch (t: Throwable) {
				Log.w(TAG, "TfLite.initialize() failed; engine remains not-ready", t)
				return
			}

			val assets = context.assets
			val encoderBuffer = tryLoadModel(assets, ENCODER_ASSET) ?: run {
				Log.w(TAG, "Encoder asset '$ENCODER_ASSET' not found; engine remains not-ready")
				return
			}
			val decoderBuffer = tryLoadModel(assets, DECODER_ASSET) ?: run {
				Log.w(TAG, "Decoder asset '$DECODER_ASSET' not found; engine remains not-ready")
				return
			}
			val tokenizer = tryReadTokenizer(assets) ?: run {
				Log.w(TAG, "Tokenizer asset '$TOKENIZER_ASSET' not found; engine remains not-ready")
				return
			}

			val options = InterpreterApi.Options()
				.setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)

			val enc: InterpreterApi
			val dec: InterpreterApi
			try {
				enc = InterpreterApi.create(encoderBuffer, options)
				dec = InterpreterApi.create(decoderBuffer, options)
			} catch (t: Throwable) {
				Log.w(TAG, "Failed to create TFLite interpreters; engine remains not-ready", t)
				return
			}

			encoderModelBuffer = encoderBuffer
			decoderModelBuffer = decoderBuffer
			tokenizerBytes = tokenizer
			encoder = enc
			decoder = dec
			paddedInputBuffer = ByteBuffer
				.allocateDirect(MAX_SAMPLES * Float.SIZE_BYTES)
				.order(ByteOrder.nativeOrder())
			ready = true
			Log.i(TAG, "Moonshine engine initialized (encoder/decoder/tokenizer loaded)")
		}
	}

	override suspend fun close() {
		initMutex.withLock {
			ready = false
			try {
				encoder?.close()
			} catch (t: Throwable) {
				Log.w(TAG, "Error closing encoder", t)
			}
			try {
				decoder?.close()
			} catch (t: Throwable) {
				Log.w(TAG, "Error closing decoder", t)
			}
			encoder = null
			decoder = null
			encoderModelBuffer = null
			decoderModelBuffer = null
			tokenizerBytes = null
			paddedInputBuffer = null
		}
	}

	override fun isReady(): Boolean = ready

	override suspend fun transcribe(samples: FloatArray, languageHint: String): TranscriptionResult {
		// 1. Argument validation first — a programming error (wrong-sized buffer) is worse than
		//    a not-ready error, and validating up-front lets tests exercise the contract without
		//    needing a real model loaded.
		require(samples.size >= MIN_SAMPLES) {
			"samples too short: ${samples.size} (minimum $MIN_SAMPLES at 16kHz = 0.1s)"
		}
		require(samples.size <= MAX_SAMPLES) {
			"samples too long: ${samples.size} (maximum $MAX_SAMPLES at 16kHz = 30s)"
		}

		// 2. Readiness gate.
		if (!ready) {
			throw EngineNotReadyException("Moonshine model not loaded")
		}

		val paddedBuffer = paddedInputBuffer
			?: throw EngineNotReadyException("Moonshine model not loaded")
		val enc = encoder ?: throw EngineNotReadyException("Moonshine model not loaded")
		@Suppress("UNUSED_VARIABLE")
		val dec = decoder ?: throw EngineNotReadyException("Moonshine model not loaded")

		// 3. Zero-pad to the 30s max-buffer length used by the TFLite port.
		paddedBuffer.clear()
		val floatView = paddedBuffer.asFloatBuffer()
		floatView.put(samples)
		// Remaining floats are already zero from the fresh ByteBuffer allocation; explicitly
		// zero them on subsequent calls to avoid leaking previous audio into padding.
		val remaining = MAX_SAMPLES - samples.size
		if (remaining > 0) {
			val zeros = FloatArray(remaining)
			floatView.put(zeros)
		}
		paddedBuffer.rewind()

		val startNs = System.nanoTime()

		// 4. Run the encoder. The actual output tensor shape depends on the Moonshine
		//    TFLite export; we allocate a conservative placeholder and let the runtime
		//    throw if the shape doesn't match, which surfaces a clear signal once the
		//    real model is bundled. Until the model is bundled this branch is exercised
		//    only through integration tests.
		try {
			// Note: the real invocation needs the output tensor allocated to the model's
			//       declared shape. We deliberately keep this as-is so it will fail loudly
			//       once a real model is dropped in but tokenizer integration is still
			//       missing, rather than silently producing bogus data.
			@Suppress("UNUSED_VARIABLE")
			val encoderOutputs = runEncoder(enc, paddedBuffer)
		} catch (t: Throwable) {
			// Surface any runtime shape/allocation error; callers should treat this as a
			// fatal engine failure.
			throw t
		}

		// 5. Decoder loop — pending the Moonshine tokenizer (.spm) integration.
		//    Structure is present so bundling the real tokenizer + running the loop is
		//    a drop-in replacement of `decode()` below.
		val decodedText = decode()

		val durationMs = (System.nanoTime() - startNs) / 1_000_000L
		return TranscriptionResult(
			text = decodedText,
			confidence = 0f,
			durationMs = durationMs,
		)
	}

	/**
	 * Placeholder for the encoder forward pass. Allocates an output holder matching a
	 * reasonable acoustic-feature tensor shape; the real shape will be determined once
	 * the Moonshine TFLite export is bundled. Kept as a stub so the call site exists.
	 */
	private fun runEncoder(enc: InterpreterApi, input: ByteBuffer): Array<FloatArray> {
		// TODO(ambient-scribe): wire real encoder output shape once the model is bundled.
		// Until then, callers never reach this path because isReady() is false without
		// the model, and tests exercise only the unready / validation paths.
		val placeholderOut = Array(1) { FloatArray(1) }
		enc.run(input, placeholderOut)
		return placeholderOut
	}

	/**
	 * Autoregressive decoder loop. Intentionally a scaffold — the real implementation
	 * requires the Moonshine SentencePiece tokenizer, which is not yet bundled.
	 */
	private fun decode(): String {
		// TODO(ambient-scribe): implement the autoregressive loop once the Moonshine
		//   tokenizer (`tokenizer.spm`) is bundled and the decoder's I/O tensor shapes
		//   are wired. Expected structure:
		//
		//     val tokens = mutableListOf(BOS_TOKEN_ID)
		//     while (tokens.last() != EOS_TOKEN_ID && tokens.size < MAX_TOKENS) {
		//         val logits = runDecoderStep(dec, encoderOutputs, tokens)
		//         tokens += argmax(logits)
		//     }
		//     return sentencePiece.decode(tokens.drop(1).dropLast(1))
		//
		throw NotImplementedError("Decoder loop pending Moonshine tokenizer integration")
	}

	private fun tryLoadModel(assets: AssetManager, path: String): MappedByteBuffer? {
		return try {
			assets.openFd(path).use { afd ->
				afd.createInputStream().use { input ->
					input.channel.map(
						FileChannel.MapMode.READ_ONLY,
						afd.startOffset,
						afd.declaredLength,
					)
				}
			}
		} catch (e: FileNotFoundException) {
			null
		} catch (e: IOException) {
			Log.w(TAG, "I/O error reading model asset '$path'", e)
			null
		}
	}

	private fun tryReadTokenizer(assets: AssetManager): ByteArray? {
		return try {
			assets.open(TOKENIZER_ASSET).use { it.readBytes() }
		} catch (e: FileNotFoundException) {
			null
		} catch (e: IOException) {
			Log.w(TAG, "I/O error reading tokenizer asset '$TOKENIZER_ASSET'", e)
			null
		}
	}

	/** Wraps `TfLite.initialize(context)` (which returns a Play-Services `Task<Void>`) as a suspend call. */
	private suspend fun awaitTask() {
		withContext(Dispatchers.IO) {
			suspendCancellableCoroutine<Unit> { cont ->
				val task = TfLite.initialize(context)
				task.addOnSuccessListener { cont.resume(Unit) }
				task.addOnFailureListener { e -> cont.resumeWithException(e) }
				task.addOnCanceledListener { cont.cancel() }
			}
		}
	}

	companion object {
		private const val TAG = "MoonshineTfliteEngine"

		private const val ENCODER_ASSET = "models/moonshine/encoder.tflite"
		private const val DECODER_ASSET = "models/moonshine/decoder.tflite"
		private const val TOKENIZER_ASSET = "models/moonshine/tokenizer.spm"

		/** 16kHz * 0.1s. */
		internal const val MIN_SAMPLES = 1_600

		/** 16kHz * 30s. */
		internal const val MAX_SAMPLES = 480_000
	}
}

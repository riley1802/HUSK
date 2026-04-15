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
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.tensorflow.lite.InterpreterApi

/**
 * TranscriptionEngine backed by the Moonshine speech-to-text model running on the Google
 * Play Services TensorFlow Lite runtime (`play-services-tflite-java`).
 *
 * Moonshine's official TFLite port (https://github.com/moonshine-ai/moonshine-tflite) ships
 * FOUR graphs plus a HuggingFace Tokenizers JSON file:
 *   - `preprocessor.tfl`   — raw 16 kHz PCM window → log-mel spectrogram features
 *   - `encoder.tfl`        — features → encoder hidden states
 *   - `decoder_initial.tfl`— first decoder step (no KV cache inputs, emits initial cache)
 *   - `decoder.tfl`        — subsequent decoder steps with KV-cache round-tripping
 *   - `tokenizer.json`     — HuggingFace Tokenizers (NOT SentencePiece) vocabulary
 *
 * Model assets are loaded from:
 *   - `assets/models/moonshine/preprocessor.tfl`
 *   - `assets/models/moonshine/encoder.tfl`
 *   - `assets/models/moonshine/decoder_initial.tfl`
 *   - `assets/models/moonshine/decoder.tfl`
 *   - `assets/models/moonshine/tokenizer.json`
 *
 * None of these are bundled in the repo — they're distributed via git-lfs on the upstream
 * repo and would blow past the 10 MB asset budget even quantized. Users side-load them onto
 * the device after install. Until all five files exist, [initialize] logs a single warning
 * and leaves [isReady] as false; callers that invoke [transcribe] on an unready engine
 * receive [EngineNotReadyException].
 *
 * The decode loop itself is intentionally not implemented in this build — see
 * [decode] for the expected pipeline shape. Because [ready] stays false without the assets,
 * the decode path is unreachable at runtime.
 */
class MoonshineTfliteEngine(
	private val context: Context,
) : TranscriptionEngine {

	private val initMutex = Mutex()
	private val missingAssetsWarningLogged = AtomicBoolean(false)

	@Volatile private var ready: Boolean = false
	@Volatile private var preprocessor: InterpreterApi? = null
	@Volatile private var encoder: InterpreterApi? = null
	@Volatile private var decoderInitial: InterpreterApi? = null
	@Volatile private var decoder: InterpreterApi? = null
	@Volatile private var preprocessorModelBuffer: MappedByteBuffer? = null
	@Volatile private var encoderModelBuffer: MappedByteBuffer? = null
	@Volatile private var decoderInitialModelBuffer: MappedByteBuffer? = null
	@Volatile private var decoderModelBuffer: MappedByteBuffer? = null
	@Volatile private var tokenizerJson: ByteArray? = null

	override suspend fun initialize() {
		initMutex.withLock {
			if (ready) return

			val assets = context.assets

			// Check every asset up-front. If any is missing, log once and stay not-ready.
			val preBuf = tryLoadModel(assets, PREPROCESSOR_ASSET)
			val encBuf = tryLoadModel(assets, ENCODER_ASSET)
			val decInitBuf = tryLoadModel(assets, DECODER_INITIAL_ASSET)
			val decBuf = tryLoadModel(assets, DECODER_ASSET)
			val tokBytes = tryReadBytes(assets, TOKENIZER_ASSET)

			if (preBuf == null || encBuf == null || decInitBuf == null || decBuf == null || tokBytes == null) {
				if (missingAssetsWarningLogged.compareAndSet(false, true)) {
					Log.w(
						TAG,
						"Moonshine assets not bundled — transcription disabled until models + " +
							"tokenizer are side-loaded into app assets",
					)
				}
				return
			}

			// All assets present — now we need the Play-Services runtime.
			try {
				awaitTask()
			} catch (t: Throwable) {
				Log.w(TAG, "TfLite.initialize() failed; engine remains not-ready", t)
				return
			}

			val options = InterpreterApi.Options()
				.setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)

			val createdInterpreters = ArrayList<InterpreterApi>(4)
			val pre: InterpreterApi = try {
				InterpreterApi.create(preBuf, options).also { createdInterpreters += it }
			} catch (t: Throwable) {
				Log.w(TAG, "Failed to create preprocessor interpreter; engine remains not-ready", t)
				return
			}
			val enc: InterpreterApi = try {
				InterpreterApi.create(encBuf, options).also { createdInterpreters += it }
			} catch (t: Throwable) {
				createdInterpreters.forEach { runCatching { it.close() } }
				Log.w(TAG, "Failed to create encoder interpreter; engine remains not-ready", t)
				return
			}
			val decInit: InterpreterApi = try {
				InterpreterApi.create(decInitBuf, options).also { createdInterpreters += it }
			} catch (t: Throwable) {
				createdInterpreters.forEach { runCatching { it.close() } }
				Log.w(TAG, "Failed to create decoder_initial interpreter; engine remains not-ready", t)
				return
			}
			val dec: InterpreterApi = try {
				InterpreterApi.create(decBuf, options).also { createdInterpreters += it }
			} catch (t: Throwable) {
				createdInterpreters.forEach { runCatching { it.close() } }
				Log.w(TAG, "Failed to create decoder interpreter; engine remains not-ready", t)
				return
			}

			// Only assign to fields after every interpreter succeeded.
			preprocessorModelBuffer = preBuf
			encoderModelBuffer = encBuf
			decoderInitialModelBuffer = decInitBuf
			decoderModelBuffer = decBuf
			tokenizerJson = tokBytes
			preprocessor = pre
			encoder = enc
			decoderInitial = decInit
			decoder = dec
			ready = true
			Log.i(TAG, "Moonshine engine initialized (4 models + tokenizer loaded)")
		}
	}

	override suspend fun close() {
		initMutex.withLock {
			ready = false
			listOf(
				"preprocessor" to preprocessor,
				"encoder" to encoder,
				"decoder_initial" to decoderInitial,
				"decoder" to decoder,
			).forEach { (name, interp) ->
				try {
					interp?.close()
				} catch (t: Throwable) {
					Log.w(TAG, "Error closing $name", t)
				}
			}
			preprocessor = null
			encoder = null
			decoderInitial = null
			decoder = null
			preprocessorModelBuffer = null
			encoderModelBuffer = null
			decoderInitialModelBuffer = null
			decoderModelBuffer = null
			tokenizerJson = null
		}
	}

	override fun isReady(): Boolean = ready

	override suspend fun transcribe(samples: FloatArray, languageHint: String): TranscriptionResult {
		// Argument validation runs synchronously on the caller's dispatcher — a programming
		// error (wrong-sized buffer) is worse than a not-ready error, and keeping these out
		// of the withContext block ensures IllegalArgumentException is thrown on the caller's
		// thread rather than hopped to Dispatchers.Default.
		require(samples.size >= MIN_SAMPLES) {
			"samples too short: ${samples.size} (minimum $MIN_SAMPLES at 16kHz = 0.1s)"
		}
		require(samples.size <= MAX_SAMPLES) {
			"samples too long: ${samples.size} (maximum $MAX_SAMPLES at 16kHz = 30s)"
		}

		return withContext(Dispatchers.Default) {
			// Readiness gate. Without the full 4-model + tokenizer asset set this is always
			// the path taken; decode() below is unreachable in this build.
			if (!ready) {
				throw EngineNotReadyException("Moonshine model not loaded")
			}
			throw EngineNotReadyException("Moonshine decoder loop not implemented in this build")
		}
	}

	/**
	 * Moonshine autoregressive decode loop. Intentionally not implemented in this build.
	 *
	 * Expected pipeline shape for a future integration:
	 *   1. preprocessor(samples)           -> features
	 *   2. encoder(features)               -> encoder_out
	 *   3. decoder_initial(encoder_out)    -> (logits_0, kv_cache_0)
	 *   4. loop while token != EOS && len < MAX_TOKENS:
	 *        decoder(encoder_out, prev_token, kv_cache_n) -> (logits_n+1, kv_cache_n+1)
	 *        next_token = argmax(logits_n+1)
	 *   5. HuggingFace Tokenizers (tokenizer.json) decodes the token sequence to text
	 *
	 * Not ported in this build because: (a) the 4 TFLite graphs require git-lfs to fetch and
	 * exceed the 10 MB asset budget even quantized, and (b) Android has no first-party
	 * HuggingFace Tokenizers runtime — it needs either the Rust tokenizers crate via JNI or
	 * a pure-Kotlin reimplementation of the BPE pipeline described in tokenizer.json.
	 */
	@Suppress("unused")
	private fun decode(): String {
		throw EngineNotReadyException("Moonshine decoder loop not implemented in this build")
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

	private fun tryReadBytes(assets: AssetManager, path: String): ByteArray? {
		return try {
			assets.open(path).use { it.readBytes() }
		} catch (e: FileNotFoundException) {
			null
		} catch (e: IOException) {
			Log.w(TAG, "I/O error reading asset '$path'", e)
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

		private const val PREPROCESSOR_ASSET = "models/moonshine/preprocessor.tfl"
		private const val ENCODER_ASSET = "models/moonshine/encoder.tfl"
		private const val DECODER_INITIAL_ASSET = "models/moonshine/decoder_initial.tfl"
		private const val DECODER_ASSET = "models/moonshine/decoder.tfl"
		private const val TOKENIZER_ASSET = "models/moonshine/tokenizer.json"

		/** 16kHz * 0.1s. */
		internal const val MIN_SAMPLES = 1_600

		/** 16kHz * 30s. */
		internal const val MAX_SAMPLES = 480_000
	}
}

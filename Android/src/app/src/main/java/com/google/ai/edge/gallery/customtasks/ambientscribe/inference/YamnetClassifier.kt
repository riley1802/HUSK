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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.InterpreterApi

/**
 * [AudioEventClassifier] backed by Google's YAMNet TFLite model running on the Play
 * Services TensorFlow Lite runtime (`play-services-tflite-java`).
 *
 * YAMNet I/O (canonical TFLite export):
 *   - Input 0: float `(-1,)` — 16kHz mono PCM waveform
 *   - Output 0: float `(frames, 521)` per-frame AudioSet scores
 *   - Output 1: float `(frames, 1024)` embeddings (ignored)
 *   - Output 2: float `(frames, 64)` log-mel spectrogram (ignored)
 *
 * A single 0.975s window produces one frame; longer windows produce multiple frames at
 * ~0.48s hop. Per-class scores are aggregated across frames by mean for stability.
 *
 * Model assets:
 *   - `assets/models/yamnet/yamnet.tflite`
 *   - `assets/models/yamnet/yamnet_class_map.csv` (schema: `index,mid,display_name`, header row)
 *
 * If either asset is missing, [initialize] logs a warning and leaves [isReady] false.
 * Callers that invoke [classify] on an unready classifier receive [ClassifierNotReadyException].
 *
 * TODO(ambient-scribe): verify YAMNet I/O shapes on first real run — the canonical model
 *   takes a dynamic-length 1-D waveform and emits three outputs; the resize/allocate path
 *   below assumes `InterpreterApi.resizeInput` is supported. If the play-services runtime
 *   rejects it, fall back to padding to 15600 samples (one frame).
 */
class YamnetClassifier @Inject constructor(
	@ApplicationContext private val context: Context,
) : AudioEventClassifier {

	private val initMutex = Mutex()

	@Volatile private var ready: Boolean = false
	@Volatile private var interpreter: InterpreterApi? = null
	@Volatile private var modelBuffer: MappedByteBuffer? = null
	@Volatile private var labels: List<String> = emptyList()

	override suspend fun initialize() {
		initMutex.withLock {
			if (ready) return

			try {
				awaitTfLiteInit()
			} catch (t: Throwable) {
				Log.w(TAG, "TfLite.initialize() failed; classifier remains not-ready", t)
				return
			}

			val assets = context.assets
			val model = tryLoadModel(assets, MODEL_ASSET) ?: run {
				Log.w(TAG, "YAMNet asset '$MODEL_ASSET' not found; classifier remains not-ready")
				return
			}
			val classMap = tryLoadClassMap(assets) ?: run {
				Log.w(TAG, "YAMNet class map '$CLASS_MAP_ASSET' not found; classifier remains not-ready")
				return
			}
			if (classMap.size != EXPECTED_CLASSES) {
				Log.w(
					TAG,
					"YAMNet class map size ${classMap.size} != expected $EXPECTED_CLASSES; " +
						"proceeding with parsed count (out-of-range indices will fall back to synthetic names)",
				)
			}

			val options = InterpreterApi.Options()
				.setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)

			val interp: InterpreterApi = try {
				InterpreterApi.create(model, options)
			} catch (t: Throwable) {
				Log.w(TAG, "Failed to create YAMNet interpreter; classifier remains not-ready", t)
				return
			}

			modelBuffer = model
			labels = classMap
			interpreter = interp
			ready = true
			Log.i(TAG, "YAMNet classifier initialized (${classMap.size} labels)")
		}
	}

	override suspend fun close() {
		initMutex.withLock {
			ready = false
			try {
				interpreter?.close()
			} catch (t: Throwable) {
				Log.w(TAG, "Error closing YAMNet interpreter", t)
			}
			interpreter = null
			modelBuffer = null
			labels = emptyList()
		}
	}

	override fun isReady(): Boolean = ready

	override suspend fun classify(
		samples: FloatArray,
		topK: Int,
		minConfidence: Float,
	): List<AudioEventLabel> {
		// Argument validation runs synchronously on the caller's dispatcher — programming
		// errors should surface as IllegalArgumentException on the caller's thread, not be
		// hopped onto Dispatchers.Default.
		require(samples.size in MIN_SAMPLES..MAX_SAMPLES) {
			"samples length ${samples.size} out of range [$MIN_SAMPLES, $MAX_SAMPLES] at 16kHz (1s..30s)"
		}
		require(topK in 1..EXPECTED_CLASSES) {
			"topK out of range: $topK (expected 1..$EXPECTED_CLASSES)"
		}
		require(minConfidence in 0f..1f) {
			"minConfidence out of range: $minConfidence (expected 0..1)"
		}

		return withContext(Dispatchers.Default) {
			if (!ready) {
				throw ClassifierNotReadyException("YAMNet model not loaded")
			}
			val interp = interpreter ?: throw ClassifierNotReadyException("YAMNet model not loaded")
			val labelList = labels

			// Resize input to the actual sample count. YAMNet is a dynamic-length model.
			try {
				interp.resizeInput(0, intArrayOf(samples.size))
				interp.allocateTensors()
			} catch (t: Throwable) {
				// TODO(ambient-scribe): If the play-services InterpreterApi rejects dynamic
				//   resizing, fall back to padding to a fixed 15600-sample frame here.
				throw IllegalStateException("Failed to resize YAMNet input tensor", t)
			}

			// Build the input buffer (1-D float waveform).
			val inputBuffer = ByteBuffer
				.allocateDirect(samples.size * Float.SIZE_BYTES)
				.order(ByteOrder.nativeOrder())
			inputBuffer.asFloatBuffer().put(samples)
			inputBuffer.rewind()

			// Output 0: (frames, 521) scores. We don't know the frame count in advance for
			// a dynamic model, so query the output tensor shape after allocateTensors().
			val outputScoresShape: IntArray = try {
				interp.getOutputTensor(0).shape()
			} catch (t: Throwable) {
				throw IllegalStateException("Failed to query YAMNet output tensor shape", t)
			}
			val frames = if (outputScoresShape.isNotEmpty()) outputScoresShape[0].coerceAtLeast(1) else 1
			val classes = if (outputScoresShape.size >= 2) outputScoresShape[1] else EXPECTED_CLASSES

			val scoresOut = Array(frames) { FloatArray(classes) }

			// The runInterpreter with multi-output API requires `runForMultipleInputsOutputs`.
			// We request only output 0 and ignore the embedding/spectrogram outputs.
			val outputs = HashMap<Int, Any>()
			outputs[0] = scoresOut

			val startNs = System.nanoTime()
			try {
				interp.runForMultipleInputsOutputs(arrayOf<Any>(inputBuffer), outputs)
			} catch (t: Throwable) {
				throw IllegalStateException("YAMNet inference failed", t)
			}
			val durationMs = (System.nanoTime() - startNs) / 1_000_000L
			Log.v(TAG, "YAMNet inference took ${durationMs}ms over $frames frames")

			// Aggregate per-frame scores → per-class mean.
			val classScores = FloatArray(classes)
			for (frameIdx in 0 until frames) {
				val row = scoresOut[frameIdx]
				for (c in 0 until classes) {
					classScores[c] += row[c]
				}
			}
			if (frames > 0) {
				for (c in 0 until classes) {
					classScores[c] /= frames.toFloat()
				}
			}

			// Sort descending, take topK, filter by minConfidence.
			val indexed = IntArray(classes) { it }
			val sortedIdx = indexed
				.toTypedArray()
				.sortedByDescending { classScores[it] }

			val result = ArrayList<AudioEventLabel>(topK)
			for (i in 0 until topK.coerceAtMost(classes)) {
				val classIdx = sortedIdx[i]
				val score = classScores[classIdx]
				if (score < minConfidence) break
				val displayName = if (classIdx in labelList.indices) {
					labelList[classIdx]
				} else {
					"class_$classIdx"
				}
				result.add(AudioEventLabel(displayName, score))
			}
			result
		}
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

	/**
	 * Parses `yamnet_class_map.csv` (header row + `index,mid,display_name` rows). Returns
	 * null if the asset is missing; returns the parsed list otherwise, preserving CSV order
	 * so the list index corresponds to the YAMNet class index.
	 */
	private fun tryLoadClassMap(assets: AssetManager): List<String>? {
		return try {
			assets.open(CLASS_MAP_ASSET).use { input ->
				BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
					val result = ArrayList<String>(EXPECTED_CLASSES)
					var isHeader = true
					reader.lineSequence().forEach { line ->
						if (isHeader) {
							isHeader = false
							return@forEach
						}
						if (line.isBlank()) return@forEach
						// Display name is the third column; may itself contain commas if quoted.
						val displayName = parseDisplayName(line)
						result.add(displayName)
					}
					result
				}
			}
		} catch (e: FileNotFoundException) {
			null
		} catch (e: IOException) {
			Log.w(TAG, "I/O error reading class map '$CLASS_MAP_ASSET'", e)
			null
		}
	}

	/**
	 * Extracts the `display_name` column (third field) from a YAMNet class map CSV row.
	 * Handles the common case where the display name is unquoted and contains no commas;
	 * if it's double-quoted, strips the enclosing quotes and unescapes doubled quotes per
	 * RFC 4180.
	 */
	private fun parseDisplayName(line: String): String {
		val firstComma = line.indexOf(',')
		if (firstComma < 0) return line
		val secondComma = line.indexOf(',', firstComma + 1)
		if (secondComma < 0) return line.substring(firstComma + 1)
		val rest = line.substring(secondComma + 1)
		return if (rest.startsWith('"') && rest.endsWith('"') && rest.length >= 2) {
			rest.substring(1, rest.length - 1).replace("\"\"", "\"")
		} else {
			rest
		}
	}

	/** Wraps `TfLite.initialize(context)` (Play-Services `Task<Void>`) as a suspend call. */
	private suspend fun awaitTfLiteInit() {
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
		private const val TAG = "YamnetClassifier"

		private const val MODEL_ASSET = "models/yamnet/yamnet.tflite"
		private const val CLASS_MAP_ASSET = "models/yamnet/yamnet_class_map.csv"

		/** 16kHz * 1s. */
		internal const val MIN_SAMPLES = 16_000

		/** 16kHz * 30s. */
		internal const val MAX_SAMPLES = 480_000

		/** YAMNet AudioSet vocabulary size. */
		internal const val EXPECTED_CLASSES = 521
	}
}

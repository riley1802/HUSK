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

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [VoiceActivityDetector] backed by Silero VAD v5 running on ONNX Runtime Mobile.
 *
 * Silero VAD v5 I/O:
 *   - input:  float tensor `(1, 512)` — 512-sample PCM window at 16kHz
 *   - state:  float tensor `(2, 1, 128)` — streaming hidden state, carried across calls
 *   - sr:     int64 tensor `[1]` — sample rate (16000)
 * Outputs:
 *   - [0]: float `(1, 1)` — speech probability
 *   - [1]: float `(2, 1, 128)` — updated state
 *
 * Model asset: `assets/models/silero/silero_vad.onnx`. If missing, [initialize] logs a
 * warning and leaves [isReady] as false; callers that invoke [detectSpeech] on an unready
 * detector receive [VadNotReadyException].
 *
 * TODO(ambient-scribe): verify input/output tensor names ("input", "state", "sr") against
 *   the bundled silero_vad.onnx when the asset lands.
 */
class SileroVad @Inject constructor(
	@ApplicationContext private val context: Context,
) : VoiceActivityDetector {

	private val initMutex = Mutex()

	@Volatile private var ready: Boolean = false
	@Volatile private var env: OrtEnvironment? = null
	@Volatile private var session: OrtSession? = null

	/** Retained streaming state across detectSpeech() calls — shape (2, 1, 128). */
	private val state: FloatArray = FloatArray(STATE_ELEMENTS)

	override suspend fun initialize() {
		initMutex.withLock {
			if (ready) return

			val modelBytes: ByteArray = try {
				withContext(Dispatchers.IO) {
					context.assets.open(MODEL_ASSET).use { it.readBytes() }
				}
			} catch (e: FileNotFoundException) {
				Log.w(TAG, "Silero VAD asset '$MODEL_ASSET' not found; detector remains not-ready")
				return
			} catch (e: IOException) {
				Log.w(TAG, "I/O error reading Silero VAD asset '$MODEL_ASSET'", e)
				return
			}

			val environment: OrtEnvironment = try {
				OrtEnvironment.getEnvironment()
			} catch (t: Throwable) {
				Log.w(TAG, "Failed to obtain OrtEnvironment; detector remains not-ready", t)
				return
			}

			val sess: OrtSession = try {
				environment.createSession(modelBytes, OrtSession.SessionOptions())
			} catch (t: Throwable) {
				Log.w(TAG, "Failed to create Silero VAD session; detector remains not-ready", t)
				return
			}

			// Zero the state on fresh initialize.
			state.fill(0f)
			env = environment
			session = sess
			ready = true
			Log.i(TAG, "Silero VAD initialized")
		}
	}

	override suspend fun close() {
		initMutex.withLock {
			ready = false
			try {
				session?.close()
			} catch (t: Throwable) {
				Log.w(TAG, "Error closing Silero VAD session", t)
			}
			session = null
			// OrtEnvironment is a shared singleton — don't close it, just drop our reference.
			env = null
			state.fill(0f)
		}
	}

	override fun isReady(): Boolean = ready

	override fun frameSize(): Int = FRAME_SAMPLES

	override suspend fun detectSpeech(samples: FloatArray): Boolean {
		// Argument validation runs synchronously on the caller's dispatcher, before the
		// readiness gate, so programming errors surface as IllegalArgumentException even
		// when the model is absent.
		require(samples.isNotEmpty() && samples.size % FRAME_SAMPLES == 0) {
			"samples must be non-empty and a multiple of frame size ($FRAME_SAMPLES); got ${samples.size}"
		}

		return withContext(Dispatchers.Default) {
			if (!ready) {
				throw VadNotReadyException("Silero VAD model not loaded")
			}
			val environment = env ?: throw VadNotReadyException("Silero VAD model not loaded")
			val sess = session ?: throw VadNotReadyException("Silero VAD model not loaded")

			val frameCount = samples.size / FRAME_SAMPLES
			var foundSpeech = false

			for (frameIdx in 0 until frameCount) {
				val offset = frameIdx * FRAME_SAMPLES
				val frame = FloatArray(FRAME_SAMPLES)
				System.arraycopy(samples, offset, frame, 0, FRAME_SAMPLES)

				var audioTensor: OnnxTensor? = null
				var stateTensor: OnnxTensor? = null
				var srTensor: OnnxTensor? = null
				var result: OrtSession.Result? = null
				try {
					audioTensor = OnnxTensor.createTensor(
						environment,
						FloatBuffer.wrap(frame),
						longArrayOf(1L, FRAME_SAMPLES.toLong()),
					)
					stateTensor = OnnxTensor.createTensor(
						environment,
						FloatBuffer.wrap(state),
						longArrayOf(2L, 1L, 128L),
					)
					srTensor = OnnxTensor.createTensor(
						environment,
						LongBuffer.wrap(longArrayOf(SAMPLE_RATE.toLong())),
						longArrayOf(1L),
					)

					val inputs: Map<String, OnnxTensor> = mapOf(
						INPUT_NAME to audioTensor,
						STATE_NAME to stateTensor,
						SR_NAME to srTensor,
					)

					result = sess.run(inputs)

					// Output 0: probability (1, 1)
					val probValue = result[0].value
					val probability: Float = when (probValue) {
						is Array<*> -> {
							@Suppress("UNCHECKED_CAST")
							val outer = probValue as Array<FloatArray>
							outer[0][0]
						}
						else -> throw IllegalStateException(
							"Unexpected Silero VAD probability output type: ${probValue?.javaClass}",
						)
					}

					// Output 1: updated state (2, 1, 128)
					val stateValue = result[1].value
					copyStateBack(stateValue)

					if (probability >= SPEECH_THRESHOLD) {
						foundSpeech = true
					}
					// Do NOT early-return: state propagation must occur for every frame so
					// subsequent calls receive the correct streaming context.
				} finally {
					try {
						result?.close()
					} catch (t: Throwable) {
						Log.w(TAG, "Error closing Silero VAD result", t)
					}
					try {
						audioTensor?.close()
					} catch (t: Throwable) {
						Log.w(TAG, "Error closing audio tensor", t)
					}
					try {
						stateTensor?.close()
					} catch (t: Throwable) {
						Log.w(TAG, "Error closing state tensor", t)
					}
					try {
						srTensor?.close()
					} catch (t: Throwable) {
						Log.w(TAG, "Error closing sr tensor", t)
					}
				}
			}

			foundSpeech
		}
	}

	/** Copies the (2, 1, 128) nested-array state output back into the retained [state] buffer. */
	private fun copyStateBack(stateValue: Any?) {
		if (stateValue !is Array<*>) {
			throw IllegalStateException(
				"Unexpected Silero VAD state output type: ${stateValue?.javaClass}",
			)
		}
		// Expected shape: Array<Array<FloatArray>> with dims (2, 1, 128).
		var writeIdx = 0
		for (dim0 in stateValue) {
			val dim1 = dim0 as? Array<*>
				?: throw IllegalStateException("Silero VAD state dim1 not an array")
			for (dim2 in dim1) {
				val floats = dim2 as? FloatArray
					?: throw IllegalStateException("Silero VAD state leaf not FloatArray")
				System.arraycopy(floats, 0, state, writeIdx, floats.size)
				writeIdx += floats.size
			}
		}
		if (writeIdx != STATE_ELEMENTS) {
			throw IllegalStateException(
				"Silero VAD state size mismatch: wrote $writeIdx, expected $STATE_ELEMENTS",
			)
		}
	}

	companion object {
		private const val TAG = "SileroVad"

		private const val MODEL_ASSET = "models/silero/silero_vad.onnx"

		/** 16kHz v5 frame size in samples. */
		const val FRAME_SAMPLES = 512

		internal const val SAMPLE_RATE = 16_000

		/** Silero VAD streaming state shape: (2, 1, 128) = 256 floats. */
		private const val STATE_ELEMENTS = 2 * 1 * 128

		/** Default speech-probability threshold per Silero VAD docs. */
		internal const val SPEECH_THRESHOLD = 0.5f

		private const val INPUT_NAME = "input"
		private const val STATE_NAME = "state"
		private const val SR_NAME = "sr"
	}
}

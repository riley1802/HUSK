/*
 * Copyright 2026 Riley Thomason
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.data.speaker

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.InterpreterApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the ECAPA-TDNN TFLite model for generating speaker voice embeddings.
 *
 * The model takes a short audio segment (16kHz mono float samples) and produces
 * a 192-dimensional embedding vector that characterizes the speaker's voice.
 *
 * Thread-safe via mutex — safe for concurrent access.
 */
@Singleton
class SpeakerEmbeddingManager @Inject constructor() {

	companion object {
		private const val TAG = "SpeakerEmbeddingMgr"
		const val EMBEDDING_DIM = 192
		// Minimum audio segment length in samples (0.5 seconds at 16kHz).
		const val MIN_SEGMENT_SAMPLES = 8000
		// Target audio segment length in samples (3 seconds at 16kHz).
		const val TARGET_SEGMENT_SAMPLES = 48000
	}

	private val mutex = Mutex()
	private var interpreter: InterpreterApi? = null
	private var modelPath: String? = null

	val isLoaded: Boolean
		get() = interpreter != null

	/**
	 * Load the ECAPA-TDNN TFLite model from the given path.
	 */
	suspend fun load(path: String) = mutex.withLock {
		if (interpreter != null && modelPath == path) {
			Log.d(TAG, "Model already loaded from $path")
			return@withLock
		}
		unloadInternal()

		Log.d(TAG, "Loading ECAPA-TDNN model from $path")
		val options = InterpreterApi.Options().apply {
			setNumThreads(2)
		}
		interpreter = InterpreterApi.create(File(path), options)
		modelPath = path
		Log.d(TAG, "ECAPA-TDNN model loaded successfully")
	}

	/**
	 * Compute a 192-dimensional speaker embedding from audio samples.
	 *
	 * @param audioSamples 16kHz mono float PCM samples (ideally 2-5 seconds).
	 * @return 192-dimensional embedding vector, or null if model not loaded.
	 */
	suspend fun embed(audioSamples: FloatArray): FloatArray? = mutex.withLock {
		val interp = interpreter
		if (interp == null) {
			Log.e(TAG, "Cannot embed: model not loaded")
			return@withLock null
		}

		if (audioSamples.size < MIN_SEGMENT_SAMPLES) {
			Log.w(TAG, "Audio segment too short: ${audioSamples.size} samples (min $MIN_SEGMENT_SAMPLES)")
			return@withLock null
		}

		// Pad or trim to target length.
		val input = if (audioSamples.size > TARGET_SEGMENT_SAMPLES) {
			audioSamples.copyOfRange(0, TARGET_SEGMENT_SAMPLES)
		} else {
			audioSamples
		}

		try {
			// Prepare input tensor: [1, numSamples]
			val inputBuffer = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
			for (sample in input) {
				inputBuffer.putFloat(sample)
			}
			inputBuffer.rewind()

			// Prepare output tensor: [1, EMBEDDING_DIM]
			val outputBuffer = ByteBuffer.allocateDirect(EMBEDDING_DIM * 4).order(ByteOrder.nativeOrder())

			interp.run(inputBuffer, outputBuffer)

			outputBuffer.rewind()
			val embedding = FloatArray(EMBEDDING_DIM)
			for (i in embedding.indices) {
				embedding[i] = outputBuffer.getFloat()
			}

			// L2 normalize the embedding.
			val norm = Math.sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
			if (norm > 0f) {
				for (i in embedding.indices) {
					embedding[i] /= norm
				}
			}

			embedding
		} catch (e: Exception) {
			Log.e(TAG, "Error computing embedding", e)
			null
		}
	}

	/**
	 * Unload the model and release resources.
	 */
	suspend fun unload() = mutex.withLock {
		unloadInternal()
	}

	private fun unloadInternal() {
		interpreter?.close()
		interpreter = null
		modelPath = null
		Log.d(TAG, "ECAPA-TDNN model unloaded")
	}
}

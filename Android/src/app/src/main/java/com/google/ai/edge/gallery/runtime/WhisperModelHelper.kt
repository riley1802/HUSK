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

package com.google.ai.edge.gallery.runtime

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Whisper model lifecycle and provides transcription API.
 *
 * This is NOT an LlmModelHelper — Whisper is speech-to-text, not a chat model.
 * It is called directly by the Audio Scribe pipeline.
 */
object WhisperModelHelper {

	private const val TAG = "WhisperModelHelper"

	data class TranscriptSegment(
		val text: String,
		val startMs: Long,
		val endMs: Long,
	)

	private var contextPtr: Long = 0L
	private var loadedModelPath: String? = null

	val isLoaded: Boolean
		get() = contextPtr != 0L

	/**
	 * Initialize the Whisper model from the downloaded model file.
	 *
	 * @param context Android context for resolving model path.
	 * @param model The Whisper model entry (Tiny/Base/Small).
	 * @param onDone Callback with empty string on success, error message on failure.
	 */
	fun initialize(context: Context, model: Model, onDone: (String) -> Unit) {
		initialize(context, model.getPath(context), onDone)
	}

	/**
	 * Initialize the Whisper model from a direct file path.
	 */
	fun initialize(context: Context, modelPath: String, onDone: (String) -> Unit) {
		val path = modelPath
		Log.d(TAG, "Initializing Whisper model from $path")

		if (contextPtr != 0L && loadedModelPath == path) {
			Log.d(TAG, "Model already loaded from $path")
			onDone("")
			return
		}

		// Free any previously loaded model.
		if (contextPtr != 0L) {
			WhisperJni.freeModel(contextPtr)
			contextPtr = 0L
			loadedModelPath = null
		}

		try {
			contextPtr = WhisperJni.initModel(path)
			if (contextPtr == 0L) {
				onDone("Failed to load Whisper model from $path")
				return
			}
			loadedModelPath = path
			Log.d(TAG, "Whisper model loaded successfully")
			onDone("")
		} catch (e: Exception) {
			Log.e(TAG, "Error initializing Whisper model", e)
			onDone("Error loading Whisper model: ${e.message}")
		}
	}

	/**
	 * Transcribe audio samples using the loaded Whisper model.
	 *
	 * @param samples 16kHz mono float PCM samples normalized to [-1.0, 1.0].
	 * @param language Language code (default "en", use "auto" for auto-detect).
	 * @param nThreads Number of CPU threads (default 4).
	 * @param onSegment Optional callback invoked for each segment as it's produced.
	 * @return List of transcript segments, or empty list on failure.
	 */
	suspend fun transcribe(
		samples: FloatArray,
		language: String = "en",
		nThreads: Int = 4,
		onSegment: ((TranscriptSegment) -> Unit)? = null,
	): List<TranscriptSegment> = withContext(Dispatchers.Default) {
		if (contextPtr == 0L) {
			Log.e(TAG, "Cannot transcribe: model not loaded")
			return@withContext emptyList()
		}

		Log.d(TAG, "Starting transcription of ${samples.size} samples")

		val jniSegments = WhisperJni.transcribe(contextPtr, samples, language, nThreads)
		if (jniSegments == null) {
			Log.e(TAG, "Transcription returned null")
			return@withContext emptyList()
		}

		val segments = jniSegments.map { jniSeg ->
			TranscriptSegment(
				text = jniSeg.text.trim(),
				startMs = jniSeg.startMs,
				endMs = jniSeg.endMs,
			).also { seg ->
				onSegment?.invoke(seg)
			}
		}

		Log.d(TAG, "Transcription complete: ${segments.size} segments")
		segments
	}

	/**
	 * Free the loaded Whisper model and release native memory.
	 */
	fun cleanUp() {
		if (contextPtr != 0L) {
			WhisperJni.freeModel(contextPtr)
			contextPtr = 0L
			loadedModelPath = null
			Log.d(TAG, "Whisper model cleaned up")
		}
	}
}

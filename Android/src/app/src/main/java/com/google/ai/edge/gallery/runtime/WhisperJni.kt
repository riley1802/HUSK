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

/**
 * JNI bridge to whisper.cpp native library for on-device speech-to-text.
 */
object WhisperJni {

	init {
		System.loadLibrary("whisper_jni")
	}

	/**
	 * A transcription segment with text and timing information.
	 */
	data class Segment(
		val text: String,
		val startMs: Long,
		val endMs: Long,
	)

	/**
	 * Initialize a Whisper model from a file path.
	 * @return Native context pointer, or 0 on failure.
	 */
	external fun initModel(modelPath: String): Long

	/**
	 * Run full transcription on audio samples.
	 *
	 * @param contextPtr Native context pointer from [initModel].
	 * @param samples 16kHz mono float PCM samples normalized to [-1.0, 1.0].
	 * @param language Language code (e.g., "en", "auto").
	 * @param nThreads Number of CPU threads to use.
	 * @return Array of transcription segments, or null on failure.
	 */
	external fun transcribe(
		contextPtr: Long,
		samples: FloatArray,
		language: String = "en",
		nThreads: Int = 4,
	): Array<Segment>?

	/**
	 * Free a Whisper model context and release native memory.
	 */
	external fun freeModel(contextPtr: Long)

	/**
	 * Get whisper.cpp system info string (for debugging).
	 */
	external fun getSystemInfo(): String
}

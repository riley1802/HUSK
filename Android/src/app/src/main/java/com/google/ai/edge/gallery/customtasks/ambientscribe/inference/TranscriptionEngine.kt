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

/**
 * Abstraction over a speech-to-text engine used by the Ambient Scribe feature.
 *
 * Implementations are expected to be thread-safe with respect to [isReady]; the lifecycle
 * methods ([initialize], [close]) and [transcribe] are marked suspend so callers can invoke
 * them from coroutines without blocking the calling thread.
 */
interface TranscriptionEngine {
	/** Initializes the engine. Safe to call multiple times; subsequent calls no-op if already ready. */
	suspend fun initialize()

	/** Releases native resources. After close(), isReady() returns false. */
	suspend fun close()

	/** True iff initialize() completed successfully and the engine can transcribe. */
	fun isReady(): Boolean

	/**
	 * Transcribes the given 16kHz mono PCM float samples (range [-1.0, 1.0]).
	 *
	 * @param samples raw audio — caller guarantees exactly 16kHz mono
	 * @param languageHint ISO 639-1 code, ignored if the engine doesn't support language hints
	 * @return TranscriptionResult or throws EngineNotReadyException if not ready
	 * @throws EngineNotReadyException if the engine has not been initialized successfully
	 * @throws IllegalArgumentException if [samples] violates the supported length range
	 */
	suspend fun transcribe(samples: FloatArray, languageHint: String = "en"): TranscriptionResult
}

/**
 * Result of a successful transcription call.
 *
 * @property text decoded transcript text (may be empty if the model produced no tokens)
 * @property confidence engine-specific confidence estimate in the range [0.0, 1.0]
 * @property durationMs wall-clock inference time, measured around the native call
 */
data class TranscriptionResult(
	val text: String,
	val confidence: Float,
	val durationMs: Long,
)

/** Thrown when [TranscriptionEngine.transcribe] is invoked before the engine is ready. */
class EngineNotReadyException(message: String) : IllegalStateException(message)

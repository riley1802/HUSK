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
 * Abstraction over a voice-activity detector used by the Ambient Scribe feature.
 *
 * Implementations are expected to be thread-safe with respect to [isReady]; lifecycle
 * methods ([initialize], [close]) and [detectSpeech] are marked suspend so callers can
 * invoke them from coroutines without blocking the calling thread.
 */
interface VoiceActivityDetector {
	/** Initializes the detector. Safe to call multiple times; subsequent calls no-op if already ready. */
	suspend fun initialize()

	/**
	 * Releases native resources. After close(), isReady() returns false.
	 *
	 * Not safe to invoke concurrently with close() — callers must serialize lifecycle
	 * transitions. The foreground service serializes these by construction.
	 */
	suspend fun close()

	/** True iff initialize() completed successfully and the detector can run inference. */
	fun isReady(): Boolean

	/**
	 * Detects speech activity within the given window.
	 *
	 * Not safe to invoke concurrently with close() — callers must serialize lifecycle
	 * transitions.
	 *
	 * @param samples 16kHz mono PCM floats, length MUST equal frameSize() * N for some N≥1
	 * @return true iff any frame in the window crossed the speech-probability threshold
	 * @throws VadNotReadyException if the detector has not been initialized successfully
	 * @throws IllegalArgumentException if [samples] is empty or not a multiple of frame size
	 */
	suspend fun detectSpeech(samples: FloatArray): Boolean

	/** Frame size in samples Silero requires (512 for 16kHz v5). */
	fun frameSize(): Int
}

/** Thrown when [VoiceActivityDetector.detectSpeech] is invoked before the detector is ready. */
class VadNotReadyException(message: String) : IllegalStateException(message)

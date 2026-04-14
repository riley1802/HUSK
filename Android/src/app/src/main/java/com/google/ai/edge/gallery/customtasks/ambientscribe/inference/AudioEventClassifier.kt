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
 * Abstraction over an on-device audio-event classifier (e.g. YAMNet) used by the
 * Ambient Scribe feature to attach AudioSet labels to captured audio windows.
 *
 * Implementations are expected to be thread-safe with respect to [isReady]; lifecycle
 * methods ([initialize], [close]) and [classify] are marked suspend so callers can invoke
 * them from coroutines without blocking the calling thread.
 */
interface AudioEventClassifier {
	/** Initializes the classifier. Safe to call multiple times; subsequent calls no-op if already ready. */
	suspend fun initialize()

	/**
	 * Releases native resources. After close(), isReady() returns false.
	 *
	 * Not safe to invoke concurrently with classify() — callers must serialize lifecycle
	 * transitions.
	 */
	suspend fun close()

	/** True iff initialize() completed successfully and the classifier can classify audio. */
	fun isReady(): Boolean

	/**
	 * Classifies audio events in the given window.
	 *
	 * Not safe to invoke concurrently with close() — callers must serialize lifecycle
	 * transitions.
	 *
	 * @param samples 16kHz mono PCM floats, min length 16000 (1s), max length 480000 (30s)
	 * @param topK how many top-scoring labels to return (default 3)
	 * @param minConfidence labels below this are filtered (default 0.1f)
	 * @return list of labels sorted descending by confidence; may be empty if no label cleared minConfidence
	 * @throws ClassifierNotReadyException if the classifier has not been initialized successfully
	 * @throws IllegalArgumentException if [samples], [topK], or [minConfidence] are out of range
	 */
	suspend fun classify(
		samples: FloatArray,
		topK: Int = 3,
		minConfidence: Float = 0.1f,
	): List<AudioEventLabel>
}

/**
 * A single classified audio event.
 *
 * @property displayName AudioSet class name (e.g. "Speech", "Dog", "Music")
 * @property confidence model confidence in [0, 1]
 */
data class AudioEventLabel(val displayName: String, val confidence: Float)

/** Thrown when [AudioEventClassifier.classify] is invoked before the classifier is ready. */
class ClassifierNotReadyException(message: String) : IllegalStateException(message)

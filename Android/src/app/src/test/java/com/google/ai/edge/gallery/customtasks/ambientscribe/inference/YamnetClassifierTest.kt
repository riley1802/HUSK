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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Contract-level tests for [YamnetClassifier]. These do not load a real YAMNet model; they
 * verify unready-state behavior and argument validation, which are the only paths reachable
 * without the bundled assets. Argument validation fires before the readiness check so
 * callers catch programming errors promptly.
 */
class YamnetClassifierTest {

	private lateinit var context: Context
	private lateinit var classifier: YamnetClassifier

	@Before
	fun setUp() {
		context = mock(Context::class.java)
		classifier = YamnetClassifier(context)
	}

	@Test
	fun `uninitialized classifier reports not ready`() {
		assertFalse(classifier.isReady())
	}

	@Test
	fun `classify on uninitialized classifier throws ClassifierNotReadyException`() {
		val samples = FloatArray(YamnetClassifier.MIN_SAMPLES)
		assertThrows(ClassifierNotReadyException::class.java) {
			runBlocking { classifier.classify(samples) }
		}
	}

	@Test
	fun `classify rejects too-short samples with IllegalArgumentException`() {
		val tooShort = FloatArray(YamnetClassifier.MIN_SAMPLES - 1)
		val ex = assertThrows(IllegalArgumentException::class.java) {
			runBlocking { classifier.classify(tooShort) }
		}
		assert(ex !is ClassifierNotReadyException)
	}

	@Test
	fun `classify rejects too-long samples with IllegalArgumentException`() {
		val tooLong = FloatArray(YamnetClassifier.MAX_SAMPLES + 1)
		val ex = assertThrows(IllegalArgumentException::class.java) {
			runBlocking { classifier.classify(tooLong) }
		}
		assert(ex !is ClassifierNotReadyException)
	}

	@Test
	fun `classify rejects topK zero with IllegalArgumentException`() {
		val samples = FloatArray(YamnetClassifier.MIN_SAMPLES)
		val ex = assertThrows(IllegalArgumentException::class.java) {
			runBlocking { classifier.classify(samples, topK = 0) }
		}
		assert(ex !is ClassifierNotReadyException)
	}

	@Test
	fun `classify rejects topK 522 with IllegalArgumentException`() {
		val samples = FloatArray(YamnetClassifier.MIN_SAMPLES)
		val ex = assertThrows(IllegalArgumentException::class.java) {
			runBlocking { classifier.classify(samples, topK = 522) }
		}
		assert(ex !is ClassifierNotReadyException)
	}

	@Test
	fun `classify rejects negative minConfidence with IllegalArgumentException`() {
		val samples = FloatArray(YamnetClassifier.MIN_SAMPLES)
		val ex = assertThrows(IllegalArgumentException::class.java) {
			runBlocking { classifier.classify(samples, minConfidence = -0.01f) }
		}
		assert(ex !is ClassifierNotReadyException)
	}

	@Test
	fun `classify rejects over-one minConfidence with IllegalArgumentException`() {
		val samples = FloatArray(YamnetClassifier.MIN_SAMPLES)
		val ex = assertThrows(IllegalArgumentException::class.java) {
			runBlocking { classifier.classify(samples, minConfidence = 1.01f) }
		}
		assert(ex !is ClassifierNotReadyException)
	}

	@Test
	fun `classify with valid-sized buffer surfaces readiness error not arg error`() {
		// Documents the ordering contract: argument validation passes for a valid buffer,
		// so the next failure mode on an uninitialized classifier is the readiness gate.
		val samples = FloatArray(YamnetClassifier.MIN_SAMPLES)
		assertThrows(ClassifierNotReadyException::class.java) {
			runBlocking { classifier.classify(samples) }
		}
	}

	@Test
	fun `close is idempotent`() {
		runBlocking {
			classifier.close()
			classifier.close()
		}
		assertFalse(classifier.isReady())
	}

	@Test
	fun `AudioEventLabel data class equality round-trip`() {
		val a = AudioEventLabel("Speech", 0.8f)
		val b = AudioEventLabel("Speech", 0.8f)
		val c = AudioEventLabel("Music", 0.8f)
		val d = AudioEventLabel("Speech", 0.5f)
		assertEquals(a, b)
		assertEquals(a.hashCode(), b.hashCode())
		assertNotEquals(a, c)
		assertNotEquals(a, d)
		assertEquals("Speech", a.displayName)
		assertEquals(0.8f, a.confidence, 0.0f)
	}
}

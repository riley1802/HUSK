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
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Contract-level tests for [SileroVad]. These do not load a real ONNX model; they verify
 * that the detector behaves correctly in the unready state (the only state reachable
 * without the bundled Silero asset) and that argument validation fires before the
 * readiness check so callers catch programming errors promptly.
 */
class SileroVadTest {

	private lateinit var context: Context
	private lateinit var vad: SileroVad

	@Before
	fun setUp() {
		context = mock(Context::class.java)
		vad = SileroVad(context)
	}

	@Test
	fun `uninitialized detector reports not ready`() {
		assertFalse(vad.isReady())
	}

	@Test
	fun `detectSpeech on uninitialized detector throws VadNotReadyException`() {
		val samples = FloatArray(SileroVad.FRAME_SAMPLES)
		assertThrows(VadNotReadyException::class.java) {
			runBlocking { vad.detectSpeech(samples) }
		}
	}

	@Test
	fun `detectSpeech rejects empty samples with IllegalArgumentException`() {
		val empty = FloatArray(0)
		val ex = assertThrows(IllegalArgumentException::class.java) {
			runBlocking { vad.detectSpeech(empty) }
		}
		// Must NOT be the narrower VadNotReadyException subclass.
		assert(ex !is VadNotReadyException)
	}

	@Test
	fun `detectSpeech rejects non-multiple-of-frame-size input with IllegalArgumentException`() {
		val bad = FloatArray(100)
		val ex = assertThrows(IllegalArgumentException::class.java) {
			runBlocking { vad.detectSpeech(bad) }
		}
		assert(ex !is VadNotReadyException)
	}

	@Test
	fun `detectSpeech with exactly FRAME_SAMPLES length surfaces readiness error not arg error`() {
		// Argument validation passes for a well-sized buffer — so the next failure mode
		// on an uninitialized detector is the readiness gate. Documents the ordering
		// contract: validation precedes the readiness check.
		val samples = FloatArray(SileroVad.FRAME_SAMPLES)
		assertThrows(VadNotReadyException::class.java) {
			runBlocking { vad.detectSpeech(samples) }
		}
	}

	@Test
	fun `close is idempotent`() {
		runBlocking {
			vad.close()
			vad.close()
		}
		assertFalse(vad.isReady())
	}

	@Test
	fun `frameSize returns 512`() {
		assertEquals(512, vad.frameSize())
	}
}

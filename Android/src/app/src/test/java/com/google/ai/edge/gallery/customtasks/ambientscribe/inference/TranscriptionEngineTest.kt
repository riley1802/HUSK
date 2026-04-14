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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionEngineTest {

	@Test
	fun `TranscriptionResult equality is value-based`() {
		val a = TranscriptionResult(text = "hello", confidence = 0.9f, durationMs = 12L)
		val b = TranscriptionResult(text = "hello", confidence = 0.9f, durationMs = 12L)
		val c = TranscriptionResult(text = "hello", confidence = 0.9f, durationMs = 13L)
		assertEquals(a, b)
		assertEquals(a.hashCode(), b.hashCode())
		assertNotEquals(a, c)
	}

	@Test
	fun `EngineNotReadyException is an IllegalStateException`() {
		val ex = EngineNotReadyException("test")
		assertTrue(ex is IllegalStateException)
		assertEquals("test", ex.message)
	}
}

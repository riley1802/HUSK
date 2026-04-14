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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Contract-level tests for [MoonshineTfliteEngine]. These do not load a real TFLite model;
 * they verify that the engine behaves correctly in the unready state (which is the only
 * state reachable without the bundled Moonshine assets) and that argument validation fires
 * before the readiness check so callers catch programming errors promptly.
 */
class MoonshineTfliteEngineTest {

	private lateinit var context: Context
	private lateinit var engine: MoonshineTfliteEngine

	@Before
	fun setUp() {
		context = mock(Context::class.java)
		engine = MoonshineTfliteEngine(context)
	}

	@Test
	fun `uninitialized engine reports not ready`() {
		assertFalse(engine.isReady())
	}

	@Test
	fun `transcribe on uninitialized engine throws EngineNotReadyException`() {
		val samples = FloatArray(MoonshineTfliteEngine.MIN_SAMPLES)
		assertThrows(EngineNotReadyException::class.java) {
			runBlocking { engine.transcribe(samples) }
		}
	}

	@Test
	fun `transcribe rejects samples shorter than minimum length`() {
		// 0.05s — half the 0.1s minimum
		val samples = FloatArray(MoonshineTfliteEngine.MIN_SAMPLES - 1)
		assertThrows(IllegalArgumentException::class.java) {
			runBlocking { engine.transcribe(samples) }
		}
	}

	@Test
	fun `transcribe rejects samples longer than maximum length`() {
		val samples = FloatArray(MoonshineTfliteEngine.MAX_SAMPLES + 1)
		assertThrows(IllegalArgumentException::class.java) {
			runBlocking { engine.transcribe(samples) }
		}
	}

	@Test
	fun `argument validation runs before readiness check`() {
		// An unready engine + bad args should surface the arg error, not the readiness error.
		// This is important so callers learn about programming errors even before the model
		// is bundled.
		val tooShort = FloatArray(0)
		val ex = assertThrows(IllegalArgumentException::class.java) {
			runBlocking { engine.transcribe(tooShort) }
		}
		// The exception must NOT be the narrower EngineNotReadyException subclass.
		assert(ex !is EngineNotReadyException)
	}

	@Test
	fun `close is idempotent`() {
		runBlocking {
			engine.close()
			engine.close()
		}
		assertFalse(engine.isReady())
	}
}

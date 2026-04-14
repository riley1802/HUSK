/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.llm

import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEvent
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.RewriteState
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * Unit tests for [GemmaEventDescriptionRewriter]. These mock out [GemmaClient] so no real
 * on-device LLM is required; the tests verify the sanitize / skip / not-ready semantics
 * documented on the rewriter.
 *
 * Suspend-function mocking uses `doAnswer { }.when(mock).suspendFn(...)` because Mockito's
 * `when(...)` cannot be invoked outside a coroutine for suspend methods.
 */
class GemmaEventDescriptionRewriterTest {

	private lateinit var client: GemmaClient
	private lateinit var rewriter: GemmaEventDescriptionRewriter

	@Before
	fun setUp() {
		client = mock(GemmaClient::class.java)
		rewriter = GemmaEventDescriptionRewriter(client)
	}

	@Test
	fun `empty event list returns empty map without invoking client`() : Unit = runBlocking {
		val result = rewriter.rewrite(emptyList())
		assertTrue(result.isEmpty())
		verify(client, never()).isReady()
		verify(client, never()).generate(anyString())
	}

	@Test
	fun `not-ready client returns empty map without invoking generate`() : Unit = runBlocking {
		doReturn(false).`when`(client).isReady()
		val events = listOf(event(id = 1, label = "Dog bark"))

		val result = rewriter.rewrite(events)

		assertTrue(result.isEmpty())
		verify(client, never()).generate(anyString())
	}

	@Test
	fun `successful rewrite strips wrapping quotes and whitespace`() : Unit = runBlocking {
		doReturn(true).`when`(client).isReady()
		stubGenerate { "  \"A dog barked nearby.\"  \n" }
		val events = listOf(event(id = 1, label = "Dog bark"))

		val result = rewriter.rewrite(events)

		assertEquals(1, result.size)
		assertEquals("A dog barked nearby.", result[1L])
	}

	@Test
	fun `multi-line model output is collapsed to the first non-empty line`() : Unit = runBlocking {
		doReturn(true).`when`(client).isReady()
		stubGenerate { "\n  The vacuum ran in the background.\n\nExtra commentary ignored." }
		val events = listOf(event(id = 7, label = "Vacuum cleaner"))

		val result = rewriter.rewrite(events)

		assertEquals("The vacuum ran in the background.", result[7L])
	}

	@Test
	fun `events whose generate throws are skipped and do not abort the batch`() : Unit = runBlocking {
		doReturn(true).`when`(client).isReady()
		val responses = ArrayDeque(listOf<() -> String>(
			{ "First one worked." },
			{ throw RuntimeException("boom") },
			{ "Third one worked." },
		))
		doAnswer { responses.removeFirst().invoke() }.`when`(client).generate(anyString())

		val events = listOf(
			event(id = 1, label = "Dog bark"),
			event(id = 2, label = "Music"),
			event(id = 3, label = "Door knock"),
		)

		val result = rewriter.rewrite(events)

		assertEquals(2, result.size)
		assertEquals("First one worked.", result[1L])
		assertEquals("Third one worked.", result[3L])
		assertFalse(result.containsKey(2L))
	}

	@Test
	fun `blank model output produces no map entry`() : Unit = runBlocking {
		doReturn(true).`when`(client).isReady()
		stubGenerate { "   \n   " }
		val events = listOf(event(id = 99, label = "Silence"))

		val result = rewriter.rewrite(events)

		assertTrue(result.isEmpty())
	}

	@Test
	fun `curly-quoted output is unwrapped`() : Unit = runBlocking {
		doReturn(true).`when`(client).isReady()
		stubGenerate { "\u201CA dog barked.\u201D" }
		val events = listOf(event(id = 42, label = "Dog bark"))

		val result = rewriter.rewrite(events)

		assertEquals("A dog barked.", result[42L])
	}

	private suspend fun stubGenerate(supplier: () -> String) {
		doAnswer { supplier() }.`when`(client).generate(anyString())
	}

	private fun event(id: Long, label: String) = AudioEvent(
		id = id,
		date = LocalDate.of(2026, 4, 14),
		timestamp = 0L,
		durationMs = 1000L,
		label = label,
		confidence = 0.87f,
		naturalDescription = null,
		rewriteState = RewriteState.PENDING,
		audioFilePath = null,
	)
}

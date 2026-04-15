/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.service

import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEvent
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEventDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadata
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.RewriteState
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegment
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegmentDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.AudioEventClassifier
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.AudioEventLabel
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.ClassifierNotReadyException
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.EngineNotReadyException
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.TranscriptionEngine
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.TranscriptionResult
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.VoiceActivityDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.LocalDate
import java.time.ZoneOffset

// Kotlin-friendly any() — registers Mockito's any() matcher and returns a non-null
// placeholder so non-nullable Kotlin parameters don't NPE.
private fun <T> any(): T {
	ArgumentMatchers.any<T>()
	return uninitialized()
}

private fun anyFloatArray(): FloatArray {
	ArgumentMatchers.any(FloatArray::class.java)
	return FloatArray(0)
}

@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

@OptIn(ExperimentalCoroutinesApi::class)
class ChunkDispatcherTest {

	private lateinit var vad: VoiceActivityDetector
	private lateinit var classifier: AudioEventClassifier
	private lateinit var transcriber: TranscriptionEngine
	private lateinit var transcriptDao: TranscriptSegmentDao
	private lateinit var audioEventDao: AudioEventDao
	private lateinit var metadataDao: DailyMetadataDao

	private val sampleRate = 16_000
	private val chunkDurationMs = 30_000L
	private val windowSamples = (sampleRate * chunkDurationMs / 1000L).toInt()

	// Use a fixed UTC zone + clock so LocalDate derivation is deterministic.
	private val zone = ZoneOffset.UTC
	// 2026-01-02 00:00:00 UTC in millis.
	private val baseTs = 1767312000_000L
	private var currentTs = baseTs

	@Before
	fun setUp() {
		vad = mock(VoiceActivityDetector::class.java)
		classifier = mock(AudioEventClassifier::class.java)
		transcriber = mock(TranscriptionEngine::class.java)
		transcriptDao = mock(TranscriptSegmentDao::class.java)
		audioEventDao = mock(AudioEventDao::class.java)
		metadataDao = mock(DailyMetadataDao::class.java)
		currentTs = baseTs
		// Default frameSize — Silero's 512-sample frame. Individual tests override as needed.
		doAnswer { 512 }.`when`(vad).frameSize()
	}

	private fun newDispatcher(): ChunkDispatcher = ChunkDispatcher(
		vad = vad,
		classifier = classifier,
		transcriber = transcriber,
		transcriptDao = transcriptDao,
		audioEventDao = audioEventDao,
		metadataDao = metadataDao,
		clock = { currentTs },
		zoneId = zone,
		chunkDurationMs = chunkDurationMs,
		sampleRate = sampleRate,
	)

	/**
	 * Emits [count] back-to-back full windows, advancing the injected clock per window so
	 * the dispatcher's `endTs = clock()` call returns the post-window timestamp.
	 */
	private fun audioFlow(count: Int): Flow<FloatArray> = flow {
		repeat(count) {
			currentTs += chunkDurationMs
			emit(FloatArray(windowSamples) { 0f })
		}
	}

	private suspend fun stubVadSpeech(detected: Boolean) {
		doAnswer { detected }.`when`(vad).detectSpeech(anyFloatArray())
	}

	private suspend fun stubClassifier(labels: List<AudioEventLabel>) {
		doAnswer { labels }.`when`(classifier)
			.classify(anyFloatArray(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyFloat())
	}

	private suspend fun stubTranscribe(result: TranscriptionResult) {
		doAnswer { result }.`when`(transcriber).transcribe(anyFloatArray(), ArgumentMatchers.anyString())
	}

	@Test
	fun `vad false and classifier empty writes nothing`() = runTest(StandardTestDispatcher()) {
		stubVadSpeech(false)
		stubClassifier(emptyList())
		doAnswer { null }.`when`(metadataDao).getByDate(any())
		val metadataCaptures = mutableListOf<DailyMetadata>()
		doAnswer {
			metadataCaptures.add(it.arguments[0] as DailyMetadata)
			null
		}.`when`(metadataDao).upsert(any())

		newDispatcher().run(audioFlow(1))

		verify(transcriptDao, never()).insert(any())
		verify(audioEventDao, never()).insert(any())
		assertEquals(1, metadataCaptures.size)
		val md = metadataCaptures[0]
		assertEquals(0, md.totalSegments)
		assertEquals(0, md.totalWordCount)
		assertEquals(chunkDurationMs, md.totalDurationMs)
		assertNull(md.firstSegmentTime)
	}

	@Test
	fun `vad true but transcriber not ready skips transcript and still writes events`() =
		runTest(StandardTestDispatcher()) {
			stubVadSpeech(true)
			doThrow(EngineNotReadyException("not ready")).`when`(transcriber)
				.transcribe(anyFloatArray(), ArgumentMatchers.anyString())
			stubClassifier(listOf(AudioEventLabel("Dog", 0.9f)))
			doAnswer { null }.`when`(metadataDao).getByDate(any())

			newDispatcher().run(audioFlow(1))

			verify(transcriptDao, never()).insert(any())
			verify(audioEventDao).insert(any())
		}

	@Test
	fun `vad true and transcriber returns text writes segment`() = runTest(StandardTestDispatcher()) {
		stubVadSpeech(true)
		stubTranscribe(TranscriptionResult("hello world", 0.87f, 12L))
		stubClassifier(emptyList())
		doAnswer { null }.`when`(metadataDao).getByDate(any())
		val segs = mutableListOf<TranscriptSegment>()
		doAnswer {
			segs.add(it.arguments[0] as TranscriptSegment)
			1L
		}.`when`(transcriptDao).insert(any())

		newDispatcher().run(audioFlow(1))

		assertEquals(1, segs.size)
		val seg = segs[0]
		assertEquals("hello world", seg.text)
		assertEquals(0.87f, seg.confidence, 1e-6f)
		assertEquals(2, seg.wordCount)
		assertEquals(chunkDurationMs, seg.durationMs)
		assertEquals(LocalDate.of(2026, 1, 2), seg.date)
	}

	@Test
	fun `classifier returns two labels writes two pending audio events`() =
		runTest(StandardTestDispatcher()) {
			stubVadSpeech(false)
			stubClassifier(
				listOf(
					AudioEventLabel("Dog", 0.82f),
					AudioEventLabel("Bark", 0.74f),
				)
			)
			doAnswer { null }.`when`(metadataDao).getByDate(any())
			val events = mutableListOf<AudioEvent>()
			doAnswer {
				events.add(it.arguments[0] as AudioEvent)
				events.size.toLong()
			}.`when`(audioEventDao).insert(any())

			newDispatcher().run(audioFlow(1))

			assertEquals(2, events.size)
			assertTrue(events.all { it.rewriteState == RewriteState.PENDING })
			assertEquals(setOf("Dog", "Bark"), events.map { it.label }.toSet())
		}

	@Test
	fun `daily metadata totals accumulate across multiple windows on same day`() =
		runTest(StandardTestDispatcher()) {
			stubVadSpeech(true)
			stubTranscribe(TranscriptionResult("one two three", 0.5f, 5L))
			stubClassifier(emptyList())

			val captured = mutableListOf<DailyMetadata>()
			doAnswer { captured.lastOrNull() }.`when`(metadataDao).getByDate(any())
			doAnswer {
				captured.add(it.arguments[0] as DailyMetadata)
				null
			}.`when`(metadataDao).upsert(any())

			newDispatcher().run(audioFlow(3))

			val final = captured.last()
			assertEquals(3, final.totalSegments)
			assertEquals(9, final.totalWordCount)
			assertEquals(3 * chunkDurationMs, final.totalDurationMs)
			assertNotNull(final.firstSegmentTime)
			assertNotNull(final.lastSegmentTime)
			assertTrue(final.lastSegmentTime!! > final.firstSegmentTime!!)
		}

	@Test
	fun `transcriber throwing runtime error does not stop subsequent windows`() =
		runTest(StandardTestDispatcher()) {
			stubVadSpeech(true)
			var callCount = 0
			doAnswer {
				callCount++
				if (callCount == 1) throw RuntimeException("boom")
				TranscriptionResult("second", 0.5f, 1L)
			}.`when`(transcriber).transcribe(anyFloatArray(), ArgumentMatchers.anyString())
			stubClassifier(emptyList())
			doAnswer { null }.`when`(metadataDao).getByDate(any())

			newDispatcher().run(audioFlow(2))

			verify(transcriptDao, times(1)).insert(any())
		}

	@Test
	fun `classifier throwing runtime error does not stop subsequent windows`() =
		runTest(StandardTestDispatcher()) {
			stubVadSpeech(false)
			var callCount = 0
			doAnswer {
				callCount++
				if (callCount == 1) throw ClassifierNotReadyException("x")
				listOf(AudioEventLabel("Speech", 0.9f))
			}.`when`(classifier).classify(anyFloatArray(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyFloat())
			doAnswer { null }.`when`(metadataDao).getByDate(any())

			newDispatcher().run(audioFlow(2))

			verify(audioEventDao, times(1)).insert(any())
		}

	@Test
	fun `VAD is called with a frame-size-aligned subset of the 30s window`() =
		runTest(StandardTestDispatcher()) {
			// Silero-style 512-sample frame: 480000 % 512 == 256, so the dispatcher must
			// truncate to 479744 samples before calling detectSpeech.
			doAnswer { 512 }.`when`(vad).frameSize()
			// Capture the FloatArray actually passed to detectSpeech via doAnswer, which is
			// Kotlin-null-safe (unlike ArgumentCaptor.capture() on a non-null primitive array).
			val captured = mutableListOf<FloatArray>()
			doAnswer {
				captured.add(it.arguments[0] as FloatArray)
				false
			}.`when`(vad).detectSpeech(anyFloatArray())
			stubClassifier(emptyList())
			doAnswer { null }.`when`(metadataDao).getByDate(any())

			newDispatcher().run(audioFlow(1))

			assertEquals(1, captured.size)
			val passed = captured[0]
			val expected = (windowSamples / 512) * 512
			assertEquals(expected, passed.size)
			assertEquals(0, passed.size % 512)
			assertTrue(passed.size <= windowSamples)
			assertTrue(windowSamples - passed.size < 512)
		}

	@Test
	fun `injectable clock drives window endTs`() = runTest(StandardTestDispatcher()) {
		stubVadSpeech(true)
		stubTranscribe(TranscriptionResult("hi", 1.0f, 1L))
		stubClassifier(emptyList())
		doAnswer { null }.`when`(metadataDao).getByDate(any())
		val segs = mutableListOf<TranscriptSegment>()
		doAnswer {
			segs.add(it.arguments[0] as TranscriptSegment)
			1L
		}.`when`(transcriptDao).insert(any())

		newDispatcher().run(audioFlow(1))

		assertEquals(1, segs.size)
		val seg = segs[0]
		// Clock starts at baseTs and advances chunkDurationMs before the emission is consumed,
		// so startTs captured at run() entry is baseTs and endTs at window completion is baseTs+30s.
		assertEquals(baseTs, seg.startTimestamp)
		assertEquals(baseTs + chunkDurationMs, seg.endTimestamp)
	}
}

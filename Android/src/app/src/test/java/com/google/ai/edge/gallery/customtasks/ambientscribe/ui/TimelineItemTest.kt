/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.ui

import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEvent
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.RewriteState
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegment
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineItemTest {

	private val date = LocalDate.of(2026, 4, 14)

	private fun seg(
		id: Long,
		startMs: Long,
		endMs: Long = startMs + 60_000L,
		text: String = "t$id",
	): TranscriptSegment = TranscriptSegment(
		id = id,
		date = date,
		startTimestamp = startMs,
		endTimestamp = endMs,
		text = text,
		confidence = 0.9f,
		durationMs = endMs - startMs,
		wordCount = 3,
	)

	private fun evt(
		id: Long,
		tsMs: Long,
		durationMs: Long = 1_000L,
		label: String = "e$id",
	): AudioEvent = AudioEvent(
		id = id,
		date = date,
		timestamp = tsMs,
		durationMs = durationMs,
		label = label,
		confidence = 0.8f,
		rewriteState = RewriteState.PENDING,
	)

	@Test
	fun `empty input returns empty list`() {
		assertTrue(buildTimeline(emptyList(), emptyList()).isEmpty())
	}

	@Test
	fun `only segments are sorted by startTimestamp`() {
		val s1 = seg(1, 3_000)
		val s2 = seg(2, 1_000)
		val s3 = seg(3, 2_000)
		val timeline = buildTimeline(listOf(s1, s2, s3), emptyList())
		// With 1s/2s/3s starts and 1-minute durations, none exceed 15 min gap.
		assertEquals(3, timeline.size)
		assertEquals(s2.id, (timeline[0] as TimelineItem.Segment).segment.id)
		assertEquals(s3.id, (timeline[1] as TimelineItem.Segment).segment.id)
		assertEquals(s1.id, (timeline[2] as TimelineItem.Segment).segment.id)
	}

	@Test
	fun `only events are sorted by timestamp`() {
		val e1 = evt(1, 5_000)
		val e2 = evt(2, 1_000)
		val e3 = evt(3, 3_000)
		val timeline = buildTimeline(emptyList(), listOf(e1, e2, e3))
		assertEquals(3, timeline.size)
		assertEquals(e2.id, (timeline[0] as TimelineItem.Event).event.id)
		assertEquals(e3.id, (timeline[1] as TimelineItem.Event).event.id)
		assertEquals(e1.id, (timeline[2] as TimelineItem.Event).event.id)
	}

	@Test
	fun `interleaves segments and events in timestamp order`() {
		val s1 = seg(1, startMs = 1_000, endMs = 2_000)
		val e1 = evt(10, tsMs = 1_500)
		val s2 = seg(2, startMs = 3_000, endMs = 4_000)
		val e2 = evt(11, tsMs = 2_500)
		val timeline = buildTimeline(listOf(s1, s2), listOf(e1, e2))
		// Expected order by timestamp: s1 (1000), e1 (1500), e2 (2500), s2 (3000). No gaps (<15m).
		assertEquals(4, timeline.size)
		assertEquals(1_000L, timeline[0].timestamp)
		assertTrue(timeline[0] is TimelineItem.Segment)
		assertEquals(1_500L, timeline[1].timestamp)
		assertTrue(timeline[1] is TimelineItem.Event)
		assertEquals(2_500L, timeline[2].timestamp)
		assertTrue(timeline[2] is TimelineItem.Event)
		assertEquals(3_000L, timeline[3].timestamp)
		assertTrue(timeline[3] is TimelineItem.Segment)
	}

	@Test
	fun `inserts Gap when adjacent items exceed 15 minutes`() {
		val s1 = seg(1, startMs = 0L, endMs = 60_000L)
		// Next starts 20 minutes after s1 ends -> gap of 20 * 60_000 ms.
		val s2Start = 60_000L + 20L * 60_000L
		val s2 = seg(2, startMs = s2Start, endMs = s2Start + 60_000L)
		val timeline = buildTimeline(listOf(s1, s2), emptyList())
		assertEquals(3, timeline.size)
		assertTrue(timeline[0] is TimelineItem.Segment)
		val gap = timeline[1] as TimelineItem.Gap
		assertEquals(20L * 60_000L, gap.durationMs)
		assertEquals(60_000L, gap.timestamp) // starts at prevEnd
		assertTrue(timeline[2] is TimelineItem.Segment)
	}

	@Test
	fun `does not insert Gap when gap is exactly or under 15 minutes`() {
		val s1 = seg(1, startMs = 0L, endMs = 60_000L)
		// Next starts exactly 15 min after s1 ends -> gap == threshold, not > threshold.
		val s2Start = 60_000L + 15L * 60_000L
		val s2 = seg(2, startMs = s2Start, endMs = s2Start + 60_000L)
		val timeline = buildTimeline(listOf(s1, s2), emptyList())
		assertEquals(2, timeline.size)
		assertTrue(timeline.none { it is TimelineItem.Gap })
	}

	@Test
	fun `uses segment endTimestamp when computing gap after a segment`() {
		// Segment spans 0..10min. Next item starts at 22min. End-based gap = 12min -> no gap.
		// If the implementation wrongly used startTimestamp, gap would be 22min -> spurious gap.
		val s1 = seg(1, startMs = 0L, endMs = 10L * 60_000L)
		val s2Start = 22L * 60_000L
		val s2 = seg(2, startMs = s2Start, endMs = s2Start + 60_000L)
		val timeline = buildTimeline(listOf(s1, s2), emptyList())
		assertEquals(2, timeline.size)
		assertTrue(timeline.none { it is TimelineItem.Gap })
	}

	@Test
	fun `uses event end when computing gap after an event`() {
		// Event at 0 with 5-min duration; next segment at 21min. End-based gap = 16min -> gap.
		val e1 = evt(10, tsMs = 0L, durationMs = 5L * 60_000L)
		val s1 = seg(1, startMs = 21L * 60_000L, endMs = 22L * 60_000L)
		val timeline = buildTimeline(listOf(s1), listOf(e1))
		assertEquals(3, timeline.size)
		val gap = timeline[1] as TimelineItem.Gap
		assertEquals(16L * 60_000L, gap.durationMs)
		assertEquals(5L * 60_000L, gap.timestamp)
	}
}

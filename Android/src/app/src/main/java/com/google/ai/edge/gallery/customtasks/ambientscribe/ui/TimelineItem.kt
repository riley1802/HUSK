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
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegment

/** Threshold in milliseconds between two adjacent items above which a gap divider is inserted. */
internal const val GAP_THRESHOLD_MS: Long = 15L * 60_000L

/**
 * One row in the Day Detail timeline. Items are ordered by [timestamp] and may be either
 * real data ([Segment], [Event]) or a synthetic [Gap] divider produced by [buildTimeline].
 */
sealed class TimelineItem {
	abstract val timestamp: Long

	data class Segment(val segment: TranscriptSegment) : TimelineItem() {
		override val timestamp: Long get() = segment.startTimestamp
	}

	data class Event(val event: AudioEvent) : TimelineItem() {
		override val timestamp: Long get() = event.timestamp
	}

	data class Gap(val durationMs: Long, override val timestamp: Long) : TimelineItem()
}

/**
 * Merges transcript segments and audio events into a single ascending timeline, inserting a
 * [TimelineItem.Gap] between any two adjacent items separated by more than [GAP_THRESHOLD_MS].
 *
 * The gap is measured from the end of the previous item ([TranscriptSegment.endTimestamp] for
 * segments, `timestamp + durationMs` for events) to the start of the next.
 */
fun buildTimeline(
	segments: List<TranscriptSegment>,
	events: List<AudioEvent>,
): List<TimelineItem> {
	val combined: List<TimelineItem> =
		(segments.map { TimelineItem.Segment(it) } + events.map { TimelineItem.Event(it) })
			.sortedBy { it.timestamp }
	if (combined.isEmpty()) return emptyList()

	val result = mutableListOf<TimelineItem>()
	result.add(combined.first())
	for (i in 1 until combined.size) {
		val prev = combined[i - 1]
		val curr = combined[i]
		val prevEnd = when (prev) {
			is TimelineItem.Segment -> prev.segment.endTimestamp
			is TimelineItem.Event -> prev.event.timestamp + prev.event.durationMs
			is TimelineItem.Gap -> prev.timestamp
		}
		val gap = curr.timestamp - prevEnd
		if (gap > GAP_THRESHOLD_MS) {
			result.add(TimelineItem.Gap(durationMs = gap, timestamp = prevEnd))
		}
		result.add(curr)
	}
	return result
}

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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEvent
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadata
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.RewriteState
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegment
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HEADER_DATE_FORMATTER: DateTimeFormatter =
	DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())

private val TIME_FORMATTER: DateTimeFormatter =
	DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** Formats a duration in ms as e.g. "3h 12m", "12m", or "<1m" for non-zero tiny values. */
internal fun formatDuration(ms: Long): String {
	if (ms <= 0L) return "0m"
	val totalMinutes = ms / 60_000L
	val hours = totalMinutes / 60L
	val minutes = totalMinutes % 60L
	return when {
		hours > 0 -> "${hours}h ${minutes}m"
		totalMinutes > 0 -> "${minutes}m"
		else -> "<1m"
	}
}

/** Formats an epoch-millis timestamp to the device-local HH:mm wall-clock string. */
private fun formatTime(epochMs: Long): String =
	TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

@Composable
fun DayDetailScreen(
	viewModel: DayDetailViewModel = hiltViewModel(),
	onBackClick: () -> Unit,
) {
	val segments by viewModel.transcriptSegments.collectAsState()
	val events by viewModel.audioEvents.collectAsState()
	val metadata by viewModel.metadata.collectAsState()
	val hasPending by viewModel.hasPendingEvents.collectAsState()

	DayDetailContent(
		date = viewModel.date,
		segments = segments,
		events = events,
		metadata = metadata,
		showEnhanceButton = hasPending,
		onEnhanceClick = viewModel::onEnhanceDescriptions,
		onToggleBookmark = viewModel::onToggleBookmark,
		onBackClick = onBackClick,
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayDetailContent(
	date: LocalDate,
	segments: List<TranscriptSegment>,
	events: List<AudioEvent>,
	metadata: DailyMetadata?,
	showEnhanceButton: Boolean,
	onEnhanceClick: () -> Unit,
	onToggleBookmark: (segmentId: Long, currentlyBookmarked: Boolean) -> Unit,
	onBackClick: () -> Unit,
) {
	val timeline = remember(segments, events) { buildTimeline(segments, events) }

	Scaffold(
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = date.format(HEADER_DATE_FORMATTER),
						style = MaterialTheme.typography.titleMedium,
					)
				},
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "Back",
						)
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
			)
		},
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding),
		) {
			StatsSubHeader(metadata = metadata)
			if (showEnhanceButton) {
				Spacer(Modifier.height(4.dp))
				FilledTonalButton(
					onClick = onEnhanceClick,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp),
				) {
					Icon(
						imageVector = Icons.Filled.AutoFixHigh,
						contentDescription = null,
					)
					Spacer(Modifier.width(8.dp))
					Text(text = "Enhance descriptions")
				}
			}
			Spacer(Modifier.height(4.dp))
			HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
			if (timeline.isEmpty()) {
				EmptyTimeline()
			} else {
				LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = androidx.compose.foundation.layout.PaddingValues(
						vertical = 8.dp,
					),
				) {
					items(
						items = timeline,
						key = { item ->
							when (item) {
								is TimelineItem.Segment -> "seg-${item.segment.id}"
								is TimelineItem.Event -> "evt-${item.event.id}"
								is TimelineItem.Gap -> "gap-${item.timestamp}-${item.durationMs}"
							}
						},
					) { item ->
						when (item) {
							is TimelineItem.Segment -> SegmentRow(
								segment = item.segment,
								onToggleBookmark = onToggleBookmark,
							)
							is TimelineItem.Event -> EventRow(event = item.event)
							is TimelineItem.Gap -> GapRow(durationMs = item.durationMs)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun StatsSubHeader(metadata: DailyMetadata?) {
	val words = metadata?.totalWordCount ?: 0
	val duration = formatDuration(metadata?.totalDurationMs ?: 0L)
	Text(
		text = "%,d words \u00B7 %s captured".format(words, duration),
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp),
	)
}

@Composable
private fun EmptyTimeline() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(32.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = "No transcripts or events captured on this day.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SegmentRow(
	segment: TranscriptSegment,
	onToggleBookmark: (segmentId: Long, currentlyBookmarked: Boolean) -> Unit,
) {
	val isLowConfidence = segment.confidence < 0.5f
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = {},
				onLongClick = { onToggleBookmark(segment.id, segment.isBookmarked) },
			)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.Top,
	) {
		Text(
			text = formatTime(segment.startTimestamp),
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.width(48.dp),
		)
		Spacer(Modifier.width(8.dp))
		Text(
			text = segment.text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			fontStyle = if (isLowConfidence) FontStyle.Italic else FontStyle.Normal,
			modifier = Modifier.weight(1f),
		)
		if (segment.isBookmarked) {
			Spacer(Modifier.width(8.dp))
			Icon(
				imageVector = Icons.Filled.Star,
				contentDescription = "Bookmarked",
				tint = MaterialTheme.colorScheme.primary,
			)
		}
	}
}

@Composable
private fun EventRow(event: AudioEvent) {
	val isPending = event.rewriteState == RewriteState.PENDING
	val body = event.naturalDescription ?: event.label
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = formatTime(event.timestamp),
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.width(48.dp),
		)
		Spacer(Modifier.width(8.dp))
		Icon(
			imageVector = Icons.Filled.GraphicEq,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.width(8.dp))
		Text(
			text = body,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			fontStyle = if (isPending) FontStyle.Italic else FontStyle.Normal,
			fontWeight = FontWeight.Medium,
			modifier = Modifier.weight(1f),
		)
		if (isPending) {
			Spacer(Modifier.width(8.dp))
			Icon(
				imageVector = Icons.Filled.HourglassBottom,
				contentDescription = "Pending rewrite",
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

@Composable
private fun GapRow(durationMs: Long) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.alpha(0.55f)
			.padding(horizontal = 12.dp, vertical = 10.dp),
		horizontalArrangement = Arrangement.Center,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = "\u2500\u2500 ${formatDuration(durationMs)} gap \u2500\u2500",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private fun previewMs(hour: Int, minute: Int): Long {
	val date = LocalDate.of(2026, 4, 14)
	return date.atTime(hour, minute)
		.atZone(ZoneId.systemDefault())
		.toInstant()
		.toEpochMilli()
}

@Preview(showBackground = true, name = "Day detail — populated")
@Composable
private fun PreviewDayDetailPopulated() {
	val date = LocalDate.of(2026, 4, 14)
	val segments = listOf(
		TranscriptSegment(
			id = 1,
			date = date,
			startTimestamp = previewMs(9, 0),
			endTimestamp = previewMs(9, 2),
			text = "Morning stand-up recap: shipped the calendar screen yesterday.",
			confidence = 0.92f,
			isBookmarked = true,
			durationMs = 120_000L,
			wordCount = 9,
		),
		TranscriptSegment(
			id = 2,
			date = date,
			startTimestamp = previewMs(9, 5),
			endTimestamp = previewMs(9, 6),
			text = "Muffled low-confidence line about follow-ups and the plan.",
			confidence = 0.41f,
			durationMs = 60_000L,
			wordCount = 9,
		),
		TranscriptSegment(
			id = 3,
			date = date,
			startTimestamp = previewMs(11, 30),
			endTimestamp = previewMs(11, 32),
			text = "Back from coffee, starting on the day-detail timeline.",
			confidence = 0.88f,
			durationMs = 120_000L,
			wordCount = 9,
		),
	)
	val events = listOf(
		AudioEvent(
			id = 10,
			date = date,
			timestamp = previewMs(9, 3),
			durationMs = 3_000L,
			label = "Door knock",
			confidence = 0.8f,
			naturalDescription = "A sharp knock at the door.",
			rewriteState = RewriteState.DONE,
		),
		AudioEvent(
			id = 11,
			date = date,
			timestamp = previewMs(11, 45),
			durationMs = 5_000L,
			label = "Dog bark",
			confidence = 0.71f,
			rewriteState = RewriteState.PENDING,
		),
	)
	val metadata = DailyMetadata(
		date = date,
		totalDurationMs = (3L * 3600 + 12 * 60) * 1000L,
		totalWordCount = 8_432,
		totalSegments = 3,
	)
	GalleryTheme {
		DayDetailContent(
			date = date,
			segments = segments,
			events = events,
			metadata = metadata,
			showEnhanceButton = true,
			onEnhanceClick = {},
			onToggleBookmark = { _, _ -> },
			onBackClick = {},
		)
	}
}

@Preview(showBackground = true, name = "Day detail — empty")
@Composable
private fun PreviewDayDetailEmpty() {
	GalleryTheme {
		DayDetailContent(
			date = LocalDate.of(2026, 4, 14),
			segments = emptyList(),
			events = emptyList(),
			metadata = null,
			showEnhanceButton = false,
			onEnhanceClick = {},
			onToggleBookmark = { _, _ -> },
			onBackClick = {},
		)
	}
}

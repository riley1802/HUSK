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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadata
import com.google.ai.edge.gallery.customtasks.ambientscribe.service.ServiceState
import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reference max duration for the per-day density bar (8 hours). */
private const val MAX_REFERENCE_MS: Long = 8L * 3_600_000L

private val MONTH_FORMATTER: DateTimeFormatter =
	DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@Composable
fun CalendarScreen(
	onDayClick: (LocalDate) -> Unit,
	onSettingsClick: () -> Unit,
	onBackClick: () -> Unit,
	viewModel: AmbientScribeViewModel = hiltViewModel(),
) {
	val visibleMonth by viewModel.visibleMonth.collectAsState()
	val monthData by viewModel.monthData.collectAsState()
	val serviceState by viewModel.serviceState.collectAsState()

	CalendarScreenContent(
		visibleMonth = visibleMonth,
		monthData = monthData,
		serviceState = serviceState,
		onPrevMonth = viewModel::onPrevMonth,
		onNextMonth = viewModel::onNextMonth,
		onDayClick = onDayClick,
		onSettingsClick = onSettingsClick,
		onBackClick = onBackClick,
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarScreenContent(
	visibleMonth: YearMonth,
	monthData: Map<LocalDate, DailyMetadata>,
	serviceState: ServiceState,
	onPrevMonth: () -> Unit,
	onNextMonth: () -> Unit,
	onDayClick: (LocalDate) -> Unit,
	onSettingsClick: () -> Unit,
	onBackClick: () -> Unit,
	today: LocalDate = LocalDate.now(),
) {
	Scaffold(
		topBar = {
			TopAppBar(
				title = {
					Column {
						Text(
							text = "Ambient Scribe",
							style = MaterialTheme.typography.titleMedium,
						)
						Text(
							text = visibleMonth.format(MONTH_FORMATTER),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				},
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "Back",
						)
					}
				},
				actions = {
					IconButton(onClick = onSettingsClick) {
						Icon(
							imageVector = Icons.Filled.Settings,
							contentDescription = "Settings",
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
				.padding(padding)
				.padding(horizontal = 12.dp),
		) {
			ServiceStatePill(state = serviceState)
			Spacer(Modifier.height(8.dp))
			MonthNavigationHeader(
				visibleMonth = visibleMonth,
				onPrevMonth = onPrevMonth,
				onNextMonth = onNextMonth,
			)
			Spacer(Modifier.height(4.dp))
			DayOfWeekHeaderRow()
			Spacer(Modifier.height(4.dp))
			MonthGrid(
				visibleMonth = visibleMonth,
				monthData = monthData,
				today = today,
				onDayClick = onDayClick,
			)
		}
	}
}

@Composable
private fun ServiceStatePill(state: ServiceState) {
	val (label, dotColor) = when (state) {
		ServiceState.Running -> "Recording" to Color(0xFF2E9E5F)
		ServiceState.Paused -> "Paused" to Color(0xFFCAA12A)
		ServiceState.Idle -> "Idle" to MaterialTheme.colorScheme.onSurfaceVariant
		ServiceState.Initializing -> "Initializing" to MaterialTheme.colorScheme.onSurfaceVariant
		ServiceState.Stopping -> "Stopping" to MaterialTheme.colorScheme.onSurfaceVariant
	}
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.background(
				color = MaterialTheme.colorScheme.surfaceContainer,
				shape = RoundedCornerShape(50),
			)
			.padding(horizontal = 10.dp, vertical = 4.dp),
	) {
		Box(
			modifier = Modifier
				.size(8.dp)
				.background(color = dotColor, shape = CircleShape),
		)
		Spacer(Modifier.width(6.dp))
		Text(
			text = label,
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
private fun MonthNavigationHeader(
	visibleMonth: YearMonth,
	onPrevMonth: () -> Unit,
	onNextMonth: () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		IconButton(onClick = onPrevMonth) {
			Icon(
				imageVector = Icons.Filled.ChevronLeft,
				contentDescription = "Previous month",
			)
		}
		Text(
			text = visibleMonth.format(MONTH_FORMATTER),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold,
		)
		IconButton(onClick = onNextMonth) {
			Icon(
				imageVector = Icons.Filled.ChevronRight,
				contentDescription = "Next month",
			)
		}
	}
}

@Composable
private fun DayOfWeekHeaderRow() {
	// Fixed Sunday-first labels to match the grid layout (Sunday column = 0).
	val labels = listOf("S", "M", "T", "W", "T", "F", "S")
	Row(modifier = Modifier.fillMaxWidth()) {
		labels.forEach { label ->
			Box(
				modifier = Modifier.weight(1f),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = label,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}

/**
 * Sunday-first column index for a [DayOfWeek].
 * java.time has Monday=1..Sunday=7; this maps Sunday=0, Monday=1, ..., Saturday=6.
 */
private fun DayOfWeek.sundayFirstIndex(): Int = this.value % 7

@Composable
private fun MonthGrid(
	visibleMonth: YearMonth,
	monthData: Map<LocalDate, DailyMetadata>,
	today: LocalDate,
	onDayClick: (LocalDate) -> Unit,
) {
	val firstOfMonth = visibleMonth.atDay(1)
	val leadingBlanks = firstOfMonth.dayOfWeek.sundayFirstIndex()
	val daysInMonth = visibleMonth.lengthOfMonth()
	// Always render a 6-row (42 cell) grid for a stable layout.
	val totalCells = 42
	val trailingBlanks = totalCells - leadingBlanks - daysInMonth

	// Build a stable list of cells: null => blank filler, LocalDate => in-month.
	val cells: List<LocalDate?> = buildList(totalCells) {
		repeat(leadingBlanks) { add(null) }
		for (day in 1..daysInMonth) add(visibleMonth.atDay(day))
		repeat(trailingBlanks.coerceAtLeast(0)) { add(null) }
	}

	LazyVerticalGrid(
		columns = GridCells.Fixed(7),
		contentPadding = PaddingValues(vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
		modifier = Modifier.fillMaxWidth(),
	) {
		items(cells.size) { index ->
			val date = cells[index]
			if (date == null) {
				Box(
					modifier = Modifier
						.aspectRatio(1f)
						.alpha(0f),
				)
			} else {
				DayCell(
					date = date,
					metadata = monthData[date],
					isToday = date == today,
					onDayClick = onDayClick,
				)
			}
		}
	}
}

@Composable
private fun DayCell(
	date: LocalDate,
	metadata: DailyMetadata?,
	isToday: Boolean,
	onDayClick: (LocalDate) -> Unit,
) {
	val hasData = metadata != null
	val cellBg = if (isToday) MaterialTheme.colorScheme.surfaceContainerHigh
	else MaterialTheme.colorScheme.surfaceContainer
	val borderColor = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent

	Box(
		modifier = Modifier
			.aspectRatio(1f)
			.background(color = cellBg, shape = RoundedCornerShape(8.dp))
			.border(
				width = if (isToday) 1.dp else 0.dp,
				color = borderColor,
				shape = RoundedCornerShape(8.dp),
			)
			.alpha(if (hasData) 1f else 0.55f)
			.clickable { onDayClick(date) }
			.padding(4.dp),
	) {
		Text(
			text = date.dayOfMonth.toString(),
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.align(Alignment.TopStart),
		)
		DensityBar(
			durationMs = metadata?.totalDurationMs ?: 0L,
			modifier = Modifier.align(Alignment.BottomCenter),
		)
	}
}

@Composable
private fun DensityBar(durationMs: Long, modifier: Modifier = Modifier) {
	val fraction = (durationMs.toFloat() / MAX_REFERENCE_MS.toFloat()).coerceIn(0f, 1f)
	// Track column showing the bar growing upward from the cell's bottom.
	Box(
		modifier = modifier
			.width(6.dp)
			.height(28.dp),
		contentAlignment = Alignment.BottomCenter,
	) {
		Box(
			modifier = Modifier
				.width(6.dp)
				.background(
					color = MaterialTheme.colorScheme.surfaceContainerHighest,
					shape = RoundedCornerShape(3.dp),
				)
				.height(28.dp),
		)
		Box(
			modifier = Modifier
				.width(6.dp)
				.background(
					color = MaterialTheme.colorScheme.primary,
					shape = RoundedCornerShape(3.dp),
				)
				.height((28f * fraction).dp),
		)
	}
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Calendar — empty month")
@Composable
private fun PreviewCalendarEmpty() {
	GalleryTheme {
		CalendarScreenContent(
			visibleMonth = YearMonth.of(2026, 4),
			monthData = emptyMap(),
			serviceState = ServiceState.Idle,
			onPrevMonth = {},
			onNextMonth = {},
			onDayClick = {},
			onSettingsClick = {},
			onBackClick = {},
			today = LocalDate.of(2026, 4, 14),
		)
	}
}

@Preview(showBackground = true, name = "Calendar — half filled")
@Composable
private fun PreviewCalendarHalf() {
	val ym = YearMonth.of(2026, 4)
	val fills = (1..ym.lengthOfMonth()).filter { it % 2 == 0 }
	val data = fills.associate { day ->
		val date = ym.atDay(day)
		date to DailyMetadata(
			date = date,
			totalDurationMs = ((day % 8) * 3_600_000L).coerceAtLeast(5 * 60_000L),
			totalWordCount = day * 42,
			totalSegments = day,
		)
	}
	GalleryTheme {
		CalendarScreenContent(
			visibleMonth = ym,
			monthData = data,
			serviceState = ServiceState.Running,
			onPrevMonth = {},
			onNextMonth = {},
			onDayClick = {},
			onSettingsClick = {},
			onBackClick = {},
			today = LocalDate.of(2026, 4, 14),
		)
	}
}

@Preview(showBackground = true, name = "Calendar — every day")
@Composable
private fun PreviewCalendarFull() {
	val ym = YearMonth.of(2026, 4)
	val data = (1..ym.lengthOfMonth()).associate { day ->
		val date = ym.atDay(day)
		date to DailyMetadata(
			date = date,
			// Vary duration between ~5 minutes and 8 hours.
			totalDurationMs = (5 * 60_000L) + ((day - 1).toLong() * (8L * 3_600_000L - 5L * 60_000L)
				/ (ym.lengthOfMonth() - 1).coerceAtLeast(1)),
			totalWordCount = day * 120,
			totalSegments = day * 3,
		)
	}
	GalleryTheme {
		CalendarScreenContent(
			visibleMonth = ym,
			monthData = data,
			serviceState = ServiceState.Paused,
			onPrevMonth = {},
			onNextMonth = {},
			onDayClick = {},
			onSettingsClick = {},
			onBackClick = {},
			today = LocalDate.of(2026, 4, 14),
		)
	}
}

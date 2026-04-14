/*
 * Copyright 2026 Riley Thomason
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.thermal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ThermalGreen = Color(0xFF4CAF50)
private val ThermalYellow = Color(0xFFFFC107)
private val ThermalOrange = Color(0xFFFF9800)
private val ThermalRed = Color(0xFFF44336)

/**
 * Tiny temperature indicator for the app bar.
 * Shows skin temp with a color-coded dot. Tappable to open detailed popup.
 */
@Composable
fun ThermalMeterChip(modifier: Modifier = Modifier) {
	val context = LocalContext.current
	var showDetail by remember { mutableStateOf(false) }

	val thermalFlow = remember { ThermalMonitor.observe(context, intervalMs = 3000) }
	val snapshot by thermalFlow.collectAsState(initial = null)

	val displayTemp = snapshot?.skinTemp ?: snapshot?.cpuTemp ?: 0f
	val severity = snapshot?.severityLevel ?: 0

	val dotColor by animateColorAsState(
		targetValue = when (severity) {
			0 -> ThermalGreen
			1 -> ThermalYellow
			2 -> ThermalOrange
			else -> ThermalRed
		},
		animationSpec = tween(500),
		label = "thermalColor",
	)

	if (displayTemp > 0f) {
		Row(
			modifier = modifier
				.clip(RoundedCornerShape(8.dp))
				.clickable { showDetail = true }
				.padding(horizontal = 6.dp, vertical = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Box(
				modifier = Modifier
					.size(8.dp)
					.clip(CircleShape)
					.background(dotColor),
			)
			Text(
				"%.0f°".format(displayTemp),
				style = MaterialTheme.typography.labelSmall.copy(
					fontSize = 11.sp,
					fontWeight = FontWeight.Medium,
				),
				color = dotColor,
			)
		}
	}

	if (showDetail && snapshot != null) {
		ThermalDetailSheet(
			snapshot = snapshot!!,
			onDismiss = { showDetail = false },
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThermalDetailSheet(
	snapshot: ThermalMonitor.ThermalSnapshot,
	onDismiss: () -> Unit,
) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surface,
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 24.dp)
				.padding(bottom = 32.dp)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			// Header
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Text(
					"Device Thermals",
					style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
					color = MaterialTheme.colorScheme.onSurface,
				)
				StatusBadge(snapshot.statusLabel, snapshot.severityLevel)
			}

			// Headroom
			if (!snapshot.headroom.isNaN()) {
				HeadroomIndicator(snapshot.headroom)
			}

			// Sensor readings
			Text(
				"Live Sensors",
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.primary,
			)

			snapshot.readings.forEach { reading ->
				SensorRow(reading)
			}

			// Recommendations
			Spacer(modifier = Modifier.height(4.dp))
			Text(
				"Recommendations",
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.primary,
			)

			RecommendationCard(
				title = "Skin Temperature",
				current = snapshot.skinTemp,
				ideal = ThermalMonitor.Recommendations.IDEAL_MAX_SKIN,
				warning = ThermalMonitor.Recommendations.WARNING_SKIN,
				critical = ThermalMonitor.Recommendations.CRITICAL_SKIN,
				advice = snapshot.skinTemp?.let { ThermalMonitor.Recommendations.getSkinAdvice(it) },
			)

			RecommendationCard(
				title = "Battery Temperature",
				current = snapshot.batteryTemp,
				ideal = ThermalMonitor.Recommendations.IDEAL_MAX_BATTERY,
				warning = ThermalMonitor.Recommendations.WARNING_BATTERY,
				critical = ThermalMonitor.Recommendations.CRITICAL_BATTERY,
				advice = snapshot.batteryTemp?.let { ThermalMonitor.Recommendations.getBatteryAdvice(it) },
			)

			// Recommended ranges
			Spacer(modifier = Modifier.height(4.dp))
			Text(
				"Recommended Ranges for AI Inference",
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.primary,
			)

			RangeRow("Skin (ideal)", "< 37°C", ThermalGreen)
			RangeRow("Skin (warm)", "37-40°C", ThermalYellow)
			RangeRow("Skin (hot, throttling)", "40-42°C", ThermalOrange)
			RangeRow("Skin (critical)", "> 45°C", ThermalRed)
			RangeRow("Battery (normal)", "< 35°C", ThermalGreen)
			RangeRow("Battery (caution)", "35-40°C", ThermalYellow)
			RangeRow("Battery (stop inference)", "> 45°C", ThermalRed)

			Spacer(modifier = Modifier.height(16.dp))
		}
	}
}

@Composable
private fun StatusBadge(label: String, severity: Int) {
	val color = when (severity) {
		0 -> ThermalGreen
		1 -> ThermalYellow
		2 -> ThermalOrange
		else -> ThermalRed
	}
	Text(
		label,
		style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
		color = color,
		modifier = Modifier
			.clip(RoundedCornerShape(6.dp))
			.background(color.copy(alpha = 0.15f))
			.padding(horizontal = 8.dp, vertical = 2.dp),
	)
}

@Composable
private fun HeadroomIndicator(headroom: Float) {
	val progress = (headroom / 2f).coerceIn(0f, 1f)
	val color = when {
		headroom < 0.5f -> ThermalGreen
		headroom < 0.8f -> ThermalYellow
		headroom < 1.0f -> ThermalOrange
		else -> ThermalRed
	}

	Column {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(
				"Thermal Headroom",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Text(
				"%.2f".format(headroom),
				style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
				color = color,
			)
		}
		Spacer(modifier = Modifier.height(4.dp))
		LinearProgressIndicator(
			progress = { progress },
			modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
			color = color,
			trackColor = MaterialTheme.colorScheme.surfaceVariant,
		)
		Text(
			if (headroom < 1f) "OK — room before throttling" else "Throttling threshold reached",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.outline,
		)
	}
}

@Composable
private fun SensorRow(reading: ThermalMonitor.ThermalReading) {
	val color = when {
		reading.tempCelsius >= 45f -> ThermalRed
		reading.tempCelsius >= 40f -> ThermalOrange
		reading.tempCelsius >= 37f -> ThermalYellow
		else -> ThermalGreen
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Box(
				modifier = Modifier
					.size(10.dp)
					.clip(CircleShape)
					.background(color),
			)
			Column {
				Text(
					reading.type.label,
					style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
					color = MaterialTheme.colorScheme.onSurface,
				)
				Text(
					reading.name,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.outline,
				)
			}
		}
		Text(
			"%.1f°C".format(reading.tempCelsius),
			style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
			color = color,
		)
	}
}

@Composable
private fun RecommendationCard(
	title: String,
	current: Float?,
	ideal: Float,
	warning: Float,
	critical: Float,
	advice: String?,
) {
	if (current == null) return

	val color = when {
		current >= critical -> ThermalRed
		current >= warning -> ThermalOrange
		current >= ideal -> ThermalYellow
		else -> ThermalGreen
	}

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(10.dp))
			.background(color.copy(alpha = 0.08f))
			.padding(12.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
			Text("%.1f°C".format(current), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = color)
		}
		if (advice != null) {
			Text(advice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
}

@Composable
private fun RangeRow(label: String, range: String, color: Color) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
			Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
		}
		Text(range, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = color)
	}
}

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

package com.google.ai.edge.gallery.ui.audioscribe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * Displays a structured transcript with speaker labels, timestamps, and text.
 * Unknown speakers are tappable for labeling.
 */
@Composable
fun TranscriptView(
	segments: List<TranscriptSegment>,
	onUnknownSpeakerClicked: ((TranscriptSegment) -> Unit)? = null,
) {
	var showFullText by remember { mutableStateOf(false) }

	Column(
		modifier = Modifier.fillMaxWidth(),
		verticalArrangement = Arrangement.spacedBy(2.dp),
	) {
		// Toggle between structured and full text view.
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.End,
		) {
			TextButton(onClick = { showFullText = !showFullText }) {
				Text(
					if (showFullText) "Show speakers" else "Full text",
					style = MaterialTheme.typography.labelSmall,
				)
			}
		}

		if (showFullText) {
			// Plain text view for copy/paste.
			Text(
				text = segments.joinToString(" ") { it.text },
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		} else {
			// Structured view with speaker labels.
			segments.forEach { segment ->
				TranscriptSegmentRow(
					segment = segment,
					onSpeakerClicked = if (segment.speakerId == null && segment.speakerName.startsWith("Unknown")) {
						{ onUnknownSpeakerClicked?.invoke(segment) }
					} else {
						null
					},
				)
			}
		}
	}
}

@Composable
private fun TranscriptSegmentRow(
	segment: TranscriptSegment,
	onSpeakerClicked: (() -> Unit)?,
) {
	val speakerColor = remember(segment.speakerName) {
		getSpeakerColor(segment.speakerName)
	}
	val isUnknown = segment.speakerId == null && segment.speakerName.startsWith("Unknown")

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 2.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.Top,
	) {
		// Speaker badge + timestamp.
		Column(
			horizontalAlignment = Alignment.Start,
		) {
			Text(
				text = segment.speakerName,
				style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
				color = if (isUnknown) MaterialTheme.colorScheme.primary else speakerColor,
				modifier = if (onSpeakerClicked != null) {
					Modifier
						.clip(RoundedCornerShape(4.dp))
						.background(speakerColor.copy(alpha = 0.1f))
						.clickable(onClick = onSpeakerClicked)
						.padding(horizontal = 4.dp, vertical = 1.dp)
				} else {
					Modifier
						.clip(RoundedCornerShape(4.dp))
						.background(speakerColor.copy(alpha = 0.1f))
						.padding(horizontal = 4.dp, vertical = 1.dp)
				},
			)
			Text(
				text = segment.formatTimestamp(),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.outline,
			)
		}

		// Segment text.
		Text(
			text = segment.text,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.weight(1f),
		)
	}
}

/** Generate a stable color for a speaker name by hashing. */
private fun getSpeakerColor(name: String): Color {
	val colors = listOf(
		Color(0xFF1976D2), // Blue
		Color(0xFF388E3C), // Green
		Color(0xFFD32F2F), // Red
		Color(0xFF7B1FA2), // Purple
		Color(0xFFF57C00), // Orange
		Color(0xFF00796B), // Teal
		Color(0xFFC2185B), // Pink
		Color(0xFF455A64), // Blue Grey
	)
	val index = name.hashCode().absoluteValue % colors.size
	return colors[index]
}

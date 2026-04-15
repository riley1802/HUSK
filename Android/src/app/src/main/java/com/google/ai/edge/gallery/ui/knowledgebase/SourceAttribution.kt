package com.google.ai.edge.gallery.ui.knowledgebase

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.data.rag.ChunkResult
import com.google.ai.edge.gallery.ui.theme.customColors

/**
 * Collapsible source attribution panel shown below RAG-grounded chat responses.
 * Collapsed by default, tap to expand.
 */
@Composable
fun SourceAttribution(
	chunks: List<ChunkResult>,
	modifier: Modifier = Modifier,
) {
	if (chunks.isEmpty()) return

	var expanded by remember { mutableStateOf(false) }
	val customColors = MaterialTheme.customColors

	// Deduplicate by document name for the count display
	val uniqueDocCount = chunks.map { it.documentName }.distinct().size

	Column(
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
			.background(MaterialTheme.colorScheme.surfaceContainer)
	) {
		// Toggle header — always visible
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.clickable { expanded = !expanded }
				.padding(horizontal = 16.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			Text(
				text = if (expanded) "▾" else "▸",
				fontSize = 12.sp,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Text(
				text = "Sources · $uniqueDocCount document${if (uniqueDocCount != 1) "s" else ""}",
				fontSize = 12.sp,
				fontWeight = FontWeight.Bold,
				letterSpacing = 0.5.sp,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}

		// Expandable source list
		AnimatedVisibility(
			visible = expanded,
			enter = expandVertically(),
			exit = shrinkVertically(),
		) {
			Column(
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				chunks.forEach { chunk ->
					SourceCard(chunk = chunk, customColors = customColors)
				}
				// Bottom padding
				Box(modifier = Modifier.height(8.dp))
			}
		}
	}
}

@Composable
private fun SourceCard(
	chunk: ChunkResult,
	customColors: com.google.ai.edge.gallery.ui.theme.CustomColors,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		// Accent left bar — brighter for higher relevance
		val barColor = if (chunk.relevanceScore > 0.7f) {
			customColors.linkColor
		} else {
			MaterialTheme.colorScheme.secondary
		}
		Box(
			modifier = Modifier
				.width(4.dp)
				.height(48.dp)
				.clip(RoundedCornerShape(2.dp))
				.background(barColor)
		)

		Column(
			modifier = Modifier.weight(1f),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = chunk.documentName,
					fontSize = 12.sp,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				Text(
					text = "${"%.0f".format(chunk.relevanceScore * 100)}%",
					fontSize = 11.sp,
					fontWeight = FontWeight.SemiBold,
					color = if (chunk.relevanceScore > 0.7f) {
						customColors.linkColor
					} else {
						MaterialTheme.colorScheme.secondary
					},
				)
			}
			Text(
				text = chunk.chunkContent,
				fontSize = 12.sp,
				color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
				lineHeight = 16.sp,
			)
		}
	}
}

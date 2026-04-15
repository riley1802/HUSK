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

package com.google.ai.edge.gallery.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.notes.Note
import com.google.ai.edge.gallery.data.notes.NotesRepository
import com.google.ai.edge.gallery.ui.notes.NotesViewModel
import com.google.ai.edge.gallery.ui.notes.formatRelativeTime
import com.google.ai.edge.gallery.ui.theme.customColors

@Composable
fun HuskNotesCard(
	viewModel: NotesViewModel,
	onCardClick: () -> Unit,
	onSearchClick: () -> Unit,
	onNoteClick: (noteId: String) -> Unit,
) {
	val recentNotes by viewModel.recentNotes.collectAsState()

	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(28.dp))
			.clickable(onClick = onCardClick)
			.border(
				width = 1.dp,
				color = MaterialTheme.colorScheme.outline,
				shape = RoundedCornerShape(28.dp),
			),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.customColors.taskCardBgColor,
		),
	) {
		Column(
			modifier = Modifier.padding(20.dp),
		) {
			// Header
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Icon(
						Icons.Outlined.EditNote,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(24.dp),
					)
					Text(
						"Notes",
						style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
						color = MaterialTheme.colorScheme.onSurface,
					)
				}
				IconButton(
					onClick = onSearchClick,
					modifier = Modifier.size(32.dp),
				) {
					Icon(
						Icons.Outlined.Search,
						contentDescription = "Search notes",
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.size(20.dp),
					)
				}
			}

			Spacer(modifier = Modifier.height(12.dp))

			if (recentNotes.isEmpty()) {
				// Empty state
				Text(
					"Jot down an idea to get started",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.outline,
					modifier = Modifier.padding(vertical = 8.dp),
				)
			} else {
				// Recent notes preview
				Column(
					verticalArrangement = Arrangement.spacedBy(6.dp),
				) {
					recentNotes.forEach { note ->
						NotePreviewRow(
							note = note,
							onClick = { onNoteClick(note.id) },
						)
					}
				}
			}
		}
	}
}

@Composable
private fun NotePreviewRow(note: Note, onClick: () -> Unit) {
	val tags = remember(note.tags) { NotesRepository.parseTags(note.tags) }
	val relativeTime = remember(note.updatedMs) { formatRelativeTime(note.updatedMs) }

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(10.dp))
			.background(MaterialTheme.colorScheme.surface)
			.clickable(onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier.weight(1f),
		) {
			Row(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				if (tags.isNotEmpty()) {
					Text(
						tags.first(),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
					Text(
						"·",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.outline,
					)
				}
				Text(
					relativeTime,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.outline,
				)
			}
			Text(
				note.title,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

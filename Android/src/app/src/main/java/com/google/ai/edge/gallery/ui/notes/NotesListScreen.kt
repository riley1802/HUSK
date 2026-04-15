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

package com.google.ai.edge.gallery.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.notes.MatchType
import com.google.ai.edge.gallery.data.notes.Note
import com.google.ai.edge.gallery.data.notes.NoteSearchResult
import com.google.ai.edge.gallery.data.notes.NotesDefaults
import com.google.ai.edge.gallery.data.notes.NotesRepository
import com.google.ai.edge.gallery.ui.theme.customColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
	viewModel: NotesViewModel,
	navigateUp: () -> Unit,
	navigateToNote: (noteId: String, targetMessageId: String?) -> Unit,
	startWithSearch: Boolean = false,
) {
	val notes by viewModel.notes.collectAsState()
	val uiState by viewModel.uiState.collectAsState()
	val scope = rememberCoroutineScope()

	var showSystemPromptSheet by remember { mutableStateOf(false) }
	var showSearch by remember { mutableStateOf(startWithSearch) }
	val searchFocusRequester = remember { FocusRequester() }

	LaunchedEffect(startWithSearch) {
		if (startWithSearch) {
			showSearch = true
		}
	}

	LaunchedEffect(showSearch) {
		if (showSearch) {
			try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
		}
	}

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			TopAppBar(
				title = { Text("Notes") },
				navigationIcon = {
					IconButton(onClick = navigateUp) {
						Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
					}
				},
				actions = {
					// Model toggle
					SingleChoiceSegmentedButtonRow(
						modifier = Modifier.padding(end = 4.dp),
					) {
						SegmentedButton(
							selected = uiState.selectedModel == NotesDefaults.MODEL_KEY_E2B,
							onClick = { viewModel.setModel(NotesDefaults.MODEL_KEY_E2B) },
							shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
							label = { Text("E2B", style = MaterialTheme.typography.labelSmall) },
						)
						SegmentedButton(
							selected = uiState.selectedModel == NotesDefaults.MODEL_KEY_E4B,
							onClick = { viewModel.setModel(NotesDefaults.MODEL_KEY_E4B) },
							shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
							label = { Text("E4B", style = MaterialTheme.typography.labelSmall) },
						)
					}
					IconButton(onClick = { showSearch = !showSearch }) {
						Icon(Icons.Outlined.Search, contentDescription = "Search")
					}
					IconButton(onClick = { showSystemPromptSheet = true }) {
						Icon(Icons.Outlined.Settings, contentDescription = "Settings")
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
				),
			)
		},
		floatingActionButton = {
			FloatingActionButton(
				onClick = {
					scope.launch {
						val note = viewModel.createNote()
						navigateToNote(note.id, null)
					}
				},
				containerColor = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.background,
			) {
				Icon(Icons.Outlined.Add, contentDescription = "New Note")
			}
		},
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.padding(horizontal = 16.dp),
		) {
			// Search bar
			if (showSearch) {
				OutlinedTextField(
					value = uiState.searchQuery,
					onValueChange = { viewModel.searchNotes(it) },
					modifier = Modifier
						.fillMaxWidth()
						.padding(bottom = 12.dp)
						.focusRequester(searchFocusRequester),
					placeholder = { Text("Search notes...") },
					leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
					trailingIcon = {
						if (uiState.searchQuery.isNotBlank()) {
							IconButton(onClick = { viewModel.clearSearch() }) {
								Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
							}
						}
					},
					singleLine = true,
					shape = RoundedCornerShape(16.dp),
					colors = OutlinedTextFieldDefaults.colors(
						focusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
						unfocusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
						focusedBorderColor = MaterialTheme.colorScheme.primary,
						unfocusedBorderColor = MaterialTheme.colorScheme.outline,
					),
				)
			}

			if (uiState.searchQuery.isNotBlank() && uiState.searchResults.isNotEmpty()) {
				// Search results
				LazyColumn(
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					items(uiState.searchResults, key = { "${it.note.id}_${it.matchedMessage?.id}" }) { result ->
						NoteSearchResultItem(
							result = result,
							onClick = {
								navigateToNote(result.note.id, result.matchedMessage?.id)
							},
						)
					}
				}
			} else if (uiState.searchQuery.isNotBlank() && !uiState.isSearching) {
				// No results
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center,
				) {
					Text(
						"No results found",
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			} else if (notes.isEmpty()) {
				// Empty state
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center,
				) {
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Text(
							"No notes yet",
							style = MaterialTheme.typography.titleMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
						Text(
							"Tap + to jot down an idea",
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.outline,
						)
					}
				}
			} else {
				// Notes list
				LazyColumn(
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					items(notes, key = { it.id }) { note ->
						NoteListItem(
							note = note,
							onClick = { navigateToNote(note.id, null) },
						)
					}
				}
			}
		}
	}

	if (showSystemPromptSheet) {
		NotesSystemPromptSheet(
			viewModel = viewModel,
			onDismiss = { showSystemPromptSheet = false },
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteListItem(note: Note, onClick: () -> Unit) {
	val tags = NotesRepository.parseTags(note.tags)
	val relativeTime = formatRelativeTime(note.updatedMs)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(16.dp))
			.background(MaterialTheme.customColors.taskCardBgColor)
			.clickable(onClick = onClick)
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				note.title,
				style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			Spacer(modifier = Modifier.width(8.dp))
			Text(
				relativeTime,
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.outline,
			)
		}
		if (tags.isNotEmpty()) {
			FlowRow(
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp),
			) {
				tags.forEach { tag ->
					TagChip(tag)
				}
			}
		}
	}
}

@Composable
private fun NoteSearchResultItem(result: NoteSearchResult, onClick: () -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(16.dp))
			.background(MaterialTheme.customColors.taskCardBgColor)
			.clickable(onClick = onClick)
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(
				result.note.title,
				style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			Text(
				when (result.matchType) {
					MatchType.TITLE -> "title"
					MatchType.TAG -> "tag"
					MatchType.MESSAGE -> "message"
				},
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.primary,
			)
		}
		Text(
			result.snippet,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
fun TagChip(tag: String) {
	Text(
		tag,
		style = MaterialTheme.typography.labelSmall,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier
			.clip(RoundedCornerShape(8.dp))
			.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
			.padding(horizontal = 8.dp, vertical = 2.dp),
	)
}

/** Format a timestamp as relative time (e.g. "Today", "Yesterday", "3 days ago"). */
internal fun formatRelativeTime(timestampMs: Long): String {
	val now = System.currentTimeMillis()
	val diff = now - timestampMs
	val minutes = diff / 60_000
	val hours = diff / 3_600_000
	val days = diff / 86_400_000
	return when {
		minutes < 1 -> "Just now"
		minutes < 60 -> "${minutes}m ago"
		hours < 24 -> "${hours}h ago"
		days < 1 -> "Today"
		days < 2 -> "Yesterday"
		days < 7 -> "${days}d ago"
		else -> "${days / 7}w ago"
	}
}

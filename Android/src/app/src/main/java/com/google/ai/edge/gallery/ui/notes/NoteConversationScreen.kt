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

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.notes.NoteMessage
import com.google.ai.edge.gallery.ui.common.MarkdownText
import com.google.ai.edge.gallery.ui.theme.chatDisplayConfig
import com.google.ai.edge.gallery.ui.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteConversationScreen(
	viewModel: NoteConversationViewModel,
	model: Model?,
	noteId: String,
	targetMessageId: String? = null,
	navigateUp: () -> Unit,
) {
	val uiState by viewModel.uiState.collectAsState()
	val messages by viewModel.messages.collectAsState()
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val listState = rememberLazyListState()

	var inputText by remember { mutableStateOf("") }
	var showMenu by remember { mutableStateOf(false) }
	var highlightedMessageId by remember { mutableStateOf<String?>(null) }

	// Load the note on first composition.
	LaunchedEffect(noteId) {
		viewModel.loadNote(noteId)
		if (model != null) {
			viewModel.resetModelSession(model)
		}
	}

	// Scroll to target message if provided (from search).
	LaunchedEffect(targetMessageId, messages) {
		if (targetMessageId != null && messages.isNotEmpty()) {
			val index = messages.indexOfFirst { it.id == targetMessageId }
			if (index >= 0) {
				listState.animateScrollToItem(index)
				highlightedMessageId = targetMessageId
				delay(2000)
				highlightedMessageId = null
			}
		}
	}

	// Auto-scroll to bottom when new messages arrive.
	LaunchedEffect(messages.size, uiState.streamingContent) {
		if (messages.isNotEmpty()) {
			listState.animateScrollToItem(messages.size - 1 + if (uiState.streamingContent.isNotBlank()) 1 else 0)
		}
	}

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			TopAppBar(
				title = {
					val note = uiState.note
					if (note != null) {
						// Editable title
						BasicTextField(
							value = note.title,
							onValueChange = { viewModel.updateTitle(it) },
							singleLine = true,
							textStyle = MaterialTheme.typography.titleMedium.copy(
								color = MaterialTheme.colorScheme.onSurface,
								fontWeight = FontWeight.Medium,
							),
							cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
						)
					}
				},
				navigationIcon = {
					IconButton(onClick = {
						viewModel.closeNote()
						navigateUp()
					}) {
						Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
					}
				},
				actions = {
					Box {
						IconButton(onClick = { showMenu = true }) {
							Icon(Icons.Rounded.MoreVert, contentDescription = "More")
						}
						DropdownMenu(
							expanded = showMenu,
							onDismissRequest = { showMenu = false },
						) {
							DropdownMenuItem(
								text = { Text("Export") },
								leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
								onClick = {
									showMenu = false
									scope.launch {
										val markdown = viewModel.exportNote()
										if (markdown != null) {
											val intent = Intent(Intent.ACTION_SEND).apply {
												type = "text/plain"
												putExtra(Intent.EXTRA_TEXT, markdown)
												putExtra(Intent.EXTRA_SUBJECT, uiState.note?.title ?: "Note")
											}
											context.startActivity(Intent.createChooser(intent, "Share note"))
										}
									}
								},
							)
							DropdownMenuItem(
								text = { Text("Archive") },
								leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
								onClick = {
									showMenu = false
									viewModel.archiveNote()
									navigateUp()
								},
							)
						}
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.background,
				),
			)
		},
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.imePadding(),
		) {
			// Tags display
			val note = uiState.note
			if (note != null) {
				val tags = com.google.ai.edge.gallery.data.notes.NotesRepository.parseTags(note.tags)
				if (tags.isNotEmpty()) {
					Row(
						modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						tags.forEach { tag -> TagChip(tag) }
					}
				}
			}

			// Messages list
			LazyColumn(
				state = listState,
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth()
					.padding(horizontal = 12.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				item { Spacer(modifier = Modifier.height(8.dp)) }

				itemsIndexed(messages, key = { _, msg -> msg.id }) { _, message ->
					if (!message.isThinking) {
						NoteMessageBubble(
							message = message,
							isHighlighted = message.id == highlightedMessageId,
						)
					}
				}

				// Streaming message
				if (uiState.streamingContent.isNotBlank()) {
					item {
						NoteMessageBubble(
							message = NoteMessage(
								id = "streaming",
								noteId = noteId,
								role = "AGENT",
								content = uiState.streamingContent,
								timestampMs = System.currentTimeMillis(),
							),
							isHighlighted = false,
						)
					}
				}

				// Loading indicator
				if (uiState.isPreparing) {
					item {
						Row(
							modifier = Modifier.padding(start = 4.dp, top = 4.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							CircularProgressIndicator(
								modifier = Modifier.size(16.dp),
								strokeWidth = 2.dp,
								color = MaterialTheme.colorScheme.primary,
							)
							Text(
								"Thinking...",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}
				}

				item { Spacer(modifier = Modifier.height(8.dp)) }
			}

			// Error display
			if (uiState.error != null) {
				Text(
					uiState.error!!,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
				)
			}

			// Input bar
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 12.dp, vertical = 8.dp),
				verticalAlignment = Alignment.Bottom,
				horizontalArrangement = Arrangement.spacedBy(8.dp),
			) {
				OutlinedTextField(
					value = inputText,
					onValueChange = { inputText = it },
					modifier = Modifier.weight(1f),
					placeholder = { Text("Jot down an idea...") },
					shape = RoundedCornerShape(24.dp),
					colors = OutlinedTextFieldDefaults.colors(
						focusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
						unfocusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
						focusedBorderColor = MaterialTheme.colorScheme.primary,
						unfocusedBorderColor = MaterialTheme.colorScheme.outline,
					),
					maxLines = 4,
					enabled = !uiState.isGenerating,
				)
				IconButton(
					onClick = {
						if (inputText.isNotBlank() && model != null && !uiState.isGenerating) {
							val text = inputText
							inputText = ""
							viewModel.sendMessage(text, model)
						}
					},
					enabled = inputText.isNotBlank() && model != null && !uiState.isGenerating,
				) {
					Icon(
						Icons.AutoMirrored.Rounded.Send,
						contentDescription = "Send",
						tint = if (inputText.isNotBlank() && !uiState.isGenerating) {
							MaterialTheme.colorScheme.primary
						} else {
							MaterialTheme.colorScheme.outline
						},
					)
				}
			}
		}
	}
}

@Composable
private fun NoteMessageBubble(
	message: NoteMessage,
	isHighlighted: Boolean,
) {
	val isUser = message.role == "USER"
	val config = MaterialTheme.chatDisplayConfig
	val highlightColor by animateColorAsState(
		targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
		animationSpec = tween(durationMillis = 500),
		label = "highlight",
	)

	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
	) {
		Box(
			modifier = Modifier
				.widthIn(max = 320.dp)
				.clip(
					RoundedCornerShape(
						topStart = 16.dp,
						topEnd = 16.dp,
						bottomStart = if (isUser) 16.dp else 4.dp,
						bottomEnd = if (isUser) 4.dp else 16.dp,
					)
				)
				.background(
					if (isUser) MaterialTheme.customColors.userBubbleBgColor
					else MaterialTheme.customColors.agentBubbleBgColor
				)
				.background(highlightColor)
				.padding(config.bubblePaddingInner),
		) {
			if (message.content.isNotBlank()) {
				MarkdownText(
					text = message.content,
					textColor = if (isUser) MaterialTheme.colorScheme.background
					else MaterialTheme.colorScheme.onSurface,
				)
			}
		}
	}
}

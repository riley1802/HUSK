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

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.customColors

private const val TAG = "AudioScribeScreen"

/**
 * Dedicated Audio Scribe screen — purpose-built for transcription.
 * Clean recorder/file-picker UI with transcript displayed below.
 * No chat bubbles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScribeScreen(
	viewModel: AudioScribeViewModel,
	modelManagerViewModel: ModelManagerViewModel? = null,
	navigateUp: () -> Unit,
) {
	val uiState by viewModel.uiState.collectAsState()
	val context = LocalContext.current
	val clipboardManager = LocalClipboardManager.current
	val scrollState = rememberScrollState()

	// Find Gemma E4B for summary generation.
	val gemmaE4b: Model? = remember(modelManagerViewModel) {
		modelManagerViewModel?.let { mm ->
			val allModels = mm.getAllModels()
			viewModel.findGemmaE4b(allModels)
		}
	}

	// Speaker label sheet state.
	var labelingSegment by remember { mutableStateOf<TranscriptSegment?>(null) }

	// File picker.
	val filePicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.StartActivityForResult()
	) { result ->
		if (result.resultCode == android.app.Activity.RESULT_OK) {
			result.data?.data?.let { uri ->
				Log.d(TAG, "Picked file: $uri")
				viewModel.processAudioFile(context, uri, gemmaE4b)
			}
		}
	}

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		topBar = {
			TopAppBar(
				title = {
					Text(
						"Audio Scribe",
						style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
					)
				},
				navigationIcon = {
					IconButton(onClick = navigateUp) {
						Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
					}
				},
				actions = {
					if (uiState.transcriptSegments != null) {
						// New transcription button.
						IconButton(onClick = { viewModel.clearResults() }) {
							Icon(Icons.Outlined.Refresh, contentDescription = "New")
						}
						// Share button.
						IconButton(onClick = {
							val text = uiState.transcriptSegments?.joinToString("\n") { seg ->
								"${seg.speakerName} [${seg.formatTimestamp()}]: ${seg.text}"
							} ?: ""
							val fullText = if (uiState.summaryText != null) {
								"SUMMARY:\n${uiState.summaryText}\n\nTRANSCRIPT:\n$text"
							} else {
								text
							}
							val intent = Intent(Intent.ACTION_SEND).apply {
								type = "text/plain"
								putExtra(Intent.EXTRA_TEXT, fullText)
							}
							context.startActivity(Intent.createChooser(intent, "Share transcript"))
						}) {
							Icon(Icons.Outlined.Share, contentDescription = "Share")
						}
						// Copy button.
						IconButton(onClick = {
							val text = uiState.transcriptSegments?.joinToString("\n") { seg ->
								"${seg.speakerName} [${seg.formatTimestamp()}]: ${seg.text}"
							} ?: ""
							clipboardManager.setText(AnnotatedString(text))
						}) {
							Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
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
				.verticalScroll(scrollState),
		) {
			// Whisper model selector.
			WhisperConfigCard(
				selectedModel = uiState.selectedWhisperModel,
				onModelSelected = { viewModel.selectWhisperModel(it) },
			)

			Spacer(modifier = Modifier.height(8.dp))

			// Error display.
			uiState.error?.let { error ->
				Text(
					error,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
				)
			}

			when {
				uiState.isProcessing -> {
					ProcessingState(phase = uiState.processingPhase ?: "Processing...")
				}
				uiState.transcriptSegments != null -> {
					// Transcript view.
					TranscriptCard(
						segments = uiState.transcriptSegments!!,
						onUnknownSpeakerClicked = { segment ->
							labelingSegment = segment
						},
					)

					// Summary card.
					uiState.summaryText?.let { summary ->
						Spacer(modifier = Modifier.height(12.dp))
						SummaryCard(summary = summary)
					}
				}
				else -> {
					// Empty state — input controls.
					EmptyState(
						onPickFile = {
							val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
								addCategory(Intent.CATEGORY_OPENABLE)
								type = "*/*"
								val mimeTypes = arrayOf("audio/*", "video/*")
								putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
								putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
									.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
									.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
							}
							filePicker.launch(intent)
						},
						onRecord = {
							// TODO: Open recording flow
						},
					)
				}
			}

			Spacer(modifier = Modifier.height(24.dp))
		}
	}

	// Speaker label sheet.
	labelingSegment?.let { segment ->
		SpeakerLabelSheet(
			segment = segment,
			existingProfiles = uiState.speakerProfiles,
			onLabel = { name, existingId ->
				segment.speakerEmbedding?.let { embedding ->
					viewModel.labelSpeaker(embedding, name, existingId)
				}
				labelingSegment = null
			},
			onDismiss = { labelingSegment = null },
		)
	}
}

@Composable
private fun WhisperConfigCard(
	selectedModel: String,
	onModelSelected: (String) -> Unit,
) {
	val options = remember {
		listOf(
			WhisperModelOption("tiny", "Tiny", true),
			WhisperModelOption("base", "Base", true),
			WhisperModelOption("small", "Small", true),
		)
	}

	Card(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 4.dp),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.customColors.taskCardBgColor,
		),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(
				"Whisper",
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
			WhisperModelSelector(
				options = options,
				selectedKey = selectedModel,
				onSelected = onModelSelected,
			)
		}
	}
}

@Composable
private fun EmptyState(
	onPickFile: () -> Unit,
	onRecord: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 32.dp)
			.padding(top = 48.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(24.dp),
	) {
		Box(
			modifier = Modifier
				.size(80.dp)
				.clip(CircleShape)
				.background(MaterialTheme.colorScheme.primaryContainer),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				Icons.Rounded.Mic,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onPrimaryContainer,
				modifier = Modifier.size(40.dp),
			)
		}

		Text(
			"Transcribe any audio or video",
			style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurface,
		)

		Text(
			"Pick a file or record a clip. Supports M4A, MP3, MP4, MKV, WAV, and more. Speaker identification included.",
			style = MaterialTheme.typography.bodyMedium,
			textAlign = TextAlign.Center,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		Spacer(modifier = Modifier.height(8.dp))

		Column(
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			OutlinedButton(
				onClick = onPickFile,
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(16.dp),
			) {
				Icon(
					Icons.Outlined.AudioFile,
					contentDescription = null,
					modifier = Modifier.size(20.dp),
				)
				Text("  Pick audio or video file")
			}

			OutlinedButton(
				onClick = onRecord,
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(16.dp),
			) {
				Icon(
					Icons.Rounded.Mic,
					contentDescription = null,
					modifier = Modifier.size(20.dp),
				)
				Text("  Record a clip")
			}
		}
	}
}

@Composable
private fun ProcessingState(phase: String) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = 80.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		CircularProgressIndicator(
			modifier = Modifier.size(48.dp),
			strokeWidth = 3.dp,
			color = MaterialTheme.colorScheme.primary,
		)
		Text(
			phase,
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
private fun TranscriptCard(
	segments: List<TranscriptSegment>,
	onUnknownSpeakerClicked: (TranscriptSegment) -> Unit,
) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.customColors.taskCardBgColor,
		),
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(
				"Transcript",
				style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(modifier = Modifier.height(12.dp))
			TranscriptView(
				segments = segments,
				onUnknownSpeakerClicked = onUnknownSpeakerClicked,
			)
		}
	}
}

@Composable
private fun SummaryCard(summary: String) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
		),
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(
				"Summary",
				style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
				color = MaterialTheme.colorScheme.primary,
			)
			Spacer(modifier = Modifier.height(8.dp))
			Text(
				summary,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
			)
		}
	}
}

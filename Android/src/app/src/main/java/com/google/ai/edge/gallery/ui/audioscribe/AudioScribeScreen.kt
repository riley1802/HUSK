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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.speaker.Transcription
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.customColors

private const val TAG = "AudioScribeScreen"

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

	var selectedTab by remember { mutableIntStateOf(0) }

	// Find models.
	val allModels = remember(modelManagerViewModel) {
		modelManagerViewModel?.getAllModels() ?: emptyList()
	}
	val gemmaE4b = remember(allModels) { viewModel.findGemmaE4b(allModels) }
	val whisperModel = remember(allModels, uiState.selectedWhisperModel) { viewModel.findWhisperModel(allModels) }

	// Auto-download all 3 Whisper models on screen entry (skips already-downloaded).
	LaunchedEffect(modelManagerViewModel) {
		if (modelManagerViewModel != null) {
			val task = modelManagerViewModel.getTaskById(com.google.ai.edge.gallery.data.BuiltInTaskId.LLM_ASK_AUDIO)
			val whisperNames = listOf("Whisper-Tiny", "Whisper-Base", "Whisper-Small")
			for (model in allModels) {
				if (model.name in whisperNames) {
					val path = model.getPath(context)
					val file = java.io.File(path)
					if (!file.exists()) {
						Log.d(TAG, "Auto-downloading ${model.name}")
						modelManagerViewModel.downloadModel(task, model)
					}
				}
			}
		}
	}

	// Auto-initialize selected Whisper model when available.
	LaunchedEffect(uiState.selectedWhisperModel, whisperModel) {
		if (!uiState.whisperModelReady && !uiState.isInitializing && whisperModel != null) {
			viewModel.initializeWhisperFromModel(context, whisperModel)
		}
	}

	var labelingSegment by remember { mutableStateOf<TranscriptSegment?>(null) }

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
						IconButton(onClick = { viewModel.clearResults() }) {
							Icon(Icons.Outlined.Refresh, contentDescription = "New")
						}
						IconButton(onClick = {
							val text = buildShareText(uiState.transcriptSegments, uiState.summaryText)
							val intent = Intent(Intent.ACTION_SEND).apply {
								type = "text/plain"
								putExtra(Intent.EXTRA_TEXT, text)
							}
							context.startActivity(Intent.createChooser(intent, "Share transcript"))
						}) {
							Icon(Icons.Outlined.Share, contentDescription = "Share")
						}
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
				.padding(innerPadding),
		) {
			// Tabs: Scribe / History
			TabRow(selectedTabIndex = selectedTab) {
				Tab(
					selected = selectedTab == 0,
					onClick = { selectedTab = 0 },
					text = { Text("Scribe") },
				)
				Tab(
					selected = selectedTab == 1,
					onClick = { selectedTab = 1 },
					text = { Text("History") },
					icon = { Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
				)
			}

			when (selectedTab) {
				0 -> ScribeTab(
					uiState = uiState,
					onPickFile = {
						val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
							addCategory(Intent.CATEGORY_OPENABLE)
							type = "*/*"
							putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
							putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
								.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
								.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
						}
						filePicker.launch(intent)
					},
					onModelSelected = { key ->
						viewModel.selectWhisperModel(key)
					},
					onUnknownSpeakerClicked = { labelingSegment = it },
				)
				1 -> HistoryTab(
					transcriptions = uiState.transcriptions,
					onTranscriptionClicked = { id ->
						viewModel.loadTranscription(id)
						selectedTab = 0
					},
					onDeleteClicked = { id -> viewModel.deleteTranscription(id) },
				)
			}
		}
	}

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
private fun ScribeTab(
	uiState: AudioScribeUiState,
	onPickFile: () -> Unit,
	onModelSelected: (String) -> Unit,
	onUnknownSpeakerClicked: (TranscriptSegment) -> Unit,
) {
	val scrollState = rememberScrollState()

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(scrollState),
	) {
		// Whisper model selector.
		WhisperConfigCard(
			selectedModel = uiState.selectedWhisperModel,
			isReady = uiState.whisperModelReady,
			isInitializing = uiState.isInitializing,
			onModelSelected = onModelSelected,
		)

		Spacer(modifier = Modifier.height(8.dp))

		uiState.error?.let { error ->
			Text(
				error,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.error,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
			)
		}

		when {
			uiState.isProcessing -> ProcessingState(
				phase = uiState.processingPhase ?: "Processing...",
				eta = uiState.etaText,
			)
			uiState.transcriptSegments != null -> {
				TranscriptCard(
					segments = uiState.transcriptSegments,
					onUnknownSpeakerClicked = onUnknownSpeakerClicked,
				)
				uiState.summaryText?.let { summary ->
					Spacer(modifier = Modifier.height(12.dp))
					SummaryCard(summary = summary)
				}
			}
			else -> EmptyState(
				onPickFile = onPickFile,
				whisperReady = uiState.whisperModelReady,
			)
		}

		Spacer(modifier = Modifier.height(24.dp))
	}
}

@Composable
private fun HistoryTab(
	transcriptions: List<Transcription>,
	onTranscriptionClicked: (String) -> Unit,
	onDeleteClicked: (String) -> Unit,
) {
	if (transcriptions.isEmpty()) {
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center,
		) {
			Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Icon(
					Icons.Outlined.History,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.outline,
					modifier = Modifier.size(48.dp),
				)
				Spacer(modifier = Modifier.height(12.dp))
				Text(
					"No transcriptions yet",
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.outline,
				)
			}
		}
	} else {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState())
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			transcriptions.forEach { transcription ->
				TranscriptionHistoryItem(
					transcription = transcription,
					onClick = { onTranscriptionClicked(transcription.id) },
					onDelete = { onDeleteClicked(transcription.id) },
				)
			}
		}
	}
}

@Composable
private fun TranscriptionHistoryItem(
	transcription: Transcription,
	onClick: () -> Unit,
	onDelete: () -> Unit,
) {
	val relativeTime = remember(transcription.createdMs) {
		val diff = System.currentTimeMillis() - transcription.createdMs
		when {
			diff < 60_000 -> "Just now"
			diff < 3600_000 -> "${diff / 60_000}m ago"
			diff < 86400_000 -> "${diff / 3600_000}h ago"
			else -> "${diff / 86400_000}d ago"
		}
	}

	val durationText = remember(transcription.durationMs) {
		val totalSec = transcription.durationMs / 1000
		when {
			totalSec < 60 -> "${totalSec}s"
			totalSec < 3600 -> "${totalSec / 60}m ${totalSec % 60}s"
			else -> "${totalSec / 3600}h ${(totalSec % 3600) / 60}m"
		}
	}

	Card(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick),
		shape = RoundedCornerShape(12.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.customColors.taskCardBgColor,
		),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					transcription.title,
					style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						relativeTime,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.outline,
					)
					Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
					Text(
						durationText,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.outline,
					)
					Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
					Text(
						transcription.whisperModel.uppercase(),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
					)
				}
				if (transcription.summary != null) {
					Text(
						transcription.summary,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.padding(top = 4.dp),
					)
				}
			}
			IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
				Icon(
					Icons.Outlined.Delete,
					contentDescription = "Delete",
					tint = MaterialTheme.colorScheme.outline,
					modifier = Modifier.size(18.dp),
				)
			}
		}
	}
}

@Composable
private fun WhisperConfigCard(
	selectedModel: String,
	isReady: Boolean,
	isInitializing: Boolean,
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
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					"Whisper",
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSurface,
				)
				Spacer(modifier = Modifier.width(8.dp))
				if (isInitializing) {
					CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
					Spacer(modifier = Modifier.width(4.dp))
					Text("Loading...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
				} else if (isReady) {
					Text("Ready", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
				} else {
					CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
					Spacer(modifier = Modifier.width(4.dp))
					Text("Downloading...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
				}
			}
			WhisperModelSelector(
				options = options,
				selectedKey = selectedModel,
				onSelected = onModelSelected,
			)
		}
	}
}

@Composable
private fun EmptyState(onPickFile: () -> Unit, whisperReady: Boolean = false) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 32.dp)
			.padding(top = 48.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(24.dp),
	) {
		Box(
			modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
			contentAlignment = Alignment.Center,
		) {
			Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
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

		OutlinedButton(
			onClick = onPickFile,
			modifier = Modifier.fillMaxWidth(),
			shape = RoundedCornerShape(16.dp),
			enabled = whisperReady,
		) {
			Icon(Icons.Outlined.AudioFile, contentDescription = null, modifier = Modifier.size(20.dp))
			Text(if (whisperReady) "  Pick audio or video file" else "  Downloading Whisper models...")
		}
	}
}

@Composable
private fun ProcessingState(phase: String, eta: String?) {
	Column(
		modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
		Text(phase, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
		if (eta != null) {
			Text(eta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
		}
	}
}

@Composable
private fun TranscriptCard(segments: List<TranscriptSegment>, onUnknownSpeakerClicked: (TranscriptSegment) -> Unit) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.customColors.taskCardBgColor),
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text("Transcript", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
			Spacer(modifier = Modifier.height(12.dp))
			TranscriptView(segments = segments, onUnknownSpeakerClicked = onUnknownSpeakerClicked)
		}
	}
}

@Composable
private fun SummaryCard(summary: String) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text("Summary", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary)
			Spacer(modifier = Modifier.height(8.dp))
			Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
		}
	}
}

private fun buildShareText(segments: List<TranscriptSegment>?, summary: String?): String {
	val transcript = segments?.joinToString("\n") { "${it.speakerName} [${it.formatTimestamp()}]: ${it.text}" } ?: ""
	return if (summary != null) "SUMMARY:\n$summary\n\nTRANSCRIPT:\n$transcript" else transcript
}

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ui.theme.customColors

data class WhisperModelInfo(
	val key: String,
	val name: String,
	val size: String,
	val isDownloaded: Boolean,
)

/**
 * First-run setup card shown when no Whisper model is downloaded.
 * Lists available models with sizes and download buttons.
 */
@Composable
fun AudioScribeSetupCard(
	models: List<WhisperModelInfo>,
	onDownload: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	Card(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(20.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.customColors.taskCardBgColor,
		),
	) {
		Column(
			modifier = Modifier.padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Icon(
					Icons.Rounded.Mic,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(32.dp),
				)
				Column {
					Text(
						"Download Transcription Models",
						style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
						color = MaterialTheme.colorScheme.onSurface,
					)
					Text(
						"Download at least one model to get started",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			Spacer(modifier = Modifier.height(4.dp))

			models.forEach { model ->
				ModelDownloadRow(
					model = model,
					onDownload = { onDownload(model.key) },
				)
			}
		}
	}
}

@Composable
private fun ModelDownloadRow(
	model: WhisperModelInfo,
	onDownload: () -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				model.name,
				style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
				color = MaterialTheme.colorScheme.onSurface,
			)
			Text(
				model.size,
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.outline,
			)
		}

		if (model.isDownloaded) {
			Icon(
				Icons.Outlined.CheckCircle,
				contentDescription = "Downloaded",
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(24.dp),
			)
		} else {
			OutlinedButton(onClick = onDownload) {
				Icon(
					Icons.Outlined.Download,
					contentDescription = "Download",
					modifier = Modifier.size(16.dp),
				)
				Text(
					" Download",
					style = MaterialTheme.typography.labelSmall,
				)
			}
		}
	}
}

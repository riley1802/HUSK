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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class WhisperModelOption(
	val key: String,
	val label: String,
	val isDownloaded: Boolean,
)

/**
 * Segmented button for selecting the Whisper transcription model.
 * Only downloaded models are selectable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperModelSelector(
	options: List<WhisperModelOption>,
	selectedKey: String,
	onSelected: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	SingleChoiceSegmentedButtonRow(modifier = modifier.padding(horizontal = 8.dp)) {
		options.forEachIndexed { index, option ->
			SegmentedButton(
				selected = selectedKey == option.key,
				onClick = { if (option.isDownloaded) onSelected(option.key) },
				shape = SegmentedButtonDefaults.itemShape(index, options.size),
				enabled = option.isDownloaded,
			) {
				Text(
					text = option.label,
					style = MaterialTheme.typography.labelSmall,
				)
			}
		}
	}
}

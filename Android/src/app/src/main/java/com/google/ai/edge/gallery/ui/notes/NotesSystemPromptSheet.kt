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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.notes.NotesDefaults
import com.google.ai.edge.gallery.ui.theme.customColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesSystemPromptSheet(
	viewModel: NotesViewModel,
	onDismiss: () -> Unit,
) {
	val uiState by viewModel.uiState.collectAsState()
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

	var e2bPrompt by remember { mutableStateOf(uiState.e2bSystemPrompt) }
	var e4bPrompt by remember { mutableStateOf(uiState.e4bSystemPrompt) }

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
			Text(
				"System Prompts",
				style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
				color = MaterialTheme.colorScheme.onSurface,
			)

			Text(
				"Customize how the AI behaves during brainstorming for each model.",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)

			// E2B prompt
			Column {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Text(
						"Gemma 3n E2B",
						style = MaterialTheme.typography.labelLarge,
						color = MaterialTheme.colorScheme.primary,
					)
					TextButton(onClick = {
						e2bPrompt = NotesDefaults.DEFAULT_E2B_SYSTEM_PROMPT
					}) {
						Text("Reset", style = MaterialTheme.typography.labelSmall)
					}
				}
				OutlinedTextField(
					value = e2bPrompt,
					onValueChange = { e2bPrompt = it },
					modifier = Modifier.fillMaxWidth().height(160.dp),
					textStyle = MaterialTheme.typography.bodySmall,
					shape = RoundedCornerShape(12.dp),
					colors = OutlinedTextFieldDefaults.colors(
						focusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
						unfocusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
						focusedBorderColor = MaterialTheme.colorScheme.primary,
						unfocusedBorderColor = MaterialTheme.colorScheme.outline,
					),
				)
			}

			// E4B prompt
			Column {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Text(
						"Gemma 3n E4B",
						style = MaterialTheme.typography.labelLarge,
						color = MaterialTheme.colorScheme.primary,
					)
					TextButton(onClick = {
						e4bPrompt = NotesDefaults.DEFAULT_E4B_SYSTEM_PROMPT
					}) {
						Text("Reset", style = MaterialTheme.typography.labelSmall)
					}
				}
				OutlinedTextField(
					value = e4bPrompt,
					onValueChange = { e4bPrompt = it },
					modifier = Modifier.fillMaxWidth().height(160.dp),
					textStyle = MaterialTheme.typography.bodySmall,
					shape = RoundedCornerShape(12.dp),
					colors = OutlinedTextFieldDefaults.colors(
						focusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
						unfocusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
						focusedBorderColor = MaterialTheme.colorScheme.primary,
						unfocusedBorderColor = MaterialTheme.colorScheme.outline,
					),
				)
			}

			// Save button
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
			) {
				TextButton(onClick = onDismiss) {
					Text("Cancel")
				}
				TextButton(onClick = {
					viewModel.saveSystemPrompt(NotesDefaults.MODEL_KEY_E2B, e2bPrompt)
					viewModel.saveSystemPrompt(NotesDefaults.MODEL_KEY_E4B, e4bPrompt)
					onDismiss()
				}) {
					Text("Save")
				}
			}

			Spacer(modifier = Modifier.height(16.dp))
		}
	}
}

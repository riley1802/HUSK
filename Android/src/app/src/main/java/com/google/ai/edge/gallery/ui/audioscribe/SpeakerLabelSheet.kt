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

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.data.speaker.SpeakerProfile
import com.google.ai.edge.gallery.ui.theme.customColors

/**
 * Bottom sheet for naming an unknown speaker.
 * Shows a text field for a new name and a list of existing profiles to merge with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerLabelSheet(
	segment: TranscriptSegment,
	existingProfiles: List<SpeakerProfile>,
	onLabel: (name: String, existingProfileId: String?) -> Unit,
	onDismiss: () -> Unit,
) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

	var newName by remember { mutableStateOf("") }
	var selectedProfileId by remember { mutableStateOf<String?>(null) }

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
				"Name This Speaker",
				style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
				color = MaterialTheme.colorScheme.onSurface,
			)

			Text(
				"\"${segment.text.take(80)}${if (segment.text.length > 80) "..." else ""}\"",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)

			// New name input.
			OutlinedTextField(
				value = newName,
				onValueChange = {
					newName = it
					selectedProfileId = null
				},
				modifier = Modifier.fillMaxWidth(),
				label = { Text("Enter a name") },
				placeholder = { Text("e.g., Riley, Alex") },
				singleLine = true,
				shape = RoundedCornerShape(12.dp),
				colors = OutlinedTextFieldDefaults.colors(
					focusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
					unfocusedContainerColor = MaterialTheme.customColors.taskCardBgColor,
					focusedBorderColor = MaterialTheme.colorScheme.primary,
					unfocusedBorderColor = MaterialTheme.colorScheme.outline,
				),
			)

			// Existing profiles to merge with.
			if (existingProfiles.isNotEmpty()) {
				Text(
					"Or merge with existing voice:",
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				Column {
					existingProfiles.forEach { profile ->
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clickable {
									selectedProfileId = profile.id
									newName = ""
								}
								.padding(vertical = 4.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							RadioButton(
								selected = selectedProfileId == profile.id,
								onClick = {
									selectedProfileId = profile.id
									newName = ""
								},
							)
							Column {
								Text(
									profile.name,
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurface,
								)
								Text(
									"${profile.sampleCount} sample${if (profile.sampleCount != 1) "s" else ""}",
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.outline,
								)
							}
						}
					}
				}
			}

			// Action buttons.
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
			) {
				TextButton(onClick = onDismiss) {
					Text("Cancel")
				}
				TextButton(
					onClick = {
						if (selectedProfileId != null) {
							// Merge with existing profile.
							onLabel("", selectedProfileId)
						} else if (newName.isNotBlank()) {
							// Create new profile.
							onLabel(newName.trim(), null)
						}
					},
					enabled = newName.isNotBlank() || selectedProfileId != null,
				) {
					Text("Save")
				}
			}

			Spacer(modifier = Modifier.height(16.dp))
		}
	}
}

/*
 * Copyright 2025 Google LLC
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

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.proto.ChatDensity
import com.google.ai.edge.gallery.proto.FontScale
import com.google.ai.edge.gallery.proto.Theme
import com.google.ai.edge.gallery.ui.common.tos.AppTosDialog
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import com.google.ai.edge.gallery.ui.theme.accentPresets
import com.google.ai.edge.gallery.ui.theme.labelSmallNarrow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
	curThemeOverride: Theme,
	modelManagerViewModel: ModelManagerViewModel,
	onDismissed: () -> Unit,
) {
	var hfToken by remember { mutableStateOf(modelManagerViewModel.getTokenStatusAndData().data) }
	val dateFormatter = remember {
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault())
			.withLocale(Locale.getDefault())
	}
	var customHfToken by remember { mutableStateOf("") }
	var isFocused by remember { mutableStateOf(false) }
	val focusRequester = remember { FocusRequester() }
	val interactionSource = remember { MutableInteractionSource() }
	var showTos by remember { mutableStateOf(false) }

	val repo = modelManagerViewModel.dataStoreRepository

	Dialog(onDismissRequest = onDismissed) {
		val focusManager = LocalFocusManager.current
		Card(
			modifier =
				Modifier.fillMaxWidth().clickable(
					interactionSource = interactionSource,
					indication = null,
				) {
					focusManager.clearFocus()
				},
			shape = RoundedCornerShape(16.dp),
		) {
			Column(
				modifier = Modifier.padding(20.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp),
			) {
				// Dialog title and subtitle.
				Column {
					Text(
						"Settings",
						style = MaterialTheme.typography.titleLarge,
						modifier = Modifier.padding(bottom = 8.dp),
					)
					Text(
						"App version: ${BuildConfig.VERSION_NAME}",
						style = labelSmallNarrow,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.offset(y = (-6).dp),
					)
				}

				Column(
					modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					val context = LocalContext.current

					// ── Appearance section ──────────────────────────────────────
					Column(
						modifier = Modifier.fillMaxWidth(),
						verticalArrangement = Arrangement.spacedBy(12.dp),
					) {
						Text(
							"Appearance",
							style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
						)

						// AMOLED Black Mode toggle.
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Column(modifier = Modifier.weight(1f)) {
								Text("AMOLED black", style = MaterialTheme.typography.bodyMedium)
								Text(
									"True black for OLED screens",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							Switch(
								checked = ThemeSettings.amoledMode.value,
								onCheckedChange = { enabled ->
									ThemeSettings.amoledMode.value = enabled
									repo.saveAmoledMode(enabled)
								},
								colors = SwitchDefaults.colors(
									checkedThumbColor = MaterialTheme.colorScheme.primary,
									checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
								),
							)
						}

						// Accent color picker.
						Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
							Text("Accent color", style = MaterialTheme.typography.bodyMedium)
							Row(
								horizontalArrangement = Arrangement.spacedBy(8.dp),
								modifier = Modifier.fillMaxWidth(),
							) {
								val currentArgb = ThemeSettings.accentColorArgb.value
								accentPresets.forEach { preset ->
									val isSelected = currentArgb == preset.argb
									Box(
										modifier = Modifier
											.size(32.dp)
											.then(
												if (isSelected)
													Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
												else Modifier
											)
											.padding(2.dp)
											.clip(CircleShape)
											.background(preset.color)
											.clickable {
												ThemeSettings.accentColorArgb.value = preset.argb
												repo.saveAccentColor(preset.argb)
											},
										contentAlignment = Alignment.Center,
									) {
										if (isSelected) {
											Icon(
												Icons.Rounded.Check,
												contentDescription = "${preset.name} selected",
												tint = Color.Black,
												modifier = Modifier.size(16.dp),
											)
										}
									}
								}
							}
						}

						// Font size selector.
						Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
							Text("Chat font size", style = MaterialTheme.typography.bodyMedium)
							val currentFontScale = ThemeSettings.fontScale.value
							val fontOptions = listOf(
								FontScale.FONT_SCALE_SMALL to "S",
								FontScale.FONT_SCALE_DEFAULT to "M",
								FontScale.FONT_SCALE_LARGE to "L",
								FontScale.FONT_SCALE_EXTRA_LARGE to "XL",
							)
							SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
								fontOptions.forEachIndexed { index, (scale, label) ->
									SegmentedButton(
										selected = currentFontScale == scale,
										onClick = {
											ThemeSettings.fontScale.value = scale
											repo.saveFontScale(scale)
										},
										shape = SegmentedButtonDefaults.itemShape(index, fontOptions.size),
									) {
										Text(label)
									}
								}
							}
						}

						// Chat density selector.
						Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
							Text("Chat density", style = MaterialTheme.typography.bodyMedium)
							val currentDensity = ThemeSettings.chatDensity.value
							val densityOptions = listOf(
								ChatDensity.CHAT_DENSITY_COMPACT to "Compact",
								ChatDensity.CHAT_DENSITY_COMFORTABLE to "Comfortable",
								ChatDensity.CHAT_DENSITY_SPACIOUS to "Spacious",
							)
							SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
								densityOptions.forEachIndexed { index, (density, label) ->
									SegmentedButton(
										selected = currentDensity == density,
										onClick = {
											ThemeSettings.chatDensity.value = density
											repo.saveChatDensity(density)
										},
										shape = SegmentedButtonDefaults.itemShape(index, densityOptions.size),
									) {
										Text(label)
									}
								}
							}
						}
					}

					HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

					// ── HF Token management ────────────────────────────────────
					Column(
						modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
						verticalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Text(
							"HuggingFace access token",
							style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
						)
						val curHfToken = hfToken
						if (curHfToken != null && curHfToken.accessToken.isNotEmpty()) {
							Text(
								curHfToken.accessToken.substring(0, min(16, curHfToken.accessToken.length)) + "...",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
							Text(
								"Expires at: ${dateFormatter.format(Instant.ofEpochMilli(curHfToken.expiresAtMs))}",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						} else {
							Text(
								"Not available",
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
							Text(
								"The token will be automatically retrieved when a gated model is downloaded",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
							OutlinedButton(
								onClick = {
									modelManagerViewModel.clearAccessToken()
									hfToken = null
								},
								enabled = curHfToken != null,
							) {
								Text("Clear")
							}
							val handleSaveToken = {
								modelManagerViewModel.saveAccessToken(
									accessToken = customHfToken,
									refreshToken = "",
									expiresAt = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10,
								)
								hfToken = modelManagerViewModel.getTokenStatusAndData().data
								focusManager.clearFocus()
							}
							BasicTextField(
								value = customHfToken,
								singleLine = true,
								keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
								keyboardActions = KeyboardActions(onDone = { handleSaveToken() }),
								modifier =
									Modifier.fillMaxWidth()
										.padding(top = 4.dp)
										.focusRequester(focusRequester)
										.onFocusChanged { isFocused = it.isFocused },
								onValueChange = { customHfToken = it },
								textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
								cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
							) { innerTextField ->
								Box(
									modifier =
										Modifier.border(
												width = if (isFocused) 2.dp else 1.dp,
												color =
													if (isFocused) MaterialTheme.colorScheme.primary
													else MaterialTheme.colorScheme.outline,
												shape = CircleShape,
											)
											.height(40.dp),
									contentAlignment = Alignment.CenterStart,
								) {
									Row(verticalAlignment = Alignment.CenterVertically) {
										Box(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
											if (customHfToken.isEmpty()) {
												Text(
													"Enter token manually",
													color = MaterialTheme.colorScheme.onSurfaceVariant,
													style = MaterialTheme.typography.bodySmall,
												)
											}
											innerTextField()
										}
										if (customHfToken.isNotEmpty()) {
											IconButton(modifier = Modifier.offset(x = 1.dp), onClick = handleSaveToken) {
												Icon(
													Icons.Rounded.CheckCircle,
													contentDescription = stringResource(R.string.cd_done_icon),
												)
											}
										}
									}
								}
							}
						}
					}

					// Third party licenses.
					Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
						Text(
							"Third-party libraries",
							style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
						)
						OutlinedButton(
							onClick = {
								val intent = Intent(context, OssLicensesMenuActivity::class.java)
								context.startActivity(intent)
							}
						) {
							Text("View licenses")
						}
					}

					// RAG Settings
					Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
						Text(
							"RAG / Knowledge Base",
							style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
						)
						Text(
							"Auto-retrieve context from uploaded documents when chatting",
							style = labelSmallNarrow,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
						)
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically,
						) {
							Text("Auto-retrieve", style = MaterialTheme.typography.bodyMedium)
							var ragEnabled by remember { mutableStateOf(repo.getRagAutoRetrieve()) }
							Switch(
								checked = ragEnabled,
								onCheckedChange = {
									ragEnabled = it
									repo.setRagAutoRetrieve(it)
								},
								colors = SwitchDefaults.colors(
									checkedThumbColor = MaterialTheme.colorScheme.primary,
									checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
								),
							)
						}
					}

					HorizontalDivider()

					// Tos
					Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
						Text(
							stringResource(R.string.settings_dialog_tos_title),
							style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
						)
						OutlinedButton(onClick = { showTos = true }) {
							Text(stringResource(R.string.settings_dialog_view_app_terms_of_service))
						}
					}
				}

				// Button row.
				Row(
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
					horizontalArrangement = Arrangement.End,
				) {
					Button(onClick = { onDismissed() }) { Text("Close") }
				}
			}
		}
	}

	if (showTos) {
		AppTosDialog(onTosAccepted = { showTos = false }, viewingMode = true)
	}
}

/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Settings entry point for Ambient Scribe.
 *
 * Contains:
 *  1. Status — toggle that starts/stops the foreground service (gated by consent + permissions).
 *  2. Permissions — runtime audio/notification permission state + battery-opt exclusion.
 *  3. Data — destructive "Clear all data" with confirmation.
 *  4. About — legal disclaimer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientScribeSettingsScreen(
	onNavigateBack: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: AmbientScribeSettingsViewModel = hiltViewModel(),
) {
	val context = LocalContext.current
	val snackbarHostState = remember { SnackbarHostState() }
	val scope = rememberCoroutineScope()

	val serviceEnabled by viewModel.serviceEnabled.collectAsStateWithLifecycle()
	val hasAcceptedConsent by viewModel.hasAcceptedConsent.collectAsStateWithLifecycle()

	val recordAudioGranted = rememberHasPermission(Manifest.permission.RECORD_AUDIO)
	val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
		rememberHasPermission(Manifest.permission.POST_NOTIFICATIONS)
	} else {
		true
	}
	val ignoringBatteryOpt = rememberIsIgnoringBatteryOptimizations()

	// State machine for "user tapped enable" — tracks whether we're mid-flow waiting for
	// consent or permissions before finally calling onToggleService(true).
	var pendingEnable by remember { mutableStateOf(false) }
	var showConsentDialog by remember { mutableStateOf(false) }
	var showClearDialog by remember { mutableStateOf(false) }

	val permissionLauncher = rememberLauncherForActivityResult(
		contract = RequestMultiplePermissions(),
	) { result ->
		val micOk = result[Manifest.permission.RECORD_AUDIO] ?: recordAudioGranted
		val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			result[Manifest.permission.POST_NOTIFICATIONS] ?: notificationsGranted
		} else {
			true
		}
		if (pendingEnable && micOk) {
			// Notification permission is nice-to-have; mic is mandatory. If notifs denied we
			// still start the service (the foreground notification shows regardless on API 33+
			// because FGS start bypasses the runtime grant for its own notification).
			pendingEnable = false
			viewModel.onToggleService(true)
			if (!notifOk) {
				scope.launch {
					snackbarHostState.showSnackbar(
						"Notification permission denied; the service will still run.",
					)
				}
			}
		} else if (pendingEnable && !micOk) {
			pendingEnable = false
			scope.launch {
				snackbarHostState.showSnackbar(
					"Recording requires microphone permission. Tap Enable to try again.",
				)
			}
		}
	}

	fun requestRequiredPermissions() {
		val needed = buildList {
			if (!recordAudioGranted) add(Manifest.permission.RECORD_AUDIO)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
				add(Manifest.permission.POST_NOTIFICATIONS)
			}
		}
		if (needed.isEmpty()) {
			pendingEnable = false
			viewModel.onToggleService(true)
		} else {
			permissionLauncher.launch(needed.toTypedArray())
		}
	}

	fun beginEnableFlow() {
		pendingEnable = true
		if (!hasAcceptedConsent) {
			showConsentDialog = true
		} else {
			requestRequiredPermissions()
		}
	}

	Scaffold(
		modifier = modifier,
		topBar = {
			TopAppBar(
				title = { Text("Ambient Scribe Settings") },
				navigationIcon = {
					IconButton(onClick = onNavigateBack) {
						Icon(
							imageVector = Icons.AutoMirrored.Filled.ArrowBack,
							contentDescription = "Back",
						)
					}
				},
			)
		},
		snackbarHost = { SnackbarHost(snackbarHostState) },
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp, vertical = 8.dp),
		) {
			SectionHeader("Status")
			SwitchRow(
				title = "Enable Ambient Scribe",
				subtitle = "Service runs in the background and captures audio continuously.",
				checked = serviceEnabled,
				onCheckedChange = { wantsOn ->
					if (wantsOn) {
						beginEnableFlow()
					} else {
						viewModel.onToggleService(false)
					}
				},
			)

			SectionDivider()

			SectionHeader("Permissions")
			PermissionRow(
				title = "Microphone (RECORD_AUDIO)",
				granted = recordAudioGranted,
				onGrant = {
					permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
				},
			)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				PermissionRow(
					title = "Notifications (POST_NOTIFICATIONS)",
					granted = notificationsGranted,
					onGrant = {
						permissionLauncher.launch(
							arrayOf(Manifest.permission.POST_NOTIFICATIONS),
						)
					},
				)
			}
			PermissionRow(
				title = "Ignore battery optimizations",
				granted = ignoringBatteryOpt,
				subtitle = if (ignoringBatteryOpt) {
					null
				} else {
					"Battery optimization is enabled; the service may be killed."
				},
				onGrant = {
					val intent = Intent(
						Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
						Uri.parse("package:${context.packageName}"),
					)
					try {
						context.startActivity(intent)
					} catch (e: Throwable) {
						scope.launch {
							snackbarHostState.showSnackbar(
								"Unable to open battery-optimization settings.",
							)
						}
					}
				},
			)

			SectionDivider()

			SectionHeader("Data")
			OutlinedButton(
				onClick = { showClearDialog = true },
				modifier = Modifier.fillMaxWidth(),
			) {
				Text("Clear all data")
			}

			SectionDivider()

			SectionHeader("About")
			Text(
				text = "Important: Recording conversations may be subject to federal and " +
					"state wiretapping laws. In many jurisdictions, you must obtain consent " +
					"from all parties before recording a conversation. Ambient Scribe records " +
					"audio from your device's microphone continuously while active. You are " +
					"solely responsible for complying with all applicable laws regarding " +
					"audio recording in your jurisdiction. HUSK does not transmit or store " +
					"any data off your device.",
				style = MaterialTheme.typography.bodySmall,
			)
			Spacer(Modifier.height(24.dp))
		}
	}

	if (showConsentDialog) {
		AmbientScribeConsentDialog(
			onAccept = {
				showConsentDialog = false
				viewModel.onAcceptConsent()
				requestRequiredPermissions()
			},
			onDecline = {
				showConsentDialog = false
				pendingEnable = false
			},
		)
	}

	if (showClearDialog) {
		AlertDialog(
			onDismissRequest = { showClearDialog = false },
			title = { Text("Clear all data?") },
			text = {
				Text(
					"This permanently deletes all transcripts, audio events, and daily " +
						"summaries from this device. This cannot be undone.",
				)
			},
			confirmButton = {
				TextButton(onClick = {
					showClearDialog = false
					viewModel.onClearAllData()
					scope.launch { snackbarHostState.showSnackbar("All data cleared.") }
				}) {
					Text("Delete")
				}
			},
			dismissButton = {
				TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
			},
		)
	}

	// If the user re-enters the screen and the service toggle is on but RECORD_AUDIO was revoked
	// from system Settings, flip the toggle off automatically so persisted state reflects reality.
	LaunchedEffect(serviceEnabled, recordAudioGranted) {
		if (serviceEnabled && !recordAudioGranted) {
			viewModel.onToggleService(false)
			snackbarHostState.showSnackbar(
				"Microphone permission revoked; service stopped.",
			)
		}
	}
}

@Composable
private fun SectionHeader(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleSmall,
		fontWeight = FontWeight.SemiBold,
		modifier = Modifier.padding(vertical = 8.dp),
	)
}

@Composable
private fun SectionDivider() {
	HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun SwitchRow(
	title: String,
	subtitle: String?,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
			Text(title, style = MaterialTheme.typography.bodyLarge)
			if (subtitle != null) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
		Switch(checked = checked, onCheckedChange = onCheckedChange)
	}
}

@Composable
private fun PermissionRow(
	title: String,
	granted: Boolean,
	onGrant: () -> Unit,
	subtitle: String? = null,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
			Text(title, style = MaterialTheme.typography.bodyLarge)
			Text(
				text = if (granted) "Granted" else "Not granted",
				style = MaterialTheme.typography.bodySmall,
				color = if (granted) {
					MaterialTheme.colorScheme.primary
				} else {
					MaterialTheme.colorScheme.error
				},
			)
			if (subtitle != null) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
		if (!granted) {
			TextButton(onClick = onGrant) { Text("Grant") }
		}
	}
}

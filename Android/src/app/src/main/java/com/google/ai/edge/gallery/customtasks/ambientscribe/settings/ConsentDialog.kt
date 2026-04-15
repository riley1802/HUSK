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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * First-run dialog that surfaces the legal disclaimer before enabling Ambient Scribe.
 *
 * Consent must be a deliberate tap on the confirm button; dismissing the dialog (back-press or
 * outside-tap) is treated as a decline so the service is NOT silently enabled.
 */
@Composable
fun AmbientScribeConsentDialog(
	onAccept: () -> Unit,
	onDecline: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDecline,
		title = { Text("Ambient Scribe") },
		text = {
			Column {
				Text(
					"Ambient Scribe captures and transcribes audio from your device's " +
						"microphone continuously while active.",
				)
				Spacer(Modifier.height(12.dp))
				Text("All data stays on your device. Nothing is uploaded or shared.")
				Spacer(Modifier.height(12.dp))
				Text(
					"Important: Recording conversations may be subject to federal and state " +
						"wiretapping laws. In many jurisdictions, you must obtain consent " +
						"from all parties before recording a conversation. You are solely " +
						"responsible for complying with all applicable laws regarding audio " +
						"recording in your jurisdiction. HUSK does not transmit or store any " +
						"data off your device.",
					style = MaterialTheme.typography.bodySmall,
				)
			}
		},
		confirmButton = {
			TextButton(onClick = onAccept) { Text("I understand, enable") }
		},
		dismissButton = {
			TextButton(onClick = onDecline) { Text("Not now") }
		},
	)
}

/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.customtasks.ambientscribe.settings.AmbientScribePreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts the Ambient Scribe foreground service at device boot, but only when:
 *  - the user has enabled the service via [AmbientScribePreferences.serviceEnabled], and
 *  - the RECORD_AUDIO runtime permission is still granted.
 *
 * If either gate is false, this receiver silently returns so we do not surface an unexpected
 * foreground-service notification (or crash due to missing microphone permission) after reboot.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

	@Inject lateinit var prefs: AmbientScribePreferences

	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
		val pendingResult = goAsync()
		CoroutineScope(Dispatchers.IO).launch {
			try {
				val enabled = try {
					prefs.serviceEnabled.first()
				} catch (e: Throwable) {
					Log.w(TAG, "Failed to read serviceEnabled preference; skipping auto-start", e)
					false
				}
				val hasMic = ContextCompat.checkSelfPermission(
					context,
					Manifest.permission.RECORD_AUDIO,
				) == PackageManager.PERMISSION_GRANTED
				if (!enabled) {
					Log.i(TAG, "Skipping boot auto-start: user preference disabled")
					return@launch
				}
				if (!hasMic) {
					Log.i(TAG, "Skipping boot auto-start: RECORD_AUDIO permission not granted")
					return@launch
				}
				try {
					ContextCompat.startForegroundService(
						context,
						AmbientScribeService.startIntent(context),
					)
				} catch (e: Throwable) {
					Log.e(TAG, "Failed to start AmbientScribeService on boot", e)
				}
			} finally {
				pendingResult.finish()
			}
		}
	}

	companion object {
		private const val TAG = "AmbientScribeBoot"
	}
}

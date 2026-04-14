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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Restarts the Ambient Scribe foreground service at device boot. For Task 5 this is
 * unconditional; a later task will gate auto-start on a user preference stored in DataStore.
 */
class BootReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
		try {
			val startIntent = AmbientScribeService.startIntent(context)
			ContextCompat.startForegroundService(context, startIntent)
		} catch (e: Throwable) {
			Log.e(TAG, "Failed to start AmbientScribeService on boot", e)
		}
	}

	companion object {
		private const val TAG = "AmbientScribeBoot"
	}
}

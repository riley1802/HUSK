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

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEventDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegmentDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.service.AmbientScribeService
import com.google.ai.edge.gallery.customtasks.ambientscribe.work.RewriteScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backing ViewModel for the Ambient Scribe settings screen.
 *
 * Responsibilities:
 *  - surface persisted toggles (service-enabled, consent-accepted) as [StateFlow]s for Compose;
 *  - when the service toggle flips, start/stop the foreground service and the periodic
 *    auto-rewrite job in lockstep so UI state cannot drift from runtime state;
 *  - expose destructive data-clear as a single suspending action.
 */
@HiltViewModel
class AmbientScribeSettingsViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val transcriptDao: TranscriptSegmentDao,
	private val audioEventDao: AudioEventDao,
	private val metadataDao: DailyMetadataDao,
	private val prefs: AmbientScribePreferences,
	private val rewriteScheduler: RewriteScheduler,
) : ViewModel() {

	val serviceEnabled: StateFlow<Boolean> = prefs.serviceEnabled.stateIn(
		viewModelScope, SharingStarted.Eagerly, false,
	)

	val hasAcceptedConsent: StateFlow<Boolean> = prefs.hasAcceptedConsent.stateIn(
		viewModelScope, SharingStarted.Eagerly, false,
	)

	fun onToggleService(enabled: Boolean) {
		viewModelScope.launch {
			prefs.setServiceEnabled(enabled)
			if (enabled) {
				// Guard the Android-framework calls so a misbehaving platform (e.g. during
				// tests or a malformed Intent construction) cannot prevent the scheduler
				// and prefs writes from being observed.
				try {
					ContextCompat.startForegroundService(
						context,
						AmbientScribeService.startIntent(context),
					)
				} catch (t: Throwable) {
					Log.w(TAG, "startForegroundService failed", t)
				}
				rewriteScheduler.scheduleAuto()
			} else {
				try {
					context.startService(AmbientScribeService.stopIntent(context))
				} catch (t: Throwable) {
					Log.w(TAG, "startService(stop) failed", t)
				}
				rewriteScheduler.cancelAuto()
			}
		}
	}

	fun onAcceptConsent() {
		viewModelScope.launch { prefs.setHasAcceptedConsent(true) }
	}

	fun onClearAllData() {
		viewModelScope.launch {
			transcriptDao.deleteAll()
			audioEventDao.deleteAll()
			metadataDao.deleteAll()
		}
	}

	companion object {
		private const val TAG = "AmbientScribeSettingsVM"
	}
}

/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around WorkManager that schedules the Ambient Scribe event-rewrite worker.
 *
 * Two entry points:
 *  - [scheduleAuto] / [cancelAuto]: manage the daily periodic job that runs overnight
 *    (requires charging + device-idle).
 *  - [triggerManual]: kicks off a one-shot rewrite for a specific day, useful for a
 *    "Enhance descriptions" button in the UI. Runs without charging/idle constraints so
 *    the user isn't left waiting for the next maintenance window.
 */
@Singleton
class RewriteScheduler @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	/**
	 * Enqueues the periodic auto-rewrite job. Idempotent: calling multiple times keeps the
	 * existing schedule due to [ExistingPeriodicWorkPolicy.KEEP].
	 */
	fun scheduleAuto() {
		val constraints = Constraints.Builder()
			.setRequiresCharging(true)
			.setRequiresDeviceIdle(true)
			.setRequiredNetworkType(NetworkType.NOT_REQUIRED)
			.build()
		val request = PeriodicWorkRequestBuilder<EventRewriteWorker>(1, TimeUnit.DAYS)
			.setConstraints(constraints)
			.addTag(TAG_AUTO)
			.build()
		WorkManager.getInstance(context).enqueueUniquePeriodicWork(
			EventRewriteWorker.UNIQUE_NAME_AUTO,
			ExistingPeriodicWorkPolicy.KEEP,
			request,
		)
	}

	/** Cancels the periodic auto-rewrite job (no-op if it was never scheduled). */
	fun cancelAuto() {
		WorkManager.getInstance(context).cancelUniqueWork(EventRewriteWorker.UNIQUE_NAME_AUTO)
	}

	/**
	 * Triggers a one-shot rewrite for [date]. Runs immediately regardless of charging/idle.
	 *
	 * If a manual rewrite for the same date is already running or queued,
	 * [ExistingWorkPolicy.REPLACE] swaps it for this new request — consistent with a user
	 * tapping "Enhance descriptions" multiple times.
	 */
	fun triggerManual(date: LocalDate) {
		val inputData = workDataOf(EventRewriteWorker.KEY_DATE to date.toString())
		val request = OneTimeWorkRequestBuilder<EventRewriteWorker>()
			.setInputData(inputData)
			.addTag(TAG_MANUAL)
			.build()
		WorkManager.getInstance(context).enqueueUniqueWork(
			EventRewriteWorker.UNIQUE_NAME_MANUAL_PREFIX + date,
			ExistingWorkPolicy.REPLACE,
			request,
		)
	}

	companion object {
		const val TAG_AUTO = "ambient_scribe_rewrite_auto"
		const val TAG_MANUAL = "ambient_scribe_rewrite_manual"
	}
}

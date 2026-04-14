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
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEventDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.RewriteState
import com.google.ai.edge.gallery.customtasks.ambientscribe.llm.EventDescriptionRewriter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * WorkManager worker that rewrites pending audio-event labels into natural-language
 * descriptions via [EventDescriptionRewriter].
 *
 * Two modes:
 *  - **Auto / periodic**: no input data. Rewrites all [RewriteState.PENDING] rows across
 *    all dates. Scheduled with charging + idle constraints by [RewriteScheduler.scheduleAuto].
 *  - **Manual / one-shot**: [KEY_DATE] set to a `LocalDate.toString()`. Rewrites only
 *    pending rows for that day. Scheduled by [RewriteScheduler.triggerManual].
 *
 * Events whose rewrite fails are left at [RewriteState.PENDING] so the next run retries
 * them. A future enhancement could mark them [RewriteState.SKIPPED_LOW_CONF] after a retry
 * budget is exhausted; that is out of scope for this worker.
 */
@HiltWorker
class EventRewriteWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val rewriter: EventDescriptionRewriter,
	private val audioEventDao: AudioEventDao,
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		val rawDate = inputData.getString(KEY_DATE)
		val targetDate: LocalDate? = if (rawDate.isNullOrEmpty()) {
			null
		} else {
			try {
				LocalDate.parse(rawDate)
			} catch (e: DateTimeParseException) {
				Log.w(TAG, "Invalid $KEY_DATE='$rawDate'; failing work", e)
				return Result.failure()
			}
		}

		val pending = if (targetDate != null) {
			audioEventDao.getByState(RewriteState.PENDING, targetDate)
		} else {
			audioEventDao.getAllByState(RewriteState.PENDING)
		}
		if (pending.isEmpty()) {
			Log.d(TAG, "No pending events (date=$targetDate); nothing to do")
			return Result.success()
		}

		val rewrites = rewriter.rewrite(pending)
		Log.d(TAG, "Rewriter returned ${rewrites.size}/${pending.size} descriptions (date=$targetDate)")

		rewrites.forEach { (id, description) ->
			try {
				audioEventDao.updateRewrite(id, description, RewriteState.DONE)
			} catch (t: Throwable) {
				// A single row failure shouldn't tank the whole batch — the row simply stays
				// PENDING and will be retried next run.
				Log.w(TAG, "Failed to persist rewrite for event id=$id", t)
			}
		}

		return Result.success()
	}

	companion object {
		private const val TAG = "EventRewriteWorker"

		/** Optional input-data key: ISO-8601 date (LocalDate.toString()) limiting the scope. */
		const val KEY_DATE = "target_date"

		/** Unique work name for the periodic auto-rewrite job. */
		const val UNIQUE_NAME_AUTO = "ambient_scribe_rewrite_auto"

		/** Prefix for per-day unique manual job names — concatenated with the date string. */
		const val UNIQUE_NAME_MANUAL_PREFIX = "ambient_scribe_rewrite_manual_"
	}
}

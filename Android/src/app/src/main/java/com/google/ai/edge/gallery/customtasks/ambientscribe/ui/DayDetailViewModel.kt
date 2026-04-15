/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEvent
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEventDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadata
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.RewriteState
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegment
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegmentDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.work.RewriteScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Day Detail screen. The target day is supplied via [SavedStateHandle] as an ISO-8601
 * string under the [ARG_DATE] key.
 *
 * Data sources:
 *  - [transcriptSegments] and [audioEvents] are live Flows from their respective DAOs so the
 *    screen updates as the foreground service continues to append rows during a live day.
 *  - [metadata] is a one-shot suspend query wrapped in a Flow (the DAO exposes no Flow variant).
 *  - [hasPendingEvents] is derived from [audioEvents] and drives the "Enhance descriptions" CTA.
 */
@HiltViewModel
class DayDetailViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val transcriptDao: TranscriptSegmentDao,
	audioEventDao: AudioEventDao,
	metadataDao: DailyMetadataDao,
	private val rewriteScheduler: RewriteScheduler,
) : ViewModel() {

	val date: LocalDate = run {
		val raw = savedStateHandle.get<String>(ARG_DATE)
		val parsed = runCatching { raw?.let { LocalDate.parse(it) } }.getOrNull()
		if (parsed == null) {
			Log.w(TAG, "Invalid or missing date nav arg, falling back to today: $raw")
			LocalDate.now()
		} else {
			parsed
		}
	}

	val transcriptSegments: StateFlow<List<TranscriptSegment>> =
		transcriptDao.observeByDate(date)
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

	val audioEvents: StateFlow<List<AudioEvent>> =
		audioEventDao.observeByDate(date)
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

	val metadata: StateFlow<DailyMetadata?> = flow {
		emit(metadataDao.getByDate(date))
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

	/** True while at least one audio event on [date] is still awaiting LLM rewrite. */
	val hasPendingEvents: StateFlow<Boolean> = audioEvents
		.map { events -> events.any { it.rewriteState == RewriteState.PENDING } }
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

	fun onEnhanceDescriptions() {
		rewriteScheduler.triggerManual(date)
	}

	fun onToggleBookmark(segmentId: Long, currentlyBookmarked: Boolean) {
		viewModelScope.launch {
			transcriptDao.setBookmarked(segmentId, !currentlyBookmarked)
		}
	}

	companion object {
		const val ARG_DATE = "date"
		private const val TAG = "DayDetailVM"
	}
}

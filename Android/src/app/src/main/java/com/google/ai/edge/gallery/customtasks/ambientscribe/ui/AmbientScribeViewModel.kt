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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadata
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.service.AmbientScribeServiceController
import com.google.ai.edge.gallery.customtasks.ambientscribe.service.ServiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel backing the monthly Ambient Scribe calendar.
 *
 * Exposes:
 *  - [visibleMonth]: the month currently rendered by the calendar grid.
 *  - [monthData]: per-day metadata for [visibleMonth], re-queried whenever the month changes.
 *  - [serviceState]: live service state from [AmbientScribeServiceController].
 *
 * The [DailyMetadataDao.getMonth] query takes a `YYYY-MM` string; [YearMonth.toString] produces
 * that exact format, so the value is forwarded directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AmbientScribeViewModel @Inject constructor(
	private val metadataDao: DailyMetadataDao,
	private val serviceController: AmbientScribeServiceController,
) : ViewModel() {

	private val _visibleMonth = MutableStateFlow(YearMonth.now())
	val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

	/**
	 * Per-day metadata for [visibleMonth]. Days without recorded data are absent from the map.
	 */
	val monthData: StateFlow<Map<LocalDate, DailyMetadata>> = _visibleMonth
		.flatMapLatest { ym ->
			flow {
				emit(metadataDao.getMonth(ym.toString()).associateBy { it.date })
			}
		}
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

	val serviceState: StateFlow<ServiceState> = serviceController.state

	fun onPrevMonth() {
		_visibleMonth.value = _visibleMonth.value.minusMonths(1)
	}

	fun onNextMonth() {
		_visibleMonth.value = _visibleMonth.value.plusMonths(1)
	}

	fun goToMonth(ym: YearMonth) {
		_visibleMonth.value = ym
	}
}

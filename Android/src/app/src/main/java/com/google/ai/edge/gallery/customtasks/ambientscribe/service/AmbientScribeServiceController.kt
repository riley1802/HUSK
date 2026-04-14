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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide holder for the Ambient Scribe service's observable state.
 *
 * The service updates [state] and [todaySegmentCount] as it runs; the UI layer injects this
 * controller and observes the flows. Kept as a Hilt @Singleton so both the service and
 * ViewModels see the same instance.
 */
@Singleton
class AmbientScribeServiceController @Inject constructor() {

	private val _state = MutableStateFlow(ServiceState.Idle)
	val state: StateFlow<ServiceState> = _state.asStateFlow()

	private val _todaySegmentCount = MutableStateFlow(0)
	val todaySegmentCount: StateFlow<Int> = _todaySegmentCount.asStateFlow()

	fun updateState(newState: ServiceState) {
		_state.value = newState
	}

	fun updateTodaySegmentCount(count: Int) {
		_todaySegmentCount.value = count
	}
}

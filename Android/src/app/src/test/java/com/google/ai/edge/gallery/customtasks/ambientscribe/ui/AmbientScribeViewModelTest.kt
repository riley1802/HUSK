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

import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadata
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.service.AmbientScribeServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class AmbientScribeViewModelTest {

	private lateinit var metadataDao: DailyMetadataDao
	private lateinit var controller: AmbientScribeServiceController
	private val testDispatcher = StandardTestDispatcher()

	@Before
	fun setUp() {
		Dispatchers.setMain(testDispatcher)
		metadataDao = mock(DailyMetadataDao::class.java)
		controller = AmbientScribeServiceController()
		// Default: return empty list for any month query. Stubbing a `suspend fun` must happen
		// inside a coroutine context, hence runBlocking.
		runBlocking {
			doAnswer { emptyList<DailyMetadata>() }
				.`when`(metadataDao).getMonth(ArgumentMatchers.anyString())
		}
	}

	@After
	fun tearDown() {
		Dispatchers.resetMain()
	}

	private fun newViewModel(): AmbientScribeViewModel =
		AmbientScribeViewModel(metadataDao, controller)

	@Test
	fun `visibleMonth defaults to YearMonth now`() = runTest(testDispatcher) {
		val vm = newViewModel()
		assertEquals(YearMonth.now(), vm.visibleMonth.value)
	}

	@Test
	fun `onPrevMonth moves visibleMonth back by one`() = runTest(testDispatcher) {
		val vm = newViewModel()
		val start = vm.visibleMonth.value
		vm.onPrevMonth()
		assertEquals(start.minusMonths(1), vm.visibleMonth.value)
	}

	@Test
	fun `onNextMonth moves visibleMonth forward by one`() = runTest(testDispatcher) {
		val vm = newViewModel()
		val start = vm.visibleMonth.value
		vm.onNextMonth()
		assertEquals(start.plusMonths(1), vm.visibleMonth.value)
	}

	@Test
	fun `goToMonth updates visibleMonth directly`() = runTest(testDispatcher) {
		val vm = newViewModel()
		val target = YearMonth.of(2030, 6)
		vm.goToMonth(target)
		assertEquals(target, vm.visibleMonth.value)
	}

	@Test
	fun `monthData emits mocked DAO result when visibleMonth changes`() =
		runTest(testDispatcher) {
			val targetMonth = YearMonth.of(2026, 4)
			val expected = listOf(
				DailyMetadata(
					date = LocalDate.of(2026, 4, 1),
					totalDurationMs = 123_000L,
					totalWordCount = 42,
					totalSegments = 3,
				),
				DailyMetadata(
					date = LocalDate.of(2026, 4, 15),
					totalDurationMs = 456_000L,
					totalWordCount = 100,
					totalSegments = 7,
				),
			)
			runBlocking {
				doAnswer { expected }.`when`(metadataDao).getMonth("2026-04")
			}

			val vm = newViewModel()
			vm.goToMonth(targetMonth)

			// Subscribe so stateIn activates, then drive the test scheduler until idle.
			val result = vm.monthData.first { it.isNotEmpty() }
			advanceUntilIdle()

			assertEquals(2, result.size)
			assertEquals(expected[0], result[LocalDate.of(2026, 4, 1)])
			assertEquals(expected[1], result[LocalDate.of(2026, 4, 15)])
		}
}

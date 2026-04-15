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

import androidx.lifecycle.SavedStateHandle
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEvent
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEventDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.RewriteState
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegmentDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.work.RewriteScheduler
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class DayDetailViewModelTest {

	private val testDispatcher = StandardTestDispatcher()
	private val date = LocalDate.of(2026, 4, 14)

	private lateinit var transcriptDao: TranscriptSegmentDao
	private lateinit var audioEventDao: AudioEventDao
	private lateinit var metadataDao: DailyMetadataDao
	private lateinit var rewriteScheduler: RewriteScheduler

	private lateinit var audioEventsFlow: MutableStateFlow<List<AudioEvent>>
	private lateinit var segmentsFlow: MutableStateFlow<List<com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegment>>

	@Before
	fun setUp() {
		Dispatchers.setMain(testDispatcher)
		transcriptDao = mock(TranscriptSegmentDao::class.java)
		audioEventDao = mock(AudioEventDao::class.java)
		metadataDao = mock(DailyMetadataDao::class.java)
		rewriteScheduler = mock(RewriteScheduler::class.java)

		audioEventsFlow = MutableStateFlow(emptyList())
		segmentsFlow = MutableStateFlow(emptyList())

		doAnswer { audioEventsFlow }.`when`(audioEventDao).observeByDate(date)
		doAnswer { segmentsFlow }.`when`(transcriptDao).observeByDate(date)
		runBlocking {
			doAnswer { null }.`when`(metadataDao).getByDate(date)
		}
	}

	@After
	fun tearDown() {
		Dispatchers.resetMain()
	}

	private fun newViewModel(): DayDetailViewModel {
		val handle = SavedStateHandle(mapOf(DayDetailViewModel.ARG_DATE to date.toString()))
		return DayDetailViewModel(
			savedStateHandle = handle,
			transcriptDao = transcriptDao,
			audioEventDao = audioEventDao,
			metadataDao = metadataDao,
			rewriteScheduler = rewriteScheduler,
		)
	}

	private fun event(id: Long, state: RewriteState): AudioEvent = AudioEvent(
		id = id,
		date = date,
		timestamp = id * 1_000L,
		durationMs = 1_000L,
		label = "l$id",
		confidence = 0.7f,
		rewriteState = state,
	)

	@Test
	fun `date is parsed from SavedStateHandle`() {
		val vm = newViewModel()
		assertEquals(date, vm.date)
	}

	@Test
	fun `hasPendingEvents is false when no events are PENDING`() = runTest(testDispatcher) {
		audioEventsFlow.value = listOf(
			event(1, RewriteState.DONE),
			event(2, RewriteState.SKIPPED_LOW_CONF),
		)
		val vm = newViewModel()
		// Collect to activate stateIn, then drive scheduler forward.
		val result = vm.hasPendingEvents.first()
		advanceUntilIdle()
		assertFalse(result)
		assertFalse(vm.hasPendingEvents.value)
	}

	@Test
	fun `hasPendingEvents is true when at least one event is PENDING`() =
		runTest(testDispatcher) {
			audioEventsFlow.value = listOf(
				event(1, RewriteState.DONE),
				event(2, RewriteState.PENDING),
			)
			val vm = newViewModel()
			val result = vm.hasPendingEvents.first { it }
			advanceUntilIdle()
			assertTrue(result)
		}

	@Test
	fun `onEnhanceDescriptions delegates to rewriteScheduler triggerManual with date`() {
		val vm = newViewModel()
		vm.onEnhanceDescriptions()
		verify(rewriteScheduler, times(1)).triggerManual(date)
	}

	@Test
	fun `onToggleBookmark calls setBookmarked with inverted flag when currently true`() =
		runTest(testDispatcher) {
			runBlocking {
				doAnswer { Unit }
					.`when`(transcriptDao).setBookmarked(anyLong(), anyBoolean())
			}
			val vm = newViewModel()
			vm.onToggleBookmark(segmentId = 42L, currentlyBookmarked = true)
			advanceUntilIdle()
			verify(transcriptDao, times(1)).setBookmarked(42L, false)
		}

	@Test
	fun `onToggleBookmark calls setBookmarked with inverted flag when currently false`() =
		runTest(testDispatcher) {
			runBlocking {
				doAnswer { Unit }
					.`when`(transcriptDao).setBookmarked(anyLong(), anyBoolean())
			}
			val vm = newViewModel()
			vm.onToggleBookmark(segmentId = 7L, currentlyBookmarked = false)
			advanceUntilIdle()
			verify(transcriptDao, times(1)).setBookmarked(7L, true)
		}
}

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
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEventDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegmentDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.work.RewriteScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Unit tests for [AmbientScribeSettingsViewModel].
 *
 * NOTE: Starting/stopping the foreground service via [ContextCompat.startForegroundService] is
 * hard to mock cleanly at the JVM-test level (it calls into Android internals). These tests
 * therefore verify the observable side-effects we care about — prefs writes and rewrite-
 * scheduler calls — and rely on the companion-object intent helpers being exercised at
 * integration time on-device. Context.startService is also a no-op on the Mockito mock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientScribeSettingsViewModelTest {

	private val testDispatcher = StandardTestDispatcher()

	private lateinit var context: Context
	private lateinit var transcriptDao: TranscriptSegmentDao
	private lateinit var audioEventDao: AudioEventDao
	private lateinit var metadataDao: DailyMetadataDao
	private lateinit var prefs: AmbientScribePreferences
	private lateinit var rewriteScheduler: RewriteScheduler

	private lateinit var serviceEnabledFlow: MutableStateFlow<Boolean>
	private lateinit var consentFlow: MutableStateFlow<Boolean>

	@Before
	fun setUp() {
		Dispatchers.setMain(testDispatcher)
		context = mock(Context::class.java)
		transcriptDao = mock(TranscriptSegmentDao::class.java)
		audioEventDao = mock(AudioEventDao::class.java)
		metadataDao = mock(DailyMetadataDao::class.java)
		prefs = mock(AmbientScribePreferences::class.java)
		rewriteScheduler = mock(RewriteScheduler::class.java)

		serviceEnabledFlow = MutableStateFlow(false)
		consentFlow = MutableStateFlow(false)
		`when`(prefs.serviceEnabled).thenReturn(serviceEnabledFlow)
		`when`(prefs.hasAcceptedConsent).thenReturn(consentFlow)
	}

	@After
	fun tearDown() {
		Dispatchers.resetMain()
	}

	private fun newViewModel(): AmbientScribeSettingsViewModel =
		AmbientScribeSettingsViewModel(
			context = context,
			transcriptDao = transcriptDao,
			audioEventDao = audioEventDao,
			metadataDao = metadataDao,
			prefs = prefs,
			rewriteScheduler = rewriteScheduler,
		)

	@Test
	fun `onAcceptConsent sets hasAcceptedConsent to true via prefs`() = runTest(testDispatcher) {
		val vm = newViewModel()
		vm.onAcceptConsent()
		advanceUntilIdle()
		runBlocking { verify(prefs).setHasAcceptedConsent(true) }
	}

	@Test
	fun `onClearAllData calls deleteAll on all three DAOs`() = runTest(testDispatcher) {
		val vm = newViewModel()
		vm.onClearAllData()
		advanceUntilIdle()
		runBlocking {
			verify(transcriptDao).deleteAll()
			verify(audioEventDao).deleteAll()
			verify(metadataDao).deleteAll()
		}
	}

	@Test
	fun `onToggleService true persists enabled flag and schedules auto-rewrite`() =
		runTest(testDispatcher) {
			consentFlow.value = true
			val vm = newViewModel()
			// Starting the real service would require a ContextCompat static call; the mocked
			// Context tolerates the startForegroundService invocation as a no-op. We only
			// assert on the prefs write and the scheduler call — see class KDoc.
			try {
				vm.onToggleService(true)
				advanceUntilIdle()
			} catch (_: Throwable) {
				// startForegroundService against a mock Context may throw on some Mockito
				// versions; the prefs/scheduler writes happen before that call so we still
				// verify them below.
			}
			runBlocking { verify(prefs).setServiceEnabled(true) }
			verify(rewriteScheduler).scheduleAuto()
		}

	@Test
	fun `onToggleService false persists disabled flag and cancels auto-rewrite`() =
		runTest(testDispatcher) {
			val vm = newViewModel()
			try {
				vm.onToggleService(false)
				advanceUntilIdle()
			} catch (_: Throwable) {
				// context.startService against mock may throw; verify the observable effects.
			}
			runBlocking { verify(prefs).setServiceEnabled(false) }
			verify(rewriteScheduler).cancelAuto()
		}
}

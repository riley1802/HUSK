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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEventDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.DailyMetadataDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.TranscriptSegmentDao
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.AudioEventClassifier
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.TranscriptionEngine
import com.google.ai.edge.gallery.customtasks.ambientscribe.inference.VoiceActivityDetector
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Always-on foreground service that owns the audio-capture + inference pipeline.
 *
 * Lifecycle:
 *  - [ACTION_START] initializes engines (once) and begins capture.
 *  - [ACTION_PAUSE] cancels the capture job; engines stay warm.
 *  - [ACTION_RESUME] restarts capture using the already-warm engines.
 *  - [ACTION_STOP] or [onDestroy] tears down everything and calls [stopSelf].
 *
 * State transitions are published through the injected [AmbientScribeServiceController] so
 * UI observers see updates without binding to the service.
 */
@AndroidEntryPoint
class AmbientScribeService : LifecycleService() {

	@Inject lateinit var transcriptDao: TranscriptSegmentDao
	@Inject lateinit var audioEventDao: AudioEventDao
	@Inject lateinit var metadataDao: DailyMetadataDao
	@Inject lateinit var transcriber: TranscriptionEngine
	@Inject lateinit var vad: VoiceActivityDetector
	@Inject lateinit var classifier: AudioEventClassifier
	@Inject lateinit var controller: AmbientScribeServiceController

	private val pipelineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private val lifecycleMutex = Mutex()

	private var captureJob: Job? = null
	private var enginesInitialized = false
	private var metadataObserverJob: Job? = null

	override fun onCreate() {
		super.onCreate()
		AmbientScribeNotifications.createChannel(this)
		startForegroundCompat(ServiceState.Idle, todaySegmentCount = 0)
		controller.updateState(ServiceState.Idle)
		observeTodaySegmentCount()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)
		val action = intent?.action ?: ACTION_START
		when (action) {
			ACTION_START -> handleStart()
			ACTION_PAUSE -> handlePause()
			ACTION_RESUME -> handleResume()
			ACTION_STOP -> handleStop()
			else -> Log.w(TAG, "Unknown action: $action")
		}
		return START_STICKY
	}

	private fun handleStart() {
		pipelineScope.launch {
			lifecycleMutex.withLock {
				if (captureJob?.isActive == true) return@withLock
				updateState(ServiceState.Initializing)
				if (!enginesInitialized) {
					try {
						coroutineScope {
							launch { vad.initialize() }
							launch { classifier.initialize() }
							launch { transcriber.initialize() }
						}
						enginesInitialized = true
					} catch (e: Throwable) {
						Log.e(TAG, "Engine initialization failed", e)
						updateState(ServiceState.Idle)
						return@withLock
					}
				}
				startCaptureLocked()
			}
		}
	}

	private fun handlePause() {
		pipelineScope.launch {
			lifecycleMutex.withLock {
				captureJob?.cancel()
				captureJob = null
				updateState(ServiceState.Paused)
			}
		}
	}

	private fun handleResume() {
		pipelineScope.launch {
			lifecycleMutex.withLock {
				if (!enginesInitialized) {
					// Nothing to resume — act like a fresh start.
					updateState(ServiceState.Initializing)
					try {
						coroutineScope {
							launch { vad.initialize() }
							launch { classifier.initialize() }
							launch { transcriber.initialize() }
						}
						enginesInitialized = true
					} catch (e: Throwable) {
						Log.e(TAG, "Engine initialization failed during resume", e)
						updateState(ServiceState.Idle)
						return@withLock
					}
				}
				if (captureJob?.isActive != true) {
					startCaptureLocked()
				}
			}
		}
	}

	private fun handleStop() {
		pipelineScope.launch {
			lifecycleMutex.withLock {
				updateState(ServiceState.Stopping)
				// Wait for the capture/dispatcher job to fully unwind before closing engines —
				// otherwise a native inference call can race with session teardown and trigger a
				// JNI use-after-free on Silero / Moonshine.
				captureJob?.cancelAndJoin()
				captureJob = null
				if (enginesInitialized) {
					try {
						vad.close()
					} catch (e: Throwable) {
						Log.w(TAG, "vad.close() failed", e)
					}
					try {
						classifier.close()
					} catch (e: Throwable) {
						Log.w(TAG, "classifier.close() failed", e)
					}
					try {
						transcriber.close()
					} catch (e: Throwable) {
						Log.w(TAG, "transcriber.close() failed", e)
					}
					enginesInitialized = false
				}
				updateState(ServiceState.Idle)
			}
			stopForegroundCompat()
			stopSelf()
		}
	}

	/** Must be called under [lifecycleMutex]. */
	private fun startCaptureLocked() {
		val dispatcher = ChunkDispatcher(
			vad = vad,
			classifier = classifier,
			transcriber = transcriber,
			transcriptDao = transcriptDao,
			audioEventDao = audioEventDao,
			metadataDao = metadataDao,
		)
		val capture = AudioCaptureLoop()
		captureJob = pipelineScope.launch {
			try {
				dispatcher.run(capture.capture())
			} catch (e: MicrophoneUnavailableException) {
				Log.e(TAG, "Microphone unavailable; cannot capture", e)
				updateState(ServiceState.Idle)
			} catch (e: Throwable) {
				Log.e(TAG, "Capture pipeline crashed", e)
				updateState(ServiceState.Idle)
			}
		}
		updateState(ServiceState.Running)
	}

	private fun observeTodaySegmentCount() {
		// Refresh the foreground notification when the per-day segment count changes so the
		// user sees up-to-date totals without opening the app.
		metadataObserverJob?.cancel()
		metadataObserverJob = lifecycleScope.launch {
			controller.todaySegmentCount
				.onEach { count ->
					refreshNotification(controller.state.value, count)
				}
				.launchIn(this)
			controller.state
				.onEach { state ->
					refreshNotification(state, controller.todaySegmentCount.value)
				}
				.launchIn(this)
		}
		// Periodically pull today's segment count so the notification reflects freshly
		// written transcripts (the service does not observe the DAO directly to avoid
		// coupling the service lifecycle to Room's Flow).
		lifecycleScope.launch {
			while (true) {
				try {
					val count = transcriptDao.getByDate(LocalDate.now()).size
					controller.updateTodaySegmentCount(count)
				} catch (e: Throwable) {
					Log.w(TAG, "Failed to refresh today's segment count", e)
				}
				kotlinx.coroutines.delay(COUNT_REFRESH_INTERVAL_MS)
			}
		}
	}

	private fun updateState(newState: ServiceState) {
		controller.updateState(newState)
	}

	private fun refreshNotification(state: ServiceState, count: Int) {
		val notification = AmbientScribeNotifications.buildNotification(this, state, count)
		try {
			NotificationManagerCompat.from(this)
				.notify(AmbientScribeNotifications.NOTIFICATION_ID, notification)
		} catch (e: SecurityException) {
			// POST_NOTIFICATIONS not granted — the foreground notification from onCreate is
			// still visible because startForeground bypasses that permission check.
			Log.w(TAG, "Unable to refresh notification", e)
		}
	}

	private fun startForegroundCompat(state: ServiceState, todaySegmentCount: Int) {
		val notification = AmbientScribeNotifications.buildNotification(this, state, todaySegmentCount)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			startForeground(
				AmbientScribeNotifications.NOTIFICATION_ID,
				notification,
				ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
			)
		} else {
			startForeground(AmbientScribeNotifications.NOTIFICATION_ID, notification)
		}
	}

	private fun stopForegroundCompat() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			stopForeground(Service.STOP_FOREGROUND_REMOVE)
		} else {
			@Suppress("DEPRECATION")
			stopForeground(true)
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		captureJob?.cancel()
		metadataObserverJob?.cancel()
		pipelineScope.cancel()
		controller.updateState(ServiceState.Idle)
	}

	companion object {
		private const val TAG = "AmbientScribeService"
		private const val COUNT_REFRESH_INTERVAL_MS = 5_000L

		const val ACTION_START = "com.google.ai.edge.gallery.customtasks.ambientscribe.ACTION_START"
		const val ACTION_PAUSE = "com.google.ai.edge.gallery.customtasks.ambientscribe.ACTION_PAUSE"
		const val ACTION_RESUME = "com.google.ai.edge.gallery.customtasks.ambientscribe.ACTION_RESUME"
		const val ACTION_STOP = "com.google.ai.edge.gallery.customtasks.ambientscribe.ACTION_STOP"

		fun startIntent(context: Context): Intent = intentForAction(context, ACTION_START)

		fun stopIntent(context: Context): Intent = intentForAction(context, ACTION_STOP)

		fun intentForAction(context: Context, action: String): Intent =
			Intent(context, AmbientScribeService::class.java).setAction(action)
	}
}

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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R

/**
 * Notification plumbing for the always-on Ambient Scribe foreground service.
 *
 * Uses a single low-importance "ongoing service" channel so the persistent notification does
 * not vibrate or interrupt the user.
 */
object AmbientScribeNotifications {
	const val CHANNEL_ID = "ambient_scribe_service"
	const val NOTIFICATION_ID = 0xA11B12
	private const val CHANNEL_NAME = "Ambient Scribe"
	private const val CHANNEL_DESC = "Persistent notification for the always-on transcription service"

	/** Idempotent — creates the channel if it does not already exist. Safe to call repeatedly. */
	fun createChannel(context: Context) {
		val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
			?: return
		val existing = manager.getNotificationChannel(CHANNEL_ID)
		if (existing != null) return
		val channel = NotificationChannel(
			CHANNEL_ID,
			CHANNEL_NAME,
			NotificationManager.IMPORTANCE_LOW,
		).apply {
			description = CHANNEL_DESC
			setShowBadge(false)
		}
		manager.createNotificationChannel(channel)
	}

	/**
	 * Builds the persistent notification with the given state + segment-count label and two
	 * actions: a Pause/Resume toggle and a Stop action, plus a tap intent that opens
	 * [MainActivity].
	 */
	fun buildNotification(
		context: Context,
		state: ServiceState,
		todaySegmentCount: Int,
	): Notification {
		val tapIntent = Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
		}
		val tapPending = PendingIntent.getActivity(
			context,
			0,
			tapIntent,
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
		)

		val contentText = "${state.label()} — today: $todaySegmentCount segments"

		val builder = NotificationCompat.Builder(context, CHANNEL_ID)
			.setContentTitle("Ambient Scribe")
			.setContentText(contentText)
			.setSmallIcon(R.mipmap.ic_launcher)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setCategory(NotificationCompat.CATEGORY_SERVICE)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setContentIntent(tapPending)

		// Pause/Resume toggle action.
		when (state) {
			ServiceState.Running, ServiceState.Initializing -> {
				builder.addAction(
					0,
					"Pause",
					servicePendingIntent(context, AmbientScribeService.ACTION_PAUSE, requestCode = 1),
				)
			}
			ServiceState.Paused -> {
				builder.addAction(
					0,
					"Resume",
					servicePendingIntent(context, AmbientScribeService.ACTION_RESUME, requestCode = 2),
				)
			}
			else -> {
				// No pause/resume control when Idle or Stopping.
			}
		}

		builder.addAction(
			0,
			"Stop",
			servicePendingIntent(context, AmbientScribeService.ACTION_STOP, requestCode = 3),
		)

		return builder.build()
	}

	private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
		val intent = AmbientScribeService.intentForAction(context, action)
		return PendingIntent.getService(
			context,
			requestCode,
			intent,
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
		)
	}

	private fun ServiceState.label(): String = when (this) {
		ServiceState.Idle -> "Idle"
		ServiceState.Initializing -> "Starting"
		ServiceState.Running -> "Listening"
		ServiceState.Paused -> "Paused"
		ServiceState.Stopping -> "Stopping"
	}
}

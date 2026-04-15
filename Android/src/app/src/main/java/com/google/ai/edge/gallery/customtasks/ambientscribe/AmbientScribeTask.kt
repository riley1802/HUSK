/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.ambientscribe.ui.AmbientScribeNavHost
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.CategoryInfo
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * Registers Ambient Scribe on the HUSK home screen.
 *
 * Unlike model-based custom tasks, Ambient Scribe's engines (VAD, ASR, audio tagger, LLM rewriter)
 * initialize themselves inside the foreground service as the user enables capture. This task's
 * [initializeModelFn] and [cleanUpModelFn] are therefore no-ops — no per-model lifecycle exists.
 *
 * The [MainScreen] delegates to [AmbientScribeNavHost], which runs an internal `NavHost` between
 * the calendar, day-detail, and settings screens. The outer app bar back button is routed here via
 * [CustomTaskData.setCustomNavigateUpCallback] so "back" pops the internal stack first, then exits
 * the task only when the internal stack is empty.
 */
@Singleton
class AmbientScribeTask @Inject constructor() : CustomTask {

	override val task: Task = Task(
		id = TASK_ID,
		label = TASK_LABEL,
		category = CategoryInfo(id = "ambient_scribe", label = "Ambient"),
		icon = Icons.Outlined.GraphicEq,
		description = "Passively capture, transcribe, and describe audio from your device. " +
			"Runs continuously on-device, zero cloud.",
		shortDescription = "On-device audio capture & transcription",
		docUrl = "",
		sourceCodeUrl = "",
		models = mutableListOf(),
	)

	override fun initializeModelFn(
		context: Context,
		coroutineScope: CoroutineScope,
		model: Model,
		onDone: (error: String) -> Unit,
	) {
		// No-op — engines self-initialize inside the foreground service.
		onDone("")
	}

	override fun cleanUpModelFn(
		context: Context,
		coroutineScope: CoroutineScope,
		model: Model,
		onDone: () -> Unit,
	) {
		// No-op — foreground service owns engine lifecycle.
		onDone()
	}

	@Composable
	override fun MainScreen(data: Any) {
		val customTaskData = data as? CustomTaskData
		AmbientScribeNavHost(
			setCustomNavigateUpCallback = customTaskData?.setCustomNavigateUpCallback ?: {},
			setTopBarVisible = customTaskData?.setTopBarVisible ?: {},
		)
	}

	companion object {
		const val TASK_ID: String = "ambient_scribe"
		const val TASK_LABEL: String = "Ambient Scribe"
	}
}

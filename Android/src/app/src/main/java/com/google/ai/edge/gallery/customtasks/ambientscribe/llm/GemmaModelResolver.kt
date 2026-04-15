/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Locates a Gemma [Model] on disk that the Ambient Scribe rewriter can use.
 *
 * Model lookup goes through the app's [CustomTask] set — the same source the Model Manager
 * uses — so any Gemma model the user has downloaded (either from the allowlist via the
 * LLM_CHAT task, or imported manually) will surface here once the allowlist has loaded.
 *
 * Returns `null` (rather than throwing) when no Gemma model is available on disk, so the
 * rewriter stays in a safe not-ready state until the user downloads one. The first
 * [GemmaClient.generate] call after a model lands on disk is expected to initialize the
 * underlying engine — this class does not own the lifecycle.
 *
 * Note on injection: [CustomTask] is provided into a Hilt [Set] via multibinding. We take
 * a [Provider] of that set because the [GemmaClient] singleton is constructed eagerly and
 * we want to re-enumerate tasks (and the models they accumulate over time from the
 * allowlist download + imports) at every call to [currentGemmaModel].
 */
@Singleton
class GemmaModelResolver @Inject constructor(
	@ApplicationContext private val context: Context,
	private val customTasksProvider: Provider<Set<@JvmSuppressWildcards CustomTask>>,
) {

	/**
	 * Returns a downloaded Gemma model that the Ambient Scribe rewriter can use, or null
	 * if none is available.
	 *
	 * Selection order:
	 *   1. Find every Model whose name contains "gemma" (case-insensitive) across all
	 *      registered tasks, deduplicated by name. Prefer models attached to LLM_CHAT
	 *      because that's the most permissive chat task — models that only ship for
	 *      Ask Image / Ask Audio wouldn't expose a pure-text rewriter path cleanly.
	 *   2. Keep only those whose primary model file actually exists on disk
	 *      ([Model.getPath]).
	 *   3. Return the smallest-by-sizeInBytes model — Gemma 3n E2B (~2B effective) beats
	 *      E4B (~4B) beats the 7B/27B variants for latency on the Z Fold 7, and the
	 *      rewriter workload is short so smaller = faster end-to-end.
	 *
	 * The selection is intentionally simple — no user-stated preference is stored in
	 * DataStore yet. When one is added, filter/rank by that key before falling back to
	 * size.
	 */
	fun currentGemmaModel(): Model? {
		val tasks = try {
			customTasksProvider.get().map { it.task }
		} catch (t: Throwable) {
			Log.w(TAG, "Failed to enumerate custom tasks", t)
			return null
		}

		// Prefer LLM_CHAT models; fall back to any task if the chat task isn't present.
		val chatTask = tasks.find { it.id == BuiltInTaskId.LLM_CHAT }
		val taskOrder = if (chatTask != null) {
			listOf(chatTask) + tasks.filter { it.id != BuiltInTaskId.LLM_CHAT }
		} else {
			tasks
		}

		val seen = HashSet<String>()
		val candidates = ArrayList<Model>()
		for (task in taskOrder) {
			for (model in task.models) {
				if (!seen.add(model.name)) continue
				if (!model.name.contains("gemma", ignoreCase = true)) continue
				candidates += model
			}
		}

		if (candidates.isEmpty()) return null

		// Keep only models whose file has been downloaded. Guard with a try/catch because
		// getPath() dereferences context.getExternalFilesDir() which can theoretically
		// return null on some devices in exotic storage states.
		val downloaded = candidates.filter { model ->
			try {
				File(model.getPath(context)).exists()
			} catch (t: Throwable) {
				Log.w(TAG, "Failed to resolve path for model '${model.name}'", t)
				false
			}
		}

		if (downloaded.isEmpty()) return null

		// Smallest first. sizeInBytes == 0 (unknown) sorts to the end so a sized model wins.
		return downloaded.minWithOrNull(
			compareBy { if (it.sizeInBytes > 0L) it.sizeInBytes else Long.MAX_VALUE },
		)
	}

	companion object {
		private const val TAG = "GemmaModelResolver"
	}
}

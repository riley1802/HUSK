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

import android.util.Log
import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Gemma-backed implementation of [EventDescriptionRewriter].
 *
 * For each event the rewriter builds a short, factual prompt and invokes [GemmaClient] to
 * produce a single-line description. Events whose rewrite throws are silently skipped — the
 * calling worker leaves those rows in [com.google.ai.edge.gallery.customtasks.ambientscribe
 * .data.RewriteState.PENDING] so they can be retried on a subsequent run.
 *
 * If [GemmaClient.isReady] returns false (no model loaded), the rewriter returns an empty
 * map and logs a single warning — it does NOT throw. This mirrors the stub pattern used by
 * [com.google.ai.edge.gallery.customtasks.ambientscribe.inference.MoonshineTfliteEngine] so
 * the feature degrades gracefully until the underlying model is wired up.
 *
 * Rewrites are processed sequentially because the on-device LLM runtime is single-threaded
 * and parallelism only adds memory pressure without improving throughput.
 */
@Singleton
class GemmaEventDescriptionRewriter @Inject constructor(
	private val gemmaClient: GemmaClient,
) : EventDescriptionRewriter {

	override suspend fun rewrite(events: List<AudioEvent>): Map<Long, String> {
		if (events.isEmpty()) return emptyMap()

		if (!gemmaClient.isReady()) {
			Log.w(TAG, "Gemma client not ready; returning empty rewrite map (${events.size} events skipped)")
			return emptyMap()
		}

		val output = LinkedHashMap<Long, String>(events.size)
		for (event in events) {
			val prompt = buildPrompt(event)
			val rewritten = try {
				gemmaClient.generate(prompt)
			} catch (ce: CancellationException) {
				// Propagate cancellation; don't swallow structured-concurrency signals.
				throw ce
			} catch (t: Throwable) {
				Log.w(TAG, "Rewrite failed for event id=${event.id} label='${event.label}'", t)
				continue
			}
			val cleaned = sanitize(rewritten)
			if (cleaned.isNotEmpty()) {
				output[event.id] = cleaned
			}
		}
		return output
	}

	private fun buildPrompt(event: AudioEvent): String =
		buildString {
			append("Rewrite the following audio event label as a brief, natural sentence suitable for a journal entry.\n")
			append("Be factual and concise (≤ 15 words). Do not add speculative context.\n")
			append("Label: \"").append(event.label).append("\"\n")
			append("Confidence: ").append(String.format("%.2f", event.confidence))
		}

	/**
	 * Strips surrounding whitespace, wrapping quotation marks, and any trailing newlines
	 * Gemma may include. Collapses to the first non-empty line since we asked for a single
	 * sentence.
	 */
	private fun sanitize(raw: String): String {
		if (raw.isEmpty()) return ""
		// Take the first non-empty line only — the prompt asked for one sentence but models
		// sometimes continue with a blank line + commentary.
		val firstLine = raw
			.lineSequence()
			.map { it.trim() }
			.firstOrNull { it.isNotEmpty() }
			?: return ""
		return firstLine.trim().trim('"', '\u201C', '\u201D', '\'').trim()
	}

	companion object {
		private const val TAG = "GemmaEventRewriter"
	}
}

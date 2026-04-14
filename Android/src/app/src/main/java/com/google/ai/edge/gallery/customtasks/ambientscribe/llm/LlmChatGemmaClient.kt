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
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [GemmaClient] backed by [LlmChatModelHelper].
 *
 * The wrapper does NOT own model lifecycle (initialize / cleanUp). It expects some other
 * component in the app — typically the model-download / model-picker flow — to have already
 * called [LlmChatModelHelper.initialize] on the [Model] returned by [modelProvider]. If the
 * model is absent or not yet initialized ([Model.instance] is null), [isReady] reports
 * false and [generate] throws — matching the Moonshine stub pattern used elsewhere in
 * Ambient Scribe.
 *
 * For MVP the default Hilt binding supplies a [modelProvider] that returns null, so the
 * client is never ready and the rewriter is a no-op. Once the model-picker integration
 * lands, the provider can be swapped without touching the rewriter itself.
 *
 * The class serializes [generate] calls with [generateMutex] — on-device inference is
 * single-threaded and a second call while one is in flight would conflict on the shared
 * conversation state.
 */
class LlmChatGemmaClient(
	private val modelProvider: () -> Model?,
	private val helper: LlmChatModelHelper = LlmChatModelHelper,
) : GemmaClient {

	private val generateMutex = Mutex()

	override fun isReady(): Boolean {
		val model = modelProvider() ?: return false
		return model.instance is LlmModelInstance
	}

	override suspend fun generate(prompt: String): String {
		val model = modelProvider()
			?: throw IllegalStateException("No Gemma model configured for Ambient Scribe")
		if (model.instance !is LlmModelInstance) {
			throw IllegalStateException("Gemma model '${model.name}' is not initialized")
		}

		return generateMutex.withLock {
			suspendCancellableCoroutine<String> { cont ->
				val accumulator = StringBuilder()
				try {
					helper.runInference(
						model = model,
						input = prompt,
						resultListener = { partial, done, _ ->
							// resultListener emits partial chunks with done=false and a final
							// (partial="", done=true) terminator.
							if (partial.isNotEmpty()) {
								accumulator.append(partial)
							}
							if (done && cont.isActive) {
								cont.resume(accumulator.toString())
							}
						},
						cleanUpListener = { /* no-op: we don't own lifecycle */ },
						onError = { message ->
							if (cont.isActive) {
								cont.resumeWithException(
									IllegalStateException("Gemma inference error: $message"),
								)
							}
						},
						images = emptyList(),
						audioClips = emptyList(),
						coroutineScope = null,
						extraContext = null,
					)
				} catch (t: Throwable) {
					Log.w(TAG, "Failed to dispatch Gemma inference", t)
					if (cont.isActive) cont.resumeWithException(t)
				}

				cont.invokeOnCancellation {
					try {
						helper.stopResponse(model)
					} catch (t: Throwable) {
						Log.w(TAG, "stopResponse failed during cancellation", t)
					}
				}
			}
		}
	}

	companion object {
		private const val TAG = "LlmChatGemmaClient"
	}
}

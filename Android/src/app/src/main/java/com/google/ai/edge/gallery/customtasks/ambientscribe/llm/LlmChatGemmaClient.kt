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
 * The client is lazy: [isReady] reports true whenever [modelProvider] returns a non-null
 * [Model] with a downloaded file on disk — the engine doesn't have to be initialized yet.
 * On the first [generate] call after a model becomes available, the client calls
 * [LlmChatModelHelper.initialize] itself (under [initMutex] so two concurrent rewrite
 * requests don't double-initialize the engine) and then runs inference.
 *
 * The rewriter does NOT own long-term model lifecycle — if another part of the app
 * (e.g. the chat screen) has already initialized the same [Model] instance, `model.instance`
 * is non-null when we reach [generate] and we skip the initialize step entirely.
 *
 * [generate] calls are serialized with [generateMutex] — on-device LiteRT-LM inference is
 * single-threaded per Engine instance and a concurrent call would conflict on the shared
 * conversation state.
 */
class LlmChatGemmaClient(
	private val context: Context,
	private val modelProvider: () -> Model?,
	private val helper: LlmChatModelHelper = LlmChatModelHelper,
) : GemmaClient {

	private val generateMutex = Mutex()
	private val initMutex = Mutex()

	override fun isReady(): Boolean {
		// Ready as soon as a Gemma model with a downloaded file exists — the resolver has
		// already enforced "file on disk". The engine is initialized lazily on the first
		// generate() call, so we deliberately do not require model.instance != null here.
		return modelProvider() != null
	}

	override suspend fun generate(prompt: String): String {
		val model = modelProvider()
			?: throw IllegalStateException("No Gemma model configured for Ambient Scribe")

		ensureInitialized(model)

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

	/**
	 * Initializes the LiteRT-LM engine for [model] if it has not been initialized already.
	 * Serialized with [initMutex] so two concurrent [generate] calls can't race into a
	 * double-initialize. Safe to call repeatedly — the check happens inside the lock.
	 */
	private suspend fun ensureInitialized(model: Model) {
		if (model.instance is LlmModelInstance) return
		initMutex.withLock {
			// Re-check under the lock.
			if (model.instance is LlmModelInstance) return

			suspendCancellableCoroutine<Unit> { cont ->
				try {
					helper.initialize(
						context = context,
						model = model,
						supportImage = false,
						supportAudio = false,
						onDone = { errorMessage ->
							if (!cont.isActive) return@initialize
							if (errorMessage.isEmpty() && model.instance is LlmModelInstance) {
								cont.resume(Unit)
							} else {
								val message = if (errorMessage.isNotEmpty()) {
									errorMessage
								} else {
									"engine reported success but model.instance is null"
								}
								cont.resumeWithException(
									IllegalStateException(
										"Failed to initialize Gemma model '${model.name}': $message",
									),
								)
							}
						},
					)
				} catch (t: Throwable) {
					if (cont.isActive) cont.resumeWithException(t)
				}
			}
		}
	}

	companion object {
		private const val TAG = "LlmChatGemmaClient"
	}
}

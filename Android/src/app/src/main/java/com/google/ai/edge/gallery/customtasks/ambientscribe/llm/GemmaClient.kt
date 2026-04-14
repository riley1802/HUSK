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

/**
 * Minimal surface that [GemmaEventDescriptionRewriter] needs from the underlying on-device
 * LLM. Introduced so the rewriter can be unit-tested with a plain mock, without pulling in
 * the callback-based `LlmChatModelHelper` API surface or a real `Model` instance.
 */
interface GemmaClient {
	/** True iff the client has a loaded model and can accept [generate] calls. */
	fun isReady(): Boolean

	/**
	 * Runs a single prompt to completion and returns the accumulated text. Throws if the
	 * underlying engine signals an error. Callers are expected to serialize invocations —
	 * on-device inference is single-threaded and parallel calls offer no benefit.
	 */
	suspend fun generate(prompt: String): String
}

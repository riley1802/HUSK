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

import com.google.ai.edge.gallery.customtasks.ambientscribe.data.AudioEvent

/**
 * Rewrites raw audio-event labels (e.g. AudioSet labels like "Dog bark") into natural
 * language sentences suitable for a journal entry.
 *
 * Implementations are expected to be safe to invoke on arbitrary background dispatchers and
 * MUST NOT throw on individual rewrite failures — failed events are simply absent from the
 * returned map so the caller can retry them later.
 */
interface EventDescriptionRewriter {
	/**
	 * Rewrites the supplied events. Returns a map of [AudioEvent.id] to the natural-language
	 * description. Events whose rewrite fails or errors are simply absent from the returned
	 * map.
	 *
	 * If the underlying inference engine is not ready (e.g. the on-device model is not yet
	 * loaded), the implementation returns an empty map without throwing.
	 */
	suspend fun rewrite(events: List<AudioEvent>): Map<Long, String>
}

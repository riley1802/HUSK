/*
 * Copyright 2026 Riley Thomason
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.data.notes

/** The type of match that produced this search result. */
enum class MatchType {
	TITLE,
	TAG,
	MESSAGE,
}

/**
 * A search result combining the matched note, optional matched message,
 * a display snippet, and the match type.
 */
data class NoteSearchResult(
	val note: Note,
	/** Non-null when the match was in message content. */
	val matchedMessage: NoteMessage? = null,
	/** Snippet text for display (truncated match context). */
	val snippet: String,
	val matchType: MatchType,
)

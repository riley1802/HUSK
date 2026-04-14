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

package com.google.ai.edge.gallery.ui.audioscribe

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * A single segment of a diarized transcript with speaker identification.
 */
data class TranscriptSegment(
	val speakerName: String,
	val text: String,
	val startMs: Long,
	val endMs: Long,
	val speakerId: String? = null,
	val speakerEmbedding: FloatArray? = null,
) {
	companion object {
		private val gson = Gson()
		private const val TRANSCRIPT_MARKER = "##TRANSCRIPT##"

		/** Serialize a list of segments to JSON for storage. */
		fun toJson(segments: List<TranscriptSegment>): String {
			// Strip embeddings from serialization to save space.
			val stripped = segments.map { it.copy(speakerEmbedding = null) }
			return TRANSCRIPT_MARKER + gson.toJson(stripped)
		}

		/** Deserialize a list of segments from JSON. Returns null if not a transcript. */
		fun fromJson(json: String): List<TranscriptSegment>? {
			if (!json.startsWith(TRANSCRIPT_MARKER)) return null
			val content = json.removePrefix(TRANSCRIPT_MARKER)
			val type = object : TypeToken<List<TranscriptSegment>>() {}.type
			return gson.fromJson(content, type)
		}

		/** Check if a string is a serialized transcript. */
		fun isTranscript(content: String): Boolean {
			return content.startsWith(TRANSCRIPT_MARKER)
		}
	}

	/** Format timestamp as MM:SS. */
	fun formatTimestamp(): String {
		val totalSeconds = startMs / 1000
		val minutes = totalSeconds / 60
		val seconds = totalSeconds % 60
		return "%d:%02d".format(minutes, seconds)
	}

	/** Format timestamp range as MM:SS - MM:SS. */
	fun formatTimestampRange(): String {
		val startTotal = startMs / 1000
		val endTotal = endMs / 1000
		return "%d:%02d - %d:%02d".format(
			startTotal / 60, startTotal % 60,
			endTotal / 60, endTotal % 60,
		)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is TranscriptSegment) return false
		return startMs == other.startMs && endMs == other.endMs && text == other.text
	}

	override fun hashCode(): Int {
		var result = text.hashCode()
		result = 31 * result + startMs.hashCode()
		result = 31 * result + endMs.hashCode()
		return result
	}
}

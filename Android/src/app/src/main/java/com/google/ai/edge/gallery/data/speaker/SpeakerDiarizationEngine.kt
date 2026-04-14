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

package com.google.ai.edge.gallery.data.speaker

import android.util.Log
import com.google.ai.edge.gallery.runtime.WhisperModelHelper
import java.util.UUID
import kotlin.math.sqrt

/**
 * Speaker diarization engine that assigns speaker labels to transcription segments.
 *
 * Uses ECAPA-TDNN embeddings to compare each segment's audio against saved
 * speaker profiles. Known speakers are identified automatically; unknown
 * speakers are grouped and labeled "Unknown Speaker N".
 */
class SpeakerDiarizationEngine(
	private val embeddingManager: SpeakerEmbeddingManager,
	private val speakerProfileDao: SpeakerProfileDao,
) {
	companion object {
		private const val TAG = "SpeakerDiarization"
		private const val SIMILARITY_THRESHOLD = 0.75f
		private const val UNKNOWN_GROUPING_THRESHOLD = 0.80f
		private const val SAMPLE_RATE = 16000
	}

	data class DiarizedSegment(
		val text: String,
		val startMs: Long,
		val endMs: Long,
		val speakerName: String,
		val speakerId: String?,
		val speakerEmbedding: FloatArray?,
	) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is DiarizedSegment) return false
			return startMs == other.startMs && endMs == other.endMs && text == other.text
		}

		override fun hashCode(): Int {
			var result = text.hashCode()
			result = 31 * result + startMs.hashCode()
			result = 31 * result + endMs.hashCode()
			return result
		}
	}

	/**
	 * Run diarization on Whisper transcript segments.
	 *
	 * @param segments Whisper transcription segments with timestamps.
	 * @param fullPcm The complete 16kHz mono float PCM buffer.
	 * @return List of segments with speaker labels assigned.
	 */
	suspend fun diarize(
		segments: List<WhisperModelHelper.TranscriptSegment>,
		fullPcm: FloatArray,
	): List<DiarizedSegment> {
		if (segments.isEmpty()) return emptyList()

		val profiles = speakerProfileDao.getAll()
		val profileEmbeddings = profiles.map { SpeakerProfile.bytesToEmbedding(it.embedding) }

		// Track unknown speakers for grouping.
		val unknownSpeakers = mutableListOf<Pair<FloatArray, Int>>() // embedding, unknownIndex
		var unknownCounter = 0

		val diarized = segments.map { segment ->
			// Extract audio slice for this segment.
			val startSample = ((segment.startMs * SAMPLE_RATE) / 1000).toInt().coerceIn(0, fullPcm.size)
			val endSample = ((segment.endMs * SAMPLE_RATE) / 1000).toInt().coerceIn(startSample, fullPcm.size)
			val audioSlice = fullPcm.copyOfRange(startSample, endSample)

			// Skip embedding for very short segments.
			if (audioSlice.size < SpeakerEmbeddingManager.MIN_SEGMENT_SAMPLES) {
				return@map DiarizedSegment(
					text = segment.text,
					startMs = segment.startMs,
					endMs = segment.endMs,
					speakerName = if (unknownSpeakers.isNotEmpty()) {
						"Unknown Speaker ${unknownSpeakers.last().second + 1}"
					} else {
						unknownCounter++
						unknownSpeakers.add(Pair(FloatArray(0), unknownCounter))
						"Unknown Speaker $unknownCounter"
					},
					speakerId = null,
					speakerEmbedding = null,
				)
			}

			val embedding = embeddingManager.embed(audioSlice)
			if (embedding == null) {
				unknownCounter++
				return@map DiarizedSegment(
					text = segment.text,
					startMs = segment.startMs,
					endMs = segment.endMs,
					speakerName = "Unknown Speaker $unknownCounter",
					speakerId = null,
					speakerEmbedding = null,
				)
			}

			// Compare against saved profiles.
			var bestMatch: SpeakerProfile? = null
			var bestSimilarity = 0f

			for (i in profiles.indices) {
				val similarity = cosineSimilarity(embedding, profileEmbeddings[i])
				if (similarity > bestSimilarity) {
					bestSimilarity = similarity
					bestMatch = profiles[i]
				}
			}

			if (bestMatch != null && bestSimilarity >= SIMILARITY_THRESHOLD) {
				Log.d(TAG, "Matched speaker '${bestMatch.name}' with similarity $bestSimilarity")
				DiarizedSegment(
					text = segment.text,
					startMs = segment.startMs,
					endMs = segment.endMs,
					speakerName = bestMatch.name,
					speakerId = bestMatch.id,
					speakerEmbedding = embedding,
				)
			} else {
				// Try to group with existing unknown speakers.
				var groupedIndex = -1
				for ((unknownEmb, idx) in unknownSpeakers) {
					if (unknownEmb.isNotEmpty() && cosineSimilarity(embedding, unknownEmb) >= UNKNOWN_GROUPING_THRESHOLD) {
						groupedIndex = idx
						break
					}
				}

				if (groupedIndex < 0) {
					unknownCounter++
					unknownSpeakers.add(Pair(embedding, unknownCounter))
					groupedIndex = unknownCounter
				}

				DiarizedSegment(
					text = segment.text,
					startMs = segment.startMs,
					endMs = segment.endMs,
					speakerName = "Unknown Speaker $groupedIndex",
					speakerId = null,
					speakerEmbedding = embedding,
				)
			}
		}

		return diarized
	}

	/**
	 * Label an unknown speaker: create a new profile or merge with an existing one.
	 *
	 * @param unknownEmbedding The embedding of the unknown speaker.
	 * @param name Name to assign to this speaker.
	 * @param existingProfileId If non-null, merge into this existing profile via running average.
	 * @return The speaker profile ID.
	 */
	suspend fun labelSpeaker(
		unknownEmbedding: FloatArray,
		name: String,
		existingProfileId: String? = null,
	): String {
		if (existingProfileId != null) {
			// Running average merge with existing profile.
			val existing = speakerProfileDao.getById(existingProfileId)
			if (existing != null) {
				val oldEmbedding = SpeakerProfile.bytesToEmbedding(existing.embedding)
				val newCount = existing.sampleCount + 1
				val merged = FloatArray(oldEmbedding.size)
				for (i in merged.indices) {
					merged[i] = (oldEmbedding[i] * existing.sampleCount + unknownEmbedding[i]) / newCount
				}
				// Re-normalize.
				val norm = sqrt(merged.sumOf { (it * it).toDouble() }).toFloat()
				if (norm > 0f) {
					for (i in merged.indices) {
						merged[i] /= norm
					}
				}
				speakerProfileDao.updateEmbedding(
					existing.id,
					SpeakerProfile.embeddingToBytes(merged),
					newCount,
				)
				Log.d(TAG, "Updated profile '${existing.name}' with $newCount samples")
				return existing.id
			}
		}

		// Create new profile.
		val id = UUID.randomUUID().toString()
		val profile = SpeakerProfile(
			id = id,
			name = name,
			embedding = SpeakerProfile.embeddingToBytes(unknownEmbedding),
			createdMs = System.currentTimeMillis(),
			sampleCount = 1,
		)
		speakerProfileDao.insert(profile)
		Log.d(TAG, "Created new speaker profile '$name'")
		return id
	}

	/**
	 * Compute cosine similarity between two vectors. Returns value in [-1, 1].
	 */
	fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
		if (a.size != b.size || a.isEmpty()) return 0f
		var dotProduct = 0f
		var normA = 0f
		var normB = 0f
		for (i in a.indices) {
			dotProduct += a[i] * b[i]
			normA += a[i] * a[i]
			normB += b[i] * b[i]
		}
		val denominator = sqrt(normA.toDouble()) * sqrt(normB.toDouble())
		return if (denominator > 0) (dotProduct / denominator.toFloat()) else 0f
	}
}

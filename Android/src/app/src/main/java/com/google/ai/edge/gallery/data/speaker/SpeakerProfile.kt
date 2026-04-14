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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Room entity representing a persistent speaker voice profile.
 *
 * Each profile stores a 192-dimensional embedding from the ECAPA-TDNN model
 * that characterizes a speaker's voice. Embeddings improve over time as
 * more samples are averaged in.
 */
@Entity(tableName = "speaker_profiles")
data class SpeakerProfile(
	@PrimaryKey val id: String,
	val name: String,
	@ColumnInfo(typeAffinity = ColumnInfo.BLOB)
	val embedding: ByteArray,
	@ColumnInfo(name = "created_ms") val createdMs: Long,
	@ColumnInfo(name = "sample_count") val sampleCount: Int = 1,
) {
	companion object {
		const val EMBEDDING_DIM = 192

		/** Serialize a FloatArray embedding to ByteArray for Room storage. */
		fun embeddingToBytes(embedding: FloatArray): ByteArray {
			val buffer = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
			for (value in embedding) {
				buffer.putFloat(value)
			}
			return buffer.array()
		}

		/** Deserialize a ByteArray back to FloatArray embedding. */
		fun bytesToEmbedding(bytes: ByteArray): FloatArray {
			val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
			val floats = FloatArray(bytes.size / 4)
			for (i in floats.indices) {
				floats[i] = buffer.getFloat()
			}
			return floats
		}
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is SpeakerProfile) return false
		return id == other.id
	}

	override fun hashCode(): Int = id.hashCode()
}

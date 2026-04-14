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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SpeakerProfileDao {
	@Query("SELECT * FROM speaker_profiles ORDER BY name ASC")
	suspend fun getAll(): List<SpeakerProfile>

	@Query("SELECT * FROM speaker_profiles WHERE id = :id")
	suspend fun getById(id: String): SpeakerProfile?

	@Query("SELECT * FROM speaker_profiles WHERE name = :name")
	suspend fun getByName(name: String): SpeakerProfile?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insert(profile: SpeakerProfile)

	@Query("UPDATE speaker_profiles SET embedding = :embedding, sample_count = :sampleCount WHERE id = :id")
	suspend fun updateEmbedding(id: String, embedding: ByteArray, sampleCount: Int)

	@Query("UPDATE speaker_profiles SET name = :name WHERE id = :id")
	suspend fun updateName(id: String, name: String)

	@Query("DELETE FROM speaker_profiles WHERE id = :id")
	suspend fun delete(id: String)
}

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
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
	@Query("SELECT * FROM transcriptions ORDER BY created_ms DESC")
	fun getAll(): Flow<List<Transcription>>

	@Query("SELECT * FROM transcriptions ORDER BY created_ms DESC LIMIT :limit")
	fun getRecent(limit: Int): Flow<List<Transcription>>

	@Query("SELECT * FROM transcriptions WHERE id = :id")
	suspend fun getById(id: String): Transcription?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insert(transcription: Transcription)

	@Query("DELETE FROM transcriptions WHERE id = :id")
	suspend fun delete(id: String)

	@Query("UPDATE transcriptions SET summary = :summary WHERE id = :id")
	suspend fun updateSummary(id: String, summary: String)
}

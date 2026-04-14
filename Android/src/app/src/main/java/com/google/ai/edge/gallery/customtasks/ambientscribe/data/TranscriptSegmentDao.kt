/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.customtasks.ambientscribe.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TranscriptSegmentDao {

	@Insert
	suspend fun insert(segment: TranscriptSegment): Long

	@Query("SELECT * FROM transcript_segments WHERE date = :date ORDER BY startTimestamp ASC")
	suspend fun getByDate(date: LocalDate): List<TranscriptSegment>

	@Query("SELECT * FROM transcript_segments WHERE date = :date ORDER BY startTimestamp ASC")
	fun observeByDate(date: LocalDate): Flow<List<TranscriptSegment>>

	@Query("SELECT DISTINCT date FROM transcript_segments ORDER BY date DESC")
	suspend fun getDatesWithData(): List<LocalDate>

	@Query("DELETE FROM transcript_segments")
	suspend fun deleteAll()

	@Query("UPDATE transcript_segments SET isBookmarked = :bookmarked WHERE id = :id")
	suspend fun setBookmarked(id: Long, bookmarked: Boolean)
}

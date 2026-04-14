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
interface AudioEventDao {

	@Insert
	suspend fun insert(event: AudioEvent): Long

	@Query("SELECT * FROM audio_events WHERE date = :date ORDER BY timestamp ASC")
	suspend fun getByDate(date: LocalDate): List<AudioEvent>

	@Query("SELECT * FROM audio_events WHERE date = :date ORDER BY timestamp ASC")
	fun observeByDate(date: LocalDate): Flow<List<AudioEvent>>

	@Query("SELECT * FROM audio_events WHERE rewriteState = :state AND date = :date ORDER BY timestamp ASC")
	suspend fun getByState(state: RewriteState, date: LocalDate): List<AudioEvent>

	@Query("SELECT * FROM audio_events WHERE rewriteState = :state ORDER BY timestamp ASC")
	suspend fun getAllByState(state: RewriteState): List<AudioEvent>

	suspend fun getPendingEvents(date: LocalDate): List<AudioEvent> =
		getByState(RewriteState.PENDING, date)

	suspend fun getAllPendingEvents(): List<AudioEvent> =
		getAllByState(RewriteState.PENDING)

	@Query("UPDATE audio_events SET naturalDescription = :description, rewriteState = :state WHERE id = :id")
	suspend fun updateRewrite(id: Long, description: String, state: RewriteState)

	@Query("DELETE FROM audio_events")
	suspend fun deleteAll()
}

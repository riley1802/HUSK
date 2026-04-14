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
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDate

@Dao
interface DailyMetadataDao {

	@Upsert
	suspend fun upsert(metadata: DailyMetadata)

	@Query("SELECT * FROM daily_metadata WHERE date = :date LIMIT 1")
	suspend fun getByDate(date: LocalDate): DailyMetadata?

	@Query("SELECT * FROM daily_metadata WHERE strftime('%Y-%m', date) = :yearMonth")
	suspend fun getMonth(yearMonth: String): List<DailyMetadata>

	@Query("DELETE FROM daily_metadata")
	suspend fun deleteAll()
}

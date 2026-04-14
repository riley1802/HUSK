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

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "daily_metadata")
data class DailyMetadata(
	@PrimaryKey
	val date: LocalDate,
	val totalDurationMs: Long,
	val totalWordCount: Int,
	val totalSegments: Int,
	val firstSegmentTime: Long? = null,
	val lastSegmentTime: Long? = null,
	val summary: String? = null,
)

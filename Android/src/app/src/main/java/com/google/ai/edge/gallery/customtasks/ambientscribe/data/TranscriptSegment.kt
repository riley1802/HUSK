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

@Entity(tableName = "transcript_segments")
data class TranscriptSegment(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val date: LocalDate,
	val startTimestamp: Long,
	val endTimestamp: Long,
	val text: String,
	val confidence: Float,
	val isBookmarked: Boolean = false,
	val audioFilePath: String? = null,
	val durationMs: Long,
	val wordCount: Int,
)

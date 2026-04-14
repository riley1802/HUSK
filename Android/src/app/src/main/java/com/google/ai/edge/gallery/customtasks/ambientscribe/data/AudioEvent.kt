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

enum class RewriteState { PENDING, DONE, SKIPPED_LOW_CONF }

@Entity(tableName = "audio_events")
data class AudioEvent(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val date: LocalDate,
	val timestamp: Long,
	val durationMs: Long,
	val label: String,
	val confidence: Float,
	val naturalDescription: String? = null,
	val rewriteState: RewriteState = RewriteState.PENDING,
	val audioFilePath: String? = null,
)

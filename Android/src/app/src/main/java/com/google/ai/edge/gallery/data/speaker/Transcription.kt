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

/**
 * Room entity for a saved transcription.
 */
@Entity(tableName = "transcriptions")
data class Transcription(
	@PrimaryKey val id: String,
	val title: String,
	@ColumnInfo(name = "transcript_json") val transcriptJson: String,
	val summary: String?,
	@ColumnInfo(name = "duration_ms") val durationMs: Long,
	@ColumnInfo(name = "whisper_model") val whisperModel: String,
	@ColumnInfo(name = "created_ms") val createdMs: Long,
	@ColumnInfo(name = "source_name") val sourceName: String?,
)

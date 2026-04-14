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

package com.google.ai.edge.gallery.data.notes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A brainstorm note — one per idea/topic. Contains metadata and links
 * to the conversation messages in [NoteMessage].
 */
@Entity(tableName = "notes")
data class Note(
	@PrimaryKey
	val id: String,

	/** AI-generated title, editable by the user. */
	val title: String,

	/** JSON array of tag strings, AI-generated (e.g. ["architecture", "api"]). */
	val tags: String = "[]",

	/** Which model was used (e.g. "Gemma-3n-E2B-it" or "Gemma-3n-E4B-it"). */
	@ColumnInfo(name = "model_id")
	val modelId: String,

	/** Timestamp when this note was created. */
	@ColumnInfo(name = "created_ms")
	val createdMs: Long,

	/** Timestamp when the last message was sent. */
	@ColumnInfo(name = "updated_ms")
	val updatedMs: Long,

	/** Timestamp when this note was last opened. */
	@ColumnInfo(name = "last_accessed_ms")
	val lastAccessedMs: Long,

	/** Soft delete flag. */
	@ColumnInfo(name = "is_archived")
	val isArchived: Boolean = false,
)

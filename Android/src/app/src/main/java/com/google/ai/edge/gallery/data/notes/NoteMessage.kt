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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single message within a note conversation. FK to [Note] with CASCADE delete.
 */
@Entity(
	tableName = "note_messages",
	foreignKeys = [
		ForeignKey(
			entity = Note::class,
			parentColumns = ["id"],
			childColumns = ["note_id"],
			onDelete = ForeignKey.CASCADE,
		),
	],
	indices = [Index(value = ["note_id"])],
)
data class NoteMessage(
	@PrimaryKey
	val id: String,

	/** FK to the parent note. */
	@ColumnInfo(name = "note_id")
	val noteId: String,

	/** "USER", "AGENT", or "SYSTEM". */
	val role: String,

	/** Message text (markdown). */
	val content: String,

	/** When this message was sent. */
	@ColumnInfo(name = "timestamp_ms")
	val timestampMs: Long,

	/** Whether this is a thinking/reasoning message. */
	@ColumnInfo(name = "is_thinking")
	val isThinking: Boolean = false,
)

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

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table for full-text search across note message content.
 * Linked to the [NoteMessage] content entity — Room keeps them in sync.
 */
@Fts4(contentEntity = NoteMessage::class)
@Entity(tableName = "note_messages_fts")
data class NoteMessageFts(
	val content: String,
)

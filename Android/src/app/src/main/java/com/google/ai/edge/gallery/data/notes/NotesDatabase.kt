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

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for brainstorm notes. Stores notes and their conversation
 * messages with FTS4 full-text search across titles, tags, and message content.
 */
@Database(
	entities = [Note::class, NoteMessage::class, NoteFts::class, NoteMessageFts::class],
	version = 1,
	exportSchema = false,
)
abstract class NotesDatabase : RoomDatabase() {
	abstract fun notesDao(): NotesDao
}

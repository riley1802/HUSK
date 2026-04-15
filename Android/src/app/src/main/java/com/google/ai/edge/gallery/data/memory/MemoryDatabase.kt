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

package com.google.ai.edge.gallery.data.memory

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for L2 warm memory. Stores the model's accumulated knowledge
 * about the user — facts, preferences, conversation summaries, and behavioral patterns.
 */
@Database(
	entities = [Memory::class, MemoryFts::class],
	version = 1,
	exportSchema = false,
)
abstract class MemoryDatabase : RoomDatabase() {
	abstract fun memoryDao(): MemoryDao
}

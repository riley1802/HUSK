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

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table for full-text search across L2 memories.
 * Linked to the [Memory] content entity — Room keeps them in sync.
 */
@Fts4(contentEntity = Memory::class)
@Entity(tableName = "memories_fts")
data class MemoryFts(
	val content: String,
	val category: String,
	val tags: String,
)

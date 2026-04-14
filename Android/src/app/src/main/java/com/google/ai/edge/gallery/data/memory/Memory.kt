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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * L2 warm memory entity — stores facts, preferences, summaries, and behavioral
 * patterns that the model has extracted from conversations. Queried on-demand
 * via function calling tools.
 */
@Entity(tableName = "memories")
data class Memory(
	@PrimaryKey
	val id: String,

	/** The memory content (e.g., "User prefers Kotlin over Java"). */
	val content: String,

	/** Category: "fact", "preference", "summary", or "behavior". */
	val category: String,

	/** Comma-separated tags for filtering (e.g., "kotlin,programming,languages"). */
	val tags: String,

	/** Origin of this memory (e.g., "conversation:2026-04-13", "extracted"). */
	val source: String,

	/** Model's confidence in this memory (0.0 to 1.0). */
	val confidence: Float,

	/** How many times this memory has been retrieved by the model. */
	@ColumnInfo(name = "access_count")
	val accessCount: Int = 0,

	/** Timestamp when this memory was created. */
	@ColumnInfo(name = "created_ms")
	val createdMs: Long,

	/** Timestamp when this memory was last updated. */
	@ColumnInfo(name = "updated_ms")
	val updatedMs: Long,

	/** Timestamp when this memory was last accessed/retrieved. */
	@ColumnInfo(name = "last_accessed_ms")
	val lastAccessedMs: Long,
)

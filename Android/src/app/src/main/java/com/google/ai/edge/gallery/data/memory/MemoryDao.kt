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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data access object for L2 warm memory. Provides full-text search via FTS4,
 * category filtering, and access-frequency queries.
 */
@Dao
interface MemoryDao {
	/**
	 * Full-text search across memory content, category, and tags.
	 * Uses the FTS4 virtual table for fast matching.
	 */
	@Query(
		"""
		SELECT memories.* FROM memories
		JOIN memories_fts ON memories.rowid = memories_fts.rowid
		WHERE memories_fts MATCH :query
		ORDER BY memories.confidence DESC
		LIMIT :limit
		"""
	)
	suspend fun search(query: String, limit: Int = 10): List<Memory>

	/** Get all memories in a specific category, newest first. */
	@Query("SELECT * FROM memories WHERE category = :category ORDER BY updated_ms DESC")
	suspend fun getByCategory(category: String): List<Memory>

	/** Get the most frequently accessed memories. */
	@Query("SELECT * FROM memories ORDER BY access_count DESC LIMIT :limit")
	suspend fun getMostAccessed(limit: Int = 20): List<Memory>

	/** Get all memories, newest first. */
	@Query("SELECT * FROM memories ORDER BY updated_ms DESC")
	suspend fun getAll(): List<Memory>

	/** Get a single memory by ID. */
	@Query("SELECT * FROM memories WHERE id = :id")
	suspend fun getById(id: String): Memory?

	/** Get the total count of memories. */
	@Query("SELECT COUNT(*) FROM memories")
	suspend fun getCount(): Int

	/** Insert a new memory. Replaces on conflict (same ID). */
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insert(memory: Memory)

	/** Update an existing memory. */
	@Update
	suspend fun update(memory: Memory)

	/** Delete a memory by ID. */
	@Query("DELETE FROM memories WHERE id = :id")
	suspend fun deleteById(id: String)

	/** Increment the access count and update last accessed timestamp. */
	@Query(
		"""
		UPDATE memories
		SET access_count = access_count + 1, last_accessed_ms = :timestamp
		WHERE id = :id
		"""
	)
	suspend fun recordAccess(id: String, timestamp: Long = System.currentTimeMillis())
}

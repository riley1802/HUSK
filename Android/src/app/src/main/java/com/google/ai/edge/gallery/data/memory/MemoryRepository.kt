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

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MemoryRepository"

/**
 * Repository wrapping [MemoryDao] for L2 warm memory operations.
 * Provides convenience methods used by [MemoryToolSet].
 */
@Singleton
class MemoryRepository @Inject constructor(
	private val memoryDao: MemoryDao,
) {
	/** Full-text search across all memories. */
	suspend fun search(query: String, category: String? = null, limit: Int = 10): List<Memory> {
		val results = if (category != null) {
			// FTS search filtered by category post-query.
			memoryDao.search(query, limit).filter { it.category == category }
		} else {
			memoryDao.search(query, limit)
		}
		// Record access for returned results.
		val now = System.currentTimeMillis()
		for (memory in results) {
			memoryDao.recordAccess(memory.id, now)
		}
		return results
	}

	/** Save a new memory. Returns the generated ID. */
	suspend fun save(
		content: String,
		category: String,
		tags: String,
		confidence: Float,
		source: String = "extracted",
	): String {
		val id = UUID.randomUUID().toString()
		val now = System.currentTimeMillis()
		val memory = Memory(
			id = id,
			content = content,
			category = category,
			tags = tags,
			source = source,
			confidence = confidence.coerceIn(0f, 1f),
			accessCount = 0,
			createdMs = now,
			updatedMs = now,
			lastAccessedMs = now,
		)
		memoryDao.insert(memory)
		Log.d(TAG, "Saved memory: id=$id, category=$category")
		return id
	}

	/** Update an existing memory's content and/or confidence. */
	suspend fun update(id: String, content: String? = null, confidence: Float? = null): Boolean {
		val existing = memoryDao.getById(id) ?: return false
		val updated = existing.copy(
			content = content ?: existing.content,
			confidence = confidence?.coerceIn(0f, 1f) ?: existing.confidence,
			updatedMs = System.currentTimeMillis(),
		)
		memoryDao.update(updated)
		Log.d(TAG, "Updated memory: id=$id")
		return true
	}

	/** Delete a memory by ID. */
	suspend fun delete(id: String): Boolean {
		val existing = memoryDao.getById(id) ?: return false
		memoryDao.deleteById(id)
		Log.d(TAG, "Deleted memory: id=$id")
		return true
	}

	/** List memories, optionally filtered by category. */
	suspend fun list(category: String? = null, sortBy: String = "updated"): List<Memory> {
		val memories = if (category != null) {
			memoryDao.getByCategory(category)
		} else {
			memoryDao.getAll()
		}
		return when (sortBy) {
			"access" -> memories.sortedByDescending { it.accessCount }
			"confidence" -> memories.sortedByDescending { it.confidence }
			"created" -> memories.sortedByDescending { it.createdMs }
			else -> memories // already sorted by updatedMs DESC from DAO
		}
	}

	/** Get a single memory by ID. */
	suspend fun getById(id: String): Memory? = memoryDao.getById(id)

	/** Get total memory count. */
	suspend fun getCount(): Int = memoryDao.getCount()
}

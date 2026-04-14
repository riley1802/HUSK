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
import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.proto.HotMemory
import com.google.ai.edge.gallery.proto.HotMemoryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HotMemoryStore"

/**
 * Maximum approximate token budget for L1 hot memory.
 * Measured as totalChars / 4 (standard English approximation).
 */
private const val MAX_TOKEN_BUDGET = 500
private const val CHARS_PER_TOKEN = 4

/**
 * Manages L1 hot memory — the always-present context injected into every conversation's
 * system prompt. This is the smallest, most curated layer of HUSK's 3-tier memory system.
 */
@Singleton
class HotMemoryStore @Inject constructor(
	private val hotMemoryDataStore: DataStore<HotMemory>,
) {
	/**
	 * Returns all current L1 memory entries, filtering out any that have expired.
	 */
	fun getAll(): List<HotMemoryEntry> {
		return runBlocking {
			val memory = hotMemoryDataStore.data.first()
			val now = System.currentTimeMillis()
			memory.entriesList.filter { entry ->
				entry.expiresMs == 0L || entry.expiresMs > now
			}
		}
	}

	/**
	 * Returns the current token usage of L1 memory (approximate).
	 */
	fun getTokenUsage(): Int {
		val entries = getAll()
		val totalChars = entries.sumOf { it.content.length + it.key.length + it.category.length + 10 }
		return totalChars / CHARS_PER_TOKEN
	}

	/**
	 * Checks whether adding [content] would exceed the token budget.
	 */
	fun wouldExceedBudget(content: String): Boolean {
		val currentUsage = getTokenUsage()
		val additionalTokens = (content.length + 20) / CHARS_PER_TOKEN
		return (currentUsage + additionalTokens) > MAX_TOKEN_BUDGET
	}

	/**
	 * Adds or updates an entry in L1 hot memory.
	 * Returns a result message indicating success or failure (e.g., budget exceeded).
	 */
	fun add(key: String, content: String, category: String, priority: Int, expiresMs: Long = 0L): String {
		// Check if updating an existing entry (no budget check needed for updates).
		val existing = getAll().find { it.key == key }
		if (existing != null) {
			return update(key, content)
		}

		// Check budget for new entries.
		if (wouldExceedBudget(content)) {
			val currentUsage = getTokenUsage()
			return "ERROR: L1 memory budget exceeded. Current usage: $currentUsage/$MAX_TOKEN_BUDGET tokens. Demote an existing entry first."
		}

		val entry = HotMemoryEntry.newBuilder()
			.setKey(key)
			.setContent(content)
			.setCategory(category)
			.setCreatedMs(System.currentTimeMillis())
			.setExpiresMs(expiresMs)
			.setPriority(priority.coerceIn(1, 10))
			.build()

		runBlocking {
			hotMemoryDataStore.updateData { memory ->
				memory.toBuilder()
					.addEntries(entry)
					.setLastUpdatedMs(System.currentTimeMillis())
					.build()
			}
		}
		Log.d(TAG, "Added L1 entry: key=$key, category=$category, priority=$priority")
		return "OK: Memory entry '$key' added to L1 hot memory."
	}

	/**
	 * Removes an entry from L1 hot memory by key.
	 * Returns the removed entry's content (for migration to L2), or null if not found.
	 */
	fun remove(key: String): HotMemoryEntry? {
		var removed: HotMemoryEntry? = null
		runBlocking {
			hotMemoryDataStore.updateData { memory ->
				val builder = memory.toBuilder()
				val index = builder.entriesList.indexOfFirst { it.key == key }
				if (index >= 0) {
					removed = builder.entriesList[index]
					builder.removeEntries(index)
				}
				builder.setLastUpdatedMs(System.currentTimeMillis()).build()
			}
		}
		if (removed != null) {
			Log.d(TAG, "Removed L1 entry: key=$key")
		}
		return removed
	}

	/**
	 * Updates an existing entry's content.
	 */
	fun update(key: String, content: String): String {
		var found = false
		runBlocking {
			hotMemoryDataStore.updateData { memory ->
				val builder = memory.toBuilder()
				val index = builder.entriesList.indexOfFirst { it.key == key }
				if (index >= 0) {
					found = true
					val updated = builder.entriesList[index].toBuilder()
						.setContent(content)
						.build()
					builder.setEntries(index, updated)
				}
				builder.setLastUpdatedMs(System.currentTimeMillis()).build()
			}
		}
		return if (found) {
			Log.d(TAG, "Updated L1 entry: key=$key")
			"OK: Memory entry '$key' updated."
		} else {
			"ERROR: No L1 memory entry found with key '$key'."
		}
	}

	/**
	 * Serializes all L1 entries into a text block suitable for system prompt injection.
	 * Returns null if there are no entries.
	 */
	fun serializeForSystemPrompt(): String? {
		val entries = getAll()
		if (entries.isEmpty()) return null

		val sb = StringBuilder()
		sb.appendLine("[HUSK Memory — What you know about the user right now]")
		for (entry in entries.sortedByDescending { it.priority }) {
			sb.appendLine("- [${entry.category}] ${entry.content}")
		}
		return sb.toString().trimEnd()
	}
}

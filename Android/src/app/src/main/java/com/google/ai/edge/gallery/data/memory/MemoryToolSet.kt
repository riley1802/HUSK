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
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking

private const val TAG = "MemoryToolSet"

/**
 * LiteRT-LM ToolSet providing memory management tools for the model.
 * Implements both L1 (hot memory) and L2 (warm memory) tools.
 */
class MemoryToolSet(
	private val hotMemoryStore: HotMemoryStore,
	private val memoryRepository: MemoryRepository? = null,
) : ToolSet {

	// ---- L1 Hot Memory Tools ----

	@Tool(description = "Promote important context to always-visible hot memory. Use this when the user shares critical information that should be remembered across all future conversations (current projects, major life events, key preferences). L1 memory is limited to ~500 tokens, so only promote the most important things.")
	fun promoteToL1(
		@ToolParam(description = "A short unique key for this memory (e.g., 'current_project', 'employer', 'pet_name')") key: String,
		@ToolParam(description = "The content to remember") content: String,
		@ToolParam(description = "Category: 'project', 'event', 'fact', or 'preference'") category: String,
		@ToolParam(description = "Importance from 1 (low) to 10 (critical)") priority: Int,
	): Map<String, String> {
		Log.d(TAG, "promoteToL1: key=$key, category=$category, priority=$priority")
		val result = hotMemoryStore.add(key, content, category, priority)
		return mapOf("result" to result)
	}

	@Tool(description = "Remove a memory entry from always-visible hot memory. Use this when information is no longer immediately relevant (e.g., a project is completed, an event has passed). The entry will be preserved in long-term memory (L2) for future reference.")
	fun demoteFromL1(
		@ToolParam(description = "The key of the memory entry to demote") key: String,
	): Map<String, String> {
		Log.d(TAG, "demoteFromL1: key=$key")
		val removed = hotMemoryStore.remove(key)
		return if (removed != null) {
			// Auto-save to L2 warm memory so nothing is lost.
			if (memoryRepository != null) {
				runBlocking {
					memoryRepository.save(
						content = removed.content,
						category = removed.category,
						tags = key,
						confidence = removed.priority / 10f,
						source = "demoted_from_l1",
					)
				}
			}
			mapOf(
				"result" to "OK: Entry '$key' demoted from L1 to L2 long-term memory.",
				"demoted_content" to removed.content,
			)
		} else {
			mapOf("result" to "ERROR: No L1 memory entry found with key '$key'.")
		}
	}

	@Tool(description = "Update the content of an existing hot memory entry. Use this when previously stored information has changed (e.g., user switched projects, updated a preference).")
	fun updateL1(
		@ToolParam(description = "The key of the memory entry to update") key: String,
		@ToolParam(description = "The new content for this entry") content: String,
	): Map<String, String> {
		Log.d(TAG, "updateL1: key=$key")
		val result = hotMemoryStore.update(key, content)
		return mapOf("result" to result)
	}

	@Tool(description = "List all entries currently in hot memory. Use this to review what is being injected into every conversation before making changes.")
	fun listL1(): Map<String, Any> {
		val entries = hotMemoryStore.getAll()
		val tokenUsage = hotMemoryStore.getTokenUsage()
		val entryList = entries.map { entry ->
			mapOf(
				"key" to entry.key,
				"content" to entry.content,
				"category" to entry.category,
				"priority" to entry.priority.toString(),
			)
		}
		return mapOf(
			"entries" to entryList,
			"token_usage" to "$tokenUsage/500",
			"entry_count" to entries.size.toString(),
		)
	}

	// ---- L2 Warm Memory Tools ----

	@Tool(description = "Search long-term memory for relevant context about the user. Use this when you need background information that isn't in hot memory — past preferences, facts, conversation history, behavioral patterns. Always search before asking the user for information they may have already shared.")
	fun searchMemory(
		@ToolParam(description = "Search query — keywords or phrases to find relevant memories") query: String,
		@ToolParam(description = "Optional category filter: 'fact', 'preference', 'summary', or 'behavior'. Leave empty to search all.") category: String,
		@ToolParam(description = "Maximum number of results to return (default 10)") limit: Int,
	): Map<String, Any> {
		val repo = memoryRepository ?: return mapOf("result" to "ERROR: L2 memory not available.")
		val effectiveLimit = if (limit <= 0) 10 else limit
		val effectiveCategory = category.ifBlank { null }
		val results = runBlocking { repo.search(query, effectiveCategory, effectiveLimit) }
		val entries = results.map { memory ->
			mapOf(
				"id" to memory.id,
				"content" to memory.content,
				"category" to memory.category,
				"tags" to memory.tags,
				"confidence" to memory.confidence.toString(),
			)
		}
		Log.d(TAG, "searchMemory: query='$query', found=${results.size}")
		return mapOf(
			"results" to entries,
			"count" to results.size.toString(),
		)
	}

	@Tool(description = "Save a new memory about the user. Use this when you learn facts, preferences, or patterns from the conversation. Be selective — save things that will be useful in future conversations, not trivial details.")
	fun saveMemory(
		@ToolParam(description = "The content to remember (e.g., 'User is a software engineer at Google')") content: String,
		@ToolParam(description = "Category: 'fact', 'preference', 'summary', or 'behavior'") category: String,
		@ToolParam(description = "Comma-separated tags for this memory (e.g., 'work,employer,career')") tags: String,
		@ToolParam(description = "Your confidence in this memory from 0.0 (uncertain) to 1.0 (certain)") confidence: Float,
	): Map<String, String> {
		val repo = memoryRepository ?: return mapOf("result" to "ERROR: L2 memory not available.")
		val id = runBlocking { repo.save(content, category, tags, confidence) }
		Log.d(TAG, "saveMemory: id=$id, category=$category")
		return mapOf(
			"result" to "OK: Memory saved with id '$id'.",
			"id" to id,
		)
	}

	@Tool(description = "Update an existing long-term memory. Use this when previously stored information has changed or needs correction.")
	fun updateMemory(
		@ToolParam(description = "The ID of the memory to update (from searchMemory results)") id: String,
		@ToolParam(description = "The new content for this memory") content: String,
		@ToolParam(description = "Updated confidence score (0.0 to 1.0), or -1 to keep current") confidence: Float,
	): Map<String, String> {
		val repo = memoryRepository ?: return mapOf("result" to "ERROR: L2 memory not available.")
		val effectiveConfidence = if (confidence < 0f) null else confidence
		val success = runBlocking { repo.update(id, content, effectiveConfidence) }
		return if (success) {
			Log.d(TAG, "updateMemory: id=$id")
			mapOf("result" to "OK: Memory '$id' updated.")
		} else {
			mapOf("result" to "ERROR: No memory found with id '$id'.")
		}
	}

	@Tool(description = "Delete a memory that is no longer accurate or relevant. Use this sparingly — prefer updating over deleting.")
	fun deleteMemory(
		@ToolParam(description = "The ID of the memory to delete") id: String,
	): Map<String, String> {
		val repo = memoryRepository ?: return mapOf("result" to "ERROR: L2 memory not available.")
		val success = runBlocking { repo.delete(id) }
		return if (success) {
			Log.d(TAG, "deleteMemory: id=$id")
			mapOf("result" to "OK: Memory '$id' deleted.")
		} else {
			mapOf("result" to "ERROR: No memory found with id '$id'.")
		}
	}

	@Tool(description = "List all memories in long-term storage, optionally filtered by category. Use this to get an overview of what you know about the user.")
	fun listMemories(
		@ToolParam(description = "Optional category filter: 'fact', 'preference', 'summary', or 'behavior'. Leave empty for all.") category: String,
		@ToolParam(description = "Sort by: 'updated' (default), 'access', 'confidence', or 'created'") sortBy: String,
	): Map<String, Any> {
		val repo = memoryRepository ?: return mapOf("result" to "ERROR: L2 memory not available.")
		val effectiveCategory = category.ifBlank { null }
		val effectiveSortBy = sortBy.ifBlank { "updated" }
		val memories = runBlocking { repo.list(effectiveCategory, effectiveSortBy) }
		val entries = memories.map { memory ->
			mapOf(
				"id" to memory.id,
				"content" to memory.content,
				"category" to memory.category,
				"tags" to memory.tags,
				"confidence" to memory.confidence.toString(),
			)
		}
		Log.d(TAG, "listMemories: category=${effectiveCategory ?: "all"}, count=${memories.size}")
		return mapOf(
			"memories" to entries,
			"total_count" to memories.size.toString(),
		)
	}
}

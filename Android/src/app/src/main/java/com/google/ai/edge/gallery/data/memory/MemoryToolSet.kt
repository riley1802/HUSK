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

private const val TAG = "MemoryToolSet"

/**
 * LiteRT-LM ToolSet providing memory management tools for the model.
 * Currently implements L1 (hot memory) tools. L2 (warm memory) tools
 * will be added in Phase 2.
 */
class MemoryToolSet(
	private val hotMemoryStore: HotMemoryStore,
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
			// TODO: In Phase 2, auto-save to L2 warm memory here.
			mapOf(
				"result" to "OK: Entry '$key' removed from L1 hot memory.",
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
}

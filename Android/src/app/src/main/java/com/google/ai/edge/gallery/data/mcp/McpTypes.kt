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

package com.google.ai.edge.gallery.data.mcp

import org.json.JSONArray
import org.json.JSONObject

// ---- JSON-RPC 2.0 Messages ----

data class JsonRpcRequest(
	val method: String,
	val params: JSONObject = JSONObject(),
	val id: Long = System.nanoTime(),
) {
	fun toJson(): JSONObject = JSONObject().apply {
		put("jsonrpc", "2.0")
		put("id", id)
		put("method", method)
		put("params", params)
	}
}

data class JsonRpcResponse(
	val id: Long?,
	val result: JSONObject?,
	val error: JsonRpcError?,
) {
	val isError: Boolean get() = error != null

	companion object {
		fun fromJson(json: JSONObject): JsonRpcResponse {
			val id = if (json.has("id") && !json.isNull("id")) json.getLong("id") else null
			val result = if (json.has("result") && !json.isNull("result")) json.getJSONObject("result") else null
			val error = if (json.has("error") && !json.isNull("error")) {
				val errObj = json.getJSONObject("error")
				JsonRpcError(
					code = errObj.getInt("code"),
					message = errObj.getString("message"),
					data = if (errObj.has("data")) errObj.opt("data") else null,
				)
			} else null
			return JsonRpcResponse(id, result, error)
		}
	}
}

data class JsonRpcError(
	val code: Int,
	val message: String,
	val data: Any? = null,
)

// ---- MCP Protocol Types ----

/** Information about an MCP server's capabilities after initialization. */
data class McpServerInfo(
	val name: String,
	val version: String,
	val capabilities: McpCapabilities,
)

/** What capabilities the server supports. */
data class McpCapabilities(
	val tools: Boolean = false,
	val resources: Boolean = false,
	val prompts: Boolean = false,
)

/** A tool exposed by an MCP server. */
data class McpToolDefinition(
	val name: String,
	val description: String,
	val inputSchema: JSONObject,
) {
	companion object {
		fun fromJson(json: JSONObject): McpToolDefinition {
			return McpToolDefinition(
				name = json.getString("name"),
				description = json.optString("description", ""),
				inputSchema = json.optJSONObject("inputSchema") ?: JSONObject(),
			)
		}
	}
}

/** Result of calling an MCP tool. */
data class McpToolResult(
	val content: List<McpContent>,
	val isError: Boolean = false,
) {
	companion object {
		fun fromJson(json: JSONObject): McpToolResult {
			val contentArray = json.optJSONArray("content") ?: JSONArray()
			val content = (0 until contentArray.length()).map { i ->
				McpContent.fromJson(contentArray.getJSONObject(i))
			}
			return McpToolResult(
				content = content,
				isError = json.optBoolean("isError", false),
			)
		}
	}

	/** Concatenate all text content into a single string. */
	fun toText(): String = content
		.filter { it.type == "text" }
		.joinToString("\n") { it.text }
}

/** A content block in an MCP response. */
data class McpContent(
	val type: String,
	val text: String = "",
) {
	companion object {
		fun fromJson(json: JSONObject): McpContent {
			return McpContent(
				type = json.optString("type", "text"),
				text = json.optString("text", ""),
			)
		}
	}
}

/** Lightweight server capability manifest for progressive disclosure. */
data class ServerCapability(
	val serverId: String,
	val name: String,
	val description: String,
	val categories: List<String>,
	val toolCount: Int,
)

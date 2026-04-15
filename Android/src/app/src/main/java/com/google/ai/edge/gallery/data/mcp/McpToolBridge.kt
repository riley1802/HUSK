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

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "McpToolBridge"

/**
 * Bridges MCP tools into LiteRT-LM's function calling system.
 *
 * Rather than creating dynamic ToolSet instances (which LiteRT-LM doesn't
 * support via reflection), this provides a single generic tool that the model
 * can call with a server name, tool name, and JSON arguments. The bridge
 * routes the call to the correct MCP server.
 *
 * This is the "L3 gateway" — the model calls `mcpTool()` when it needs
 * external data, and the bridge handles routing, execution, and error wrapping.
 */
@Singleton
class McpToolBridge @Inject constructor(
	private val mcpManager: McpManager,
) : ToolSet {

	@Tool(description = "Call an external tool via MCP (Model Context Protocol). Use this to reach outside the device — fetch GitHub issues, search the web, query databases, or interact with any connected MCP server. First use listMcpServers to see available servers, then listMcpTools to see what tools a server offers.")
	fun mcpTool(
		@ToolParam(description = "The name of the MCP server to use (from listMcpServers)") server: String,
		@ToolParam(description = "The name of the tool to call (from listMcpTools)") tool: String,
		@ToolParam(description = "JSON string of arguments to pass to the tool (e.g., '{\"repo\": \"owner/name\"}')") arguments: String,
	): Map<String, String> {
		// Find the server by name.
		val serverConfig = mcpManager.getServers().find { it.name == server }
			?: return mapOf("result" to "ERROR: MCP server '$server' not found. Use listMcpServers to see available servers.")

		if (!mcpManager.isConnected(serverConfig.id)) {
			// Try to connect.
			val connectResult = runBlocking { mcpManager.connect(serverConfig.id) }
			if (connectResult.isFailure) {
				return mapOf("result" to "ERROR: Failed to connect to '$server': ${connectResult.exceptionOrNull()?.message}")
			}
		}

		// Parse arguments.
		val args = try {
			if (arguments.isBlank() || arguments == "{}") JSONObject() else JSONObject(arguments)
		} catch (e: Exception) {
			return mapOf("result" to "ERROR: Invalid JSON arguments: ${e.message}")
		}

		// Execute the tool call.
		val result = runBlocking { mcpManager.callTool(serverConfig.id, tool, args) }
		return result.fold(
			onSuccess = { toolResult ->
				Log.d(TAG, "mcpTool: $server/$tool → ${if (toolResult.isError) "error" else "ok"}")
				mapOf(
					"result" to if (toolResult.isError) "ERROR" else "OK",
					"content" to toolResult.toText(),
				)
			},
			onFailure = { error ->
				Log.e(TAG, "mcpTool failed: $server/$tool: ${error.message}")
				mapOf("result" to "ERROR: ${error.message}")
			}
		)
	}

	@Tool(description = "List all configured MCP servers and their connection status. Use this to discover what external tools are available before calling mcpTool.")
	fun listMcpServers(): Map<String, Any> {
		val servers = mcpManager.getServers()
		val serverList = servers.map { config ->
			mapOf(
				"name" to config.name,
				"url" to config.url,
				"connected" to mcpManager.isConnected(config.id).toString(),
				"tool_count" to (mcpManager.getTools(config.id).size).toString(),
			)
		}
		return mapOf(
			"servers" to serverList,
			"count" to servers.size.toString(),
		)
	}

	@Tool(description = "List all tools available on a specific MCP server. Use this to discover what a server can do before calling mcpTool.")
	fun listMcpTools(
		@ToolParam(description = "The name of the MCP server") server: String,
	): Map<String, Any> {
		val serverConfig = mcpManager.getServers().find { it.name == server }
			?: return mapOf("result" to "ERROR: MCP server '$server' not found.")

		if (!mcpManager.isConnected(serverConfig.id)) {
			val connectResult = runBlocking { mcpManager.connect(serverConfig.id) }
			if (connectResult.isFailure) {
				return mapOf("result" to "ERROR: Failed to connect: ${connectResult.exceptionOrNull()?.message}")
			}
		}

		val tools = mcpManager.getTools(serverConfig.id)
		val toolList = tools.map { tool ->
			mapOf(
				"name" to tool.name,
				"description" to tool.description,
			)
		}
		return mapOf(
			"server" to server,
			"tools" to toolList,
			"count" to tools.size.toString(),
		)
	}
}

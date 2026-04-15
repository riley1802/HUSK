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
import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.proto.McpServerRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "McpManager"

/**
 * Singleton managing all MCP server connections. Handles server configuration
 * persistence, transport selection, tool discovery, and tool execution.
 *
 * This is the central L3 (external context) component of HUSK's 3-layer
 * memory architecture.
 */
@Singleton
class McpManager @Inject constructor(
	private val registryDataStore: DataStore<McpServerRegistry>,
	private val transports: Set<@JvmSuppressWildcards McpTransport>,
) {
	/** Cached server info from initialization handshakes. */
	private val serverInfoCache = mutableMapOf<String, McpServerInfo>()

	/** Cached tool definitions per server. */
	private val toolCache = mutableMapOf<String, List<McpToolDefinition>>()

	/** Connected server IDs. */
	private val connectedServers = mutableSetOf<String>()

	// ---- Server Configuration ----

	/** Get all configured servers. */
	fun getServers(): List<McpServerConfig> {
		return runBlocking {
			registryDataStore.data.first().serversList.map { McpServerConfig.fromProto(it) }
		}
	}

	/** Add a new server configuration. */
	fun addServer(config: McpServerConfig) {
		runBlocking {
			registryDataStore.updateData { registry ->
				registry.toBuilder()
					.addServers(McpServerConfig.toProto(config))
					.build()
			}
		}
		Log.d(TAG, "Added server: ${config.name} (${config.url})")
	}

	/** Remove a server configuration. */
	fun removeServer(serverId: String) {
		disconnect(serverId)
		runBlocking {
			registryDataStore.updateData { registry ->
				val filtered = registry.serversList.filter { it.id != serverId }
				registry.toBuilder().clearServers().addAllServers(filtered).build()
			}
		}
		Log.d(TAG, "Removed server: $serverId")
	}

	/** Update a server configuration. */
	fun updateServer(config: McpServerConfig) {
		runBlocking {
			registryDataStore.updateData { registry ->
				val updated = registry.serversList.map { proto ->
					if (proto.id == config.id) McpServerConfig.toProto(config) else proto
				}
				registry.toBuilder().clearServers().addAllServers(updated).build()
			}
		}
		Log.d(TAG, "Updated server: ${config.name}")
	}

	// ---- Connection Management ----

	/** Initialize a connection to an MCP server (handshake + tool discovery). */
	suspend fun connect(serverId: String): Result<McpServerInfo> {
		val config = getServers().find { it.id == serverId }
			?: return Result.failure(IllegalArgumentException("Server not found: $serverId"))

		val transport = findTransport(config)
			?: return Result.failure(IllegalArgumentException("No transport for type: ${config.transport}"))

		return try {
			// MCP initialize handshake.
			val initRequest = JsonRpcRequest(
				method = "initialize",
				params = JSONObject().apply {
					put("protocolVersion", "2024-11-05")
					put("capabilities", JSONObject())
					put("clientInfo", JSONObject().apply {
						put("name", "husk")
						put("version", "1.0.0")
					})
				}
			)
			val initResponse = transport.sendRequest(config, initRequest)
			if (initResponse.isError) {
				return Result.failure(Exception("Initialize failed: ${initResponse.error?.message}"))
			}

			val result = initResponse.result ?: JSONObject()
			val serverInfoJson = result.optJSONObject("serverInfo") ?: JSONObject()
			val capsJson = result.optJSONObject("capabilities") ?: JSONObject()

			val serverInfo = McpServerInfo(
				name = serverInfoJson.optString("name", config.name),
				version = serverInfoJson.optString("version", "unknown"),
				capabilities = McpCapabilities(
					tools = capsJson.has("tools"),
					resources = capsJson.has("resources"),
					prompts = capsJson.has("prompts"),
				),
			)

			// Send initialized notification.
			transport.sendRequest(config, JsonRpcRequest(method = "notifications/initialized"))

			serverInfoCache[serverId] = serverInfo
			connectedServers.add(serverId)

			// Discover tools if supported.
			if (serverInfo.capabilities.tools) {
				refreshTools(serverId)
			}

			Log.d(TAG, "Connected to ${serverInfo.name} v${serverInfo.version} (${toolCache[serverId]?.size ?: 0} tools)")
			Result.success(serverInfo)
		} catch (e: Exception) {
			Log.e(TAG, "Failed to connect to $serverId: ${e.message}")
			Result.failure(e)
		}
	}

	/** Disconnect from a server. */
	fun disconnect(serverId: String) {
		connectedServers.remove(serverId)
		serverInfoCache.remove(serverId)
		toolCache.remove(serverId)
		Log.d(TAG, "Disconnected: $serverId")
	}

	/** Check if a server is connected. */
	fun isConnected(serverId: String): Boolean = serverId in connectedServers

	/** Get all connected server IDs. */
	fun getConnectedServers(): Set<String> = connectedServers.toSet()

	// ---- Tool Discovery ----

	/** Refresh the tool list for a connected server. */
	suspend fun refreshTools(serverId: String): List<McpToolDefinition> {
		val config = getServers().find { it.id == serverId } ?: return emptyList()
		val transport = findTransport(config) ?: return emptyList()

		val response = transport.sendRequest(config, JsonRpcRequest(method = "tools/list"))
		if (response.isError) {
			Log.e(TAG, "tools/list failed for $serverId: ${response.error?.message}")
			return emptyList()
		}

		val toolsArray = response.result?.optJSONArray("tools") ?: return emptyList()
		val tools = (0 until toolsArray.length()).map { i ->
			McpToolDefinition.fromJson(toolsArray.getJSONObject(i))
		}

		// Filter by enabled tools if configured.
		val filtered = if (config.enabledTools.isNotEmpty()) {
			tools.filter { it.name in config.enabledTools }
		} else {
			tools
		}

		toolCache[serverId] = filtered
		Log.d(TAG, "Discovered ${filtered.size} tools for $serverId")
		return filtered
	}

	/** Get cached tools for a server. */
	fun getTools(serverId: String): List<McpToolDefinition> = toolCache[serverId] ?: emptyList()

	/** Get all available tools across all connected servers. */
	fun getAllTools(): Map<String, List<McpToolDefinition>> {
		return connectedServers.associateWith { getTools(it) }
	}

	// ---- Tool Execution ----

	/** Call a tool on a specific server. */
	suspend fun callTool(
		serverId: String,
		toolName: String,
		arguments: JSONObject = JSONObject(),
	): Result<McpToolResult> {
		val config = getServers().find { it.id == serverId }
			?: return Result.failure(IllegalArgumentException("Server not found: $serverId"))

		if (!isConnected(serverId)) {
			return Result.failure(IllegalStateException("Server not connected: $serverId"))
		}

		val transport = findTransport(config)
			?: return Result.failure(IllegalArgumentException("No transport for: ${config.transport}"))

		return try {
			val request = JsonRpcRequest(
				method = "tools/call",
				params = JSONObject().apply {
					put("name", toolName)
					put("arguments", arguments)
				}
			)

			val response = transport.sendRequest(config, request)
			if (response.isError) {
				return Result.failure(Exception("Tool call failed: ${response.error?.message}"))
			}

			val result = McpToolResult.fromJson(response.result ?: JSONObject())
			Log.d(TAG, "Tool $toolName@$serverId: ${if (result.isError) "ERROR" else "OK"}")
			Result.success(result)
		} catch (e: Exception) {
			Log.e(TAG, "Tool call failed: $toolName@$serverId: ${e.message}")
			Result.failure(e)
		}
	}

	// ---- Capability Manifests ----

	/** Get lightweight capability manifests for all configured servers. */
	fun getCapabilityManifests(): List<ServerCapability> {
		return getServers().map { config ->
			ServerCapability(
				serverId = config.id,
				name = config.name,
				description = serverInfoCache[config.id]?.let {
					"${it.name} v${it.version}"
				} ?: config.url,
				categories = listOf(config.transport),
				toolCount = toolCache[config.id]?.size ?: 0,
			)
		}
	}

	// ---- Internal ----

	private fun findTransport(config: McpServerConfig): McpTransport? {
		return transports.find { it.isSupported(config) }
	}
}

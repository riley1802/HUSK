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

/**
 * Abstraction for MCP transport layers. Implementations handle the actual
 * communication protocol (HTTP/SSE, WebSocket, stdio) while McpManager
 * works with this interface.
 *
 * New transports can be added by implementing this interface and registering
 * via Hilt @IntoSet.
 */
interface McpTransport {
	/** The transport type identifier (e.g., "http", "websocket", "stdio"). */
	val type: String

	/** Whether this transport can handle the given server config. */
	fun isSupported(config: McpServerConfig): Boolean

	/** Send a JSON-RPC request and receive the response. */
	suspend fun sendRequest(config: McpServerConfig, request: JsonRpcRequest): JsonRpcResponse

	/** Test connectivity to the server. Returns true if reachable. */
	suspend fun testConnection(config: McpServerConfig): Boolean
}

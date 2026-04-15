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
 * Configuration for an MCP server connection.
 * Stored in Proto DataStore as part of McpServerRegistry.
 */
data class McpServerConfig(
	/** Unique server identifier (UUID). */
	val id: String,

	/** Display name (e.g., "GitHub"). */
	val name: String,

	/** Server URL (e.g., "http://localhost:3000/mcp"). */
	val url: String,

	/** Transport type: "http", "websocket", or "stdio". */
	val transport: String = "http",

	/** Optional auth token (stored encrypted). */
	val authToken: String = "",

	/** Whether to auto-connect on app launch. */
	val autoConnect: Boolean = false,

	/** Whitelist of enabled tool names. Empty = all tools enabled. */
	val enabledTools: List<String> = emptyList(),

	/** Whether this server has read-only access (no destructive actions). */
	val readOnly: Boolean = false,
) {
	companion object {
		fun fromProto(proto: com.google.ai.edge.gallery.proto.McpServerConfig): McpServerConfig {
			return McpServerConfig(
				id = proto.id,
				name = proto.name,
				url = proto.url,
				transport = proto.transport.ifEmpty { "http" },
				authToken = proto.authToken,
				autoConnect = proto.autoConnect,
				enabledTools = proto.enabledToolsList.toList(),
				readOnly = proto.readOnly,
			)
		}

		fun toProto(config: McpServerConfig): com.google.ai.edge.gallery.proto.McpServerConfig {
			return com.google.ai.edge.gallery.proto.McpServerConfig.newBuilder()
				.setId(config.id)
				.setName(config.name)
				.setUrl(config.url)
				.setTransport(config.transport)
				.setAuthToken(config.authToken)
				.setAutoConnect(config.autoConnect)
				.addAllEnabledTools(config.enabledTools)
				.setReadOnly(config.readOnly)
				.build()
		}
	}
}

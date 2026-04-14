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

package com.google.ai.edge.gallery.data.mcp.transports

import android.util.Log
import com.google.ai.edge.gallery.data.mcp.JsonRpcRequest
import com.google.ai.edge.gallery.data.mcp.JsonRpcResponse
import com.google.ai.edge.gallery.data.mcp.McpServerConfig
import com.google.ai.edge.gallery.data.mcp.McpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "HttpTransport"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * MCP transport over HTTP POST. Sends JSON-RPC requests as POST bodies
 * and receives JSON-RPC responses in the response body.
 *
 * This is the "Streamable HTTP" transport from the MCP spec — the simplest
 * and most widely supported transport for remote MCP servers.
 */
class HttpTransport : McpTransport {
	override val type: String = "http"

	private val client = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(30, TimeUnit.SECONDS)
		.writeTimeout(10, TimeUnit.SECONDS)
		.build()

	override fun isSupported(config: McpServerConfig): Boolean {
		return config.transport == "http" && config.url.startsWith("http")
	}

	override suspend fun sendRequest(
		config: McpServerConfig,
		request: JsonRpcRequest,
	): JsonRpcResponse = withContext(Dispatchers.IO) {
		val jsonBody = request.toJson().toString()
		Log.d(TAG, "→ ${config.name}: ${request.method}")

		val httpRequest = Request.Builder()
			.url(config.url)
			.post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
			.apply {
				if (config.authToken.isNotEmpty()) {
					addHeader("Authorization", "Bearer ${config.authToken}")
				}
			}
			.build()

		val response = client.newCall(httpRequest).execute()
		val body = response.body?.string() ?: throw McpTransportException("Empty response body")

		if (!response.isSuccessful) {
			throw McpTransportException("HTTP ${response.code}: $body")
		}

		val jsonResponse = JSONObject(body)
		val rpcResponse = JsonRpcResponse.fromJson(jsonResponse)
		Log.d(TAG, "← ${config.name}: ${if (rpcResponse.isError) "ERROR" else "OK"}")
		rpcResponse
	}

	override suspend fun testConnection(config: McpServerConfig): Boolean {
		return try {
			withContext(Dispatchers.IO) {
				val request = Request.Builder()
					.url(config.url)
					.head()
					.build()
				val response = client.newCall(request).execute()
				response.isSuccessful || response.code == 405 // 405 = POST only, but server is reachable
			}
		} catch (e: Exception) {
			Log.d(TAG, "Connection test failed for ${config.name}: ${e.message}")
			false
		}
	}
}

class McpTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

package com.google.ai.edge.gallery.data.rag

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking

private const val TAG = "RagToolSet"

/**
 * LiteRT-LM ToolSet exposing RAG operations to the model.
 * Allows the model to search user documents during agentic reasoning.
 */
class RagToolSet(
	private val ragManager: RagManager,
	private val ragDao: RagDao,
) : ToolSet {

	@Tool(description = "Search the user's knowledge base for relevant documents. Use this when the user asks about information that may be in their uploaded documents, PDFs, or notes. Returns the most relevant text passages with source attribution.")
	fun searchDocuments(
		@ToolParam(description = "Search query — describe what you're looking for") query: String,
		@ToolParam(description = "Maximum number of results to return (1-10, default 5)") topK: Int,
	): Map<String, Any> {
		val effectiveTopK = topK.coerceIn(1, 10)
		val results = runBlocking { ragManager.retrieve(query, effectiveTopK) }
		if (results.isEmpty()) {
			return mapOf(
				"results" to emptyList<Map<String, String>>(),
				"count" to "0",
				"message" to "No relevant documents found for this query.",
			)
		}
		val entries = results.map { result ->
			mapOf(
				"document_name" to result.documentName,
				"content" to result.chunkContent,
				"relevance" to "${"%.0f".format(result.relevanceScore * 100)}%",
			)
		}
		Log.d(TAG, "searchDocuments: query='$query', found=${results.size}")
		return mapOf(
			"results" to entries,
			"count" to results.size.toString(),
		)
	}

	@Tool(description = "List all documents in the user's knowledge base. Shows document names, status, and chunk counts.")
	fun listDocuments(): Map<String, Any> {
		val documents = runBlocking { ragDao.getAllDocuments() }
		val entries = documents.map { doc ->
			mapOf(
				"name" to doc.name,
				"status" to doc.status.name,
				"chunks" to doc.chunkCount.toString(),
				"type" to doc.mimeType,
			)
		}
		Log.d(TAG, "listDocuments: count=${documents.size}")
		return mapOf(
			"documents" to entries,
			"total_count" to documents.size.toString(),
		)
	}

	@Tool(description = "Get the status of the user's knowledge base. Shows document count, chunk count, vector storage size, and active embedding model.")
	fun knowledgeBaseStatus(): Map<String, String> {
		val stats = runBlocking {
			val docCount = ragDao.getDocumentCount()
			val chunkCount = ragDao.getTotalChunkCount()
			val vectorBytes = ragDao.getTotalVectorSizeBytes()
			Triple(docCount, chunkCount, vectorBytes)
		}
		val vectorSizeMb = "%.2f".format(stats.third / (1024.0 * 1024.0))
		Log.d(TAG, "knowledgeBaseStatus: ${stats.first} docs, ${stats.second} chunks")
		return mapOf(
			"document_count" to stats.first.toString(),
			"chunk_count" to stats.second.toString(),
			"vector_size_mb" to vectorSizeMb,
			"embedding_model" to (ragManager.let { "gecko" }),
		)
	}
}

package com.google.ai.edge.gallery.data.rag

/**
 * A retrieval result from the RAG pipeline, used for source attribution in chat.
 */
data class ChunkResult(
	val documentName: String,
	val chunkContent: String,
	val relevanceScore: Float,
	val documentId: String,
	val chunkId: String,
)

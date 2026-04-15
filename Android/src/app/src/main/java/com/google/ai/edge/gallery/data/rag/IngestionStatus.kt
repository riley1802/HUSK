package com.google.ai.edge.gallery.data.rag

/**
 * Status of a document's ingestion into the RAG knowledge base.
 */
enum class IngestionStatus {
	PENDING,
	PROCESSING,
	READY,
	FAILED,
}

package com.google.ai.edge.gallery.data.rag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A document indexed in the RAG knowledge base.
 */
@Entity(tableName = "documents")
data class Document(
	@PrimaryKey
	val id: String,

	val name: String,

	@ColumnInfo(name = "mime_type")
	val mimeType: String,

	@ColumnInfo(name = "source_uri")
	val sourceUri: String? = null,

	@ColumnInfo(name = "collection_id")
	val collectionId: String = "global",

	@ColumnInfo(name = "chunk_count")
	val chunkCount: Int = 0,

	@ColumnInfo(name = "total_token_estimate")
	val totalTokenEstimate: Int = 0,

	val status: IngestionStatus = IngestionStatus.PENDING,

	@ColumnInfo(name = "error_message")
	val errorMessage: String? = null,

	@ColumnInfo(name = "content_hash")
	val contentHash: String? = null,

	@ColumnInfo(name = "vector_size_bytes")
	val vectorSizeBytes: Long = 0,

	@ColumnInfo(name = "embedding_model")
	val embeddingModel: String? = null,

	@ColumnInfo(name = "created_at")
	val createdAt: Long = System.currentTimeMillis(),

	@ColumnInfo(name = "updated_at")
	val updatedAt: Long = System.currentTimeMillis(),
)

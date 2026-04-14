package com.google.ai.edge.gallery.data.rag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A text chunk from an indexed document, paired with a vector in SqliteVectorStore.
 */
@Entity(
	tableName = "document_chunks",
	foreignKeys = [
		ForeignKey(
			entity = Document::class,
			parentColumns = ["id"],
			childColumns = ["document_id"],
			onDelete = ForeignKey.CASCADE,
		),
	],
	indices = [Index("document_id")],
)
data class DocumentChunk(
	@PrimaryKey
	val id: String,

	@ColumnInfo(name = "document_id")
	val documentId: String,

	@ColumnInfo(name = "chunk_index")
	val chunkIndex: Int,

	val content: String,

	@ColumnInfo(name = "token_estimate")
	val tokenEstimate: Int = 0,

	val metadata: String? = null,
)

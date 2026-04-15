package com.google.ai.edge.gallery.data.rag

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDao {

	// ---- Documents ----

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertDocument(document: Document)

	@Update
	suspend fun updateDocument(document: Document)

	@Query("SELECT * FROM documents ORDER BY created_at DESC")
	fun observeAllDocuments(): Flow<List<Document>>

	@Query("SELECT * FROM documents ORDER BY created_at DESC")
	suspend fun getAllDocuments(): List<Document>

	@Query("SELECT * FROM documents WHERE id = :id")
	suspend fun getDocumentById(id: String): Document?

	@Query("SELECT * FROM documents WHERE collection_id = :collectionId ORDER BY created_at DESC")
	suspend fun getDocumentsByCollection(collectionId: String): List<Document>

	@Query("SELECT * FROM documents WHERE content_hash = :hash LIMIT 1")
	suspend fun getDocumentByHash(hash: String): Document?

	@Query("SELECT * FROM documents WHERE source_uri = :sourceUri LIMIT 1")
	suspend fun getDocumentBySourceUri(sourceUri: String): Document?

	@Query("SELECT * FROM documents WHERE status = :status")
	suspend fun getDocumentsByStatus(status: IngestionStatus): List<Document>

	@Query("DELETE FROM documents WHERE id = :id")
	suspend fun deleteDocumentById(id: String)

	@Query("DELETE FROM documents")
	suspend fun deleteAllDocuments()

	@Query("SELECT COUNT(*) FROM documents WHERE status = 'READY'")
	suspend fun getReadyDocumentCount(): Int

	// ---- Chunks ----

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertChunks(chunks: List<DocumentChunk>)

	@Query("SELECT * FROM document_chunks WHERE document_id = :documentId ORDER BY chunk_index ASC")
	suspend fun getChunksByDocumentId(documentId: String): List<DocumentChunk>

	@Query("SELECT * FROM document_chunks WHERE id = :chunkId")
	suspend fun getChunkById(chunkId: String): DocumentChunk?

	@Query("SELECT COUNT(*) FROM document_chunks")
	suspend fun getTotalChunkCount(): Int

	@Query("DELETE FROM document_chunks WHERE document_id = :documentId")
	suspend fun deleteChunksByDocumentId(documentId: String)

	// ---- Stats ----

	@Query("SELECT COUNT(*) FROM documents")
	suspend fun getDocumentCount(): Int

	@Query("SELECT COALESCE(SUM(vector_size_bytes), 0) FROM documents")
	suspend fun getTotalVectorSizeBytes(): Long
}

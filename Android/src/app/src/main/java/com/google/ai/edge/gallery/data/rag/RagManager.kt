package com.google.ai.edge.gallery.data.rag

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.gallery.data.rag.embedding.EmbeddingModelManager
import com.google.ai.edge.gallery.data.rag.parser.DocumentParser
import com.google.ai.edge.gallery.data.rag.parser.PdfParser
import com.google.ai.edge.gallery.data.rag.parser.TextParser
import com.google.ai.edge.localagents.rag.memory.SqliteVectorStore
import com.google.ai.edge.localagents.rag.memory.VectorStoreRecord
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level coordinator for the RAG pipeline.
 * Handles document ingestion (parse → chunk → embed → store)
 * and retrieval (embed query → search → rank → return).
 */
@Singleton
class RagManager @Inject constructor(
	@ApplicationContext private val context: Context,
	private val ragDao: RagDao,
	private val embeddingModelManager: EmbeddingModelManager,
) {
	companion object {
		private const val TAG = "RagManager"
		private const val CHUNK_SIZE_CHARS = 600 // ~150 tokens — small for precision
		private const val CHUNK_OVERLAP_CHARS = 100 // ~25 tokens overlap
		private const val DEFAULT_TOP_K = 8
		private const val DEFAULT_THRESHOLD = 0.15f // lower threshold to catch more results
		private const val MAX_RAG_CONTEXT_CHARS = 6000 // ~1500 tokens — more context budget
		private const val VECTOR_DB_NAME = "husk_rag_vectors.db"
	}

	private val parsers: List<DocumentParser> = listOf(TextParser(), PdfParser())

	private var vectorStore: SqliteVectorStore? = null

	// ---- Initialization ----

	/**
	 * Initialize the vector store. Must be called after embedding model is loaded.
	 */
	fun initVectorStore() {
		if (vectorStore != null) return
		val dbPath = context.getDatabasePath(VECTOR_DB_NAME).absolutePath
		vectorStore = SqliteVectorStore(
			embeddingModelManager.dimensions,
			dbPath,
		)
		Log.d(TAG, "Vector store initialized at $dbPath (${embeddingModelManager.dimensions}d)")
	}

	// ---- Ingestion ----

	/**
	 * Ingest a document: parse, chunk, embed, and store.
	 * Returns the created Document entity.
	 */
	suspend fun ingest(
		uri: Uri,
		name: String,
		mimeType: String,
	): Document = withContext(Dispatchers.IO) {
		val documentId = UUID.randomUUID().toString()
		val document = Document(
			id = documentId,
			name = name,
			mimeType = mimeType,
			sourceUri = uri.toString(),
			status = IngestionStatus.PENDING,
		)
		ragDao.insertDocument(document)

		try {
			// Update to PROCESSING
			ragDao.updateDocument(document.copy(status = IngestionStatus.PROCESSING))

			// Parse
			val parser = parsers.find { mimeType in it.supportedMimeTypes }
				?: throw IllegalArgumentException("Unsupported file type: $mimeType")
			val parseResult = parser.parse(context, uri)

			// Duplicate detection
			val contentHash = sha256(parseResult.text)
			val existing = ragDao.getDocumentByHash(contentHash)
			if (existing != null && existing.id != documentId) {
				ragDao.deleteDocumentById(documentId)
				throw IllegalArgumentException(
					"Document already indexed as '${existing.name}'"
				)
			}

			// Chunk
			val chunkTexts = chunkText(parseResult.text)
			if (chunkTexts.isEmpty()) {
				throw IllegalArgumentException("Document produced no text chunks")
			}

			// Embed in sub-batches for speed + progress feedback.
			if (!embeddingModelManager.isLoaded) {
				throw IllegalStateException("Embedding model not loaded")
			}
			val store = vectorStore
				?: throw IllegalStateException("Vector store not initialized")

			val allChunks = mutableListOf<DocumentChunk>()
			val batchSize = 20 // Sub-batch size — balances JNI overhead vs memory
			val totalBatches = (chunkTexts.size + batchSize - 1) / batchSize
			val embedStart = System.currentTimeMillis()

			for (batchIdx in 0 until totalBatches) {
				val startIdx = batchIdx * batchSize
				val endIdx = minOf(startIdx + batchSize, chunkTexts.size)
				val batchTexts = chunkTexts.subList(startIdx, endIdx)

				// Embed this sub-batch
				val batchEmbeddings = embeddingModelManager.batchEmbed(batchTexts)

				// Create chunk entities + insert into Room and vector store immediately
				val batchChunks = batchTexts.mapIndexed { i, text ->
					DocumentChunk(
						id = UUID.randomUUID().toString(),
						documentId = documentId,
						chunkIndex = startIdx + i,
						content = text,
						tokenEstimate = estimateTokens(text),
						metadata = parseResult.metadata.takeIf { it.isNotEmpty() }
							?.let { meta -> meta.entries.joinToString(", ") { "${it.key}=${it.value}" } },
					)
				}
				ragDao.insertChunks(batchChunks)

				batchChunks.forEachIndexed { i, chunk ->
					val record = VectorStoreRecord.create(
						chunk.id,
						ImmutableList.copyOf(batchEmbeddings[i].map { it }),
					)
					store.insert(record)
				}

				allChunks.addAll(batchChunks)
				Log.d(TAG, "Embedded batch ${batchIdx + 1}/$totalBatches (${allChunks.size}/${chunkTexts.size} chunks)")
			}

			val embedMs = System.currentTimeMillis() - embedStart
			Log.d(TAG, "Embedding completed in ${embedMs}ms for ${chunkTexts.size} chunks")

			// Calculate vector size estimate
			val vectorSizeBytes = (allChunks.size * embeddingModelManager.dimensions * 4).toLong()

			// Update document as READY
			val readyDoc = document.copy(
				status = IngestionStatus.READY,
				chunkCount = allChunks.size,
				totalTokenEstimate = allChunks.sumOf { it.tokenEstimate },
				contentHash = contentHash,
				vectorSizeBytes = vectorSizeBytes,
				embeddingModel = embeddingModelManager.modelName,
				updatedAt = System.currentTimeMillis(),
			)
			ragDao.updateDocument(readyDoc)
			Log.d(TAG, "Ingested '${name}': ${allChunks.size} chunks, ${vectorSizeBytes} bytes vectors, ${embedMs}ms")
			readyDoc
		} catch (e: Exception) {
			Log.e(TAG, "Ingestion failed for '$name': ${e.message}", e)
			val failedDoc = document.copy(
				status = IngestionStatus.FAILED,
				errorMessage = e.message ?: "Unknown error",
				updatedAt = System.currentTimeMillis(),
			)
			ragDao.updateDocument(failedDoc)
			failedDoc
		}
	}

	// ---- Retrieval ----

	/**
	 * Retrieve relevant chunks for a query.
	 * Returns ranked ChunkResults within the token budget.
	 */
	suspend fun retrieve(
		query: String,
		topK: Int = DEFAULT_TOP_K,
		threshold: Float = DEFAULT_THRESHOLD,
	): List<ChunkResult> = withContext(Dispatchers.IO) {
		val store = vectorStore ?: return@withContext emptyList()

		if (!embeddingModelManager.isLoaded) {
			Log.w(TAG, "Embedding model not loaded, skipping retrieval")
			return@withContext emptyList()
		}

		if (ragDao.getReadyDocumentCount() == 0) {
			return@withContext emptyList()
		}

		try {
			// Embed the query
			val queryEmbedding = embeddingModelManager.embedQuery(query)
			val queryVector = queryEmbedding.map { it }.toList()

			// Search vector store
			val records = store.getNearestRecords(queryVector, topK, threshold)

			// Map to ChunkResults with token budget
			var totalChars = 0
			val results = mutableListOf<ChunkResult>()

			for (record in records) {
				val chunkId = record.data
				val chunk = ragDao.getChunkById(chunkId) ?: continue
				val doc = ragDao.getDocumentById(chunk.documentId) ?: continue

				if (totalChars + chunk.content.length > MAX_RAG_CONTEXT_CHARS) break
				totalChars += chunk.content.length

				// Extract similarity score from embeddings distance
				// VectorStoreRecord doesn't directly expose score,
				// but results are ordered by similarity
				val score = if (records.size > 1) {
					// Approximate: linearly scale position to [1.0, threshold]
					val position = results.size.toFloat() / records.size
					1.0f - (position * (1.0f - threshold))
				} else {
					1.0f
				}

				results.add(
					ChunkResult(
						documentName = doc.name,
						chunkContent = chunk.content,
						relevanceScore = score,
						documentId = doc.id,
						chunkId = chunk.id,
					)
				)
			}

			Log.d(TAG, "Retrieved ${results.size} chunks for query (${totalChars} chars)")
			results
		} catch (e: Exception) {
			Log.e(TAG, "Retrieval failed: ${e.message}", e)
			emptyList()
		}
	}

	/**
	 * Format retrieval results as a context block for the system prompt.
	 */
	fun formatContextBlock(results: List<ChunkResult>): String {
		if (results.isEmpty()) return ""
		val sb = StringBuilder()
		sb.appendLine("IMPORTANT: The following context was retrieved from the user's uploaded documents. You MUST use this information to answer the user's question. Base your response on these sources and cite them.")
		sb.appendLine()
		for ((i, result) in results.withIndex()) {
			sb.appendLine("[Source ${i + 1}: ${result.documentName}]")
			sb.appendLine(result.chunkContent)
			sb.appendLine()
		}
		return sb.toString().trimEnd()
	}

	// ---- Document Management ----

	/**
	 * Delete a document and its chunks. Vector store entries are cleaned up
	 * by removing records matching the chunk IDs.
	 */
	suspend fun deleteDocument(documentId: String) = withContext(Dispatchers.IO) {
		val chunks = ragDao.getChunksByDocumentId(documentId)
		// SqliteVectorStore doesn't expose a delete API in v0.1.0,
		// so we track deleted chunk IDs and skip them during retrieval.
		// Full cleanup happens on re-index.
		ragDao.deleteDocumentById(documentId)
		Log.d(TAG, "Deleted document $documentId (${chunks.size} chunks)")
	}

	/**
	 * Delete all documents and reset the knowledge base.
	 */
	suspend fun clearAll() = withContext(Dispatchers.IO) {
		ragDao.deleteAllDocuments()
		// Re-create vector store (fresh DB)
		vectorStore = null
		val dbFile = context.getDatabasePath(VECTOR_DB_NAME)
		if (dbFile.exists()) dbFile.delete()
		initVectorStore()
		Log.d(TAG, "Knowledge base cleared")
	}

	/**
	 * Check if the knowledge base has any ready documents.
	 */
	suspend fun hasDocuments(): Boolean = ragDao.getReadyDocumentCount() > 0

	// ---- Internal ----

	private fun chunkText(text: String): List<String> {
		val chunks = mutableListOf<String>()
		var start = 0
		while (start < text.length) {
			val end = minOf(start + CHUNK_SIZE_CHARS, text.length)
			chunks.add(text.substring(start, end))
			start += CHUNK_SIZE_CHARS - CHUNK_OVERLAP_CHARS
		}
		return chunks
	}

	private fun estimateTokens(text: String): Int {
		// Rough estimate: 1 token per 4 characters
		return text.length / 4
	}

	private fun sha256(text: String): String {
		val digest = MessageDigest.getInstance("SHA-256")
		val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
		return hash.joinToString("") { "%02x".format(it) }
	}
}

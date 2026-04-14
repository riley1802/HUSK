package com.google.ai.edge.gallery.ui.knowledgebase

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.rag.Document
import com.google.ai.edge.gallery.data.rag.DocumentChunk
import com.google.ai.edge.gallery.data.rag.IngestionStatus
import com.google.ai.edge.gallery.data.rag.RagDao
import com.google.ai.edge.gallery.data.rag.RagManager
import com.google.ai.edge.gallery.data.rag.embedding.EmbeddingModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "KnowledgeBaseViewModel"

data class KnowledgeBaseStats(
	val documentCount: Int = 0,
	val chunkCount: Int = 0,
	val vectorSizeBytes: Long = 0,
	val embeddingModel: String = "None",
)

@HiltViewModel
class KnowledgeBaseViewModel @Inject constructor(
	private val ragManager: RagManager,
	private val ragDao: RagDao,
	private val embeddingModelManager: EmbeddingModelManager,
) : ViewModel() {

	private val _documents = MutableStateFlow<List<Document>>(emptyList())
	val documents: StateFlow<List<Document>> = _documents.asStateFlow()

	private val _stats = MutableStateFlow(KnowledgeBaseStats())
	val stats: StateFlow<KnowledgeBaseStats> = _stats.asStateFlow()

	private val _selectedDocument = MutableStateFlow<Document?>(null)
	val selectedDocument: StateFlow<Document?> = _selectedDocument.asStateFlow()

	private val _selectedDocumentChunks = MutableStateFlow<List<DocumentChunk>>(emptyList())
	val selectedDocumentChunks: StateFlow<List<DocumentChunk>> = _selectedDocumentChunks.asStateFlow()

	init {
		viewModelScope.launch {
			ragDao.observeAllDocuments().collect { docs ->
				_documents.value = docs
				refreshStats()
			}
		}
	}

	private suspend fun refreshStats() {
		_stats.value = KnowledgeBaseStats(
			documentCount = ragDao.getDocumentCount(),
			chunkCount = ragDao.getTotalChunkCount(),
			vectorSizeBytes = ragDao.getTotalVectorSizeBytes(),
			embeddingModel = embeddingModelManager.modelName ?: "Not loaded",
		)
	}

	fun ingestDocument(context: Context, uri: Uri, name: String, mimeType: String) {
		viewModelScope.launch {
			try {
				ragManager.ingest(uri, name, mimeType)
				Log.d(TAG, "Document ingested: $name")
			} catch (e: Exception) {
				Log.e(TAG, "Ingestion failed: ${e.message}", e)
			}
		}
	}

	fun selectDocument(document: Document) {
		_selectedDocument.value = document
		viewModelScope.launch {
			_selectedDocumentChunks.value = ragDao.getChunksByDocumentId(document.id)
		}
	}

	fun clearSelectedDocument() {
		_selectedDocument.value = null
		_selectedDocumentChunks.value = emptyList()
	}

	fun deleteDocument(documentId: String) {
		viewModelScope.launch {
			ragManager.deleteDocument(documentId)
			clearSelectedDocument()
		}
	}

	fun clearKnowledgeBase() {
		viewModelScope.launch {
			ragManager.clearAll()
		}
	}
}

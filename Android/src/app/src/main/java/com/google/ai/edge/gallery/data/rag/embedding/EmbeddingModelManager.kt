package com.google.ai.edge.gallery.data.rag.embedding

import android.content.Context
import android.util.Log
import com.google.ai.edge.localagents.rag.models.EmbedData
import com.google.ai.edge.localagents.rag.models.Embedder
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.GeckoEmbeddingModel
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages embedding model lifecycle: loading, unloading, and embedding text.
 * Currently supports Gecko; EmbeddingGemma support is a future extension.
 */
@Singleton
class EmbeddingModelManager @Inject constructor() {

	companion object {
		private const val TAG = "EmbeddingModelManager"
		const val GECKO_DIMENSIONS = 256
		const val GECKO_MODEL_FILENAME = "gecko_embedding.tflite"
	}

	private val mutex = Mutex()
	private var embedder: Embedder<String>? = null
	private var currentModelName: String? = null
	private var currentDimensions: Int = GECKO_DIMENSIONS

	val isLoaded: Boolean get() = embedder != null
	val modelName: String? get() = currentModelName
	val dimensions: Int get() = currentDimensions

	/**
	 * Load the Gecko embedding model from the given path.
	 * Thread-safe — only one load at a time.
	 */
	suspend fun loadGecko(context: Context, modelPath: String) = mutex.withLock {
		withContext(Dispatchers.IO) {
			if (embedder != null && currentModelName == "gecko") {
				Log.d(TAG, "Gecko already loaded")
				return@withContext
			}

			// Unload any existing model
			unloadInternal()

			val modelFile = File(modelPath)
			if (!modelFile.exists()) {
				throw IllegalStateException("Gecko model not found at: $modelPath")
			}

			Log.d(TAG, "Loading Gecko embedding model from $modelPath")
			embedder = GeckoEmbeddingModel(
				modelPath,
				java.util.Optional.empty(),
				false,
			)
			currentModelName = "gecko"
			currentDimensions = GECKO_DIMENSIONS
			Log.d(TAG, "Gecko loaded successfully (${GECKO_DIMENSIONS}d)")
		}
	}

	/**
	 * Embed a single text string. Returns a float array of embeddings.
	 */
	suspend fun embed(text: String): FloatArray {
		val model = embedder ?: throw IllegalStateException("No embedding model loaded")
		val embedData = EmbedData.create(text, EmbedData.TaskType.RETRIEVAL_DOCUMENT)
		val request = EmbeddingRequest.create<String>(listOf(embedData))
		val result = model.getEmbeddings(request).await()
		return result.map { it.toFloat() }.toFloatArray()
	}

	/**
	 * Embed a query string (uses RETRIEVAL_QUERY task type for asymmetric search).
	 */
	suspend fun embedQuery(text: String): FloatArray {
		val model = embedder ?: throw IllegalStateException("No embedding model loaded")
		val embedData = EmbedData.create(text, EmbedData.TaskType.RETRIEVAL_QUERY)
		val request = EmbeddingRequest.create<String>(listOf(embedData))
		val result = model.getEmbeddings(request).await()
		return result.map { it.toFloat() }.toFloatArray()
	}

	/**
	 * Batch-embed multiple text strings. Returns a list of float arrays.
	 */
	suspend fun batchEmbed(texts: List<String>): List<FloatArray> {
		val model = embedder ?: throw IllegalStateException("No embedding model loaded")
		val embedDataList = texts.map { text ->
			EmbedData.create(text, EmbedData.TaskType.RETRIEVAL_DOCUMENT)
		}
		val request = EmbeddingRequest.create<String>(embedDataList)
		val result = model.getBatchEmbeddings(request).await()
		return result.map { embedding ->
			embedding.map { it.toFloat() }.toFloatArray()
		}
	}

	/**
	 * Unload the current embedding model and free resources.
	 */
	suspend fun unload() = mutex.withLock {
		unloadInternal()
	}

	private fun unloadInternal() {
		if (embedder != null) {
			Log.d(TAG, "Unloading embedding model: $currentModelName")
			// GeckoEmbeddingModel doesn't expose a close/destroy, rely on GC
			embedder = null
			currentModelName = null
		}
	}
}

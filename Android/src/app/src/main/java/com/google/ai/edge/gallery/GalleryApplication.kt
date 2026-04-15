/*
 * Copyright 2025 Google LLC
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

package com.google.ai.edge.gallery

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.rag.RagManager
import com.google.ai.edge.gallery.data.rag.embedding.EmbeddingModelManager
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class GalleryApplication : Application(), Configuration.Provider {

  @Inject lateinit var dataStoreRepository: DataStoreRepository
  @Inject lateinit var embeddingModelManager: EmbeddingModelManager
  @Inject lateinit var ragManager: RagManager

  @Inject lateinit var workerFactory: HiltWorkerFactory

  override val workManagerConfiguration: Configuration
    get() =
      Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()

  override fun onCreate() {
    super.onCreate()

    // Load saved theme and appearance preferences.
    ThemeSettings.themeOverride.value = dataStoreRepository.readTheme()
    ThemeSettings.loadFrom(dataStoreRepository)

    FirebaseApp.initializeApp(this)

    // Initialize RAG embedding model in the background.
    CoroutineScope(Dispatchers.IO).launch {
      initializeRag()
    }
  }

  private suspend fun initializeRag() {
    try {
      // Look for Gecko model in known locations.
      val geckoLocations = listOf(
        File(getExternalFilesDir(null), "gecko_embedding/Gecko_256_quant.tflite"),
        File(filesDir, "gecko_embedding/Gecko_256_quant.tflite"),
      )
      val modelFile = geckoLocations.firstOrNull { it.exists() }
      if (modelFile != null) {
        // Load the sentencepiece tokenizer from the same directory.
        val spModel = File(modelFile.parent, "sentencepiece.model")
        if (spModel.exists()) {
          Log.d(TAG, "Loading Gecko model from ${modelFile.absolutePath}")
          embeddingModelManager.loadGecko(this, modelFile.absolutePath)
          ragManager.initVectorStore()
          Log.d(TAG, "RAG system initialized successfully")
        } else {
          Log.w(TAG, "Gecko model found but sentencepiece.model missing at ${spModel.absolutePath}")
        }
      } else {
        Log.d(TAG, "No Gecko embedding model found — RAG disabled until model is downloaded")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize RAG: ${e.message}", e)
    }
  }

  companion object {
    private const val TAG = "GalleryApplication"
  }
}

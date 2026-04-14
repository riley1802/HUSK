/*
 * Copyright 2026 Riley Thomason
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

package com.google.ai.edge.gallery.ui.audioscribe

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.speaker.SpeakerDiarizationEngine
import com.google.ai.edge.gallery.data.speaker.SpeakerEmbeddingManager
import com.google.ai.edge.gallery.data.speaker.SpeakerProfile
import com.google.ai.edge.gallery.data.speaker.SpeakerProfileDao
import com.google.ai.edge.gallery.runtime.WhisperModelHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioScribeUiState(
	val selectedWhisperModel: String = "small",
	val whisperModelReady: Boolean = false,
	val speakerModelReady: Boolean = false,
	val speakerProfiles: List<SpeakerProfile> = emptyList(),
	val isInitializing: Boolean = false,
)

/**
 * ViewModel for Audio Scribe Whisper configuration and speaker profile management.
 * Manages Whisper model selection, initialization, and speaker diarization state.
 */
@HiltViewModel
class AudioScribeViewModel @Inject constructor(
	private val dataStoreRepository: DataStoreRepository,
	private val speakerEmbeddingManager: SpeakerEmbeddingManager,
	val diarizationEngine: SpeakerDiarizationEngine,
	private val speakerProfileDao: SpeakerProfileDao,
) : ViewModel() {

	companion object {
		private const val TAG = "AudioScribeVM"
	}

	private val _uiState = MutableStateFlow(AudioScribeUiState())
	val uiState: StateFlow<AudioScribeUiState> = _uiState.asStateFlow()

	init {
		val savedModel = dataStoreRepository.readWhisperSelectedModel()
		_uiState.update { it.copy(selectedWhisperModel = savedModel) }
		loadSpeakerProfiles()
	}

	fun selectWhisperModel(modelKey: String) {
		_uiState.update { it.copy(selectedWhisperModel = modelKey) }
		dataStoreRepository.saveWhisperSelectedModel(modelKey)
	}

	/**
	 * Initialize the Whisper model for transcription.
	 * Called when entering the Audio Scribe screen or changing models.
	 */
	fun initializeWhisper(context: Context, whisperModelPath: String) {
		_uiState.update { it.copy(isInitializing = true) }
		viewModelScope.launch(Dispatchers.IO) {
			WhisperModelHelper.initialize(context, whisperModelPath) { error ->
				_uiState.update {
					it.copy(
						whisperModelReady = error.isEmpty(),
						isInitializing = false,
					)
				}
				if (error.isNotEmpty()) {
					Log.e(TAG, "Failed to initialize Whisper: $error")
				}
			}
		}
	}

	fun cleanUpWhisper() {
		WhisperModelHelper.cleanUp()
		_uiState.update { it.copy(whisperModelReady = false) }
	}

	private fun loadSpeakerProfiles() {
		viewModelScope.launch(Dispatchers.IO) {
			val profiles = speakerProfileDao.getAll()
			_uiState.update { it.copy(speakerProfiles = profiles) }
		}
	}

	fun labelSpeaker(
		embedding: FloatArray,
		name: String,
		existingProfileId: String?,
	) {
		viewModelScope.launch(Dispatchers.IO) {
			diarizationEngine.labelSpeaker(embedding, name, existingProfileId)
			loadSpeakerProfiles()
		}
	}
}

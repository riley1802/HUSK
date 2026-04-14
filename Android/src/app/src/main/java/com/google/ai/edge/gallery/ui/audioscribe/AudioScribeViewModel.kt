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
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.common.AudioDecoder
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.speaker.SpeakerDiarizationEngine
import com.google.ai.edge.gallery.data.speaker.SpeakerEmbeddingManager
import com.google.ai.edge.gallery.data.speaker.SpeakerProfile
import com.google.ai.edge.gallery.data.speaker.SpeakerProfileDao
import com.google.ai.edge.gallery.runtime.WhisperModelHelper
import com.google.ai.edge.gallery.runtime.runtimeHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioScribeUiState(
	val selectedWhisperModel: String = "base",
	val whisperModelReady: Boolean = false,
	val speakerProfiles: List<SpeakerProfile> = emptyList(),
	val isInitializing: Boolean = false,

	// Processing state.
	val isProcessing: Boolean = false,
	val processingPhase: String? = null,
	val processingProgress: Float = 0f,

	// Results.
	val transcriptSegments: List<TranscriptSegment>? = null,
	val summaryText: String? = null,
	val error: String? = null,
)

/**
 * ViewModel for Audio Scribe — manages Whisper transcription, speaker diarization,
 * and Gemma E4B summary generation.
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
		private const val SUMMARY_MAX_DURATION_MS = 10 * 60 * 1000L
		private const val GEMMA_E4B_MODEL_NAME = "Gemma-4-E4B-it"
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

	/**
	 * Process an audio/video file through the full pipeline:
	 * 1. Decode any format to PCM
	 * 2. Whisper transcription
	 * 3. Speaker diarization
	 * 4. Gemma E4B summary (if < 10 min)
	 */
	fun processAudioFile(
		context: Context,
		uri: Uri,
		gemmaE4bModel: Model?,
	) {
		viewModelScope.launch(Dispatchers.Default) {
			_uiState.update {
				it.copy(
					isProcessing = true,
					processingPhase = "Extracting audio...",
					processingProgress = 0f,
					transcriptSegments = null,
					summaryText = null,
					error = null,
				)
			}

			try {
				// Phase 1: Decode audio.
				val decoder = AudioDecoder(context)
				val decoded = decoder.decode(uri) { progress ->
					_uiState.update { it.copy(processingProgress = progress) }
				}

				if (decoded == null) {
					_uiState.update {
						it.copy(isProcessing = false, processingPhase = null, error = "Failed to decode audio file")
					}
					return@launch
				}

				Log.d(TAG, "Decoded: ${decoded.samples.size} samples, ${decoded.durationMs}ms")

				// Phase 2: Transcribe with Whisper in chunks for long audio.
				_uiState.update { it.copy(processingPhase = "Transcribing...") }

				val segments = transcribeInChunks(decoded.samples, decoded.durationMs)
				if (segments.isEmpty()) {
					_uiState.update {
						it.copy(isProcessing = false, processingPhase = null, error = "Transcription produced no results")
					}
					return@launch
				}

				// Phase 3: Speaker diarization.
				_uiState.update { it.copy(processingPhase = "Identifying speakers...") }

				val diarized = diarizationEngine.diarize(segments, decoded.samples)
				val transcriptSegments = diarized.map { ds ->
					TranscriptSegment(
						speakerName = ds.speakerName,
						text = ds.text,
						startMs = ds.startMs,
						endMs = ds.endMs,
						speakerId = ds.speakerId,
						speakerEmbedding = ds.speakerEmbedding,
					)
				}

				_uiState.update {
					it.copy(
						transcriptSegments = transcriptSegments,
						processingPhase = null,
					)
				}

				// Phase 4: Generate summary with Gemma E4B if < 10 minutes.
				if (decoded.durationMs <= SUMMARY_MAX_DURATION_MS && gemmaE4bModel != null) {
					_uiState.update { it.copy(processingPhase = "Generating summary...") }
					generateSummary(gemmaE4bModel, transcriptSegments)
				}

				_uiState.update { it.copy(isProcessing = false, processingPhase = null) }
			} catch (e: Exception) {
				Log.e(TAG, "Error processing audio", e)
				_uiState.update {
					it.copy(
						isProcessing = false,
						processingPhase = null,
						error = "Processing error: ${e.message}",
					)
				}
			}
		}
	}

	/**
	 * Transcribe audio in chunks to avoid OOM on long recordings.
	 * Whisper processes optimally in ~30 second windows.
	 */
	private suspend fun transcribeInChunks(
		samples: FloatArray,
		durationMs: Long,
	): List<WhisperModelHelper.TranscriptSegment> {
		// For short audio (< 5 min), process in one shot.
		val chunkDurationSamples = 30 * 16000 // 30 seconds at 16kHz
		if (samples.size <= chunkDurationSamples * 10) { // < ~5 min
			return WhisperModelHelper.transcribe(samples)
		}

		// For long audio, process in 30-second chunks.
		Log.d(TAG, "Long audio (${durationMs / 1000}s), transcribing in chunks")
		val allSegments = mutableListOf<WhisperModelHelper.TranscriptSegment>()
		var offset = 0
		var chunkIndex = 0
		val totalChunks = (samples.size + chunkDurationSamples - 1) / chunkDurationSamples

		while (offset < samples.size) {
			val end = minOf(offset + chunkDurationSamples, samples.size)
			val chunk = samples.copyOfRange(offset, end)
			val chunkOffsetMs = (offset.toLong() * 1000L) / 16000L

			_uiState.update {
				it.copy(processingPhase = "Transcribing chunk ${chunkIndex + 1}/$totalChunks...")
			}

			val chunkSegments = WhisperModelHelper.transcribe(chunk)

			// Adjust timestamps to be relative to the full audio.
			for (seg in chunkSegments) {
				allSegments.add(
					WhisperModelHelper.TranscriptSegment(
						text = seg.text,
						startMs = seg.startMs + chunkOffsetMs,
						endMs = seg.endMs + chunkOffsetMs,
					)
				)
			}

			offset = end
			chunkIndex++
		}

		return allSegments
	}

	private suspend fun generateSummary(model: Model, segments: List<TranscriptSegment>) {
		try {
			val transcriptText = segments.joinToString("\n") { seg ->
				"${seg.speakerName}: ${seg.text}"
			}

			val prompt = "Summarize the following transcript concisely. " +
				"Highlight key points, decisions, and action items if any.\n\n" +
				"TRANSCRIPT:\n$transcriptText"

			val summaryBuilder = StringBuilder()

			val resultListener: (String, Boolean, String?) -> Unit = { partialResult, done, _ ->
				if (!partialResult.startsWith("<ctrl")) {
					summaryBuilder.append(partialResult)
					_uiState.update { it.copy(summaryText = summaryBuilder.toString()) }
				}
			}

			val cleanUpListener: () -> Unit = {}
			val errorListener: (String) -> Unit = { message ->
				Log.e(TAG, "Summary generation error: $message")
			}

			model.runtimeHelper.runInference(
				model = model,
				input = prompt,
				resultListener = resultListener,
				cleanUpListener = cleanUpListener,
				onError = errorListener,
			)
		} catch (e: Exception) {
			Log.e(TAG, "Error generating summary", e)
		}
	}

	fun clearResults() {
		_uiState.update {
			it.copy(
				transcriptSegments = null,
				summaryText = null,
				error = null,
			)
		}
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

	/**
	 * Find the Gemma 4 E4B model from the available models.
	 */
	fun findGemmaE4b(models: List<Model>): Model? {
		return models.find { it.name == GEMMA_E4B_MODEL_NAME }
	}
}

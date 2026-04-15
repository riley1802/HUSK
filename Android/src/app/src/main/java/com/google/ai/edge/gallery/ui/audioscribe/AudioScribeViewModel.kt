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
import android.os.PowerManager
import android.provider.OpenableColumns
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
import com.google.ai.edge.gallery.data.speaker.Transcription
import com.google.ai.edge.gallery.data.speaker.TranscriptionDao
import com.google.ai.edge.gallery.runtime.WhisperModelHelper
import com.google.ai.edge.gallery.runtime.runtimeHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
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
	val etaText: String? = null,

	// Results.
	val transcriptSegments: List<TranscriptSegment>? = null,
	val summaryText: String? = null,
	val error: String? = null,

	// History.
	val transcriptions: List<Transcription> = emptyList(),
)

@HiltViewModel
class AudioScribeViewModel @Inject constructor(
	private val dataStoreRepository: DataStoreRepository,
	private val speakerEmbeddingManager: SpeakerEmbeddingManager,
	val diarizationEngine: SpeakerDiarizationEngine,
	private val speakerProfileDao: SpeakerProfileDao,
	private val transcriptionDao: TranscriptionDao,
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
		loadTranscriptionHistory()
	}

	fun selectWhisperModel(modelKey: String) {
		_uiState.update { it.copy(selectedWhisperModel = modelKey) }
		dataStoreRepository.saveWhisperSelectedModel(modelKey)
	}

	/**
	 * Initialize Whisper from a Model object (checks download status and loads).
	 */
	fun initializeWhisperFromModel(context: Context, model: Model) {
		val path = model.getPath(context)
		val file = java.io.File(path)
		if (!file.exists()) {
			Log.e(TAG, "Whisper model not found at $path")
			_uiState.update { it.copy(whisperModelReady = false, error = "Whisper model not downloaded. Please download from the model manager.") }
			return
		}
		_uiState.update { it.copy(isInitializing = true, error = null) }
		viewModelScope.launch(Dispatchers.IO) {
			WhisperModelHelper.initialize(context, path) { error ->
				_uiState.update {
					it.copy(
						whisperModelReady = error.isEmpty(),
						isInitializing = false,
						error = error.ifEmpty { null },
					)
				}
			}
		}
	}

	fun cleanUpWhisper() {
		WhisperModelHelper.cleanUp()
		_uiState.update { it.copy(whisperModelReady = false) }
	}

	/**
	 * Process audio through: decode -> transcribe -> diarize -> summarize.
	 * Includes adaptive ETA estimation.
	 */
	fun processAudioFile(
		context: Context,
		uri: Uri,
		gemmaE4bModel: Model?,
	) {
		viewModelScope.launch(Dispatchers.Default) {
			val sourceName = getFileName(context, uri)

			// Acquire a wake lock so processing continues reliably in the background.
			val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
			val wakeLock = powerManager.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK,
				"husk:audio_scribe_processing"
			)
			wakeLock.acquire(120 * 60 * 1000L) // 2 hour max timeout

			_uiState.update {
				it.copy(
					isProcessing = true,
					processingPhase = "Extracting audio...",
					processingProgress = 0f,
					etaText = "Estimating...",
					transcriptSegments = null,
					summaryText = null,
					error = null,
				)
			}

			try {
				// Phase 1: Decode.
				val decoder = AudioDecoder(context)
				val decoded = decoder.decode(uri) { progress ->
					_uiState.update { it.copy(processingProgress = progress) }
				}

				if (decoded == null) {
					_uiState.update {
						it.copy(isProcessing = false, processingPhase = null, etaText = null, error = "Failed to decode audio file")
					}
					return@launch
				}

				Log.d(TAG, "Decoded: ${decoded.samples.size} samples, ${decoded.durationMs}ms")

				// Check Whisper is loaded.
				if (!WhisperModelHelper.isLoaded) {
					_uiState.update {
						it.copy(isProcessing = false, processingPhase = null, etaText = null, error = "Whisper model not loaded")
					}
					return@launch
				}

				// Phase 2: Transcribe with ETA.
				val segments = transcribeWithEta(decoded.samples, decoded.durationMs)
				if (segments.isEmpty()) {
					_uiState.update {
						it.copy(isProcessing = false, processingPhase = null, etaText = null, error = "Transcription produced no results. Is the Whisper model loaded?")
					}
					return@launch
				}

				// Phase 3: Speaker diarization.
				_uiState.update { it.copy(processingPhase = "Identifying speakers...", etaText = null) }
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

				_uiState.update { it.copy(transcriptSegments = transcriptSegments, processingPhase = null) }

				// Save transcription to history.
				val transcriptionId = UUID.randomUUID().toString()
				val title = sourceName ?: "Transcription ${java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(java.util.Date())}"
				transcriptionDao.insert(
					Transcription(
						id = transcriptionId,
						title = title,
						transcriptJson = TranscriptSegment.toJson(transcriptSegments),
						summary = null,
						durationMs = decoded.durationMs,
						whisperModel = _uiState.value.selectedWhisperModel,
						createdMs = System.currentTimeMillis(),
						sourceName = sourceName,
					)
				)
				loadTranscriptionHistory()

				// Phase 4: Summary with Gemma E4B if < 10 min.
				if (decoded.durationMs <= SUMMARY_MAX_DURATION_MS && gemmaE4bModel != null) {
					_uiState.update { it.copy(processingPhase = "Generating summary...") }
					val summary = generateSummary(gemmaE4bModel, transcriptSegments)
					if (summary != null) {
						transcriptionDao.updateSummary(transcriptionId, summary)
						loadTranscriptionHistory()
					}
				}

				_uiState.update { it.copy(isProcessing = false, processingPhase = null, etaText = null) }
			} catch (e: Exception) {
				Log.e(TAG, "Error processing audio", e)
				_uiState.update {
					it.copy(isProcessing = false, processingPhase = null, etaText = null, error = "Processing error: ${e.message}")
				}
			} finally {
				if (wakeLock.isHeld) {
					wakeLock.release()
				}
			}
		}
	}

	/**
	 * Transcribe with adaptive ETA estimation.
	 * Starts with a conservative overestimate and adjusts based on actual chunk timing.
	 */
	private suspend fun transcribeWithEta(
		samples: FloatArray,
		durationMs: Long,
	): List<WhisperModelHelper.TranscriptSegment> {
		val chunkDurationSamples = 30 * 16000 // 30 seconds at 16kHz

		// Short audio — single shot, no ETA needed.
		if (samples.size <= chunkDurationSamples * 10) {
			_uiState.update { it.copy(processingPhase = "Transcribing...", etaText = null) }
			return WhisperModelHelper.transcribe(samples)
		}

		val totalChunks = (samples.size + chunkDurationSamples - 1) / chunkDurationSamples
		val allSegments = mutableListOf<WhisperModelHelper.TranscriptSegment>()

		// Initial overestimate: assume ~12 seconds per chunk (conservative).
		var estimatedSecsPerChunk = 12.0
		val chunkTimes = mutableListOf<Long>()

		var offset = 0
		var chunkIndex = 0

		while (offset < samples.size) {
			val end = minOf(offset + chunkDurationSamples, samples.size)
			val chunk = samples.copyOfRange(offset, end)
			val chunkOffsetMs = (offset.toLong() * 1000L) / 16000L

			// Calculate ETA.
			val remainingChunks = totalChunks - chunkIndex
			val etaSecs = (remainingChunks * estimatedSecsPerChunk).toInt()
			val etaText = formatEta(etaSecs)

			_uiState.update {
				it.copy(
					processingPhase = "Transcribing ${chunkIndex + 1}/$totalChunks",
					etaText = etaText,
				)
			}

			val chunkStart = System.currentTimeMillis()
			val chunkSegments = WhisperModelHelper.transcribe(chunk)
			val chunkElapsed = System.currentTimeMillis() - chunkStart

			chunkTimes.add(chunkElapsed)

			// Update estimate with exponential moving average (recent chunks weighted more).
			val recentAvg = if (chunkTimes.size <= 3) {
				chunkTimes.average() / 1000.0
			} else {
				// Use last 5 chunks for EMA.
				val recent = chunkTimes.takeLast(5)
				recent.average() / 1000.0
			}
			estimatedSecsPerChunk = recentAvg

			// Adjust timestamps relative to full audio.
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

	private fun formatEta(totalSeconds: Int): String {
		return when {
			totalSeconds < 60 -> "~${totalSeconds}s remaining"
			totalSeconds < 3600 -> {
				val min = totalSeconds / 60
				val sec = totalSeconds % 60
				"~${min}m ${sec}s remaining"
			}
			else -> {
				val hr = totalSeconds / 3600
				val min = (totalSeconds % 3600) / 60
				"~${hr}h ${min}m remaining"
			}
		}
	}

	private suspend fun generateSummary(model: Model, segments: List<TranscriptSegment>): String? {
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

			model.runtimeHelper.runInference(
				model = model,
				input = prompt,
				resultListener = resultListener,
				cleanUpListener = {},
				onError = { Log.e(TAG, "Summary error: $it") },
			)

			return summaryBuilder.toString().ifEmpty { null }
		} catch (e: Exception) {
			Log.e(TAG, "Error generating summary", e)
			return null
		}
	}

	fun clearResults() {
		_uiState.update {
			it.copy(transcriptSegments = null, summaryText = null, error = null)
		}
	}

	/**
	 * Load a saved transcription by ID.
	 */
	fun loadTranscription(id: String) {
		viewModelScope.launch(Dispatchers.IO) {
			val transcription = transcriptionDao.getById(id) ?: return@launch
			val segments = TranscriptSegment.fromJson(transcription.transcriptJson)
			_uiState.update {
				it.copy(
					transcriptSegments = segments,
					summaryText = transcription.summary,
					error = null,
				)
			}
		}
	}

	fun deleteTranscription(id: String) {
		viewModelScope.launch(Dispatchers.IO) {
			transcriptionDao.delete(id)
			loadTranscriptionHistory()
		}
	}

	private fun loadTranscriptionHistory() {
		viewModelScope.launch(Dispatchers.IO) {
			transcriptionDao.getAll().collect { list ->
				_uiState.update { it.copy(transcriptions = list) }
			}
		}
	}

	private fun loadSpeakerProfiles() {
		viewModelScope.launch(Dispatchers.IO) {
			val profiles = speakerProfileDao.getAll()
			_uiState.update { it.copy(speakerProfiles = profiles) }
		}
	}

	fun labelSpeaker(embedding: FloatArray, name: String, existingProfileId: String?) {
		viewModelScope.launch(Dispatchers.IO) {
			diarizationEngine.labelSpeaker(embedding, name, existingProfileId)
			loadSpeakerProfiles()
		}
	}

	fun findGemmaE4b(models: List<Model>): Model? {
		return models.find { it.name == GEMMA_E4B_MODEL_NAME }
	}

	/**
	 * Find the Whisper model object matching the selected key.
	 */
	fun findWhisperModel(models: List<Model>): Model? {
		val key = _uiState.value.selectedWhisperModel
		val name = when (key) {
			"tiny" -> "Whisper-Tiny"
			"base" -> "Whisper-Base"
			"small" -> "Whisper-Small"
			else -> "Whisper-Base"
		}
		return models.find { it.name == name }
	}

	private fun getFileName(context: Context, uri: Uri): String? {
		return try {
			context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
				val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
				cursor.moveToFirst()
				if (nameIndex >= 0) cursor.getString(nameIndex) else null
			}
		} catch (e: Exception) {
			null
		}
	}
}

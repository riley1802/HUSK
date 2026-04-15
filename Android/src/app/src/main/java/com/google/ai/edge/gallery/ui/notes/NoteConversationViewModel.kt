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

package com.google.ai.edge.gallery.ui.notes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.notes.Note
import com.google.ai.edge.gallery.data.notes.NoteMessage
import com.google.ai.edge.gallery.data.notes.NotesDefaults
import com.google.ai.edge.gallery.data.notes.NotesRepository
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NoteConversationVM"

/** Regex to extract [TITLE: ...] from AI response. */
private val TITLE_REGEX = Regex("""\[TITLE:\s*(.+?)\]""")
/** Regex to extract [TAGS: ...] from AI response. */
private val TAGS_REGEX = Regex("""\[TAGS:\s*(.+?)\]""")

data class NoteConversationUiState(
	val note: Note? = null,
	val isGenerating: Boolean = false,
	val isPreparing: Boolean = false,
	val streamingContent: String = "",
	val isFirstMessage: Boolean = true,
	val error: String? = null,
)

@HiltViewModel
class NoteConversationViewModel @Inject constructor(
	private val notesRepository: NotesRepository,
	private val dataStoreRepository: DataStoreRepository,
) : ViewModel() {

	private val _uiState = MutableStateFlow(NoteConversationUiState())
	val uiState: StateFlow<NoteConversationUiState> = _uiState.asStateFlow()

	/** Messages for the current note, reactive from Room. */
	private var _messages = MutableStateFlow<List<NoteMessage>>(emptyList())
	val messages: StateFlow<List<NoteMessage>> = _messages.asStateFlow()

	private var currentNoteId: String? = null

	/** Load a note and its messages. */
	fun loadNote(noteId: String) {
		currentNoteId = noteId
		viewModelScope.launch {
			val note = notesRepository.getNoteById(noteId)
			val existingMessages = notesRepository.getMessagesForNote(noteId)
				.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

			_uiState.value = _uiState.value.copy(
				note = note,
				isFirstMessage = existingMessages.value.none { it.role == "AGENT" },
			)

			// Collect messages reactively.
			viewModelScope.launch {
				existingMessages.collect { msgs ->
					_messages.value = msgs
					// Update isFirstMessage based on whether we have any agent responses.
					if (msgs.any { it.role == "AGENT" }) {
						_uiState.value = _uiState.value.copy(isFirstMessage = false)
					}
				}
			}
		}
	}

	/**
	 * Send a user message and generate an AI response.
	 * Persists both messages to Room.
	 */
	fun sendMessage(content: String, model: Model) {
		val noteId = currentNoteId ?: return
		if (content.isBlank()) return

		viewModelScope.launch(Dispatchers.Default) {
			// Persist user message.
			notesRepository.addMessage(noteId, "USER", content)

			_uiState.value = _uiState.value.copy(
				isGenerating = true,
				isPreparing = true,
				streamingContent = "",
				error = null,
			)

			// Wait for model to be initialized.
			while (model.instance == null) {
				delay(100)
			}

			try {
				val fullResponse = StringBuilder()
				var firstTokenReceived = false

				val resultListener: (String, Boolean, String?) -> Unit =
					{ partialResult, done, _ ->
						if (!partialResult.startsWith("<ctrl")) {
							if (!firstTokenReceived) {
								firstTokenReceived = true
								_uiState.value = _uiState.value.copy(isPreparing = false)
							}

							fullResponse.append(partialResult)
							_uiState.value = _uiState.value.copy(
								streamingContent = fullResponse.toString(),
							)

							if (done) {
								viewModelScope.launch {
									val responseText = fullResponse.toString()
									val cleanedText = processAiResponse(noteId, responseText)

									// Persist agent message.
									notesRepository.addMessage(noteId, "AGENT", cleanedText)

									_uiState.value = _uiState.value.copy(
										isGenerating = false,
										isPreparing = false,
										streamingContent = "",
									)
								}
							}
						}
					}

				val cleanUpListener: () -> Unit = {
					_uiState.value = _uiState.value.copy(
						isGenerating = false,
						isPreparing = false,
					)
				}

				val errorListener: (String) -> Unit = { message ->
					Log.e(TAG, "Inference error: $message")
					_uiState.value = _uiState.value.copy(
						isGenerating = false,
						isPreparing = false,
						error = message,
					)
				}

				model.runtimeHelper.runInference(
					model = model,
					input = content,
					images = emptyList(),
					audioClips = emptyList(),
					resultListener = resultListener,
					cleanUpListener = cleanUpListener,
					onError = errorListener,
					coroutineScope = viewModelScope,
					extraContext = null,
				)
			} catch (e: Exception) {
				Log.e(TAG, "Failed to run inference", e)
				_uiState.value = _uiState.value.copy(
					isGenerating = false,
					isPreparing = false,
					error = e.message ?: "Inference failed",
				)
			}
		}
	}

	/**
	 * Process the AI response: extract title/tags on first message,
	 * strip the metadata markers from the displayed text.
	 */
	private suspend fun processAiResponse(noteId: String, response: String): String {
		var cleaned = response

		if (_uiState.value.isFirstMessage) {
			// Extract title.
			val titleMatch = TITLE_REGEX.find(response)
			val title = titleMatch?.groupValues?.get(1)?.trim()

			// Extract tags.
			val tagsMatch = TAGS_REGEX.find(response)
			val tagsRaw = tagsMatch?.groupValues?.get(1)?.trim()
			val tagsList = tagsRaw?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
			val tagsJson = NotesRepository.tagsToJson(tagsList)

			if (title != null || tagsList.isNotEmpty()) {
				notesRepository.updateNoteTitleAndTags(
					noteId = noteId,
					title = title ?: "Untitled",
					tags = tagsJson,
				)
				// Refresh the note in UI state.
				val updatedNote = notesRepository.getNoteById(noteId)
				_uiState.value = _uiState.value.copy(
					note = updatedNote,
					isFirstMessage = false,
				)
			}

			// Strip metadata markers from the displayed text.
			cleaned = cleaned.replace(TITLE_REGEX, "").replace(TAGS_REGEX, "").trim()
		}

		return cleaned
	}

	/** Update the note title (user edit). */
	fun updateTitle(newTitle: String) {
		val note = _uiState.value.note ?: return
		val updated = note.copy(title = newTitle, updatedMs = System.currentTimeMillis())
		_uiState.value = _uiState.value.copy(note = updated)
		viewModelScope.launch {
			notesRepository.updateNote(updated)
		}
	}

	/** Build the system instruction Contents for the active model. */
	fun buildSystemInstruction(): Contents {
		val selectedModel = dataStoreRepository.readNotesSelectedModel()
		val prompt = when (selectedModel) {
			NotesDefaults.MODEL_KEY_E2B -> dataStoreRepository.readNotesE2bSystemPrompt()
			else -> dataStoreRepository.readNotesE4bSystemPrompt()
		}
		return Contents.of(listOf(Content.Text(prompt)))
	}

	/** Reset the conversation with the model using the brainstorm system prompt. */
	fun resetModelSession(model: Model) {
		viewModelScope.launch(Dispatchers.Default) {
			while (model.instance == null) {
				delay(100)
			}
			try {
				model.runtimeHelper.resetConversation(
					model = model,
					supportImage = false,
					supportAudio = false,
					systemInstruction = buildSystemInstruction(),
					tools = emptyList(),
					enableConversationConstrainedDecoding = false,
				)
				Log.d(TAG, "Model session reset for notes")
			} catch (e: Exception) {
				Log.e(TAG, "Failed to reset model session", e)
			}
		}
	}

	/** Close the note — triggers RAG indexing. */
	fun closeNote() {
		val noteId = currentNoteId ?: return
		viewModelScope.launch {
			notesRepository.indexNoteForRag(noteId)
		}
	}

	/** Export the note as markdown. */
	suspend fun exportNote(): String? {
		val noteId = currentNoteId ?: return null
		return notesRepository.exportNote(noteId)
	}

	/** Archive the current note. */
	fun archiveNote() {
		val noteId = currentNoteId ?: return
		viewModelScope.launch {
			notesRepository.archiveNote(noteId)
		}
	}
}

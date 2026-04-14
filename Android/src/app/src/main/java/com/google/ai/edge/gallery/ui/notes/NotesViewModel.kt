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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.notes.Note
import com.google.ai.edge.gallery.data.notes.NoteSearchResult
import com.google.ai.edge.gallery.data.notes.NotesDefaults
import com.google.ai.edge.gallery.data.notes.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
	val searchResults: List<NoteSearchResult> = emptyList(),
	val isSearching: Boolean = false,
	val searchQuery: String = "",
	val selectedModel: String = NotesDefaults.DEFAULT_MODEL,
	val e2bSystemPrompt: String = NotesDefaults.DEFAULT_E2B_SYSTEM_PROMPT,
	val e4bSystemPrompt: String = NotesDefaults.DEFAULT_E4B_SYSTEM_PROMPT,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
	private val notesRepository: NotesRepository,
	private val dataStoreRepository: DataStoreRepository,
) : ViewModel() {

	/** All non-archived notes, reactive. */
	val notes: StateFlow<List<Note>> = notesRepository.getAllNotes()
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	/** Recent notes for home card preview. */
	val recentNotes: StateFlow<List<Note>> = notesRepository.getRecentNotes(3)
		.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	private val _uiState = MutableStateFlow(NotesUiState())
	val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

	init {
		loadSettings()
	}

	private fun loadSettings() {
		_uiState.value = _uiState.value.copy(
			selectedModel = dataStoreRepository.readNotesSelectedModel(),
			e2bSystemPrompt = dataStoreRepository.readNotesE2bSystemPrompt(),
			e4bSystemPrompt = dataStoreRepository.readNotesE4bSystemPrompt(),
		)
	}

	/** Create a new note and return it for navigation. */
	suspend fun createNote(): Note {
		return notesRepository.createNote(_uiState.value.selectedModel)
	}

	/** Archive (soft-delete) a note. */
	fun archiveNote(noteId: String) {
		viewModelScope.launch {
			notesRepository.archiveNote(noteId)
		}
	}

	/** Toggle between e2b and e4b models. */
	fun toggleModel() {
		val newModel = if (_uiState.value.selectedModel == NotesDefaults.MODEL_KEY_E2B) {
			NotesDefaults.MODEL_KEY_E4B
		} else {
			NotesDefaults.MODEL_KEY_E2B
		}
		_uiState.value = _uiState.value.copy(selectedModel = newModel)
		dataStoreRepository.saveNotesSelectedModel(newModel)
	}

	/** Set a specific model. */
	fun setModel(modelKey: String) {
		_uiState.value = _uiState.value.copy(selectedModel = modelKey)
		dataStoreRepository.saveNotesSelectedModel(modelKey)
	}

	/** Update the system prompt for a model and persist. */
	fun saveSystemPrompt(modelKey: String, prompt: String) {
		when (modelKey) {
			NotesDefaults.MODEL_KEY_E2B -> {
				_uiState.value = _uiState.value.copy(e2bSystemPrompt = prompt)
				dataStoreRepository.saveNotesE2bSystemPrompt(prompt)
			}
			NotesDefaults.MODEL_KEY_E4B -> {
				_uiState.value = _uiState.value.copy(e4bSystemPrompt = prompt)
				dataStoreRepository.saveNotesE4bSystemPrompt(prompt)
			}
		}
	}

	/** Reset a model's system prompt to default. */
	fun resetSystemPrompt(modelKey: String) {
		val defaultPrompt = when (modelKey) {
			NotesDefaults.MODEL_KEY_E2B -> NotesDefaults.DEFAULT_E2B_SYSTEM_PROMPT
			else -> NotesDefaults.DEFAULT_E4B_SYSTEM_PROMPT
		}
		saveSystemPrompt(modelKey, defaultPrompt)
	}

	/** Get the system prompt for the currently selected model. */
	fun getActiveSystemPrompt(): String {
		return when (_uiState.value.selectedModel) {
			NotesDefaults.MODEL_KEY_E2B -> _uiState.value.e2bSystemPrompt
			else -> _uiState.value.e4bSystemPrompt
		}
	}

	/** Search notes. */
	fun searchNotes(query: String) {
		_uiState.value = _uiState.value.copy(searchQuery = query, isSearching = query.isNotBlank())
		if (query.isBlank()) {
			_uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
			return
		}
		viewModelScope.launch {
			val results = notesRepository.search(query)
			_uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
		}
	}

	/** Clear search state. */
	fun clearSearch() {
		_uiState.value = _uiState.value.copy(
			searchQuery = "",
			searchResults = emptyList(),
			isSearching = false,
		)
	}
}

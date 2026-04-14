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

package com.google.ai.edge.gallery.data.notes

import android.util.Log
import com.google.ai.edge.gallery.data.rag.RagManager
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotesRepository"

/**
 * Repository wrapping [NotesDao]. Coordinates CRUD, search, RAG indexing on
 * note close, and export formatting.
 */
@Singleton
class NotesRepository @Inject constructor(
	private val notesDao: NotesDao,
	private val ragManager: RagManager,
) {
	// ---- Note CRUD ----

	/** All non-archived notes as a reactive Flow. */
	fun getAllNotes(): Flow<List<Note>> = notesDao.getAllNotes()

	/** Recent notes for home card preview. */
	fun getRecentNotes(limit: Int = 3): Flow<List<Note>> = notesDao.getRecentNotes(limit)

	/** Get a single note by ID. */
	suspend fun getNoteById(noteId: String): Note? = notesDao.getNoteById(noteId)

	/** Create a new blank note. Returns the created note. */
	suspend fun createNote(modelId: String): Note {
		val now = System.currentTimeMillis()
		val note = Note(
			id = UUID.randomUUID().toString(),
			title = "New Note",
			tags = "[]",
			modelId = modelId,
			createdMs = now,
			updatedMs = now,
			lastAccessedMs = now,
		)
		notesDao.insertNote(note)
		Log.d(TAG, "Created note: id=${note.id}")
		return note
	}

	/** Update note metadata (title, tags, model). */
	suspend fun updateNote(note: Note) {
		notesDao.updateNote(note)
	}

	/** Update just the title and tags after AI generates them. */
	suspend fun updateNoteTitleAndTags(noteId: String, title: String, tags: String) {
		val existing = notesDao.getNoteById(noteId) ?: return
		notesDao.updateNote(
			existing.copy(
				title = title,
				tags = tags,
				updatedMs = System.currentTimeMillis(),
			)
		)
		Log.d(TAG, "Updated note title/tags: id=$noteId, title=$title")
	}

	/** Soft-delete a note. */
	suspend fun archiveNote(noteId: String) {
		notesDao.archiveNote(noteId)
		Log.d(TAG, "Archived note: id=$noteId")
	}

	/** Hard-delete a note and its messages. */
	suspend fun deleteNote(noteId: String) {
		notesDao.deleteNote(noteId)
		Log.d(TAG, "Deleted note: id=$noteId")
	}

	// ---- Message CRUD ----

	/** Messages for a note as a reactive Flow. */
	fun getMessagesForNote(noteId: String): Flow<List<NoteMessage>> =
		notesDao.getMessagesForNote(noteId)

	/** Add a message to a note. Also updates the note's updatedMs. */
	suspend fun addMessage(
		noteId: String,
		role: String,
		content: String,
		isThinking: Boolean = false,
	): NoteMessage {
		val now = System.currentTimeMillis()
		val message = NoteMessage(
			id = UUID.randomUUID().toString(),
			noteId = noteId,
			role = role,
			content = content,
			timestampMs = now,
			isThinking = isThinking,
		)
		notesDao.insertMessage(message)

		// Touch the parent note's updated timestamp.
		val note = notesDao.getNoteById(noteId)
		if (note != null) {
			notesDao.updateNote(note.copy(updatedMs = now))
		}
		return message
	}

	/** Get the last non-system message for preview. */
	suspend fun getLastMessage(noteId: String): NoteMessage? =
		notesDao.getLastMessage(noteId)

	// ---- Search ----

	/**
	 * Search notes by title/tags and message content. Returns combined results
	 * ranked: title matches first, then message matches, each sorted by recency.
	 */
	suspend fun search(query: String): List<NoteSearchResult> {
		val results = mutableListOf<NoteSearchResult>()

		// FTS4 requires query words joined with AND for multi-word queries.
		val ftsQuery = query.trim().split("\\s+".toRegex()).joinToString(" ")
		if (ftsQuery.isBlank()) return results

		try {
			// Title/tag matches
			val titleMatches = notesDao.searchNotesByTitle(ftsQuery)
			for (note in titleMatches) {
				val matchType = if (note.tags.contains(query, ignoreCase = true)) {
					MatchType.TAG
				} else {
					MatchType.TITLE
				}
				results.add(
					NoteSearchResult(
						note = note,
						snippet = note.title,
						matchType = matchType,
					)
				)
			}

			// Message content matches
			val messageMatches = notesDao.searchMessageContent(ftsQuery)
			val seenNoteIds = titleMatches.map { it.id }.toSet()
			for (message in messageMatches) {
				val note = notesDao.getNoteById(message.noteId) ?: continue
				if (note.isArchived) continue

				// Build a truncated snippet around the match.
				val snippet = buildSnippet(message.content, query)
				results.add(
					NoteSearchResult(
						note = note,
						matchedMessage = message,
						snippet = snippet,
						matchType = MatchType.MESSAGE,
					)
				)
			}
		} catch (e: Exception) {
			Log.e(TAG, "Search failed for query: $query", e)
		}

		return results
	}

	// ---- RAG Integration ----

	/**
	 * Index a note's conversation into the RAG vector store.
	 * Called when the user navigates away from a note.
	 */
	suspend fun indexNoteForRag(noteId: String) {
		val note = notesDao.getNoteById(noteId) ?: return
		val messages = notesDao.getMessagesForNoteOnce(noteId)
		if (messages.isEmpty()) return

		val text = messages.joinToString("\n\n") { msg ->
			val roleLabel = when (msg.role) {
				"USER" -> "[User]"
				"AGENT" -> "[AI]"
				else -> "[System]"
			}
			"$roleLabel: ${msg.content}"
		}

		try {
			// Delete existing RAG document for this note if it exists.
			val sourceUri = "note://$noteId"
			ragManager.deleteDocumentBySourceUri(sourceUri)

			// Ingest the concatenated conversation as a text document.
			ragManager.ingestText(
				text = text,
				name = note.title,
				sourceUri = sourceUri,
			)
			Log.d(TAG, "Indexed note for RAG: id=$noteId, title=${note.title}")
		} catch (e: Exception) {
			Log.e(TAG, "Failed to index note for RAG: id=$noteId", e)
		}
	}

	// ---- Export ----

	/** Format a note + conversation as markdown for sharing. */
	suspend fun exportNote(noteId: String): String? {
		val note = notesDao.getNoteById(noteId) ?: return null
		val messages = notesDao.getMessagesForNoteOnce(noteId)

		val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

		return buildString {
			appendLine("# ${note.title}")
			appendLine("Tags: ${parseTagsForDisplay(note.tags)}")
			appendLine("Created: ${dateFormat.format(Date(note.createdMs))}")
			appendLine()
			appendLine("---")
			appendLine()
			for (msg in messages) {
				if (msg.isThinking) continue
				val label = when (msg.role) {
					"USER" -> "**You**"
					"AGENT" -> "**AI**"
					else -> continue
				}
				appendLine("$label: ${msg.content}")
				appendLine()
			}
		}
	}

	// ---- Helpers ----

	private fun buildSnippet(content: String, query: String, maxLen: Int = 120): String {
		val idx = content.indexOf(query, ignoreCase = true)
		if (idx < 0) return content.take(maxLen)
		val start = (idx - 40).coerceAtLeast(0)
		val end = (idx + query.length + 40).coerceAtMost(content.length)
		val prefix = if (start > 0) "..." else ""
		val suffix = if (end < content.length) "..." else ""
		return "$prefix${content.substring(start, end)}$suffix"
	}

	companion object {
		/** Parse a JSON tag array string to a display string (e.g. "api, architecture"). */
		fun parseTagsForDisplay(tagsJson: String): String {
			return try {
				tagsJson
					.removeSurrounding("[", "]")
					.split(",")
					.map { it.trim().removeSurrounding("\"") }
					.filter { it.isNotBlank() }
					.joinToString(", ")
			} catch (_: Exception) {
				""
			}
		}

		/** Parse a JSON tag array string to a list. */
		fun parseTags(tagsJson: String): List<String> {
			return try {
				tagsJson
					.removeSurrounding("[", "]")
					.split(",")
					.map { it.trim().removeSurrounding("\"") }
					.filter { it.isNotBlank() }
			} catch (_: Exception) {
				emptyList()
			}
		}

		/** Convert a list of tags to a JSON array string. */
		fun tagsToJson(tags: List<String>): String {
			return "[${tags.joinToString(", ") { "\"$it\"" }}]"
		}
	}
}

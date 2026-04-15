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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for brainstorm notes. Provides reactive queries via Flow,
 * FTS4 full-text search, and standard CRUD operations.
 */
@Dao
interface NotesDao {

	// ---- Note queries ----

	/** All non-archived notes, newest first. Reactive via Flow. */
	@Query("SELECT * FROM notes WHERE is_archived = 0 ORDER BY updated_ms DESC")
	fun getAllNotes(): Flow<List<Note>>

	/** Most recently updated notes for home card preview. */
	@Query("SELECT * FROM notes WHERE is_archived = 0 ORDER BY updated_ms DESC LIMIT :limit")
	fun getRecentNotes(limit: Int = 3): Flow<List<Note>>

	/** Get a single note by ID. */
	@Query("SELECT * FROM notes WHERE id = :noteId")
	suspend fun getNoteById(noteId: String): Note?

	/** Insert a new note. Replaces on conflict. */
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertNote(note: Note)

	/** Update an existing note. */
	@Update
	suspend fun updateNote(note: Note)

	/** Soft-delete: mark a note as archived. */
	@Query("UPDATE notes SET is_archived = 1 WHERE id = :noteId")
	suspend fun archiveNote(noteId: String)

	/** Hard-delete a note (cascade deletes messages). */
	@Query("DELETE FROM notes WHERE id = :noteId")
	suspend fun deleteNote(noteId: String)

	// ---- Message queries ----

	/** All messages for a note, ordered by timestamp. Reactive via Flow. */
	@Query("SELECT * FROM note_messages WHERE note_id = :noteId ORDER BY timestamp_ms ASC")
	fun getMessagesForNote(noteId: String): Flow<List<NoteMessage>>

	/** All messages for a note (non-reactive, for export/RAG). */
	@Query("SELECT * FROM note_messages WHERE note_id = :noteId ORDER BY timestamp_ms ASC")
	suspend fun getMessagesForNoteOnce(noteId: String): List<NoteMessage>

	/** Get a single message by ID. */
	@Query("SELECT * FROM note_messages WHERE id = :messageId")
	suspend fun getMessageById(messageId: String): NoteMessage?

	/** Insert a new message. */
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertMessage(message: NoteMessage)

	/** Get the most recent message for a note (for preview). */
	@Query(
		"""
		SELECT * FROM note_messages
		WHERE note_id = :noteId AND role != 'SYSTEM'
		ORDER BY timestamp_ms DESC
		LIMIT 1
		"""
	)
	suspend fun getLastMessage(noteId: String): NoteMessage?

	/** Get total message count for a note. */
	@Query("SELECT COUNT(*) FROM note_messages WHERE note_id = :noteId")
	suspend fun getMessageCount(noteId: String): Int

	// ---- Search ----

	/**
	 * Full-text search across note titles and tags via FTS4.
	 * Returns notes matching the query, newest first.
	 */
	@Query(
		"""
		SELECT notes.* FROM notes
		JOIN notes_fts ON notes.rowid = notes_fts.rowid
		WHERE notes_fts MATCH :query AND notes.is_archived = 0
		ORDER BY notes.updated_ms DESC
		"""
	)
	suspend fun searchNotesByTitle(query: String): List<Note>

	/**
	 * Full-text search across message content via FTS4.
	 * Returns messages matching the query with their note IDs.
	 */
	@Query(
		"""
		SELECT note_messages.* FROM note_messages
		JOIN note_messages_fts ON note_messages.rowid = note_messages_fts.rowid
		WHERE note_messages_fts MATCH :query
		ORDER BY note_messages.timestamp_ms DESC
		"""
	)
	suspend fun searchMessageContent(query: String): List<NoteMessage>
}

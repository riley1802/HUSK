# Notes & Brainstorm Feature — Design Spec

## Context

HUSK currently has a primary "Talk" chat and several secondary task screens, but no way to quickly capture and develop ideas throughout the day. The Notes feature adds an AI-driven brainstorming companion as a first-class home screen card. Users jot ideas, the AI proactively asks follow-up questions to flesh them out, and everything is persisted, searchable, and auto-indexed into RAG so Talk can reference past brainstorms.

## Requirements Summary

- **Home screen card**: Full-width between Talk and secondary grid, showing 2-3 recent note previews + search icon
- **AI-driven brainstorm**: After the user drops an idea, the AI asks follow-up technical questions unprompted
- **One conversation per note**: Each idea gets its own persistent thread
- **Auto-tagging**: AI categorizes notes with tags automatically — no manual management
- **Persistent**: Notes + full conversation history saved to Room database
- **Search with jump-to-context**: FTS4 search across titles, tags, and message content; tapping a result scrolls to that message
- **Model toggle**: In-screen e2b/e4b toggle in the Notes header
- **Per-model system prompts**: User-editable brainstorm prompts for each model, stored in Settings DataStore
- **RAG auto-indexing**: On note close, conversation content is chunked and embedded into the existing RAG vector store
- **Export**: Share/export individual notes

---

## Data Layer

### New Room Database: `husk_notes.db`

**`Note` entity:**

| Field | Type | Notes |
|-------|------|-------|
| id | String (UUID) | Primary key |
| title | String | AI-generated on first message, editable |
| tags | String | JSON array of tag strings, AI-generated |
| modelId | String | Which model was used (e2b / e4b) |
| createdMs | Long | Creation timestamp |
| updatedMs | Long | Last message timestamp |
| lastAccessedMs | Long | Last opened timestamp |
| isArchived | Boolean | Soft delete |

**`NoteMessage` entity:**

| Field | Type | Notes |
|-------|------|-------|
| id | String (UUID) | Primary key |
| noteId | String | FK → Note.id, CASCADE delete |
| role | String | "USER", "AGENT", or "SYSTEM" |
| content | String | Message text (markdown) |
| timestampMs | Long | When sent |
| isThinking | Boolean | Whether this is a thinking/reasoning message |

**`NoteFts` virtual table:** FTS4 on `Note.title` + `Note.tags` for title/tag search.

**`NoteMessageFts` virtual table:** FTS4 on `NoteMessage.content` for full-text message search.

### NotesDao

- `getAllNotes(): Flow<List<Note>>` — reactive list for Notes list view, ordered by `updatedMs` desc
- `getRecentNotes(limit: Int): Flow<List<Note>>` — for home card previews (limit = 3)
- `getMessagesForNote(noteId: String): Flow<List<NoteMessage>>` — reactive message list for conversation view
- `searchNotes(query: String): List<NoteSearchResult>` — FTS4 JOIN across both tables, returns note + matched message + snippet
- `insertNote(note: Note)`, `updateNote(note: Note)`, `deleteNote(noteId: String)`
- `insertMessage(message: NoteMessage)`
- `getNoteById(noteId: String): Note?`
- `getMessageById(messageId: String): NoteMessage?`

### NotesRepository

Singleton wrapping `NotesDao`. Responsibilities:
- Standard CRUD delegation
- Tag parsing (JSON string ↔ List<String>)
- `closeNote(noteId: String)` — triggers RAG indexing via `RagManager`
- `exportNote(noteId: String): String` — formats note + conversation as markdown for sharing

### DI

Provided in `AppModule` following the same pattern as `MemoryDatabase`:
- `provideNotesDatabase(@ApplicationContext context: Context): NotesDatabase`
- `provideNotesDao(database: NotesDatabase): NotesDao`
- `provideNotesRepository(notesDao: NotesDao, ragManager: RagManager): NotesRepository`

---

## UI

### Home Screen Card — `HuskNotesCard`

**Location**: In `HuskHub`, between `HuskTalkCard` and `HuskSecondaryGrid`.

**Layout**:
- Full-width, `RoundedCornerShape(28.dp)`, `taskCardBgColor` background, `1.dp` outline border
- **Header row**: "Notes" title (left, with 📝 icon) + search icon button (right)
- **Preview list**: 2-3 recent notes, each as a compact row:
  - Tag chip(s) + relative timestamp (e.g. "Today", "Yesterday")
  - First line of most recent message content, truncated
- **Empty state**: "Jot down an idea to get started" placeholder with subtle + icon

**Interactions**:
- Tap card body → navigate to Notes list screen
- Tap search icon → navigate to Notes list with search bar focused
- Tap a preview row → navigate directly to that note's conversation

**Data**: Observes `NotesRepository.getRecentNotes(3)` via a ViewModel.

### Notes List Screen

**Top bar**:
- Title: "Notes"
- Model toggle: Segmented button / chip selector for e2b and e4b
- Settings gear icon → opens system prompt editor bottom sheet

**Search bar**: Below the top bar. Typing searches via FTS4 across titles, tags, and message content. Results show:
- Note title + tag chips + timestamp
- Matched message snippet with highlight
- Tapping a result navigates to the Note Conversation View with `targetMessageId` for scroll-to-context

**Note list**: `LazyColumn` of note cards sorted by `updatedMs` desc. Each card shows:
- Title
- Tags as colored chips
- Relative timestamp
- First line of last message as preview
- Swipe-to-archive gesture (optional, can defer)

**FAB**: "New Note" button → creates a new `Note` entity, navigates to empty conversation view.

### Note Conversation View

**Top bar**:
- Back arrow → triggers note close (RAG indexing)
- Note title (tappable to edit inline)
- Overflow menu: Export, Archive/Delete

**Chat UI**: Reuses `ChatPanel` composable from `ui/common/chat/`:
- Same bubble layout, markdown rendering (`MarkdownText`), thinking indicators
- Messages loaded from `NotesDao.getMessagesForNote(noteId)` as a `Flow`
- New messages are persisted to Room immediately via `NotesDao.insertMessage()`

**AI behavior**:
- System prompt injected based on selected model's custom brainstorm prompt
- On first user message: AI responds with brainstorm follow-ups AND generates a title + tags (stored on the `Note` entity via a structured output or parsed from the response)
- On subsequent messages: AI continues brainstorming, proactively asking deeper questions
- Streaming works the same as Talk — incremental content updates, but each completed message is persisted to Room

**Scroll-to-context**: When opened with a `targetMessageId` (from search), the LazyColumn scrolls to that message index and applies a brief highlight animation.

**Export**: From the overflow menu, `NotesRepository.exportNote()` formats as:
```
# {title}
Tags: {tags}
Created: {date}

---

**You**: {message}

**AI**: {response}

...
```
Shared via Android's `ShareSheet` (Intent.ACTION_SEND, text/plain or text/markdown).

### System Prompt Settings

Accessed via gear icon in Notes list top bar. Bottom sheet with:
- Two text fields: "e2b System Prompt" and "e4b System Prompt"
- Pre-filled with a default brainstorm prompt (see below)
- Stored as new fields in the existing `Settings` proto DataStore

**Default brainstorm system prompt**:
```
You are a brainstorming partner. When the user shares an idea, help them develop it by:
1. Asking specific, probing technical questions one at a time
2. Identifying potential challenges and edge cases
3. Suggesting related approaches or alternatives
4. Building on their answers to go deeper

Keep responses concise. Ask one focused question at a time. Drive the conversation forward — don't just summarize what they said.

After the first message, generate a short title and 1-3 relevant tags for this note in the format:
[TITLE: your title here]
[TAGS: tag1, tag2, tag3]
```

---

## Search

### FTS4 Implementation

Search query hits both FTS4 tables with a UNION query, returning `NoteSearchResult`:

```kotlin
data class NoteSearchResult(
	val note: Note,
	val matchedMessage: NoteMessage?,  // null if match was title/tag only
	val snippet: String,               // FTS4 snippet with highlights
	val matchType: MatchType           // TITLE, TAG, or MESSAGE
)
```

**Ranking**: Title matches first, then tag matches, then message content matches. Within each group, sorted by `note.updatedMs` desc.

**Jump-to-context**: When a message match is tapped, navigate with `noteId + targetMessageId`. The conversation view finds the message index in the loaded list and calls `LazyListState.animateScrollToItem(index)`, then applies a brief background color flash on that message bubble.

---

## RAG Integration

### Auto-indexing on Note Close

When the user navigates back from a note conversation:

1. `NotesRepository.closeNote(noteId)` fires
2. Concatenate all messages into a single document string:
   ```
   [User]: {message}
   [AI]: {response}
   ...
   ```
3. Check if a RAG `Document` already exists with `sourceUri = "note://{noteId}"`
   - If yes: delete existing chunks, re-ingest (handles notes that grow over sessions)
   - If no: create new `Document` entry
4. Call `RagManager.ingestDocument()` with the concatenated content — same pipeline as knowledge base documents (600-char chunks, 100-char overlap, batch embedding, vector store insertion)
5. Document metadata: `name = note.title`, `sourceUri = "note://{noteId}"`, `mimeType = "text/plain"`

**Result**: Notes become searchable from Talk's RAG retrieval. "What was that idea about WebSockets?" in Talk will find relevant note content.

---

## Navigation

New routes added to `GalleryNavGraph.kt`:

- `notes_list` → Notes list screen
- `notes_conversation/{noteId}?targetMessageId={messageId}` → Note conversation view

Home card navigates to `notes_list`. Search results and preview row taps navigate to `notes_conversation/{noteId}`.

---

## New Files

| File | Purpose |
|------|---------|
| `data/notes/Note.kt` | Note entity |
| `data/notes/NoteMessage.kt` | NoteMessage entity |
| `data/notes/NoteFts.kt` | FTS4 virtual table entity |
| `data/notes/NoteMessageFts.kt` | FTS4 virtual table entity |
| `data/notes/NotesDatabase.kt` | Room database definition |
| `data/notes/NotesDao.kt` | DAO with search queries |
| `data/notes/NotesRepository.kt` | Repository + RAG indexing coordination |
| `ui/notes/NotesViewModel.kt` | ViewModel for notes list + search |
| `ui/notes/NoteConversationViewModel.kt` | ViewModel for individual note chat |
| `ui/notes/NotesListScreen.kt` | Notes list + search UI |
| `ui/notes/NoteConversationScreen.kt` | Note chat screen |
| `ui/home/HuskNotesCard.kt` | Home screen card composable |

## Modified Files

| File | Change |
|------|--------|
| `ui/home/HomeScreen.kt` | Add `HuskNotesCard` to `HuskHub` between Talk and secondary grid |
| `ui/navigation/GalleryNavGraph.kt` | Add `notes_list` and `notes_conversation` routes |
| `di/AppModule.kt` | Provide NotesDatabase, NotesDao, NotesRepository |
| `src/main/proto/settings.proto` | Add `string notes_e2b_system_prompt` and `string notes_e4b_system_prompt` fields |
| `data/rag/RagManager.kt` | Minor: accept `sourceUri` parameter for note-sourced documents |

---

## Verification

1. **Data persistence**: Create a note, send messages, force-close app, reopen — note and messages should be intact
2. **Home card**: Verify recent notes preview updates in real-time as notes are created/modified
3. **Search**: Search for a word in a message body, verify result shows snippet, tap it, verify scroll-to-context with highlight
4. **Model toggle**: Switch between e2b/e4b, verify the correct model is used for inference and persisted on the note
5. **System prompts**: Edit a model's system prompt in settings, start a new note, verify the AI uses the updated prompt
6. **RAG indexing**: Create a note about topic X, close it, go to Talk, ask about topic X — verify RAG retrieves the note content
7. **Export**: Open a note, tap export, verify the ShareSheet shows formatted markdown content
8. **Empty state**: Fresh install with no notes — home card shows placeholder, notes list shows empty state
9. **Auto-tagging**: Send first message in a new note, verify title and tags are generated and displayed

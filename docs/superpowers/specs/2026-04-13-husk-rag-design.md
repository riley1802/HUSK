# HUSK RAG System — Design Specification

**Date:** 2026-04-13
**Status:** Draft
**Target:** Samsung Galaxy Z Fold 7 (Snapdragon 8 Elite, 12 GB RAM)
**Stack:** Kotlin / Jetpack Compose / Google AI Edge RAG SDK / LiteRT-LM

---

## Context

HUSK is an on-device AI assistant that runs Gemma 4 E2B/E4B locally via LiteRT-LM. It currently has a 3-layer memory system (L1 hot memory, L2 Room+FTS4, L3 MCP) and agent skills with tool calling. Users can chat with the model but have no way to ground responses in their own documents. RAG transforms HUSK from a generic chatbot into a personal knowledge assistant — users upload documents, and the model's responses are grounded in that content, entirely offline.

This spec covers Phase B: foundation RAG pipeline + knowledge base management UI + source attribution. The architecture is designed so Phase C features (photo/screenshot RAG, auto-indexing, MCP integration, semantic search UI) slot in without structural changes.

---

## Architecture

### Overview

A new **RagManager** layer sits parallel to the existing memory system. It does not extend or modify the L1/L2/L3 memory architecture — RAG is document knowledge, memory is episodic/behavioral knowledge. They share the inference pipeline but have separate storage and retrieval paths.

```
UI Layer
├── Chat Screen (+ source attribution)
├── Knowledge Base Manager Screen (new)
└── Document Import (file picker + share intent)
         │
         ▼
RAG Manager (new)
├── Ingestion Pipeline: parse → chunk → embed → store
├── Retrieval Pipeline: embed query → search → rank → return
└── RagToolSet: @Tool methods exposed to LLM
         │
         ▼
Google AI Edge RAG SDK
├── TextChunker
├── Embedder (Gecko / EmbeddingGemma)
├── SemanticMemory (top-k cosine similarity)
└── SqliteVectorStore (VectorStore interface)
         │
         ▼
Existing Systems (unchanged)
├── LiteRT-LM inference
├── L1 HotMemoryStore
├── L2 MemoryRepository
├── L3 McpManager
└── AgentTools
```

### Key Boundaries

- **RagManager** owns all document lifecycle: ingestion, storage, retrieval, deletion
- **LlmChatViewModel** calls RagManager.retrieve() before inference and injects results into the system prompt
- **RagToolSet** exposes RAG operations as `@Tool` methods so the model can search documents during agentic reasoning
- **SqliteVectorStore** sits behind the SDK's `VectorStore` interface — swappable to ObjectBox later without touching RagManager

---

## Data Model

### Room Entities (in RagDatabase — separate from MemoryDatabase)

```kotlin
@Entity(tableName = "documents")
data class Document(
    @PrimaryKey val id: String,          // UUID
    val name: String,                     // original filename
    val mimeType: String,                 // text/plain, application/pdf, text/markdown
    val sourceUri: String?,               // original file URI (nullable for share intents)
    val collectionId: String,             // default: "global" (ready for per-conversation scoping)
    val chunkCount: Int,
    val totalTokenEstimate: Int,
    val status: IngestionStatus,          // PENDING, PROCESSING, READY, FAILED
    val errorMessage: String?,            // null unless FAILED
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "document_chunks")
data class DocumentChunk(
    @PrimaryKey val id: String,           // UUID
    val documentId: String,               // FK → Document
    val chunkIndex: Int,                  // ordering within document
    val content: String,                  // the actual text
    val tokenEstimate: Int,
    val metadata: String?                 // JSON — page number, heading, etc.
)

enum class IngestionStatus {
    PENDING, PROCESSING, READY, FAILED
}
```

### Vector Storage

- **SqliteVectorStore** from the AI Edge RAG SDK
- Separate SQLite database file from Room (SDK manages its own schema)
- Vectors keyed by `DocumentChunk.id`
- Dimensions: 256 (Gecko) or 128–768 (EmbeddingGemma, configurable)

### Storage Budget

- Gecko 256d: ~1 KB per chunk embedding
- 100-page PDF ≈ 500 chunks ≈ 500 KB vectors
- Budget: 1–2 GB for vector storage (supports ~2,000–4,000 PDFs)

---

## Ingestion Pipeline

### Flow

```
User Action (File Picker / Share Intent)
    → DocumentParser
        ├── TextParser: .txt, .md → raw UTF-8
        └── PdfParser: .pdf → extracted text (PDFBox-Android)
    → TextChunker (AI Edge RAG SDK)
        ├── Strategy: fixed-size with overlap
        ├── Chunk size: 512 tokens
        └── Overlap: 50 tokens
    → Embedder (AI Edge RAG SDK)
        ├── Model: Gecko (default) or EmbeddingGemma
        └── Batch embedding (all chunks in one pass)
    → Storage (parallel writes)
        ├── Room: Document + DocumentChunk entities
        └── SqliteVectorStore: vectors keyed by chunk ID
    → Status: PROCESSING → READY (or FAILED)
```

### Details

- **Background processing**: Runs on `Dispatchers.IO`. Large PDFs may take several seconds. UI shows progress via `IngestionStatus` + percentage.
- **Batch embedding**: All chunks embedded in a single pass for throughput. Gecko on Snapdragon 8 Elite handles 500 chunks in a few seconds.
- **Share Intent**: HUSK registers as a share target for `text/*` and `application/pdf` MIME types. Shared documents enter the ingestion pipeline with a confirmation toast.
- **Error handling**: Parse or embed failures set status to FAILED with `errorMessage`. User can retry or delete from the KB Manager. Image-only PDFs (no extractable text) produce a clear error message.
- **Duplicate detection**: Hash the raw extracted text. If an identical hash exists, skip re-ingestion and notify the user.

### Dependencies

```kotlin
// AI Edge RAG SDK
implementation("com.google.ai.edge.localagents:localagents-rag:0.1.0")
implementation("com.google.mediapipe:tasks-genai:0.10.22")

// PDF parsing
implementation("com.tom-roush:pdfbox-android:2.0.27.0")  // ~2.5 MB
```

---

## Retrieval Pipeline

### Automatic Retrieval (on every message)

```
User sends message
    → Embedder.embed(userQuery)
    → SemanticMemory.search(queryVector, topK=5, threshold=0.3)
    → Filter: drop chunks below 0.3 cosine similarity
    → Token budget: cap total retrieved context at ~1000 tokens
    → Inject into system prompt (after L1 hot memory, before tool definitions):
        "Relevant knowledge from user's documents:
         [Source: meeting-notes.pdf, relevance: 0.82]
         {chunk content}
         [Source: project-spec.md, relevance: 0.71]
         {chunk content}"
    → LiteRT-LM inference (unchanged)
    → Post-processing: attach ChunkResult metadata to ChatMessage for UI attribution
```

### Tool-Based Retrieval (agentic)

The model can also call RAG tools explicitly during reasoning:

```kotlin
@ToolSet
class RagToolSet(private val ragManager: RagManager) {

    @Tool("Search the user's knowledge base for relevant documents")
    fun searchDocuments(
        @ToolParam("Search query") query: String,
        @ToolParam("Max results (1-10)") topK: Int = 5
    ): String  // Returns JSON array of {documentName, chunkContent, relevance}

    @Tool("List all documents in the knowledge base")
    fun listDocuments(): String  // Returns JSON array of {name, chunkCount, status}

    @Tool("Get the status of the knowledge base")
    fun knowledgeBaseStatus(): String  // Returns {documentCount, chunkCount, vectorSizeMb, embeddingModel}
}
```

### Parameters

| Parameter | Default | Rationale |
|-----------|---------|-----------|
| topK | 5 | Balances relevance coverage with token budget |
| threshold | 0.3 | Cosine similarity floor — below this, chunks are noise |
| token budget | ~1000 | Preserves context window for conversation history + L1 memory |

### RAG Toggle

A per-conversation toggle in the chat header enables/disables automatic retrieval. Defaults to ON when the knowledge base is non-empty. Tool-based retrieval via RagToolSet is always available regardless of the toggle.

---

## Embedding Models

### Gecko (Default)

- Size: ~30 MB
- Dimensions: 256 (or 768)
- Source: Google AI Edge / MediaPipe
- Strengths: Tiny footprint, officially supported, negligible resource cost
- Downloaded through existing HF model pipeline

### EmbeddingGemma (Optional Upgrade)

- Size: ~200 MB
- Dimensions: 128–768 (configurable)
- Source: Google (Sept 2025)
- Strengths: Higher quality embeddings, purpose-built for edge
- Downloaded as an optional model from Settings → RAG section

### Model Switching

- Switching embedding models requires re-indexing all documents (vectors from different models aren't compatible)
- Settings UI shows a warning and "Re-index all" button when switching
- Re-indexing runs in background with progress tracking

---

## UI: Knowledge Base Manager

### Navigation

Accessible from the main navigation drawer, same level as "Models Manager" and "Settings".

### Main View

- **Top bar**: "Knowledge Base" title, back arrow, settings gear (links to RAG settings)
- **Stats bar** (surfaceHigh card): document count, chunk count, vector storage size, active embedding model name (in success color)
- **Document list**: Surface-colored cards (12dp radius) with:
  - Document name (15sp, semibold)
  - Subtitle: chunk count · vector size · date added (12sp, muted)
  - Status badge (uppercase, 11sp, bold): Ready (success), Indexing (warning + progress bar), Failed (error + message)
- **Add Document button**: Sand accent, 12dp radius, bottom of list

### Document Detail View (on tap)

- **Top bar**: Document name, back arrow, Delete action (error color)
- **Metadata card** (surface bg): 2-column grid showing type, original size, chunks, vector size, date, embedding model (accent colored), collection, status
- **Chunk preview section**: Scrollable list of chunk cards (surfaceHigh bg) showing index, total count, and truncated content
- **Actions**: Re-index button (surfaceHigh bg)

### Styling

All components use HUSK's existing design tokens:
- Background: `#0E1013` (huskBackground)
- Cards: `#15181C` (huskSurface), 12dp corners
- Elevated: `#1B1F24` (huskSurfaceHigh)
- Accent: `#D6C9A8` (sand)
- Status: `#7E9B7A` (success), `#C9A86E` (warning), `#C97A6E` (error)
- Text: `#E6E8EC` (primary), `#A0A6B0` (secondary), `#6B7280` (muted)
- Font: Nunito, consistent weight hierarchy
- Spacing: 24dp horizontal padding, 16dp card padding, 8dp card gaps

---

## UI: Source Attribution in Chat

### Design

Source attribution attaches below the model's response bubble as a collapsible panel:

- **Collapsed (default)**: Single toggle bar showing "▸ Sources · N documents" in muted uppercase text. Sits directly below the response bubble with 2dp top radius for visual continuity.
- **Expanded (on tap)**: Reveals source cards, each with:
  - Sand accent left bar (brighter for higher relevance, muted for lower)
  - Document name (12sp, bold, secondary color)
  - Relevance percentage (accent colored)
  - Chunk preview (12sp, muted, 2-line truncation)
- **No sources**: When nothing is retrieved above threshold, no sources section appears. The response looks like a normal chat bubble.
- **Animation**: `AnimatedVisibility` for expand/collapse, matching existing `ModelItem` expand pattern.

### Data

Each `ChatMessage` with RAG context stores a `List<ChunkResult>`:

```kotlin
data class ChunkResult(
    val documentName: String,
    val chunkContent: String,
    val relevanceScore: Float,      // 0.0–1.0
    val documentId: String,
    val chunkId: String
)
```

---

## RAG Settings

Added to the existing Settings dialog as a new "RAG" section:

- **Active embedding model**: Selector (Gecko / EmbeddingGemma) with download button for EmbeddingGemma if not installed
- **Re-index all**: Button shown when switching models, with warning about incompatible vectors
- **Auto-retrieve**: Toggle for automatic RAG retrieval on every message (default: on)
- **Top-K results**: Slider (1–10, default: 5)
- **Relevance threshold**: Slider (0.1–0.9, default: 0.3)
- **Clear knowledge base**: Destructive action with confirmation dialog

---

## New Files

```
data/rag/
├── RagManager.kt              # Top-level coordinator (ingestion + retrieval)
├── RagRepository.kt           # Room DAO + database operations
├── RagDatabase.kt             # Room database (Document + DocumentChunk tables)
├── Document.kt                # Document entity
├── DocumentChunk.kt           # DocumentChunk entity
├── IngestionStatus.kt         # Enum
├── ChunkResult.kt             # Retrieval result data class
├── RagToolSet.kt              # @Tool methods for LLM
├── parser/
│   ├── DocumentParser.kt      # Interface
│   ├── TextParser.kt          # .txt, .md parsing
│   └── PdfParser.kt           # .pdf parsing via PDFBox-Android
└── embedding/
    └── EmbeddingModelManager.kt  # Gecko/EmbeddingGemma lifecycle

ui/knowledgebase/
├── KnowledgeBaseScreen.kt     # Main list view
├── KnowledgeBaseViewModel.kt  # State management
├── DocumentDetailScreen.kt    # Detail view with chunk previews
└── SourceAttribution.kt       # Collapsible sources composable for chat

di/
└── RagModule.kt               # Hilt module for RAG dependencies
```

## Modified Files

```
ui/llmchat/LlmChatViewModel.kt    # Add RAG retrieval before inference
ui/llmchat/LlmChatModelHelper.kt  # Include RAG context in system prompt
ui/common/chat/ChatPanel.kt        # Render source attribution below responses
ui/home/HomeScreen.kt              # Add KB Manager to navigation drawer
ui/home/SettingsDialog.kt          # Add RAG settings section
data/Model.kt                      # Add embedding model definitions
AndroidManifest.xml                # Register share intent filters
build.gradle                       # Add RAG SDK + PDFBox dependencies
```

---

## Phase C Extension Points

The architecture is designed so these future features require no structural changes:

| Feature | Extension Point |
|---------|----------------|
| Photo/screenshot RAG | Add `ImageParser` implementing `DocumentParser`, uses Gemma 4 vision for OCR |
| Auto-index directories | Add `FileWatcher` service feeding `RagManager.ingest()` |
| Per-conversation docs | Filter by `collectionId` in retrieval — field already exists |
| Named collections | UI for managing collections, `collectionId` already on Document |
| MCP integration | MCP servers query `RagToolSet` — tools already exposed |
| ObjectBox swap | Implement `VectorStore` interface with ObjectBox, swap in `RagModule` |
| Hybrid search | Combine `SqliteVectorStore` results with Room FTS4 on `DocumentChunk.content` |
| Semantic search UI | Standalone screen calling `RagManager.retrieve()` directly |

---

## Verification

1. **Ingestion**: Upload a multi-page PDF and a .txt file. Verify both appear in KB Manager with READY status, correct chunk counts, and browsable chunk previews.
2. **Share Intent**: Share a text file from Android Files app to HUSK. Verify it enters the ingestion pipeline and appears in the KB Manager.
3. **Retrieval**: In chat, ask a question that can only be answered from an uploaded document. Verify the response is grounded in the document content and source attribution appears.
4. **Source attribution**: Verify sources panel is collapsed by default, expands on tap, shows correct document names and relevance scores.
5. **No-match behavior**: Ask a question unrelated to any uploaded documents. Verify no sources section appears and the model responds normally.
6. **Embedding model switch**: Switch from Gecko to EmbeddingGemma in settings. Verify re-index warning appears and re-indexing completes successfully.
7. **Document deletion**: Delete a document from KB Manager. Verify its chunks and vectors are removed.
8. **Tool-based retrieval**: In Agent Skills chat, verify the model can call `searchDocuments()` and `listDocuments()` tools.
9. **Error handling**: Upload an image-only PDF. Verify FAILED status with clear error message.
10. **Performance**: With 10+ documents indexed, verify retrieval latency is under 500ms and chat remains responsive.

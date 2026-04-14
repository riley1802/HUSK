## 3. RAG (Retrieval Augmented Generation) Support

### Google AI Edge RAG SDK

Google provides an **official on-device RAG SDK** for Android: `com.google.ai.edge.localagents:localagents-rag:0.1.0`. This is the single most important feature to add to HUSK because it lets the model answer questions grounded in user-provided documents — entirely offline.

### SDK Architecture

The AI Edge RAG SDK provides these modular components:

| Component | Interface | Purpose |
|-----------|-----------|---------|
| Language Models | `LanguageModel` | LLM with open-prompt API (local or server) |
| Text Embedding | `Embedder` | Convert text to vectors for semantic search |
| Vector Stores | `VectorStore` | Hold embeddings + metadata, support similarity queries |
| Semantic Memory | `SemanticMemory` | Top-k retrieval of relevant chunks for a query |
| Text Chunking | `TextChunker` | Split documents into indexable pieces |
| Chains | `Chain` | Orchestrate retrieval + inference pipeline |

### RAG Pipeline Steps

1. **Import data** — User provides text files, PDFs, notes
2. **Split and index** — Break into chunks using `TextChunker`
3. **Generate embeddings** — Use Gecko embedder (on-device) or Gemini Embedder (cloud)
4. **Store in vector DB** — `SqliteVectorStore` (persistent) or `DefaultVectorStore` (in-memory)
5. **Retrieve** — For a user query, find top-k relevant chunks via cosine similarity
6. **Generate** — Feed retrieved context + query to the LLM

### Implementation in HUSK

```kotlin
// Add to build.gradle
dependencies {
    implementation("com.google.ai.edge.localagents:localagents-rag:0.1.0")
    implementation("com.google.mediapipe:tasks-genai:0.10.22")
}
```

**Key embedding options:**
- **Gecko Model** — Fully on-device, ~30MB, good quality for mobile
- **Gemini Embedder** — Cloud-based, higher quality but requires internet
- **Custom Embedder** — Implement the `Embedder<String>` interface for any model

**Vector storage options:**
- **SqliteVectorStore** — Persistent, survives app restarts, recommended for HUSK
- **DefaultVectorStore** — In-memory only, faster but ephemeral

### Features to Build

1. **Document Ingestion UI** — Let users upload/select text files, PDFs, notes for RAG indexing
2. **Knowledge Base Manager** — View, add, remove indexed documents; show chunk counts and vector stats
3. **RAG-Enhanced Chat Mode** — A chat mode that automatically retrieves relevant context before generating
4. **Per-Conversation Knowledge Bases** — Attach specific documents to specific conversations
5. **Photo/Screenshot RAG** — Use multimodal vision to OCR images into text, then index for RAG
6. **Contact/Calendar RAG** — Index Android contacts and calendar entries (with permission) for personal assistant queries
7. **Clipboard History RAG** — Index clipboard history for instant context retrieval
8. **Auto-Chunking Strategies** — Offer different chunking methods (fixed-size, sentence-based, semantic)
9. **Hybrid Search** — Combine vector similarity with keyword matching for better retrieval
10. **RAG Quality Indicators** — Show confidence scores and source attribution in responses

### MobileRAG Research

Academic research on MobileRAG (2025-2026) shows three specialized RAG subsystems for mobile:

- **InterRAG** — Facilitates invocation of external applications and information retrieval across apps
- **LocalRAG** — Lightweight on-device RAG optimized for mobile environments
- **MemRAG** — Preserves representative and successful user actions for improved real-time responsiveness

MobileRAG achieves 80% task success rate on multi-app automation benchmarks, a 10.3% improvement over prior state-of-the-art.

### Sources
- AI Edge RAG Guide: https://ai.google.dev/edge/mediapipe/solutions/genai/rag
- AI Edge RAG Android Guide: https://ai.google.dev/edge/mediapipe/solutions/genai/rag/android
- Google Developers Blog (RAG + FC): https://developers.googleblog.com/google-ai-edge-small-language-models-multimodality-rag-function-calling/
- AI Edge APIs GitHub: https://github.com/google-ai-edge/ai-edge-apis
- DeepWiki RAG Example: https://deepwiki.com/google-ai-edge/ai-edge-apis/5-examples-and-demos
- InfoQ Gemma 3n + RAG: https://www.infoq.com/news/2025/05/gemma-3n-on-device-inference/
- MobileRAG Paper: https://arxiv.org/html/2509.03891v1
- MobileRAG System Overview: https://www.emergentmind.com/topics/mobilerag-system
- Signity Solutions RAG Trends: https://www.signitysolutions.com/blog/trends-in-active-retrieval-augmented-generation

---


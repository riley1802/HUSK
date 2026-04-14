## 13. On-Device Embeddings & Vector Search

### Available Embedding Models

For on-device RAG, HUSK needs a small embedding model:

| Model | Size | Dims | Notes |
|-------|------|------|-------|
| Gecko (Google AI Edge) | ~30MB | 256/768 | Official AI Edge embedder, optimized for mobile |
| nomic-embed-text | ~260MB | 768 | Good quality, GGUF available |
| bge-m3 | ~1.1GB | 1024 | Best accuracy for RAG, multilingual |
| all-MiniLM-L6-v2 | ~90MB | 384 | Lightweight, decent quality |

**Recommendation for HUSK:** Use Gecko as the default (smallest, officially supported), offer bge-m3 as a "high quality" option for power users with storage to spare.

### Vector Store Options

For on-device storage within the Kotlin/Android stack:

1. **SqliteVectorStore** (from AI Edge RAG SDK) — Already available, persistent, uses SQLite FTS for combined keyword + vector search
2. **Custom Room-based Vector Store** — Use Room database with vector columns for tighter Android integration
3. **FAISS-Mobile** — Facebook's vector search library, available as a native library for Android
4. **HNSW Implementation** — Hierarchical Navigable Small World graphs for approximate nearest neighbor search

### Features to Build

1. **Vector DB Dashboard** — Show indexed document count, total vectors, storage size, query latency
2. **Semantic Search UI** — Standalone search interface over indexed documents
3. **Auto-Index New Files** — Watch specific directories and auto-index new/modified files
4. **Cross-Conversation Search** — Search across all past conversation content
5. **Embedding Visualization** — 2D projection of document vectors for exploration (t-SNE/UMAP style)

---


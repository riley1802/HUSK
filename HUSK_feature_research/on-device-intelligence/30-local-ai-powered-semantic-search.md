## 30. Local AI-Powered Semantic Search

### EmbeddingGemma — Purpose-Built for Edge

**EmbeddingGemma** (September 2025, 308M parameters, <200 MB RAM) is Google's purpose-built on-device embedding model:
- Highest-ranking open multilingual model under 500M parameters on MTEB
- 100+ languages
- Customizable dimensions (128–768)
- Optimized for LiteRT deployment

### On-Device Vector Database: ObjectBox 4.0

**ObjectBox** provides native Android vector search with HNSW (Hierarchical Navigable Small World) indexing:
- Native Kotlin API
- Sub-millisecond vector queries
- Combined with scalar filtering
- ~1 MB library size

### Full-Text Search: Jetpack AppSearch

**Jetpack AppSearch** provides BM25F full-text search with:
- Type-safe schema definitions
- Tokenization and stemming
- Cross-app search (Global AppSearch on supported devices)
- Zero network dependency

### Unified Search Architecture

1. **Embed at idle time** — Use EmbeddingGemma to vectorize messages, files, photos, notes
2. **Store in ObjectBox** — HNSW index for fast similarity search
3. **Hybrid retrieval** — Combine vector similarity (EmbeddingGemma) + keyword matching (AppSearch)
4. **Photo search** — Embed images using SigLIP 2 or Gemma 4 vision
5. **NL queries** — "Find that photo of the sunset from last week" → embedding similarity search

### Sources
- ObjectBox Vector DB: https://objectbox.io/the-on-device-vector-database-for-android-and-java/
- AndroidSemanticSearch Example: https://github.com/hissain/AndroidSemanticSearch
- AppSearch: https://android-developers.googleblog.com/2021/06/sophisticated-search-with-appsearch-in-jetpack.html

---


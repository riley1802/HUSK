# Google AI Edge RAG SDK Integration

## Date: 2026-04-13

## What
Integrated the Google AI Edge RAG SDK (v0.1.0) into HUSK for on-device document retrieval.

## Key Findings

### SDK API Notes
- `GeckoEmbeddingModel` constructor takes `(modelPath: String, title: Optional<String>, useGpu: Boolean)`
- `Embedder.getEmbeddings()` returns `ListenableFuture` — need `kotlinx-coroutines-guava` for `.await()` bridge
- `SqliteVectorStore` constructor takes `(embeddingDimensions: Int, dbPath: String)` — manages its own SQLite DB
- `EmbedData.TaskType` has `RETRIEVAL_QUERY` and `RETRIEVAL_DOCUMENT` for asymmetric search
- SDK uses native JNI libraries: `libgecko_embedding_model_jni.so`, `libsqlite_vector_store_jni.so`, `libtext_chunker_jni.so`
- `SqliteVectorStore` v0.1.0 does NOT expose a delete API — cleanup requires DB file recreation

### Gecko Embedding Dimensions
- `Gecko_256_quant.tflite` — the "256" is the **max sequence length** (tokens), NOT the embedding dimension
- Gecko always outputs **768-dimensional** embeddings regardless of sequence length variant
- Mismatching dimensions causes a native SIGABRT in `sqlite_memory_store.cc` (`Check failed: record.embeddings().size() == embedding_dimension_`)
- Always verify actual output dimensions by checking the model, not inferring from the filename

### KDoc Comment Bug
- Don't use `/*` inside KDoc comments (e.g., `text/*` in a comment causes "Unclosed comment" error in kapt)
- The Kotlin compiler treats `/*` inside `/** ... */` as nested comment start

### Integration Architecture
- RAG context injection works best by prepending `Content.Text(ragContext)` before user input in `LlmChatModelHelper.runInference()`, not through system prompt (which is built once at init)
- `ChatMessageText.data: Any?` field is a convenient slot for attaching metadata (like `List<ChunkResult>`) without modifying the base class

## How to Prevent
- Always check SDK interfaces via `javap` before implementing wrappers
- Test KDoc comments don't contain `/*` sequences
- When injecting per-message context, use the content pipeline, not the system prompt

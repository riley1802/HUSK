# Husk (Android)

The Android application for HUSK — an on-device AI playground built with Jetpack Compose.

## Project Structure

```
Android/src/app/src/main/
├── java/com/google/ai/edge/gallery/
│   ├── ui/                    # Compose UI layer (screens by feature)
│   │   ├── audioscribe/       # Audio Scribe — transcription + diarization
│   │   ├── llmchat/           # LLM chat with thinking mode
│   │   ├── llmsingleturn/     # Single-turn LLM inference
│   │   ├── thermal/           # Thermal monitoring (meter + detail sheet)
│   │   ├── modelmanager/      # Model download and management
│   │   ├── agentchat/         # Agent Skills with tool calling
│   │   ├── knowledgebase/     # RAG knowledge base management
│   │   ├── notes/             # Notes with LLM conversation
│   │   ├── home/              # Home screen and app entry
│   │   ├── benchmark/         # Model benchmarking
│   │   ├── common/            # Shared components (chat, markdown, inputs)
│   │   ├── navigation/        # Nav graph and routing
│   │   ├── icon/              # App icon assets
│   │   └── theme/             # Material3 theme and colors
│   ├── data/                  # Data models and enums
│   │   ├── speaker/           # Speaker profiles, transcriptions (Room DB)
│   │   ├── rag/               # RAG documents, chunks, embeddings (Room DB)
│   │   ├── mcp/               # MCP server config and transport layer
│   │   ├── memory/            # Hot memory / context management
│   │   └── notes/             # Notes data layer
│   ├── runtime/               # Model lifecycle (WhisperJni, LlmModelHelper)
│   ├── common/                # Utilities (AudioDecoder, ProjectConfig)
│   ├── di/                    # Hilt dependency injection
│   ├── worker/                # Background WorkManager tasks
│   └── customtasks/           # Mobile Actions, Tiny Garden
├── cpp/                       # Native C++ code
│   ├── whisper_jni.cpp        # JNI bridge to whisper.cpp
│   ├── CMakeLists.txt         # Native build config
│   └── whisper/               # Vendored whisper.cpp (gitignored)
├── proto/                     # Proto DataStore definitions
│   ├── settings.proto         # App settings schema
│   ├── benchmark.proto        # Benchmark results schema
│   └── skill.proto            # Agent skills schema
└── res/                       # Android resources
```

## Key Directories

### `ui/audioscribe/`
The Audio Scribe feature — HUSK's flagship transcription system. Contains the main screen (tabbed: Scribe + History), ViewModel with the full decode-transcribe-diarize pipeline, transcript rendering, speaker label sheet, and Whisper model selector.

### `ui/agentchat/`
Agent chat with tool calling support. Exposes HUSK's skill system to the LLM, allowing the model to invoke registered tools and return structured results mid-conversation.

### `ui/knowledgebase/`
RAG knowledge base management screen. Lets users ingest documents, view ingestion status, and manage the vector store used for retrieval-augmented generation.

### `ui/notes/`
Note-taking with per-note LLM conversations. Each note has its own system prompt and conversation history. `NotesListScreen.kt` lists all notes; `NoteConversationScreen.kt` handles the chat interface.

### `ui/thermal/`
Real-time device thermal monitoring. `ThermalMonitor.kt` reads from PowerManager and BatteryManager APIs. `ThermalMeter.kt` renders the app bar chip and detail bottom sheet.

### `data/speaker/`
Room database entities and DAOs for speaker voice profiles and saved transcriptions. `SpeakerEmbeddingManager.kt` runs ECAPA-TDNN inference. `SpeakerDiarizationEngine.kt` orchestrates the speaker identification pipeline.

### `data/rag/`
Room database for RAG document storage. Contains document and chunk entities, embedding storage, a parser layer, and `RagManager.kt` which coordinates ingestion and retrieval. `RagToolSet.kt` exposes retrieval as agent tools.

### `data/mcp/`
MCP (Model Context Protocol) client implementation. `McpManager.kt` manages server connections. `McpTransport.kt` and the `transports/` subdirectory handle the transport layer. `McpToolBridge.kt` bridges MCP tools into the agent tool-calling system.

### `runtime/`
Model lifecycle management. `WhisperJni.kt` is the Kotlin JNI bridge to native whisper.cpp. `WhisperModelHelper.kt` wraps init/transcribe/cleanup. `LlmModelHelper.kt` handles LiteRT-LM chat models.

### `common/`
`AudioDecoder.kt` — universal audio/video decoder using MediaExtractor + MediaCodec. Outputs 16kHz mono PCM. Handles large files via temp file spool.

### `cpp/`
Native C++ code for whisper.cpp integration. The `whisper/` subdirectory must be cloned separately (see DEVELOPMENT.md). CMake builds `libwhisper_jni.so` for arm64-v8a.

## Build

See [DEVELOPMENT.md](../DEVELOPMENT.md) for full build instructions including native library setup.

```
cd Android/src/
./gradlew installDebug
```

## Architecture

See [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) for the full system architecture documentation.

# HUSK Architecture

## Overview
HUSK is an on-device AI playground for Android built with Jetpack Compose and Material3. It runs open-source models (LLMs, speech-to-text, embeddings) entirely on-device using Google AI Edge / LiteRT and native C++ libraries via JNI.

Architecture layers (text diagram):
```
┌─────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose + Material3)     │
│  Screens: audioscribe, llmchat, thermal,    │
│  modelmanager, promptlab, agentchat, etc.   │
├─────────────────────────────────────────────┤
│  ViewModel Layer (Hilt-injected)            │
│  AudioScribeViewModel, ModelManagerViewModel│
│  LlmChatViewModel, etc.                    │
├─────────────────────────────────────────────┤
│  Runtime Layer                              │
│  WhisperModelHelper, LlmModelHelper,        │
│  WhisperJni (JNI bridge)                    │
├──────────────────┬──────────────────────────┤
│  Data Layer      │  Native Layer (C++)      │
│  Room DB         │  whisper.cpp + ggml      │
│  Proto DataStore │  whisper_jni.cpp         │
│  ModelAllowlist  │  libwhisper_jni.so       │
└──────────────────┴──────────────────────────┘
```

## Module Map

Source root: `Android/src/app/src/main/java/com/google/ai/edge/gallery/`

| Directory | Purpose |
|-----------|---------|
| `ui/audioscribe/` | Audio Scribe screen — transcription, history, speaker labels |
| `ui/llmchat/` | LLM chat interface with thinking mode |
| `ui/thermal/` | Thermal monitoring — ThermalMonitor + ThermalMeter UI |
| `ui/modelmanager/` | Model download and management |
| `ui/agentchat/` | Agent Skills with tool calling |
| `ui/knowledgebase/` | RAG knowledge base management |
| `ui/notes/` | Notes with per-note LLM conversations |
| `ui/common/` | Shared Compose components (chat bubbles, markdown, inputs) |
| `ui/navigation/` | Navigation graph and routing |
| `data/` | Data models, enums (Model.kt, RuntimeType) |
| `data/speaker/` | Speaker diarization — Room entities, DAOs, embedding manager, diarization engine |
| `data/rag/` | RAG documents, chunks, embeddings (Room DB) + RagManager |
| `data/mcp/` | MCP server config, transport layer, tool bridge |
| `data/memory/` | Hot/warm memory and context management |
| `data/notes/` | Notes data layer |
| `runtime/` | Model lifecycle — WhisperJni, WhisperModelHelper, LlmModelHelper |
| `common/` | Utilities — AudioDecoder, ProjectConfig |
| `di/` | Hilt dependency injection modules |
| `worker/` | Background WorkManager tasks |
| `customtasks/` | Mobile Actions, Tiny Garden (function calling tasks) |

Native code: `Android/src/app/src/main/cpp/`
- `whisper_jni.cpp` — JNI bridge to whisper.cpp
- `whisper/` — Vendored whisper.cpp source (gitignored, ~37MB)
- `CMakeLists.txt` — Build config targeting arm64-v8a with fp16

## Audio Scribe Pipeline

The Audio Scribe is HUSK's flagship feature — on-device speech-to-text with speaker identification.

```
File/Recording Input
       │
       ▼
┌──────────────┐
│ AudioDecoder │  MediaExtractor + MediaCodec
│              │  Supports: M4A, MP3, OGG, FLAC, WAV, AMR, OPUS
│              │  + video: MP4, MKV, 3GP, WebM, MOV (audio strip)
│              │  Output: 16kHz mono Float PCM
│              │  Large files (>50MB PCM): temp file spool
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ whisper.cpp  │  Native C++ via JNI (WhisperJni.kt → whisper_jni.cpp)
│              │  Models: Tiny (~75MB), Base (~150MB), Small (~500MB)
│              │  Greedy sampling, 4 threads
│              │  Long audio: chunked in 30-second segments
│              │  Output: List<TranscriptSegment> with timestamps
└──────┬───────┘
       │
       ▼
┌──────────────────────┐
│ Speaker Diarization  │  ECAPA-TDNN TFLite model
│                      │  192-dimensional voice embeddings
│                      │  Cosine similarity matching (threshold: 0.75)
│                      │  Unknown speaker grouping (threshold: 0.80)
│                      │  Profiles persisted in Room DB
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│ TranscriptView       │  Color-coded speaker labels
│                      │  Timestamps, full text toggle
│                      │  Tappable unknown speakers → label sheet
└──────┬───────────────┘
       │
       ▼ (optional, if < 10 min)
┌──────────────────────┐
│ Gemma E4B Summary    │  Auto-selected LLM generates summary
│                      │  Saved to Room with transcript
└──────────────────────┘
```

### Key classes:
- `AudioDecoder` (`common/AudioDecoder.kt`) — Universal format decoder. Two strategies: in-memory for small files, temp file spool for large files (>50MB decoded PCM).
- `WhisperJni` (`runtime/WhisperJni.kt`) — Kotlin JNI bridge. `initModel(path)` → native context pointer, `transcribe(ptr, samples)` → segments, `freeModel(ptr)`.
- `WhisperModelHelper` (`runtime/WhisperModelHelper.kt`) — Lifecycle wrapper. Manages init/transcribe/cleanup. Runs transcription on `Dispatchers.Default`.
- `AudioScribeViewModel` (`ui/audioscribe/AudioScribeViewModel.kt`) — Orchestrates the full pipeline. Adaptive ETA (EMA of last 5 chunk times). Wake lock during processing. Saves transcriptions to Room.

## Speaker Diarization System

```
Audio Segment (PCM slice)
       │
       ▼
┌─────────────────────────┐
│ SpeakerEmbeddingManager │  ECAPA-TDNN TFLite model
│                         │  Input: Float PCM (min 8000 samples)
│                         │  Output: L2-normalized Float[192]
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ SpeakerDiarizationEngine│
│                         │  1. Extract audio slice per segment
│                         │  2. Compute embedding
│                         │  3. Compare vs saved profiles (cosine sim)
│                         │  4. > 0.75 → known speaker
│                         │  5. < 0.75 → "Unknown Speaker N"
│                         │  6. Group unknowns by similarity (> 0.80)
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ Room DB (SpeakerProfile)│  id, name, embedding (BLOB), sampleCount
│                         │  Running average: new = (old * n + sample) / (n+1)
│                         │  User labels unknown → creates/merges profile
└─────────────────────────┘
```

## Thermal Monitoring

Real-time thermal tracking using public Android APIs (no elevated permissions).

**Data sources:**
- `PowerManager.getThermalHeadroom(10)` — thermal headroom (0.0 = cool, 1.0 = throttle point). API 30+.
- `PowerManager.currentThermalStatus` — discrete status (None/Light/Moderate/Severe/Critical/Emergency/Shutdown). API 29+.
- `BatteryManager.EXTRA_TEMPERATURE` — battery temp in tenths of °C (always available, no permissions).

**Estimation from headroom:**
- Skin temp: `25 + headroom * 25` (clamped 20-55°C)
- CPU temp: skin + 3°C (clamped 25-60°C)

**UI:**
- `ThermalMeterChip` — tiny color-coded dot + temp in the app's top bar (GalleryTopAppBar). Green < 37°C, Yellow < 40°C, Orange < 45°C, Red >= 45°C.
- `ThermalDetailSheet` — modal bottom sheet with all sensor readings, headroom progress bar, recommendations, reference ranges for AI inference.

**Files:** `ui/thermal/ThermalMonitor.kt` (data), `ui/thermal/ThermalMeter.kt` (UI)

## Navigation

Routes defined in `ui/navigation/GalleryNavGraph.kt`:

| Route | Screen | Notes |
|-------|--------|-------|
| `home` | Home screen | Hub with feature cards |
| `audio_scribe` | AudioScribeScreen | Direct route — bypasses model selection |
| `model_list/{taskType}` | Model list | Model selection before entering a task |
| `chat/{taskType}/{modelName}` | Chat/task screen | LLM chat, Ask Image, Prompt Lab, etc. |
| `model_manager` | Model manager | Download/manage all models |
| `app_setting` | Settings | App-wide configuration |

Audio Scribe uses a dedicated `ROUTE_AUDIO_SCRIBE` that navigates directly to the screen without requiring model selection first. Models are managed within the Audio Scribe UI itself.

## Dependency Injection

Hilt modules in `di/AppModule.kt` provide:
- `SpeakerDatabase` — Room database for speaker profiles + transcriptions
- `SpeakerProfileDao` — DAO for speaker profile CRUD
- `TranscriptionDao` — DAO for saved transcriptions
- `SpeakerDiarizationEngine` — Pipeline combining embedding + matching + persistence

ViewModels use `@HiltViewModel` with constructor injection.

## Model Management

### Allowlist System
Models are defined in JSON allowlists (`model_allowlists/1_0_11.json`). Each entry specifies:
- `name`, `url` (HuggingFace download), `sizeInBytes`
- `runtimeType`: `"whisper"` (whisper.cpp GGML), `"aicore"` (Android AICore), or omit for LiteRT-LM / standard TFLite
- `defaultConfig.accelerators`: `"gpu"`, `"cpu"`, or `"gpu,cpu"` (REQUIRED — missing causes NPE)
- Generation parameters (`topK`, `temperature`, `maxTokens`, etc.) live inside `defaultConfig`

### Runtime Loading Precedence
1. Test file: `/data/local/tmp/model_allowlist_test.json` (development only)
2. GitHub-hosted allowlist (fetched on app start)
3. Cached local file (offline fallback)

### RuntimeType Routing
- `LITERT_LM` → `LlmModelHelper` (chat, thinking, tool calling) — inferred from `.litertlm` file extension
- `WHISPER` → `WhisperModelHelper` (speech-to-text via JNI)
- `AICORE` → Android AICore runtime (on-device Google models)
- `UNKNOWN` / default → standard TFLite interpreter

### Downloads
Model downloads use Android WorkManager via `DownloadRepository`. Progress is tracked in `ModelManagerViewModel.uiState` and displayed per-model in UI.

## Data Persistence

| Store | Technology | Contents |
|-------|-----------|----------|
| Speaker profiles | Room (`husk_speakers.db`) | Voice embeddings, names, sample counts |
| Transcriptions | Room (`husk_speakers.db`) | Saved transcripts with JSON segments, summaries |
| App settings | Proto DataStore | Theme, selected models, preferences |
| Model files | Disk (app files dir) | Downloaded GGML/TFLite model binaries |
| Model allowlists | JSON (GitHub/cache) | Available model definitions |
| Notes | Room | User notes with per-note LLM conversations |
| RAG documents | Room + SqliteVectorStore | Ingested documents, chunks, Gecko embeddings |
| Memory | In-memory + Room | Hot/warm context for cross-conversation awareness |

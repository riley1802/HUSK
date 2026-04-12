# Lumen — Deep Dive Reference

> **Purpose of this document**: Authoritative technical reference for the Lumen project. Goes well beyond the quick-start README. Written for someone who needs to understand _why_ decisions were made, _how_ every system works, and _what_ has already been learned the hard way.

**Last updated:** 2026-04-11

---

## Table of Contents

1. [Project Identity](#1-project-identity)
2. [Repository Layout](#2-repository-layout)
3. [Tech Stack](#3-tech-stack)
4. [Kotlin Multiplatform Architecture](#4-kotlin-multiplatform-architecture)
5. [Android App — `:app`](#5-android-app----app)
6. [Desktop App — `:desktop`](#6-desktop-app----desktop)
7. [Server — `:server`](#7-server----server)
8. [GardenLab — Deep Dive](#8-gardenlab--deep-dive)
9. [Phone Data Mining — Deep Dive](#9-phone-data-mining--deep-dive)
10. [Testing](#10-testing)
11. [Build System](#11-build-system)
12. [Lessons Learned](#12-lessons-learned)
13. [Pending Work / Known Gaps](#13-pending-work--known-gaps)

---

## 1. Project Identity

### What Lumen Is

Lumen is a **cross-platform, fully private, on-device AI assistant** built on Kotlin Multiplatform + Compose Multiplatform. It runs on:

- **Android** (Samsung Galaxy Z Fold 7) — using Google's LiteRT-LM for on-device inference
- **Desktop** (Linux / Windows) — using Ollama as the local LLM backend
- **Server** (same desktop machine) — a Ktor HTTP service that receives phone data and embeds it with pgvector

Everything runs locally. No data leaves the machine or the Tailscale tailnet. The models run on-device or on the local GPU.

### Where It Came From

Lumen is forked from **[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)**, an open-source Android demo app that showcases on-device ML with LiteRT. We kept the Android app nearly intact (migrating only its DI from Hilt → Koin) and built on top of it:

- A **KMP shared module** (`:shared`) for code shared between Android, desktop, and server
- A **Compose Multiplatform desktop app** (`:desktop`) that mirrors the Android UI
- A **Ktor server** (`:server`) that acts as the phone-context brain
- Advanced Android features: **GardenLab** (LLM farming simulation) and **Phone Data Mining**

### Target Hardware

| Device | Role | GPU |
|---|---|---|
| Samsung Galaxy Z Fold 7 | Android client, phone data source | On-device (Gemma-4-E2B-it via LiteRT) |
| Desktop (primary) | Ollama server, lumen-server, pgvector | GTX 1660 Super (6 GB VRAM) |
| Desktop (secondary) | Spare GPU | RTX 3050 (6 GB VRAM) |

### Core Design Principles

1. **Private by default** — all inference is local; phone data flows only over Tailscale tailnet
2. **Parity** — the desktop app should feel like the Android app; same features, same visual design, same model
3. **Phone as sensor** — the phone continuously collects structured events and makes them available to AI chat/agents without any manual effort
4. **Idempotency everywhere** — every write operation is safe to repeat; backfills never create duplicates

---

## 2. Repository Layout

```
Instance ai/                          ← git root
├── Android/src/                      ← Gradle monorepo (4 modules)
│   ├── app/                          ← :app — Android application
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/google/ai/edge/gallery/
│   │       ├── GalleryApplication.kt              ← Koin startKoin()
│   │       ├── di/AppModule.kt                    ← Koin ViewModels, repos
│   │       ├── gardenlab/                         ← GardenLab feature
│   │       │   ├── AgentLoop.kt                   ← 8-phase agent orchestration
│   │       │   ├── GardenLabViewModel.kt          ← StateFlow, Mutex, live loop
│   │       │   ├── LiteRtLmClient.kt              ← LiteRT-LM adapter (runInference)
│   │       │   ├── world/                         ← Simulation engine
│   │       │   │   ├── World.kt                   ← 6×6 grid
│   │       │   │   ├── Bee.kt                     ← Farmer agent
│   │       │   │   └── Species.kt                 ← Crop definitions
│   │       │   ├── dsl/                           ← Farming script DSL
│   │       │   │   ├── Lexer.kt
│   │       │   │   ├── Parser.kt
│   │       │   │   ├── Interpreter.kt
│   │       │   │   └── ExecutionBudget.kt
│   │       │   ├── quest/                         ← Quest system
│   │       │   │   ├── Quest.kt                   ← 4 quest types
│   │       │   │   ├── QuestGenerator.kt
│   │       │   │   └── QuestEngine.kt
│   │       │   └── db/                            ← Room (gardenlab.db)
│   │       │       ├── GardenLabDb.kt
│   │       │       ├── GenerationDao.kt
│   │       │       ├── WorldStateDao.kt
│   │       │       ├── QuestStateDao.kt
│   │       │       └── SettingsDao.kt
│   │       └── phonedata/                         ← Phone Data Mining
│   │           ├── Collector.kt                   ← interface + RawEvent
│   │           ├── PhoneDataModule.kt             ← Koin named("phonedataCollectors")
│   │           ├── collectors/                    ← 10 collector implementations
│   │           ├── services/                      ← NotificationListenerService
│   │           ├── outbox/                        ← Room (phonedata_outbox.db)
│   │           └── sync/                          ← SyncEngine + network tiers
│   │
│   ├── shared/                       ← :shared — KMP shared module
│   │   └── src/
│   │       ├── commonMain/kotlin/com/lumen/shared/
│   │       │   ├── data/                          ← Serializable data models
│   │       │   ├── engine/InferenceEngine.kt      ← Core inference interface
│   │       │   ├── repository/                    ← ModelRepository, SettingsRepository
│   │       │   └── di/SharedModule.kt             ← (extensible, currently empty)
│   │       ├── androidMain/kotlin/com/lumen/shared/di/
│   │       │   └── AndroidModule.kt               ← (placeholder)
│   │       └── desktopMain/kotlin/com/lumen/shared/
│   │           ├── engine/OllamaInferenceEngine.kt
│   │           ├── repository/OllamaModelRepository.kt
│   │           ├── settings/JvmSettingsRepository.kt
│   │           ├── tools/                         ← Wikipedia, Calculator, WebSearch, FileReader
│   │           └── di/DesktopModule.kt
│   │
│   ├── desktop/                      ← :desktop — Compose Multiplatform desktop
│   │   ├── build.gradle.kts
│   │   └── src/jvmMain/
│   │       ├── kotlin/com/lumen/desktop/
│   │       │   ├── Main.kt                        ← Entry point
│   │       │   └── ui/
│   │       │       ├── theme/                     ← Color, Type, Theme (Material 3 dark)
│   │       │       ├── navigation/                ← Screen, LumenApp, TaskDefinitions
│   │       │       ├── common/                    ← LumenTopBar, TaskIcon
│   │       │       ├── home/HomeScreen.kt
│   │       │       ├── modellist/ModelListScreen.kt
│   │       │       ├── chat/ChatScreen.kt
│   │       │       ├── promptlab/PromptLabScreen.kt
│   │       │       ├── askimage/AskImageScreen.kt
│   │       │       ├── audioscribe/AudioScribeScreen.kt
│   │       │       ├── agentskills/AgentSkillsScreen.kt
│   │       │       ├── desktopactions/DesktopActionsScreen.kt
│   │       │       ├── tinygarden/TinyGardenScreen.kt
│   │       │       ├── modelmanager/ModelManagerScreen.kt
│   │       │       └── phoneexplorer/PhoneExplorerScreen.kt
│   │       └── resources/fonts/                   ← Nunito TTF files
│   │
│   ├── server/                       ← :server — Ktor HTTP server
│   │   └── src/main/kotlin/com/lumen/server/
│   │       ├── Application.kt                     ← Ktor entry point
│   │       ├── phonedata/
│   │       │   ├── PhoneEventRepository.kt        ← Postgres ingest + MEMORY_WORTHY_SOURCES
│   │       │   ├── PhoneMemoryEnricher.kt         ← 30s tick, Semaphore(5) embedding loop
│   │       │   └── routes/PhoneRoutes.kt          ← /api/phone/* + /api/pair/*
│   │       ├── memory/
│   │       │   └── PostgresMemoryRepository.kt    ← pgvector semantic search
│   │       ├── embedding/EmbeddingService.kt      ← Ollama nomic-embed-text
│   │       └── db/Tables.kt                       ← Exposed table definitions
│   │
│   ├── gradle/libs.versions.toml     ← Version catalog (57 libraries, 13 plugins)
│   ├── settings.gradle.kts           ← include(":app", ":shared", ":desktop", ":server")
│   └── build.gradle.kts              ← Root plugins
│
├── docs/
│   └── phone-data-mining.md          ← ~500-line deep dive on phone data architecture
├── lessons/                          ← Post-mortem notes (read before every session)
│   └── 2026-04-10-android-room-ksp2-robolectric-compat.md
├── gallery/                          ← UNTRACKED: upstream Google AI Edge Gallery repo
├── docker-compose.yml                ← Postgres + pgvector for lumen-server
├── README.md                         ← Quick-start guide
├── DEVELOPMENT.md                    ← Dev environment setup
├── CONTRIBUTING.md
├── Bug_Reporting_Guide.md
└── Function_Calling_Guide.md
```

### Module Dependency Graph

```
:app ──────────────────────────┐
   depends on: :shared         │
   (commonMain + androidMain)  │
                               ▼
                          :shared (KMP)
                               ▲
:desktop ──────────────────────┤
   depends on: :shared         │
   (commonMain + desktopMain)  │
                               │
:server ───────────────────────┘
   depends on: :shared
   (commonMain only)
```

---

## 3. Tech Stack

### Core Stack

| Component | Library | Version | Why |
|---|---|---|---|
| Language | Kotlin | 2.2.0 | KMP support, coroutines, null safety |
| UI — Android | Jetpack Compose | (via AGP) | Same as upstream Gallery |
| UI — Desktop/Shared | Compose Multiplatform | 1.8.0 | Single UI codebase for desktop |
| DI | Koin | 3.5.6 | KMP-compatible (Hilt is Android-only) |
| HTTP Client | Ktor (CIO engine) | 2.3.12 | KMP-compatible, streaming support |
| Serialization | kotlinx.serialization | 1.7.3 | KMP-native, no reflection needed |
| Coroutines | kotlinx.coroutines | 1.8.1 | Async + Flow for streaming tokens |
| Build | Gradle + AGP | 8.8.2 | KMP monorepo standard |
| KSP | KSP2 | 2.2.0-2.0.2 | Annotation processing (Room, etc.) |

### Android-Specific

| Component | Library | Version | Why |
|---|---|---|---|
| On-device LLM | LiteRT-LM | 0.10.0 | Google's on-device inference runtime |
| Local DB | Room | 2.7.0 | KSP2-compatible (2.6.1 is not) |
| Preferences | DataStore | (bundled) | Android-native, async |
| Background work | WorkManager | 2.10.0 | Cellular sync (15 min periodic) |
| HTTP (phone→server) | Ktor Android | 2.3.12 | Shared with desktop |
| DI | Koin Android | 3.5.6 | ViewModels, scopes |
| Testing | Robolectric | 4.14.1 | SDK 35 support (4.13 maxes at 34) |

### Desktop-Specific

| Component | Library | Version | Why |
|---|---|---|---|
| LLM Backend | Ollama (HTTP) | Any | GPU acceleration, model management |
| Settings | JVM file I/O | — | JSON file in `~/.config/lumen/` |
| Audio input | `javax.sound.sampled` | — | System mic, 16kHz mono |
| File picker | `JFileChooser` | — | Native file dialog |
| Window mgmt | `wmctrl` (system) | — | List open windows for Desktop Actions |
| Packaging | CMP packaging | 1.8.0 | `.deb` (Linux), `.exe` (Windows) |

### Server-Specific

| Component | Library | Version | Why |
|---|---|---|---|
| HTTP Server | Ktor server | 2.3.12 | Kotlin-native, Koin-ktor plugin |
| ORM | Exposed | (bundled) | Kotlin DSL for SQL, easy pgvector |
| Database | Postgres + pgvector | latest | Vector similarity search on memories |
| Embeddings | `nomic-embed-text` via Ollama | — | 768-dim vectors, fast on GTX 1660 |
| Containers | Docker Compose | — | Postgres + pgvector setup |

### Removed / Deprecated

| Library | Removed Because |
|---|---|
| **Hilt** | Android-only; incompatible with KMP. Replaced with Koin. |
| **Moshi codegen** (`moshi-kotlin-codegen`) | KSP2 AA mode incompatible. Uses KSP1 APIs internally. Replaced with Moshi reflection adapter. Do not re-add until a Moshi KSP2 release ships. |

---

## 4. Kotlin Multiplatform Architecture

### Source Set Hierarchy

```
:shared
├── commonMain      ← Interfaces, data models, DI definitions
│   (all platforms)
├── androidMain     ← Android implementations (currently placeholder; actual LiteRT impl lives in :app)
│   (Android only)
└── desktopMain     ← JVM/Ollama implementations
    (JVM only)
```

### Core Interfaces (commonMain)

#### `InferenceEngine`

```kotlin
interface InferenceEngine {
    suspend fun chat(
        messages: List<ConversationMessage>,
        config: InferenceConfig,
        onToken: (String) -> Unit
    )
    suspend fun generateWithImage(
        prompt: String,
        imageBytes: ByteArray,
        config: InferenceConfig,
        onToken: (String) -> Unit
    )
    suspend fun transcribe(audioBytes: ByteArray): String
}
```

Android uses LiteRT-LM. Desktop uses Ktor streaming to Ollama `/api/chat`.

#### `ModelRepository`

```kotlin
interface ModelRepository {
    suspend fun listModels(): List<ModelInfo>
    suspend fun pullModel(name: String, onProgress: (PullProgress) -> Unit)
    suspend fun deleteModel(name: String)
    suspend fun isModelReady(name: String): Boolean
}
```

Android uses HuggingFace OAuth + download. Desktop uses `ollama pull` via HTTP.

#### `SettingsRepository`

```kotlin
interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
}
```

Android uses DataStore. Desktop uses a JSON file in `~/.config/lumen/`.

### Data Models (commonMain)

| Class | Purpose |
|---|---|
| `AppSettings` | theme (SYSTEM/LIGHT/DARK), ollamaBaseUrl, defaultModelName |
| `ConversationMessage` | role (USER/ASSISTANT/SYSTEM), content, optional imageBytes, isThinking flag |
| `InferenceConfig` | temperature (0-2), topK, topP, maxTokens (64-8192) |
| `Conversation` | id, title, createdAt, deviceOrigin (ANDROID/DESKTOP) |
| `MemoryEntry` | id, content, embedding, source, createdAt |

All data models are `@Serializable` via kotlinx.serialization.

### Desktop Implementations (desktopMain)

**`OllamaInferenceEngine`** — Streams tokens from Ollama:
- Opens a persistent Ktor HTTP connection to `POST /api/chat`
- Reads newline-delimited JSON chunks (`{"message":{"content":"..."},"done":false}`)
- Calls `onToken(chunk)` for each partial token
- Detects thinking mode via `<think>...</think>` tags and sets `isThinking` accordingly

**`OllamaModelRepository`** — Manages Ollama model lifecycle:
- `GET /api/tags` → parse model list with sizes
- `POST /api/pull` → streaming progress (JSON lines with `status`, `completed`, `total`)
- `DELETE /api/delete` → remove model by name
- Readiness = `listModels().any { it.name == name }`

**`JvmSettingsRepository`** — Persists to JSON file:
- Path: `~/.config/lumen/settings.json` (Linux) or `%APPDATA%\Lumen\settings.json` (Windows)
- Uses `kotlinx.serialization` JSON codec
- Emits updates via `MutableStateFlow`

### Dependency Injection (Koin)

There are three Koin modules:

**`sharedModule`** (`commonMain/di/SharedModule.kt`) — Currently empty, reserved for future common singletons.

**`androidModule`** (`androidMain/di/AndroidModule.kt`) — Currently a placeholder. Actual Android DI lives in `:app`'s `AppModule.kt` because LiteRT requires Android context not available in `:shared`.

**`desktopModule`** (`desktopMain/di/DesktopModule.kt`):
```kotlin
val desktopModule = module {
    single<InferenceEngine> { OllamaInferenceEngine(get()) }
    single<ModelRepository> { OllamaModelRepository(get()) }
    single<SettingsRepository> { JvmSettingsRepository() }
    single { HttpClient(CIO) { /* Ktor config */ } }
}
```

Initialized in `Main.kt`:
```kotlin
startKoin {
    modules(sharedModule, desktopModule)
}
```

### Hilt → Koin Migration

The upstream Google AI Edge Gallery used Hilt for DI. We replaced it with Koin to enable KMP compatibility. This was a **DI-only change** — zero business logic touched.

Scope of change: **32 files modified** in `:app`.

| Before (Hilt) | After (Koin) |
|---|---|
| `@HiltAndroidApp` on Application | `startKoin { }` in `GalleryApplication.kt` |
| `@AndroidEntryPoint` on Activities/Fragments | Removed (not needed with Koin) |
| `@HiltViewModel` on ViewModels | `viewModel { }` in Koin module |
| `hiltViewModel()` in Composables | `koinViewModel()` in Composables |
| `@Inject constructor(...)` | Koin factory/single declarations |
| `@Provides` functions in `@Module` | Koin `module { single { } }` |

The Android app continues to build and function identically.

---

## 5. Android App — `:app`

### Package Layout

```
com.google.ai.edge.gallery
├── GalleryApplication.kt       ← Koin startKoin + module wiring
├── MainActivity.kt             ← Single Activity, Compose NavHost
├── di/AppModule.kt             ← ViewModels, repos, task registrations
├── gardenlab/                  ← GardenLab AI farming feature
├── phonedata/                  ← Phone data mining infrastructure
├── tasks/                      ← Other AI tasks (chat, vision, etc.)
└── ui/                         ← Shared composables, theme
```

### LiteRT-LM Integration

LiteRT-LM (`com.google.ai.edge.litert:litert-lm:0.10.0`) is Google's on-device LLM runtime. It wraps TFLite/ML model execution and exposes a chat-like API via `LlmChatModelHelper`.

**The critical integration point is `LiteRtLmClient.kt`.** This file adapts the `LlmClient` interface (used by GardenLab and other tasks) to LiteRT-LM:

```kotlin
// WRONG — causes token queue overflow after ~7 iterations
llmChatModelHelper.sendMessage(prompt) { partial, done -> ... }

// CORRECT — production path with proper async token queue management
llmChatModelHelper.runInference(prompt) { partial, done -> ... }
```

The `sendMessage` API is synchronous and does not drain the engine's internal token queue between calls. After ~7 LLM calls in the same session, the queue overflows and the native layer crashes with a SIGSEGV. `runInference` is the async path that all other LiteRT-based tasks use — it drains the queue properly.

**Thread rule**: LiteRT native JNI **must never run on the main thread**. Always dispatch to `Dispatchers.Default`:
```kotlin
withContext(Dispatchers.Default) {
    llmChatModelHelper.runInference(prompt) { ... }
}
```

Violating this causes SIGSEGV (native crash).

**Conversation reset**: Before each multi-iteration call, reset the conversation to single-turn semantics:
```kotlin
llmChatModelHelper.resetConversation()
```

Without this, each call appends to a growing context window. In an iterative loop (like GardenLab's agent loop), this causes the context to balloon and eventually overflow.

### GardenLab (Overview — see Section 8 for deep dive)

GardenLab is the most complex feature in the project. It's an **AI-driven farming simulation** where:
- A custom DSL controls a bee farmer on a 6×6 grid
- The LLM generates farming scripts each iteration
- The simulation runs deterministically against the script
- The LLM critiques the results and generates a better script
- A quest system rewards milestone achievements
- All state is persisted in Room between sessions

### Phone Data Mining (Overview — see Section 9 for deep dive)

Ten on-device data collectors continuously capture structured events into a Room outbox. A network-aware SyncEngine flushes the outbox to `lumen-server` over Tailscale. The server archives everything in Postgres and embeds text-bearing sources with pgvector.

### Room Databases

The app maintains **two separate Room databases**:

**`gardenlab.db`** — GardenLab state:
| DAO | Table | Contents |
|---|---|---|
| `GenerationDao` | `generation` | script text, LLM critique, simulation score per iteration |
| `WorldStateDao` | `world_state` | serialized world JSON (6×6 grid + bee state) |
| `QuestStateDao` | `quest_state` | active quest + whether it's been completed |
| `SettingsDao` | `settings` | GardenLab user prefs (simulation interval, paused flag) |

**`phonedata_outbox.db`** — Phone data sync outbox:
| DAO | Table | Contents |
|---|---|---|
| `PendingEventDao` | `pending_events` | RawEvents awaiting sync to server, keyed by `eventHash` |

### Koin Named Qualifiers Pattern

The app has two `Set<T>` bindings in Koin:
- `Set<CustomTask>` — all AI tasks registered with the task system
- `Set<Collector>` — all phone data collectors

At JVM runtime, generic type parameters are erased. Both are just `Set` to the JVM. Without differentiation, Koin cannot resolve which `Set` to inject where.

**Solution**: Use named qualifiers:
```kotlin
// In PhoneDataModule.kt
single(named("phonedataCollectors")) {
    setOf(
        get<SmsCollector>(),
        get<CallLogCollector>(),
        // ...
    )
}

// At injection site
val collectors: Set<Collector> by inject(named("phonedataCollectors"))
```

This is an easy mistake to make whenever adding a new `Set<SomeInterface>` binding.

---

## 6. Desktop App — `:desktop`

### All Screens

| Screen | File | What It Does |
|---|---|---|
| **Home** | `HomeScreen.kt` | Hero banner, Featured/All/Custom tabs, 4-column task grid |
| **Model List** | `ModelListScreen.kt` | Filter by category, search by name, shows ready status |
| **Chat** | `ChatScreen.kt` | Multi-turn streaming chat, Thinking Mode toggle, Enter to send |
| **Prompt Lab** | `PromptLabScreen.kt` | Split pane: left = prompt controls, right = streaming response. 4 templates, style chips, parameter sliders |
| **Ask Image** | `AskImageScreen.kt` | JFileChooser image picker, preview, vision model inference |
| **Audio Scribe** | `AudioScribeScreen.kt` | javax.sound.sampled mic capture, whisper transcription |
| **Agent Skills** | `AgentSkillsScreen.kt` | Tool-augmented LLM: Wikipedia, Calculator, WebSearch, FileReader. Structured tool call display |
| **Desktop Actions** | `DesktopActionsScreen.kt` | Shell (ProcessBuilder), File open (java.awt.Desktop), Clipboard (java.awt.Toolkit), window list (wmctrl) |
| **Tiny Garden** | `TinyGardenScreen.kt` | Idle mini-game: 4 plants × 5 growth stages, AI commentary per-watering |
| **Model Manager** | `ModelManagerScreen.kt` | Ollama pull (streaming progress), delete (confirmation), benchmark (tok/s) |
| **Phone Explorer** | `PhoneExplorerScreen.kt` | Browse phone data by source, semantic search over memories, ask phone context in chat |

### Visual Design

Lumen exactly mirrors the Google AI Edge Gallery Android app's design language:

| Token | Value |
|---|---|
| Background | `#131314` |
| Surface | `#1E1F20` |
| Surface Elevated | `#282A2C` |
| Surface Highest | `#333537` |
| Primary | `#3174F1` (dark) / `#A8C7FA` (light) |
| Font | Nunito (Regular, SemiBold, Bold, ExtraBold) |
| Card radius | 24dp |
| Wordmark gradient | `#A8C7FA` → `#669DF6`, Nunito Bold |
| Task icon style | Gradient-filled rounded squares |
| Chat bubble (user) | Blue, right-aligned |
| Chat bubble (assistant) | Dark gray, left-aligned |

Font files live in `desktop/src/jvmMain/resources/fonts/` as bundled TTF assets.

### Navigation

Navigation is a simple sealed class back stack — no Jetpack Navigation (not available for desktop):

```kotlin
sealed class Screen {
    object Home : Screen()
    data class ModelList(val taskType: TaskType) : Screen()
    data class Task(val task: LumenTask, val model: ModelInfo) : Screen()
}
```

`LumenApp.kt` maintains a `mutableStateListOf<Screen>` as the back stack. `Crossfade` animates between screens. The `Escape` key always pops the back stack.

### Settings Persistence

`JvmSettingsRepository` writes to:
- Linux: `~/.config/lumen/settings.json`
- Windows: `%APPDATA%\Lumen\settings.json`

Schema:
```json
{
    "theme": "SYSTEM",
    "ollamaBaseUrl": "http://localhost:11434",
    "defaultModelName": ""
}
```

### Packaging

Compose Multiplatform's packaging task produces OS-native distributions:
- Linux: `.deb` package in `desktop/build/compose/binaries/`
- Windows: `.exe` installer

Entry point: `com.lumen.desktop.MainKt`
Window size at launch: 1280×800

---

## 7. Server — `:server`

### Purpose

`lumen-server` is the desktop-side brain for phone data. It receives structured events from the Android app over Tailscale HTTP, archives them in Postgres, and embeds text-bearing ones with pgvector. The existing chat/agent pipelines that already query `memories` pick up phone context automatically — no changes to those pipelines were needed.

### Ktor Routes

```
POST /api/phone/events          ← Batch ingest (idempotent on event_hash)
GET  /api/phone/status          ← Per-source event counts + last-seen timestamps
GET  /api/pair/qr               ← SVG QR code encoding pairing payload (unauth, tailnet-only)
GET  /api/pair/payload          ← Same payload as plain JSON (debug endpoint)
```

Authentication: Bearer token stored in `EncryptedSharedPreferences` on the phone, verified on the server. The QR pairing endpoint is intentionally unauthenticated — it's only accessible on the Tailscale tailnet, which is already trusted.

### Postgres Schema

Three tables managed by Exposed ORM:

**`phone_events`**
```sql
id             BIGSERIAL PRIMARY KEY
source         TEXT NOT NULL          -- "sms", "calllog", etc.
event_hash     TEXT UNIQUE NOT NULL   -- sha256(source|naturalId|capturedAtMs)
payload        JSONB NOT NULL         -- raw event data
captured_at    TIMESTAMPTZ NOT NULL
ingested_at    TIMESTAMPTZ NOT NULL DEFAULT now()
embedded       BOOLEAN NOT NULL DEFAULT false
```

**`memories`** (existing table, extended for phone data)
```sql
id             BIGSERIAL PRIMARY KEY
content        TEXT NOT NULL          -- natural-language prose of the event
embedding      VECTOR(768)            -- nomic-embed-text output
source         TEXT                   -- "sms", "calendar", etc. (null = chat history)
source_event_id BIGINT REFERENCES phone_events(id)
created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
```
Index: `ivfflat` cosine similarity on `embedding` (pgvector)

**`conversations`**
```sql
id             BIGSERIAL PRIMARY KEY
title          TEXT
device_origin  TEXT                   -- "ANDROID" | "DESKTOP"
created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
```

### PhoneMemoryEnricher

The enricher runs on a **30-second tick** and processes un-embedded phone events:

1. Query `phone_events WHERE embedded = false AND source IN (MEMORY_WORTHY_SOURCES) ORDER BY captured_at DESC LIMIT 100` (drain-hot-tail: newest first)
2. For each event, format a natural-language prose description (e.g., SMS → "Text from Alice on Monday: 'Hey, are you free tonight?'")
3. Call `EmbeddingService` with `Semaphore(5)` parallelism
4. Insert into `memories`
5. Mark `phone_events.embedded = true`

**Memory-worthy sources** (`MEMORY_WORTHY_SOURCES` in `PhoneEventRepository.kt`):
```kotlin
private val MEMORY_WORTHY_SOURCES = setOf("sms", "notification", "calendar", "clipboard", "contacts")
```

The other five sources (`calllog`, `usagestats`, `photos`, `files`, `location`) are archived as structured JSONB but never embedded. A GPS coordinate or a file size run through a language model produces noise, not signal.

**Throughput**: ~26 events/sec sustained on GTX 1660 Super with `nomic-embed-text`.

### EmbeddingService

Calls Ollama's embedding API:
```
POST http://localhost:11434/api/embeddings
{ "model": "nomic-embed-text", "prompt": "..." }
```

Returns a 768-dimensional float vector. Stored in pgvector's `VECTOR(768)` column.

### Docker Compose

`docker-compose.yml` at project root starts Postgres with pgvector:
```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: lumen
      POSTGRES_USER: lumen
      POSTGRES_PASSWORD: lumen
    ports:
      - "5432:5432"
```

Start with: `docker compose up -d`

---

## 8. GardenLab — Deep Dive

GardenLab is an **iterative, LLM-driven farming simulation** running entirely on-device on the Galaxy Z Fold 7 using the Gemma-4-E2B-it model via LiteRT-LM.

### World Model

**`World.kt`** — A 6×6 grid of tiles:

| Tile Type | Description |
|---|---|
| `GRASS` | Barren, nothing grows here |
| `SOIL` | Plantable; basic yield |
| `RICH_SOIL` | Plantable; higher yield |
| `WATER` | Water source; the bee can water adjacent soil |

**`Bee.kt`** — The farmer agent:
- Properties: `x`, `y`, `facing` (N/E/S/W), `energy`, `inventory` (Map<CropType, Int>)
- Can move, turn, plant seeds, water, harvest, rest
- Deterministic — same script always produces the same world state

**`Species.kt`** — Crop definitions:
- Each species has growth stages (SEED → SPROUT → MATURE → HARVESTABLE → DEAD)
- Growth advances deterministically when a tile is watered
- Each crop has an `energyCost` to plant and a `yieldAmount` to harvest

### DSL (Domain-Specific Language)

The bee's behavior is controlled by a small farming script DSL. The LLM generates these scripts each iteration.

Example script:
```
MOVE NORTH
MOVE NORTH
PLANT WHEAT
MOVE EAST
WATER
HARVEST NORTH
REPEAT 3 {
    MOVE SOUTH
    WATER
}
```

**Pipeline: `Lexer` → `Parser` → `Interpreter`**

- **`Lexer.kt`**: Tokenizes the script text into a list of `Token` objects (KEYWORD, IDENTIFIER, NUMBER, BRACE_OPEN, BRACE_CLOSE, newlines)
- **`Parser.kt`**: Builds an AST of `Instruction` sealed classes (Move, Turn, Plant, Water, Harvest, Repeat, If)
- **`Interpreter.kt`**: Walks the AST and calls `World`/`Bee` mutation methods. Returns the final `WorldState` after execution.
- **`ExecutionBudget.kt`**: A step counter injected into the interpreter. Throws `BudgetExceededException` if a script executes more than N steps (prevents infinite loops from `REPEAT 9999` or recursive constructs).

### AgentLoop — 8 Phases

`AgentLoop.kt` orchestrates each iteration through exactly 8 phases, published via a callback:

```
LOADING       ← Load world state + active quest from Room
RUNNING_SCRIPT ← Execute current DSL script against world
EVALUATING_QUEST ← Check if quest completion conditions are met
CALLING_LLM   ← Send (world state + quest + previous critique) to LLM
PARSING       ← Parse LLM output to extract new DSL script + critique
VALIDATING    ← Lex/parse the new script to catch syntax errors
PERSISTING    ← Save new script, critique, world state to Room
DONE          ← Emit completion, schedule next iteration
```

If `VALIDATING` fails (the LLM wrote invalid DSL), the loop uses the previous iteration's script as a fallback and logs the parse error in the critique for the next call.

`GardenLabViewModel.kt` manages the live loop:
- Holds an `iterationMutex: Mutex` — prevents concurrent calls to `AgentLoop.iterate()`
- Publishes the current phase as `StateFlow<Phase>` for UI display
- Logcat tracing on every phase transition (tag: `GardenLab`)
- Live loop is launched on `Dispatchers.Default`, not the main thread

### Quest System

Each GardenLab session has one active quest. Quests are generated by the LLM from the world state and persisted in Room.

**4 Quest Types** (`Quest.kt`):

| Type | Example | Win Condition |
|---|---|---|
| `HARVEST_COUNT` | "Harvest 5 WHEAT" | `totalHarvested[crop] >= target` |
| `ROW_FULL` | "Fill row 2 with CORN" | All SOIL/RICH_SOIL in row N contain the named crop |
| `COLUMN_FULL` | "Fill column 4 with ROSE" | All SOIL/RICH_SOIL in column N contain the named crop |
| `SURVIVE_COUNT` | "Keep 3 SUNFLOWER alive for 5 turns" | Named crop count >= target for consecutive turns |

**`QuestGenerator.kt`**: Parses LLM JSON output to extract quest candidates. Validates each candidate against the current world state (e.g., `ROW_FULL` requires that row to have plantable tiles). Scores candidates by difficulty and selects the best one.

**`QuestEngine.kt`**: Called during `EVALUATING_QUEST`. Returns `QuestResult.COMPLETED`, `QuestResult.FAILED`, or `QuestResult.IN_PROGRESS`.

### LiteRT-LM Crash History

This is a dense area of learned lessons — see [Section 12](#12-lessons-learned) for the full list. Short summary:

1. **2Hz UI thrash** (fixed in commit `7990100`): Live loop was calling a NoOp `LlmClient` because `LlmClientHolder` wasn't initialized. Always guard with `LlmClientHolder.isInitialized` before starting the loop.

2. **SIGSEGV at iteration 7** (fixed in commit `90b232d`): Native JNI was running on the main thread. Fixed by dispatching to `Dispatchers.Default`.

3. **Token queue overflow at iteration 7** (fixed in commit `30032f2`): `sendMessage()` doesn't drain the engine's input queue. Fixed by switching to `runInference()`.

---

## 9. Phone Data Mining — Deep Dive

### Architecture

```
┌─────────────────────── Phone (Lumen Android) ─────────────────────────────┐
│                                                                            │
│  Ten Collectors (one Kotlin file each)                                     │
│    SmsCollector · CallLogCollector · ContactsCollector                     │
│    CalendarCollector · NotificationCollector · ClipboardCollector          │
│    UsageStatsCollector · PhotosMetadataCollector                           │
│    FilesCollector · LocationCollector                                      │
│           │  RawEvent(source, naturalId, capturedAtMs, payload)            │
│           ▼                                                                │
│  Room outbox (pending_events)                                              │
│    INSERT OR IGNORE ON eventHash = sha256(source|naturalId|capturedAtMs)   │
│           │                                                                │
│           ▼                                                                │
│  SyncEngine (pure logic — no Android deps)                                 │
│      ├─ Wi-Fi   → PhoneDataForegroundService  (250 ms debounced flush)     │
│      └─ Cellular → PhoneDataPeriodicWorker    (WorkManager, 15 min)        │
│           │                                                                │
│           ▼                                                                │
│  TailscaleDiscovery — MagicDNS lookup, cached host                        │
└───────────┼────────────────────────────────────────────────────────────────┘
            │  POST /api/phone/events over Tailscale HTTP (bearer auth)
            ▼
┌─────────────────────── Desktop (lumen-server) ─────────────────────────────┐
│                                                                            │
│  PhoneEventRepository → phone_events (source, event_hash UNIQUE, payload)  │
│           │                                                                │
│           ▼                                                                │
│  PhoneMemoryEnricher (30 s tick, Semaphore(5), drain-hot-tail ordering)    │
│           │                                                                │
│           ▼                                                                │
│  EmbeddingService (Ollama nomic-embed-text → 768-dim vector)               │
│           │                                                                │
│           ▼                                                                │
│  memories (pgvector ivfflat cosine index)                                  │
│                                                                            │
│  → chat / agents query memories — phone data appears automatically          │
└────────────────────────────────────────────────────────────────────────────┘
```

### Ten Collectors

| # | Source | Android API | Permission | Backfill | Observe Method | Memory-Worthy |
|---|---|---|---|---|---|---|
| 1 | `sms` | `Telephony.Sms` | `READ_SMS` (runtime) | ✅ | ContentObserver | ✅ |
| 2 | `calllog` | `CallLog.Calls` | `READ_CALL_LOG` (runtime) | ✅ | ContentObserver | — |
| 3 | `contacts` | `ContactsContract.Contacts` | `READ_CONTACTS` (runtime) | ✅ | ContentObserver | ✅ |
| 4 | `calendar` | `CalendarContract.Events` | `READ_CALENDAR` (runtime) | ✅ | ContentObserver | ✅ |
| 5 | `notification` | `NotificationListenerService` | Special (settings) | — | SharedFlow from listener | ✅ |
| 6 | `clipboard` | `ClipboardManager` | None (foreground only) | — | `OnPrimaryClipChangedListener` | ✅ |
| 7 | `usagestats` | `UsageStatsManager.INTERVAL_DAILY` | `PACKAGE_USAGE_STATS` (settings) | ✅ (30d) | 15-min poll | — |
| 8 | `photos` | `MediaStore.Images.Media` | `READ_MEDIA_IMAGES` (runtime, API 33+) | ✅ | ContentObserver | — |
| 9 | `files` | `MediaStore.Files` | Multiple media permissions | ✅ | ContentObserver | — |
| 10 | `location` | `LocationManager` (GPS + NETWORK) | `ACCESS_FINE/COARSE_LOCATION` + background | — | LocationListener (60s / 25m minimum) | — |

**Memory-worthy = should be embedded with pgvector** (has natural-language value when recalled in chat/agents).

Non-memory-worthy sources (calllog, usagestats, photos, files, location) are archived as structured JSONB for a future quantified-self dashboard, but are never sent through the embedding model.

### RawEvent and Idempotency

Every collector emits `RawEvent`:
```kotlin
data class RawEvent(
    val source: String,
    val naturalId: String,          // Provider-assigned ID or composite key
    val capturedAtEpochMs: Long,
    val payload: Map<String, String>,
) {
    val eventHash: String by lazy {
        sha256("$source|$naturalId|$capturedAtEpochMs")
    }
}
```

`eventHash` is the idempotency key through the entire pipeline:
- Room: `INSERT OR IGNORE` on `event_hash`
- Server: `INSERT ... ON CONFLICT (event_hash) DO NOTHING`

Re-running a backfill — whether accidental or intentional — is completely safe and cheap.

### Network-Aware Sync (Two-Tier Strategy)

**Tier 1 — Wi-Fi (aggressive):**
- `PhoneDataForegroundService` runs as a persistent Android foreground service
- Shows a permanent notification ("Lumen is syncing your phone data")
- 250ms debounced flush: when a new event lands in the outbox, wait 250ms, then flush all pending events to the server in a single batch
- Immediate: changes appear in `lumen-server` within ~500ms of capture

**Tier 2 — Cellular (conservative):**
- `PhoneDataPeriodicWorker` (WorkManager) runs every 15 minutes
- Additional expedited trigger: if the outbox grows to 50+ events, a one-time expedited `WorkRequest` is enqueued immediately
- Reduces battery and mobile data usage

`NetworkModeController` uses `ConnectivityManager.NetworkCallback` to switch between tiers as connectivity changes.

### TailscaleDiscovery + QR Pairing

1. **Desktop** serves a pairing payload at `GET /api/pair/qr` (SVG QR code) and `GET /api/pair/payload` (JSON)
2. Pairing payload: `{ "host": "mydesktop.tail12345.ts.net", "token": "..." }`
3. **Phone** scans the QR code using Google Play Services code scanner
4. Saves host + token to `EncryptedSharedPreferences`
5. `TailscaleDiscovery` does a MagicDNS lookup on the saved host at sync time and caches the result
6. All phone→server traffic is HTTP over the Tailscale tailnet (encrypted by WireGuard, trusted network)

### Server Enricher Performance

Measured on GTX 1660 Super (6 GB VRAM):
- Embedding model: `nomic-embed-text` (768-dim)
- Parallelism: `Semaphore(5)` (5 concurrent Ollama embedding requests)
- Scheduling: drain-hot-tail (newest events first, to keep recent context fresh)
- Sustained throughput: **~26 events/sec**

This means a full backfill of 30 days of SMS (~3,000 messages) completes in under 2 minutes.

---

## 10. Testing

### Test Counts by Module

| Module | Test Suite | Count | Notes |
|---|---|---|---|
| `:shared` (commonTest) | `ConversationMessageTest` | 3 | Message creation, roles, image attachment |
| `:shared` (commonTest) | `InferenceEngineContractTest` | 3 | Interface contract via FakeInferenceEngine |
| `:shared` (desktopTest) | `OllamaInferenceEngineTest` | 2 | Streaming token parsing, reachability (MockEngine) |
| `:shared` (desktopTest) | `OllamaModelRepositoryTest` | 2 | Model list parsing, readiness (MockEngine) |
| `:shared` (desktopTest) | `JvmSettingsRepositoryTest` | 3 | Defaults, persistence, Flow emission |
| `:app` (androidTest) | Room DAO tests | varies | In-memory Room, real SQL |
| `:app` (test) | `AgentLoopTest` | — | Mocked LlmClient, phase sequence |
| `:app` (test) | `WorldTest` | — | Grid mutation, tile state |
| `:app` (test) | `InterpreterTest` | — | DSL execution, budget enforcement |
| `:app` (test) | `PendingEventDaoTest` | — | Outbox insert-or-ignore, batch query |

Total: **25+ tests**

### Ktor HTTP Tests (MockEngine)

All desktop Ktor client tests use `MockEngine` — no running Ollama instance required:

```kotlin
val client = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            when (request.url.encodedPath) {
                "/api/chat" -> respond(
                    content = """{"message":{"role":"assistant","content":"Hello"},"done":true}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unhandled ${request.url}")
            }
        }
    }
}
```

### Robolectric Tests

All Robolectric tests in `:app` must use this annotation to avoid Koin conflicts:
```kotlin
@Config(application = android.app.Application::class)
class MyTest { ... }
```

Without it, Robolectric instantiates `GalleryApplication`, which calls `startKoin()`. The second test's `setUp()` then fails with `KoinAppAlreadyStartedException` because Koin is already running.

### Room DAO Tests

Room DAO tests use an in-memory database:
```kotlin
@Before
fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        GardenLabDb::class.java
    ).allowMainThreadQueries().build()
    dao = db.generationDao()
}
```

---

## 11. Build System

### Gradle Configuration

| Item | Value |
|---|---|
| Gradle version | 8.8.2 |
| AGP version | 8.8.2 |
| Kotlin version | 2.2.0 |
| KSP version | 2.2.0-2.0.2 |
| JVM target | 17 |

### Key Build Rules

**KSP version MUST match Kotlin version.** The format is `{kotlin_version}-{ksp_release}`. For Kotlin 2.2.0, the correct KSP version is `2.2.0-2.0.2`. Using a standalone KSP version (e.g., `2.3.6`) that targets a different Kotlin stdlib causes `unexpected jvm signature V` errors in KSP processors.

**Room 2.7.0+ required for KSP2.** Room 2.6.1 uses `RoomKspProcessor` internals that break under KSP2 AA mode. Room 2.7.0 has native KSP2 support.

**Robolectric 4.14.1+ required for SDK 35.** Robolectric 4.13 has `maxSdkVersion = 34`. With `targetSdk = 35`, tests fail with `Package targetSdkVersion=35 > maxSdkVersion=34`.

### Complete Version Table (`libs.versions.toml`)

| Key | Version |
|---|---|
| `kotlin` | 2.2.0 |
| `ksp` | 2.2.0-2.0.2 |
| `compose-multiplatform` | 1.8.0 |
| `agp` | 8.8.2 |
| `koin` | 3.5.6 |
| `ktor` | 2.3.12 |
| `kotlinx-serialization` | 1.7.3 |
| `kotlinx-coroutines` | 1.8.1 |
| `room` | 2.7.0 |
| `litert-lm` | 0.10.0 |
| `robolectric` | 4.14.1 |
| `workmanager` | 2.10.0 |
| `datastore` | latest stable |

### Build Tasks Cheat Sheet

```bash
# Enter the Gradle root
cd Android/src

# Run the desktop app
./gradlew :desktop:run

# Build the Android APK and install to device
./gradlew :app:assembleDebug && adb install app/build/outputs/apk/debug/app-debug.apk

# Run all shared module tests
./gradlew :shared:allTests

# Run Android unit tests
./gradlew :app:test

# Run Android instrumented tests
./gradlew :app:connectedAndroidTest

# Package desktop distributable
./gradlew :desktop:packageDistributionForCurrentOS

# Build the server JAR
./gradlew :server:shadowJar

# Check all modules compile
./gradlew build

# Clean build
./gradlew clean build
```

---

## 12. Lessons Learned

This section consolidates everything learned the hard way — from `lessons/` files and git commit post-mortems. **Read this before every session.**

---

### L1 — KSP Version Pinning (CRITICAL)

**Rule**: KSP version in `libs.versions.toml` must use the format `{kotlin_version}-{ksp_release}`. Example for Kotlin 2.2.0: `ksp = "2.2.0-2.0.2"`.

**Why**: KSP is a plugin that extends the Kotlin compiler. It links against the Kotlin stdlib. If the KSP version targets a different stdlib version than the project's Kotlin version, annotation processing fails with cryptic `unexpected jvm signature V` errors in every KSP-powered processor (Room, Moshi, etc.).

**How to apply**: When upgrading Kotlin, always look up the corresponding KSP2 release tag. Never use a standalone `2.x.y` KSP version. The KSP GitHub releases page lists which Kotlin version each KSP release supports.

---

### L2 — Room 2.6.1 → 2.7.0 Required for KSP2

**Rule**: Use Room 2.7.0 or later. Never use Room 2.6.1 with KSP2.

**Why**: Room 2.6.1's `RoomKspProcessor` uses KSP1 internal APIs that fail under KSP2 AA (Analysis API) mode. The error is the same `unexpected jvm signature V` as L1, making it hard to distinguish from a KSP version mismatch. Room 2.7.0 has native KSP2 support and no longer uses the old APIs.

**How to apply**: After upgrading Kotlin (and therefore KSP), always verify Room is on 2.7.0+.

---

### L3 — Moshi Codegen Removed (Do Not Re-Add)

**Rule**: Do not add `ksp(libs.moshi.kotlin.codegen)` back to any `build.gradle.kts` until a Moshi release explicitly states KSP2 compatibility.

**Why**: `moshi-kotlin-codegen:1.15.2` depends on `symbol-processing-api:1.9.0-1.0.13` (KSP1 API) and fails under KSP2 AA mode with the same `unexpected jvm signature V`. Moshi 1.15.2 is the current latest stable — no KSP2-compatible version exists yet.

**How to apply**: The `moshi-kotlin` reflection adapter handles all `@JsonClass(generateAdapter = true)` usages at runtime without codegen. Minor reflection overhead, but acceptable for a mobile app. The reflection adapter is already in the dependency graph.

---

### L4 — Robolectric 4.13 → 4.14.1 Required for SDK 35

**Rule**: Use Robolectric 4.14.1 or later when `targetSdk = 35`.

**Why**: Robolectric 4.13's `maxSdkVersion = 34`. With `targetSdk = 35`, any test annotated with `@RunWith(RobolectricTestRunner::class)` fails immediately: `Package targetSdkVersion=35 > maxSdkVersion=34`.

**How to apply**: After upgrading `targetSdk`, check Robolectric's supported SDK table and update accordingly.

---

### L5 — Koin in Robolectric Tests (CRITICAL Pattern)

**Rule**: Every Robolectric test class in `:app` must be annotated with:
```kotlin
@Config(application = android.app.Application::class)
```

**Why**: Without this annotation, Robolectric instantiates the real `GalleryApplication`, which calls `startKoin()`. After the first test, subsequent `setUp()` calls hit `KoinAppAlreadyStartedException` because Koin is a process-level singleton and it's already running from the previous test's application instantiation.

**How to apply**: This annotation tells Robolectric to use a plain bare `Application` instead of `GalleryApplication`, bypassing Koin initialization entirely. Any test that needs specific Koin modules should set them up manually within the test.

---

### L6 — LiteRT-LM: `runInference` Not `sendMessage`

**Rule**: Always use `LlmChatModelHelper.runInference()`. Never use `sendMessage()` in multi-call contexts.

**Why**: `sendMessage()` is synchronous and does not drain the engine's internal input token queue between calls. After approximately 7 sequential LLM calls in the same session (e.g., the GardenLab iterative loop), the token queue overflows and the native LiteRT layer crashes — usually a SIGSEGV or an `IllegalStateException` from the JNI bridge.

`runInference()` is the async path that other Gallery tasks (chat, ask image, etc.) use. It properly drains the queue via a suspending callback.

**How to apply**: In `LiteRtLmClient.kt`, the implementation should use:
```kotlin
withContext(Dispatchers.Default) {
    suspendCancellableCoroutine { continuation ->
        val sb = StringBuilder()
        llmChatModelHelper.runInference(prompt) { partial, done ->
            sb.append(partial)
            onToken(partial)
            if (done) continuation.resume(sb.toString())
        }
    }
}
```

---

### L7 — LiteRT-LM: Never Run on Main Thread

**Rule**: All `LlmChatModelHelper` calls must run on `Dispatchers.Default`. Never call native LiteRT methods from the main thread.

**Why**: LiteRT's JNI bridge is not main-thread safe. Calling it from the main thread produces a SIGSEGV (native crash). The crash manifests immediately on the first call — it's not a timing issue.

**How to apply**: Wrap every LiteRT call:
```kotlin
withContext(Dispatchers.Default) {
    // LiteRT calls here
}
```

In `GardenLabViewModel.kt`, the entire live loop is launched on `Dispatchers.Default`:
```kotlin
viewModelScope.launch(Dispatchers.Default) {
    agentLoop.iterate()
}
```

---

### L8 — LiteRT-LM: Reset Conversation Between Iterations

**Rule**: Call `llmChatModelHelper.resetConversation()` before each LLM call in a multi-iteration loop.

**Why**: LiteRT-LM accumulates conversation context across calls within the same session. In GardenLab's iterative loop (generate script → critique → generate → critique → ...), each call is logically independent (single-turn). Without resetting, the context window grows with every iteration. After enough iterations, the context exceeds the model's window size and the inference quality degrades or crashes.

**How to apply**: GardenLab treats every LLM call as stateless. Reset before each:
```kotlin
llmChatModelHelper.resetConversation()
llmChatModelHelper.runInference(prompt) { ... }
```

---

### L9 — LiteRT-LM: Guard LlmClientHolder Initialization

**Rule**: Before starting the GardenLab live loop, always check that `LlmClientHolder.isInitialized` is true.

**Why**: `LlmClientHolder` is set when the user selects a model in the UI. If the live loop starts before model initialization completes (e.g., on app resume, during configuration change), it gets a NoOp `LlmClient`. The loop runs at 2Hz producing nothing, consuming battery, and updating the UI with empty critiques.

**How to apply**: In `GardenLabViewModel.kt`:
```kotlin
if (!LlmClientHolder.isInitialized) {
    _phase.value = Phase.LOADING
    return
}
// Safe to start the loop
agentLoop.iterate()
```

---

### L10 — Koin Named Qualifiers for Generic Set Bindings

**Rule**: When registering multiple `Set<SomeInterface>` bindings in Koin, always use `named("uniqueName")` to differentiate them.

**Why**: Kotlin generics are erased at JVM runtime. To the JVM, `Set<Collector>` and `Set<CustomTask>` are both just `Set`. Koin uses runtime type information to resolve injections. Without named qualifiers, the two bindings collide and one silently overwrites the other.

**How to apply**:
```kotlin
// Registration
single(named("phonedataCollectors")) {
    setOf(get<SmsCollector>(), get<CallLogCollector>(), ...)
}

// Injection
val collectors: Set<Collector> by inject(named("phonedataCollectors"))
```

Apply this pattern to any future `Set<T>` where multiple `Set` bindings coexist.

---

## 13. Pending Work / Known Gaps

### Quantified-Self Dashboard
**What**: A visual dashboard showing timelines, usage patterns, and trends from the non-memory-worthy collectors (calllog, usagestats, photos, files, location).

**Status**: The data is already being collected and archived in `phone_events` as JSONB. The server routes and Postgres schema are in place. What's missing: the frontend (both `PhoneExplorerScreen.kt` on desktop and the Android Phone Data card) currently shows only raw per-source event counts.

**When to build**: After confirming the embedding pipeline is stable and the memory-worthy sources are reliably surfacing in chat.

---

### Desktop Settings UI
**What**: An in-app settings screen (theme picker, Ollama URL input, default model selector).

**Status**: Settings are persisted via `JvmSettingsRepository` and the `AppSettings` data model is complete. The UI screen doesn't exist yet — users must edit `~/.config/lumen/settings.json` manually.

**Estimated scope**: 1 new `SettingsScreen.kt`, 1 new navigation entry in `Screen`, ~50 LOC in `LumenApp.kt` for routing.

---

### Full `androidMain` Koin Bindings in `:shared`
**What**: The `AndroidModule.kt` in `:shared/androidMain` is a placeholder. Currently, all Android DI lives in `:app`'s `AppModule.kt` because LiteRT requires Android `Context`.

**Status**: Acceptable for now since `:app` is the only Android consumer of `:shared`. If a second Android module is added, the bindings should be promoted to `:shared/androidMain`.

---

### Automated CI
**What**: A CI pipeline (GitHub Actions or similar) that runs `./gradlew :shared:allTests` and `./gradlew :app:test` on every push.

**Status**: Not configured. Currently all tests are run manually.

---

### Upstream Gallery Sync
**What**: Periodic rebase/merge from the upstream `google-ai-edge/gallery` repo to pick up model updates, bug fixes, and new features.

**Status**: The `gallery/` directory (untracked) is a local copy of upstream. The project is currently **47 commits ahead of origin/main** with no upstream tracking configured.

**Risk**: The longer we diverge without upstream syncs, the harder a future merge becomes. The Hilt → Koin migration touches 32 files — any upstream change to those files creates a merge conflict.

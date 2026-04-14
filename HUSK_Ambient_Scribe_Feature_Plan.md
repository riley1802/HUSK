# HUSK — Ambient Scribe Feature Plan

> **Status:** Planning / Pre-Development
> **Author:** Riley
> **Date:** April 14, 2026
> **Target Platform:** Android 12+ (Samsung Galaxy Z Fold 7 primary target)
> **Repository:** [github.com/riley1802/HUSK](https://github.com/riley1802/HUSK)

---

## 1. Overview

### 1.1 What Is Ambient Scribe?

Ambient Scribe is a new standalone tile for the HUSK hub screen. It runs an always-on microphone service in the background, continuously capturing and transcribing ambient audio on-device using a local ASR (Automatic Speech Recognition) model. Transcriptions are stored locally and presented through a calendar-based UI where users can browse, search, and review any day's full transcript.

Think of it as a **private, offline, on-device life logger** — every conversation, meeting, lecture, or passing thought captured and transcribed without ever leaving the phone.

### 1.2 Why This Feature?

HUSK already has Audio Scribe for manual record-and-transcribe sessions. Ambient Scribe takes that concept to its logical extreme: **passive, continuous, zero-effort capture**. The value proposition is simple — you never have to remember to hit record, and you never lose a conversation again.

This feature leans hard into HUSK's core identity:

- **100% on-device** — no cloud, no API calls, total privacy
- **Powered by open-source models** — ASR models running via LiteRT
- **Monochrome dark aesthetic** — calendar UI fits the existing Husk design language
- **Showcases on-device AI capability** — continuous real-time inference is a flex

### 1.3 Core User Stories

- "I want to passively record and transcribe everything I hear throughout the day so I can search it later."
- "I want a calendar view where I can tap any day and scroll through a timestamped transcript of what was said."
- "I want all of this to happen entirely on my phone with no internet required."
- "I want to be able to search across days — 'when did I mention X?'"

---

## 2. Feature Anatomy

### 2.1 Hub Tile

A new tile on the HUSK home screen called **"Ambient Scribe"** (or a shorter name like **"Echo"**, **"Murmur"**, or **"Trace"** — TBD). The tile shows:

- Current status indicator (recording / paused / idle)
- Today's word count or duration captured
- Tap to open the calendar view

### 2.2 Calendar View (Primary UI)

The main interface is a **monthly calendar grid** in HUSK's monochrome dark theme.

- Each day cell shows a visual indicator of how much audio was captured (e.g., a small bar or dot density)
- Days with no data are dimmed
- Tap a day to open the **Day Detail View**
- Swipe left/right to navigate months
- Top bar: month/year, search icon, settings gear

### 2.3 Day Detail View

When you tap a day on the calendar, you see:

- **Timeline** — a vertically scrolling list of transcript segments, each with a timestamp
- Each segment represents a chunk of continuous speech (e.g., 30s–2min window)
- Gaps in recording (silence, paused periods) are shown as visual breaks in the timeline
- Segments are color-coded or icon-tagged by confidence level
- Tap a segment to expand it and see full text
- Long-press a segment to bookmark/star it
- Floating action button: play back the original audio (if audio retention is enabled)

### 2.4 Background Service

The core engine — a persistent Android foreground service that:

1. Captures raw audio from the microphone
2. Buffers audio into chunks (configurable window size)
3. Runs on-device ASR inference on each chunk via LiteRT
4. Writes the resulting text + timestamp + metadata to local storage
5. Optionally retains the raw audio clip alongside the transcript
6. Shows a persistent notification with status and quick controls

---

## 3. Technical Architecture

### 3.1 Service Layer

```
┌──────────────────────────────────────────────────┐
│              Ambient Scribe Service               │
│            (Android Foreground Service)            │
│                                                    │
│  ┌──────────┐   ┌──────────────┐   ┌───────────┐ │
│  │ Mic Input │──▶│ Audio Buffer │──▶│ ASR Model │ │
│  │ (AudioRec)│   │ (Ring Buffer)│   │ (LiteRT)  │ │
│  └──────────┘   └──────────────┘   └─────┬─────┘ │
│                                          │        │
│  ┌──────────┐   ┌──────────────┐         │        │
│  │ Silence  │──▶│ Skip / Pause │         │        │
│  │ Detector │   │   Logic      │         │        │
│  └──────────┘   └──────────────┘         │        │
│                                          ▼        │
│                               ┌──────────────┐    │
│                               │  Local DB     │    │
│                               │  (Room/SQLite)│    │
│                               └──────────────┘    │
└──────────────────────────────────────────────────┘
```

### 3.2 Audio Pipeline

| Stage | Detail |
|---|---|
| **Capture** | `AudioRecord` API, 16kHz mono PCM (standard for most ASR models) |
| **Buffering** | Rolling ring buffer, configurable chunk size (default: 30 seconds) |
| **Silence Detection** | VAD (Voice Activity Detection) — skip chunks that are below the noise floor threshold to save processing and storage |
| **Inference** | LiteRT runtime invoking a Whisper-class or Moonshine-class on-device ASR model on each chunk |
| **Post-processing** | Basic punctuation restoration, segment joining for continuous speech that spans chunk boundaries |
| **Storage** | Room database with `TranscriptSegment` entities (see schema below) |

### 3.3 Data Model

```kotlin
@Entity(tableName = "transcript_segments")
data class TranscriptSegment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,              // Calendar day this belongs to
    val startTimestamp: Long,         // Epoch millis when this segment started
    val endTimestamp: Long,           // Epoch millis when this segment ended
    val text: String,                 // Transcribed text content
    val confidence: Float,            // Model confidence score (0.0–1.0)
    val isBookmarked: Boolean = false,// User-starred segment
    val audioFilePath: String? = null,// Path to raw audio clip (null if retention disabled)
    val durationMs: Long,             // Duration of the audio chunk in ms
    val wordCount: Int                // Word count for quick stats
)
```

```kotlin
@Entity(tableName = "daily_metadata")
data class DailyMetadata(
    @PrimaryKey val date: LocalDate,
    val totalDurationMs: Long,       // Total captured audio duration for the day
    val totalWordCount: Int,         // Total words transcribed
    val totalSegments: Int,          // Number of segments
    val firstSegmentTime: Long?,     // Timestamp of first capture
    val lastSegmentTime: Long?,      // Timestamp of last capture
    val summary: String? = null      // LLM-generated daily summary (stretch goal)
)
```

### 3.4 ASR Model Considerations

| Model | Size | Speed | Quality | Notes |
|---|---|---|---|---|
| **Whisper Tiny** | ~39MB | Fast | Decent | Well-supported, good baseline |
| **Whisper Base** | ~74MB | Moderate | Good | Better accuracy, still phone-friendly |
| **Moonshine Tiny** | ~30MB | Very Fast | Decent | Purpose-built for on-device, lower latency |
| **Moonshine Base** | ~60MB | Fast | Good | Strong balance of speed and accuracy |
| **Parakeet TDT 0.6B** | ~600MB | Slow | Excellent | NVIDIA model, probably too large for continuous use |

**Recommendation:** Start with **Whisper Tiny or Moonshine Tiny** for the always-on use case. Speed and battery matter more than perfect accuracy for a background service. Let users optionally select a larger model if they want higher quality and can tolerate the battery hit.

The model needs to be converted to a LiteRT-compatible format (TFLite or similar). Check existing LiteRT community models on Hugging Face (`litert-community`).

### 3.5 Storage Estimates

Assuming ~8 hours of active capture per day (silence-filtered):

| Metric | Estimate |
|---|---|
| Words per minute (spoken) | ~130 avg |
| Words per hour | ~7,800 |
| Words per 8-hour day | ~62,400 |
| Characters per day | ~375,000 (~375 KB) |
| Characters per month | ~11.25 MB (text only) |
| Characters per year | ~135 MB (text only) |
| Raw audio per day (16kHz mono 16-bit) | ~900 MB (if retained) |

**Conclusion:** Text-only storage is trivially small. Raw audio retention is expensive — this should be opt-in and probably set to auto-delete after N days by default.

---

## 4. UI Specifications

### 4.1 Screens

| Screen | Purpose |
|---|---|
| **Hub Tile** | Entry point, shows status + today's stats |
| **Calendar Grid** | Monthly view, tap a day to see its transcript |
| **Day Detail** | Timeline of transcript segments with timestamps |
| **Segment Detail** | Expanded view of a single segment (tap to expand) |
| **Search** | Full-text search across all days with results grouped by date |
| **Settings** | Service config: chunk size, model selection, audio retention, quiet hours, storage management |

### 4.2 Calendar Grid Mockup (Text)

```
┌─────────────────────────────────────────┐
│  ◀  April 2026                    🔍  ⚙  │
├─────┬─────┬─────┬─────┬─────┬─────┬─────┤
│ Sun │ Mon │ Tue │ Wed │ Thu │ Fri │ Sat │
├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
│     │     │  1  │  2  │  3  │  4  │  5  │
│     │     │ ▓▓  │ ░░  │ ▓▓▓ │ ▓▓  │     │
├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
│  6  │  7  │  8  │  9  │ 10  │ 11  │ 12  │
│     │ ▓▓▓ │ ▓▓▓ │ ▓▓  │ ▓▓▓ │ ▓▓  │ ░   │
├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
│ 13  │ [14]│ 15  │ 16  │ 17  │ 18  │ 19  │
│ ░   │ ▓▓● │     │     │     │     │     │
└─────┴─────┴─────┴─────┴─────┴─────┴─────┘

▓ = captured data density    ● = currently recording
░ = minimal data             [14] = today (highlighted)
```

### 4.3 Day Detail Mockup (Text)

```
┌─────────────────────────────────────────┐
│  ◀  Monday, April 14, 2026              │
│  8,432 words · 3h 12m captured          │
├─────────────────────────────────────────┤
│                                         │
│  09:12 AM ─────────────────────────     │
│  "...talked about the project timeline  │
│   and we agreed to push the deadline    │
│   to next Friday..."                    │
│                                    ★    │
│                                         │
│  ── 45 min gap ──                       │
│                                         │
│  10:03 AM ─────────────────────────     │
│  "...grabbing coffee, need to remember  │
│   to call the dentist before three..."  │
│                                         │
│  10:04 AM ─────────────────────────     │
│  "...yeah the new build is broken,      │
│   something in the Gradle config..."    │
│                                         │
│  ── 2 hour gap ──                       │
│                                         │
│  12:15 PM ─────────────────────────     │
│  "...ordering the usual, chicken        │
│   teriyaki bowl..."                     │
│                                         │
└─────────────────────────────────────────┘
```

### 4.4 Design Notes

- Follow HUSK's existing monochrome dark theme (dark grays, white text, minimal accent color)
- Calendar cells use subtle gradients or dot patterns to indicate data density — not color
- Timestamps in the day view use a left-aligned gutter with a vertical line connecting segments
- Gaps are shown as dashed or faded line breaks with duration labels
- Bookmarked segments get a star icon on the right edge
- Low-confidence segments get a subtle warning indicator (e.g., slightly dimmed text or a `⚠` icon)
- All Jetpack Compose — consistent with the rest of HUSK's UI

---

## 5. Feature Details

### 5.1 Always-On Foreground Service

Android requires a persistent notification for foreground services accessing the microphone. The notification should show:

- **Title:** "Ambient Scribe is listening"
- **Subtitle:** "Tap to pause · [X] words today"
- **Actions:** Pause/Resume button, Open app button
- **Icon:** Microphone icon (monochrome, matches HUSK branding)

Must handle:

- Service restart on device reboot (via `BOOT_COMPLETED` receiver)
- Battery optimization exclusion (prompt user to disable battery optimization for HUSK)
- Doze mode behavior — microphone access may be restricted
- Foreground service type: `microphone` (required on Android 14+)

### 5.2 Silence Detection / Voice Activity Detection (VAD)

Critical for battery life and storage. Without VAD, the service will waste resources transcribing hours of HVAC noise, traffic, and silence.

**Implementation options:**

1. **Simple energy-based VAD** — calculate RMS energy of each audio frame, only process chunks above a configurable threshold. Cheapest option, works surprisingly well.
2. **WebRTC VAD** — Google's well-tested VAD from the WebRTC project. Available as a small native library. More accurate than simple energy-based detection.
3. **Silero VAD** — ML-based VAD model, very small (~2MB), high accuracy. Runs as a separate small model inference. Best accuracy but adds a tiny amount of overhead.

**Recommendation:** Start with Silero VAD — it's small, accurate, and well-documented. The marginal overhead is worth it compared to wasting ASR inference cycles on silence.

### 5.3 Chunked Transcription Pipeline

Audio is processed in fixed-size windows, not as a continuous stream. This is necessary because:

- On-device ASR models expect fixed-length input
- It bounds memory usage
- It creates natural segment boundaries for the UI

**Chunk handling:**

```
Audio Stream:  ──────|──────|──────|──────|──────▶
                30s    30s    30s    30s    30s

VAD Filter:    ──────|SKIP  |──────|SKIP  |──────▶
                ✅     ❌      ✅     ❌      ✅

ASR Inference:  [T1]          [T2]          [T3]
                 │             │             │
                 ▼             ▼             ▼
              Segment 1    Segment 2    Segment 3
```

**Edge case — speech spanning chunk boundaries:** When speech is detected at the end of one chunk and the start of the next, the system should either:

- Use overlapping windows (e.g., 30s chunks with 5s overlap) and deduplicate
- Or post-process to merge adjacent segments that appear to be mid-sentence (heuristic: no sentence-ending punctuation at chunk boundary)

### 5.4 Search

Full-text search across the entire transcript database.

- Search bar accessible from the calendar view (top-right icon)
- Results grouped by date, showing matching segment with highlighted keyword
- Uses SQLite FTS5 (Full-Text Search) for fast queries over large corpora
- Search should feel instant even with months of data

**FTS5 setup:**

```sql
CREATE VIRTUAL TABLE transcript_fts USING fts5(
    text,
    content='transcript_segments',
    content_rowid='id'
);
```

### 5.5 Bookmarking / Starring

Users can long-press any transcript segment to bookmark it. Bookmarked segments:

- Show a star icon in the timeline
- Are accessible via a "Bookmarks" filter in the calendar view or settings
- Are excluded from auto-purge (if storage management is enabled)

### 5.6 Audio Retention (Optional)

By default, raw audio is discarded after transcription to save storage. Users can opt into audio retention in settings, which:

- Saves the raw audio chunk as a `.wav` or `.opus` file alongside the transcript segment
- Enables a "Play" button on each segment in the day detail view
- Adds an auto-delete policy: keep audio for N days (default: 7), then purge
- `.opus` encoding is strongly preferred — ~10x smaller than raw PCM

### 5.7 Quiet Hours

Configurable schedule to automatically pause the service. Default: disabled.

- Start time / end time picker (e.g., 11:00 PM – 7:00 AM)
- Per-day-of-week toggles (e.g., disable on weekends)
- Visual indicator on the calendar for quiet-hours periods

### 5.8 Storage Management

Settings screen with:

- Total storage used (text + audio breakdown)
- Auto-purge policy: delete transcripts older than N days/months (default: never for text, 7 days for audio)
- Manual purge: delete all data, or delete a specific date range
- Export: share a day's transcript as `.txt` or `.md` file

---

## 6. Permissions & Privacy

### 6.1 Required Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Microphone access |
| `FOREGROUND_SERVICE` | Keep service alive |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 14+ foreground service type |
| `POST_NOTIFICATIONS` | Persistent service notification |
| `RECEIVE_BOOT_COMPLETED` | Restart service on reboot |
| `WAKE_LOCK` | Keep CPU awake during inference |

### 6.2 Privacy Considerations

This is an extremely sensitive feature. HUSK's on-device architecture is the strongest privacy argument, but the feature still needs:

- **First-run consent dialog** — clearly explain what the feature does, that it records continuously, and that all data stays on-device
- **Recording indicator** — Android 12+ shows a green dot when the mic is active; this is automatic and cannot be suppressed (good — it's transparent)
- **No export to cloud** — the transcript database should never be synced, backed up to cloud, or transmitted off-device. Export is manual and explicit only.
- **Easy data deletion** — one-tap to delete all data, or delete specific days
- **Legal disclaimer** — recording conversations may be illegal in some jurisdictions without consent of all parties. Include a disclaimer in the first-run dialog.

### 6.3 Legal Notice (Draft)

> **Important:** Recording conversations may be subject to federal and state wiretapping laws. In many jurisdictions, you must obtain consent from all parties before recording a conversation. Ambient Scribe records audio from your device's microphone continuously while active. You are solely responsible for complying with all applicable laws regarding audio recording in your jurisdiction. Husk does not transmit or store any data off your device.

---

## 7. Battery & Performance

### 7.1 Battery Impact Estimates

| Component | Estimated Impact |
|---|---|
| Microphone capture (continuous) | Low (~2-3% per hour) |
| VAD inference (continuous, tiny model) | Negligible |
| ASR inference (intermittent, per-chunk) | Moderate (~3-5% per hour of active speech) |
| **Total estimated** | **~5-8% per hour of active speech** |

These are rough estimates — actual impact depends heavily on the device SoC, model size, and how much speech vs. silence is detected.

### 7.2 Optimization Strategies

1. **VAD gating** — only run ASR on chunks with detected speech (biggest win)
2. **Chunk size tuning** — larger chunks = fewer inference calls, but more latency
3. **Model quantization** — INT8 or INT4 quantized models are 2-4x faster with minimal quality loss
4. **NNAPI / GPU delegate** — offload inference to DSP/NPU if available on the device SoC
5. **Adaptive quality** — reduce model size or increase chunk interval when battery is low
6. **Background processing limits** — batch process accumulated audio when device is charging
7. **Thermal throttling awareness** — back off inference if device is overheating

### 7.3 Battery Settings UI

Expose a simple toggle in settings:

- **Performance mode:** Faster transcription, higher accuracy, more battery drain
- **Balanced mode:** Default, good tradeoff (recommended)
- **Battery saver mode:** Larger chunks, smaller model, minimal drain

---

## 8. Scope & Phasing

### 8.1 MVP (Phase 1) — "It Works"

The minimum viable version that delivers core value:

- [ ] Foreground service with mic capture and persistent notification
- [ ] Chunked audio pipeline with silence detection (energy-based VAD)
- [ ] On-device ASR via Whisper Tiny (LiteRT)
- [ ] Room database storage with `TranscriptSegment` schema
- [ ] Calendar grid view (monthly, tap day to see transcript)
- [ ] Day detail view with timestamped segments
- [ ] Pause/resume from notification and in-app
- [ ] Settings: toggle service on/off, clear all data
- [ ] First-run consent dialog with legal disclaimer
- [ ] Hub tile with status indicator

**Feasibility:** ✅ Doable now — all components are well-understood, no moonshot tech

### 8.2 Phase 2 — "It's Good"

Polish and usability improvements:

- [ ] Silero VAD (upgrade from energy-based)
- [ ] Full-text search with FTS5
- [ ] Bookmarking / starring segments
- [ ] Quiet hours scheduling
- [ ] Audio retention toggle with auto-purge
- [ ] Storage management screen
- [ ] Export day transcript as markdown
- [ ] Chunk boundary merging (cross-chunk sentence continuity)
- [ ] Battery mode selector (performance / balanced / saver)
- [ ] Model selection (let user pick Whisper Tiny vs Base vs Moonshine)

**Feasibility:** ⚡ Doable with effort — each item is incremental, no single item is hard

### 8.3 Phase 3 — "It's Amazing" (Stretch Goals)

Features that push the concept further:

- [ ] **Daily summary generation** — end-of-day LLM pass using a Gemma-class model to summarize the day's transcript into a paragraph. Shown at the top of the day detail view.
- [ ] **Speaker diarization** — label different speakers in the transcript (Speaker 1, Speaker 2). Requires either a dedicated diarization model or a multi-speaker-aware ASR model.
- [ ] **Keyword/entity extraction** — auto-detect names, places, dates, and action items. Highlight them in the transcript and make them searchable as tags.
- [ ] **Query mode** — instead of browsing, the user asks a natural language question ("What did I talk about at lunch Tuesday?") and gets an answer synthesized from the transcript. This would feed relevant transcript segments as context into HUSK's AI Chat.
- [ ] **Cross-feature integration** — show Ambient Scribe transcripts alongside Ask Image captures and Mobile Actions in a unified "Day Log" view.
- [ ] **Home screen widget** — shows today's status, word count, and last captured snippet
- [ ] **Wearable companion** — if HUSK ever targets Wear OS, the mic capture could run on the watch (closer to mouth, always accessible)

**Feasibility:** 🚀 Stretch / 🌕 Moonshot — these require significant additional architecture

---

## 9. Risks & Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| **Battery drain is unacceptable** | High | VAD gating, battery modes, user-configurable quality. Test extensively on Z Fold 7. |
| **ASR quality too low for useful transcripts** | Medium | Start with Whisper Tiny, offer Base as upgrade. Evaluate Moonshine models. Accept that ambient audio quality is inherently noisy. |
| **Legal liability from recording** | High | Strong first-run consent dialog, legal disclaimer, easy data deletion. Cannot solve this technically — it's a user responsibility issue. |
| **Android killing the foreground service** | Medium | Battery optimization exclusion, proper foreground service type declaration, service restart on boot. Test across OEMs (Samsung is aggressive with background killing). |
| **Storage bloat over months** | Low | Text-only is tiny (~11 MB/month). Audio retention is opt-in with auto-purge. Offer manual data management. |
| **Mic access conflicts with other apps** | Medium | Android handles mic sharing (only one app gets priority). Ambient Scribe should gracefully pause when another app takes the mic and resume when released. |
| **Chunk boundary artifacts** | Low | Overlapping windows or post-processing merge pass. Acceptable for MVP to have occasional mid-sentence breaks. |

---

## 10. Open Questions

These need answers before or during development:

1. **Model availability** — Is Whisper Tiny already available as a LiteRT model in the `litert-community` Hugging Face org, or does it need conversion? What about Moonshine?
2. **Feature naming** — "Ambient Scribe" is descriptive but long. Shorter candidates: "Echo", "Murmur", "Trace", "Undercurrent", "Chronicle". What fits the HUSK vibe?
3. **Default chunk size** — 30 seconds is a reasonable default, but should this be user-configurable in Phase 1 or deferred?
4. **Audio format for retention** — `.opus` (small, good quality) vs `.wav` (raw, large, trivial to implement). Opus requires an encoder dependency.
5. **Samsung-specific quirks** — One UI is notoriously aggressive with background service killing. Need to test and potentially add Samsung-specific workarounds (battery optimization exclusion prompts, etc.).
6. **Multi-language support** — Whisper supports 99 languages. Should the UI expose a language selector, or default to English-only for MVP?
7. **Accessibility** — How does this feature interact with TalkBack and other accessibility services?

---

## 11. Competitive Landscape

For reference — similar products exist, but none are fully on-device and open-source:

| Product | On-Device? | Open Source? | Notes |
|---|---|---|---|
| **Limitless Pendant** | No (cloud) | No | Dedicated hardware wearable, cloud transcription |
| **Otter.ai** | No (cloud) | No | Meeting-focused, requires internet |
| **Rewind.ai** | Partial (Mac only) | No | Desktop screen + audio capture, some local processing |
| **Google Recorder** | Yes | No | On-device transcription, but manual record-only, not always-on |
| **HUSK Ambient Scribe** | **Yes** | **Yes** | Always-on, fully offline, open-source, Android |

HUSK's differentiator is clear: **fully on-device, fully open-source, always-on, and integrated with an AI playground ecosystem.**

---

## 12. Implementation Notes

### 12.1 Key Files to Modify/Create

Based on HUSK's existing architecture (referencing `Function_Calling_Guide.md` patterns):

| File/Package | Purpose |
|---|---|
| `customtasks/ambientscribe/` | New package for all Ambient Scribe code |
| `AmbientScribeTask.kt` | Task definition for the hub tile |
| `AmbientScribeViewModel.kt` | ViewModel managing service state, calendar data |
| `AmbientScribeService.kt` | Foreground service — mic capture, VAD, ASR pipeline |
| `AmbientScribeDatabase.kt` | Room database definition |
| `TranscriptSegmentDao.kt` | DAO for transcript CRUD operations |
| `CalendarScreen.kt` | Compose UI — calendar grid |
| `DayDetailScreen.kt` | Compose UI — day timeline |
| `SearchScreen.kt` | Compose UI — full-text search |
| `AmbientScribeSettings.kt` | Compose UI — settings screen |
| `SilenceDetector.kt` | VAD implementation (energy-based for MVP, Silero later) |
| `TranscriptionEngine.kt` | LiteRT model loading and inference wrapper |

### 12.2 Dependencies to Add

```kotlin
// Room (local database)
implementation("androidx.room:room-runtime:2.6.x")
implementation("androidx.room:room-ktx:2.6.x")
kapt("androidx.room:room-compiler:2.6.x")

// FTS5 support (Phase 2)
// Built into SQLite on Android, no extra dependency needed

// Opus encoding (Phase 2, audio retention)
// TBD — evaluate available Android Opus libraries
```

### 12.3 Manifest Additions

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service
    android:name=".customtasks.ambientscribe.AmbientScribeService"
    android:foregroundServiceType="microphone"
    android:exported="false" />

<receiver
    android:name=".customtasks.ambientscribe.BootReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

---

## 13. Success Criteria

How we know this feature is working:

| Metric | Target |
|---|---|
| Service runs for 8+ hours without crash | Pass |
| Battery drain < 10% per hour of active speech | Pass |
| Transcription latency < 5 seconds per 30s chunk | Pass |
| Search returns results in < 500ms over 30 days of data | Pass |
| Storage usage < 15 MB/month (text only) | Pass |
| Zero data leaves the device | Pass |

---

## 14. Brainstorm Appendix

### Ideas Considered But Deferred

These came up during brainstorming and are interesting but out of scope for now:

- **Dashcam-style rolling buffer** — only permanently save when user "pins" a moment, otherwise audio cycles out. Interesting UX but adds complexity.
- **Training signal** — use transcript corpus to improve AI Chat's understanding of user vocabulary. Cool idea, but requires fine-tuning pipeline that doesn't exist yet.
- **Context injection into AI Chat** — auto-feed recent transcript as context when user opens a chat session. Powerful but needs careful UX to avoid overwhelming the model's context window.
- **Structured extraction** — LLM pass to extract meetings, to-dos, decisions, promises. Deferred to Phase 3 as part of daily summary.
- **Unified Day Log** — combining Ambient Scribe with Ask Image and Mobile Actions into one timeline. Architecturally interesting but requires cross-feature data layer.

### Naming Candidates

| Name | Vibe |
|---|---|
| **Ambient Scribe** | Descriptive, clear, a bit long |
| **Echo** | Short, evocative, "echo of your day" |
| **Murmur** | Subtle, ambient, fits the passive nature |
| **Trace** | Minimal, implies leaving a mark |
| **Chronicle** | Journal-like, historical |
| **Undercurrent** | Running beneath everything, always on |
| **Loom** | Weaving together threads of your day |
| **Whisper Log** | Plays on the Whisper model name |

---

*This document is a living plan. Update as decisions are made and implementation progresses.*

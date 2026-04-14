# Audio Scribe Upgrade — Wide Format Support, Whisper Transcription, Speaker Diarization

**Date:** 2026-04-14
**Status:** Design approved

## Problem

The Audio Scribe feature currently only accepts WAV files, uses the Gemma LLM for transcription (mediocre quality), has a 30-second duration limit, and has no speaker identification. Users want WhisperFlow-quality transcription that handles any audio or video format, identifies speakers by voice, and lets them build up a library of recognized voices over time.

## Requirements

1. Accept a wide range of audio formats: M4A/AAC, MP3, OGG/Vorbis, FLAC, WAV, AMR, OPUS
2. Accept video files and extract the audio track: MP4, MKV, 3GP, WebM, MOV
3. M4A is a must-have format
4. No duration limit on imported files
5. High-quality on-device transcription via whisper.cpp (no cloud dependency)
6. User-selectable Whisper model size: Tiny (~75MB), Base (~150MB), Small (~500MB)
7. Speaker diarization: identify different speakers within a recording
8. Persistent speaker profiles: user manually labels unknown speakers, app remembers their voice for future transcriptions
9. Speaker profile embeddings improve over time via running average
10. Progress indicator for video audio extraction
11. Structured transcript output with speaker labels and timestamps
12. Fully on-device — no network required for transcription or diarization
13. Audio Scribe only — Notes feature stays text-only

## Architecture

### 1. Audio Format Conversion Pipeline

New class `AudioDecoder` in `common/` handles all format conversion using Android's native `MediaExtractor` + `MediaCodec` APIs. No third-party library needed.

**Flow:**
```
User picks file (any audio or video format via Content URI)
  → MediaExtractor reads container, selects first audio track
  → MediaCodec decodes to raw PCM (16-bit, original sample rate)
  → Resample to 16kHz mono (Whisper's required input format)
  → Output: FloatArray of normalized samples [-1.0, 1.0]
```

**Details:**
- Accepts any Content URI — works with Android scoped storage
- For video files: `MediaExtractor.selectTrack()` picks the first audio track, video tracks are ignored
- Progress callback for large files (video extraction shows a progress indicator in the UI)
- The existing `convertWavToMonoWithMaxSeconds()` in Utils.kt stays for backward compatibility with raw mic recordings, but file imports route through `AudioDecoder`
- No duration cap — process the entire file

**Supported formats (all native Android `MediaExtractor` support):**
- Audio: M4A/AAC, MP3, OGG/Vorbis, FLAC, WAV, AMR, OPUS
- Video (audio extraction): MP4, MKV, 3GP, WebM, MOV

### 2. Whisper.cpp Transcription Engine

**Model setup:**
- whisper.cpp compiled as a native `.so` library via CMake in the Android build
- JNI bridge class `WhisperJni` exposes: `initModel(modelPath)`, `transcribe(samples, params)`, `freeModel()`
- whisper.cpp source vendored under `app/src/main/cpp/whisper/` or added as a git submodule
- NDK builds for `arm64-v8a` (primary target — Galaxy Z Fold 7)

**Model options (user-selectable):**

| Model | File | Size | Speed | Quality |
|-------|------|------|-------|---------|
| Tiny | ggml-tiny.bin | ~75MB | Fastest | Good |
| Base | ggml-base.bin | ~150MB | Fast | Better |
| Small | ggml-small.bin | ~500MB | Moderate | Best |

- Models downloaded through existing `DownloadRepository` + WorkManager infrastructure
- Each model is an independent download — user picks which to install
- At least one Whisper model must be downloaded to use Audio Scribe
- Selection persisted to Proto DataStore (`whisper_selected_model` field)
- Models stored at standard model path: `{externalFilesDir}/whisper-{size}/{version}/`

**Transcription flow:**
```
FloatArray (16kHz mono PCM from AudioDecoder)
  → WhisperJni.transcribe(samples, params)
  → Returns: List<WhisperSegment>(text, startMs, endMs)
  → Each segment is ~5-10 seconds of speech with timestamps
```

**Integration:**
- New `WhisperModelHelper` class (separate interface from `LlmModelHelper` — STT not chat)
- Runs on background coroutine dispatcher (CPU-intensive)
- Streams partial results back to UI as segments complete
- New `RuntimeType.WHISPER` added to the runtime type enum
- Model entries added to allowlist JSON

### 3. Speaker Diarization & Voice Profiles

**Speaker embedding model:**
- ECAPA-TDNN model (~20MB) converted to TFLite format
- Loaded via `SpeakerEmbeddingManager` singleton (same pattern as `EmbeddingModelManager` for Gecko)
- Input: short audio segment (~2-5 seconds of speech)
- Output: 192-dimensional embedding vector (FloatArray)
- Always downloaded regardless of Whisper model choice (small enough to bundle)

**Diarization pipeline:**
```
Whisper segments (with timestamps)
  → For each segment, extract the audio slice using timestamps from the full PCM buffer
  → SpeakerEmbeddingManager.embed(audioSlice) → FloatArray(192)
  → Compare embedding against saved speaker profiles (cosine similarity)
  → If similarity > threshold (~0.75): assign known speaker name
  → If no match: label as "Unknown Speaker N"
```

**Speaker profile storage (Room DB):**
- New `SpeakerProfile` entity: `id` (UUID), `name`, `embedding` (BLOB — serialized FloatArray), `createdMs`, `sampleCount` (how many samples averaged into this profile)
- New `SpeakerProfileDao`: `getAll()`, `insert()`, `updateEmbedding()`, `delete()`, `getByName()`
- Stored in a new `husk_speakers.db` Room database
- Embeddings improve over time: each time a named speaker is confirmed, their profile embedding is updated as a running average weighted by sample count

**Manual labeling flow:**
1. After transcription, unknown speakers appear as "Unknown Speaker 1", "Unknown Speaker 2"
2. User taps an unknown speaker label in the transcript
3. A bottom sheet appears with:
   - A playback button to hear a sample of that speaker's voice
   - A text field to enter a new name
   - A list of existing speaker profiles to merge with
4. User types a name or picks an existing profile
5. Embedding is saved (new profile) or merged (running average update)
6. All segments from that speaker in the current transcript are relabeled immediately
7. Future transcriptions auto-recognize that voice

### 4. Transcript Output & UI

**Transcript data model:**
- `TranscriptSegment` data class: `speakerName`, `text`, `startMs`, `endMs`, `speakerId` (nullable — links to `SpeakerProfile`), `speakerEmbedding` (FloatArray — for unknown speakers pending labeling)
- Final transcript: `List<TranscriptSegment>` serialized as JSON and stored in the chat message's content field (the structured JSON replaces plain text for transcription messages)

**Transcript display:**
- After transcription completes, the result appears as a structured message bubble in the Audio Scribe chat
- Each segment shows: speaker name (color-coded per speaker), timestamp, and text
- Unknown speakers have a tappable label → opens the speaker naming bottom sheet
- "Full text" toggle collapses speaker labels and shows continuous text (for copy/paste)

**Progress phases during transcription:**
1. "Extracting audio..." — for video files, with progress bar
2. "Transcribing..." — segments appear incrementally as Whisper processes them
3. "Identifying speakers..." — quick pass after transcription (embeddings are fast)

**File picker changes:**
- `MessageInputText.kt` file picker updated from `audio/wav`, `audio/x-wav` to accept `audio/*` and `video/*`
- After picking, file routes through: `AudioDecoder` → Whisper → Diarization
- Existing recording flow (mic button → 16kHz PCM) also routes through Whisper instead of LLM

**Model selector:**
- Dropdown or segmented control in Audio Scribe screen for Whisper model selection (Tiny/Base/Small)
- Similar to the E2B/E4B toggle in Notes
- Only shows downloaded models as selectable options
- Persisted to DataStore

### 5. Model Management & Download

**Models to download:**
- Whisper Tiny (~75MB) — optional
- Whisper Base (~150MB) — optional
- Whisper Small (~500MB) — optional
- ECAPA-TDNN (~20MB) — required, always downloaded

**Download UX:**
- When user first opens Audio Scribe and no Whisper model is downloaded: show a setup card with "Download transcription models to get started", sizes, and download buttons per model
- Uses existing `DownloadRepository` + WorkManager — same progress UI as Gemma downloads
- Audio Scribe is disabled until at least one Whisper model + ECAPA-TDNN are ready
- Additional models can be downloaded later from a model management area
- Already-downloaded models show a checkmark

**Model lifecycle:**
- `WhisperModelHelper` initializes whisper.cpp engine on first use, keeps it warm while on Audio Scribe screen
- `SpeakerEmbeddingManager` loads ECAPA-TDNN on first diarization request, stays loaded
- Both cleaned up when leaving Audio Scribe (same lifecycle as `LlmChatModelHelper`)

## Files to Create

| File | Purpose |
|------|---------|
| `common/AudioDecoder.kt` | MediaExtractor + MediaCodec pipeline for any format → PCM |
| `app/src/main/cpp/whisper/` | whisper.cpp source (vendored or submodule) |
| `app/src/main/cpp/CMakeLists.txt` | Native build config for whisper.cpp |
| `runtime/WhisperJni.kt` | JNI bridge to whisper.cpp native library |
| `runtime/WhisperModelHelper.kt` | Whisper model lifecycle and transcription API |
| `data/speaker/SpeakerProfile.kt` | Room entity for voice profiles |
| `data/speaker/SpeakerProfileDao.kt` | DAO for speaker profile CRUD |
| `data/speaker/SpeakerDatabase.kt` | Room database for speaker profiles |
| `data/speaker/SpeakerEmbeddingManager.kt` | ECAPA-TDNN model manager singleton |
| `data/speaker/SpeakerDiarizationEngine.kt` | Diarization pipeline: segments → speaker assignment |
| `ui/audioscribe/TranscriptSegment.kt` | Data class for transcript segments |
| `ui/audioscribe/TranscriptView.kt` | Composable for structured transcript display |
| `ui/audioscribe/SpeakerLabelSheet.kt` | Bottom sheet for naming unknown speakers |
| `ui/audioscribe/WhisperModelSelector.kt` | Model selection UI component |
| `ui/audioscribe/AudioScribeSetupCard.kt` | First-run model download card |

## Files to Modify

| File | Change |
|------|--------|
| `common/Utils.kt` | Keep existing WAV conversion, no changes needed |
| `ui/common/chat/MessageInputText.kt` | Update file picker MIME types to `audio/*` + `video/*`, route through new pipeline |
| `data/Consts.kt` | Remove `MAX_AUDIO_CLIP_DURATION_SEC` usage for file imports |
| `data/Model.kt` | Add `RuntimeType.WHISPER` |
| `runtime/ModelHelperExt.kt` | Add routing for `RuntimeType.WHISPER` |
| `model_allowlists/*.json` | Add Whisper model entries (tiny, base, small) + ECAPA-TDNN |
| `di/AppModule.kt` | Add providers for SpeakerDatabase, SpeakerProfileDao, SpeakerEmbeddingManager |
| `src/main/proto/settings.proto` | Add `whisper_selected_model` field |
| `data/DataStoreRepository.kt` | Add read/save for whisper model selection |
| `app/build.gradle.kts` | Add CMake/NDK config for whisper.cpp native build |
| `AndroidManifest.xml` | No changes needed (existing permissions sufficient) |
| `ui/llmchat/LlmChatTaskModule.kt` | Update LLM_ASK_AUDIO task to use Whisper pipeline instead of LLM inference |

## Verification

1. Pick an M4A file → transcription produces accurate text with timestamps
2. Pick an MP4 video → progress shows "Extracting audio...", then transcription works
3. Pick MP3, OGG, FLAC, WAV → all produce correct transcriptions
4. Record via mic → routes through Whisper, produces better quality than old LLM approach
5. Multi-speaker audio → segments labeled "Unknown Speaker 1", "Unknown Speaker 2"
6. Tap unknown speaker → bottom sheet with playback + name field
7. Name a speaker → all their segments relabel, profile saved
8. New recording with same speaker → auto-recognized by name
9. Switch Whisper model (Tiny → Small) → transcription quality improves, speed decreases
10. Download/delete models independently
11. No Whisper model downloaded → setup card shown, Audio Scribe disabled
12. Large video file → progress indicator shows extraction progress
13. Long recording (10+ minutes) → completes without crash or OOM

# HUSK

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/riley1802/HUSK)](https://github.com/riley1802/HUSK/releases)

**A monochrome on-device generative AI playground for Android.**

HUSK is a fork of [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) rebranded with a dark monochrome theme and an expanded feature set. It runs open-source models directly on your device — fully offline, private, and without cloud dependencies — using Google AI Edge and LiteRT under the hood.

## Core Features

**AI Chat with Thinking Mode** — Engage in multi-turn conversations with supported models including the Gemma family. Toggle Thinking Mode to expose the model's step-by-step reasoning process.

**Agent Skills** — Augment your LLM with tools like Wikipedia for fact-grounding, interactive maps, and visual summary cards. Load modular skills from a URL or browse community contributions.

**Ask Image** — Use multimodal inference to identify objects, solve visual problems, or get detailed descriptions using your camera or photo gallery.

**Audio Scribe** — Full-featured on-device audio transcription powered by whisper.cpp. Supports universal audio formats (M4A/AAC, MP3, OGG/Vorbis, FLAC, WAV, AMR, OPUS) and video audio extraction (MP4, MKV, 3GP, WebM, MOV). Features user-selectable Whisper model size (Tiny ~75MB, Base ~150MB, Small ~500MB), no duration limit, adaptive ETA display, and transcription history. Speaker diarization uses ECAPA-TDNN embeddings with persistent voice profiles that improve through manual labeling. Recordings under 10 minutes are auto-summarized via Gemma E4B.

**Notes and Brainstorm** — An AI-driven brainstorming companion on the home screen. Notes persist in a Room database with auto-tagging and full-text search (FTS4).

**Knowledge Base (RAG)** — On-device document retrieval using the Google AI Edge RAG SDK. Documents are indexed with Gecko embeddings (768-dimensional) stored in a SqliteVectorStore, grounding model responses in your own content.

**Prompt Lab** — A focused workspace for testing prompts and single-turn use cases with granular control over parameters like temperature and top-k.

**Mobile Actions** — Offline device controls and automated tasks powered by a fine-tuned FunctionGemma 270m.

**Tiny Garden** — An experimental mini-game using natural language to plant and harvest a virtual garden, also powered by FunctionGemma 270m.

**Model Management and Benchmark** — Download models from the built-in list or load custom models. Run benchmark tests to compare performance on your specific hardware.

## On-Device Privacy

All inference runs on device hardware. No internet connection is required for model inference, and your prompts, audio, images, and documents never leave the device.

## Thermal Monitor

A live thermal meter in the app's top bar shows device skin temperature with a color-coded severity indicator (green / yellow / orange / red). Tapping it opens a detailed popup with all sensor readings, a thermal headroom bar, battery temperature, and recommended temperature ranges for AI inference. Uses Android `PowerManager.getThermalHeadroom()` and `BatteryManager` APIs.

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for detailed system architecture.

## Technology Highlights

- **Google AI Edge** — Core APIs and tools for on-device ML
- **LiteRT** — Lightweight runtime for optimized model execution
- **whisper.cpp** — Native C++ speech recognition via NDK/JNI
- **ECAPA-TDNN** — Speaker embedding model for diarization
- **Google AI Edge RAG SDK** — Retrieval-augmented generation pipeline
- **Room Database** — Persistent local storage for notes and transcripts
- **Proto DataStore** — Typed settings and user preferences
- **Hugging Face** — Model discovery and download

## Get Started

1. **OS requirement**: Android 12 or higher.
2. **Build from source**: See [DEVELOPMENT.md](DEVELOPMENT.md) for local build instructions.

## Feedback

- **Found a bug?** [Report it here.](https://github.com/riley1802/HUSK/issues/new?assignees=&labels=bug&template=bug_report.md&title=%5BBUG%5D)
- **Have an idea?** [Suggest a feature.](https://github.com/riley1802/HUSK/issues/new?assignees=&labels=enhancement&template=feature_request.md&title=%5BFEATURE%5D)

## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## Useful Links

- [Upstream: Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)
- [Hugging Face LiteRT Community](https://huggingface.co/litert-community)
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
- [Google AI Edge Documentation](https://ai.google.dev/edge)

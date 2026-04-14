## 1. Stack Overview & Architecture Context

HUSK is a fork of Google AI Edge Gallery, a Kotlin-native Android app (91% Kotlin, 8.4% HTML) that runs open-source LLMs on-device using Google AI Edge and LiteRT. The app currently supports:

- **AI Chat** with Thinking Mode (multi-turn, step-by-step reasoning visualization)
- **Agent Skills** (modular tool-augmented agentic workflows via FunctionGemma/Gemma 4)
- **Ask Image** (multimodal vision — camera/gallery → model)
- **Audio Scribe** (on-device transcription and translation)
- **Prompt Lab** (single-turn prompt testing with temperature/top-k controls)
- **Mobile Actions** (FunctionGemma 270m finetune for device controls)
- **Tiny Garden** (NL-driven minigame via FunctionGemma 270m)
- **Model Management & Benchmark** (download, manage, benchmark models)

The software stack that all new features must conform to:

| Layer | Technology |
|-------|-----------|
| Language | Kotlin (Jetpack Compose UI) |
| Runtime | LiteRT / LiteRT-LM (v0.10.1+) |
| Model Format | `.litertlm` (LiteRT-LM native format) |
| Model Source | Hugging Face (litert-community) |
| Acceleration | XNNPack (CPU), ML Drift (GPU), NPU delegates |
| Target Models | Gemma 4 E2B, Gemma 4 E4B |
| Platform | Android 12+ |
| License | Apache 2.0 |

All recommendations below work within this stack.

---


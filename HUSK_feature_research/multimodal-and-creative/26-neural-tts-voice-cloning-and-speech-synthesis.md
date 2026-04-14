## 26. Neural TTS, Voice Cloning & Speech Synthesis

### Kokoro-82M (Top Recommendation)

**Kokoro-82M** is the standout mobile TTS model: 82M parameters, ~80 MB quantized, 8 languages, 26+ voices, with emotion tags (`[whisper]`, `[angry]`, `[happy]`) via **Sherpa-ONNX** integration. A 10-second clip takes ~8 seconds on recent smartphones. Sherpa-ONNX provides the Android runtime with Kotlin bindings.

**VoxSherpa TTS** is an open-source Android app built on Sherpa-ONNX that demonstrates the complete offline TTS stack with Kokoro + Piper + VITS voice support.

### NeuTTS Air — On-Device Voice Cloning

**NeuTTS Air** (Apache 2.0, October 2025, Neuphonic) clones a voice from **3 seconds** of reference audio with 85–90% speaker similarity (95%+ with 15 seconds):
- Runs at RTF <0.5 on CPU with Q4 GGUF
- ~400–600 MB RAM usage
- Benchmarked on a **Galaxy A25 5G** (budget device)
- Zero cloud dependency

### Model Comparison

| Model | Size | Languages | Voices | RTF (Mobile) | Voice Cloning | License |
|-------|------|-----------|--------|-------------|---------------|---------|
| Piper TTS | ~15 MB/voice | 30+ | 100+ | <0.3 | ❌ | MIT |
| Kokoro-82M | ~80 MB | 8 | 26+ | ~0.8 | ❌ | Apache 2.0 |
| NeuTTS Air | ~400 MB | 6+ | Cloned | <0.5 | ✅ (3s ref) | Apache 2.0 |
| Picovoice Orca | ~25 MB | 4 | 12 | 6.5× faster | ❌ | Proprietary |
| XTTS v2 | ~1.8 GB | 17 | Cloned | GPU only | ✅ | CPML |

**Recommendation:** Piper for default TTS (smallest, fastest), Kokoro for premium quality, NeuTTS Air for voice cloning. XTTS v2 and Bark are too large for mobile.

### Complete Voice Pipeline

| Stage | Component | Size |
|-------|-----------|------|
| Wake Word | Porcupine (Picovoice) | ~1.5 MB |
| Speech-to-Text | Gemma 4 E2B/E4B (native audio) | Built-in |
| Intent Processing | Gemma 4 + Function Calling | LiteRT-LM |
| Text-to-Speech | Kokoro-82M / Piper | ~80 MB / ~15 MB |
| Voice Cloning | NeuTTS Air | ~400 MB |
| Noise Cancellation | RNNoise | ~1 MB |

### Sources
- Kokoro TTS: https://kokorottsai.com
- Kokoro on-device: https://www.nimbleedge.com/blog/how-to-run-kokoro-tts-model-on-device/
- VoxSherpa TTS (Android): https://github.com/k2-fsa/sherpa-onnx/discussions/3383
- NeuTTS Air: https://medium.com/data-science-in-your-pocket/neutts-air-revolutionizing-on-device-text-to-speech-with-instant-voice-cloning-df3aadebc5cc
- NeuTTS GitHub: https://github.com/neuphonic/neutts
- Open-Source TTS Comparison 2026: https://www.bentoml.com/blog/exploring-the-world-of-open-source-text-to-speech-models
- Voice Cloning Tools: https://www.resemble.ai/best-open-source-ai-voice-cloning-tools/

---


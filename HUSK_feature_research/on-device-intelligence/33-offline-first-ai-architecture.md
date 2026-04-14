## 33. Offline-First AI Architecture

### Three-Tier Progressive Enhancement

| Tier | Connectivity | Capabilities |
|------|-------------|-------------|
| L1 — Offline | None | Gemma 4 E2B via LiteRT-LM, ML Kit Translation (pre-downloaded), local RAG via ObjectBox + EmbeddingGemma, cached responses |
| L2 — Limited | Intermittent | On-device for latency-sensitive tasks, queued cloud requests for complex work |
| L3 — Full | WiFi/5G | Gemini API for complex reasoning, Google Cloud Translation for highest quality, cloud RAG |

### Storage Budget

Total for a comprehensive offline assistant: **~5–8 GB**
- Gemma 4 E2B: ~2.58 GB
- Translation pairs (5 languages): ~150 MB
- EmbeddingGemma: ~200 MB
- Knowledge base + vectors: ~500 MB–2 GB
- TTS model (Kokoro): ~80 MB

### Emergency Mode

Pre-cached capabilities for zero-connectivity scenarios:
- First-aid procedures
- Offline map tiles (via OSM)
- Multilingual SOS templates
- Basic device diagnostics
- Last-known weather/calendar data

### Reference: ToolNeuron

**ToolNeuron** (open-source Kotlin app) demonstrates the complete offline-first stack:
- LLM chat (GGUF/llama.cpp)
- RAG knowledge packs
- TTS (Supertonic, 10 voices/5 languages)
- Tool calling
- AES-256-GCM encrypted storage
- Vision models (VLM)
- Image generation (Stable Diffusion)

### Sources
- ToolNeuron: https://github.com/Siddhesh2377/ToolNeuron

---


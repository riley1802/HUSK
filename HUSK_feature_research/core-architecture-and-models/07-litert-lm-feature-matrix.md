## 7. LiteRT-LM Feature Matrix

LiteRT-LM v0.10.1 is the current release. It's the production inference framework powering HUSK's model execution.

### Current Features

| Feature | Status | Notes |
|---------|--------|-------|
| Cross-platform (Android, iOS, Web, Desktop, IoT) | ✅ | Kotlin API for Android/JVM |
| GPU acceleration (ML Drift) | ✅ | Significant speedup vs CPU |
| NPU acceleration | ✅ | Qualcomm & MediaTek support |
| CPU acceleration (XNNPack) | ✅ | 4-thread default |
| Multi-modality (vision + audio) | ✅ | Image/audio input support |
| Function calling / Tool use | ✅ | `@Tool`, `@ToolParam` annotations |
| Automatic tool calling | ✅ | Recursive up to 5 calls |
| OpenAPI tool definitions | ✅ | JSON schema tool descriptions |
| Constrained decoding | ✅ | JSON/function-call output enforcement |
| Speculative decoding | ✅ | New in v0.10.x |
| KV cache (LiteRT-based) | ✅ | New implementation |
| Context merging | ✅ | Conversation history handling |
| Memory-mapped embeddings | ✅ | Per-layer, reduces working memory |
| 2-bit / 4-bit quantization | ✅ | Weight quantization |
| Dynamic context length | ✅ | Up to 128K |
| Streaming responses | ✅ | Channel-based in Kotlin API |
| Session cloning | ✅ | Multi-session support |
| Benchmark API | ✅ | TTFT, decode speed, memory |

### Upcoming / Trackable Features

- TurboQuant KV-cache compression integration
- Expanded NPU support for newer chipsets
- Improved speculative decoding configurations
- WebGPU acceleration improvements

### Sources
- LiteRT-LM GitHub: https://github.com/google-ai-edge/LiteRT-LM
- LiteRT-LM Overview Docs: https://ai.google.dev/edge/litert-lm/overview
- LiteRT-LM Android Guide: https://ai.google.dev/edge/litert-lm/android
- LiteRT-LM Kotlin API (DeepWiki): https://deepwiki.com/google-ai-edge/LiteRT-LM/4.6-kotlin-and-android-api
- LiteRT-LM Releases: https://github.com/google-ai-edge/LiteRT-LM/releases
- LiteRT-LM Kotlin Getting Started: https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- Google AI Edge Portal: https://ai.google.dev/edge

---


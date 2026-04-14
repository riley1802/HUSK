## 6. Gemma 4 E2B/E4B Deep Dive

### Architecture

Gemma 4 was released April 3, 2026 under **Apache 2.0** (major licensing shift from prior versions). The E2B and E4B use a unique architecture:

**PLE (Per-Layer Embedding)** instead of standard MoE:
- Adds a parallel lower-dimensional conditioning pathway
- Each decoder layer receives token-specific information only when relevant
- Enables E2B to run under 1.5GB RAM on some devices via LiteRT-LM

**Hybrid Attention:**
- Alternating local sliding-window (512 tokens for edge models) and global full-context attention
- Final layer is always global
- Shared KV Cache across global layers (eliminates redundant KV projections)

### Capability Matrix

| Feature | E2B | E4B |
|---------|-----|-----|
| Parameters (active) | ~2B | ~4B |
| Context Window | 128K tokens | 128K tokens |
| Text Input | ✅ | ✅ |
| Image Input | ✅ | ✅ |
| Audio Input | ✅ | ✅ |
| Function Calling | ✅ (simple) | ✅ (complex/multi-tool) |
| System Prompt | ✅ (native) | ✅ (native) |
| Thinking Mode | ✅ | ✅ |
| Languages | 140+ | 140+ |
| LiteRT-LM Size | ~2.58 GB | ~4.5 GB |
| Min RAM (INT4) | ~1.5 GB | ~3 GB |
| Quantization | 2-bit, 4-bit | 2-bit, 4-bit |

### Performance on Z Fold 7 (Snapdragon 8 Elite)

The Z Fold 7 with Snapdragon 8 Elite is a flagship-tier device with 12 GB RAM. Expected performance:
- **E2B INT4:** 25-40 tokens/second decode, <2 second TTFT
- **E4B INT4:** 15-25 tokens/second decode, <4 second TTFT
- Both models comfortably fit with room for RAG vector stores and MCP connections

### What's New vs Gemma 3n

- **Native system prompt support** — First Gemma model with system role
- **Enhanced function calling** — More reliable multi-tool selection
- **Improved coding capabilities** — Better code generation benchmarks
- **Apache 2.0 license** — Full commercial freedom
- **128K context** — Up from 8K/32K in prior edge models
- **Better vision** — Improved OCR, chart understanding, visual reasoning
- **Audio understanding** — Native ASR and speech-to-translated-text

### Sources
- Google Blog (Gemma 4): https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/
- Google DeepMind (Gemma 4): https://deepmind.google/models/gemma/gemma-4/
- Google Developers Blog (Agentic Skills): https://developers.googleblog.com/bring-state-of-the-art-agentic-skills-to-the-edge-with-gemma-4/
- DEV Community Complete Guide: https://dev.to/linnn_charm_2e397112f3b51/gemma-4-complete-guide-architecture-models-and-deployment-in-2026-3m5b
- MindStudio E2B vs E4B: https://www.mindstudio.ai/blog/gemma-4-e2b-e4b-edge-models-phone-local
- MindStudio Edge Deployment: https://www.mindstudio.ai/blog/gemma-4-edge-deployment-e2b-e4b-models
- MindStudio Audio/Vision: https://www.mindstudio.ai/blog/gemma-4-e2b-vs-e4b-edge-models-audio-vision-phone
- Intel Optimization: https://www.edge-ai-vision.com/2026/04/gemma-4-models-optimized-for-intel-hardware-enabling-instant-deployment-from-day-zero/
- HF E2B Model Card: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- HF E4B Model Card: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
- Medium On-Device Guide: https://medium.com/google-cloud/on-device-ai-with-the-google-ai-edge-gallery-and-gemma-4-1c31a220d3ee

---


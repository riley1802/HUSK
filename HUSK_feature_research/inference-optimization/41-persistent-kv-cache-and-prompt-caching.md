## 41. Persistent KV Cache & Prompt Caching

### Agent Memory Below the Prompt (February 2026)

Serializes KV caches to disk in **Q4 safetensors format**:
- Reduces TTFT by **up to 136× on Gemma 3 12B** at 32K context
- Only −0.7% perplexity impact
- Reload latency: ~500 ms

### KVSwap (arXiv 2511.11907)

First disk-based KV cache offloading framework explicitly designed for **mobile/embedded storage bandwidth constraints**. Uses async I/O with UFS 4.0 sequential read speeds (~4 GB/s on Z Fold 7).

### LiteRT-LM KV Cache Status

LiteRT-LM provides built-in KV-cache management and session cloning but **lacks a public API for disk serialization**. Recommended workarounds:
1. Keep the `Engine` alive in a Foreground Service across Activity lifecycle
2. Use safetensors approach for cross-launch persistence
3. Implement system prompt KV cache warm-up on first launch

### KV Cache Memory Estimates (Gemma 4)

| Model | Context Length | KV Cache Size (FP16) | KV Cache Size (Q4) |
|-------|---------------|---------------------|---------------------|
| E2B | 4K tokens | ~250 MB | ~65 MB |
| E2B | 8K tokens | ~500 MB | ~125 MB |
| E2B | 16K tokens | ~1 GB | ~250 MB |
| E4B | 4K tokens | ~500 MB | ~125 MB |
| E4B | 8K tokens | ~1 GB | ~250 MB |

### Sources
- Persistent KV Cache: https://arxiv.org/html/2603.04428v1
- KVSwap: https://arxiv.org/html/2511.11907v1

---


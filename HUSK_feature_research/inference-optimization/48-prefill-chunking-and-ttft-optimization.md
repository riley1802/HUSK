## 48. Prefill Chunking & TTFT Optimization

### The Problem

Full-prompt prefill blocks decode from starting. For a 4K-token prompt, the user waits for the entire prefill before seeing any output.

### Prefill Chunking

Process the prompt in ~512-token chunks:
1. First chunk prefills → decode begins immediately after
2. Remaining chunks prefill in background while early tokens stream
3. Saturates mobile GPU throughput while reducing perceived latency

llm.npu's implementation shares 120 of 144 subgraphs in Qwen1.5-1.8B, reducing memory by up to **7.2 GB** for 1024-token prompts.

### Context Extension

Gemma 4 natively supports **128K tokens** via hybrid local-sliding-window + global attention with proportional RoPE. **YaRN** extends context 16–32× with only 400–600 fine-tuning steps.

### Practical Mobile Limits

| Model | RAM Available | Max Practical Context | KV Cache (Q4) |
|-------|-------------|----------------------|---------------|
| E2B | 12 GB | **8–16K tokens** | 125–250 MB |
| E4B | 12 GB | **4–8K tokens** | 125–250 MB |
| E2B | 16 GB | **16–32K tokens** | 250–500 MB |

Approximately **0.5–1 MB per 1K tokens** for a 2B GQA model at Q4.

### Token Streaming Optimization

LiteRT-LM native Kotlin Flow API:
```kotlin
conversation.sendMessageAsync(prompt).collect { message ->
    // StateFlow -> Jetpack Compose recomposition
    uiState.update { it.copy(response = message.text) }
}
```

| Decode Speed | UX Quality |
|-------------|------------|
| 20+ tok/s | Smooth streaming |
| 10–20 tok/s | Slight pauses, acceptable |
| 5–10 tok/s | Buffer word-level chunks before display |
| <5 tok/s | Show loading indicator between chunks |

---


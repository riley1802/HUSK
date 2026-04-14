## 21. Hidden Gems for On-Device AI

These are the less-obvious, high-value features that most people overlook:

### 21.1 Memory-Mapped Per-Layer Embeddings

LiteRT-LM uses memory-mapped embeddings that aren't loaded into working memory until needed. This means HUSK can run E2B with <1.5GB working memory even though the model file is 2.58GB. **Hidden gem:** You can monitor which layers are actively mapped and potentially evict unused ones for even lower memory usage.

### 21.2 Session Cloning for A/B Testing

LiteRT-LM supports session cloning — create a conversation, clone it, and send different messages to each clone. This enables user-facing "what if" exploration without re-processing the entire conversation history.

### 21.3 Automatic Tool Call Recursion

LiteRT-LM's automatic tool calling supports recursive loops (up to 5 by default). The model calls a tool, gets the result, decides if it needs another tool, and chains them. **Hidden gem:** Increase `RECURRING_TOOL_CALL_LIMIT` for complex multi-step workflows.

### 21.4 Backend Switching (CPU ↔ GPU ↔ NPU)

```kotlin
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.GPU(), // or Backend.CPU(), Backend.NPU(...)
)
```

**Hidden gem:** You can switch backends mid-session for different workloads — use GPU for fast generation, switch to NPU for sustained inference with lower power consumption.

### 21.5 Shared KV Cache in Hybrid Attention

Gemma 4's hybrid attention shares KV states across global attention layers. This means long-context conversations use significantly less memory than you'd expect from the context window size. **Hidden gem for HUSK:** Display "effective context efficiency" as a benchmark metric.

### 21.6 PLE Architecture for Mobile

Gemma 4's Per-Layer Embedding is purpose-built for mobile. Unlike MoE which loads expert weights dynamically, PLE generates tiny per-layer conditioning vectors on-the-fly. **Hidden gem:** This makes Gemma 4 edge models significantly more efficient at batch prefill than comparable MoE models, meaning faster time-to-first-token.

### 21.7 Android AICore Integration

On supported Android devices, Gemma 4 is available through **Android AICore as Gemini Nano**. This is a system-level, hardware-optimized model that doesn't count against your app's memory. **Hidden gem for HUSK:** Detect if AICore is available and offer it as an additional model option — users get Gemma 4 performance with zero storage cost.

### 21.8 WebGPU Browser Execution

LiteRT-LM supports web-based inference via WebGPU. **Hidden gem:** HUSK could expose a local web interface accessible from other devices on the same WiFi network, turning the Z Fold 7 into a personal AI server.

### 21.9 Speculative Decoding with Asymmetric Models

Use E2B as a draft model for E4B inference — they share the same tokenizer, vocabulary, and chat template. **Hidden gem:** Because they're architecturally related, draft acceptance rates are higher than using unrelated models, yielding 2-3x speedup.

### 21.10 Context-Aware Quantization Switching

LiteRT-LM supports dynamic quantization backends. **Hidden gem:** For simple queries, use 2-bit quantization (faster, less memory). For complex reasoning, switch to 4-bit (better quality). Implement an auto-detection system based on query complexity.

---


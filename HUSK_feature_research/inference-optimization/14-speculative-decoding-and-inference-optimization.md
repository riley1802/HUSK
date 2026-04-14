## 14. Speculative Decoding & Inference Optimization

LiteRT-LM v0.10.x added **speculative decoding support**. This is a massive performance win for on-device inference.

### How Speculative Decoding Works

1. A small "draft" model quickly generates N candidate tokens
2. The larger "target" model verifies all N tokens in a single forward pass
3. Accepted tokens are output immediately; rejected tokens trigger re-generation
4. Net result: 2-3x speedup for the same output quality

### Implementation in HUSK

- **Dual Model Loading** — Load E2B as draft model, E4B as target model
- **Auto-Speculation** — Automatically enable speculative decoding when both models are downloaded
- **Speculation Settings** — Let users tune speculation depth (number of draft tokens)
- **Performance Comparison** — Show benchmark results with/without speculative decoding

### Other Inference Optimizations

1. **KV-Cache Reuse** — Reuse KV cache across similar prompts to reduce TTFT
2. **Prompt Caching** — Cache system prompt KV states for instant reuse
3. **Batch Prefill** — Prefill multiple conversation turns simultaneously
4. **Context Window Windowing** — Intelligently truncate old context to maintain speed
5. **Token Streaming Optimization** — Minimize UI thread blocking during token generation
6. **Background Inference** — Start generating responses while user is still typing
7. **Model Preloading** — Keep model loaded in memory between sessions for instant response

---


## 47. LiteRT-LM vs GGUF Format Comparison

### Performance Comparison (Samsung S26 Ultra, Snapdragon 8 Elite Gen 5)

| Metric | LiteRT-LM (Gemma 4 E2B) | llama.cpp GGUF (3B Q4_K_M) |
|--------|-------------------------|---------------------------|
| Prefill (GPU) | **3,808 tok/s** | ~500 tok/s |
| Decode (GPU) | **52.1 tok/s** | ~11 tok/s |
| Prefill (CPU) | **557 tok/s** | ~200 tok/s |
| Decode (CPU) | **46.9 tok/s** | ~8 tok/s |
| GPU Memory | **676 MB** | ~1.5 GB |
| NPU Support | ✅ (QNN native) | ❌ (falls back to CPU) |
| Model Format | `.litertlm` | `.gguf` |
| AOT Compilation | ✅ | ❌ |

### Why LiteRT-LM Wins on Samsung

1. **Memory-mapped embeddings** — E2B's 2.58 GB model uses only 676 MB GPU memory (1.12 GB stays mmap'd from UFS 4.0)
2. **ML Drift GPU kernels** — Purpose-built for Adreno, not generic OpenCL
3. **QNN NPU path** — Direct Hexagon access, 3–10× over GPU for prefill
4. **AOT compilation** — Pre-compiled for target SoC via Google Play AI Packs

### When GGUF Still Matters

- Desktop/server inference (your EndeavourOS rig with Ollama)
- Cross-platform compatibility
- Community model ecosystem (more models available in GGUF)
- When NPU isn't available

### Sources
- LiteRT-LM Benchmarks: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- LiteRT Universal Framework: https://developers.googleblog.com/litert-the-universal-framework-for-on-device-ai/
- GGUF vs NNAPI: https://dev.to/software_mvp-factory/running-llms-on-device-in-android-gguf-models-nnapi-and-the-real-performance-tradeoffs-5bfc

---


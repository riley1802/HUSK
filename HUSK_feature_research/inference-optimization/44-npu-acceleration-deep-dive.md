## 44. NPU Acceleration Deep Dive

### Qualcomm Hexagon NPU Performance

LiteRT QNN Accelerator benchmarks show **56 of 72 canonical models running in under 5 ms** on NPU. For LLMs, the NPU provides:
- Up to **100× faster than CPU** at ~40% less power
- **Dedicated power rail** with independent DVFS
- Parallel execution to GPU and CPU
- INT4 weights with INT16 activations unlock fastest kernels

### Hexagon HMX Internals

The HMX unit converts INT4 values to FP16 using **`vlut16` vector lookup table instructions** — a single instruction replacing the conventional mask-unpack-convert sequence. This is why INT4 is dramatically faster than sub-4-bit on this hardware.

### Qualcomm vs Exynos vs Tensor

| Metric | Snapdragon 8 Elite | Exynos 2600 | Tensor G5 |
|--------|-------------------|-------------|-----------|
| NPU TOPS | 100+ | ~80 | ~45 |
| LLM Decode (E2B) | ~52 tok/s | ~40 tok/s | ~30 tok/s |
| Power Efficiency | Best | Good | Moderate |

### LiteRT-LM + QNN Setup

```kotlin
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.NPU(), // QNN Accelerator
)
```

**AOT compilation** via LiteRT's `CompiledModel` API is strongly recommended — pre-compiles for target SoC, eliminates first-run compilation that can exceed 1 minute.

### Important: NNAPI Deprecated

NNAPI is **officially deprecated starting Android 15**. The QNN Accelerator with direct Hexagon NPU access replaces it.

### Sources
- LiteRT QNN Accelerator: https://developers.googleblog.com/unlocking-peak-performance-on-qualcomm-npu-with-litert/
- Qualcomm NPU Whitepaper: https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Unlocking-on-device-generative-AI-with-an-NPU-and-heterogeneous-computing.pdf
- LiteRT NPU Docs: https://ai.google.dev/edge/litert/next/npu
- Scaling NPU Test-Time Compute: https://arxiv.org/html/2509.23324v1

---


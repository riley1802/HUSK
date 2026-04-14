## 49. Edge AI Benchmarking Standards & Metrics

### MLPerf Mobile v5.0 (February 2026)

Covers Classification, Detection, Segmentation, Super-Resolution, Language, and Stable Diffusion across Snapdragon 8 Elite Gen 5, Exynos 2600, and Tensor G5. APK available: `org.mlcommons.android.mlperfbench`.

### Standardized On-Device LLM Metrics

| Metric | Unit | What It Measures |
|--------|------|-----------------|
| Prefill Speed | tok/s | Input processing throughput |
| Decode Speed | tok/s | Output generation throughput |
| TTFT | seconds | Time to first token |
| Peak Memory | GB | Maximum RAM usage during inference |
| Model Size on Disk | GB | Storage footprint |
| Sustained Power | W | Continuous power draw |
| Thermal-Sustained Decode | tok/s | Decode speed after 10 minutes |

### Reference Benchmarks (Snapdragon 8 Elite Gen 5)

| Model | Prefill | Decode | TTFT | Memory |
|-------|---------|--------|------|--------|
| Gemma 4 E2B (GPU) | 3,808 tok/s | 52.1 tok/s | <1s | 676 MB |
| Gemma 4 E2B (CPU) | 557 tok/s | 46.9 tok/s | ~2s | 1,733 MB |
| Gemma 4 E4B (GPU) | 1,293 tok/s | 22.1 tok/s | ~2s | ~1.5 GB |
| Gemma 4 E4B (CPU) | 195 tok/s | 17.7 tok/s | ~5s | ~3 GB |
| FastVLM-0.5B (NPU) | 11,000+ tok/s | 100+ tok/s | 0.12s | <500 MB |

### Key Insight

**Thermal-sustained benchmarks are essential** — real-world performance is typically **60–70% of peak** after 10 minutes. The Z Fold 7's slim 4.2 mm profile makes sustained thermal testing critical before shipping. Measure via `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` (μA) for power.

### Sources
- MLPerf Mobile: https://www.sammobile.com/news/heres-how-exynos-2600-competes-snapdragon-8-elite-gen-5-ai-test/
- Snapdragon 8 Elite Gen 5 Product Brief: https://www.qualcomm.com/content/dam/qcomm-martech/dm-assets/documents/Snapdragon-8-Elite-Gen-5-product-brief.pdf

---



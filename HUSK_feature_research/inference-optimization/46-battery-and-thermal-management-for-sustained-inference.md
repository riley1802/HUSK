## 46. Battery & Thermal Management for Sustained Inference

### The Thermal Problem

The Snapdragon 8 Elite's GPU stability drops to **25% of peak** in passively cooled devices during sustained stress. CPU throttles to **74–77% within 15 minutes**. On a Galaxy S24 Ultra, sustained LLM inference caused the thermal governor to floor GPU frequency from 680 to **231 MHz after just 6 iterations**. The Z Fold 7's thin **4.2 mm** foldable profile likely worsens thermal dissipation.

### ADPF (Android Dynamic Performance Framework)

**Primary mitigation tool.** Key APIs:

- `PowerManager.getThermalHeadroom()` → 0.0–1.0 forecast (1.0 = throttling onset), 10-second polling
- **Performance Hint Sessions** → System adjusts CPU frequency based on actual vs. target work duration
- Arm testing showed **57% improvement** in sustained frame rates

### Power-Aware Inference Manager

```
Charging:     Full performance (E4B, GPU, max context)
Battery >50%: INT4 + NPU, 4K context cap
Battery 20-50%: E2B only, efficiency cores, inter-token delays
Battery <20%: Inference suspended, cached responses only
Critical:     Emergency mode, pre-cached content only
```

### Duty-Cycling Strategy

The decode phase dominates energy consumption at **8–10 W**. MNN-AECS research demonstrates that **CPU core selection** (efficiency cores for decode, performance cores for prefill) reduces energy with only ~5% speed loss.

Pattern: Run inference → cool for 2–3 seconds (thermal headroom check) → resume. This prevents the catastrophic thermal cliff that causes frequency collapse.

### NPU Power Advantage

The NPU runs on a **dedicated power rail** with independent DVFS, parallel to GPU and CPU. NPU inference is up to **100× faster than CPU at ~40% less power** — always prefer NPU path when possible.

### Sources
- Snapdragon 8 Elite Thermals: https://loadsyn.com/snapdragon-elite-gen-5-gaming-test-throttling-solved/
- ADPF Thermal API: https://developer.android.com/games/optimize/adpf/thermal
- ADPF Overview: https://developer.android.com/games/optimize/adpf
- ARM ADPF Guide: https://developer.arm.com/community/arm-community-blogs/b/mobile-graphics-and-gaming-blog/posts/getting-started-with-adpf
- MNN-AECS Energy Optimization: https://arxiv.org/pdf/2506.19884
- LLM Inference at the Edge: https://arxiv.org/pdf/2603.23640

---


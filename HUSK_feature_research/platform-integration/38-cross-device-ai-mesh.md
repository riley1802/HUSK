## 38. Cross-Device AI Mesh

### Research Results

ACM ICSCA 2025 demonstrated distributed inference with a master-worker architecture achieving **74% latency improvement** at 4 nodes. **EdgeShard** partitions models into shards distributed across edge devices and cloud, showing **50% latency reduction and 2× throughput** over cloud-only.

However, WiFi/BLE communication overhead makes single-device inference preferable when the model fits in memory.

### Transport: Nearby Connections API

Encrypted full-duplex peer-to-peer networking via Bluetooth/WiFi/WiFi Direct:
- Byte, file, and stream payloads
- All offline-capable
- No server infrastructure required

### Cross-device SDK (Preview)

`crossdevice:0.1.0-preview01` provides secure connections and multi-device sessions but currently supports only **two devices at a time**.

### Practical Architecture

| Device | Role | Model |
|--------|------|-------|
| Z Fold 7 (Phone) | Orchestrator | Gemma 4 E4B (full LLM) |
| Tablet | Vision worker | Parallel image/video processing |
| Galaxy Watch | Sensor hub | Activity recognition, health data |
| PC (Desktop) | Heavy compute | Larger model variants via Ollama |

### Sources
- Distributed Edge Inference: https://dl.acm.org/doi/10.1145/3731806.3731859
- Nearby Connections: https://developers.google.com/nearby/connections/overview
- Cross-device SDK: https://developer.android.com/guide/topics/connectivity/cross-device-sdk/overview

---


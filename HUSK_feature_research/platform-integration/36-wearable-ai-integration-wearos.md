## 36. Wearable AI Integration (WearOS)

### LiteRT-LM on Pixel Watch

LiteRT-LM has been deployed on **Pixel Watch** for Smart Replies using Gemini Nano, confirming WearOS viability for small models.

### Galaxy Watch 7 Capabilities

- **Exynos W1000** — 2 GB RAM, 3nm process
- Can run sub-500M parameter models locally (keyword spotting, activity recognition)
- Meaningful LLM tasks → offload to phone via Wearable Data Layer API

### Samsung Health Sensor SDK

Direct access to Galaxy Watch4+ sensors:
- Heart rate (continuous)
- SpO2
- Accelerometer/gyroscope streaming
- Data pipes directly to phone for on-device AI interpretation

### Architecture

```
Galaxy Watch (sensor data + small models)
    ↓ Wearable Data Layer API
Galaxy Z Fold 7 (Gemma 4 inference + full processing)
    ↓ Results
Watch face complications / Notifications
```

### Sources
- LiteRT-LM on Pixel Watch: https://developers.googleblog.com/on-device-genai-in-chrome-chromebook-plus-and-pixel-watch-with-litert-lm/
- Galaxy Watch 7 Features: https://www.sammobile.com/news/best-galaxy-watch-7-features-exynos-w1000-galaxy-ai/

---


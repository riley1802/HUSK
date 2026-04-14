## 37. Smart Home & IoT Integration

### Google Home APIs (Public Beta)

`play-services-home:16.0.0-beta1` exposes **750M+ connected devices** through a Kotlin DSL:

```kotlin
// Discover devices
val homeClient = Home.getClient(context)
val devices = homeClient.devices().list()

// Control a light
val light = devices.first { it.has(OnOffTrait) }
light.execute(OnOffTrait.on())
```

### Automation API

Programmatic routine creation with:
- **Starters** — Triggers (time, device state, location)
- **Conditions** — Guards (temperature > threshold, device online)
- **Actions** — Sequential or parallel execution
- Gemini integration suggests automations and enables natural-language creation

### On-Device Pipeline

1. User voice/text → Gemma 4 intent parsing
2. Constrained JSON output → valid Home API command
3. Home API execution → device control
4. No cloud required for command interpretation

### SmartThings Integration

Samsung SmartThings APIs provide parallel access to:
- Samsung ecosystem devices
- Multi-Admin Matter device sharing
- Z Fold 7 as SmartThings hub

### Sources
- Google Home APIs: https://developers.googleblog.com/en/build-the-future-of-home-with-google-home-apis/
- Home APIs Documentation: https://developers.home.google.com/apis
- Home APIs Developer Guide: https://developers.googleblog.com/en/home-apis-enabling-all-developers-to-build-for-the-home/

---


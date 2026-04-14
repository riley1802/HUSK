## 32. Multilingual & Translation Pipelines

### Gemma 4 Native Multilingual

Gemma 4 E2B/E4B is pre-trained on **140+ languages** with out-of-box support for 35+ languages. Native audio input handles speech-to-translated-text directly, eliminating the need for a separate ASR→translation cascade.

### ML Kit Translation

- **50+ language pairs** using English as a pivot
- ~30 MB per language pair (download once, use offline)
- On-device only after download
- API: `com.google.mlkit:translate:17.0.x`

### Recommended Architecture

For highest-quality offline translation at scale:
1. **Gemma 4 native ASR** → speech-to-text in source language
2. **ML Kit Translation** → translate to target language
3. **Kokoro/Piper TTS** → synthesize translated text
4. All fully on-device after initial model downloads

### ML Kit GenAI APIs (Galaxy Z Fold 7 Confirmed)

The Galaxy Z Fold 7 is explicitly confirmed for ML Kit GenAI APIs:
- **Summarization** — On-device text summarization
- **Proofreading** — Grammar and spelling correction
- **Rewriting** — Tone and style adjustment
- **Image Description** — AI-generated alt-text
- **Prompt API** — General-purpose on-device inference

All powered by Gemini Nano via AICore.

### Sources
- ML Kit Translation: https://developers.google.com/ml-kit/language/translation/android
- ML Kit GenAI APIs: https://developers.google.com/ml-kit/genai
- AI on Android I/O '25: https://android-developers.googleblog.com/2025/06/top-3-updates-for-ai-on-android-google-io.html

---


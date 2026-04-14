## 34. AI-Powered Accessibility Features

### Gemini-Powered TalkBack

Google integrated Gemini into TalkBack (2024–2025):
- AI-generated image descriptions with follow-up questions
- Full-screen understanding
- Available on Pixel and Samsung flagships

### ML Kit GenAI Image Description

`genai-image-description:1.0.0-beta1` generates alt-text entirely on-device. **Confirmed for Galaxy Z Fold 7** via AICore.

### Expressive Captions (Android 15+)

Captures not just words but emotional tone — `[joy]`, `[sadness]`, speech intensity, duration markers:
- Live Caption supports **22+ languages** (15 offline)
- Sound Notifications detect doorbells, alarms, baby crying, fire alarms
- Visual + vibration alerts

### Sign Language Recognition

MediaPipe Hands detects 21 landmarks per hand in real-time. Combined with pose detection (33 body landmarks), it enables fingerspelling recognition at **<50 ms latency** via TFLite classifiers.

### Features to Build

1. **Screen Reader Mode** — AccessibilityService captures screen content → Gemma 4 generates rich descriptions
2. **Navigation Assistant** — Gemma 4 vision + camera → spatial descriptions with direction and distance
3. **Sign Language Input** — MediaPipe Hands + Pose → fingerspelling → text input
4. **Document Reader** — Photograph documents → OCR → TTS readback with navigation controls
5. **Conversation Captioning** — Real-time speech-to-text with speaker diarization and emotion tags
6. **Simplified Mode** — Large text, high contrast, simplified UI with voice-first interaction

### Sources
- Gemini TalkBack: https://blog.google/outreach-initiatives/accessibility/android-gemini-ai-gaad-2025/
- Pixel Accessibility Updates: https://blog.google/outreach-initiatives/accessibility/google-pixel-camera-accessibility-update-2024/
- ML Kit GenAI: https://developers.google.com/ml-kit/genai

---


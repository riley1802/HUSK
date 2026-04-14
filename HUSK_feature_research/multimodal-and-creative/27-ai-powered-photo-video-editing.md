## 27. AI-Powered Photo/Video Editing

### On-Device Image Generation

**Local Dream** runs Stable Diffusion 1.5 on the Snapdragon 8 Elite NPU in **5–10 seconds** at 512×512 with 20 steps via Qualcomm QNN SDK, supporting txt2img, img2img, inpainting, and LoRA weights. No internet required.

### ML Kit Vision Pipeline

| Feature | API | Size | Latency |
|---------|-----|------|---------|
| Selfie Segmentation | `selfie-segmentation` | ~4.5 MB | 25–65 ms |
| Face Detection | `face-detection` | ~2 MB | 10–30 ms |
| Pose Detection | `pose-detection` | ~3 MB | 30–50 ms |
| Object Detection | `object-detection-custom` | varies | 20–50 ms |
| Image Labeling | `image-labeling` | ~5 MB | 15–40 ms |

### Features to Build

1. **AI Background Removal** — Real-time selfie segmentation + background replacement
2. **Style Transfer** — Apply artistic styles to photos using on-device neural style transfer (TFLite models, ~5–10 MB each)
3. **Super-Resolution** — 4× upscaling via Real-ESRGAN NCNN (~10 MB model)
4. **Smart Crop** — AI-detected subject → automatic composition crop
5. **Video Summarization** — OpenCV key-frame extraction → Gemma 4 vision captioning → summarized timeline
6. **Photo Search** — Embed images at idle time using SigLIP 2 / Gemma 4 vision → ObjectBox vector search → NL queries
7. **Document Enhancement** — Deskew, shadow removal, contrast adjustment via ML Kit Document Scanner
8. **Batch Photo Captioning** — Generate descriptions for entire photo albums offline

### Sources
- Local Dream: https://grokipedia.com/page/Local_Dream_app
- Stable Diffusion Android: https://dev.to/alichherawalla/how-to-run-stable-diffusion-on-your-android-phone-on-device-ai-image-generation-2gbe

---


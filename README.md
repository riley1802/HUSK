# Husk ✨

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/riley1802/HUSK)](https://github.com/riley1802/HUSK/releases)

**A cool, monochrome on-device generative AI playground for Android.**

Husk is a fork of [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) rebranded with a cool monochrome dark theme and a hub home screen. It runs open-source Large Language Models (LLMs) directly on your device — fully offline, private, and lightning-fast — using Google AI Edge and LiteRT under the hood.

## ✨ Core Features

* **Agent Skills**: Transform your LLM from a conversationalist into a proactive assistant. Use the Agent Skills tile to augment model capabilities with tools like Wikipedia for fact-grounding, interactive maps, and rich visual summary cards. You can even load modular skills from a URL or browse community contributions.

* **AI Chat with Thinking Mode**: Engage in fluid, multi-turn conversations and toggle Thinking Mode to peek "under the hood." This feature allows you to see the model's step-by-step reasoning process, which is perfect for understanding complex problem-solving. Note: Thinking Mode currently works with supported models, starting with the Gemma family.

* **Ask Image**: Use multimodal power to identify objects, solve visual puzzles, or get detailed descriptions using your device's camera or photo gallery.

* **Audio Scribe**: Transcribe and translate voice recordings into text in real-time using high-efficiency on-device language models.

* **Prompt Lab**: A dedicated workspace to test different prompts and single-turn use cases with granular control over model parameters like temperature and top-k.

* **Mobile Actions**: Offline device controls and automated tasks powered by a finetune of FunctionGemma 270m.

* **Tiny Garden**: A fun, experimental mini-game that uses natural language to plant and harvest a virtual garden, also powered by a finetune of FunctionGemma 270m.

* **Model Management & Benchmark**: Husk is a flexible sandbox for a wide variety of open-source models. Easily download models from the list or load your own custom models. Manage your model library effortlessly and run benchmark tests to understand exactly how each model performs on your specific hardware.

* **100% On-Device Privacy**: All model inferences happen directly on your device hardware. No internet is required, ensuring total privacy for your prompts, images, and sensitive data.

## 🏁 Get Started

1. **Check OS Requirement**: Android 12 and up.
2. **Build from source**: See [DEVELOPMENT.md](DEVELOPMENT.md) for local build instructions.

## 🛠️ Technology Highlights

*   **Google AI Edge:** Core APIs and tools for on-device ML.
*   **LiteRT:** Lightweight runtime for optimized model execution.
*   **Hugging Face Integration:** For model discovery and download.

## ⌨️ Development

Check out the [development notes](DEVELOPMENT.md) for instructions about how to build the app locally.

## 🤝 Feedback

*   🐞 **Found a bug?** [Report it here!](https://github.com/riley1802/HUSK/issues/new?assignees=&labels=bug&template=bug_report.md&title=%5BBUG%5D)
*   💡 **Have an idea?** [Suggest a feature!](https://github.com/riley1802/HUSK/issues/new?assignees=&labels=enhancement&template=feature_request.md&title=%5BFEATURE%5D)

## 📄 License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## 🔗 Useful Links

*   [Upstream: Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)
*   [Hugging Face LiteRT Community](https://huggingface.co/litert-community)
*   [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
*   [Google AI Edge Documentation](https://ai.google.dev/edge)

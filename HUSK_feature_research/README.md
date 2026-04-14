# HUSK feature research (split)

This folder breaks [HUSK_Feature_Research_MEGA.md](../HUSK_Feature_Research_MEGA.md) into category subfolders. The monolithic mega file is unchanged and remains the full single-file copy.

**Date:** April 13, 2026 (Expanded from April 12, 2026 original)

**Target repo:** [github.com/riley1802/HUSK](https://github.com/riley1802/HUSK)

**Stack:** Kotlin/Android · Google AI Edge · LiteRT / LiteRT-LM · Hugging Face Integration · Gemma 4 E2B/E4B

**Upstream:** [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery)

**Target device:** Samsung Galaxy Z Fold 7 · Snapdragon 8 Elite · 12 GB RAM

---

## Documents by category

### Core architecture and models

- [core-architecture-and-models/01-stack-overview-and-architecture-context.md](core-architecture-and-models/01-stack-overview-and-architecture-context.md)
- [core-architecture-and-models/06-gemma-4-e2b-e4b-deep-dive.md](core-architecture-and-models/06-gemma-4-e2b-e4b-deep-dive.md)
- [core-architecture-and-models/07-litert-lm-feature-matrix.md](core-architecture-and-models/07-litert-lm-feature-matrix.md)

### Intelligence and tool use

- [intelligence-and-tool-use/02-mcp-model-context-protocol-support.md](intelligence-and-tool-use/02-mcp-model-context-protocol-support.md)
- [intelligence-and-tool-use/03-rag-retrieval-augmented-generation-support.md](intelligence-and-tool-use/03-rag-retrieval-augmented-generation-support.md)
- [intelligence-and-tool-use/09-on-device-agent-skills-expansion.md](intelligence-and-tool-use/09-on-device-agent-skills-expansion.md)
- [intelligence-and-tool-use/10-function-calling-and-constrained-decoding.md](intelligence-and-tool-use/10-function-calling-and-constrained-decoding.md)
- [intelligence-and-tool-use/24-structured-output-and-json-mode.md](intelligence-and-tool-use/24-structured-output-and-json-mode.md)
- [intelligence-and-tool-use/25-sandboxed-code-execution.md](intelligence-and-tool-use/25-sandboxed-code-execution.md)

### Proactive and autonomous features

- [proactive-and-autonomous/08-proactive-autonomous-ai-assistant-features.md](proactive-and-autonomous/08-proactive-autonomous-ai-assistant-features.md)
- [proactive-and-autonomous/15-memory-and-context-management.md](proactive-and-autonomous/15-memory-and-context-management.md)
- [proactive-and-autonomous/16-notification-and-ambient-intelligence.md](proactive-and-autonomous/16-notification-and-ambient-intelligence.md)

### Multimodal and creative

- [multimodal-and-creative/11-multimodal-pipeline-features.md](multimodal-and-creative/11-multimodal-pipeline-features.md)
- [multimodal-and-creative/26-neural-tts-voice-cloning-and-speech-synthesis.md](multimodal-and-creative/26-neural-tts-voice-cloning-and-speech-synthesis.md)
- [multimodal-and-creative/27-ai-powered-photo-video-editing.md](multimodal-and-creative/27-ai-powered-photo-video-editing.md)

### On-device intelligence

- [on-device-intelligence/13-on-device-embeddings-and-vector-search.md](on-device-intelligence/13-on-device-embeddings-and-vector-search.md)
- [on-device-intelligence/28-on-device-fine-tuning-and-personalization.md](on-device-intelligence/28-on-device-fine-tuning-and-personalization.md)
- [on-device-intelligence/29-edge-ai-model-distillation.md](on-device-intelligence/29-edge-ai-model-distillation.md)
- [on-device-intelligence/30-local-ai-powered-semantic-search.md](on-device-intelligence/30-local-ai-powered-semantic-search.md)
- [on-device-intelligence/31-on-device-document-understanding.md](on-device-intelligence/31-on-device-document-understanding.md)
- [on-device-intelligence/32-multilingual-and-translation-pipelines.md](on-device-intelligence/32-multilingual-and-translation-pipelines.md)
- [on-device-intelligence/33-offline-first-ai-architecture.md](on-device-intelligence/33-offline-first-ai-architecture.md)

### Platform integration

- [platform-integration/12-android-automation-and-accessibility.md](platform-integration/12-android-automation-and-accessibility.md)
- [platform-integration/34-ai-powered-accessibility-features.md](platform-integration/34-ai-powered-accessibility-features.md)
- [platform-integration/35-health-and-fitness-ai.md](platform-integration/35-health-and-fitness-ai.md)
- [platform-integration/36-wearable-ai-integration-wearos.md](platform-integration/36-wearable-ai-integration-wearos.md)
- [platform-integration/37-smart-home-and-iot-integration.md](platform-integration/37-smart-home-and-iot-integration.md)
- [platform-integration/38-cross-device-ai-mesh.md](platform-integration/38-cross-device-ai-mesh.md)
- [platform-integration/39-android-background-ai-processing.md](platform-integration/39-android-background-ai-processing.md)

### Inference optimization

- [inference-optimization/04-turboquant-kv-cache-compression.md](inference-optimization/04-turboquant-kv-cache-compression.md)
- [inference-optimization/14-speculative-decoding-and-inference-optimization.md](inference-optimization/14-speculative-decoding-and-inference-optimization.md)
- [inference-optimization/40-sub-4-bit-quantization.md](inference-optimization/40-sub-4-bit-quantization.md)
- [inference-optimization/41-persistent-kv-cache-and-prompt-caching.md](inference-optimization/41-persistent-kv-cache-and-prompt-caching.md)
- [inference-optimization/42-flash-attention-and-mobile-gpu-attention-variants.md](inference-optimization/42-flash-attention-and-mobile-gpu-attention-variants.md)
- [inference-optimization/43-cpu-gpu-npu-heterogeneous-compute-scheduling.md](inference-optimization/43-cpu-gpu-npu-heterogeneous-compute-scheduling.md)
- [inference-optimization/44-npu-acceleration-deep-dive.md](inference-optimization/44-npu-acceleration-deep-dive.md)
- [inference-optimization/45-adaptive-compute-early-exit-and-layer-skipping.md](inference-optimization/45-adaptive-compute-early-exit-and-layer-skipping.md)
- [inference-optimization/46-battery-and-thermal-management-for-sustained-inference.md](inference-optimization/46-battery-and-thermal-management-for-sustained-inference.md)
- [inference-optimization/47-litert-lm-vs-gguf-format-comparison.md](inference-optimization/47-litert-lm-vs-gguf-format-comparison.md)
- [inference-optimization/48-prefill-chunking-and-ttft-optimization.md](inference-optimization/48-prefill-chunking-and-ttft-optimization.md)

### Quality and standards

- [quality-and-standards/49-edge-ai-benchmarking-standards-and-metrics.md](quality-and-standards/49-edge-ai-benchmarking-standards-and-metrics.md)

### Privacy, UX, and developer

- [privacy-ux-developer/17-privacy-and-security-features.md](privacy-ux-developer/17-privacy-and-security-features.md)
- [privacy-ux-developer/18-model-management-enhancements.md](privacy-ux-developer/18-model-management-enhancements.md)
- [privacy-ux-developer/19-developer-power-user-features.md](privacy-ux-developer/19-developer-power-user-features.md)
- [privacy-ux-developer/20-ui-ux-enhancements.md](privacy-ux-developer/20-ui-ux-enhancements.md)
- [privacy-ux-developer/21-hidden-gems-for-on-device-ai.md](privacy-ux-developer/21-hidden-gems-for-on-device-ai.md)

### Reference

- [reference/05-project-tusk-research-status.md](reference/05-project-tusk-research-status.md)
- [reference/51-complete-source-index.md](reference/51-complete-source-index.md)
- [reference/addendum-deep-dive-expansions.md](reference/addendum-deep-dive-expansions.md)
- [reference/implementation-priority-matrix.md](reference/implementation-priority-matrix.md)


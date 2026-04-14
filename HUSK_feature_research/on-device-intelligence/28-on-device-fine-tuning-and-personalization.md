## 28. On-Device Fine-Tuning & Personalization

### QVAC Fabric LLM (Production-Ready)

**QVAC Fabric LLM** (Apache 2.0, December 2025) integrates LoRA fine-tuning directly into llama.cpp using Vulkan compute, supporting Adreno, Mali, and Apple GPUs:

| Device | Model | Training Time | Tokens |
|--------|-------|---------------|--------|
| Samsung S25 (Adreno) | BitNet 1B | 78 minutes | ~18K |
| Samsung S25 (Adreno) | 125M model | ~10 minutes | ~18K |
| Desktop GPU | Gemma 3 2B | ~20 minutes | ~18K |

Gemma 3 and Qwen3 architectures are supported, with Gemma 4 expected to follow.

### MobileFineTuner (Research)

**MobileFineTuner** (arXiv 2512.08211) provides a unified C++ framework tested on Pixel devices with GPT-2, Gemma, and Qwen. Currently CPU-only, focusing on memory-efficient gradient computation.

### Memory Budget for Z Fold 7

QLoRA fine-tuning of Gemma 4 E2B is feasible on the Z Fold 7's **12 GB RAM**:
- Model at INT4: ~1.3 GB
- LoRA adapter overhead: 2–4 GB (rank 8–16)
- OS + system: ~3–4 GB
- Headroom: ~3–5 GB remaining

LoRA adapter files are small — **10–50 MB** for rank 8–16 targeting major linear layers — enabling per-user adapter storage at negligible cost.

### LoRA-FA (Memory Optimization)

**LoRA-FA** freezes the projection-down matrix (A) after initialization and only trains the up-projection (B), reducing activation memory by **1.4×** over standard LoRA. Ideal for constrained devices.

### Google's Official Path

Google's recommended workflow remains cloud-to-device:
1. Fine-tune LoRA adapters on Vertex AI
2. Deploy as compressed adapter files
3. Load dynamically at inference via Android AI Core (supports Gemini Nano adapter swapping)
4. LiteRT-LM itself is **inference-only** — no fine-tuning API

### Federated Learning

Android's On-Device Personalization module (since Android 13) provides `FederatedCompute` APIs with:
- Differential privacy guarantees
- Aggregation in a Trusted Execution Environment
- **HeLoRA** (ACM TOIT, April 2025) extends this to heterogeneous LoRA ranks across devices

### Features to Build

1. **Personal Adapter Training** — UI for on-device LoRA fine-tuning (when QVAC supports Gemma 4)
2. **Adapter Manager** — Browse, load, swap, and delete trained adapters
3. **Training Data Curator** — Collect and format training examples from conversations
4. **Cloud Fine-Tune Exporter** — Export formatted training data for cloud LoRA training
5. **Style Cloning** — Fine-tune on user's writing samples to match their voice
6. **Domain Specialization** — Fine-tune for specific domains (medical, legal, code)

### Sources
- QVAC Fabric LLM: https://huggingface.co/blog/qvac/fabric-llm-finetune
- QVAC BitNet Fine-Tuning: https://huggingface.co/blog/qvac/fabric-llm-finetune-bitnet
- MobileFineTuner: https://arxiv.org/html/2512.08211
- LoRA-FA: https://openreview.net/forum?id=RbKThNNFxr
- LiteRT Fine-Tuning Issue: https://github.com/google-ai-edge/LiteRT/issues/1420
- Google On-Device Personalization: https://www.predli.com/post/fine-tuning-series-on-device-llms---how-google-leads-and-why-apple-should-follow
- Federated Learning (Privacy Sandbox): https://privacysandbox.google.com/protections/on-device-personalization/create-federated-learning-job

---


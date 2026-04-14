## 42. Flash Attention & Mobile GPU Attention Variants

### Flash Attention is NOT Compatible with Mobile GPUs

FA2/3/4 require NVIDIA CUDA or AMD ROCm hardware. They do **not** run on Adreno or Mali GPUs.

### What Works Instead

**ML Drift** (Google) — Powers LiteRT-LM on Adreno 830 with:
- Specialized OpenCL attention kernels
- Mixed-precision quantization (8/4/4: int8 for attention, int4 for embedding/FFN)
- Optimized for mobile memory hierarchy

**Transformer-Lite** (OPPO) — Achieves **330 tok/s prefill** for Gemma 2B on Adreno 750 — 10× over MLC-LLM.

**Qualcomm OpenCL Backend** for llama.cpp — Officially upstreamed, but Flash Attention falls back to CPU on Adreno.

### Key Insight

Mobile GPU efficiency comes from **format-specific kernels** (ML Drift, Transformer-Lite) rather than porting desktop attention algorithms. LiteRT-LM's native kernels are purpose-built for Adreno/Mali and outperform generic OpenCL implementations.

### Sources
- Flash Attention Explained: https://www.clarifai.com/blog/flash-attention-2
- On-Device LLMs Survey 2026: https://v-chandra.github.io/on-device-llms/
- Qualcomm OpenCL llama.cpp: https://github.com/ggml-org/llama.cpp/pull/10693
- Scaling On-Device GPU Inference: https://openaccess.thecvf.com/content/CVPR2025W/EDGE/papers/Tang_Scaling_On-Device_GPU_Inference_for_Large_Generative_Models_CVPRW_2025_paper.pdf

---


## 29. Edge AI Model Distillation

### Server-Side Only (Practical Reality)

Knowledge distillation **must happen server-side** — running teacher and student simultaneously exceeds mobile memory budgets. Google distilled Gemma 4 from Gemini models using **on-policy distillation** (student generates its own completions, then minimizes KL divergence against teacher logits).

### Distillation Pipeline

The **ECLD framework** (arXiv 2602.13628) defines a four-stage pipeline purpose-built for edge targets:
1. **Pruning** — Remove redundant parameters
2. **Distillation** — Transfer knowledge from teacher to student
3. **Quantization** — Compress to INT4/INT8
4. **Hardware-Aware Deployment** — Optimize for target SoC

### Practical Numbers

- Distilling Gemma 4B → custom 1B: ~100–500 GPU hours on cloud
- Resulting INT4 1B model: **~250–500 MB**, sub-1 GB RAM
- Quality retention: ~85–92% of teacher on domain-specific tasks

### Sources
- ECLD Framework: https://arxiv.org/html/2602.13628v1
- Gemma 4 Model Card: https://huggingface.co/google/gemma-4-E2B

---


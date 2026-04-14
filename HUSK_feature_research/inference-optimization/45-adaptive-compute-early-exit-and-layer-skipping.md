## 45. Adaptive Compute — Early Exit & Layer Skipping

### ADEPT (January 2026)

Enables early exit with a decoupled mechanism:
- Parallelizes skipped-layer processing
- **25% efficiency improvement**
- Hidden state mapper maintains KV cache consistency across exits

### AdaInfer (IJCAI 2025)

Prunes layers dynamically per-token:
- **9–43% of layers pruned** (average 17.8%)
- <1% performance drop
- Negligible decision overhead (small linear classifier per layer)

### Implementation for Gemma 4

Add lightweight linear exit heads every 4–6 layers:
1. Train exit classifiers on intermediate hidden states (frozen base model)
2. At inference, classifier predicts confidence → skip remaining layers if sufficient
3. Simple queries exit early (fewer layers), complex queries use full depth
4. Expected speedup: **1.3–2× for simple queries**, no regression for complex ones

### Sources
- ADEPT: https://arxiv.org/html/2601.03700
- AdaInfer: https://www.ijcai.org/proceedings/2025/0566.pdf

---


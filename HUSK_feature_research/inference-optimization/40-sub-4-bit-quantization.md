## 40. Sub-4-Bit Quantization

### BitNet b1.58 2B4T (Microsoft, April 2025)

Ternary weights {-1, 0, +1} at just **0.4 GB** for 2B non-embedding weights:
- Decode latency: **29 ms**
- Energy: **0.028 J** per inference — 6× better than Gemma-3 1B
- Matches FP16 within 1–2 points on MMLU and GSM8K
- ARM NEON kernels available via `bitnet.cpp`
- ⚠️ Requires custom runtime (not compatible with LiteRT-LM or GGUF)

### AQLM (Multi-Codebook Quantization)

- O(1) lookup-table dequantization
- Explicitly cited as "critical for edge deployment on ARM CPUs"
- Pareto-optimal quality in the **sub-3-bit range**
- Uses additive quantization with learned codebooks

### QuIP# (Hadamard Incoherence + Lattice Codebooks)

- Near-lossless at 2-bit
- Hadamard rotation reduces outlier sensitivity
- Lattice codebooks for efficient vector quantization

### QTIP (NeurIPS 2024)

- Outperforms both QuIP# and AQLM at **all bitrates**
- 2 KiB codebook fits in **L1 cache**
- Uses ARM's `vqtbl4q_u8` NEON intrinsic for fast lookup
- Trellis-coded quantization for optimal rate-distortion

### Practical Verdict

A **2-bit 4B model with AQLM/QTIP likely outperforms a 4-bit 2B model** because larger models retain more expressive capacity under compression. However:
- Snapdragon 8 Elite's Hexagon NPU has **no documented native sub-4-bit kernel support**
- Sub-4-bit must run on **CPU only**
- **INT4 remains the sweet spot** for NPU-accelerated mobile inference

LiteRT-LM supports 2-bit and 4-bit weights with memory-mapped embeddings. Qualcomm's standard deployment format is **W4A16** (4-bit weights, 16-bit activations).

### Sources
- BitNet b1.58: https://huggingface.co/microsoft/bitnet-b1.58-2B-4T
- BitNet GitHub: https://github.com/microsoft/BitNet
- AQLM/QuIP#: https://www.researchgate.net/publication/395215528_QuIP_Even_Better_LLM_Quantization_with_Hadamard_Incoherence_and_Lattice_Codebooks
- QTIP (NeurIPS 2024): https://proceedings.neurips.cc/paper_files/paper/2024/file/6de2e84b8da47bb2eb5e2ac96c63d2b0-Paper-Conference.pdf
- Awesome LLM Quantization: https://github.com/pprp/Awesome-LLM-Quantization

---


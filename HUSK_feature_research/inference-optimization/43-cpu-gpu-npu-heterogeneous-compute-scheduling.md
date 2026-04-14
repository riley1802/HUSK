## 43. CPU-GPU-NPU Heterogeneous Compute Scheduling

### Three Research Systems Define the State of the Art

**llm.npu (ASPLOS '25):**
- Maximizes NPU prefill execution with chunk-wise fixed-length graphs
- Avoids the ~11-second re-preparation cost per different prompt length
- Reduces memory by **75%** through subgraph sharing
- 120 of 144 subgraphs shared in Qwen1.5-1.8B

**HeteroLLM (SOSP '25):**
- Parallelizes GPU+NPU to reach ~60 GB/s bandwidth (vs ~45 for GPU alone)
- Different tensor partition strategies for compute-bound prefill vs memory-bound decode
- Optimal split: NPU handles integer MatMul, GPU handles float attention

**Agent.xpu:**
- Adds kernel-level preemption for agentic workloads
- Priority-based scheduling for multi-agent scenarios

### Optimal Mapping for Snapdragon 8 Elite

| Component | Best Accelerator | Why |
|-----------|-----------------|-----|
| Integer MatMul (weights) | NPU (Hexagon) | 10× more TFLOPS than GPU |
| Float Attention | GPU (Adreno 830) | Better float throughput |
| Decode phase | GPU | Higher memory bandwidth |
| Prefill phase | NPU | More compute-efficient |
| LayerNorm, Softmax | CPU | Control plane, synchronization |

### Sources
- llm.npu (ASPLOS '25): https://xumengwei.github.io/files/ASPLOS25-NPU.pdf
- HeteroLLM: https://arxiv.org/html/2501.14794v1
- LLM Inference at the Edge: https://arxiv.org/pdf/2603.23640

---


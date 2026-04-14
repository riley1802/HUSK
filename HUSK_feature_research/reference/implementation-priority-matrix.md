# Implementation Priority Matrix

## Summary matrix (original §22)


| Priority | Feature | Effort | Impact |
|----------|---------|--------|--------|
| 🔴 P0 | RAG SDK Integration | Medium | Massive — grounds responses in user data |
| 🔴 P0 | MCP Client Support | Medium-High | Massive — universal tool connectivity |
| 🔴 P0 | Persistent Memory System | Medium | High — makes assistant feel intelligent |
| 🟠 P1 | Enhanced Function Calling (tool chaining) | Medium | High — enables complex workflows |
| 🟠 P1 | Constrained Decoding UI | Low | High — guaranteed structured output |
| 🟠 P1 | Voice Command Mode | Medium | High — hands-free operation |
| 🟡 P2 | Speculative Decoding (E2B→E4B) | Low-Medium | Medium — significant speedup |
| 🟡 P2 | Android Notification Integration | Medium | Medium — ambient intelligence |
| 🟡 P2 | Document Scanner + OCR | Low | Medium — feeds RAG pipeline |
| 🟡 P2 | Conversation Export/Search | Low | Medium — power user feature |
| 🟢 P3 | TurboQuant Integration | Low (when available) | Medium — longer conversations |
| 🟢 P3 | AICore Detection | Low | Medium — zero-storage model option |
| 🟢 P3 | Local Web UI / API Mode | Medium | Niche — power users |
| 🟢 P3 | Custom Skill Builder | High | Medium — extensibility |

---




---


---

## Expanded matrix (§50)


### P0 — Critical (Implement First)

| Feature | Effort | Impact | Section |
|---------|--------|--------|---------|
| RAG SDK Integration | Medium | Massive — grounds responses in user data | §3 |
| MCP Client Support | Medium-High | Massive — universal tool connectivity | §2 |
| Persistent Memory System | Medium | High — makes assistant feel intelligent | §15 |
| Structured JSON Output (Constrained Decoding) | Low | High — guaranteed structured output | §24 |
| ADPF Thermal Management | Low-Medium | Critical — prevents thermal collapse | §46 |

### P1 — High Priority

| Feature | Effort | Impact | Section |
|---------|--------|--------|---------|
| Enhanced Function Calling (tool chaining) | Medium | High — enables complex workflows | §10 |
| Voice Command Mode (Kokoro/Piper TTS) | Medium | High — hands-free operation | §26 |
| NPU-First Inference (QNN Accelerator) | Medium | Very High — 3–10× perf vs CPU | §44 |
| Document Understanding Pipeline | Low-Medium | High — feeds RAG | §31 |
| Google Home API Integration | Medium | High — smart home control | §37 |
| EmbeddingGemma + ObjectBox Search | Medium | High — unified semantic search | §30 |

### P2 — Medium Priority

| Feature | Effort | Impact | Section |
|---------|--------|--------|---------|
| Speculative Decoding (E2B→E4B) | Low-Medium | Medium — significant speedup | §14 |
| Android Notification Intelligence | Medium | Medium — ambient AI | §16 |
| Sandboxed Code Execution | Low | Medium — code runner skill | §25 |
| ML Kit Accessibility Features | Medium | Medium — inclusive design | §34 |
| Health Connect Integration | Medium | Medium — wellness AI | §35 |
| WorkManager Background Processing | Low-Medium | Medium — overnight indexing | §39 |
| Conversation Export/Search | Low | Medium — power user feature | §19 |
| Multilingual Translation Pipelines | Low-Medium | Medium — global reach | §32 |

### P3 — Future / Experimental

| Feature | Effort | Impact | Section |
|---------|--------|--------|---------|
| TurboQuant Integration | Low (when available) | Medium — longer conversations | §4 |
| On-Device LoRA Fine-Tuning | High | High — personal models | §28 |
| Persistent KV Cache Serialization | High | Medium — instant session restore | §41 |
| Cross-Device AI Mesh | High | Niche — multi-device users | §38 |
| Adaptive Compute (Early Exit) | High | Medium — query-adaptive speed | §45 |
| Sub-4-Bit Quantization | Medium | Low (NPU incompatible) | §40 |
| WearOS Companion App | Medium | Niche — watch owners | §36 |
| AICore Detection | Low | Medium — zero-storage model | §21 |
| Local Web UI / API Mode | Medium | Niche — power users | §19 |
| NeuTTS Air Voice Cloning | Medium | Niche — personalization | §26 |
| AI Image Generation (Local Dream) | Medium | Niche — creative users | §27 |

---


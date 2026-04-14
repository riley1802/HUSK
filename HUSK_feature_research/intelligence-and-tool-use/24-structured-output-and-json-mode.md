## 24. Structured Output & JSON Mode

### LiteRT-LM Native Constrained Decoding

LiteRT-LM v0.10.1+ includes **first-party constrained decoding** that applies token masks at each generation step, guaranteeing 100% structural correctness for JSON, function calls, and custom schemas. This is not post-processing — it modifies the logits before sampling, making invalid tokens impossible.

### XGrammar — Near-Zero Overhead

**XGrammar** (CMU/MLC team, MLSys 2025) provides the fastest JSON schema enforcement available — up to **100× faster** than Outlines or Guidance — with near-zero overhead per token. It integrates with MLC-LLM on Android and uses a pushdown automaton for O(1) token validation. Key insight: constrained decoding can actually **speed up** generation by eliminating filler tokens and stopping immediately when the structure is complete.

### llama.cpp GBNF Grammars

For GGUF-based models (not the primary HUSK path but useful context), llama.cpp provides GBNF grammar support with a built-in JSON Schema→GBNF converter. This enables regex, enum, and context-free grammar constraints.

### Implementation for HUSK

```kotlin
// LiteRT-LM constrained decoding for JSON output
val constraintOptions = ConstraintOptions.newBuilder()
    .setJsonSchema(jsonSchemaString) // Force output to match schema
    .build()
chatSession.enableConstraint(constraintOptions)
```

**Features to build:**
1. **Schema Editor UI** — Visual JSON schema builder for non-technical users
2. **Pre-Built Schemas** — Library of common output schemas (todo items, contacts, events, structured notes)
3. **Auto-Schema from Context** — Model analyzes the conversation and suggests an appropriate output schema
4. **Schema Validation Dashboard** — Show success/failure rates for constrained generation
5. **Enum-Only Mode** — Restrict output to predefined options for classification tasks
6. **Grammar-Constrained Output** — Support for context-free grammars beyond JSON

### Sources
- XGrammar (MLSys 2025): https://blog.vllm.ai/2025/01/14/struct-decode-intro.html
- llama.cpp GBNF: https://github.com/ggml-org/llama.cpp/blob/master/grammars/README.md
- Structured Generation Guide: https://www.dataiku.com/stories/blog/your-guide-to-structured-text-generation

---


## 10. Function Calling & Constrained Decoding

### Current State

HUSK inherits Google AI Edge Gallery's function calling system, powered by:
- **FunctionGemma 270m** — Purpose-built model for Mobile Actions and Tiny Garden
- **LiteRT-LM Tool Use API** — `@Tool` and `@ToolParam` annotations for Kotlin functions
- **Constrained Decoding** — Forces LLM output to conform to valid function call schemas

### Enhanced Function Calling Features to Add

1. **Dynamic Tool Registration** — Register/unregister tools at runtime without app restart
2. **Tool Chaining** — Output of one tool feeds as input to the next automatically
3. **Conditional Tool Execution** — "If weather is rainy, set reminder; otherwise, suggest outdoor activity"
4. **Tool Permission System** — Per-tool user approval toggles (e.g., "allow calendar write but not read")
5. **Tool Usage Analytics** — Track which tools are used most, success rates, latency
6. **Custom Tool Builder** — UI for users to define simple tools (input schema → action) without code
7. **Tool Import from OpenAPI** — Import tool definitions from OpenAPI/Swagger specs
8. **Fallback Tool Strategies** — When a tool fails, try alternative tools automatically

### Constrained Decoding Enhancements

Gemma 4 supports constrained decoding natively via LiteRT-LM. This forces output to match a schema:

```kotlin
val constraintOptions = ConstraintOptions.newBuilder()
    .setToolCallOnly(
        ConstraintOptions.ToolCallOnly.newBuilder()
            .setConstraintPrefix("```tool_code\n")
            .setConstraintSuffix("\n```")
    ).build()
chatSession.enableConstraint(constraintOptions)
```

Additional constrained decoding features to implement:
- **JSON Schema Mode** — Force all outputs to valid JSON matching a user-defined schema
- **Enum-Only Mode** — Restrict output to a predefined set of options (for classification tasks)
- **Regex-Constrained Output** — Force output to match a regex pattern
- **Grammar-Constrained Output** — Use context-free grammars for structured output

### Sources
- AI Edge Function Calling Guide: https://ai.google.dev/edge/mediapipe/solutions/genai/function_calling/android
- LiteRT-LM Tool Use: https://ai.google.dev/edge/litert-lm/android
- Constrained Decoding Guide: https://www.aidancooper.co.uk/constrained-decoding/

---


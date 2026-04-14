## 25. Sandboxed Code Execution

### JavaScriptSandbox (Primary Recommendation)

Android's **JavaScriptSandbox** (`androidx.javascriptengine`) runs a dedicated V8 engine in a **separate process** with zero file system or network access. It supports WebAssembly via `provideNamedData()`, works on API 26+, and integrates with Kotlin coroutines.

**Integration pattern:**
1. Gemma generates JavaScript code via function calling
2. JavaScriptSandbox evaluates with configurable timeout
3. Result string feeds back into conversation context
4. Model interprets results and continues

```kotlin
// Kotlin integration
val sandbox = JavaScriptSandbox.createConnectedInstanceAsync(context).await()
val isolate = sandbox.createIsolate()
val result = isolate.evaluateJavaScriptAsync("2 + 2").await() // "4"
```

### Python on Android: Chaquopy

**Chaquopy** embeds CPython with NumPy, Pandas, and OpenCV support at ~15–30 MB APK impact. It provides a full Python environment for data science and numerical computation workloads.

### Maximum Sandboxing: Chasm (Kotlin WASM Runtime)

**Chasm** is a pure Kotlin WebAssembly runtime providing typesafe interfaces with WASI capability-based I/O control. Perfect for running arbitrary code with fine-grained permission control.

### Features to Build

1. **Code Runner Skill** — Agent Skill that generates and executes code in the sandbox
2. **Output Visualization** — Render code output as tables, charts, or formatted text
3. **Execution History** — Track all code executions with inputs and outputs
4. **Language Selection** — Toggle between JS (default) and Python (Chaquopy)
5. **Timeout Controls** — User-configurable execution timeouts
6. **WASM Module Support** — Load pre-compiled WASM modules for specific tasks (crypto, data processing)

### Sources
- AndroidX JavaScriptEngine: https://developer.android.com/reference/androidx/javascriptengine/package-summary
- Chaquopy: https://proandroiddev.com/chaquopy-using-python-in-android-apps-dd5177c9ab6b
- Chasm WASM Runtime: https://github.com/nicholasgasior/chasm

---


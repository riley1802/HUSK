# Model Allowlists

## Overview
Model allowlists define which AI models are available for download and use in HUSK. Each allowlist is a JSON file containing model definitions with HuggingFace coordinates, file sizes, runtime types, and generation configuration.

## JSON Schema

The top-level structure is an object with a `models` array:

```json
{
  "models": [ ... ]
}
```

Each model entry has these fields:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Display name shown in the app |
| `modelId` | string | Yes | HuggingFace repo ID (e.g. `litert-community/Gemma3-1B-IT`) |
| `modelFile` | string | Yes | Filename within the HuggingFace repo to download |
| `description` | string | No | Human-readable description shown in the app |
| `sizeInBytes` | number | Yes | File size used for download progress display |
| `minDeviceMemoryInGb` | number | No | Minimum RAM required; used to filter models on low-memory devices |
| `commitHash` | string | Yes | HuggingFace commit hash or `"main"` to pin the download |
| `runtimeType` | string | No | Runtime engine — see Runtime Types below. Omit for standard TFLite |
| `llmSupportImage` | boolean | No | Declares multimodal image input support |
| `llmSupportAudio` | boolean | No | Declares multimodal audio input support |
| `llmSupportThinking` | boolean | No | Declares extended thinking / chain-of-thought support |
| `defaultConfig` | object | **Yes** | Runtime configuration — must include at least `"accelerators"` |
| `taskTypes` | array | Yes | Task screens this model appears on — see Task Types below |
| `bestForTaskTypes` | array | No | Subset of `taskTypes` where this model is the recommended choice |

### Runtime Types

| `runtimeType` value | Engine | Model formats | Example models |
|---------------------|--------|---------------|----------------|
| _(omitted)_ | Standard TFLite interpreter | `.tflite` | ECAPA-TDNN |
| `whisper` | whisper.cpp via JNI | `.bin` (GGML) | Whisper Tiny/Base/Small |
| _(LiteRT-LM is the default for `.litertlm` files)_ | LiteRT-LM (Google AI Edge) | `.litertlm` | Gemma, Qwen, DeepSeek |

Note: LiteRT-LM models do not set `runtimeType` explicitly — the app infers the runtime from the `.litertlm` file extension.

### defaultConfig (REQUIRED)

Every model entry must have a `defaultConfig` block. At minimum it must contain `accelerators`:

```json
"defaultConfig": {
  "accelerators": "cpu"
}
```

LLM models include generation parameters inline in this same block:

```json
"defaultConfig": {
  "topK": 64,
  "topP": 0.95,
  "temperature": 1.0,
  "maxTokens": 4096,
  "maxContextLength": 32000,
  "accelerators": "gpu,cpu",
  "visionAccelerator": "gpu"
}
```

**Warning:** Missing `defaultConfig` or a missing `accelerators` key causes a NullPointerException in `ModelManagerViewModel`.

#### defaultConfig fields

| Field | Type | Description |
|-------|------|-------------|
| `accelerators` | string | Comma-separated accelerator preference order: `"gpu,cpu"`, `"cpu"`, `"gpu"` |
| `visionAccelerator` | string | Accelerator for vision encoder when `llmSupportImage` is true |
| `topK` | number | Top-K sampling parameter |
| `topP` | number | Nucleus sampling threshold |
| `temperature` | number | Sampling temperature (0.0 = greedy/deterministic) |
| `maxTokens` | number | Maximum tokens to generate per response |
| `maxContextLength` | number | KV cache context window size |

### Task Types

| `taskTypes` value | Screen |
|-------------------|--------|
| `llm_chat` | Chat interface |
| `llm_prompt_lab` | Prompt Lab (raw prompt testing) |
| `llm_agent_chat` | Agent Chat (tool-use enabled) |
| `llm_ask_image` | Ask Image (vision input) |
| `llm_ask_audio` | Ask Audio / Audio Scribe (audio input) |
| `llm_tiny_garden` | Tiny Garden (function-calling demo) |
| `llm_mobile_actions` | Mobile Actions (on-device automation) |

## Adding New Models

1. Add the model entry to the appropriate allowlist JSON file (e.g., `1_0_11.json`)
2. Include all required fields, especially `defaultConfig` with `accelerators`
3. Push to device for testing:
   ```
   adb push model_allowlists/1_0_11.json /data/local/tmp/model_allowlist_test.json
   ```
4. Restart the app — the test file takes priority over the GitHub-hosted allowlist

### Example: Adding a Whisper model

```json
{
  "name": "Whisper-Base",
  "modelId": "ggerganov/whisper.cpp",
  "modelFile": "ggml-base.bin",
  "description": "OpenAI Whisper Base model for on-device speech transcription via whisper.cpp.",
  "sizeInBytes": 148000000,
  "minDeviceMemoryInGb": 4,
  "commitHash": "main",
  "runtimeType": "whisper",
  "defaultConfig": {
    "accelerators": "cpu"
  },
  "taskTypes": ["llm_ask_audio"],
  "bestForTaskTypes": []
}
```

### Example: Adding a LiteRT-LM chat model

```json
{
  "name": "Gemma3-1B-IT",
  "modelId": "litert-community/Gemma3-1B-IT",
  "modelFile": "gemma3-1b-it-int4.litertlm",
  "description": "Gemma 3 1B with 4-bit quantization for on-device chat.",
  "sizeInBytes": 584417280,
  "minDeviceMemoryInGb": 6,
  "commitHash": "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
  "defaultConfig": {
    "topK": 64,
    "topP": 0.95,
    "temperature": 1.0,
    "maxTokens": 1024,
    "accelerators": "gpu,cpu"
  },
  "taskTypes": ["llm_chat", "llm_prompt_lab"],
  "bestForTaskTypes": ["llm_chat", "llm_prompt_lab"]
}
```

## Runtime Loading Precedence

The app loads the model allowlist from these sources, in order:

1. **Test file** (dev only): `/data/local/tmp/model_allowlist_test.json`
2. **GitHub raw URL**: Fetched from the repository on app launch
3. **Cached local file**: Offline fallback from last successful fetch

For development, always use the test file path to iterate quickly without a GitHub push.

## File Naming

Allowlist files follow the pattern `{major}_{minor}_{patch}.json` corresponding to the app version. The app loads the file matching its current version string.

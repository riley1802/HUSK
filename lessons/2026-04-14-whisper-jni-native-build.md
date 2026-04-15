# whisper.cpp JNI Native Build for Android

## What went wrong
Integrating whisper.cpp as a native Android library required navigating several
CMake and NDK configuration challenges that aren't well-documented.

## Key findings

### CMake Configuration
- whisper.cpp depends on ggml. Rather than manually enumerating ggml source files,
  use CMake `FetchContent` pointing to the local ggml directory within whisper.cpp:
  ```cmake
  FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_DIR}/ggml)
  FetchContent_MakeAvailable(ggml)
  ```
- Must explicitly define `GGML_USE_CPU` since Android doesn't support ggml's CUDA/Metal backends
- Target arm64-v8a with fp16 optimization: `-march=armv8.2-a+fp16`

### JNI Function Naming
- JNI function names MUST exactly match the Kotlin package path
- Example: `Java_com_google_ai_edge_gallery_runtime_WhisperJni_initModel`
- Any mismatch causes `UnsatisfiedLinkError` at runtime with no helpful message

### Build Integration
- In `app/build.gradle.kts`, add `externalNativeBuild { cmake { path = "src/main/cpp/CMakeLists.txt" } }`
- Set `ndk { abiFilters += listOf("arm64-v8a") }` to avoid building for unused architectures
- The whisper.cpp source directory (~37MB) is gitignored and must be cloned separately

## How to prevent
- Use FetchContent for ggml rather than manual source enumeration
- Verify JNI function names match package path exactly before debugging link errors
- Test native build early with `./gradlew assembleDebug` — don't wait until UI integration
- Keep whisper.cpp as a shallow clone (`--depth 1`) to minimize size

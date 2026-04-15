# Development Guide

## Prerequisites

- Android Studio Ladybug or newer
- JDK 17+
- Android NDK (installed via SDK Manager — arm64-v8a only)
- CMake 3.22+ (installed via SDK Manager)
- Git
- Android device or emulator running Android 12+ (API 31+)
- ADB configured with device connected

To install NDK and CMake: Android Studio -> Settings -> Languages & Frameworks -> Android SDK -> SDK Tools -> check NDK (Side by side) and CMake.

## Clone and Setup

```
git clone git@github.com:riley1802/HUSK.git
cd HUSK
```

## whisper.cpp Native Library

The Audio Scribe feature requires whisper.cpp compiled as a native library. The source is vendored but gitignored (~37 MB of C/C++ source), so you must clone it manually:

```
git clone --depth 1 https://github.com/ggml-org/whisper.cpp.git Android/src/app/src/main/cpp/whisper
```

The CMake build at `Android/src/app/src/main/cpp/CMakeLists.txt` compiles this to `libwhisper_jni.so` targeting arm64-v8a with fp16 optimizations. The first build also fetches ggml via CMake `FetchContent` from within the whisper.cpp directory.

## HuggingFace OAuth Setup

Model download functionality requires a HuggingFace OAuth application. Create one at [huggingface.co/settings/applications](https://huggingface.co/settings/applications) ([official docs](https://huggingface.co/docs/hub/oauth#creating-an-oauth-app)).

After creating the application:

1. In [`ProjectConfig.kt`](https://github.com/riley1802/HUSK/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/common/ProjectConfig.kt), replace the placeholders for `clientId` and `redirectUri` with the values from your HuggingFace developer application.

2. In [`app/build.gradle.kts`](https://github.com/riley1802/HUSK/blob/main/Android/src/app/build.gradle.kts), modify the `manifestPlaceholders["appAuthRedirectScheme"]` value to match the redirect URL you configured in your HuggingFace developer application.

## Build and Install

```
cd Android/src/
./gradlew installDebug
```

This compiles the native whisper.cpp library, packages the APK, and installs it on the connected device. First build takes longer due to ggml FetchContent and C++ compilation.

## Model Allowlist (Development)

Models available in the app are controlled by an allowlist JSON.

```
adb push model_allowlists/1_0_11.json /data/local/tmp/model_allowlist_test.json
```

- The app checks `/data/local/tmp/model_allowlist_test.json` first before falling back to GitHub-hosted allowlists
- After modifying the allowlist, push again and restart the app
- Model entries must include a `defaultConfig` block with at least `"accelerators": "cpu"` — a missing `defaultConfig` causes a null pointer exception at runtime

## Proto DataStore

Settings are stored via Proto DataStore. The proto definition is at:

```
Android/src/app/src/main/proto/settings.proto
```

Protobuf classes are auto-generated on build. If you modify the proto definition, rebuild the project to regenerate them.

## Room Database

Speaker profiles and transcription history use Room (`SpeakerDatabase`). The database is configured with `fallbackToDestructiveMigration()` — if you change entity schemas, bump the version number in `SpeakerDatabase.kt`. This wipes local data on upgrade, which is acceptable during development.

## Target Device

Primary development target: Samsung Galaxy Z Fold 7 (Snapdragon 8 Elite, 12 GB RAM). The project builds arm64-v8a only.

## Troubleshooting

**CMake errors**
Ensure CMake 3.22+ is installed: SDK Manager -> SDK Tools -> CMake.

**NDK not found**
Install NDK via SDK Manager. The project requires arm64-v8a support.

**ggml fetch fails**
The CMake build uses `FetchContent` for ggml from within the local whisper.cpp directory. Ensure the whisper.cpp clone step was completed before building.

**Proto compilation errors**
Clean and rebuild:
```
./gradlew clean assembleDebug
```

**Models not appearing**
Ensure the test allowlist is pushed to the device and the app has been restarted. See the Model Allowlist section above.

**`defaultConfig` NPE**
Model entries in the allowlist must include a `defaultConfig` block with at least `"accelerators": "cpu"`. A missing block causes a null pointer exception on model list load.

## See Also

- [README.md](README.md) — Project overview and features
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — System architecture documentation
- [model_allowlists/README.md](model_allowlists/README.md) — Model allowlist schema and workflow
- [Android/README.md](Android/README.md) — Android module structure guide

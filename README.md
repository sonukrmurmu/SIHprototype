# IndicT: 100% Offline AI Translation & Speech App

**IndicT** is a privacy-first, 100% offline Android application for bi-directional machine translation, speech recognition (ASR), and speech synthesis (TTS) targeting English and Indian languages, with dedicated support for **Santali (Ol Chiki & Devanagari scripts)** and **Hindi**.

Built for low-latency, edge-device AI execution, IndicT operates completely without internet connectivity, running quantized neural networks locally on device hardware.

---

## Key Features

- **100% Offline AI Execution**: All translation, speech recognition (ASR), and speech synthesis (TTS) models run locally on-device. Zero data leaves your phone.
- **Bi-Directional Multi-Language Support**:
  - English $\leftrightarrow$ Hindi
  - English $\leftrightarrow$ Santali (Ol Chiki)
  - Hindi $\leftrightarrow$ Santali (Dual-Engine Pivot Relay)
- **Santali Ol Chiki ⇄ Devanagari Script Toggle**:
  - Translates natively into Ol Chiki script.
  - Dynamically converts Ol Chiki script into phonetic Devanagari script for easy reading and accent-matched Indian voice synthesis.
- **Speech Recognition (ASR)**: Voice input support converting 16kHz PCM audio to text via Sherpa-ONNX.
- **Speech Synthesis (TTS)**: Offline voice output via Piper-ONNX engine using low-latency native `AudioTrack` PCM float audio streaming.
- **Instant Language Reverse (`⇄`)**: One-tap language and text swapping with 0ms model re-init delays.
- **Low RAM & High Speed Execution Modes**:
  - **Optimised Mode**: Single-Model Memory Pinning (~130 MB RAM) to protect budget 2GB RAM Android phones.
  - **High Speed Mode**: Dual-Engine pre-loading (~180 MB RAM total) for instant 0ms back-and-forth translation.
- **Fast-Path Lexicon Caching**: Local SQLite dictionary lookup for instant 0ms responses on common conversational phrases.

---

## App Interface Preview

- **Language Direction Selectors**: Dropdown selection with instant reverse button (`⇄`).
- **Speed / Beam Search Control**: 1 (Max Accuracy, Beam Size 5) to 5 (Max Speed, Greedy Search).
- **Script Toggle Button**: Toggle Santali output between **Ol Chiki ➔ Devn** and **Devn ➔ Ol Chiki**.
- **Voice Buttons**: MIC (Speech Input) and PLAY (Audio Output).

---

## Detailed Documentation Breakdown

For in-depth technical documentation, please refer to the following dedicated manuals:

1. 📖 **[Architecture Guide](architecture.md)**: Exhaustive walkthrough of the app's multi-layered system architecture, JNI bindings, Kotlin Coroutine orchestration, mutex guards, and audio pipelines.
2. ⚡ **[Optimization Guide](optimisation.md)**: Detailed breakdown of all performance optimizations (dynamic CPU performance core affinity micro-benchmarking, INT8 quantization, single-model RAM pinning, decoding length scaling, and transliteration algorithms).
3. 🤖 **[Model Specifications](model.md)**: Deep dive into all neural NMT, ASR, and TTS models, SentencePiece tokenizers, FLORES-200 language tags, and instructions on where to obtain/download them.

---

## Quick Start & Installation

### Prerequisites
- Android Studio Ladybug or newer
- Android SDK 24+ (Android 7.0 Nougat minimum)
- NDK (Side by side) & CMake 3.22.1+
- Java 17 / Kotlin 1.9+

### Building the Project
1. Clone or download the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/IndicT.git
   cd IndicT
   ```
2. Place required model assets inside `app/src/main/assets/` (see **[model.md](model.md)** for folder structure).
3. Build the debug APK via Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
4. Install on your Android device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## License & Credits
Built for Smart India Hackathon (SIH) with CTranslate2, SentencePiece, Sherpa-ONNX, and Piper-TTS.

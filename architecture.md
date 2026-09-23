# IndicT System Architecture Manual

This document provides a comprehensive, step-by-step architectural breakdown of the **IndicT** application, starting with high-level component organization and diving deep into native C++ dynamic bindings, Kotlin coroutines, mutex memory guards, and speech DSP pipelines.

---

## 1. High-Level System Architecture

IndicT follows a clean **Layered Architecture** designed for zero-latency, 100% offline edge execution:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                   UI LAYER                                      │
│                MainActivity.kt  •  activity_main.xml  •  Views                  │
└───────────────────────────────────────┬─────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              ORCHESTRATION LAYER                                │
│          EdgeAiOrchestrator.kt  •  Mutex RAM Guard  •  Coroutine IO Scope       │
└───────────────┬───────────────────────┼─────────────────────────┬───────────────┘
                │                       │                         │
                ▼                       ▼                         ▼
┌───────────────────────────────┐ ┌───────────────────┐ ┌─────────────────────────┐
│     NATIVE C++ NMT ENGINES    │ │    ASR ENGINE     │ │       TTS ENGINE        │
│ libsihtranslator.so           │ │ EphemeralAsrEngine│ │ EphemeralTtsEngine      │
│ libsih_indic_to_en.so         │ │ (Sherpa-ONNX)     │ │ (Piper-ONNX)            │
└───────────────────────────────┘ └───────────────────┘ └─────────────────────────┘
```

---

## 2. Layer 1: UI View Binding & Event Dispatcher (`MainActivity.kt`)

[`MainActivity.kt`](file:///home/sonukrmurmu/Documents/IndicT/app/src/main/java/com/example/indicT/MainActivity.kt) manages UI control states, captures user inputs, and delegates heavy AI processing to background IO threads.

### Primary UI Controls:
- **`sourceLangSpinner` & `targetLangSpinner`**: Select translation direction (English, Hindi, Santali).
- **`btnSwapLang` (`⇄`)**: Triggers `swapLanguagesAndText()` to reverse language dropdowns and output text in 0ms.
- **`optimisedCheckBox`**: Toggles between **Optimised Mode** (Single-Model Pinning ~130MB RAM) and **High Speed Mode** (Dual-Engine Pinning ~180MB RAM).
- **`btnToggleScript`**: Toggles Santali target translation between raw **Ol Chiki** script and phonetic **Devanagari** script.
- **`translateButton`**, **`micButton`**, **`btnSpeak`**: Triggers NMT translation, ASR speech recording, and TTS speech synthesis.

### UI Locking & Thread Safety (`setUiLoading`):
To prevent race conditions or crashes during model inference, `setUiLoading(true)` temporarily disables buttons and displays a indeterminate progress bar during asynchronous background operations.

---

## 3. Layer 2: Orchestration & Mutex Memory Guard (`EdgeAiOrchestrator.kt`)

[`EdgeAiOrchestrator.kt`](file:///home/sonukrmurmu/Documents/IndicT/app/src/main/java/com/example/indicT/EdgeAiOrchestrator.kt) serves as a thread-safe traffic controller that governs native memory allocation.

### 1. The RAM Guard Mutex (`ramLock`)
To protect devices from Out-Of-Memory (OOM) crashes, all heavy AI invocations (`runEnglishToIndic`, `runIndicToEnglish`, `runAsr`, `runTts`) execute inside `ramLock.withLock { }`. This guarantees that **only one heavy model performs active matrix multiplication at any given microsecond**.

### 2. Single-Model vs Dual-Engine Memory Pinning (`syncTextEngineMemoryState`)
- **Optimised Mode**: Pre-loads ONLY the engine required for the active direction (`eng-indic` for Eng $\rightarrow$ Indic, `indic-eng` for Indic $\rightarrow$ Eng). Unloads the unused engine, pinning the active model in native RAM (~130 MB).
- **High Speed Mode**: Pre-loads BOTH Engine 1 and Engine 2 permanently in native RAM (~180 MB total), enabling 0ms zero-latency back-and-forth translation.

---

## 4. Layer 3: Native C++ Dynamic Engine Layer (`native-lib.cpp` & `native-indic-en.cpp`)

The core machine translation engines are written in C++ using **CTranslate2** and **SentencePiece**, compiled into dynamic dynamic shared libraries (`libsihtranslator.so` and `libsih_indic_to_en.so`).

### JNI Function Declarations:
```kotlin
// Engine 1: English -> Indic
external fun initNativeTranslator(modelPath: String, spmPath: String): Int
external fun translateNativeText(text: String, srcLang: String, tgtLang: String, beamSize: Int): String
external fun unloadNativeTranslator()

// Engine 2: Indic -> English
external fun initNativeIndicToEng(modelPath: String, spmPath: String): Int
external fun translateIndicToEng(text: String, srcLang: String, beamSize: Int): String
external fun unloadNativeIndicToEng()
```

### Dual-Engine Pivot Relay (Inter-Indic Translation):
Translating between two Indian languages (e.g., `Hindi ➔ Santali`) uses a two-stage pivot relay:
$$\text{[ Hindi Input ]} \xrightarrow[\mathbf{\text{Engine 2 (indic-eng)}}]{\text{Stage 1}} \text{[ English Pivot ]} \xrightarrow[\mathbf{\text{Engine 1 (eng-indic)}}]{\text{Stage 2}} \text{[ Santali Output ]}$$

---

## 5. Layer 4: Speech Processing Pipelines (ASR & TTS)

### 1. Voice Input (Speech-to-Text ASR)
1. [`AudioRecorder.kt`](file:///home/sonukrmurmu/Documents/IndicT/app/src/main/java/com/example/indicT/AudioRecorder.kt) captures 16kHz 16-bit mono PCM audio from the microphone into a byte array.
2. [`EphemeralAsrEngine.kt`](file:///home/sonukrmurmu/Documents/IndicT/app/src/main/java/com/example/indicT/EphemeralAsrEngine.kt) boots `sherpa-onnx` (NeMo CTC acoustic model).
3. Audio floats are fed into `recognizer.decode(stream)`.
4. Transcribed text is returned, and native C++ stream pointers are **immediately released** (`recognizer.release()`).

### 2. Voice Output (Text-to-Speech TTS)
1. Input text (transliterated Santali Devanagari or Hindi) is passed to [`EphemeralTtsEngine.kt`](file:///home/sonukrmurmu/Documents/IndicT/app/src/main/java/com/example/indicT/EphemeralTtsEngine.kt).
2. Initializes `piper-onnx` VITS model (`hi_IN-pratham-medium.onnx`) with phonetic dictionaries (`espeak-ng-data`).
3. Synthesizes audio PCM float array. Native TTS pointers are immediately released (`tts.release()`).
4. Output audio stream is played directly via Android's low-latency `AudioTrack` API in `STREAM_MUSIC` mode.

---

## 6. Layer 5: Santali Script Transliteration Subsystem (`Transliterator.kt`)

[`Transliterator.kt`](file:///home/sonukrmurmu/Documents/IndicT/app/src/main/java/com/example/indicT/Transliterator.kt) handles rule-based phonetic transliteration from Santali Ol Chiki script into Devanagari script:

- **Gahla Tttu (`ᱹ`) Handling**: Transliterates `ᱟᱹ` and `ᱚᱹ` to `ऑ`/`ॉ`, and `ᱮᱹ` to `ॲ`/`ॅ`.
- **Aspirate Pairs (` con + ᱷ`)**: Maps aspirated pairs (`ᱠᱷ` $\rightarrow$ `ख`, `ᱜᱷ` $\rightarrow$ `घ`, `ᱛᱷ` $\rightarrow$ `थ`, `ᱫᱷ` $\rightarrow$ `ध`, `ᱵᱷ` $\rightarrow$ `भ`).
- **Post-Vowel Word-Final `ᱣ` (OW)**: Transliterates `ᱣ` following a vowel at word boundaries to vocalic **`ओ`** (e.g. `ᱠᱚᱨᱟᱣ` $\rightarrow$ `कोराओ`), ensuring clean Indian TTS speech.

# IndicT Model Specifications & Download Manual

This document details all artificial intelligence models, tokenizers, quantization formats, language tags, and download instructions for **IndicT**.

---

## 1. Neural Machine Translation (NMT) Models

IndicT uses quantized **CTranslate2** Transformer models trained on the **FLORES-200** parallel dataset.

### Engine 1: English ➔ Indic Languages (`eng-indic`)
- **Asset Folder**: `app/src/main/assets/eng-indic/`
- **Model Architecture**: CTranslate2 Transformer Sequence-to-Sequence NMT
- **Quantization**: INT8 / `ComputeType::AUTO` (~130 MB disk footprint)
- **Tokenization**: SentencePiece Processor (`model.SRC` & `model.TGT`)
- **Supported FLORES-200 Language Codes**:
  - `eng_Latn`: English
  - `hin_Deva`: Hindi (Devanagari script)
  - `sat_Olck`: Santali (Ol Chiki script)

### Engine 2: Indic Languages ➔ English (`indic-eng`)
- **Asset Folder**: `app/src/main/assets/indic-eng/`
- **Model Architecture**: CTranslate2 Transformer Sequence-to-Sequence NMT
- **Quantization**: INT8 / `ComputeType::AUTO` (~130 MB disk footprint)
- **Tokenization**: SentencePiece Processor (`model.SRC` & `model.TGT`)
- **Supported Target Code**: `eng_Latn` (English)

---

## 2. Automatic Speech Recognition (ASR) Model & Sherpa-ONNX Framework

IndicT leverages the **Sherpa-ONNX** framework (`com.k2fsa.sherpa.onnx`) for offline speech-to-text recognition and text-to-speech synthesis. Sherpa-ONNX is a high-performance, open-source C++ speech engine with Java JNI bindings built on top of ONNX Runtime.

### Sherpa-ONNX ASR Specifications (`asrenglish`)
- **Asset Directory**: `app/src/main/assets/asrenglish/`
- **Model Architecture**: NVIDIA NeMo EncDec CTC (Encoder-Decoder Connectionist Temporal Classification) Acoustic Model (`model.int8.onnx`).
- **Model Quantization**: INT8 ONNX Runtime (~40 MB disk size).
- **Vocabulary Tokenizer**: `tokens.txt` character/subword dictionary mapping.
- **Audio DSP Feature Extraction Configuration**:
  - **Sample Rate**: 16,000 Hz (16kHz mono 16-bit PCM WAV).
  - **Feature Dimension**: 80-channel log-mel filterbank energies (`featureDim = 80`).
  - **Decoding Strategy**: `greedy_search` for ultra-low latency.
  - **CPU Multi-Threading**: 4 worker threads allocated during the active transcription window.
- **Ephemeral RAM Management**: Audio waveform float arrays are accepted via `stream.acceptWaveform()`. Upon retrieving the decoded string result (`recognizer.getResult()`), `stream.release()` and `recognizer.release()` are invoked immediately to wipe native C++ pointers from RAM.

---

## 3. Text-to-Speech (TTS) Models & Sherpa-ONNX Piper VITS Engine

IndicT integrates Sherpa-ONNX's offline **Piper VITS** neural speech synthesis engine (`OfflineTts`).

### Sherpa-ONNX TTS Specifications (`tts` & `ttsenglish`)
- **Asset Directories**:
  - `app/src/main/assets/tts/` (Hindi Male Voice)
  - `app/src/main/assets/ttsenglish/` (English Female Voice)
- **Model Architecture**: VITS (Conditional Variational Autoencoder with Adversarial Learning for End-to-End Text-to-Speech).
- **Hindi Neural Voice**: `hi_IN-pratham-medium.onnx` (~60 MB INT8 ONNX).
- **English Neural Voice**: `en_US-lessac-medium.onnx` (~60 MB INT8 ONNX).
- **Grapheme-to-Phoneme (G2P) Engine**: Integrated `espeak-ng-data/` phonetic dictionary directory and `tokens.txt`.
- **Audio Output Waveform**: 22,050 Hz Mono 32-bit Floating-Point PCM array.
- **Native Memory Lifecycle**: Upon audio generation, `tts.release()` is called immediately to free native RAM pointers before audio streaming to Android's `AudioTrack`.

---

## 4. Asset Directory Layout Requirement

For the Android application to boot properly, place model assets in `app/src/main/assets/`:

```
app/src/main/assets/
├── dictionary.db                    # SQLite fast-path cache
├── eng-indic/                       # NMT Engine 1
│   ├── model.bin
│   ├── model.SRC
│   ├── model.TGT
│   └── shared_vocabulary.json
├── indic-eng/                       # NMT Engine 2
│   ├── model.bin
│   ├── model.SRC
│   ├── model.TGT
│   └── shared_vocabulary.json
├── asrenglish/                      # ASR Voice Input
│   ├── model.int8.onnx
│   └── tokens.txt
├── tts/                             # Hindi Speech Synthesis
│   ├── hi_IN-pratham-medium.onnx
│   ├── tokens.txt
│   └── espeak-ng-data/
└── ttsenglish/                      # English Speech Synthesis
    ├── en_US-lessac-medium.onnx
    ├── tokens.txt
    └── espeak-ng-data/
```

---

## 5. How a Normal User / Developer Can Obtain These Models

If you are cloning this repository and need to obtain or convert fresh model weights:

### 1. NMT Models (CTranslate2 & SentencePiece)
- **FLORES-200 / IndicTrans2 Checkpoints**: Download pre-trained Indic NMT weights from Hugging Face:
  - [IndicTrans2 Hugging Face Models](https://huggingface.co/ai4bharat)
- **Converting to CTranslate2 INT8**:
  Use the CTranslate2 converter CLI tool to quantize PyTorch checkpoints:
  ```bash
  ct2-transformers-converter --model ai4bharat/indictrans2-en-indic-1B \
                             --output_dir app/src/main/assets/eng-indic \
                             --quantization int8
  ```

### 2. ASR Model (Sherpa-ONNX)
- Download pre-built quantized sherpa-onnx models from GitHub Releases:
  - [Sherpa-ONNX Model Releases](https://github.com/k2-fsa/sherpa-onnx/releases)
  - Download `sherpa-onnx-nemo-ctc-en-2024-03-09.tar.bz2`, extract `model.int8.onnx` and `tokens.txt`, and copy to `app/src/main/assets/asrenglish/`.

### 3. TTS Models (Piper-TTS ONNX)
- Download pre-trained Piper ONNX voice models from Hugging Face / Piper GitHub:
  - [Piper Voice Repository](https://huggingface.co/rhasspy/piper-voices)
  - Download `hi_IN-pratham-medium.onnx` and `espeak-ng-data`, and copy to `app/src/main/assets/tts/`.

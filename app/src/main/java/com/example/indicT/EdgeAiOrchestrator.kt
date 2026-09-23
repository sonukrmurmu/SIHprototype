package com.example.indicT

import android.os.Process
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object EdgeAiOrchestrator {
    private const val TAG = "SIH_ORCHESTRATOR"
    private val ramLock = Mutex()

    fun syncTextEngineMemoryState(
        mainActivity: MainActivity,
        srcLang: String,
        tgtLang: String,
        engine1Path: String,
        engine2Path: String,
        isOptimisedMode: Boolean
    ) {
        val isEngToIndicNeeded = (tgtLang == "Hindi" || tgtLang == "Santali")
        val isIndicToEngNeeded = (srcLang == "Hindi" || srcLang == "Santali")

        if (isOptimisedMode) {
            // Optimised (Single-Model Pinning) Mode for 2GB RAM Devices:
            // Pre-loads ONLY the specific model needed for the selected direction (~130MB RAM footprint).
            // Unloads the unneeded engine to protect system memory while providing 0ms instant speed.
            if (isEngToIndicNeeded) {
                Log.i(TAG, "OPTIMISED MODE: Engine 1 (Eng-to-Indic) pinned in RAM (~130MB).")
                mainActivity.initNativeTranslator(engine1Path, "")
            } else {
                mainActivity.unloadNativeTranslator()
            }

            if (isIndicToEngNeeded) {
                Log.i(TAG, "OPTIMISED MODE: Engine 2 (Indic-to-Eng) pinned in RAM (~130MB).")
                mainActivity.initNativeIndicToEng(engine2Path, "")
            } else {
                mainActivity.unloadNativeIndicToEng()
            }
        } else {
            // Unchecked (High Speed) Mode: Both engines loaded permanently in RAM (~180MB total).
            // Enables instant zero-latency back-and-forth translation for ALL pairs (English <-> Indic & Indic <-> Indic).
            Log.i(TAG, "HIGH SPEED MODE: Both Engine 1 (Eng-to-Indic) and Engine 2 (Indic-to-Eng) pre-loaded in RAM permanently.")
            mainActivity.initNativeTranslator(engine1Path, "")
            mainActivity.initNativeIndicToEng(engine2Path, "")
        }
    }

    suspend fun runIndicToEnglish(
        mainActivity: MainActivity,
        text: String,
        srcLangCode: String,
        modelPath: String,
        beamSize: Int = 1,
        isOptimisedMode: Boolean = false
    ): String {
        return ramLock.withLock {
            var result = ""
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                Log.i(TAG, "RAM LOCK ACQUIRED: Checking/Loading Engine 2 (beam_size=$beamSize)")
                mainActivity.initNativeIndicToEng(modelPath, "")
                val rawOutput = mainActivity.translateIndicToEng(text, srcLangCode, beamSize)

                result = rawOutput
                    .replace(Regex("""<\s*br\s*/?>""", RegexOption.IGNORE_CASE), " ")
                    .replace("eng_Latn", "")
                    .replace("hin_Deva", "")
                    .replace("sat_Olck", "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                val garbage = charArrayOf(' ', ',', '?', '.', '।', '᱾')
                while (result.isNotEmpty() && garbage.contains(result.first())) {
                    result = result.substring(1).trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Engine 2 translation: ${e.message}")
            } finally {
                // Engine stays pinned in RAM for 0ms instant continuous translations!
                Log.i(TAG, "RAM LOCK RELEASED: Engine 2 execution complete.")
            }
            return@withLock result
        }
    }

    suspend fun runEnglishToIndic(
        mainActivity: MainActivity,
        text: String,
        tgtLangCode: String,
        modelPath: String,
        beamSize: Int = 1,
        isOptimisedMode: Boolean = false
    ): String {
        return ramLock.withLock {
            var finalCleanedText = ""
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                Log.i(TAG, "RAM LOCK ACQUIRED: Checking/Loading Engine 1 (beam_size=$beamSize)")
                mainActivity.initNativeTranslator(modelPath, "")

                val rawOutput = mainActivity.translateNativeText(text, "eng_Latn", tgtLangCode, beamSize)

                var cleaned = rawOutput
                    .replace(Regex("""<\s*br\s*/?>""", RegexOption.IGNORE_CASE), " ")
                    .replace("eng_Latn", "")
                    .replace("hin_Deva", "")
                    .replace("sat_Olck", "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                val garbageChars = charArrayOf(' ', ',', '?', '.', '।', '᱾')
                while (cleaned.isNotEmpty() && garbageChars.contains(cleaned.first())) {
                    cleaned = cleaned.substring(1).trim()
                }

                finalCleanedText = cleaned.ifBlank { "Translation Error" }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Engine 1 translation: ${e.message}")
            } finally {
                // Engine stays pinned in RAM for 0ms instant continuous translations!
                Log.i(TAG, "RAM LOCK RELEASED: Engine 1 execution complete.")
            }
            return@withLock finalCleanedText
        }
    }

    suspend fun runAsr(
        asrEngine: EphemeralAsrEngine,
        audioData: FloatArray,
        modelAssetPath: String,
        tokensAssetPath: String
    ): String {
        return ramLock.withLock {
            var transcribedText = ""
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                Log.i(TAG, "RAM LOCK ACQUIRED: Loading ASR Model ($modelAssetPath)")
                transcribedText = asrEngine.transcribe(audioData, modelAssetPath, tokensAssetPath)
            } finally {
                Log.i(TAG, "RAM LOCK RELEASED: ASR Model ($modelAssetPath) wiped.")
            }
            return@withLock transcribedText
        }
    }

    suspend fun runTts(ttsEngine: EphemeralTtsEngine, text: String): FloatArray {
        return ramLock.withLock {
            var audioSamples = FloatArray(0)
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
                Log.i(TAG, "RAM LOCK ACQUIRED: Loading TTS Model")
                val audioResult = ttsEngine.generateSpeech(text)
                audioSamples = audioResult.samples
            } finally {
                Log.i(TAG, "RAM LOCK RELEASED: TTS Model wiped.")
            }
            return@withLock audioSamples
        }
    }
}
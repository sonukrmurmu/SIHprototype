package com.example.indicT

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val TAG = "SIH_MAIN"

    private lateinit var statusText: TextView
    private lateinit var inputText: EditText
    private lateinit var translateButton: Button
    private lateinit var micButton: Button
    private lateinit var btnSpeak: Button
    private lateinit var hindiResult: TextView
    private lateinit var santaliResult: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sourceLangSpinner: Spinner
    private lateinit var targetLangSpinner: Spinner
    private lateinit var accuracySpinner: Spinner
    private lateinit var optimisedCheckBox: CheckBox
    private lateinit var btnToggleScript: Button
    private lateinit var btnSwapLang: TextView

    private lateinit var dictHelper: DictionaryDbHelper
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var asrEngine: EphemeralAsrEngine

    private var isEngineReady = false
    private var isRecording = false
    private var isProgrammaticSwap = false
    private var startupJob: Job? = null
    private var rawOlChikiTranslation: String = ""
    private var isShowingDevanagariScript: Boolean = false

    private lateinit var engine1Path: String
    private lateinit var engine2Path: String
    private lateinit var ttsHindiFolderPath: String
    private lateinit var ttsEnglishFolderPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        statusText = findViewById(R.id.statusText)
        inputText = findViewById(R.id.inputText)
        translateButton = findViewById(R.id.translateButton)
        micButton = findViewById(R.id.micButton)
        btnSpeak = findViewById(R.id.btn_speak)
        hindiResult = findViewById(R.id.hindiResult)
        santaliResult = findViewById(R.id.santaliResult)
        progressBar = findViewById(R.id.progressBar)
        sourceLangSpinner = findViewById(R.id.sourceLangSpinner)
        targetLangSpinner = findViewById(R.id.targetLangSpinner)
        accuracySpinner = findViewById(R.id.accuracySpinner)
        optimisedCheckBox = findViewById(R.id.optimisedCheckBox)
        btnToggleScript = findViewById(R.id.btnToggleScript)
        btnSwapLang = findViewById(R.id.btnSwapLang)

        translateButton.isEnabled = false
        micButton.isEnabled = false
        btnSpeak.isEnabled = false

        try {
            val santaliFont = ResourcesCompat.getFont(this, R.font.noto_sans_ol_chiki)
            santaliResult.typeface = santaliFont
        } catch (e: Exception) {
            e.printStackTrace()
        }

        dictHelper = DictionaryDbHelper(this)
        audioRecorder = AudioRecorder()
        asrEngine = EphemeralAsrEngine(assets)

        startAppInitialization()

        translateButton.setOnClickListener { executeTranslation() }
        micButton.setOnClickListener { toggleRecording() }
        btnSpeak.setOnClickListener { executeTts() }
        btnSwapLang.setOnClickListener { swapLanguagesAndText() }

        btnToggleScript.setOnClickListener {
            if (rawOlChikiTranslation.isBlank()) return@setOnClickListener
            isShowingDevanagariScript = !isShowingDevanagariScript
            if (isShowingDevanagariScript) {
                val devanagariText = Transliterator.olChikiToDevanagari(rawOlChikiTranslation)
                santaliResult.text = devanagariText
                santaliResult.typeface = android.graphics.Typeface.DEFAULT_BOLD
                btnToggleScript.text = "Devn ➔ Ol Chiki"
            } else {
                santaliResult.text = rawOlChikiTranslation
                try {
                    santaliResult.typeface = ResourcesCompat.getFont(this, R.font.noto_sans_ol_chiki)
                } catch (e: Exception) { e.printStackTrace() }
                btnToggleScript.text = "Ol Chiki ➔ Devn"
            }
        }

        optimisedCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isEngineReady) {
                val srcName = sourceLangSpinner.selectedItem.toString()
                val tgtName = targetLangSpinner.selectedItem.toString()
                setUiLoading(true, "Please wait...")
                CoroutineScope(Dispatchers.IO).launch {
                    EdgeAiOrchestrator.syncTextEngineMemoryState(this@MainActivity, srcName, tgtName, engine1Path, engine2Path, isChecked)
                    withContext(Dispatchers.Main) {
                        setUiLoading(false, "Ready")
                    }
                }
            }
        }

        val langSelectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isEngineReady && !isProgrammaticSwap && optimisedCheckBox.isChecked) {
                    val srcName = sourceLangSpinner.selectedItem.toString()
                    val tgtName = targetLangSpinner.selectedItem.toString()
                    setUiLoading(true, "Please wait...")
                    CoroutineScope(Dispatchers.IO).launch {
                        EdgeAiOrchestrator.syncTextEngineMemoryState(this@MainActivity, srcName, tgtName, engine1Path, engine2Path, optimisedCheckBox.isChecked)
                        withContext(Dispatchers.Main) {
                            setUiLoading(false, "Ready")
                        }
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        sourceLangSpinner.onItemSelectedListener = langSelectionListener
        targetLangSpinner.onItemSelectedListener = langSelectionListener
    }

    private fun swapLanguagesAndText() {
        val srcPos = sourceLangSpinner.selectedItemPosition
        val tgtPos = targetLangSpinner.selectedItemPosition
        if (srcPos == tgtPos) return

        val prevTargetLang = targetLangSpinner.selectedItem.toString()
        val oldTargetText = when (prevTargetLang) {
            "Santali" -> {
                if (rawOlChikiTranslation.isNotBlank()) {
                    rawOlChikiTranslation
                } else {
                    santaliResult.text.toString().takeIf { it != "..." && it != "Translating..." } ?: ""
                }
            }
            "Hindi" -> hindiResult.text.toString().takeIf { it != "..." && it != "Translating..." } ?: ""
            else -> ""
        }

        isProgrammaticSwap = true
        sourceLangSpinner.setSelection(tgtPos)
        targetLangSpinner.setSelection(srcPos)
        isProgrammaticSwap = false

        if (oldTargetText.isNotBlank()) {
            inputText.setText(oldTargetText)
            inputText.setSelection(oldTargetText.length)
        }

        santaliResult.text = "..."
        hindiResult.text = "..."
        rawOlChikiTranslation = ""
        isShowingDevanagariScript = false
        btnToggleScript.text = "Ol Chiki ➔ Devn"

        val newSrc = sourceLangSpinner.selectedItem.toString()
        val newTgt = targetLangSpinner.selectedItem.toString()

        if (isEngineReady && optimisedCheckBox.isChecked) {
            setUiLoading(true, "Please wait...")
            CoroutineScope(Dispatchers.IO).launch {
                EdgeAiOrchestrator.syncTextEngineMemoryState(
                    this@MainActivity,
                    newSrc,
                    newTgt,
                    engine1Path,
                    engine2Path,
                    optimisedCheckBox.isChecked
                )
                withContext(Dispatchers.Main) {
                    setUiLoading(false, "Ready")
                }
            }
        } else {
            statusText.text = "Ready"
        }
    }

    private fun setUiLoading(isLoading: Boolean, message: String = "") {
        runOnUiThread {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            translateButton.isEnabled = !isLoading && isEngineReady
            micButton.isEnabled = !isLoading && isEngineReady && !isRecording
            btnSpeak.isEnabled = !isLoading && isEngineReady
            sourceLangSpinner.isEnabled = !isLoading
            targetLangSpinner.isEnabled = !isLoading
            accuracySpinner.isEnabled = !isLoading
            optimisedCheckBox.isEnabled = !isLoading
            btnToggleScript.isEnabled = !isLoading
            if (message.isNotBlank()) {
                statusText.text = message
            }
        }
    }

    private fun startAppInitialization() {
        setUiLoading(true, "Please wait...")
        startupJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                engine1Path = copyModelAsset("eng-indic")
                engine2Path = copyModelAsset("indic-eng")
                ttsHindiFolderPath = copyModelAsset("tts")
                ttsEnglishFolderPath = copyModelAsset("ttsenglish")

                val srcName = sourceLangSpinner.selectedItem?.toString() ?: "English"
                val tgtName = targetLangSpinner.selectedItem?.toString() ?: "Santali"
                EdgeAiOrchestrator.syncTextEngineMemoryState(this@MainActivity, srcName, tgtName, engine1Path, engine2Path, optimisedCheckBox.isChecked)

                withContext(Dispatchers.Main) {
                    isEngineReady = true
                    setUiLoading(false, "Ready")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setUiLoading(false, "Boot Error: ${e.message}")
                }
            }
        }
    }

    private fun executeTranslation() {
        val textToTranslate = inputText.text.toString().trim()
        if (textToTranslate.isBlank() || !isEngineReady) return

        setUiLoading(true, "Please wait...")
        hindiResult.text = "Translating..."
        santaliResult.text = "..."

        val srcName = sourceLangSpinner.selectedItem.toString()
        val tgtName = targetLangSpinner.selectedItem.toString()
        val srcCode = getFloresCode(srcName)
        val tgtCode = getFloresCode(tgtName)
        
        val selectedSpeed = accuracySpinner.selectedItem?.toString()?.toIntOrNull() ?: 5
        val selectedBeamSize = when (selectedSpeed) {
            5 -> 1 // Speed 5 (Fastest) -> C++ beam_size 1 (greedy search, ~0.5s)
            4 -> 2 // Speed 4 -> C++ beam_size 2
            3 -> 3 // Speed 3 -> C++ beam_size 3
            2 -> 4 // Speed 2 -> C++ beam_size 4
            1 -> 5 // Speed 1 (Max Accuracy) -> C++ beam_size 5 (deep search, ~3.5s)
            else -> 1
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Sync text translation engine RAM states on I/O thread
                EdgeAiOrchestrator.syncTextEngineMemoryState(this@MainActivity, srcName, tgtName, engine1Path, engine2Path, optimisedCheckBox.isChecked)

                var intermediateLog = "Direct Translation (Speed $selectedSpeed)"
                val finalTranslation: String

                val localMatch = if (srcCode == "hin_Deva" && tgtCode == "sat_Olck") dictHelper.lookup(textToTranslate) else null

                if (localMatch != null) {
                    finalTranslation = localMatch
                    intermediateLog = "Found in Local SQLite Dictionary"
                } else if (srcCode == tgtCode) {
                    finalTranslation = textToTranslate
                    intermediateLog = "Same language selected."
                } else if (srcCode == "eng_Latn") {
                    finalTranslation = EdgeAiOrchestrator.runEnglishToIndic(this@MainActivity, textToTranslate, tgtCode, engine1Path, selectedBeamSize, optimisedCheckBox.isChecked)
                } else if (tgtCode == "eng_Latn") {
                    finalTranslation = EdgeAiOrchestrator.runIndicToEnglish(this@MainActivity, textToTranslate, srcCode, engine2Path, selectedBeamSize, optimisedCheckBox.isChecked)
                } else {
                    intermediateLog = "Dual-Engine Relay Used (Speed $selectedSpeed)"
                    val englishPivot = EdgeAiOrchestrator.runIndicToEnglish(this@MainActivity, textToTranslate, srcCode, engine2Path, selectedBeamSize, optimisedCheckBox.isChecked)

                    if (englishPivot.isBlank()) {
                        finalTranslation = "Pivot Error"
                    } else {
                        finalTranslation = EdgeAiOrchestrator.runEnglishToIndic(this@MainActivity, englishPivot, tgtCode, engine1Path, selectedBeamSize, optimisedCheckBox.isChecked)
                    }
                }

                withContext(Dispatchers.Main) {
                    hindiResult.text = intermediateLog
                    if (tgtCode == "sat_Olck") {
                        rawOlChikiTranslation = finalTranslation
                        isShowingDevanagariScript = false
                        santaliResult.text = rawOlChikiTranslation
                        try {
                            santaliResult.typeface = ResourcesCompat.getFont(this@MainActivity, R.font.noto_sans_ol_chiki)
                        } catch (e: Exception) { e.printStackTrace() }
                        btnToggleScript.visibility = View.VISIBLE
                        btnToggleScript.text = "Ol Chiki ➔ Devn"
                    } else {
                        rawOlChikiTranslation = ""
                        santaliResult.text = finalTranslation
                        santaliResult.typeface = android.graphics.Typeface.DEFAULT_BOLD
                        btnToggleScript.visibility = View.GONE
                    }
                    setUiLoading(false, "Translation Complete!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    santaliResult.text = "Error: ${e.message}"
                    setUiLoading(false, "Translation Error: ${e.message}")
                }
            }
        }
    }

    private fun toggleRecording() {
        if (!isRecording) {
            isRecording = true
            micButton.text = "⏹ Stop"
            statusText.text = "Listening..."
            audioRecorder.start()
            translateButton.isEnabled = false
            btnSpeak.isEnabled = false
            sourceLangSpinner.isEnabled = false
            targetLangSpinner.isEnabled = false
            accuracySpinner.isEnabled = false
        } else {
            isRecording = false
            micButton.text = "🎤 Mic"
            setUiLoading(true, "Loading Speech Model into RAM... Please wait")
            val audio = audioRecorder.stop()

            val selectedSourceLang = sourceLangSpinner.selectedItem.toString()
            val (modelAssetPath, tokensAssetPath) = when (selectedSourceLang) {
                "English" -> Pair("asrenglish/model.int8.onnx", "asrenglish/tokens.txt")
                "Hindi" -> Pair("asrhindi/model.int8.onnx", "asrhindi/tokens.txt")
                "Santali" -> Pair("voice-santali/model.int8.onnx", "voice-santali/tokens.txt")
                else -> Pair("asrenglish/model.int8.onnx", "asrenglish/tokens.txt")
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val transcribedText = EdgeAiOrchestrator.runAsr(asrEngine, audio, modelAssetPath, tokensAssetPath)
                    withContext(Dispatchers.Main) {
                        if (transcribedText.isNotBlank()) {
                            inputText.setText(transcribedText)
                            setUiLoading(false, "Speech recognized ($selectedSourceLang)")
                        } else {
                            setUiLoading(false, "Could not recognize speech")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        setUiLoading(false, "ASR Error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun executeTts() {
        val selectedTargetLang = targetLangSpinner.selectedItem.toString()
        val targetCode = getFloresCode(selectedTargetLang)
        var textToSpeak = santaliResult.text.toString().trim()

        if (textToSpeak.isBlank() || textToSpeak == "..." || textToSpeak.contains("Translating")) return

        setUiLoading(true, "Please wait...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ttsEngine: EphemeralTtsEngine
                if (selectedTargetLang == "English") {
                    ttsEngine = EphemeralTtsEngine(ttsEnglishFolderPath, "en_US-lessac-medium.onnx")
                } else {
                    if (targetCode == "sat_Olck" || selectedTargetLang == "Santali") {
                        textToSpeak = Transliterator.olChikiToDevanagari(textToSpeak)
                    }
                    ttsEngine = EphemeralTtsEngine(ttsHindiFolderPath, "hi_IN-pratham-medium.onnx")
                }

                val audioSamples = EdgeAiOrchestrator.runTts(ttsEngine, textToSpeak)
                playAudio(audioSamples, 22050)

                withContext(Dispatchers.Main) {
                    setUiLoading(false, "Audio finished.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setUiLoading(false, "TTS Error: ${e.message}")
                }
            }
        }
    }

    private fun playAudio(audioSamples: FloatArray, sampleRate: Int) {
        if (audioSamples.isEmpty()) return
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(Math.max(minBufferSize, audioSamples.size * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack.play()
        audioTrack.write(audioSamples, 0, audioSamples.size, AudioTrack.WRITE_BLOCKING)
        val playDurationMs = (audioSamples.size.toDouble() / sampleRate.toDouble() * 1000.0).toLong()
        Thread.sleep(playDurationMs + 300)
        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (ignored: Exception) {}
    }

    private fun getFloresCode(language: String): String {
        return when (language) {
            "English" -> "eng_Latn"
            "Hindi" -> "hin_Deva"
            "Santali" -> "sat_Olck"
            else -> "eng_Latn"
        }
    }

    private fun copyModelAsset(folderName: String): String {
        val destDir = File(filesDir, folderName)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        copyAssetFolder(folderName, destDir)
        return destDir.absolutePath
    }

    private fun copyAssetFolder(assetPath: String, destDir: File) {
        val files = assets.list(assetPath) ?: return
        if (!destDir.exists()) destDir.mkdirs()

        for (filename in files) {
            val subAssetPath = "$assetPath/$filename"
            val destFile = File(destDir, filename)

            val subFiles = assets.list(subAssetPath)
            if (!subFiles.isNullOrEmpty()) {
                copyAssetFolder(subAssetPath, destFile)
            } else if (!destFile.exists() || destFile.length() == 0L) {
                assets.open(subAssetPath).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isRecording) { audioRecorder.stop(); isRecording = false }
        startupJob?.cancel()
        unloadNativeTranslator()
        unloadNativeIndicToEng()
        super.onDestroy()
    }

    external fun initNativeTranslator(modelDir: String, spmPath: String): Int
    external fun translateNativeText(text: String, srcLang: String, tgtLang: String, beamSize: Int): String
    external fun unloadNativeTranslator()

    external fun initNativeIndicToEng(modelDir: String, spmPath: String): Int
    external fun translateIndicToEng(text: String, srcLang: String, beamSize: Int): String
    external fun unloadNativeIndicToEng()

    companion object {
        init {
            System.loadLibrary("sihtranslator")
            System.loadLibrary("sih_indic_to_en")
        }
    }
}
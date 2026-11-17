package com.heypapu.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class BilingualVoiceEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _activeLocaleTag = MutableStateFlow("en-IN")
    val activeLocaleTag: StateFlow<String> = _activeLocaleTag.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale("en", "IN")
                tts?.setSpeechRate(0.98f)
            }
        }
    }

    fun setRecognitionLanguage(localeTag: String) {
        _activeLocaleTag.value = localeTag // "en-IN" or "ta-IN"
    }

    fun toggleLanguage(): String {
        val next = if (_activeLocaleTag.value == "en-IN") "ta-IN" else "en-IN"
        _activeLocaleTag.value = next
        return next
    }

    /**
     * Automatically detects Tamil Unicode block (U+0B80..U+0BFF) so Tamil text is spoken
     * in natural Tamil voice and English text in Indian English voice.
     */
    fun speak(text: String, forceLocaleTag: String? = null) {
        if (!ttsReady || text.isBlank()) return
        val containsTamil = text.any { it in '\u0B80'..'\u0BFF' }
        val targetLocale = when {
            forceLocaleTag == "ta-IN" || containsTamil -> Locale("ta", "IN")
            else -> Locale("en", "IN")
        }
        val availability = tts?.isLanguageAvailable(targetLocale) ?: TextToSpeech.LANG_MISSING_DATA
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            tts?.language = targetLocale
        } else {
            tts?.language = Locale.US
        }
        _isSpeaking.value = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "papu_utterance_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun startListening(onCommandRecognized: (String) -> Unit) {
        _voiceError.value = null
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceError.value = "Speech recognition service unavailable on this device."
            return
        }
        stopListening()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    _voiceError.value = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic and try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        else -> "Voice input paused (code $error). Try again."
                    }
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    if (best.isNotEmpty()) {
                        _recognizedText.value = best
                        onCommandRecognized(best)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        _recognizedText.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, _activeLocaleTag.value)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        _isListening.value = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun shutdown() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
    }
}

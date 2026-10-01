package com.montanhajr.bereanbible

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SpeechToTextEngine {
    fun start(language: AppLanguage = AppLanguage.PORTUGUESE)
    fun stop()
    val transcript: StateFlow<String>
    val partialTranscript: StateFlow<String>
    val isListening: StateFlow<Boolean>
    val lastError: StateFlow<String?>
}

/** Fonte de transcrição "fake": o desenvolvedor digita o texto em vez de falar. */
class FakeSpeechToTextEngine : SpeechToTextEngine {
    private val _transcript = MutableStateFlow("")
    override val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    override val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override fun start(language: AppLanguage) {
        _isListening.value = true
    }

    override fun stop() {
        _isListening.value = false
    }

    fun submit(text: String) {
        _partialTranscript.value = text
        _transcript.value = text
    }
}

/**
 * Implementação real usando o reconhecedor de fala nativo do Android
 * (android.speech.SpeechRecognizer). Reinicia a escuta a cada resultado/erro para
 * aproximar um comportamento contínuo. Isso é um substituto de PROTOTIPAGEM para o
 * Whisper local recomendado na especificação (seção 5) — tem gaps pequenos entre
 * frases e depende de rede em alguns aparelhos/fabricantes.
 */
class AndroidSpeechRecognizerEngine(private val context: Context) : SpeechToTextEngine {

    private val TAG = "STT_Engine"
    private var recognizer: SpeechRecognizer? = null
    private var currentLanguage: AppLanguage = AppLanguage.PORTUGUESE

    private val _transcript = MutableStateFlow("")
    override val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    override val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            Log.e(TAG, "onError: $error")
            // Se o erro for de áudio ou ocupado, espera um pouco; senão reinicia logo.
            if (_isListening.value) {
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_AUDIO) {
                    recognizer?.cancel()
                }
                restart()
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            Log.d(TAG, "onResults: $text")
            if (!text.isNullOrBlank()) {
                _partialTranscript.value = text
                _transcript.value = text
            }
            if (_isListening.value) restart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            Log.d(TAG, "onPartialResults: $text")
            if (!text.isNullOrBlank()) {
                _partialTranscript.value = text
                _transcript.value = text
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun start(language: AppLanguage) {
        Log.d(TAG, "start($language)")
        currentLanguage = language
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available")
            _lastError.value = "Reconhecimento de fala do sistema indisponível"
            return
        }
        _lastError.value = null
        _isListening.value = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
        }
        restart()
    }

    override fun stop() {
        Log.d(TAG, "stop()")
        _isListening.value = false
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    private fun restart() {
        Log.d(TAG, "restart()")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage.toLocaleTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            // Tenta manter o reconhecimento ativo pelo maior tempo possível e com maior sensibilidade
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000)
            
            // Solicita que o sistema não use filtros de ruído agressivos que possam cortar vozes distantes
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }
        recognizer?.startListening(intent)
    }
}

package com.montanhajr.bereanbible

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Microfone contínuo + Silero VAD + Whisper offline (sherpa-onnx).
 * Só emite texto quando o VAD fecha um segmento — o detector já espera
 * resultados "fechados", então não há mudança na lógica de detecção.
 */
class SherpaWhisperEngine(
    private val modelStore: WhisperModelStore,
    private val modelKind: SpeechEngineKind,
) : SpeechToTextEngine {

    private val tag = "STT_Whisper"

    private val _transcript = MutableStateFlow("")
    override val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    override val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val keepRunning = AtomicBoolean(false)
    private var worker: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var vad: Vad? = null
    private var recognizer: OfflineRecognizer? = null
    private val decodeLock = Any()

    override fun start(language: AppLanguage) {
        Log.d(tag, "start($language, $modelKind)")
        stopInternal(join = true)
        _lastError.value = null
        _transcript.value = ""
        _partialTranscript.value = ""
        if (!modelStore.isReady(modelKind)) {
            _lastError.value = "Modelo Whisper não baixado"
            _isListening.value = false
            return
        }
        keepRunning.set(true)
        _isListening.value = true
        worker = thread(name = "sherpa-whisper") {
            try {
                runLoop(language)
            } catch (e: Exception) {
                Log.e(tag, "engine failed", e)
                _lastError.value = e.message ?: e.javaClass.simpleName
            } finally {
                releaseNative()
                _isListening.value = false
            }
        }
    }

    override fun stop() {
        Log.d(tag, "stop()")
        stopInternal(join = true)
    }

    private fun stopInternal(join: Boolean) {
        keepRunning.set(false)
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        if (join) {
            worker?.join(2_000)
        }
        worker = null
        releaseMic()
        releaseNative()
        _isListening.value = false
    }

    private fun runLoop(language: AppLanguage) {
        vad = Vad(
            assetManager = null,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = modelStore.vadPath(),
                    threshold = 0.5f,
                    minSilenceDuration = 0.8f,
                    minSpeechDuration = 0.25f,
                    windowSize = WINDOW_SIZE,
                    maxSpeechDuration = 20.0f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
            ),
        )
        recognizer = OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = modelStore.encoderPath(modelKind),
                        decoder = modelStore.decoderPath(modelKind),
                        language = modelKind.whisperLanguageCode(language),
                        task = "transcribe",
                        tailPaddings = 1000,
                    ),
                    tokens = modelStore.tokensPath(modelKind),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = "whisper",
                ),
            ),
        )

        if (!initMicrophone()) {
            _lastError.value = "Não foi possível abrir o microfone"
            return
        }
        audioRecord?.startRecording()
        val buffer = ShortArray(WINDOW_SIZE)
        val localVad = vad ?: return
        while (keepRunning.get()) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (read <= 0) continue
            val samples = FloatArray(read) { buffer[it] / 32768.0f }
            localVad.acceptWaveform(samples)
            while (!localVad.empty()) {
                val segment = localVad.front()
                localVad.pop()
                val text = decodeSegment(segment.samples)
                if (text.isNotBlank()) {
                    _partialTranscript.value = text
                    _transcript.value = text
                }
            }
        }
        localVad.flush()
        while (!localVad.empty()) {
            val segment = localVad.front()
            localVad.pop()
            val text = decodeSegment(segment.samples)
            if (text.isNotBlank()) {
                _partialTranscript.value = text
                _transcript.value = text
            }
        }
    }

    private fun decodeSegment(samples: FloatArray): String {
        val rec = recognizer ?: return ""
        return synchronized(decodeLock) {
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                rec.decode(stream)
                rec.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initMicrophone(): Boolean {
        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) return false
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBytes * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        audioRecord = record
        return true
    }

    private fun releaseMic() {
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    private fun releaseNative() {
        try {
            vad?.release()
        } catch (_: Exception) {
        }
        vad = null
        try {
            recognizer?.release()
        } catch (_: Exception) {
        }
        recognizer = null
        releaseMic()
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val WINDOW_SIZE = 512
    }
}

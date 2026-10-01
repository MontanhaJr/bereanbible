package com.montanhajr.bereanbible

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class RemoteModelFile(
    val fileName: String,
    val url: String,
    val expectedBytes: Long,
)

data class WhisperModelSpec(
    val kind: SpeechEngineKind,
    val displaySizeMb: Int,
    val files: List<RemoteModelFile>,
)

data class ModelDownloadProgress(
    val kind: SpeechEngineKind? = null,
    val inProgress: Boolean = false,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

object WhisperModelCatalog {
    // Modelos multilíngues (sem sufixo .en). Mesmo arquivo cobre pt/es/en + ~90 línguas.
    val tiny = spec(
        kind = SpeechEngineKind.WHISPER_TINY,
        prefix = "tiny",
        displaySizeMb = 99,
        encoderBytes = 12_937_772,
        decoderBytes = 89_855_401,
        tokensBytes = 816_730,
    )
    val base = spec(
        kind = SpeechEngineKind.WHISPER_BASE,
        prefix = "base",
        displaySizeMb = 153,
        encoderBytes = 29_120_534,
        decoderBytes = 130_672_026,
        tokensBytes = 816_730,
    )
    val small = spec(
        kind = SpeechEngineKind.WHISPER_SMALL,
        prefix = "small",
        displaySizeMb = 358,
        encoderBytes = 112_442_483,
        decoderBytes = 262_226_114,
        tokensBytes = 816_730,
    )

    val all: List<WhisperModelSpec> = listOf(tiny, base, small)

    val vad = RemoteModelFile(
        fileName = "silero_vad.onnx",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        expectedBytes = 0L,
    )

    fun specFor(kind: SpeechEngineKind): WhisperModelSpec? = when (kind) {
        SpeechEngineKind.WHISPER_TINY -> tiny
        SpeechEngineKind.WHISPER_BASE -> base
        SpeechEngineKind.WHISPER_SMALL -> small
        SpeechEngineKind.ANDROID_SYSTEM -> null
    }

    private fun spec(
        kind: SpeechEngineKind,
        prefix: String,
        displaySizeMb: Int,
        encoderBytes: Long,
        decoderBytes: Long,
        tokensBytes: Long,
    ): WhisperModelSpec {
        val repo = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$prefix/resolve/main"
        return WhisperModelSpec(
            kind = kind,
            displaySizeMb = displaySizeMb,
            files = listOf(
                RemoteModelFile("$prefix-encoder.int8.onnx", "$repo/$prefix-encoder.int8.onnx", encoderBytes),
                RemoteModelFile("$prefix-decoder.int8.onnx", "$repo/$prefix-decoder.int8.onnx", decoderBytes),
                RemoteModelFile("$prefix-tokens.txt", "$repo/$prefix-tokens.txt", tokensBytes),
            ),
        )
    }
}

class WhisperModelStore(context: Context) {
    private val root = File(context.filesDir, "sherpa-onnx").apply { mkdirs() }
    private val downloadMutex = Mutex()

    private val _progress = MutableStateFlow(ModelDownloadProgress())
    val progress: StateFlow<ModelDownloadProgress> = _progress.asStateFlow()

    private val _ready = MutableStateFlow(scanReady())
    val ready: StateFlow<Set<SpeechEngineKind>> = _ready.asStateFlow()

    fun isReady(kind: SpeechEngineKind): Boolean {
        if (!kind.isWhisper) return true
        return kind in _ready.value
    }

    fun encoderPath(kind: SpeechEngineKind): String = fileFor(kind, "encoder").absolutePath
    fun decoderPath(kind: SpeechEngineKind): String = fileFor(kind, "decoder").absolutePath
    fun tokensPath(kind: SpeechEngineKind): String = fileFor(kind, "tokens").absolutePath
    fun vadPath(): String = File(root, WhisperModelCatalog.vad.fileName).absolutePath

    fun refresh() {
        _ready.value = scanReady()
    }

    suspend fun download(kind: SpeechEngineKind) {
        val spec = WhisperModelCatalog.specFor(kind) ?: return
        downloadMutex.withLock {
            try {
                val files = spec.files + WhisperModelCatalog.vad
                val total = files.sumOf { if (it.expectedBytes > 0) it.expectedBytes else 2_300_000L }
                _progress.value = ModelDownloadProgress(
                    kind = kind,
                    inProgress = true,
                    bytesDownloaded = 0,
                    totalBytes = total,
                )
                var completed = 0L
                for (file in files) {
                    val dest = File(root, file.fileName)
                    if (fileLooksComplete(dest, file.expectedBytes)) {
                        completed += if (file.expectedBytes > 0) file.expectedBytes else dest.length()
                        _progress.value = _progress.value.copy(bytesDownloaded = completed)
                        continue
                    }
                    downloadFile(file, dest) { chunk ->
                        completed += chunk
                        _progress.value = _progress.value.copy(bytesDownloaded = completed)
                    }
                }
                refresh()
                _progress.value = ModelDownloadProgress()
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
                _progress.value = ModelDownloadProgress(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun delete(kind: SpeechEngineKind) {
        val spec = WhisperModelCatalog.specFor(kind) ?: return
        spec.files.forEach { File(root, it.fileName).delete() }
        val stillNeeded = WhisperModelCatalog.all.any { other ->
            other.kind != kind && other.files.all { File(root, it.fileName).exists() }
        }
        if (!stillNeeded) {
            File(root, WhisperModelCatalog.vad.fileName).delete()
        }
        refresh()
    }

    private fun fileFor(kind: SpeechEngineKind, part: String): File {
        val spec = WhisperModelCatalog.specFor(kind) ?: error("not a whisper model")
        val name = when (part) {
            "encoder" -> spec.files[0].fileName
            "decoder" -> spec.files[1].fileName
            else -> spec.files[2].fileName
        }
        return File(root, name)
    }

    private fun scanReady(): Set<SpeechEngineKind> {
        val vadOk = fileLooksComplete(File(root, WhisperModelCatalog.vad.fileName), WhisperModelCatalog.vad.expectedBytes)
        if (!vadOk) return emptySet()
        return WhisperModelCatalog.all.filter { spec ->
            spec.files.all { fileLooksComplete(File(root, it.fileName), it.expectedBytes) }
        }.map { it.kind }.toSet()
    }

    private fun fileLooksComplete(file: File, expectedBytes: Long): Boolean {
        if (!file.exists()) return false
        return if (expectedBytes > 0) file.length() == expectedBytes else file.length() > 50_000
    }

    private suspend fun downloadFile(
        remote: RemoteModelFile,
        dest: File,
        onBytes: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        tmp.delete()
        val connection = (URL(remote.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "BibliaBereana/1.0 (sherpa-onnx whisper)")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code ao baixar ${remote.fileName}")
            }
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            }
            if (remote.expectedBytes > 0 && tmp.length() != remote.expectedBytes) {
                tmp.delete()
                throw IllegalStateException("Tamanho inesperado de ${remote.fileName}")
            }
            if (remote.expectedBytes <= 0 && tmp.length() < 50_000) {
                tmp.delete()
                throw IllegalStateException("Arquivo VAD incompleto")
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "WhisperModels"
    }
}

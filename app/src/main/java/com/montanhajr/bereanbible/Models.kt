package com.montanhajr.bereanbible

enum class Testament { OLD, NEW }

enum class AppLanguage { 
    PORTUGUESE, ENGLISH, SPANISH;
    
    fun toLocaleTag(): String = when (this) {
        PORTUGUESE -> "pt-BR"
        SPANISH -> "es-ES"
        ENGLISH -> "en-US"
    }
}

enum class NavigationMode { CONFIRM_BEFORE_OPEN, AUTO_OPEN }

/**
 * Motor de STT escolhido nas configurações. O reconhecedor nativo do Android
 * permanece disponível; as três opções Whisper usam o mesmo runtime sherpa-onnx
 * com modelos multilíngues diferentes (tiny / base / small, int8, sem sufixo .en).
 */
enum class SpeechEngineKind {
    ANDROID_SYSTEM,
    WHISPER_TINY,
    WHISPER_BASE,
    WHISPER_SMALL;

    val isWhisper: Boolean get() = this != ANDROID_SYSTEM

    fun whisperLanguageCode(language: AppLanguage): String = when (language) {
        AppLanguage.PORTUGUESE -> "pt"
        AppLanguage.SPANISH -> "es"
        AppLanguage.ENGLISH -> "en"
    }
}

/**
 * Máquina de estados da detecção (ver seção 21 da especificação).
 * Nem todos os estados são usados de forma explícita neste MVP mínimo
 * (ACCEPTED/IGNORED são transitórios), mas ficam declarados para orientar
 * uma implementação futura mais rica.
 */
enum class DetectionState {
    IDLE, LISTENING, TRANSCRIBING, POSSIBLE_REFERENCE,
    VALIDATED_REFERENCE, SUGGESTED, ACCEPTED, IGNORED
}

data class BibleBook(
    val id: String,
    val name: String,
    val chapters: Int,
    val testament: Testament,
    val names: Map<AppLanguage, String> = emptyMap()
)

data class BibleReference(
    val bookId: String,
    val chapter: Int,
    val startVerse: Int,
    val endVerse: Int? = null
)

data class DetectedReference(
    val reference: BibleReference,
    val originalText: String,
    val confidence: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

data class SermonSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val detections: List<DetectedReference> = emptyList()
)

data class BibleVerse(
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

data class SermonHistoryEntry(
    val reference: BibleReference,
    val displayText: String,
    val accepted: Boolean,
    val timestampMs: Long
)

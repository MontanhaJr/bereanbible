package com.montanhajr.bereanbible

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BibleViewModel(application: Application) : AndroidViewModel(application) {

    val settings = SettingsRepository(application)
    val repository: BibleRepository = MultiLanguageBibleRepository(application, settings)
    val sessionManager = SermonSessionManager()
    val whisperModels = WhisperModelStore(application)

    private var sttEngine: SpeechToTextEngine? = null
    private val fakeEngine = FakeSpeechToTextEngine()
    private var listeningJobs: Job? = null

    private val _useFakeStt = MutableStateFlow(false) // Alterado para false por padrão para usar o microfone
    val useFakeStt: StateFlow<Boolean> = _useFakeStt.asStateFlow()

    private val _currentBookId = MutableStateFlow("JHN")
    val currentBookId: StateFlow<String> = _currentBookId.asStateFlow()

    private val _currentChapter = MutableStateFlow(3)
    val currentChapter: StateFlow<Int> = _currentChapter.asStateFlow()

    private val _targetVerse = MutableStateFlow<Int?>(1)
    val targetVerse: StateFlow<Int?> = _targetVerse.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _detectionState = MutableStateFlow(DetectionState.IDLE)
    val detectionState: StateFlow<DetectionState> = _detectionState.asStateFlow()

    private val _suggestion = MutableStateFlow<DetectedReference?>(null)
    val suggestion: StateFlow<DetectedReference?> = _suggestion.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<BibleVerse>>(emptyList())
    val searchResults: StateFlow<List<BibleVerse>> = _searchResults.asStateFlow()

    private val _lastHeard = MutableStateFlow("")
    val lastHeard: StateFlow<String> = _lastHeard.asStateFlow()

    private val _sttError = MutableStateFlow<String?>(null)
    val sttError: StateFlow<String?> = _sttError.asStateFlow()

    private var previousTranscript = ""

    fun openReference(bookId: String, chapter: Int, verse: Int? = 1) {
        _currentBookId.value = bookId
        _currentChapter.value = chapter
        _targetVerse.value = verse
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _searchResults.value = if (query.length >= 3) repository.searchText(query) else emptyList()
    }

    fun setUseFakeStt(value: Boolean) {
        _useFakeStt.value = value
    }

    fun downloadWhisperModel(kind: SpeechEngineKind) {
        viewModelScope.launch { whisperModels.download(kind) }
    }

    fun deleteWhisperModel(kind: SpeechEngineKind) {
        whisperModels.delete(kind)
    }

    fun startListening() {
        sttEngine?.stop()
        listeningJobs?.cancel()

        sessionManager.startSession()
        previousTranscript = ""
        _sttError.value = null
        _isListening.value = true
        _detectionState.value = DetectionState.LISTENING

        val selectedEngine = settings.speechEngine.value
        val engine: SpeechToTextEngine = when {
            _useFakeStt.value -> fakeEngine
            selectedEngine.isWhisper -> {
                if (!whisperModels.isReady(selectedEngine)) {
                    _sttError.value = "Baixe o modelo Whisper nas configurações antes de escutar"
                    _isListening.value = false
                    _detectionState.value = DetectionState.IDLE
                    return
                }
                SherpaWhisperEngine(whisperModels, selectedEngine)
            }
            else -> AndroidSpeechRecognizerEngine(getApplication())
        }
        sttEngine = engine
        engine.start(settings.appLanguage.value)

        listeningJobs = viewModelScope.launch {
            launch {
                engine.transcript.collect { text ->
                    if (text.isNotBlank()) onTranscript(text)
                }
            }
            launch {
                engine.partialTranscript.collect { text ->
                    if (text.isNotBlank()) _lastHeard.value = text
                }
            }
            launch {
                engine.lastError.collect { error ->
                    if (!error.isNullOrBlank()) {
                        _sttError.value = error
                    }
                }
            }
        }
    }

    fun stopListening() {
        previousTranscript = ""
        _isListening.value = false
        _detectionState.value = DetectionState.IDLE
        listeningJobs?.cancel()
        listeningJobs = null
        sttEngine?.stop()
        sttEngine = null
    }

    fun submitFakeTranscript(text: String) {
        fakeEngine.submit(text)
    }

    private fun onTranscript(text: String) {
        _lastHeard.value = text

        val delta = calculateDelta(current = text, previous = previousTranscript)
        previousTranscript = text

        if (delta.isBlank()) return

        Log.d("BibleViewModel", "onTranscript delta: '$delta'")
        _detectionState.value = DetectionState.TRANSCRIBING

        val matches = ExplicitBibleReferenceDetector.detect(delta, language = settings.appLanguage.value)
        Log.d("BibleViewModel", "Matches found: ${matches.size}")

        // Processamos todas as referências encontradas (o Deduplicator evita repetições)
        matches.forEach { match ->
            val isCurrent = match.reference.bookId == _currentBookId.value && 
                           match.reference.chapter == _currentChapter.value &&
                           match.reference.startVerse == _targetVerse.value
            
            if (!isCurrent && sessionManager.shouldSuggest(match.reference)) {
                processDetection(match)
            }
        }

        if (_suggestion.value == null) {
            _detectionState.value = DetectionState.LISTENING
        }
    }

    private fun calculateDelta(current: String, previous: String): String {
        val currNorm = current.replace(Regex("\\s+"), " ").trim()
        val prevNorm = previous.replace(Regex("\\s+"), " ").trim()

        if (prevNorm.isEmpty()) {
            return currNorm
        }

        if (currNorm.startsWith(prevNorm, ignoreCase = true)) {
            return currNorm.substring(prevNorm.length).trim()
        }

        return currNorm
    }

    private fun processDetection(match: DetectedReference) {
        sessionManager.registerSuggestionShown(match.reference)
        _detectionState.value = DetectionState.VALIDATED_REFERENCE

        val autoOpen = settings.navigationMode.value == NavigationMode.AUTO_OPEN &&
                match.confidence >= SettingsRepository.AUTO_OPEN_CONFIDENCE_THRESHOLD

        if (autoOpen) {
            acceptSuggestion(match)
        } else {
            _suggestion.value = match
            _detectionState.value = DetectionState.SUGGESTED
        }
    }

    fun acceptSuggestion(match: DetectedReference? = _suggestion.value) {
        val target = match ?: return
        openReference(target.reference.bookId, target.reference.chapter, target.reference.startVerse)
        sessionManager.recordDecision(target.reference, displayReference(target.reference), accepted = true)
        _suggestion.value = null
        _detectionState.value = if (_isListening.value) DetectionState.LISTENING else DetectionState.IDLE
    }

    fun ignoreSuggestion() {
        val target = _suggestion.value ?: return
        sessionManager.recordDecision(target.reference, displayReference(target.reference), accepted = false)
        _suggestion.value = null
        _detectionState.value = if (_isListening.value) DetectionState.LISTENING else DetectionState.IDLE
    }

    fun displayReference(ref: BibleReference): String {
        val book = repository.getBook(ref.bookId)
        val bookName = book?.names?.get(settings.appLanguage.value) ?: book?.name ?: ref.bookId
        val versePart = if (ref.endVerse != null) "${ref.startVerse}-${ref.endVerse}" else "${ref.startVerse}"
        return "$bookName ${ref.chapter}:$versePart"
    }
}

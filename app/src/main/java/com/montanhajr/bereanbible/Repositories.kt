package com.montanhajr.bereanbible

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface BibleRepository {
    fun getBooks(): List<BibleBook>
    fun getBook(id: String): BibleBook?
    fun getChapter(bookId: String, chapter: Int): List<BibleVerse>
    fun searchText(query: String): List<BibleVerse>
}

class InMemoryBibleRepository : BibleRepository {
    override fun getBooks() = BibleData.BOOKS
    override fun getBook(id: String) = BibleData.BOOKS.find { it.id == id }
    override fun getChapter(bookId: String, chapter: Int) =
        BibleData.VERSES.filter { it.bookId == bookId && it.chapter == chapter }

    override fun searchText(query: String) =
        BibleData.VERSES.filter { it.text.contains(query, ignoreCase = true) }
}

class MultiLanguageBibleRepository(
    context: Context,
    private val settingsRepository: SettingsRepository
) : BibleRepository {

    private val spanishRepository = SqliteBibleRepository(context)
    private val inMemoryRepository = InMemoryBibleRepository()

    private val currentRepository: BibleRepository
        get() = when (settingsRepository.appLanguage.value) {
            AppLanguage.SPANISH -> spanishRepository
            else -> inMemoryRepository
        }

    override fun getBooks(): List<BibleBook> = currentRepository.getBooks()

    override fun getBook(id: String): BibleBook? = currentRepository.getBook(id)

    override fun getChapter(bookId: String, chapter: Int): List<BibleVerse> =
        currentRepository.getChapter(bookId, chapter)

    override fun searchText(query: String): List<BibleVerse> =
        currentRepository.searchText(query)
}

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("biblia_pregacao_settings", Context.MODE_PRIVATE)

    private val _navigationMode = MutableStateFlow(
        NavigationMode.valueOf(prefs.getString(KEY_NAV_MODE, NavigationMode.CONFIRM_BEFORE_OPEN.name)!!)
    )
    val navigationMode: StateFlow<NavigationMode> = _navigationMode.asStateFlow()

    fun setNavigationMode(mode: NavigationMode) {
        _navigationMode.value = mode
        prefs.edit().putString(KEY_NAV_MODE, mode.name).apply()
    }

    private val _appLanguage = MutableStateFlow(
        AppLanguage.valueOf(prefs.getString(KEY_APP_LANGUAGE, AppLanguage.PORTUGUESE.name)!!)
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun setAppLanguage(language: AppLanguage) {
        _appLanguage.value = language
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
    }

    companion object {
        private const val KEY_NAV_MODE = "navigation_mode"
        private const val KEY_APP_LANGUAGE = "app_language"

        /** Threshold inicial para o modo automático — deve ser calibrado em testes reais (seção 24.1). */
        const val AUTO_OPEN_CONFIDENCE_THRESHOLD = 0.8f
    }
}

class SermonSessionManager {

    private var deduplicator = DetectionDeduplicator()

    private val _history = MutableStateFlow<List<SermonHistoryEntry>>(emptyList())
    val history: StateFlow<List<SermonHistoryEntry>> = _history.asStateFlow()

    fun startSession() {
        ExplicitBibleReferenceDetector.resetSession()
        deduplicator = DetectionDeduplicator()
        _history.value = emptyList()
    }

    fun shouldSuggest(reference: BibleReference): Boolean = !deduplicator.shouldSuppress(reference)

    fun registerSuggestionShown(reference: BibleReference) {
        deduplicator.markShown(reference)
    }

    fun recordDecision(reference: BibleReference, displayText: String, accepted: Boolean) {
        if (accepted) {
            deduplicator.forceSuppress(reference)
        }
        _history.value = _history.value + SermonHistoryEntry(
            reference = reference,
            displayText = displayText,
            accepted = accepted,
            timestampMs = System.currentTimeMillis()
        )
    }
}

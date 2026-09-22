package com.montanhajr.bereanbible

import java.text.Normalizer
import java.util.Locale

private fun normalizeForAlias(s: String, language: AppLanguage): String =
    Normalizer.normalize(s.lowercase(Locale.forLanguageTag(language.toLocaleTag())), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

object BookAliasIndex {

    private val ordinalWords = mapOf(
        AppLanguage.PORTUGUESE to mapOf(
            1 to listOf("primeiro", "primeira"),
            2 to listOf("segundo", "segunda"),
            3 to listOf("terceiro", "terceira")
        ),
        AppLanguage.SPANISH to mapOf(
            1 to listOf("primero", "primera"),
            2 to listOf("segundo", "segunda"),
            3 to listOf("tercero", "tercera")
        )
    )

    private val extraAliases: Map<AppLanguage, Map<String, String>> = mapOf(
        AppLanguage.PORTUGUESE to mapOf(
            "evangelho de joao" to "JHN",
            "evangelho segundo joao" to "JHN",
            "salmo" to "PSA",
            "salmos" to "PSA",
            "apocalipse de joao" to "REV",
            "revelacao" to "REV",
            "cantico dos canticos" to "SNG",
            "cantico de salomao" to "SNG"
        ),
        AppLanguage.SPANISH to mapOf(
            "evangelio de juan" to "JHN",
            "evangelio segun juan" to "JHN",
            "salmo" to "PSA",
            "salmos" to "PSA",
            "revelacion" to "REV",
            "cantar de los cantares" to "SNG",
            "cantares" to "SNG"
        )
    )

    private val indices: Map<AppLanguage, Map<String, String>> by lazy {
        AppLanguage.entries.associateWith { lang -> buildIndex(lang) }
    }

    private fun buildIndex(language: AppLanguage): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (book in BibleData.BOOKS) {
            val bookName = book.names[language] ?: book.name
            val normalized = normalizeForAlias(bookName, language)
            map[normalized] = book.id

            val match = Regex("^([123]) (.+)$").find(normalized)
            if (match != null) {
                val (numStr, rest) = match.destructured
                val num = numStr.toInt()
                ordinalWords[language]?.get(num)?.forEach { ord -> map["$ord $rest"] = book.id }
            }
        }
        extraAliases[language]?.forEach { (alias, id) -> map[alias] = id }
        return map
    }

    /**
     * Tenta casar um alias de livro começando em [start]. 
     */
    fun matchAt(tokens: List<String>, start: Int, language: AppLanguage): Pair<String, Int>? {
        val maxWindow = 4
        val threshold = 0.85 
        val currentIndex = indices[language] ?: emptyMap()

        for (window in maxWindow downTo 1) {
            if (start + window > tokens.size) continue
            val candidate = tokens.subList(start, start + window).joinToString(" ")
            
            currentIndex[candidate]?.let { return it to window }
            
            if (candidate.length >= 4) {
                for ((alias, id) in currentIndex) {
                    if (Math.abs(alias.length - candidate.length) > 2) continue
                    if (similarity(candidate, alias) >= threshold) {
                        return id to window
                    }
                }
            }
        }
        return null
    }

    private fun similarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else {
                    if (j > 0) {
                        var newValue = costs[j - 1]
                        if (s1[i - 1] != s2[j - 1]) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1
                        }
                        costs[j - 1] = lastValue
                        lastValue = newValue
                    }
                }
            }
            if (i > 0) costs[s2.length] = lastValue
        }
        return (Math.max(s1.length, s2.length) - costs[s2.length]).toDouble() / Math.max(s1.length, s2.length)
    }
}

interface NumberParser {
    /** Tenta ler um número a partir de [start]; retorna (valor, tokensConsumidos) ou null. */
    fun parseAt(tokens: List<String>, start: Int): Pair<Int, Int>?
}

object PortugueseNumberParser : NumberParser {

    private val units = mapOf(
        "zero" to 0, "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "quatro" to 4,
        "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9, "dez" to 10,
        "onze" to 11, "doze" to 12, "treze" to 13, "catorze" to 14, "quatorze" to 14, "quinze" to 15,
        "dezesseis" to 16, "dezessete" to 17, "dezoito" to 18, "dezenove" to 19
    )

    private val tens = mapOf(
        "vinte" to 20, "trinta" to 30, "quarenta" to 40, "cinquenta" to 50,
        "sessenta" to 60, "setenta" to 70, "oitenta" to 80, "noventa" to 90
    )

    private val hundreds = mapOf(
        "cem" to 100, "cento" to 100, "duzentos" to 200, "trezentos" to 300, "quatrocentos" to 400,
        "quinhentos" to 500, "seiscentos" to 600, "setecentos" to 700, "oitocentos" to 800, "novecentos" to 900
    )

    override fun parseAt(tokens: List<String>, start: Int): Pair<Int, Int>? {
        if (start >= tokens.size) return null

        tokens[start].toIntOrNull()?.let { return it to 1 }

        var idx = start
        var total = 0
        var matched = false

        fun current() = tokens.getOrNull(idx)
        fun skipConnector() {
            if (current() == "e") idx++
        }

        hundreds[current()]?.let { h ->
            total += h; matched = true; idx++
            skipConnector()
        }
        tens[current()]?.let { t ->
            total += t; matched = true; idx++
            skipConnector()
        }
        units[current()]?.let { u ->
            total += u; matched = true; idx++
        }

        return if (matched) total to (idx - start) else null
    }
}

object SpanishNumberParser : NumberParser {
    private val units = mapOf(
        "cero" to 0, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
        "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19
    )

    private val tens = mapOf(
        "veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
        "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90
    )

    private val hundreds = mapOf(
        "cien" to 100, "ciento" to 100, "doscientos" to 200, "trescientos" to 300, "cuatrocientos" to 400,
        "quinientos" to 500, "seiscientos" to 600, "setecientos" to 700, "ochocientos" to 800, "novecientos" to 900
    )

    override fun parseAt(tokens: List<String>, start: Int): Pair<Int, Int>? {
        if (start >= tokens.size) return null
        tokens[start].toIntOrNull()?.let { return it to 1 }

        var idx = start
        var total = 0
        var matched = false

        fun current() = tokens.getOrNull(idx)
        fun skipConnector() {
            if (current() == "y") idx++
        }

        hundreds[current()]?.let { h ->
            total += h; matched = true; idx++
            // Em espanhol não costuma ter "y" entre centenas e dezenas (ex: ciento veinte)
        }
        tens[current()]?.let { t ->
            total += t; matched = true; idx++
            skipConnector()
        }
        units[current()]?.let { u ->
            total += u; matched = true; idx++
        }

        return if (matched) total to (idx - start) else null
    }
}

object TranscriptNormalizer {

    fun normalize(text: String, language: AppLanguage): String {
        val lower = Normalizer
            .normalize(text.lowercase(Locale.forLanguageTag(language.toLocaleTag())), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val cleaned = lower.replace(Regex("[^a-z0-9: -]"), " ")
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    fun tokenize(normalized: String): List<String> =
        normalized.split(" ").filter { it.isNotBlank() }
}

object ReferenceValidator {

    fun isValid(ref: BibleReference): Boolean {
        val book = BibleData.BOOKS.find { it.id == ref.bookId } ?: return false
        if (ref.chapter < 1 || ref.chapter > book.chapters) return false
        if (ref.startVerse < 1) return false
        ref.endVerse?.let { if (it < ref.startVerse) return false }

        // O limite superior de versículo só é checado quando temos dados embutidos
        // para aquele capítulo; caso contrário, aceitamos a referência para não
        // rejeitar capítulos válidos que ainda não têm texto de amostra.
        val versesInChapter = BibleData.VERSES.count { it.bookId == ref.bookId && it.chapter == ref.chapter }
        if (versesInChapter > 0 && ref.startVerse > versesInChapter) return false

        return true
    }
}

interface BibleReferenceDetector {
    fun detect(transcript: String, language: AppLanguage): List<DetectedReference>
}

object ExplicitBibleReferenceDetector : BibleReferenceDetector {

    private val keywords = mapOf(
        AppLanguage.PORTUGUESE to mapOf(
            "chapter" to setOf("capitulo", "cap"),
            "verse" to setOf("versiculo", "versiculos", "verso", "v"),
            "range" to setOf("a", "ate", "-"),
            "connector" to setOf("de", "do", "da", "em", "no", "na")
        ),
        AppLanguage.SPANISH to mapOf(
            "chapter" to setOf("capitulo", "cap"),
            "verse" to setOf("versiculo", "versiculos", "verso", "v"),
            "range" to setOf("a", "al", "hasta", "-"),
            "connector" to setOf("de", "del", "la", "en", "el")
        )
    )
    
    private val directPattern = Regex("^(\\d{1,3}):(\\d{1,3})(?:-(\\d{1,3}))?$")

    override fun detect(transcript: String, language: AppLanguage): List<DetectedReference> {
        val normalized = TranscriptNormalizer.normalize(transcript, language)
        val tokens = TranscriptNormalizer.tokenize(normalized)
        val results = mutableListOf<DetectedReference>()

        val parser = when (language) {
            AppLanguage.SPANISH -> SpanishNumberParser
            else -> PortugueseNumberParser
        }
        val langKeywords = keywords[language] ?: keywords[AppLanguage.PORTUGUESE]!!

        var i = 0
        while (i < tokens.size) {
            var bookId: String? = null
            var chapter: Int? = null
            var startVerse = 1
            var endVerse: Int? = null
            var cursor = i
            var confidence = 0.0f

            // 1. TENTA PADRÃO "LIVRO [CAPÍTULO]"
            val bookMatch = BookAliasIndex.matchAt(tokens, i, language)
            if (bookMatch != null) {
                bookId = bookMatch.first
                cursor = i + bookMatch.second
                confidence = 0.50f

                val chapterSearchLimit = Math.min(cursor + 6, tokens.size)
                var chapterIdx = cursor
                while (chapterIdx < chapterSearchLimit) {
                    val token = tokens[chapterIdx]
                    val directMatch = directPattern.find(token)
                    if (directMatch != null) {
                        chapter = directMatch.groupValues[1].toInt()
                        startVerse = directMatch.groupValues[2].toInt()
                        endVerse = directMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
                        cursor = chapterIdx + 1
                        confidence += 0.35f
                        break
                    }
                    if (token in langKeywords["chapter"]!!) {
                        parser.parseAt(tokens, chapterIdx + 1)?.let { (value, consumed) ->
                            chapter = value
                            cursor = chapterIdx + 1 + consumed
                            confidence += 0.25f
                        }
                        if (chapter != null) break
                    }
                    parser.parseAt(tokens, chapterIdx)?.let { (value, consumed) ->
                        chapter = value
                        cursor = chapterIdx + consumed
                        confidence += 0.15f
                    }
                    if (chapter != null) break
                    chapterIdx++
                }
            }
            // 2. TENTA PADRÃO "CAPÍTULO X [DE/EM] LIVRO"
            else if (tokens[i] in langKeywords["chapter"]!!) {
                parser.parseAt(tokens, i + 1)?.let { (value, consumed) ->
                    chapter = value
                    var tempCursor = i + 1 + consumed
                    confidence = 0.30f
                    
                    // Pula conectores (ex: "de", "no")
                    if (tempCursor < tokens.size && tokens[tempCursor] in langKeywords["connector"]!!) {
                        tempCursor++
                    }
                    
                    val bookMatchReverse = BookAliasIndex.matchAt(tokens, tempCursor, language)
                    if (bookMatchReverse != null) {
                        bookId = bookMatchReverse.first
                        cursor = tempCursor + bookMatchReverse.second
                        confidence += 0.40f
                    }
                }
            }

            if (bookId == null || chapter == null) {
                i++
                continue
            }

            // 3. BUSCA PELO VERSÍCULO (Comum aos dois padrões)
            if (endVerse == null) {
                val verseSearchLimit = Math.min(cursor + 5, tokens.size)
                var verseIdx = cursor
                while (verseIdx < verseSearchLimit) {
                    val token = tokens[verseIdx]
                    if (token in langKeywords["verse"]!!) {
                        parser.parseAt(tokens, verseIdx + 1)?.let { (value, consumed) ->
                            startVerse = value
                            cursor = verseIdx + 1 + consumed
                            confidence += 0.20f
                        }
                        if (startVerse > 1) break
                    }
                    parser.parseAt(tokens, verseIdx)?.let { (value, consumed) ->
                        startVerse = value
                        cursor = verseIdx + consumed
                        confidence += 0.10f
                    }
                    if (startVerse > 1) break
                    verseIdx++
                }
            }

            // 4. BUSCA POR RANGE
            if (endVerse == null) {
                val rangeSearchLimit = Math.min(cursor + 3, tokens.size)
                var rangeIdx = cursor
                while (rangeIdx < rangeSearchLimit) {
                    if (tokens[rangeIdx] in langKeywords["range"]!!) {
                        parser.parseAt(tokens, rangeIdx + 1)?.let { (value, consumed) ->
                            endVerse = value
                            cursor = rangeIdx + 1 + consumed
                            confidence += 0.05f
                        }
                        break
                    }
                    rangeIdx++
                }
            }

            val reference = BibleReference(bookId, chapter, startVerse, endVerse)
            if (ReferenceValidator.isValid(reference)) {
                results += DetectedReference(
                    reference = reference,
                    originalText = tokens.subList(i, cursor).joinToString(" "),
                    confidence = confidence.coerceAtMost(1.0f)
                )
                i = cursor
            } else {
                i++
            }
        }
        return results
    }
}

class DetectionDeduplicator(private val windowMs: Long = 45_000L) {
    private val lastShown = mutableMapOf<BibleReference, Long>()

    fun shouldSuppress(reference: BibleReference, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = lastShown[reference]
        return last != null && (nowMs - last) < windowMs
    }

    fun markShown(reference: BibleReference, nowMs: Long = System.currentTimeMillis()) {
        lastShown[reference] = nowMs
    }

    fun forceSuppress(reference: BibleReference, durationMs: Long = 120_000L) {
        lastShown[reference] = System.currentTimeMillis() + (durationMs - windowMs)
    }
}

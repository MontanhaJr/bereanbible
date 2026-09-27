/*
 * REVISÃO — lógica de detecção de referências bíblicas
 * ------------------------------------------------------
 * O que mudou nesta revisão (contexto completo na conversa):
 *
 * 1) TRAVAMENTO COM PREGAÇÃO LONGA:
 *    - BookAliasIndex.matchAt fazia fuzzy matching (Levenshtein) comparando cada token
 *      candidato contra TODOS os aliases do dicionário (milhares de combinações de
 *      prefixo/idioma), para cada janela de 1 a 5 tokens, em CADA posição do texto.
 *      Agora o fuzzy matching é restrito a 1 token por vez e usa um índice bucketizado
 *      por letra inicial, então cada busca compara contra um punhado de candidatos, não
 *      o dicionário inteiro.
 *    - ExplicitBibleReferenceDetector deixou de tokenizar do zero a cada chamada e passou
 *      a manter uma janela de contexto interna (buffer de tokens com timestamp), limitada
 *      por tempo (25s) e por tamanho (120 tokens). O custo de cada chamada passa a ser
 *      proporcional ao tamanho dessa janela, não ao tamanho da pregação inteira.
 *
 * 2) PAUSAS ENTRE LIVRO / CAPÍTULO / VERSÍCULO:
 *    - Como o buffer agora persiste ENTRE chamadas, se o pregador disser só o nome do
 *      livro num trecho reconhecido e o capítulo/versículo vier em um trecho seguinte
 *      (depois de uma pausa), o detector consegue juntar as duas partes — desde que a
 *      pausa real entre elas seja menor que MAX_PAUSE_BETWEEN_PARTS_MS (9s, ajustável).
 *
 * 3) INTEGRAÇÃO — ATENÇÃO AO CHAMAR ESTA CLASSE:
 *    - detect(transcript, language) agora espera receber apenas o texto NOVO reconhecido
 *      desde a última chamada (um resultado final do SpeechRecognizer, ou o delta de um
 *      parcial) — NÃO a transcrição acumulada inteira. Se hoje a camada de STT reenvia a
 *      string cumulativa a cada callback, isso precisa ser ajustado, senão o buffer volta
 *      a crescer sem necessidade (ver sugestão de código na conversa).
 *    - Chame ExplicitBibleReferenceDetector.resetSession() sempre que uma nova sessão de
 *      escuta começar (ex.: dentro do SermonSessionManager.startSession()), para não
 *      herdar tokens de uma pregação/sessão anterior.
 *
 * O restante do arquivo (SpanishNumberParser, ConcatenatedNumberSplitter,
 * TranscriptNormalizer, ReferenceValidator, DetectionDeduplicator) foi mantido igual ao
 * original — não identifiquei problema nessas partes.
 */

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
            1 to listOf("1", "1a", "1o", "1ª", "1º", "primeiro", "primeira", "uma", "uno"),
            2 to listOf("2", "2a", "2o", "2ª", "2º", "segundo", "segunda", "duas", "dos"),
            3 to listOf("3", "3a", "3o", "3ª", "3º", "terceiro", "terceira", "tres")
        ),
        AppLanguage.SPANISH to mapOf(
            1 to listOf("1", "1a", "1o", "1ª", "1º", "primero", "primera", "primer", "uno", "una"),
            2 to listOf("2", "2a", "2o", "2ª", "2º", "segundo", "segunda", "dos"),
            3 to listOf("3", "3a", "3o", "3ª", "3º", "tercero", "tercera", "tercer", "tres")
        )
    )

    private val gospels = setOf("MAT", "MRK", "LUK", "JHN")
    private val epistles = setOf("ROM", "GAL", "EPH", "PHP", "COL", "TIT", "PHM", "HEB", "JAS", "JUD")

    private val indices: Map<AppLanguage, Map<String, String>> by lazy {
        AppLanguage.entries.associateWith { lang -> buildIndex(lang) }
    }

    /**
     * NOVO: índice auxiliar só com aliases de UMA palavra, agrupados pela letra inicial.
     * É contra ele que o fuzzy matching roda agora — em vez de contra o dicionário inteiro
     * (que inclui todas as combinações livro+prefixo+idioma, facilmente milhares de linhas).
     */
    private val fuzzyBuckets: Map<AppLanguage, Map<Char, List<Pair<String, String>>>> by lazy {
        AppLanguage.entries.associateWith { lang ->
            (indices[lang] ?: emptyMap())
                .filterKeys { it.isNotEmpty() && !it.contains(' ') }
                .entries
                .groupBy({ it.key.first() }, { it.key to it.value })
        }
    }

    private fun buildIndex(language: AppLanguage): Map<String, String> {
        val map = mutableMapOf<String, String>()

        for (book in BibleData.BOOKS) {
            val bookName = book.names[language] ?: book.name
            val normalized = normalizeForAlias(bookName, language)
            map[normalized] = book.id

            // Trata livros numerados (1 João, 1 Pedro, 1 Coríntios, 1 Samuel, etc.)
            val match = Regex("^([123]) (.+)$").find(normalized)
            if (match != null) {
                val (numStr, rest) = match.destructured
                val num = numStr.toInt()
                val prefixes = ordinalWords[language]?.get(num) ?: emptyList()
                val connectors = listOf("", "de ", "de sao ", "de san ", "carta de ", "carta a ", "carta aos ", "carta a los ", "epistola de ", "epistola a ", "epistola aos ", "epistola a los ")

                for (p in prefixes) {
                    for (c in connectors) {
                        map["$p $c$rest".replace(Regex("\\s+"), " ").trim()] = book.id
                    }
                }
            }

            // Trata evangelhos ("São João", "San Juan", "Evangelho de João", etc.)
            if (book.id in gospels) {
                val prefixes = listOf("sao ", "san ", "santo ", "evangelho de ", "evangelho segundo ", "evangelho de sao ", "evangelho segundo sao ", "evangelio de ", "evangelio segun ", "evangelio de san ", "evangelio segun san ")
                for (p in prefixes) {
                    map["$p$normalized".replace(Regex("\\s+"), " ").trim()] = book.id
                }
            }

            // Trata epístolas ("Carta aos Romanos", "Carta a los Hebreos", etc.)
            if (book.id in epistles) {
                val prefixes = listOf("carta a ", "carta aos ", "carta de ", "carta a los ", "epistola a ", "epistola aos ", "epistola de ", "epistola a los ")
                for (p in prefixes) {
                    map["$p$normalized".replace(Regex("\\s+"), " ").trim()] = book.id
                }
            }
        }

        // Aliases extras específicos
        val extra = mapOf(
            "evangelho de joao" to "JHN", "evangelho segundo joao" to "JHN", "sao joao" to "JHN", "san juan" to "JHN", "evangelio de juan" to "JHN",
            "salmo" to "PSA", "salmos" to "PSA", "salmo de davi" to "PSA", "salmo de david" to "PSA",
            "apocalipse" to "REV", "apocalipse de joao" to "REV", "revelacao" to "REV", "apocalipsis" to "REV", "revelacion" to "REV",
            "cantico dos canticos" to "SNG", "cantico de salomao" to "SNG", "cantar de los cantares" to "SNG", "cantares" to "SNG",
            "atos" to "ACT", "atos dos apostolos" to "ACT", "hechos" to "ACT", "hechos de los apostoles" to "ACT"
        )
        extra.forEach { (alias, id) -> map[alias] = id }

        return map
    }

    /**
     * Tenta casar um alias de livro começando em [start].
     * 1ª passada: exato, testando janelas de 5 até 1 tokens (aliases de várias palavras).
     * 2ª passada: fuzzy, restrito a 1 token, usando o bucket da letra inicial do candidato —
     * rápido mesmo com um dicionário grande, porque só compara contra quem começa com a
     * mesma letra em vez do dicionário inteiro.
     */
    fun matchAt(tokens: List<String>, start: Int, language: AppLanguage): Pair<String, Int>? {
        val maxWindow = 5
        val indexPrimary = indices[language] ?: emptyMap()
        val otherLang = if (language == AppLanguage.PORTUGUESE) AppLanguage.SPANISH else AppLanguage.PORTUGUESE
        val indexSecondary = indices[otherLang] ?: emptyMap()

        for (window in maxWindow downTo 1) {
            if (start + window > tokens.size) continue
            val candidate = tokens.subList(start, start + window).joinToString(" ")
            indexPrimary[candidate]?.let { return it to window }
            indexSecondary[candidate]?.let { return it to window }
        }

        val candidate = tokens.getOrNull(start) ?: return null
        if (candidate.length < 4 || candidate.any { it.isDigit() }) return null
        val threshold = 0.82

        for (lang in listOf(language, otherLang)) {
            val bucket = fuzzyBuckets[lang]?.get(candidate.first()) ?: continue
            for ((alias, id) in bucket) {
                if (Math.abs(alias.length - candidate.length) > 2) continue
                if (similarity(candidate, alias) >= threshold) return id to 1
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
        "dezesseis" to 16, "dezessete" to 17, "dezoito" to 18, "dezenove" to 19,
        "primeiro" to 1, "primeira" to 1, "segundo" to 2, "segunda" to 2, "terceiro" to 3, "terceira" to 3
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
        "cero" to 0, "uno" to 1, "una" to 1, "un" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
        "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
        "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veintiuno" to 21, "veintiun" to 21, "veintiuna" to 21, "veintidos" to 22,
        "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
        "primer" to 1, "primero" to 1, "primera" to 1, "segundo" to 2, "segunda" to 2,
        "tercer" to 3, "tercero" to 3, "tercera" to 3
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

object ConcatenatedNumberSplitter {
    /**
     * Tenta dividir um token numérico concatenado (ex: "316", "1215", "119105") em (capítulo, versículo)
     * com base na quantidade máxima de capítulos do livro e limites de versículos.
     */
    fun split(token: String, bookId: String): Pair<Int, Int>? {
        val n = token.toIntOrNull() ?: return null
        val book = BibleData.BOOKS.find { it.id == bookId } ?: return null
        val maxChapters = book.chapters

        if (maxChapters <= 1 || n <= maxChapters) return null

        val len = token.length
        if (len < 2) return null

        val candidates = mutableListOf<Pair<Int, Int>>()

        for (p in 1 until len) {
            val cStr = token.substring(0, p)
            val vStr = token.substring(p)
            val c = cStr.toIntOrNull() ?: continue
            val v = vStr.toIntOrNull() ?: continue

            if (c in 1..maxChapters && v in 1..176) {
                val versesInCap = BibleData.VERSES.count { it.bookId == bookId && it.chapter == c }
                if (versesInCap == 0 || v <= versesInCap) {
                    candidates.add(c to v)
                }
            }
        }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        return candidates.find { it.first < 10 } ?: candidates.first()
    }
}

object TranscriptNormalizer {

    fun normalize(text: String, language: AppLanguage): String {
        val lower = Normalizer
            .normalize(text.lowercase(Locale.forLanguageTag(language.toLocaleTag())), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        val formatted = lower
            .replace(Regex("(\\d+)\\s*[:,.]\\s*(\\d+)"), "$1:$2")
            .replace(Regex("[^a-z0-9: -]"), " ")

        return formatted.replace(Regex("\\s+"), " ").trim()
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

        val versesInChapter = BibleData.VERSES.count { it.bookId == ref.bookId && it.chapter == ref.chapter }
        if (versesInChapter > 0 && ref.startVerse > versesInChapter) return false

        return true
    }
}

interface BibleReferenceDetector {
    fun detect(transcript: String, language: AppLanguage): List<DetectedReference>
}

object ExplicitBibleReferenceDetector : BibleReferenceDetector {

    private data class TimedToken(val text: String, val timestampMs: Long)

    // Janela de contexto usada PELA DETECÇÃO. Isto é independente de uma eventual transcrição
    // completa da pregação (feature futura) — essa deve ser guardada à parte, sem limite,
    // sempre que um resultado final chegar, sem passar pelo buffer/algoritmo abaixo.
    private const val BUFFER_MAX_AGE_MS = 25_000L
    private const val BUFFER_MAX_TOKENS = 120

    // Pausa máxima tolerada ENTRE PARTES de uma mesma referência (livro -> capítulo -> versículo).
    // Ajuste conforme os testes reais em campo.
    private const val MAX_PAUSE_BETWEEN_PARTS_MS = 9_000L
    private const val FORWARD_SEARCH_TOKEN_CAP = 25

    private val buffer = mutableListOf<TimedToken>()

    /** Chame ao iniciar uma nova sessão de escuta, para não herdar contexto da anterior. */
    fun resetSession() {
        synchronized(buffer) { buffer.clear() }
    }

    private val keywords = mapOf(
        AppLanguage.PORTUGUESE to mapOf(
            "chapter" to setOf("capitulo", "capitulos", "cap"),
            "verse" to setOf("versiculo", "versiculos", "verso", "versos", "v"),
            "range" to setOf("a", "ate", "ao", "aos", "e", "-"),
            "connector" to setOf("de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", "del", "el", "la", "los", "las")
        ),
        AppLanguage.SPANISH to mapOf(
            "chapter" to setOf("capitulo", "capitulos", "cap"),
            "verse" to setOf("versiculo", "versiculos", "verso", "versos", "v"),
            "range" to setOf("a", "al", "hasta", "e", "y", "-"),
            "connector" to setOf("de", "del", "la", "el", "los", "las", "en")
        )
    )

    private val directPattern = Regex("^(\\d{1,3}):(\\d{1,3})(?:-(\\d{1,3}))?$")

    /**
     * Até onde vale a pena procurar à frente de [fromIdx]: avança enquanto o intervalo real
     * de tempo entre o token de origem e o candidato não ultrapassar MAX_PAUSE_BETWEEN_PARTS_MS
     * (tolerando pausas grandes entre segmentos de fala), com um teto absoluto de tokens como
     * salvaguarda contra varreduras longas demais dentro de uma fala contínua.
     */
    private fun forwardLimit(timestamps: List<Long>, fromIdx: Int): Int {
        if (fromIdx >= timestamps.size) return timestamps.size
        var idx = fromIdx
        val baseTime = timestamps[fromIdx]
        val maxIdx = minOf(timestamps.size, fromIdx + FORWARD_SEARCH_TOKEN_CAP)
        while (idx < maxIdx && (timestamps[idx] - baseTime) <= MAX_PAUSE_BETWEEN_PARTS_MS) idx++
        return idx
    }

    /**
     * IMPORTANTE: [transcript] deve conter apenas o texto NOVO reconhecido desde a última
     * chamada (um resultado final, ou o delta de um parcial) — não a transcrição acumulada
     * inteira da pregação. Ver observação no topo do arquivo.
     */
    override fun detect(transcript: String, language: AppLanguage): List<DetectedReference> {
        val now = System.currentTimeMillis()
        val normalized = TranscriptNormalizer.normalize(transcript, language)
        val newTokens = TranscriptNormalizer.tokenize(normalized).map { TimedToken(it, now) }
        if (newTokens.isEmpty()) return emptyList()

        val parser = when (language) {
            AppLanguage.SPANISH -> SpanishNumberParser
            else -> PortugueseNumberParser
        }
        val langKeywords = keywords[language] ?: keywords[AppLanguage.PORTUGUESE]!!

        val results = mutableListOf<DetectedReference>()

        synchronized(buffer) {
            buffer.addAll(newTokens)

            val cutoff = now - BUFFER_MAX_AGE_MS
            while (buffer.isNotEmpty() && (buffer.first().timestampMs < cutoff || buffer.size > BUFFER_MAX_TOKENS)) {
                buffer.removeAt(0)
            }

            var tokens = buffer.map { it.text }
            var timestamps = buffer.map { it.timestampMs }

            var i = 0
            while (i < tokens.size) {
                var bookId: String? = null
                var chapter: Int? = null
                var startVerse = 1
                var endVerse: Int? = null
                var cursor = i
                var confidence = 0.0f

                // 1. PADRÃO "LIVRO [CAPÍTULO / DIRECT / CONCATENADO]"
                val bookMatch = BookAliasIndex.matchAt(tokens, i, language)
                if (bookMatch != null) {
                    bookId = bookMatch.first
                    cursor = i + bookMatch.second
                    confidence = 0.50f

                    val chapterSearchLimit = forwardLimit(timestamps, cursor)
                    var chapterIdx = cursor
                    while (chapterIdx < chapterSearchLimit) {
                        val token = tokens[chapterIdx]

                        // a) Direct pattern "3:16" ou "3:16-18"
                        val directMatch = directPattern.find(token)
                        if (directMatch != null) {
                            chapter = directMatch.groupValues[1].toInt()
                            startVerse = directMatch.groupValues[2].toInt()
                            endVerse = directMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
                            cursor = chapterIdx + 1
                            confidence += 0.40f
                            break
                        }

                        // b) Número concatenado como "316" ou "119105"
                        val splitConcat = ConcatenatedNumberSplitter.split(token, bookId)
                        if (splitConcat != null) {
                            chapter = splitConcat.first
                            startVerse = splitConcat.second
                            cursor = chapterIdx + 1
                            confidence += 0.35f
                            break
                        }

                        // c) Palavra chave de capítulo ("capítulo 3", "capítulo 316")
                        if (token in langKeywords["chapter"]!!) {
                            val nextToken = tokens.getOrNull(chapterIdx + 1)
                            if (nextToken != null) {
                                val directInCap = directPattern.find(nextToken)
                                if (directInCap != null) {
                                    chapter = directInCap.groupValues[1].toInt()
                                    startVerse = directInCap.groupValues[2].toInt()
                                    endVerse = directInCap.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
                                    cursor = chapterIdx + 2
                                    confidence += 0.35f
                                    break
                                }
                                val splitInCap = ConcatenatedNumberSplitter.split(nextToken, bookId)
                                if (splitInCap != null) {
                                    chapter = splitInCap.first
                                    startVerse = splitInCap.second
                                    cursor = chapterIdx + 2
                                    confidence += 0.35f
                                    break
                                }
                            }

                            parser.parseAt(tokens, chapterIdx + 1)?.let { (value, consumed) ->
                                chapter = value
                                cursor = chapterIdx + 1 + consumed
                                confidence += 0.25f
                            }
                            if (chapter != null) break
                        }

                        // d) Número falado ou numérico simples ("3")
                        parser.parseAt(tokens, chapterIdx)?.let { (value, consumed) ->
                            chapter = value
                            cursor = chapterIdx + consumed
                            confidence += 0.15f
                        }
                        if (chapter != null) break
                        chapterIdx++
                    }
                }
                // 2. PADRÃO REVERSO "CAPÍTULO X [VERSÍCULO Y] DE LIVRO"
                else if (tokens[i] in langKeywords["chapter"]!!) {
                    var tempChapter: Int? = null
                    var tempVerse: Int? = null
                    var tempCursor = i + 1

                    val capParse = parser.parseAt(tokens, tempCursor)
                    if (capParse != null) {
                        tempChapter = capParse.first
                        tempCursor += capParse.second
                    } else {
                        val splitCap = tokens.getOrNull(tempCursor)?.toIntOrNull()
                        if (splitCap != null) {
                            tempChapter = splitCap
                            tempCursor++
                        }
                    }

                    if (tempChapter != null) {
                        confidence = 0.30f
                        if (tempCursor < tokens.size && tokens[tempCursor] in langKeywords["verse"]!!) {
                            tempCursor++
                            parser.parseAt(tokens, tempCursor)?.let { (vVal, vConsumed) ->
                                tempVerse = vVal
                                tempCursor += vConsumed
                            }
                        }

                        if (tempCursor < tokens.size && tokens[tempCursor] in langKeywords["connector"]!!) {
                            tempCursor++
                        }

                        val bookMatchReverse = BookAliasIndex.matchAt(tokens, tempCursor, language)
                        if (bookMatchReverse != null) {
                            bookId = bookMatchReverse.first
                            chapter = tempChapter
                            if (tempVerse != null) startVerse = tempVerse
                            cursor = tempCursor + bookMatchReverse.second
                            confidence += 0.40f

                            val concatSplit = ConcatenatedNumberSplitter.split(tempChapter.toString(), bookId)
                            if (concatSplit != null) {
                                chapter = concatSplit.first
                                startVerse = concatSplit.second
                            }
                        }
                    }
                }
                // 3. PADRÃO REVERSO "VERSÍCULO Y DO CAPÍTULO X DE LIVRO"
                else if (tokens[i] in langKeywords["verse"]!!) {
                    parser.parseAt(tokens, i + 1)?.let { (vVal, vConsumed) ->
                        var tempCursor = i + 1 + vConsumed
                        if (tempCursor < tokens.size && tokens[tempCursor] in langKeywords["connector"]!!) tempCursor++
                        if (tempCursor < tokens.size && tokens[tempCursor] in langKeywords["chapter"]!!) {
                            tempCursor++
                            parser.parseAt(tokens, tempCursor)?.let { (cVal, cConsumed) ->
                                tempCursor += cConsumed
                                if (tempCursor < tokens.size && tokens[tempCursor] in langKeywords["connector"]!!) tempCursor++
                                val bookMatchReverse = BookAliasIndex.matchAt(tokens, tempCursor, language)
                                if (bookMatchReverse != null) {
                                    bookId = bookMatchReverse.first
                                    chapter = cVal
                                    startVerse = vVal
                                    cursor = tempCursor + bookMatchReverse.second
                                    confidence = 0.75f
                                }
                            }
                        }
                    }
                }

                val validBookId = bookId
                var validChapter = chapter
                if (validBookId == null || validChapter == null) {
                    i++
                    continue
                }

                // Ajuste para livros de capítulo único (Obadias, Filemom, 2 João, 3 João, Judas)
                val bookObj = BibleData.BOOKS.find { it.id == validBookId }
                if (bookObj != null && bookObj.chapters == 1) {
                    if (validChapter > 1 && startVerse == 1) {
                        startVerse = validChapter
                        validChapter = 1
                    }
                }

                // 4. BUSCA PELO VERSÍCULO
                if (startVerse == 1 && endVerse == null) {
                    val verseSearchLimit = forwardLimit(timestamps, cursor)
                    var verseIdx = cursor
                    while (verseIdx < verseSearchLimit) {
                        val token = tokens[verseIdx]

                        if (token in langKeywords["connector"]!! && verseIdx + 1 < verseSearchLimit) {
                            verseIdx++
                            continue
                        }

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

                // 5. BUSCA POR RANGE
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

                val reference = BibleReference(validBookId, validChapter, startVerse, endVerse)
                if (ReferenceValidator.isValid(reference)) {
                    results += DetectedReference(
                        reference = reference,
                        originalText = tokens.subList(i, cursor).joinToString(" "),
                        confidence = confidence.coerceAtMost(1.0f)
                    )
                    // Remove só o trecho consumido; o resto do buffer (inclusive tokens que
                    // vieram antes ou depois, ainda não varridos) permanece intacto.
                    buffer.subList(i, cursor).clear()
                    tokens = buffer.map { it.text }
                    timestamps = buffer.map { it.timestampMs }
                    // não avança i: pode haver outra referência logo em seguida
                } else {
                    i++
                }
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
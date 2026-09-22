package com.montanhajr.bereanbible

object BibleData {

    /** Os 66 livros, com nome em português e número de capítulos. */
    val BOOKS: List<BibleBook> = listOf(
        BibleBook("GEN", "Gênesis", 50, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Gênesis", AppLanguage.SPANISH to "Génesis")),
        BibleBook("EXO", "Êxodo", 40, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Êxodo", AppLanguage.SPANISH to "Éxodo")),
        BibleBook("LEV", "Levítico", 27, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Levítico", AppLanguage.SPANISH to "Levítico")),
        BibleBook("NUM", "Números", 36, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Números", AppLanguage.SPANISH to "Números")),
        BibleBook("DEU", "Deuteronômio", 34, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Deuteronômio", AppLanguage.SPANISH to "Deuteronomio")),
        BibleBook("JOS", "Josué", 24, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Josué", AppLanguage.SPANISH to "Josué")),
        BibleBook("JDG", "Juízes", 21, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Juízes", AppLanguage.SPANISH to "Jueces")),
        BibleBook("RUT", "Rute", 4, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Rute", AppLanguage.SPANISH to "Rut")),
        BibleBook("1SA", "1 Samuel", 31, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "1 Samuel", AppLanguage.SPANISH to "1 Samuel")),
        BibleBook("2SA", "2 Samuel", 24, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "2 Samuel", AppLanguage.SPANISH to "2 Samuel")),
        BibleBook("1KI", "1 Reis", 22, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "1 Reis", AppLanguage.SPANISH to "1 Reyes")),
        BibleBook("2KI", "2 Reis", 25, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "2 Reis", AppLanguage.SPANISH to "2 Reyes")),
        BibleBook("1CH", "1 Crônicas", 29, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "1 Crônicas", AppLanguage.SPANISH to "1 Crónicas")),
        BibleBook("2CH", "2 Crônicas", 36, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "2 Crônicas", AppLanguage.SPANISH to "2 Crónicas")),
        BibleBook("EZR", "Esdras", 10, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Esdras", AppLanguage.SPANISH to "Esdras")),
        BibleBook("NEH", "Neemias", 13, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Neemias", AppLanguage.SPANISH to "Nehemías")),
        BibleBook("EST", "Ester", 10, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Ester", AppLanguage.SPANISH to "Ester")),
        BibleBook("JOB", "Jó", 42, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Jó", AppLanguage.SPANISH to "Job")),
        BibleBook("PSA", "Salmos", 150, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Salmos", AppLanguage.SPANISH to "Salmos")),
        BibleBook("PRO", "Provérbios", 31, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Provérbios", AppLanguage.SPANISH to "Proverbios")),
        BibleBook("ECC", "Eclesiastes", 12, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Eclesiastes", AppLanguage.SPANISH to "Eclesiastés")),
        BibleBook("SNG", "Cantares", 8, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Cantares", AppLanguage.SPANISH to "Cantares")),
        BibleBook("ISA", "Isaías", 66, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Isaías", AppLanguage.SPANISH to "Isaías")),
        BibleBook("JER", "Jeremias", 52, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Jeremias", AppLanguage.SPANISH to "Jeremías")),
        BibleBook("LAM", "Lamentações", 5, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Lamentações", AppLanguage.SPANISH to "Lamentaciones")),
        BibleBook("EZK", "Ezequiel", 48, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Ezequiel", AppLanguage.SPANISH to "Ezequiel")),
        BibleBook("DAN", "Daniel", 12, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Daniel", AppLanguage.SPANISH to "Daniel")),
        BibleBook("HOS", "Oséias", 14, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Oséias", AppLanguage.SPANISH to "Oseas")),
        BibleBook("JOL", "Joel", 3, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Joel", AppLanguage.SPANISH to "Joel")),
        BibleBook("AMO", "Amós", 9, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Amós", AppLanguage.SPANISH to "Amós")),
        BibleBook("OBA", "Obadias", 1, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Obadias", AppLanguage.SPANISH to "Abdías")),
        BibleBook("JON", "Jonas", 4, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Jonas", AppLanguage.SPANISH to "Jonás")),
        BibleBook("MIC", "Miquéias", 7, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Miquéias", AppLanguage.SPANISH to "Miqueas")),
        BibleBook("NAM", "Naum", 3, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Naum", AppLanguage.SPANISH to "Nahúm")),
        BibleBook("HAB", "Habacuque", 3, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Habacuque", AppLanguage.SPANISH to "Habacuc")),
        BibleBook("ZEP", "Sofonias", 3, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Sofonias", AppLanguage.SPANISH to "Sofonías")),
        BibleBook("HAG", "Ageu", 2, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Ageu", AppLanguage.SPANISH to "Hageo")),
        BibleBook("ZEC", "Zacarias", 14, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Zacarias", AppLanguage.SPANISH to "Zacarías")),
        BibleBook("MAL", "Malaquias", 4, Testament.OLD, mapOf(AppLanguage.PORTUGUESE to "Malaquias", AppLanguage.SPANISH to "Malaquías")),
        BibleBook("MAT", "Mateus", 28, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Mateus", AppLanguage.SPANISH to "Mateo")),
        BibleBook("MRK", "Marcos", 16, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Marcos", AppLanguage.SPANISH to "Marcos")),
        BibleBook("LUK", "Lucas", 24, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Lucas", AppLanguage.SPANISH to "Lucas")),
        BibleBook("JHN", "João", 21, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "João", AppLanguage.SPANISH to "Juan")),
        BibleBook("ACT", "Atos", 28, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Atos", AppLanguage.SPANISH to "Hechos")),
        BibleBook("ROM", "Romanos", 16, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Romanos", AppLanguage.SPANISH to "Romanos")),
        BibleBook("1CO", "1 Coríntios", 16, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "1 Coríntios", AppLanguage.SPANISH to "1 Corintios")),
        BibleBook("2CO", "2 Coríntios", 13, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "2 Coríntios", AppLanguage.SPANISH to "2 Corintios")),
        BibleBook("GAL", "Gálatas", 6, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Gálatas", AppLanguage.SPANISH to "Gálatas")),
        BibleBook("EPH", "Efésios", 6, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Efésios", AppLanguage.SPANISH to "Efesios")),
        BibleBook("PHP", "Filipenses", 4, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Filipenses", AppLanguage.SPANISH to "Filipenses")),
        BibleBook("COL", "Colossenses", 4, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Colossenses", AppLanguage.SPANISH to "Colosenses")),
        BibleBook("1TH", "1 Tessalonicenses", 5, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "1 Tessalonicenses", AppLanguage.SPANISH to "1 Tesalonicenses")),
        BibleBook("2TH", "2 Tessalonicenses", 3, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "2 Tessalonicenses", AppLanguage.SPANISH to "2 Tesalonicenses")),
        BibleBook("1TI", "1 Timóteo", 6, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "1 Timóteo", AppLanguage.SPANISH to "1 Timoteo")),
        BibleBook("2TI", "2 Timóteo", 4, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "2 Timóteo", AppLanguage.SPANISH to "2 Timoteo")),
        BibleBook("TIT", "Tito", 3, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Tito", AppLanguage.SPANISH to "Tito")),
        BibleBook("PHM", "Filemom", 1, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Filemom", AppLanguage.SPANISH to "Filemón")),
        BibleBook("HEB", "Hebreus", 13, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Hebreus", AppLanguage.SPANISH to "Hebreos")),
        BibleBook("JAS", "Tiago", 5, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Tiago", AppLanguage.SPANISH to "Santiago")),
        BibleBook("1PE", "1 Pedro", 5, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "1 Pedro", AppLanguage.SPANISH to "1 Pedro")),
        BibleBook("2PE", "2 Pedro", 3, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "2 Pedro", AppLanguage.SPANISH to "2 Pedro")),
        BibleBook("1JN", "1 João", 5, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "1 João", AppLanguage.SPANISH to "1 Juan")),
        BibleBook("2JN", "2 João", 1, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "2 João", AppLanguage.SPANISH to "2 Juan")),
        BibleBook("3JN", "3 João", 1, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "3 João", AppLanguage.SPANISH to "3 Juan")),
        BibleBook("JUD", "Judas", 1, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Judas", AppLanguage.SPANISH to "Judas")),
        BibleBook("REV", "Apocalipse", 22, Testament.NEW, mapOf(AppLanguage.PORTUGUESE to "Apocalipse", AppLanguage.SPANISH to "Apocalipsis"))
    )

    /**
     * ATENÇÃO: texto placeholder, sem valor de tradução real — ver observação de
     * licença no topo do arquivo e em bible_mvp_TODO.md. Serve apenas para exercitar
     * a leitura, a busca e a navegação por referência detectada.
     */
    private fun placeholderChapter(bookId: String, chapter: Int, verseCount: Int): List<BibleVerse> =
        (1..verseCount).map { v ->
            BibleVerse(bookId, chapter, v, "[Texto do versículo $bookId $chapter:$v — inserir tradução licenciada]")
        }

    val VERSES: List<BibleVerse> = buildList {
        addAll(placeholderChapter("JHN", 3, 36))
        addAll(placeholderChapter("PSA", 23, 6))
        addAll(placeholderChapter("ROM", 8, 39))
        addAll(placeholderChapter("1CO", 13, 13))
        addAll(placeholderChapter("EPH", 2, 22))
    }
}

package com.montanhajr.bereanbible

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DetectionLogicTest {

    @Before
    fun setUp() {
        ExplicitBibleReferenceDetector.resetSession()
    }

    @Test
    fun testConcatenatedNumbersInSpanishAndPortuguese() {
        // Juan 316 -> Juan 3:16
        val matchEs = ExplicitBibleReferenceDetector.detect("juan 316", AppLanguage.SPANISH)
        assertEquals(1, matchEs.size)
        assertEquals("JHN", matchEs[0].reference.bookId)
        assertEquals(3, matchEs[0].reference.chapter)
        assertEquals(16, matchEs[0].reference.startVerse)

        // João 316 -> João 3:16
        val matchPt = ExplicitBibleReferenceDetector.detect("joao 316", AppLanguage.PORTUGUESE)
        assertEquals(1, matchPt.size)
        assertEquals("JHN", matchPt[0].reference.bookId)
        assertEquals(3, matchPt[0].reference.chapter)
        assertEquals(16, matchPt[0].reference.startVerse)

        // Romanos 828 -> Romanos 8:28
        val matchRom = ExplicitBibleReferenceDetector.detect("romanos 828", AppLanguage.PORTUGUESE)
        assertEquals(1, matchRom.size)
        assertEquals("ROM", matchRom[0].reference.bookId)
        assertEquals(8, matchRom[0].reference.chapter)
        assertEquals(28, matchRom[0].reference.startVerse)

        // Salmos 119105 -> Salmos 119:105
        val matchPsa = ExplicitBibleReferenceDetector.detect("salmos 119105", AppLanguage.PORTUGUESE)
        assertEquals(1, matchPsa.size)
        assertEquals("PSA", matchPsa[0].reference.bookId)
        assertEquals(119, matchPsa[0].reference.chapter)
        assertEquals(105, matchPsa[0].reference.startVerse)
    }

    @Test
    fun testSpokenWordNumbers() {
        // Juan tres dieciseis
        val matchEs = ExplicitBibleReferenceDetector.detect("juan tres dieciseis", AppLanguage.SPANISH)
        assertEquals(1, matchEs.size)
        assertEquals("JHN", matchEs[0].reference.bookId)
        assertEquals(3, matchEs[0].reference.chapter)
        assertEquals(16, matchEs[0].reference.startVerse)

        // João três dezesseis
        val matchPt = ExplicitBibleReferenceDetector.detect("joao tres dezesseis", AppLanguage.PORTUGUESE)
        assertEquals(1, matchPt.size)
        assertEquals("JHN", matchPt[0].reference.bookId)
        assertEquals(3, matchPt[0].reference.chapter)
        assertEquals(16, matchPt[0].reference.startVerse)

        // Mateo veintitres uno
        val matchMateo = ExplicitBibleReferenceDetector.detect("mateo veintitres uno", AppLanguage.SPANISH)
        assertEquals(1, matchMateo.size)
        assertEquals("MAT", matchMateo[0].reference.bookId)
        assertEquals(23, matchMateo[0].reference.chapter)
        assertEquals(1, matchMateo[0].reference.startVerse)
    }

    @Test
    fun testPunctuationAndFormatting() {
        val cases = listOf("juan 3,16", "juan 3.16", "juan 3:16")
        for (input in cases) {
            val res = ExplicitBibleReferenceDetector.detect(input, AppLanguage.SPANISH)
            assertEquals("Falha para input: $input", 1, res.size)
            assertEquals("JHN", res[0].reference.bookId)
            assertEquals(3, res[0].reference.chapter)
            assertEquals(16, res[0].reference.startVerse)
        }
    }

    @Test
    fun testBookAliasesAndPrefixes() {
        // San Juan 3 16
        val m1 = ExplicitBibleReferenceDetector.detect("san juan 3 16", AppLanguage.SPANISH)
        assertEquals(1, m1.size)
        assertEquals("JHN", m1[0].reference.bookId)

        // Evangelho segundo João 3 16
        val m2 = ExplicitBibleReferenceDetector.detect("evangelho segundo joao 3 16", AppLanguage.PORTUGUESE)
        assertEquals(1, m2.size)
        assertEquals("JHN", m2[0].reference.bookId)

        // Primeira de João 3 16
        val m3 = ExplicitBibleReferenceDetector.detect("primeira de joao 3 16", AppLanguage.PORTUGUESE)
        assertEquals(1, m3.size)
        assertEquals("1JN", m3[0].reference.bookId)

        // Primera de Juan 3 16
        val m4 = ExplicitBibleReferenceDetector.detect("primera de juan 3 16", AppLanguage.SPANISH)
        assertEquals(1, m4.size)
        assertEquals("1JN", m4[0].reference.bookId)

        // Hechos de los apostoles 2 38
        val m5 = ExplicitBibleReferenceDetector.detect("hechos de los apostoles 2 38", AppLanguage.SPANISH)
        assertEquals(1, m5.size)
        assertEquals("ACT", m5[0].reference.bookId)
    }

    @Test
    fun testSingleChapterBooks() {
        // Judas 24 -> Judas cap 1 vers 24
        val mJudas = ExplicitBibleReferenceDetector.detect("judas 24", AppLanguage.PORTUGUESE)
        assertEquals(1, mJudas.size)
        assertEquals("JUD", mJudas[0].reference.bookId)
        assertEquals(1, mJudas[0].reference.chapter)
        assertEquals(24, mJudas[0].reference.startVerse)

        // 3 João 11
        val m3Jn = ExplicitBibleReferenceDetector.detect("terceira joao 11", AppLanguage.PORTUGUESE)
        assertEquals(1, m3Jn.size)
        assertEquals("3JN", m3Jn[0].reference.bookId)
        assertEquals(1, m3Jn[0].reference.chapter)
        assertEquals(11, m3Jn[0].reference.startVerse)
    }

    @Test
    fun testRangesAndComplexPhrases() {
        // Juan capitulo 3 del 16 al 18
        val m1 = ExplicitBibleReferenceDetector.detect("juan capitulo 3 del 16 al 18", AppLanguage.SPANISH)
        assertEquals(1, m1.size)
        assertEquals("JHN", m1[0].reference.bookId)
        assertEquals(3, m1[0].reference.chapter)
        assertEquals(16, m1[0].reference.startVerse)
        assertEquals(18, m1[0].reference.endVerse)

        // João capítulo 3 do 16 ao 18
        val m2 = ExplicitBibleReferenceDetector.detect("joao capitulo 3 do 16 ao 18", AppLanguage.PORTUGUESE)
        assertEquals(1, m2.size)
        assertEquals("JHN", m2[0].reference.bookId)
        assertEquals(3, m2[0].reference.chapter)
        assertEquals(16, m2[0].reference.startVerse)
        assertEquals(18, m2[0].reference.endVerse)

        // Capitulo 3 versiculo 16 de juan
        val m3 = ExplicitBibleReferenceDetector.detect("capitulo 3 versiculo 16 de juan", AppLanguage.SPANISH)
        assertEquals(1, m3.size)
        assertEquals("JHN", m3[0].reference.bookId)
        assertEquals(3, m3[0].reference.chapter)
        assertEquals(16, m3[0].reference.startVerse)

        // Versiculo 16 do capitulo 3 de joao
        val m4 = ExplicitBibleReferenceDetector.detect("versiculo 16 do capitulo 3 de joao", AppLanguage.PORTUGUESE)
        assertEquals(1, m4.size)
        assertEquals("JHN", m4[0].reference.bookId)
        assertEquals(3, m4[0].reference.chapter)
        assertEquals(16, m4[0].reference.startVerse)
    }

    @Test
    fun testSequentialDeltasAcrossPause() {
        // Simulates receiving deltas from STT
        val r1 = ExplicitBibleReferenceDetector.detect("vamos abrir o livro de joao", AppLanguage.PORTUGUESE)
        assertTrue(r1.isEmpty()) // No complete reference yet

        val r2 = ExplicitBibleReferenceDetector.detect("capitulo 3 versiculo 16", AppLanguage.PORTUGUESE)
        assertEquals(1, r2.size)
        assertEquals("JHN", r2[0].reference.bookId)
        assertEquals(3, r2[0].reference.chapter)
        assertEquals(16, r2[0].reference.startVerse)
    }

    @Test
    fun testResetSession() {
        ExplicitBibleReferenceDetector.detect("livro de joao", AppLanguage.PORTUGUESE)
        ExplicitBibleReferenceDetector.resetSession()
        
        // After reset, sending only chapter 3 versicle 16 without book name should not detect
        val res = ExplicitBibleReferenceDetector.detect("capitulo 3 versiculo 16", AppLanguage.PORTUGUESE)
        assertTrue(res.isEmpty())
    }
}

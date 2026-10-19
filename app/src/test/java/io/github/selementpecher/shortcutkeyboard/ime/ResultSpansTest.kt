package io.github.selementpecher.shortcutkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultSpansTest {
    @Test
    fun aSingleWordIsOneChunk() {
        assertEquals(listOf("Calpol"), ResultSpans.chunksOf("Calpol"))
    }

    @Test
    fun wordsAreSplitSoTheyCanWrap() {
        assertEquals(listOf("Sam ", "Smith"), ResultSpans.chunksOf("Sam Smith"))
        assertEquals(listOf("a ", "b ", "c"), ResultSpans.chunksOf("a b c"))
    }

    @Test
    fun chunksJoinBackToTheValue() {
        val value = "Two  spaces and\nnewline "
        assertEquals(value, ResultSpans.chunksOf(value).joinToString(""))
    }

    @Test
    fun nothingGivesNoChunks() {
        assertEquals(emptyList<String>(), ResultSpans.chunksOf(""))
    }
}

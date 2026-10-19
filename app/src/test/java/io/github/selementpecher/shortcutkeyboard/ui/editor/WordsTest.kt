package io.github.selementpecher.shortcutkeyboard.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class WordsTest {

    private fun words(text: String) = Words.spans(text).map { text.substring(it.start, it.end) }

    @Test
    fun splitsTheExampleMessage() {
        assertEquals(
            listOf("Sam", "Calpol", "5ml", "20:20", "next", "dose", "not", "before", "00:20"),
            words("Sam, Calpol, 5ml, 20:20 next dose not before 00:20"),
        )
    }

    @Test
    fun keepsMarksThatBelongToTheWord() {
        assertEquals(listOf("O'Brien", "paid", "£5", "for", "3.5ml", "and", "1,000", "co-op", "50%"), words("O'Brien paid £5 for 3.5ml and 1,000 co-op 50%"))
        assertEquals(listOf("it", "is", "-5", "degrees"), words("it is -5 degrees"))
        assertEquals(listOf("O’Neil"), words("O’Neil"))
    }

    @Test
    fun dropsPunctuationAtTheEdges() {
        assertEquals(listOf("Hello", "Sam"), words("Hello, Sam!"))
        assertEquals(listOf("at", "20:20"), words("(at 20:20)."))
        assertEquals(listOf("Time"), words("Time: "))
        assertEquals(listOf("one", "two"), words("one,two"))
        assertEquals(listOf("etc"), words("etc..."))
    }

    @Test
    fun emojiAndSymbolsAloneAreNotWords() {
        assertEquals(emptyList<String>(), words("💊 - , . ! ? ... £ %"))
    }

    @Test
    fun emptyTextHasNoWords() {
        assertEquals(emptyList<Words.Span>(), Words.spans(""))
    }
}

package io.github.selementpecher.shortcutkeyboard.ui.editor

import io.github.selementpecher.shortcutkeyboard.ui.ShortcutDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlankQuestionsTest {

    private fun clean(raw: String, kind: BlankKind = BlankKind.LIST, taken: Set<String> = emptySet()) =
        BlankQuestions.clean(raw, kind, taken)

    @Test
    fun keepsAnOrdinaryQuestion() {
        assertEquals("How much", clean("  How   much "))
    }

    @Test
    fun removesBracketsAndBars() {
        assertEquals("Who is it", clean("{Who} | is\nit"))
    }

    @Test
    fun anEmptyQuestionBecomesTheDefaultForTheKind() {
        assertEquals("Which", clean(" "))
        assertEquals("When", clean("", BlankKind.TIME))
        assertEquals("Date", clean("{}", BlankKind.DATE))
        assertEquals("How many", clean("|", BlankKind.NUMBER))
    }

    @Test
    fun nothingThatReadsLikeAnOffset() {
        val cleaned = clean("Dose +3")
        assertNull(ShortcutDraft.checkBlankName(cleaned))
        assertEquals("Dose 3", cleaned)
        assertNull(ShortcutDraft.checkBlankName(clean("-5 kids")))
        assertNull(ShortcutDraft.checkBlankName(clean("+ 4h")))
    }

    @Test
    fun notNowOrToday() {
        assertEquals("now\u200B", clean("now"))
        assertEquals("Today\u200B", clean("Today", BlankKind.DATE))
    }

    @Test
    fun differentFromTheOthersIgnoringCase() {
        assertEquals("who\u200B", clean("who", taken = setOf("Who")))
        assertEquals("Who\u200B\u200B", clean("Who", taken = setOf("who", "Who\u200B")))
    }

    @Test
    fun longQuestionsAreShortened() {
        assertEquals(40, clean("a".repeat(100)).length)
    }

    @Test
    fun everySuggestionIsUsable() {
        for (kind in BlankKind.entries) for (q in BlankQuestions.suggestions(kind)) {
            assertEquals(q, clean(q, kind))
        }
    }
}

class DurationPartsTest {
    @Test
    fun splitsIntoDaysHoursAndMinutes() {
        assertEquals(DurationParts(0, 4, 0), DurationParts.of(240))
        assertEquals(DurationParts(0, 1, 30), DurationParts.of(-90))
        assertEquals(DurationParts(1, 1, 5), DurationParts.of(24 * 60 + 65))
        assertEquals(DurationParts(0, 0, 0), DurationParts.of(0))
    }
}

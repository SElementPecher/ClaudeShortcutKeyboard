package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.data.SampleShortcuts
import io.github.selementpecher.shortcutkeyboard.ui.editor.MessageDraft
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleTextTest {
    private val draft = MessageDraft.fromShortcut(
        SampleShortcuts.all.first { it.id == "sample-medicine" },
        LocalDateTime.of(2026, 10, 16, 20, 20),
        Locale.UK,
    )

    @Test fun partsJoinToThePlainMessage() {
        val parts = ExampleParts.of(draft)
        assertEquals(draft.plainText(), ExampleParts.plain(parts))
    }

    @Test fun changingWordsAreMarkedAndFixedWordsAreNot() {
        val parts = ExampleParts.of(draft)
        assertTrue(parts.first { it.text == "Sam" }.changes)
        assertFalse(parts.first { it.text.contains("next dose") }.changes)
    }

    @Test fun sameBlankKeepsItsColourNumber() {
        val times = ExampleParts.of(draft).filter { it.text == "20:20" || it.text == "00:20" }
        assertEquals(2, times.size)
        assertEquals(times[0].blank, times[1].blank)
    }

    @Test fun questionsGetNumbersInOrder() {
        val blanks = ExampleParts.of(draft).filter { it.changes }.map { it.blank }.distinct()
        assertEquals(listOf(0, 1, 2, 3), blanks)
    }
}

package io.github.selementpecher.shortcutkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberEntryTest {
    private fun NumberEntry.type(keys: String) = keys.forEach { press(it) }

    @Test
    fun typesDigits() {
        val entry = NumberEntry()
        entry.type("125")
        assertEquals("125", entry.text)
    }

    @Test
    fun pointOnlyOnce() {
        val entry = NumberEntry()
        entry.type("2.5.5")
        assertEquals("2.55", entry.text)
    }

    @Test
    fun pointFirstGivesZeroPoint() {
        val entry = NumberEntry()
        entry.type(".5")
        assertEquals("0.5", entry.text)
    }

    @Test
    fun noLeadingZeros() {
        val entry = NumberEntry()
        entry.type("05")
        assertEquals("5", entry.text)
        val zero = NumberEntry()
        zero.type("00")
        assertEquals("0", zero.text)
    }

    @Test
    fun stopsAtEightCharacters() {
        val entry = NumberEntry()
        entry.type("1234567890")
        assertEquals("12345678", entry.text)
        entry.press('.')
        assertEquals("12345678", entry.text)
    }

    @Test
    fun backspaceRemovesOneAndIsSafeWhenEmpty() {
        val entry = NumberEntry("12")
        entry.backspace()
        assertEquals("1", entry.text)
        entry.backspace()
        entry.backspace()
        assertEquals("", entry.text)
    }

    @Test
    fun startsWithTheLastValueWhenItLooksLikeANumber() {
        assertEquals("5.5", NumberEntry("5.5").text)
        assertEquals("", NumberEntry("5ml").text)
        assertEquals("", NumberEntry(null).text)
        assertEquals("", NumberEntry("123456789").text)
    }

    @Test
    fun ignoresOtherKeys() {
        val entry = NumberEntry()
        entry.type("a-1")
        assertEquals("1", entry.text)
    }
}

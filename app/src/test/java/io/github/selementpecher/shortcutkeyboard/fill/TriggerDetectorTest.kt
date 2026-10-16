package io.github.selementpecher.shortcutkeyboard.fill

import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Plain JVM tests for spotting a trigger word just before the cursor. */
class TriggerDetectorTest {

    private val meds = Shortcut(id = "meds", name = "Medicine given", trigger = "/meds", template = "{child}")
    private val late = Shortcut(id = "late", name = "Running late", trigger = "/late", template = "Running late")
    private val noTrigger = Shortcut(id = "plain", name = "No trigger", template = "Hello")
    private val shortcuts = listOf(noTrigger, meds, late)

    private fun find(text: CharSequence?, list: List<Shortcut> = shortcuts) = TriggerDetector.find(text, list)

    @Test
    fun `finds a trigger that is the whole text`() {
        val match = find("/meds")!!
        assertSame(meds, match.shortcut)
        assertEquals(5, match.length)
    }

    @Test
    fun `finds a trigger after other words`() {
        val match = find("Hello there /late")!!
        assertSame(late, match.shortcut)
        assertEquals(5, match.length)
    }

    @Test
    fun `finds a trigger after a line break or tab`() {
        assertEquals(5, find("Hello\n/meds")?.length)
        assertEquals(5, find("Hello\t/meds")?.length)
    }

    @Test
    fun `triggers are matched without case`() {
        val match = find("/MEDS")!!
        assertSame(meds, match.shortcut)
        assertEquals(5, match.length)
    }

    @Test
    fun `part of a trigger does not match`() {
        assertNull(find("/med"))
        assertNull(find("/medsx"))
        assertNull(find("x/meds"))
        assertNull(find("meds"))
    }

    @Test
    fun `no match once a space has been typed after the word`() {
        assertNull(find("/meds "))
        assertNull(find("/meds\n"))
    }

    @Test
    fun `no match for null or empty text`() {
        assertNull(find(null))
        assertNull(find(""))
        assertNull(find("   "))
    }

    @Test
    fun `no match when there are no shortcuts`() {
        assertNull(find("/meds", emptyList()))
    }

    @Test
    fun `shortcuts without a trigger never match`() {
        val blank = Shortcut(id = "blank", name = "Blank", trigger = "   ", template = "x")
        assertNull(find("Hello", listOf(noTrigger, blank)))
        assertNull(find("", listOf(noTrigger, blank)))
    }

    @Test
    fun `spaces around a saved trigger are ignored`() {
        val spaced = Shortcut(id = "spaced", name = "Spaced", trigger = "  /meds ", template = "x")
        val match = find("ok /meds", listOf(spaced))!!
        assertSame(spaced, match.shortcut)
        assertEquals(5, match.length)
    }

    @Test
    fun `the first shortcut with the trigger wins`() {
        val copy = meds.copy(id = "copy")
        assertSame(meds, find("/meds", listOf(meds, copy))?.shortcut)
        assertSame(copy, find("/meds", listOf(copy, meds))?.shortcut)
    }

    @Test
    fun `length counts characters the way the text field does`() {
        // An emoji is two UTF-16 chars, which is what deleteSurroundingText counts.
        val smile = Shortcut(id = "smile", name = "Smile", trigger = "🙂", template = "Thank you!")
        val match = find("hi 🙂", listOf(smile))!!
        assertSame(smile, match.shortcut)
        assertEquals(2, match.length)
    }

    @Test
    fun `triggers without a slash work too`() {
        val word = Shortcut(id = "word", name = "Word", trigger = "omw", template = "On my way!")
        assertEquals(3, find("I am omw", listOf(word))?.length)
    }

    @Test
    fun `works with any CharSequence`() {
        val match = find(StringBuilder("text /late"))!!
        assertSame(late, match.shortcut)
        assertEquals(5, match.length)
    }
}

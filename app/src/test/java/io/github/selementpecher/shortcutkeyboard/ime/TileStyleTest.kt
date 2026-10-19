package io.github.selementpecher.shortcutkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileStyleTest {
    @Test
    fun colourIndexIsStableAndInRange() {
        val ids = (1..200).map { "id-$it" }
        for (id in ids) {
            val index = TileStyle.colourIndex(id)
            assertTrue(index in 0 until TileStyle.COLOUR_COUNT)
            assertEquals(index, TileStyle.colourIndex(id))
        }
        // All six colours get used.
        assertEquals(TileStyle.COLOUR_COUNT, ids.map { TileStyle.colourIndex(it) }.toSet().size)
    }

    @Test
    fun colourIndexHandlesAwkwardHashes() {
        // "polygenelubricants" has Int.MIN_VALUE as its hash code.
        assertTrue(TileStyle.colourIndex("polygenelubricants") in 0 until TileStyle.COLOUR_COUNT)
        assertTrue(TileStyle.colourIndex("") in 0 until TileStyle.COLOUR_COUNT)
    }

    @Test
    fun folderLabelStartsWithTheFolderEmoji() {
        assertEquals("📁 Kids", TileStyle.folderLabel("Kids"))
    }

    @Test
    fun darkenKeepsAlphaAndScalesChannels() {
        assertEquals(0xFF000000.toInt(), TileStyle.darken(0xFFFFFFFF.toInt(), 1f))
        assertEquals(0x80646464.toInt(), TileStyle.darken(0x80C8C8C8.toInt(), 0.5f))
        assertEquals(0xFF123456.toInt(), TileStyle.darken(0xFF123456.toInt(), 0f))
    }

    @Test
    fun pathTextJoinsAndCutsFromTheStart() {
        assertEquals("Kids › Meds", TileStyle.pathText(listOf("Kids", "Meds")))
        assertEquals("", TileStyle.pathText(emptyList()))
        val cut = TileStyle.pathText(listOf("A long folder name", "Another long name", "Meds"), maxChars = 20)
        assertEquals(20, cut.length)
        assertTrue(cut.startsWith("…"))
        assertTrue(cut.endsWith("Meds"))
    }
}

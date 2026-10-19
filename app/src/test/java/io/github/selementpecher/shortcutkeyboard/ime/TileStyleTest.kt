package io.github.selementpecher.shortcutkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileStyleTest {
    @Test
    fun glyphIsTheChosenPictureElseTheFirstLetterInCapitals() {
        assertEquals("💊", TileStyle.glyphFor("💊", "Medicine given", isFolder = false))
        assertEquals("M", TileStyle.glyphFor("", "medicine given", isFolder = false))
        assertEquals("S", TileStyle.glyphFor("  ", "  sam", isFolder = false))
        assertEquals("•", TileStyle.glyphFor("", "", isFolder = false))
    }

    @Test
    fun glyphKeepsAnEmojiNameWhole() {
        // A first "letter" outside the basic plane must not be cut in half.
        assertEquals("🙂", TileStyle.glyphFor("", "🙂 happy", isFolder = false))
    }

    @Test
    fun foldersDefaultToTheFolderEmojiAndOnlyGetABadgeWhenTheyHaveAnotherPicture() {
        assertEquals("📁", TileStyle.glyphFor("", "Kids", isFolder = true))
        assertEquals("🧒", TileStyle.glyphFor("🧒", "Kids", isFolder = true))
        assertNull(TileStyle.folderBadge("📁"))
        assertEquals("📁", TileStyle.folderBadge("🧒"))
    }

    @Test
    fun darkenAndLightenKeepAlphaAndScaleChannels() {
        assertEquals(0xFF000000.toInt(), TileStyle.darken(0xFFFFFFFF.toInt(), 1f))
        assertEquals(0x80646464.toInt(), TileStyle.darken(0x80C8C8C8.toInt(), 0.5f))
        assertEquals(0xFF123456.toInt(), TileStyle.darken(0xFF123456.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), TileStyle.lighten(0xFF123456.toInt(), 1f))
    }

    @Test
    fun pressedGoesTheWayThatShows() {
        val light = 0xFFF9E6B0.toInt()
        val dark = 0xFF1F242B.toInt()
        assertTrue(TileStyle.luminance(TileStyle.pressed(light)) < TileStyle.luminance(light))
        assertTrue(TileStyle.luminance(TileStyle.pressed(dark)) > TileStyle.luminance(dark))
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

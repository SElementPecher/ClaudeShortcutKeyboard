package io.github.selementpecher.shortcutkeyboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GlyphTest {
    @Test fun iconWins() = assertEquals("💊", glyphFor("💊", "Medicine"))
    @Test fun firstLetterUpper() = assertEquals("R", glyphFor("", "  running late"))
    @Test fun folderDefault() = assertEquals("📁", glyphFor("", "Kids", isFolder = true))
    @Test fun noNameBullet() = assertEquals("•", glyphFor("", "  "))
}

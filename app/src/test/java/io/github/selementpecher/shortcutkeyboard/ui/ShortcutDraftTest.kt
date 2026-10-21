package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.data.SampleShortcuts
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutDraftTest {

    @Test
    fun optionsAreCleaned() {
        assertEquals(listOf("Sam", "Ben"), ShortcutDraft.cleanOptions(listOf(" Sam ", "", "sam", "Ben", "  ")))
    }

    @Test
    fun normaliseKeepsFolderPositionAndPictureAndIsStable() {
        val stored = Shortcut(
            "id", "Meds", "{child} at {time}",
            listOf(VariableDef("child", options = listOf("Sam", " ", "sam")), VariableDef("time", VariableType.TIME)),
            parentId = "kids", order = 2, icon = "💊",
        )
        val normal = ShortcutDraft.normalise(stored)
        assertEquals("kids", normal.parentId)
        assertEquals(2, normal.order)
        assertEquals("💊", normal.icon)
        assertEquals(listOf("Sam"), normal.variables.first().options)
        assertEquals(normal, ShortcutDraft.normalise(normal))
    }

    @Test
    fun normaliseKeepsTheColourSoAnUnchangedQuiKeyHasNoChanges() {
        val stored = Shortcut("id", "Call back", "Call you back", parentId = null, order = 0, icon = "📞", color = 3)
        assertEquals(3, ShortcutDraft.normalise(stored).color)
    }

    @Test
    fun answersTheKeyboardAddedWhileEditingAreKept() {
        val child = VariableDef("child", options = listOf("Sam", "Alex"), lastValue = "Sam")
        val original = Shortcut("id", "Meds", "{child}", listOf(child), parentId = "kids", order = 1)
        // Meanwhile the keyboard added Max (and learned it) and the tile was moved.
        val latest = original.copy(variables = listOf(child.copy(options = listOf("Sam", "Alex", "Max"), lastValue = "Max")), parentId = null, order = 4)
        // The editor renamed it and added Jo.
        val edited = original.copy(name = "Medicine", variables = listOf(child.copy(options = listOf("Sam", "Alex", "Jo"))))
        val saved = ShortcutDraft.keepOutsideChanges(edited, original, latest)
        assertEquals("Medicine", saved.name)
        assertEquals(listOf("Sam", "Alex", "Jo", "Max"), saved.variables.single().options)
        assertEquals("Max", saved.variables.single().lastValue)
        assertNull(saved.parentId)
        assertEquals(4, saved.order)
    }

    @Test
    fun withNoOutsideChangesTheEditWinsAsIs() {
        val original = Shortcut("id", "A", "{x}", listOf(VariableDef("x", options = listOf("1"))))
        val edited = original.copy(variables = listOf(VariableDef("x", options = listOf("2"))))
        assertEquals(edited, ShortcutDraft.keepOutsideChanges(edited, original, original))
        assertEquals(edited, ShortcutDraft.keepOutsideChanges(edited, null, null))
    }

    @Test
    fun normaliseKeepsTheSampleShortcutsAsTheyAre() {
        for (sample in SampleShortcuts.all) {
            val normal = ShortcutDraft.normalise(sample)
            assertEquals(sample.template, normal.template)
            assertEquals(sample.variables.map { it.name to it.type }, normal.variables.map { it.name to it.type })
        }
    }

    @Test
    fun blankNamesAreChecked() {
        assertEquals(ShortcutDraft.NameProblem.EMPTY, ShortcutDraft.checkBlankName(" "))
        assertEquals(ShortcutDraft.NameProblem.BUILT_IN, ShortcutDraft.checkBlankName("today"))
        assertEquals(ShortcutDraft.NameProblem.INVALID, ShortcutDraft.checkBlankName("a{b"))
        assertEquals(ShortcutDraft.NameProblem.INVALID, ShortcutDraft.checkBlankName("time+4h"))
        assertEquals(ShortcutDraft.NameProblem.INVALID, ShortcutDraft.checkBlankName("a|b"))
        assertNull(ShortcutDraft.checkBlankName("How much"))
    }
}

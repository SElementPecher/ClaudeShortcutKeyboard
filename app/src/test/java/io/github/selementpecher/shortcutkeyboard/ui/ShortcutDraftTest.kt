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

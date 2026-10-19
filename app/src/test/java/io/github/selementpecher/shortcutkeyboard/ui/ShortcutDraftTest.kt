package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutDraftTest {

    @Test
    fun buildKeepsFolderAndPosition() {
        val built = ShortcutDraft.build("id", "  Meds ", "{child} at {time}", parentId = "kids", order = 3) { VariableDef(it) }
        assertEquals("Meds", built.name)
        assertEquals("kids", built.parentId)
        assertEquals(3, built.order)
        assertEquals(listOf("child", "time"), built.variables.map { it.name })
    }

    @Test
    fun buildDefaultsToTheTopLevel() {
        val built = ShortcutDraft.build("id", "A", "hi") { VariableDef(it) }
        assertNull(built.parentId)
        assertEquals(0, built.order)
    }

    @Test
    fun normaliseKeepsFolderAndPositionAndIsStable() {
        val stored = Shortcut(
            "id", "Meds", "{child} at {time}",
            listOf(VariableDef("child", options = listOf("Sam", " ", "sam")), VariableDef("time", VariableType.TIME)),
            parentId = "kids", order = 2,
        )
        val normal = ShortcutDraft.normalise(stored)
        assertEquals("kids", normal.parentId)
        assertEquals(2, normal.order)
        assertEquals(listOf("Sam"), normal.variables.first().options)
        assertEquals(normal, ShortcutDraft.normalise(normal))
    }

    @Test
    fun blankNamesAreChecked() {
        assertEquals(ShortcutDraft.NameProblem.EMPTY, ShortcutDraft.checkBlankName(" "))
        assertEquals(ShortcutDraft.NameProblem.BUILT_IN, ShortcutDraft.checkBlankName("today"))
        assertNull(ShortcutDraft.checkBlankName("child"))
    }
}

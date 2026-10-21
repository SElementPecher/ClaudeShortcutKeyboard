package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoShortcutsTest {
    @Test
    fun everyKindOfChangingPartHasADemo() {
        val used = DemoShortcuts.all.flatMap { it.variables }.map { it.type }.toSet()
        assertEquals(VariableType.entries.toSet(), used)
    }

    @Test
    fun theListDemoHasThreeLines() {
        val list = DemoShortcuts.all.first { it.name == "List" }
        assertEquals(listOf("Line 1", "Line 2", "Line 3"), list.variables.single().options)
        assertEquals("{List}", list.template)
    }

    @Test
    fun idsAreUniqueAndTemplatesUseTheirVariables() {
        assertEquals(DemoShortcuts.all.size, DemoShortcuts.all.map { it.id }.toSet().size)
        for (s in DemoShortcuts.all) for (v in s.variables) assertTrue(s.name, s.template.contains("{" + v.name))
    }

    @Test
    fun loadingTwiceAddsThemOnce() {
        val mine = SampleShortcuts.library
        val once = mine.withExamples(DemoShortcuts.library)
        val twice = once.withExamples(DemoShortcuts.library)
        assertEquals(mine.shortcuts.size + DemoShortcuts.all.size, once.shortcuts.size)
        assertEquals(once.shortcuts.size, twice.shortcuts.size)
        assertEquals(once.folders.size, twice.folders.size)
        assertEquals(Library().withExamples(DemoShortcuts.library).shortcuts.size, DemoShortcuts.all.size)
    }
}

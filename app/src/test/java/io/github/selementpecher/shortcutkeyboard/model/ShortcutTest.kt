package io.github.selementpecher.shortcutkeyboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plain JVM tests for the small model types. */
class ShortcutTest {

    @Test
    fun `type ids read back to the same type`() {
        for (type in VariableType.entries) {
            assertEquals(type, VariableType.fromId(type.id))
        }
    }

    @Test
    fun `unknown or missing type ids read as a list`() {
        assertEquals(VariableType.CHOICE, VariableType.fromId(null))
        assertEquals(VariableType.CHOICE, VariableType.fromId(""))
        assertEquals(VariableType.CHOICE, VariableType.fromId("colour"))
    }

    @Test
    fun `type ids are stable because backups use them`() {
        assertEquals(
            listOf("choice", "text", "number", "time", "date"),
            VariableType.entries.map { it.id },
        )
    }

    @Test
    fun `a blank key is trimmed and lower case`() {
        assertEquals("next dose", VariableDef(" Next Dose ").key)
        assertEquals("child", VariableDef.keyOf("CHILD"))
    }

    @Test
    fun `new blanks default to a remembered list`() {
        val def = VariableDef("child")
        assertEquals(VariableType.CHOICE, def.type)
        assertEquals(emptyList<String>(), def.options)
        assertEquals(true, def.remember)
        assertNull(def.lastValue)
    }

    @Test
    fun `variable finds a blank without case`() {
        val child = VariableDef("Child")
        val shortcut = Shortcut(id = "a", name = "A", template = "{child}", variables = listOf(child, VariableDef("time")))
        assertEquals(child, shortcut.variable("child"))
        assertEquals(child, shortcut.variable(" CHILD "))
        assertNull(shortcut.variable("dose"))
    }
}

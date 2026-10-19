package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingFillTest {
    private val now = LocalDateTime.of(2026, 10, 16, 20, 20)
    private val original = Shortcut(
        id = "meds",
        name = "Medicine given",
        template = "{child}, {medicine} at {time}",
        variables = listOf(
            VariableDef("child", options = listOf("Ann", "Bo")),
            VariableDef("medicine", options = listOf("Calpol")),
            VariableDef("time", VariableType.TIME),
        ),
    )

    private fun captured(index: Int, blank: String, values: Map<String, String>): PendingFill {
        val session = FillSession(original, { now }, Locale.UK)
        session.variables.forEach { v -> values[v.key]?.let { session.setValue(v, it) } }
        session.goTo(index)
        val asked = session.variables.first { it.key == blank }
        return PendingFill.capture(session, blank, asked.options, nowMs = 1000)
    }

    @Test
    fun freshOnlyForTenMinutes() {
        val p = captured(0, "child", emptyMap())
        assertTrue(p.isFresh(1000))
        assertTrue(p.isFresh(1000 + PendingFill.MAX_AGE_MS))
        assertFalse(p.isFresh(1001 + PendingFill.MAX_AGE_MS))
        assertFalse(p.isFresh(500))
    }

    @Test
    fun captureKeepsValuesAndPlace() {
        val p = captured(1, "medicine", mapOf("child" to "Ann"))
        assertEquals("meds", p.shortcutId)
        assertEquals(1, p.index)
        assertEquals("Ann", p.values["child"])
        assertEquals(listOf("Calpol"), p.optionsBefore)
    }

    @Test
    fun restoreWithoutANewAnswerStaysOnTheSameQuestion() {
        val p = captured(1, "medicine", mapOf("child" to "Ann"))
        val restored = p.restore(original, Locale.UK)
        assertEquals(1, restored.session.index)
        assertFalse(restored.finished)
        assertEquals("Ann", restored.session.valueOf(restored.session.variables[0]))
    }

    @Test
    fun restoreUsesTheNewAnswerAndMovesOn() {
        val p = captured(1, "medicine", mapOf("child" to "Ann"))
        val latest = original.copy(
            variables = original.variables.map {
                if (it.key == "medicine") it.copy(options = it.options + "Nurofen", lastValue = "Nurofen") else it
            },
        )
        val restored = p.restore(latest, Locale.UK)
        val session = restored.session
        assertEquals("Nurofen", session.valueOf(session.variables[1]))
        assertEquals("Ann", session.valueOf(session.variables[0]))
        assertEquals(2, session.index)
        assertFalse(restored.finished)
    }

    @Test
    fun newAnswerOnTheLastQuestionGoesToTheCheck() {
        val template = Shortcut("s", "Short", "{child}", listOf(VariableDef("child", options = listOf("Ann"))))
        val session = FillSession(template, { now }, Locale.UK)
        val p = PendingFill.capture(session, "child", listOf("Ann"), nowMs = 0)
        val latest = template.copy(variables = listOf(VariableDef("child", options = listOf("Ann", "Zed"), lastValue = "Zed")))
        val restored = p.restore(latest, Locale.UK)
        assertTrue(restored.finished)
        assertEquals("Zed", restored.session.result())
    }

    @Test
    fun restoreSurvivesABlankThatWasRemoved() {
        val p = captured(2, "time", mapOf("child" to "Ann", "time" to "19:00"))
        val latest = original.copy(template = "{child} only")
        val restored = p.restore(latest, Locale.UK)
        assertEquals("Ann only", restored.session.result())
        assertFalse(restored.finished)
    }
}

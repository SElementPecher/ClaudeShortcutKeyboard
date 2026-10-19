package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FillChoicesTest {
    private val now = LocalDateTime.of(2026, 10, 16, 20, 20)

    private fun session(template: String, vararg variables: VariableDef) =
        FillSession(Shortcut("s", "Test", template, variables.toList()), { now }, Locale.UK)

    @Test
    fun lastAnswerComesFirstAndIsMarked() {
        val answers = AnswerList.build(VariableDef("child", options = listOf("Ann", "Sam", "Bo"), lastValue = "Sam"))
        assertEquals(listOf("Sam", "Ann", "Bo"), answers.map { it.text })
        assertEquals(listOf(true, false, false), answers.map { it.isLast })
    }

    @Test
    fun answersHaveNoRepeatsOrBlanks() {
        val answers = AnswerList.build(VariableDef("child", options = listOf("Ann", "ann", " ", "Bo"), lastValue = "ANN"))
        assertEquals(listOf("ANN", "Bo"), answers.map { it.text })
    }

    @Test
    fun noAnswersGivesAnEmptyList() {
        assertTrue(AnswerList.build(VariableDef("child")).isEmpty())
    }

    @Test
    fun lastValueNotInOptionsIsStillOffered() {
        val answers = AnswerList.build(VariableDef("note", type = VariableType.TEXT, lastValue = "ok"))
        assertEquals(listOf(Answer("ok", true)), answers)
    }

    @Test
    fun hasNewOptionIgnoresCapitals() {
        assertFalse(AnswerList.hasNewOption(listOf("Ann"), listOf("ann")))
        assertTrue(AnswerList.hasNewOption(listOf("Ann"), listOf("Ann", "Sam")))
        assertFalse(AnswerList.hasNewOption(emptyList(), listOf(" ")))
    }

    @Test
    fun timeChoicesCountBackFromNow() {
        val s = session("{time}", VariableDef("time", VariableType.TIME))
        val time = s.variables.single()
        TimeChoices.pick(s, time, 0)
        assertEquals("20:20", s.valueOf(time))
        TimeChoices.pick(s, time, 15)
        assertEquals("20:05", s.valueOf(time))
        TimeChoices.pick(s, time, 30)
        assertEquals("19:50", s.valueOf(time))
        TimeChoices.pick(s, time, 60)
        assertEquals("19:20", s.valueOf(time))
    }

    @Test
    fun timeChoicesWrapPastMidnight() {
        val early = FillSession(
            Shortcut("s", "Test", "{time}", listOf(VariableDef("time", VariableType.TIME))),
            { LocalDateTime.of(2026, 10, 16, 0, 10) },
            Locale.UK,
        )
        TimeChoices.pick(early, early.variables.single(), 30)
        assertEquals("23:40", early.valueOf(early.variables.single()))
    }

    @Test
    fun dateChoicesAreRelativeToToday() {
        val s = session("{date}", VariableDef("date", VariableType.DATE))
        val date = s.variables.single()
        DateChoices.pick(s, date, LocalDate.of(2026, 10, 16), -1)
        assertEquals("2026-10-15", s.valueOf(date))
        DateChoices.pick(s, date, LocalDate.of(2026, 10, 16), 1)
        assertEquals("2026-10-17", s.valueOf(date))
    }

    @Test
    fun laterTextUsesTheFirstOffsetReference() {
        val template = Template.parse("{time} next {time+4h}")
        val time = VariableDef("time", VariableType.TIME)
        assertEquals("00:20", TimeChoices.laterText(template, time, "20:20"))
    }

    @Test
    fun laterTextIsNullWithoutAnOffsetOrTime() {
        val time = VariableDef("time", VariableType.TIME)
        assertNull(TimeChoices.laterText(Template.parse("{time}"), time, "20:20"))
        assertNull(TimeChoices.laterText(Template.parse("{time+1h}"), time, ""))
        assertNull(TimeChoices.laterText(Template.parse("{other+1h}"), time, "20:20"))
    }
}

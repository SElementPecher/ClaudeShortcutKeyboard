package io.github.selementpecher.shortcutkeyboard.fill

import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Plain JVM tests for filling in a shortcut's blanks one at a time. */
class FillSessionTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 16, 20, 20)

    private val medicine = Shortcut(
        id = "medicine",
        name = "Medicine given",
        template = "{child}, {medicine}, {dose}, {time} next dose not before {time+4h}",
        variables = listOf(
            VariableDef("child", VariableType.CHOICE, options = listOf("Sam")),
            VariableDef("medicine", VariableType.CHOICE, options = listOf("Calpol", "Nurofen")),
            VariableDef("dose", VariableType.CHOICE, options = listOf("2.5ml", "5ml")),
            VariableDef("time", VariableType.TIME),
        ),
    )

    private fun session(shortcut: Shortcut, clock: () -> LocalDateTime = { now }) =
        FillSession(shortcut, clock, Locale.UK)

    private fun shortcut(template: String, vararg variables: VariableDef) =
        Shortcut(id = "test", name = "Test", template = template, variables = variables.toList())

    private fun FillSession.blank(name: String): VariableDef =
        variables.first { it.key == VariableDef.keyOf(name) }

    private fun Shortcut.def(name: String): VariableDef =
        requireNotNull(variable(name)) { "no blank called $name" }

    // ---- Which blanks are asked, in what order ----

    @Test
    fun `blanks are asked in the order they first appear`() {
        val s = session(shortcut("{b} then {a} then {b} and {c+1h}"))
        assertEquals(listOf("b", "a", "c"), s.variables.map { it.name })
    }

    @Test
    fun `saved settings are used and missing ones are guessed`() {
        val child = VariableDef("Child", VariableType.CHOICE, options = listOf("Sam", "Alex"))
        val s = session(shortcut("{child} {when} {date} {count} {note} {dose+4h}", child))
        assertSame(child, s.variables[0])
        assertEquals(
            listOf(
                VariableType.CHOICE,
                VariableType.TIME,
                VariableType.DATE,
                VariableType.NUMBER,
                VariableType.CHOICE,
                VariableType.TIME,
            ),
            s.variables.map { it.type },
        )
    }

    @Test
    fun `resolveVariables matches the session`() {
        assertEquals(session(medicine).variables, FillSession.resolveVariables(medicine))
    }

    @Test
    fun `saved blanks that are no longer in the message are not asked`() {
        val s = session(shortcut("{child}", VariableDef("old"), VariableDef("child")))
        assertEquals(listOf("child"), s.variables.map { it.name })
    }

    @Test
    fun `built-ins are never asked`() {
        val s = session(shortcut("{now} {today} {child}"))
        assertEquals(listOf("child"), s.variables.map { it.name })
    }

    // ---- Starting values ----

    @Test
    fun `a time starts at now`() {
        val s = session(medicine)
        assertEquals("20:20", s.valueOf(s.blank("time")))
    }

    @Test
    fun `a date starts at today`() {
        val s = session(shortcut("{date}"))
        assertEquals("2026-10-16", s.valueOf(s.blank("date")))
        assertEquals("16 Oct 2026", s.displayValue(s.blank("date")))
    }

    @Test
    fun `a list starts with the last value used`() {
        val s = session(shortcut("{child}", VariableDef("child", options = listOf("Sam", "Alex"), lastValue = "Alex")))
        assertEquals("Alex", s.valueOf(s.blank("child")))
    }

    @Test
    fun `a list with no last value starts empty`() {
        val s = session(medicine)
        assertNull(s.valueOf(s.blank("child")))
    }

    @Test
    fun `a blank last value is ignored`() {
        val s = session(shortcut("{child}", VariableDef("child", lastValue = "  ")))
        assertNull(s.valueOf(s.blank("child")))
    }

    @Test
    fun `a number starts with the last value used`() {
        val s = session(shortcut("{count}", VariableDef("count", VariableType.NUMBER, lastValue = "3")))
        assertEquals("3", s.valueOf(s.blank("count")))
    }

    @Test
    fun `typed text starts empty`() {
        val s = session(shortcut("{note}", VariableDef("note", VariableType.TEXT, lastValue = "hello")))
        assertNull(s.valueOf(s.blank("note")))
    }

    // ---- Moving between blanks ----

    @Test
    fun `next and back move one blank at a time`() {
        val s = session(medicine)
        assertEquals(0, s.index)
        assertEquals("child", s.current?.name)
        assertTrue(s.isFirst)
        assertFalse(s.isLast)

        assertTrue(s.next())
        assertEquals("medicine", s.current?.name)
        assertFalse(s.isFirst)
        assertTrue(s.next())
        assertTrue(s.next())
        assertEquals("time", s.current?.name)
        assertTrue(s.isLast)

        assertFalse(s.next())
        assertEquals(3, s.index)

        assertTrue(s.back())
        assertTrue(s.back())
        assertTrue(s.back())
        assertEquals(0, s.index)
        assertFalse(s.back())
        assertEquals(0, s.index)
    }

    @Test
    fun `goTo stays inside the list`() {
        val s = session(medicine)
        s.goTo(2)
        assertEquals("dose", s.current?.name)
        s.goTo(-5)
        assertEquals(0, s.index)
        s.goTo(99)
        assertEquals(3, s.index)
        assertTrue(s.isLast)
    }

    @Test
    fun `a single blank is both first and last`() {
        val s = session(shortcut("{child}"))
        assertTrue(s.isFirst)
        assertTrue(s.isLast)
        assertFalse(s.next())
        assertFalse(s.back())
    }

    @Test
    fun `a shortcut without blanks is empty and safe to use`() {
        val s = session(shortcut("Hello at {now}"))
        assertTrue(s.isEmpty)
        assertNull(s.current)
        assertTrue(s.isFirst)
        assertTrue(s.isLast)
        assertFalse(s.next())
        assertFalse(s.back())
        s.goTo(3)
        assertEquals(0, s.index)
        assertEquals("Hello at 20:20", s.preview())
        assertEquals("Hello at 20:20", s.result())
        assertEquals(s.shortcut, s.updatedShortcut())
    }

    // ---- Values ----

    @Test
    fun `values are matched without case`() {
        val s = session(medicine)
        s.setValue(VariableDef("CHILD"), "Sam")
        assertEquals("Sam", s.valueOf(s.blank("child")))
        assertEquals("Sam", s.valueOf(VariableDef("Child")))
    }

    @Test
    fun `adjustTime wraps around midnight both ways`() {
        val s = session(medicine)
        val time = s.blank("time")
        s.adjustTime(time, 240)
        assertEquals("00:20", s.valueOf(time))
        s.adjustTime(time, -15)
        assertEquals("00:05", s.valueOf(time))
        s.adjustTime(time, -10)
        assertEquals("23:55", s.valueOf(time))
        s.adjustTime(time, 60)
        assertEquals("00:55", s.valueOf(time))
    }

    @Test
    fun `adjustTime starts from now when the value is not a time`() {
        val s = session(medicine)
        val time = s.blank("time")
        s.setValue(time, "soon")
        s.adjustTime(time, 5)
        assertEquals("20:25", s.valueOf(time))
    }

    @Test
    fun `setTimeToNow reads the clock again`() {
        var clock = now
        val s = session(medicine) { clock }
        val time = s.blank("time")
        s.adjustTime(time, 60)
        assertEquals("21:20", s.valueOf(time))
        clock = now.plusMinutes(7)
        s.setTimeToNow(time)
        assertEquals("20:27", s.valueOf(time))
    }

    @Test
    fun `dates can be nudged and set`() {
        val s = session(shortcut("{date}"))
        val date = s.blank("date")
        s.adjustDate(date, -1)
        assertEquals("2026-10-15", s.valueOf(date))
        s.adjustDate(date, 2)
        assertEquals("2026-10-17", s.valueOf(date))
        s.setDate(date, LocalDate.of(2026, 12, 25))
        assertEquals("2026-12-25", s.valueOf(date))
        assertEquals("25 Dec 2026", s.displayValue(date))
    }

    @Test
    fun `adjustDate starts from today when the value is not a date`() {
        val s = session(shortcut("{date}"))
        val date = s.blank("date")
        s.setValue(date, "someday")
        s.adjustDate(date, 1)
        assertEquals("2026-10-17", s.valueOf(date))
    }

    @Test
    fun `displayValue shows times and text as they are`() {
        val s = session(medicine)
        assertEquals("20:20", s.displayValue(s.blank("time")))
        assertEquals("", s.displayValue(s.blank("child")))
        s.setValue(s.blank("child"), "Sam")
        assertEquals("Sam", s.displayValue(s.blank("child")))
    }

    // ---- Preview and result ----

    @Test
    fun `preview shows unfilled blanks in square brackets`() {
        val s = session(medicine)
        assertEquals("[child], [medicine], [dose], 20:20 next dose not before 00:20", s.preview())
        s.setValue(s.blank("child"), "Sam")
        assertEquals("Sam, [medicine], [dose], 20:20 next dose not before 00:20", s.preview())
    }

    @Test
    fun `preview of an unfilled moved blank shows just its name`() {
        val s = session(shortcut("{child} back by {pickup+30m}", VariableDef("pickup", VariableType.CHOICE)))
        assertEquals("[child] back by [pickup]", s.preview())
    }

    @Test
    fun `result leaves unfilled blanks empty`() {
        val s = session(medicine)
        assertEquals(", , , 20:20 next dose not before 00:20", s.result())
    }

    @Test
    fun `useDefaults fills lists with their first answer and keeps chosen ones`() {
        val s = session(medicine)
        s.setValue(s.blank("child"), "Ben")
        s.useDefaults()
        assertEquals("Ben", s.valueOf(s.blank("child")))
        assertEquals("Calpol", s.valueOf(s.blank("medicine")))
        assertEquals("Ben, Calpol, 2.5ml, 20:20 next dose not before 00:20", s.result())
    }

    @Test
    fun `useDefaults uses the first quick choice of a time or date`() {
        val shortcut = Shortcut(
            "1", "x", "{when} {day}",
            listOf(
                VariableDef("when", VariableType.TIME, listOf("-60", "-30")),
                VariableDef("day", VariableType.DATE, listOf("1")),
            ),
        )
        val s = session(shortcut) { LocalDateTime.of(2026, 10, 16, 20, 20) }
        s.useDefaults()
        assertEquals("19:20", s.valueOf(s.blank("when")))
        assertEquals("2026-10-17", s.valueOf(s.blank("day")))
    }

    @Test
    fun `result builds the medicine example`() {
        val s = session(medicine)
        s.setValue(s.blank("child"), "Sam")
        s.setValue(s.blank("medicine"), "Calpol")
        s.setValue(s.blank("dose"), "5ml")
        assertEquals("Sam, Calpol, 5ml, 20:20 next dose not before 00:20", s.result())
    }

    @Test
    fun `result follows time changes`() {
        val s = session(medicine)
        s.adjustTime(s.blank("time"), -60)
        assertEquals(", , , 19:20 next dose not before 23:20", s.result())
    }

    @Test
    fun `result fills built-ins from the clock`() {
        val s = session(shortcut("{child} at {now} on {today}"))
        s.setValue(s.blank("child"), "Sam")
        assertEquals("Sam at 20:20 on 16 Oct 2026", s.result())
    }

    @Test
    fun `a typed value that looks like a date is not changed in a text blank`() {
        val s = session(shortcut("{note}", VariableDef("note", VariableType.TEXT)))
        s.setValue(s.blank("note"), "2026-10-16")
        assertEquals("2026-10-16", s.result())
    }

    // ---- Learning for next time ----

    @Test
    fun `a new list value is remembered as an option and as the last value`() {
        val s = session(medicine)
        s.setValue(s.blank("child"), "Alex")
        val child = s.updatedShortcut().def("child")
        assertEquals(listOf("Sam", "Alex"), child.options)
        assertEquals("Alex", child.lastValue)
    }

    @Test
    fun `a known option is not added again even in a different case`() {
        val s = session(medicine)
        s.setValue(s.blank("child"), "sam")
        val child = s.updatedShortcut().def("child")
        assertEquals(listOf("Sam"), child.options)
        assertTrue("Sam".equals(child.lastValue, ignoreCase = true))
    }

    @Test
    fun `typed values are trimmed before being remembered`() {
        val s = session(medicine)
        s.setValue(s.blank("child"), "  Alex  ")
        val child = s.updatedShortcut().def("child")
        assertEquals(listOf("Sam", "Alex"), child.options)
        assertEquals("Alex", child.lastValue)
    }

    @Test
    fun `new options are not kept when remember is off`() {
        val s = session(shortcut("{child}", VariableDef("child", options = listOf("Sam"), remember = false)))
        s.setValue(s.blank("child"), "Alex")
        val child = s.updatedShortcut().def("child")
        assertEquals(listOf("Sam"), child.options)
        assertEquals("Alex", child.lastValue)
        assertFalse(child.remember)
    }

    @Test
    fun `an empty value keeps what was learned before`() {
        val s = session(shortcut("{child}", VariableDef("child", options = listOf("Sam"), lastValue = "Sam")))
        s.setValue(s.blank("child"), "   ")
        val child = s.updatedShortcut().def("child")
        assertEquals(listOf("Sam"), child.options)
        assertEquals("Sam", child.lastValue)
    }

    @Test
    fun `times and dates are not remembered`() {
        val s = session(shortcut("{time} {date}", VariableDef("time", VariableType.TIME), VariableDef("date", VariableType.DATE)))
        s.adjustTime(s.blank("time"), 30)
        s.adjustDate(s.blank("date"), 1)
        val updated = s.updatedShortcut()
        assertEquals(VariableDef("time", VariableType.TIME), updated.def("time"))
        assertEquals(VariableDef("date", VariableType.DATE), updated.def("date"))
    }

    @Test
    fun `typed text and numbers remember the last value only`() {
        val s = session(
            shortcut(
                "{note} {count}",
                VariableDef("note", VariableType.TEXT),
                VariableDef("count", VariableType.NUMBER),
            ),
        )
        s.setValue(s.blank("note"), "with food")
        s.setValue(s.blank("count"), "2")
        val updated = s.updatedShortcut()
        assertEquals("with food", updated.def("note").lastValue)
        assertTrue(updated.def("note").options.isEmpty())
        assertEquals("2", updated.def("count").lastValue)
        assertTrue(updated.def("count").options.isEmpty())
    }

    @Test
    fun `guessed blanks are saved after the existing ones`() {
        val s = session(shortcut("{child} {extra}", VariableDef("old"), VariableDef("child", options = listOf("Sam"))))
        s.setValue(s.blank("child"), "Sam")
        s.setValue(s.blank("extra"), "biscuit")
        val updated = s.updatedShortcut()
        assertEquals(listOf("old", "child", "extra"), updated.variables.map { it.name })
        assertEquals(VariableDef("old"), updated.def("old"))
        assertEquals(listOf("biscuit"), updated.def("extra").options)
    }

    @Test
    fun `updatedShortcut keeps everything else and does not change the original`() {
        val s = session(medicine)
        s.setValue(s.blank("child"), "Alex")
        val updated = s.updatedShortcut()
        assertEquals(medicine.id, updated.id)
        assertEquals(medicine.name, updated.name)
        assertEquals(medicine.parentId, updated.parentId)
        assertEquals(medicine.template, updated.template)
        assertEquals(listOf("Sam"), medicine.def("child").options)
        assertNull(medicine.def("child").lastValue)
    }

    @Test
    fun `saved blanks keep their own spelling`() {
        val s = session(shortcut("{child}", VariableDef("Child", options = listOf("Sam"))))
        s.setValue(s.blank("child"), "Alex")
        val updated = s.updatedShortcut()
        assertEquals(listOf("Child"), updated.variables.map { it.name })
        assertEquals(listOf("Sam", "Alex"), updated.variables.single().options)
    }

    @Test
    fun `the next session starts with what was learned`() {
        val first = session(medicine)
        first.setValue(first.blank("child"), "Alex")
        first.setValue(first.blank("dose"), "5ml")

        val second = session(first.updatedShortcut())
        assertEquals("Alex", second.valueOf(second.blank("child")))
        assertEquals("5ml", second.valueOf(second.blank("dose")))
        second.setValue(second.blank("child"), "ALEX")
        assertEquals(listOf("Sam", "Alex"), second.updatedShortcut().def("child").options)
    }
}

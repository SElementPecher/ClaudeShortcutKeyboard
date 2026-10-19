package io.github.selementpecher.shortcutkeyboard.fill

import io.github.selementpecher.shortcutkeyboard.data.ShortcutJson
import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

/** The "how long" kinds: from now and ago, asked or fixed, printing the amount, the clock, or both. */
class FillSessionRelativeTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 16, 20, 20)

    private fun session(template: String, vararg defs: VariableDef) =
        FillSession(Shortcut(id = "s", name = "S", template = template, variables = defs.toList()), { now }, Locale.UK)

    private fun ahead(show: RelativeShow = RelativeShow.AMOUNT, options: List<String> = listOf("10 mins", "30 mins"), fixed: Boolean = false, last: String? = null) =
        VariableDef("How long", VariableType.TIME_AHEAD, options, lastValue = last, show = show, fixed = fixed)

    @Test
    fun `an asked blank is a step and prints the chosen amount`() {
        val s = session("Be there in {How long}.", ahead())
        assertEquals(listOf("How long"), s.variables.map { it.name })
        s.setValue(s.variables[0], "30 mins")
        assertEquals("Be there in 30 mins.", s.result())
    }

    @Test
    fun `a fixed blank is never a step but still prints`() {
        val s = session("Be there in {How long}.", ahead(options = listOf("10 mins"), fixed = true))
        assertTrue(s.variables.isEmpty())
        assertEquals("Be there in 10 mins.", s.result())
    }

    @Test
    fun `fixed is ignored for anything but a single valid amount`() {
        val noOptions = session("x {How long}", ahead(options = emptyList(), fixed = true))
        assertEquals(1, noOptions.variables.size)
        val listBlank = session("x {Who}", VariableDef("Who", VariableType.CHOICE, listOf("Sam"), fixed = true))
        assertEquals(1, listBlank.variables.size)
    }

    @Test
    fun `fixed blanks do not hide the asked ones or change their order`() {
        val s = session(
            "{Who} will be there in {How long} and again in {Later}.",
            VariableDef("Who", VariableType.CHOICE, listOf("Sam", "Alex")),
            ahead(options = listOf("5 mins"), fixed = true),
            VariableDef("Later", VariableType.TIME_AHEAD, listOf("1 hour", "2 hours")),
        )
        assertEquals(listOf("Who", "Later"), s.variables.map { it.name })
        s.setValue(s.variables[0], "Sam")
        s.setValue(s.variables[1], "2 hours")
        assertEquals("Sam will be there in 5 mins and again in 2 hours.", s.result())
    }

    @Test
    fun `from now prints the clock or both`() {
        val clock = session("At {How long}", ahead(RelativeShow.CLOCK, listOf("4 hours", "1 hour")))
        clock.setValue(clock.variables[0], "4 hours")
        assertEquals("At 00:20", clock.result())
        val both = session("Back in {How long}", ahead(RelativeShow.BOTH, listOf("10 mins", "20 mins")))
        both.setValue(both.variables[0], "10 mins")
        assertEquals("Back in 10 mins (20:30)", both.result())
    }

    @Test
    fun `ago goes backwards`() {
        val ago = VariableDef("How long", VariableType.TIME_AGO, listOf("20 mins"), show = RelativeShow.BOTH, fixed = true)
        assertEquals("Given 20 mins (20:00)", session("Given {How long}", ago).result())
        val clock = VariableDef("How long", VariableType.TIME_AGO, listOf("1 hour"), show = RelativeShow.CLOCK, fixed = true)
        assertEquals("Given at 19:20", session("Given at {How long}", clock).result())
    }

    @Test
    fun `the time is worked out when the message is finished, not when it was picked`() {
        var clock = now
        val s = FillSession(
            Shortcut(id = "s", name = "S", template = "Back at {How long}", variables = listOf(ahead(RelativeShow.CLOCK))),
            { clock }, Locale.UK,
        )
        s.setValue(s.variables[0], "10 mins")
        clock = now.plusMinutes(7)
        assertEquals("Back at 20:37", s.result())
    }

    @Test
    fun `preview shows the blank name until an amount is chosen`() {
        val s = session("In {How long}", ahead())
        assertEquals("In [How long]", s.preview())
        s.setValue(s.variables[0], "10 mins")
        assertEquals("In 10 mins", s.preview())
    }

    @Test
    fun `the last amount is offered next time and nothing stale is saved`() {
        val s = session("In {How long}", ahead(RelativeShow.BOTH))
        s.setValue(s.variables[0], "30 mins")
        val learned = s.updatedShortcut().variables.single()
        assertEquals("30 mins", learned.lastValue)
        assertEquals(listOf("10 mins", "30 mins"), learned.options)
        assertEquals(RelativeShow.BOTH, learned.show)
        val again = FillSession(s.updatedShortcut(), { now }, Locale.UK)
        assertEquals("30 mins", again.valueOf(again.variables[0]))
    }

    @Test
    fun `a fixed blank survives learning untouched`() {
        val fixed = ahead(options = listOf("10 mins"), fixed = true)
        val s = session("In {How long}", fixed)
        assertEquals(fixed, s.updatedShortcut().variables.single())
    }

    @Test
    fun `an unreadable chosen value is printed as it is and not remembered`() {
        val s = session("In {How long}", ahead())
        s.setValue(s.variables[0], "soon")
        assertEquals("In soon", s.result())
        assertNull(s.updatedShortcut().variables.single().lastValue)
    }

    @Test
    fun `the new fields survive saving and loading and old files read as plain`() {
        val def = ahead(RelativeShow.BOTH, listOf("10 mins"), fixed = true)
        val shortcut = Shortcut(id = "s", name = "S", template = "In {How long}", variables = listOf(def))
        val back = ShortcutJson.fromJson(ShortcutJson.toJson(listOf(shortcut))).single().variables.single()
        assertEquals(def, back)
        val old = ShortcutJson.fromJson("""[{"id":"a","template":"x {t}","variables":[{"name":"t","type":"time_ahead","options":["5 mins"]}]}]""")
            .single().variables.single()
        assertEquals(RelativeShow.AMOUNT, old.show)
        assertFalse(old.fixed)
    }
}

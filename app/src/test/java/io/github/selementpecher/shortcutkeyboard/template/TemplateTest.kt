package io.github.selementpecher.shortcutkeyboard.template

import io.github.selementpecher.shortcutkeyboard.model.VariableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** Plain JVM tests for the template language: parsing, offsets, formats and rendering. */
class TemplateTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 16, 20, 20)
    private val locale: Locale = Locale.UK

    private fun render(
        text: String,
        values: Map<String, String> = emptyMap(),
        types: Map<String, VariableType> = emptyMap(),
    ): String = Template.parse(text).render(values, types, now, locale)

    private fun renderTime(text: String, time: String = "20:20"): String =
        render(text, mapOf("time" to time), mapOf("time" to VariableType.TIME))

    private fun renderDate(text: String, date: String = "2026-10-16"): String =
        render(text, mapOf("date" to date), mapOf("date" to VariableType.DATE))

    private fun names(text: String): List<String> = Template.parse(text).variableNames

    private fun singleRef(text: String): Template.Part.Ref =
        Template.parse(text).parts.single() as Template.Part.Ref

    /** am/pm text differs in case between JDK and Android locale data, so compare without case. */
    private fun assertEqualsIgnoringCase(expected: String, actual: String) {
        assertEquals(expected.lowercase(Locale.ROOT), actual.lowercase(Locale.ROOT))
    }

    // ---- Parsing ----

    @Test
    fun `a value is always followed by a space before a word or number`() {
        val values = mapOf("a" to "18:13", "b" to "Sam")
        assertEquals("18:13 Zipporah", render("{a}Zipporah", values))
        assertEquals("Sam 5ml", render("{b}5ml", values))
        assertEquals("Sam Sam", render("{b}{b}", values))
    }

    @Test
    fun `no extra space before spaces punctuation or the end`() {
        val values = mapOf("b" to "Sam")
        assertEquals("Sam , ok", render("{b} , ok", values))
        assertEquals("Sam, ok", render("{b}, ok", values))
        assertEquals("Hi Sam!", render("Hi {b}!", values))
        assertEquals("Hi Sam", render("Hi {b}", values))
        // An empty value adds nothing at all.
        assertEquals("Hi ok", Template.parse("Hi {b}ok").render(emptyMap(), emptyMap(), now, locale) { "" })
    }

    @Test
    fun `plain text has no blanks and renders unchanged`() {
        val template = Template.parse("Hello there, see you soon.")
        assertEquals(listOf(Template.Part.Text("Hello there, see you soon.")), template.parts)
        assertTrue(template.variableNames.isEmpty())
        assertEquals("Hello there, see you soon.", render("Hello there, see you soon."))
    }

    @Test
    fun `empty template renders empty`() {
        assertTrue(Template.parse("").parts.isEmpty())
        assertEquals("", render(""))
    }

    @Test
    fun `parses text and blanks into parts`() {
        val parts = Template.parse("Hi {child}!").parts
        assertEquals(3, parts.size)
        assertEquals(Template.Part.Text("Hi "), parts[0])
        assertEquals("child", (parts[1] as Template.Part.Ref).name)
        assertEquals(Template.Part.Text("!"), parts[2])
    }

    @Test
    fun `blank names come in order of first use without duplicates`() {
        assertEquals(
            listOf("child", "medicine", "time"),
            names("{child}, {medicine}, {child} at {time}, again at {time+4h}"),
        )
    }

    @Test
    fun `blank names are case insensitive and keep the first spelling`() {
        assertEquals(listOf("Child"), names("{Child} and {CHILD} and {child}"))
        assertEquals("Sam and Sam", render("{Child} and {CHILD}", mapOf("child" to "Sam")))
    }

    @Test
    fun `spaces inside the braces are ignored`() {
        assertEquals(listOf("child"), names("{ child }"))
        assertEquals("Sam!", render("{  child }!", mapOf("child" to "Sam")))
    }

    @Test
    fun `names can contain spaces`() {
        assertEquals(listOf("first name"), names("{first name}"))
        assertEquals("Sam", render("{First Name}", mapOf("first name" to "Sam")))
    }

    @Test
    fun `built-in blanks are not listed as blanks`() {
        assertEquals(listOf("child"), names("{now} {today} {NOW} {Today+1d} {child}"))
    }

    @Test
    fun `isBuiltIn knows now and today in any case`() {
        assertTrue(Template.isBuiltIn("now"))
        assertTrue(Template.isBuiltIn("NOW"))
        assertTrue(Template.isBuiltIn(" today "))
        assertFalse(Template.isBuiltIn("time"))
        assertFalse(Template.isBuiltIn("nowadays"))
    }

    @Test
    fun `names may contain hyphens`() {
        assertEquals(listOf("next-dose"), names("{next-dose}"))
        assertFalse(Template.parse("{next-dose}").usesOffset("next-dose"))
        assertEquals("tomorrow", render("{next-dose}", mapOf("next-dose" to "tomorrow")))
    }

    @Test
    fun `hyphenated names can still have an offset`() {
        val ref = singleRef("{next-dose+4h}")
        assertEquals("next-dose", ref.name)
        assertEquals(240L, ref.offsetMinutes)
        assertEquals(
            "00:20",
            render("{next-dose+4h}", mapOf("next-dose" to "20:20"), mapOf("next-dose" to VariableType.TIME)),
        )
    }

    @Test
    fun `a sign and number without a time unit is part of the name`() {
        assertEquals(listOf("dose-2"), names("{dose-2}"))
        assertEquals(0L, singleRef("{dose-2}").offsetMinutes)
        assertEquals(listOf("size+2xl"), names("{size+2xl}"))
        assertEquals(0L, singleRef("{size+2xl}").offsetMinutes)
    }

    // ---- Things that must stay as plain text ----

    @Test
    fun `empty braces stay as text`() {
        assertTrue(names("a {} b").isEmpty())
        assertEquals("a {} b", render("a {} b"))
    }

    @Test
    fun `braces with only spaces stay as text`() {
        assertTrue(names("a { } b").isEmpty())
        assertEquals("a { } b", render("a { } b"))
    }

    @Test
    fun `an unclosed brace before a blank stays as text`() {
        assertEquals(listOf("b"), names("{a{b}"))
        assertEquals("{a{b}", render("{a{b}"))
        assertEquals("{aX", render("{a{b}", mapOf("b" to "X")))
    }

    @Test
    fun `a lone closing brace stays as text`() {
        assertTrue(names("a}").isEmpty())
        assertEquals("a}", render("a}"))
        assertEquals("}{", render("}{"))
    }

    @Test
    fun `an opening brace that is never closed stays as text`() {
        assertTrue(names("see {child").isEmpty())
        assertEquals("see {child", render("see {child", mapOf("child" to "Sam")))
    }

    @Test
    fun `braces around a line break stay as text`() {
        assertTrue(names("{a\nb}").isEmpty())
        assertEquals("{a\nb}", render("{a\nb}"))
    }

    @Test
    fun `an offset or format with no name stays as text`() {
        assertTrue(names("{+4h} {|HH:mm}").isEmpty())
        assertEquals("{+4h} {|HH:mm}", render("{+4h} {|HH:mm}"))
    }

    // ---- Literal braces ----

    @Test
    fun `double braces make literal braces`() {
        assertTrue(names("{{child}}").isEmpty())
        assertEquals("{child}", render("{{child}}", mapOf("child" to "Sam")))
        assertEquals("a { b } c", render("a {{ b }} c"))
        assertEquals("{", render("{{"))
        assertEquals("}", render("}}"))
    }

    @Test
    fun `literal braces can wrap a blank`() {
        assertEquals(listOf("child"), names("{{{child}}}"))
        assertEquals("{Sam}", render("{{{child}}}", mapOf("child" to "Sam")))
    }

    // ---- Time offsets ----

    @Test
    fun `adding hours wraps past midnight`() {
        assertEquals(
            "20:20 next dose not before 00:20",
            renderTime("{time} next dose not before {time+4h}"),
        )
    }

    @Test
    fun `subtracting minutes`() {
        assertEquals("20:05", renderTime("{time-15m}"))
    }

    @Test
    fun `subtracting wraps back past midnight`() {
        assertEquals("23:40", renderTime("{time-30m}", time = "00:10"))
    }

    @Test
    fun `hours and minutes together`() {
        assertEquals("21:50", renderTime("{time+1h30m}"))
        assertEquals(90L, singleRef("{time+1h30m}").offsetMinutes)
    }

    @Test
    fun `offsets may contain spaces`() {
        assertEquals("00:20", renderTime("{time + 4 h}"))
        assertEquals("20:05", renderTime("{ time - 15 m }"))
        val ref = singleRef("{time + 4 h}")
        assertEquals("time", ref.name)
        assertEquals(240L, ref.offsetMinutes)
    }

    @Test
    fun `offsets accept longer unit words`() {
        assertEquals("22:20", renderTime("{time+2 hours}"))
        assertEquals("20:50", renderTime("{time+30 mins}"))
        assertEquals("21:20", renderTime("{time+1hr}"))
        assertEquals("20:21", renderTime("{time+1 minute}"))
    }

    @Test
    fun `units are not case sensitive`() {
        assertEquals("00:20", renderTime("{time+4H}"))
    }

    @Test
    fun `several offset terms add up`() {
        assertEquals("00:05", renderTime("{time+4h -15m}"))
        assertEquals(225L, singleRef("{time+4h -15m}").offsetMinutes)
    }

    @Test
    fun `a day offset on a time keeps the clock time`() {
        assertEquals("20:20", renderTime("{time+2d}"))
    }

    @Test
    fun `usesOffset is true only when a reference moves the blank`() {
        val template = Template.parse("{time} {child} {time+4h}")
        assertTrue(template.usesOffset("time"))
        assertTrue(template.usesOffset("TIME"))
        assertFalse(template.usesOffset("child"))
        assertFalse(template.usesOffset("missing"))
        assertFalse(Template.parse("{time}").usesOffset("time"))
    }

    // ---- Date offsets ----

    @Test
    fun `adding days to a date`() {
        assertEquals("18 Oct 2026", renderDate("{date+2d}"))
    }

    @Test
    fun `subtracting a day from a date`() {
        assertEquals("15 Oct 2026", renderDate("{date-1d}"))
    }

    @Test
    fun `date offsets cross into the next month and year`() {
        assertEquals("5 Nov 2026", renderDate("{date+20d}"))
        assertEquals("1 Jan 2027", renderDate("{date+1d}", date = "2026-12-31"))
    }

    @Test
    fun `a huge day offset does not crash`() {
        // Can come from a typo or a restored backup. Rendering runs in the keyboard and on the app screens.
        val values = mapOf("date" to "2026-10-16")
        val types = mapOf("date" to VariableType.DATE)
        assertTrue(render("{today+400000000000d}").isNotEmpty())
        assertTrue(render("{date+400000000000d}", values, types).isNotEmpty())
        assertTrue(render("{date+6000000000000000d}", values, types).isNotEmpty())
    }

    @Test
    fun `a huge time offset does not crash`() {
        assertEquals(5, renderTime("{time+400000000000d}").length)
        assertEquals(5, renderTime("{time+9999999999999999m}").length)
    }

    // ---- describeOffset ----

    @Test
    fun `describeOffset writes friendly offsets`() {
        assertEquals("", Template.describeOffset(0))
        assertEquals("+4h", Template.describeOffset(240))
        assertEquals("+4h30m", Template.describeOffset(270))
        assertEquals("-15m", Template.describeOffset(-15))
        assertEquals("+1d", Template.describeOffset(1440))
        assertEquals("+1d1h", Template.describeOffset(1500))
        assertEquals("-2d", Template.describeOffset(-2880))
    }

    @Test
    fun `describeOffset output parses back to the same offset`() {
        for (minutes in listOf(1L, 15L, 45L, 60L, 90L, 240L, 270L, 1439L, 1440L, 1441L, 2880L, -1L, -15L, -90L, -1440L)) {
            val ref = singleRef("{time${Template.describeOffset(minutes)}}")
            assertEquals("time", ref.name)
            assertEquals("offset for $minutes", minutes, ref.offsetMinutes)
        }
    }

    // ---- Formats ----

    @Test
    fun `a format after a bar changes how a time looks`() {
        assertEqualsIgnoringCase("8:20 pm", renderTime("{time|h:mm a}"))
    }

    @Test
    fun `offset and format together`() {
        assertEqualsIgnoringCase("12:20 am", renderTime("{time+4h|h:mm a}"))
    }

    @Test
    fun `spaces around the bar are ignored`() {
        assertEquals("20.20", renderTime("{time | HH.mm}"))
        assertEquals("00.20", renderTime("{time + 4h | HH.mm}"))
    }

    @Test
    fun `a format changes how a date looks`() {
        assertEquals("Friday 16 October", renderDate("{date|EEEE d MMMM}"))
        assertEquals("18/10/2026", renderDate("{date+2d|dd/MM/yyyy}"))
    }

    @Test
    fun `an invalid format falls back to the normal look`() {
        assertEquals("20:20", renderTime("{time|bogus}"))
        assertEquals("16 Oct 2026", renderDate("{date|bogus}"))
    }

    @Test
    fun `a format asking for fields the value does not have falls back`() {
        assertEquals("20:20", renderTime("{time|yyyy}"))
        assertEquals("16 Oct 2026", renderDate("{date|HH:mm}"))
    }

    @Test
    fun `an empty format uses the normal look`() {
        assertEquals("20:20", renderTime("{time|}"))
        assertEquals(null, singleRef("{time|}").format)
        assertEquals("h:mm a", singleRef("{time| h:mm a }").format)
    }

    @Test
    fun `a format on a text blank is ignored`() {
        assertEquals(
            "Sam",
            render("{child|h:mm a}", mapOf("child" to "Sam"), mapOf("child" to VariableType.CHOICE)),
        )
    }

    // ---- Normal looks ----

    @Test
    fun `dates are shown in the medium style for the locale`() {
        assertEquals("16 Oct 2026", renderDate("{date}"))
    }

    @Test
    fun `times are shown as 24 hour HH mm`() {
        assertEquals("08:05", renderTime("{time}", time = "8:05"))
        assertEquals("20:20", renderTime("{time}", time = "2020"))
        assertEquals("20:20", renderTime("{time}", time = "20.20"))
    }

    @Test
    fun `a time that cannot be read is kept as typed and not moved`() {
        assertEquals("soon / soon", renderTime("{time} / {time+4h}", time = "soon"))
    }

    @Test
    fun `a date that cannot be read is kept as typed`() {
        assertEquals("next week", renderDate("{date+1d}", date = "next week"))
    }

    @Test
    fun `text number and choice blanks ignore offsets`() {
        for (type in listOf(VariableType.TEXT, VariableType.NUMBER, VariableType.CHOICE)) {
            assertEquals("20:20", render("{x+4h}", mapOf("x" to "20:20"), mapOf("x" to type)))
        }
    }

    // ---- Unfilled and unknown blanks ----

    @Test
    fun `unfilled blanks are shown as written by default`() {
        assertEquals("{child} at {time+4h}", render("{child} at {time+4h}"))
    }

    @Test
    fun `unfilled blanks keep their inner text but not outer spaces`() {
        assertEquals("{time + 4h}", render("{ time + 4h }"))
    }

    @Test
    fun `a custom missing text is used for unfilled blanks`() {
        val text = Template.parse("{child} at {time+4h}").render(emptyMap(), emptyMap(), now, locale) { "[${it.name}]" }
        assertEquals("[child] at [time]", text)
        val empty = Template.parse("{child} at {time+4h}").render(emptyMap(), emptyMap(), now, locale) { "" }
        assertEquals(" at ", empty)
    }

    @Test
    fun `values for unknown blanks are used as plain text`() {
        assertEquals("hello", render("{x}", mapOf("x" to "hello")))
    }

    @Test
    fun `values are looked up by lower case key`() {
        assertEquals("Sam", render("{CHILD}", mapOf("child" to "Sam")))
        assertEquals(
            "00:20",
            render("{Time+4h}", mapOf("time" to "20:20"), mapOf("time" to VariableType.TIME)),
        )
    }

    @Test
    fun `an untyped value that looks like a time is treated as a time`() {
        assertEquals("21:20", render("{x+1h}", mapOf("x" to "20:20")))
    }

    @Test
    fun `an untyped number without a colon stays as text`() {
        assertEquals("2020", render("{x+1h}", mapOf("x" to "2020")))
    }

    @Test
    fun `an untyped value that looks like a date is treated as a date`() {
        assertEquals("17 Oct 2026", render("{x+1d}", mapOf("x" to "2026-10-16")))
    }

    @Test
    fun `values are not parsed again as templates`() {
        assertEquals("{medicine}", render("{child}", mapOf("child" to "{medicine}", "medicine" to "Calpol")))
    }

    // ---- Built-ins ----

    @Test
    fun `now is the current time`() {
        assertEquals("20:20", render("{now}"))
        assertEquals("20:20", render("{NOW}"))
        assertEquals("00:20", render("{now+4h}"))
        assertEqualsIgnoringCase("8:20 pm", render("{now|h:mm a}"))
    }

    @Test
    fun `today is the current date`() {
        assertEquals("16 Oct 2026", render("{today}"))
        assertEquals("17 Oct 2026", render("{today+1d}"))
        assertEquals("15 Oct 2026", render("{Today-1d}"))
        assertEquals("Friday", render("{today|EEEE}"))
        assertEquals("16/10/2026", render("{today|dd/MM/yyyy}"))
    }

    @Test
    fun `built-ins are filled even when other blanks are missing`() {
        assertEquals("{child} at 20:20", render("{child} at {now}"))
    }

    @Test
    fun `a given value overrides a built-in`() {
        assertEquals(
            "09:00",
            render("{now}", mapOf("now" to "09:00"), mapOf("now" to VariableType.TIME)),
        )
    }

    // ---- Helpers ----

    @Test
    fun `parseTime reads common ways of writing a time`() {
        assertEquals(LocalTime.of(20, 20), Template.parseTime("20:20"))
        assertEquals(LocalTime.of(8, 5), Template.parseTime("8:05"))
        assertEquals(LocalTime.of(20, 20), Template.parseTime("2020"))
        assertEquals(LocalTime.of(20, 20), Template.parseTime("20.20"))
        assertEquals(LocalTime.of(9, 30), Template.parseTime(" 9:30 "))
        assertEquals(LocalTime.of(0, 0), Template.parseTime("00:00"))
        assertEquals(LocalTime.of(23, 59), Template.parseTime("23:59"))
    }

    @Test
    fun `parseTime rejects things that are not times`() {
        assertNull(Template.parseTime(""))
        assertNull(Template.parseTime("soon"))
        assertNull(Template.parseTime("24:00"))
        assertNull(Template.parseTime("20:60"))
        assertNull(Template.parseTime("7"))
        assertNull(Template.parseTime("8:5"))
        assertNull(Template.parseTime("12:30:15"))
    }

    @Test
    fun `parseDate reads ISO dates only`() {
        assertEquals(LocalDate.of(2026, 10, 16), Template.parseDate("2026-10-16"))
        assertEquals(LocalDate.of(2026, 10, 16), Template.parseDate(" 2026-10-16 "))
        assertNull(Template.parseDate("16/10/2026"))
        assertNull(Template.parseDate("2026-02-30"))
        assertNull(Template.parseDate(""))
    }

    @Test
    fun `formatTimeValue writes HH mm`() {
        assertEquals("07:05", Template.formatTimeValue(LocalTime.of(7, 5)))
        assertEquals("00:00", Template.formatTimeValue(LocalTime.MIDNIGHT))
        assertEquals("23:59", Template.formatTimeValue(LocalTime.of(23, 59, 59)))
    }

    @Test
    fun `guessType picks a type from the name`() {
        assertEquals(VariableType.TIME, Template.guessType("time"))
        assertEquals(VariableType.TIME, Template.guessType("Arrival time"))
        assertEquals(VariableType.TIME, Template.guessType("at"))
        assertEquals(VariableType.TIME, Template.guessType("when"))
        assertEquals(VariableType.DATE, Template.guessType("date"))
        assertEquals(VariableType.DATE, Template.guessType("Birthday"))
        assertEquals(VariableType.NUMBER, Template.guessType("number"))
        assertEquals(VariableType.NUMBER, Template.guessType("count"))
        assertEquals(VariableType.NUMBER, Template.guessType("Amount"))
        assertEquals(VariableType.CHOICE, Template.guessType("child"))
        assertEquals(VariableType.CHOICE, Template.guessType("medicine"))
    }

    @Test
    fun `guessType treats a moved blank as a time unless it is a date`() {
        assertEquals(VariableType.TIME, Template.guessType("dose", usesOffset = true))
        assertEquals(VariableType.DATE, Template.guessType("date", usesOffset = true))
    }

    @Test
    fun `TIME_FORMAT is 24 hour`() {
        assertEquals("20:20", Template.TIME_FORMAT.format(LocalTime.of(20, 20)))
    }
}

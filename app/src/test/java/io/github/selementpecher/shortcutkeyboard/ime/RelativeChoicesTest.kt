package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.RelativeAmount
import io.github.selementpecher.shortcutkeyboard.template.RelativeUnit
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelativeChoicesTest {
    private val now = LocalDateTime.of(2026, 10, 16, 20, 20)

    private fun blank(
        type: VariableType = VariableType.TIME_AHEAD,
        options: List<String> = listOf("10 mins", "30 mins", "1 hour"),
        last: String? = null,
        show: RelativeShow = RelativeShow.AMOUNT,
    ) = VariableDef("How long", type, options, lastValue = last, show = show)

    private fun build(v: VariableDef) = RelativeChoices.build(v, now, Locale.UK)

    @Test
    fun theOwnersAmountsComeInOrder() {
        assertEquals(listOf("10 mins", "30 mins", "1 hour"), build(blank()).map { it.label })
        assertEquals(listOf(false, false, false), build(blank()).map { it.isLast })
    }

    @Test
    fun theLastUsedAmountComesFirstAndIsMarkedWithoutRepeating() {
        val result = build(blank(last = "30 mins"))
        assertEquals(listOf("30 mins", "10 mins", "1 hour"), result.map { it.label })
        assertEquals(listOf(true, false, false), result.map { it.isLast })
    }

    @Test
    fun aLastUsedOtherAmountIsOfferedEvenWhenNotSaved() {
        val result = build(blank(last = "7 mins"))
        assertEquals(listOf("7 mins", "10 mins", "30 mins", "1 hour"), result.map { it.label })
        assertEquals(true, result.first().isLast)
    }

    @Test
    fun theSameLengthOfTimeIsShownOnce() {
        val result = build(blank(options = listOf("60 mins", "1 hour", "2 hours", "in 2 hrs"), last = "1 hour"))
        assertEquals(listOf("1 hour", "2 hours"), result.map { it.label })
    }

    @Test
    fun thingsThatAreNotAmountsAreSkipped() {
        val result = build(blank(options = listOf("soon", "", "0 mins", "5 mins"), last = "later"))
        assertEquals(listOf("5 mins"), result.map { it.label })
        assertEquals(emptyList<AmountChoice>(), build(blank(options = emptyList())))
    }

    @Test
    fun labelsAreWrittenTheCanonicalWay() {
        assertEquals(listOf("1 min", "2 hours"), build(blank(options = listOf("1 minute", "2 hrs"))).map { it.label })
    }

    @Test
    fun noClockUnderTheAmountWhenOnlyTheAmountIsPrinted() {
        assertEquals(listOf(null, null, null), build(blank(show = RelativeShow.AMOUNT)).map { it.subtitle })
    }

    @Test
    fun clockUnderTheAmountForClockAndBoth() {
        val expected = listOf("20:30", "20:50", "21:20")
        assertEquals(expected, build(blank(show = RelativeShow.CLOCK)).map { it.subtitle })
        assertEquals(expected, build(blank(show = RelativeShow.BOTH)).map { it.subtitle })
    }

    @Test
    fun agoGoesBackwards() {
        val result = build(blank(type = VariableType.TIME_AGO, show = RelativeShow.BOTH))
        assertEquals(listOf("20:10", "19:50", "19:20"), result.map { it.subtitle })
    }

    @Test
    fun clockWrapsAcrossMidnight() {
        val late = LocalDateTime.of(2026, 10, 16, 23, 50)
        val half = RelativeAmount(30, RelativeUnit.MINS)
        assertEquals("00:20", RelativeChoices.clock(half, ahead = true, now = late, locale = Locale.UK))
        val early = LocalDateTime.of(2026, 10, 16, 0, 10)
        assertEquals("23:40", RelativeChoices.clock(half, ahead = false, now = early, locale = Locale.UK))
    }

    @Test
    fun daysShowTheDate() {
        val days = RelativeAmount(2, RelativeUnit.DAYS)
        assertEquals("18 Oct", RelativeChoices.clock(days, ahead = true, now = now, locale = Locale.UK))
        assertEquals("14 Oct", RelativeChoices.clock(days, ahead = false, now = now, locale = Locale.UK))
        val v = blank(options = listOf("2 days"), show = RelativeShow.CLOCK)
        assertEquals("18 Oct", build(v).single().subtitle)
    }

    @Test
    fun printedIsWhatTheMessageWillShow() {
        val ten = RelativeAmount(10, RelativeUnit.MINS)
        assertEquals("10 mins", RelativeChoices.printed(blank(show = RelativeShow.AMOUNT), ten, now, Locale.UK))
        assertEquals("20:30", RelativeChoices.printed(blank(show = RelativeShow.CLOCK), ten, now, Locale.UK))
        assertEquals("10 mins (20:30)", RelativeChoices.printed(blank(show = RelativeShow.BOTH), ten, now, Locale.UK))
        assertEquals("10 mins (20:10)", RelativeChoices.printed(blank(VariableType.TIME_AGO, show = RelativeShow.BOTH), ten, now, Locale.UK))
    }

    @Test
    fun otherStartsOnTheUnitOfTheFirstAmount() {
        assertEquals(RelativeUnit.HOURS, RelativeChoices.startUnit(blank(options = listOf("2 hours", "30 mins"))))
        assertEquals(RelativeUnit.DAYS, RelativeChoices.startUnit(blank(options = listOf("junk", "3 days"))))
        assertEquals(RelativeUnit.MINS, RelativeChoices.startUnit(blank(options = emptyList())))
    }

    @Test
    fun directionComesFromTheKind() {
        assertEquals(true, RelativeChoices.isAhead(blank(VariableType.TIME_AHEAD)))
        assertEquals(false, RelativeChoices.isAhead(blank(VariableType.TIME_AGO)))
    }
}

class AmountEntryTest {
    @Test
    fun startsEmptyAndInvalid() {
        val entry = AmountEntry()
        assertEquals("", entry.digits)
        assertNull(entry.amount)
        assertEquals(false, entry.isValid)
    }

    @Test
    fun typedDigitsAndUnitMakeAnAmount() {
        val entry = AmountEntry(RelativeUnit.HOURS)
        entry.press('2')
        entry.press('5')
        assertEquals(RelativeAmount(25, RelativeUnit.HOURS), entry.amount)
        entry.selectUnit(RelativeUnit.MINS)
        assertEquals("25 mins", entry.amount?.text())
    }

    @Test
    fun atMostThreeDigits() {
        val entry = AmountEntry()
        "12345".forEach(entry::press)
        assertEquals("123", entry.digits)
        assertEquals(123, entry.amount?.n)
    }

    @Test
    fun zeroIsNotAnAmountAndLeadingZerosAreDropped() {
        val entry = AmountEntry()
        entry.press('0')
        assertNull(entry.amount)
        entry.press('0')
        assertEquals("0", entry.digits)
        entry.press('7')
        assertEquals("7", entry.digits)
        assertEquals(7, entry.amount?.n)
    }

    @Test
    fun backspaceRemovesTheLastDigitAndIsSafeWhenEmpty() {
        val entry = AmountEntry()
        entry.press('4')
        entry.press('2')
        entry.backspace()
        assertEquals("4", entry.digits)
        entry.backspace()
        entry.backspace()
        assertEquals("", entry.digits)
        assertNull(entry.amount)
    }

    @Test
    fun nonDigitsAreIgnored() {
        val entry = AmountEntry()
        entry.press('.')
        entry.press('x')
        assertEquals("", entry.digits)
    }

    @Test
    fun changingTheUnitKeepsTheDigits() {
        val entry = AmountEntry()
        entry.press('9')
        entry.selectUnit(RelativeUnit.DAYS)
        assertEquals("9", entry.digits)
        assertEquals("9 days", entry.amount?.text())
    }
}

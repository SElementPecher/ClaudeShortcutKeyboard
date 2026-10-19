package io.github.selementpecher.shortcutkeyboard.template

import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class RelativeAmountTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 16, 20, 20)
    private fun amount(n: Int, unit: RelativeUnit) = RelativeAmount(n, unit)

    @Test
    fun `text uses singular for one and plural otherwise`() {
        assertEquals("1 min", amount(1, RelativeUnit.MINS).text())
        assertEquals("5 mins", amount(5, RelativeUnit.MINS).text())
        assertEquals("1 hour", amount(1, RelativeUnit.HOURS).text())
        assertEquals("2 hours", amount(2, RelativeUnit.HOURS).text())
        assertEquals("1 day", amount(1, RelativeUnit.DAYS).text())
        assertEquals("3 days", amount(3, RelativeUnit.DAYS).text())
    }

    @Test
    fun `minutes are worked out from the unit`() {
        assertEquals(10L, amount(10, RelativeUnit.MINS).minutes)
        assertEquals(120L, amount(2, RelativeUnit.HOURS).minutes)
        assertEquals(2880L, amount(2, RelativeUnit.DAYS).minutes)
    }

    @Test
    fun `amount only prints the amount, ahead or behind`() {
        val a = amount(10, RelativeUnit.MINS)
        assertEquals("10 mins", a.phrase(true, RelativeShow.AMOUNT, now, Locale.UK))
        assertEquals("10 mins", a.phrase(false, RelativeShow.AMOUNT, now, Locale.UK))
    }

    @Test
    fun `clock prints the time it works out to`() {
        assertEquals("20:30", amount(10, RelativeUnit.MINS).phrase(true, RelativeShow.CLOCK, now, Locale.UK))
        assertEquals("20:10", amount(10, RelativeUnit.MINS).phrase(false, RelativeShow.CLOCK, now, Locale.UK))
        assertEquals("00:20", amount(4, RelativeUnit.HOURS).phrase(true, RelativeShow.CLOCK, now, Locale.UK))
        assertEquals("19:20", amount(1, RelativeUnit.HOURS).phrase(false, RelativeShow.CLOCK, now, Locale.UK))
    }

    @Test
    fun `both prints the amount and the clock in brackets`() {
        assertEquals("10 mins (20:30)", amount(10, RelativeUnit.MINS).phrase(true, RelativeShow.BOTH, now, Locale.UK))
        assertEquals("20 mins (20:00)", amount(20, RelativeUnit.MINS).phrase(false, RelativeShow.BOTH, now, Locale.UK))
    }

    @Test
    fun `days print the date instead of a clock`() {
        assertEquals("18 Oct", amount(2, RelativeUnit.DAYS).phrase(true, RelativeShow.CLOCK, now, Locale.UK))
        assertEquals("2 days (14 Oct)", amount(2, RelativeUnit.DAYS).phrase(false, RelativeShow.BOTH, now, Locale.UK))
    }

    @Test
    fun `parse reads common spellings`() {
        assertEquals(amount(5, RelativeUnit.MINS), RelativeAmount.parse("5 mins"))
        assertEquals(amount(1, RelativeUnit.MINS), RelativeAmount.parse("1 min"))
        assertEquals(amount(2, RelativeUnit.HOURS), RelativeAmount.parse("in 2 hours"))
        assertEquals(amount(3, RelativeUnit.DAYS), RelativeAmount.parse("3 days ago"))
        assertEquals(amount(10, RelativeUnit.MINS), RelativeAmount.parse(" 10m "))
        assertEquals(amount(1, RelativeUnit.HOURS), RelativeAmount.parse("1 hr"))
        assertEquals(amount(15, RelativeUnit.MINS), RelativeAmount.parse("15 MINUTES"))
    }

    @Test
    fun `parse refuses anything else`() {
        for (bad in listOf(null, "", "mins", "0 mins", "1000 mins", "5", "5 weeks", "five mins", "-5 mins", "5.5 hours")) {
            assertNull("'$bad'", RelativeAmount.parse(bad))
        }
    }

    @Test
    fun `an amount of zero or over the limit is refused`() {
        assertNull(RelativeAmount.of(0, RelativeUnit.MINS))
        assertNull(RelativeAmount.of(1000, RelativeUnit.MINS))
        assertEquals(amount(999, RelativeUnit.MINS), RelativeAmount.of(999, RelativeUnit.MINS))
        assertThrows(IllegalArgumentException::class.java) { RelativeAmount(0, RelativeUnit.MINS) }
    }

    @Test
    fun `the common amounts are the five keyboard tiles`() {
        assertEquals(listOf("5 mins", "15 mins", "30 mins", "1 hour", "2 hours"), RelativeAmount.COMMON.map { it.text() })
    }
}

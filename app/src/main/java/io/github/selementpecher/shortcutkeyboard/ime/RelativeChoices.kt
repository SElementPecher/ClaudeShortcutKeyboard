package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.RelativeAmount
import io.github.selementpecher.shortcutkeyboard.template.RelativeUnit
import java.time.LocalDateTime
import java.util.Locale

/**
 * An amount tile on a "how long" question: the amount in big letters ([label]) and, when the
 * shortcut prints the clock time, what it works out to under it ([subtitle], e.g. "20:35").
 * [isLast] marks the amount used last time.
 */
data class AmountChoice(
    val amount: RelativeAmount,
    val isLast: Boolean,
    val label: String,
    val subtitle: String?,
)

/** What the tiles of a "how long" question are. Plain Kotlin, no Android types. */
object RelativeChoices {

    /** True for a blank that goes forward in time ("from now"), false for "ago". */
    fun isAhead(variable: VariableDef): Boolean = variable.type == VariableType.TIME_AHEAD

    /**
     * The owner's amounts as tiles: the one used last first (marked), then the saved ones in order.
     * Anything that is not an amount is skipped, and the same length of time is shown once
     * ("60 mins" and "1 hour" are one tile).
     */
    fun build(variable: VariableDef, now: LocalDateTime, locale: Locale = Locale.getDefault()): List<AmountChoice> {
        val ahead = isAhead(variable)
        val seen = HashSet<Long>()
        val result = ArrayList<AmountChoice>()
        fun add(amount: RelativeAmount?, isLast: Boolean) {
            if (amount == null || !seen.add(amount.minutes)) return
            result += AmountChoice(amount, isLast, amount.text(), subtitle(amount, ahead, variable.show, now, locale))
        }
        add(RelativeAmount.parse(variable.lastValue), isLast = true)
        for (option in variable.options) add(RelativeAmount.parse(option), isLast = false)
        return result
    }

    /** The clock time (or, for days, the date) that [amount] works out to from [now], e.g. "20:35" or "21 Oct". */
    fun clock(amount: RelativeAmount, ahead: Boolean, now: LocalDateTime, locale: Locale = Locale.getDefault()): String =
        amount.phrase(ahead, RelativeShow.CLOCK, now, locale)

    /** The line under an amount: the clock time, only when the shortcut prints it. */
    fun subtitle(amount: RelativeAmount, ahead: Boolean, show: RelativeShow, now: LocalDateTime, locale: Locale = Locale.getDefault()): String? =
        if (show == RelativeShow.AMOUNT) null else clock(amount, ahead, now, locale)

    /** What the message will show for [amount]: "10 mins", "20:35" or "10 mins (20:35)". */
    fun printed(variable: VariableDef, amount: RelativeAmount, now: LocalDateTime, locale: Locale = Locale.getDefault()): String =
        amount.phrase(isAhead(variable), variable.show, now, locale)

    /** The unit the "Other" screen starts on: the unit of the first saved amount, else minutes. */
    fun startUnit(variable: VariableDef): RelativeUnit =
        variable.options.firstNotNullOfOrNull { RelativeAmount.parse(it) }?.unit ?: RelativeUnit.MINS
}

/**
 * The "Other" screen's entry: up to three digits and a unit. [amount] is null until the number is 1 to 999.
 */
class AmountEntry(startUnit: RelativeUnit = RelativeUnit.MINS) {
    var digits: String = ""
        private set
    var unit: RelativeUnit = startUnit
        private set

    /** Adds a digit (ignored after three, and a leading zero is dropped: "0" then "5" is "5"). */
    fun press(key: Char) {
        if (key !in '0'..'9' || digits.length >= MAX_DIGITS) return
        digits = if (digits == "0") key.toString() else digits + key
    }

    fun backspace() {
        digits = digits.dropLast(1)
    }

    fun selectUnit(unit: RelativeUnit) {
        this.unit = unit
    }

    /** What has been typed, or null when it is empty, 0 or not an amount. */
    val amount: RelativeAmount? get() = digits.toIntOrNull()?.let { RelativeAmount.of(it, unit) }

    val isValid: Boolean get() = amount != null

    companion object {
        const val MAX_DIGITS = 3
    }
}

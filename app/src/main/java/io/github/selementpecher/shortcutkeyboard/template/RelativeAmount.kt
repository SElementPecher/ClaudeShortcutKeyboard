package io.github.selementpecher.shortcutkeyboard.template

import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The unit of a [RelativeAmount]. */
enum class RelativeUnit(val minutes: Long, val singular: String, val plural: String) {
    MINS(1, "min", "mins"),
    HOURS(60, "hour", "hours"),
    DAYS(24 * 60, "day", "days"),
}

/**
 * A length of time, like "5 mins", "1 hour" or "2 days", for messages that say "in 5 mins" or "5 mins ago".
 *
 * The keyboard stores the amount as [text] and the app turns it into a phrase with [phrase]. Plain Kotlin.
 */
data class RelativeAmount(val n: Int, val unit: RelativeUnit) {

    init {
        require(n in 1..MAX) { "Amount must be 1 to $MAX" }
    }

    val minutes: Long get() = n * unit.minutes

    /** "5 mins", "1 min", "1 hour", "2 days". */
    fun text(): String = "$n ${if (n == 1) unit.singular else unit.plural}"

    /**
     * What is printed in the message: the amount ("10 mins"), the clock time it works out to ("20:35";
     * for days the date, "21 Oct") or both ("10 mins (20:35)"). The words around it ("in", "ago") are the
     * owner's own, typed in the message. [ahead] says whether it is added to or taken from [now].
     */
    fun phrase(ahead: Boolean, show: RelativeShow, now: LocalDateTime, locale: Locale = Locale.getDefault()): String {
        if (show == RelativeShow.AMOUNT) return text()
        val moved = now.plusMinutes(if (ahead) minutes else -minutes)
        val clock = if (unit == RelativeUnit.DAYS) {
            moved.format(DateTimeFormatter.ofPattern("d MMM", locale))
        } else {
            moved.format(Template.TIME_FORMAT)
        }
        return if (show == RelativeShow.CLOCK) clock else "${text()} ($clock)"
    }

    companion object {
        const val MAX = 999

        /** The amounts offered as tiles on the keyboard. */
        val COMMON: List<RelativeAmount> = listOf(
            RelativeAmount(5, RelativeUnit.MINS),
            RelativeAmount(15, RelativeUnit.MINS),
            RelativeAmount(30, RelativeUnit.MINS),
            RelativeAmount(1, RelativeUnit.HOURS),
            RelativeAmount(2, RelativeUnit.HOURS),
        )

        private val PATTERN = Regex(
            """^\s*(?:in\s+|after\s+)?(\d{1,3})\s*(min|mins|minute|minutes|m|hour|hours|hr|hrs|h|day|days|d)\b(?:\s+(?:ago|from\s+now))?\s*$""",
            RegexOption.IGNORE_CASE,
        )

        /** An amount from [n] and a unit, or null if it is 0 or too big. */
        fun of(n: Int, unit: RelativeUnit): RelativeAmount? = if (n in 1..MAX) RelativeAmount(n, unit) else null

        /**
         * Reads "5 mins", "in 2 hours", "3 days ago", "10m", "1 hr". Returns null for anything else
         * (including 0).
         */
        fun parse(text: String?): RelativeAmount? {
            val match = PATTERN.find(text.orEmpty()) ?: return null
            val n = match.groupValues[1].toIntOrNull() ?: return null
            val unit = when (match.groupValues[2].lowercase()) {
                "min", "mins", "minute", "minutes", "m" -> RelativeUnit.MINS
                "hour", "hours", "hr", "hrs", "h" -> RelativeUnit.HOURS
                else -> RelativeUnit.DAYS
            }
            return of(n, unit)
        }
    }
}

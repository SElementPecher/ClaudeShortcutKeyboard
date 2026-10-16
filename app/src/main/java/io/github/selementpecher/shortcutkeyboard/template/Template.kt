package io.github.selementpecher.shortcutkeyboard.template

import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A parsed message template.
 *
 * Syntax:
 *  - `{name}`            the value of a blank
 *  - `{name+4h}`         a time/date blank moved by an offset (d, h, m units; e.g. `+1h30m`, `-15m`, `+2d`)
 *  - `{name|h:mm a}`     a time/date blank shown with a custom java.time pattern
 *  - `{name+4h|h:mm a}`  both
 *  - `{now}`, `{today}`  built-in current time and date
 *  - `{{` and `}}`       a literal `{` or `}`
 *
 * Anything that doesn't parse as a blank is kept as plain text.
 */
class Template private constructor(val parts: List<Part>) {

    sealed interface Part {
        data class Text(val text: String) : Part
        data class Ref(
            val name: String,
            /** Offset in minutes; 0 when none. Days count as 1440 minutes. */
            val offsetMinutes: Long = 0,
            val format: String? = null,
            /** The original text between the braces, used to show unfilled blanks. */
            val raw: String = name,
        ) : Part {
            val key: String get() = VariableDef.keyOf(name)
            val hasOffset: Boolean get() = offsetMinutes != 0L
        }
    }

    /** Blank names in order of first use, without duplicates and without built-ins. */
    val variableNames: List<String> by lazy {
        val seen = LinkedHashMap<String, String>()
        for (part in parts) {
            if (part is Part.Ref && !isBuiltIn(part.name)) seen.putIfAbsent(part.key, part.name.trim())
        }
        seen.values.toList()
    }

    /** True if any reference to [name] adds an offset, which only makes sense for times and dates. */
    fun usesOffset(name: String): Boolean {
        val key = VariableDef.keyOf(name)
        return parts.any { it is Part.Ref && it.key == key && it.hasOffset }
    }

    /**
     * Builds the final text.
     *
     * @param values typed or picked values keyed by [VariableDef.keyOf]. TIME values are "HH:mm",
     *   DATE values are ISO "yyyy-MM-dd".
     * @param types the type of each blank, keyed the same way. Unknown blanks are treated as TEXT
     *   unless their value looks like a time or date.
     * @param missing what to show for a blank with no value; by default the blank as written.
     */
    fun render(
        values: Map<String, String>,
        types: Map<String, VariableType> = emptyMap(),
        now: LocalDateTime = LocalDateTime.now(),
        locale: Locale = Locale.getDefault(),
        missing: (Part.Ref) -> String = { "{${it.raw}}" },
    ): String {
        val out = StringBuilder()
        for (part in parts) {
            when (part) {
                is Part.Text -> out.append(part.text)
                is Part.Ref -> out.append(renderRef(part, values, types, now, locale, missing))
            }
        }
        return out.toString()
    }

    private fun renderRef(
        ref: Part.Ref,
        values: Map<String, String>,
        types: Map<String, VariableType>,
        now: LocalDateTime,
        locale: Locale,
        missing: (Part.Ref) -> String,
    ): String {
        val key = ref.key
        val builtIn = key == NOW || key == TODAY
        val value: String
        val type: VariableType
        if (builtIn && !values.containsKey(key)) {
            type = if (key == NOW) VariableType.TIME else VariableType.DATE
            value = if (key == NOW) formatTimeValue(now.toLocalTime()) else now.toLocalDate().toString()
        } else {
            value = values[key] ?: return missing(ref)
            type = types[key] ?: guessTypeFromValue(value)
        }
        return when (type) {
            VariableType.TIME -> {
                val time = parseTime(value) ?: return value
                val moved = time.plusMinutes(ref.offsetMinutes)
                format(moved, ref.format, TIME_FORMAT, locale)
            }
            VariableType.DATE -> {
                val date = parseDate(value) ?: return value
                val moved = date.plusDays(Math.floorDiv(ref.offsetMinutes, MINUTES_PER_DAY))
                format(moved, ref.format, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM), locale)
            }
            else -> value
        }
    }

    companion object {
        const val NOW = "now"
        const val TODAY = "today"
        private const val MINUTES_PER_DAY = 24L * 60L

        /** Default time display, 24-hour, e.g. "20:20". Also the stored form of TIME values. */
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun isBuiltIn(name: String): Boolean = VariableDef.keyOf(name).let { it == NOW || it == TODAY }

        fun parse(text: String): Template {
            val parts = ArrayList<Part>()
            val buffer = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '{' && i + 1 < text.length && text[i + 1] == '{') {
                    buffer.append('{'); i += 2; continue
                }
                if (c == '}' && i + 1 < text.length && text[i + 1] == '}') {
                    buffer.append('}'); i += 2; continue
                }
                if (c == '{') {
                    val close = text.indexOf('}', i + 1)
                    val nextOpen = text.indexOf('{', i + 1)
                    if (close > i && (nextOpen == -1 || nextOpen > close)) {
                        val ref = parseRef(text.substring(i + 1, close))
                        if (ref != null) {
                            if (buffer.isNotEmpty()) {
                                parts += Part.Text(buffer.toString()); buffer.setLength(0)
                            }
                            parts += ref
                            i = close + 1
                            continue
                        }
                    }
                }
                buffer.append(c)
                i++
            }
            if (buffer.isNotEmpty()) parts += Part.Text(buffer.toString())
            return Template(parts)
        }

        private val OFFSET_TERM = Regex("""([+-])\s*((?:\d+\s*[a-zA-Z]+\s*)+)""")
        private val OFFSET_PIECE = Regex("""(\d+)\s*([a-zA-Z]+)""")

        /** Parses the text between braces, or returns null if it isn't a usable blank. */
        internal fun parseRef(inner: String): Part.Ref? {
            val bar = inner.indexOf('|')
            val left = if (bar >= 0) inner.substring(0, bar) else inner
            val format = if (bar >= 0) inner.substring(bar + 1).trim().ifEmpty { null } else null

            var name = left.trim()
            var offset = 0L
            // An offset starts at the first +/- that is followed by a number and runs to the end.
            val start = Regex("""[+-]\s*\d""").find(left)
            if (start != null) {
                val parsed = parseOffset(left.substring(start.range.first))
                if (parsed != null) {
                    name = left.substring(0, start.range.first).trim()
                    offset = parsed
                }
            }
            if (name.isEmpty() || name.any { it == '{' || it == '}' || it == '\n' }) return null
            return Part.Ref(name = name, offsetMinutes = offset, format = format, raw = inner.trim())
        }

        /** Parses "+4h", "-15m", "+1h30m", "+2d", "+4h -15m". Returns minutes, or null if not an offset. */
        internal fun parseOffset(text: String): Long? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            var total = 0L
            var pos = 0
            while (pos < trimmed.length) {
                val match = OFFSET_TERM.matchAt(trimmed, pos) ?: return null
                val sign = if (match.groupValues[1] == "-") -1L else 1L
                for (piece in OFFSET_PIECE.findAll(match.groupValues[2])) {
                    val amount = piece.groupValues[1].toLongOrNull() ?: return null
                    val unit = unitMinutes(piece.groupValues[2]) ?: return null
                    total += sign * amount * unit
                }
                pos = match.range.last + 1
                while (pos < trimmed.length && trimmed[pos].isWhitespace()) pos++
            }
            return total
        }

        private fun unitMinutes(unit: String): Long? = when (unit.lowercase()) {
            "m", "min", "mins", "minute", "minutes" -> 1L
            "h", "hr", "hrs", "hour", "hours" -> 60L
            "d", "day", "days" -> MINUTES_PER_DAY
            else -> null
        }

        fun formatTimeValue(time: LocalTime): String = time.format(TIME_FORMAT)

        /** Accepts "20:20", "8:05", "2020", "20.20". */
        fun parseTime(value: String): LocalTime? {
            val match = Regex("""^\s*(\d{1,2})\s*[:.]?\s*(\d{2})\s*$""").find(value) ?: return null
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            if (hour > 23 || minute > 59) return null
            return LocalTime.of(hour, minute)
        }

        fun parseDate(value: String): LocalDate? =
            runCatching { LocalDate.parse(value.trim()) }.getOrNull()

        private fun guessTypeFromValue(value: String): VariableType = when {
            parseTime(value) != null && value.contains(':') -> VariableType.TIME
            parseDate(value) != null -> VariableType.DATE
            else -> VariableType.TEXT
        }

        private fun format(
            value: java.time.temporal.TemporalAccessor,
            pattern: String?,
            fallback: DateTimeFormatter,
            locale: Locale,
        ): String {
            if (pattern != null) {
                runCatching { return DateTimeFormatter.ofPattern(pattern, locale).format(value) }
            }
            return fallback.withLocale(locale).format(value)
        }

        /** Guesses a sensible type for a new blank from its name. */
        fun guessType(name: String, usesOffset: Boolean = false): VariableType {
            val key = VariableDef.keyOf(name)
            return when {
                key.contains("date") || key.contains("day") -> VariableType.DATE
                usesOffset || key.contains("time") || key == "at" || key.contains("when") -> VariableType.TIME
                key.contains("number") || key.contains("count") || key.contains("amount") -> VariableType.NUMBER
                else -> VariableType.CHOICE
            }
        }

        /** Offset in minutes as friendly text, e.g. 270 -> "+4h30m". */
        fun describeOffset(minutes: Long): String {
            if (minutes == 0L) return ""
            val sign = if (minutes < 0) "-" else "+"
            var rest = Math.abs(minutes)
            val days = rest / MINUTES_PER_DAY; rest %= MINUTES_PER_DAY
            val hours = rest / 60; val mins = rest % 60
            return buildString {
                append(sign)
                if (days > 0) append(days).append('d')
                if (hours > 0) append(hours).append('h')
                if (mins > 0) append(mins).append('m')
            }
        }
    }
}

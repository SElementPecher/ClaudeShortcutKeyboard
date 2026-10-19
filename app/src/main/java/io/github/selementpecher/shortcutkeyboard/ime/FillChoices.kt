package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.template.Template
import java.time.LocalDate

/** An answer tile for a list or text blank. [isLast] marks the one used last time. */
data class Answer(val text: String, val isLast: Boolean)

/** What the answer tiles of one question are. Plain Kotlin. */
object AnswerList {
    /** The last used answer first (marked), then the saved options, with no repeats. */
    fun build(variable: VariableDef): List<Answer> {
        val last = variable.lastValue?.trim()?.takeIf { it.isNotEmpty() }
        val seen = HashSet<String>()
        val result = ArrayList<Answer>()
        if (last != null) {
            seen += last.lowercase()
            result += Answer(last, isLast = true)
        }
        for (option in variable.options) {
            val text = option.trim()
            if (text.isNotEmpty() && seen.add(text.lowercase())) result += Answer(text, isLast = false)
        }
        return result
    }

    /** True when [options] holds an answer that [before] did not (ignoring capitals). */
    fun hasNewOption(before: List<String>, options: List<String>): Boolean {
        val known = before.map { it.trim().lowercase() }.toSet()
        return options.any { it.isNotBlank() && it.trim().lowercase() !in known }
    }
}

/** The ready-made times offered on a time question. */
object TimeChoices {
    /** "Now", "15 min ago", "30 min ago", "1 hour ago". */
    val MINUTES_AGO = listOf(0L, 15L, 30L, 60L)

    fun pick(session: FillSession, variable: VariableDef, minutesAgo: Long) {
        session.setTimeToNow(variable)
        if (minutesAgo != 0L) session.adjustTime(variable, -minutesAgo)
    }

    /**
     * A line like "00:20" for the first place the message uses this blank with an offset
     * (such as `{time+4h}`), or null if it never does.
     */
    fun laterText(template: Template, variable: VariableDef, value: String?): String? {
        val time = Template.parseTime(value.orEmpty()) ?: return null
        val ref = template.parts.filterIsInstance<Template.Part.Ref>()
            .firstOrNull { it.key == variable.key && it.hasOffset } ?: return null
        return Template.formatTimeValue(time.plusMinutes(ref.offsetMinutes))
    }
}

/** The ready-made days offered on a date question. */
object DateChoices {
    fun pick(session: FillSession, variable: VariableDef, today: LocalDate, daysFromToday: Long) {
        session.setDate(variable, today.plusDays(daysFromToday))
    }
}

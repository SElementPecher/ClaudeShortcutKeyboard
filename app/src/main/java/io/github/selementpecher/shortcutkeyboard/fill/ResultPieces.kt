package io.github.selementpecher.shortcutkeyboard.fill

import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template
import java.time.LocalDateTime
import java.util.Locale

/**
 * One stretch of a finished message: either fixed words ([blank] is [FIXED]) or the value of
 * one blank. [blank] counts the blanks in the order they first appear in the message (0, 1, 2 ...),
 * so a screen can give each one its own colour. The same blank used twice keeps the same number.
 */
data class ResultPiece(val text: String, val blank: Int = FIXED) {
    val isFilled: Boolean get() = blank != FIXED

    companion object {
        const val FIXED = -1
    }
}

/**
 * Splits a finished message into fixed words and filled-in values, so the keyboard can show
 * which parts change. Plain Kotlin, no Android types.
 *
 * Joined together the pieces read exactly like [FillSession.result] (blanks with no value are left out).
 * Values come from [FillSession.printedValues], so fixed and "how long" blanks print as the message does.
 */
object ResultPieces {

    fun of(session: FillSession, now: LocalDateTime = LocalDateTime.now(), locale: Locale = Locale.getDefault()): List<ResultPiece> {
        // Every changing part as it is printed, fixed "how long" ones included (they are not steps).
        val types = FillSession.resolveVariables(session.shortcut, session.template).associate { it.key to it.type }
        return of(session.template, session.printedValues(), types, now, locale)
    }

    fun of(
        template: Template,
        values: Map<String, String>,
        types: Map<String, VariableType>,
        now: LocalDateTime,
        locale: Locale,
    ): List<ResultPiece> {
        val numbers = LinkedHashMap<String, Int>()
        val pieces = ArrayList<ResultPiece>()
        fun add(text: String, blank: Int) {
            if (text.isEmpty()) return
            val last = pieces.lastOrNull()
            if (last != null && last.blank == blank && blank == ResultPiece.FIXED) {
                pieces[pieces.size - 1] = ResultPiece(last.text + text, blank)
            } else {
                pieces += ResultPiece(text, blank)
            }
        }
        for ((index, part) in template.parts.withIndex()) {
            when (part) {
                is Template.Part.Text -> add(part.text, ResultPiece.FIXED)
                is Template.Part.Ref -> {
                    val number = numbers.getOrPut(part.key) { numbers.size }
                    // A one-blank message renders that blank exactly as the whole message would.
                    val shown = Template.parse("{${part.raw}}").render(values, types, now, locale) { "" }
                    add(shown, number)
                    if (shown.isNotEmpty() && Template.needsSpaceBefore(template.parts.getOrNull(index + 1))) add(" ", ResultPiece.FIXED)
                }
            }
        }
        return pieces
    }
}

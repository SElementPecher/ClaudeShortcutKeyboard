package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template
import java.time.LocalDateTime
import java.util.Locale

/**
 * The message preview shown while filling in, with the blank being filled in marked so it is
 * easy to see where it goes. Also says where a blank sits in a sentence, for automatic capitals.
 */
object FillPreview {

    /**
     * [text] matches [FillSession.preview], except that a blank whose value was typed and then
     * deleted shows as "[name]" again. [highlights] are the parts that belong to the marked blank.
     */
    class Result(val text: String, val highlights: List<IntRange>)

    enum class BlankPosition { MESSAGE_START, SENTENCE_START, MID_SENTENCE }

    fun build(
        session: FillSession,
        highlightKey: String?,
        now: LocalDateTime = LocalDateTime.now(),
        locale: Locale = Locale.getDefault(),
    ): Result {
        val values = HashMap<String, String>()
        val types = HashMap<String, VariableType>()
        for (variable in session.variables) {
            session.valueOf(variable)?.takeIf { it.isNotEmpty() }?.let { values[variable.key] = it }
            types[variable.key] = variable.type
        }
        val out = StringBuilder()
        val highlights = ArrayList<IntRange>()
        for (part in session.template.parts) {
            when (part) {
                is Template.Part.Text -> out.append(part.text)
                is Template.Part.Ref -> {
                    val start = out.length
                    // Render each blank on its own so we know exactly where it lands.
                    out.append(Template.parse("{${part.raw}}").render(values, types, now, locale) { "[${it.name}]" })
                    if (part.key == highlightKey && out.length > start) highlights += start until out.length
                }
            }
        }
        return Result(out.toString(), highlights)
    }

    /** Where the blank [key] first appears in the message. */
    fun positionOf(template: Template, key: String): BlankPosition {
        val before = StringBuilder()
        var found = false
        for (part in template.parts) {
            if (part is Template.Part.Ref && part.key == key) {
                found = true
                break
            }
            when (part) {
                is Template.Part.Text -> before.append(part.text)
                is Template.Part.Ref -> before.append(OTHER_BLANK)
            }
        }
        if (!found) return BlankPosition.MID_SENTENCE
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return BlankPosition.MESSAGE_START
        val gap = before.substring(trimmed.length)
        return when {
            '\n' in gap -> BlankPosition.SENTENCE_START
            gap.isNotEmpty() && trimmed.last() in SENTENCE_ENDS -> BlankPosition.SENTENCE_START
            else -> BlankPosition.MID_SENTENCE
        }
    }

    private const val OTHER_BLANK = "x"
    private const val SENTENCE_ENDS = ".!?"
}

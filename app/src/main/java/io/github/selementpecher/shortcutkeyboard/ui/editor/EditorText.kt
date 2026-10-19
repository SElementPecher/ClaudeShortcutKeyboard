package io.github.selementpecher.shortcutkeyboard.ui.editor

import android.content.Context
import io.github.selementpecher.shortcutkeyboard.R

/** Words for the editor that depend on a number or on the draft, made from string resources. */
internal object EditorText {

    /** "4 hours", "1 hour 30 minutes", "2 days" for a length of time (its sign is ignored). */
    fun duration(context: Context, minutes: Long): String {
        val parts = DurationParts.of(minutes)
        val res = context.resources
        val pieces = ArrayList<String>()
        if (parts.days > 0) pieces += res.getQuantityString(R.plurals.editor_days, parts.days, parts.days)
        if (parts.hours > 0) pieces += res.getQuantityString(R.plurals.editor_hours, parts.hours, parts.hours)
        if (parts.minutes > 0 || pieces.isEmpty()) pieces += res.getQuantityString(R.plurals.editor_minutes, parts.minutes, parts.minutes)
        return pieces.joinToString(" ")
    }

    /** What a changing word's chip says: its question, with "+ 4 hours" when it is a time counted from another. */
    fun chipLabel(context: Context, question: String, offsetMinutes: Long): String = when {
        offsetMinutes > 0 -> context.getString(R.string.editor_chip_later, question, duration(context, offsetMinutes))
        offsetMinutes < 0 -> context.getString(R.string.editor_chip_earlier, question, duration(context, offsetMinutes))
        else -> question
    }

    fun kindName(kind: BlankKind): Int = when (kind) {
        BlankKind.LIST -> R.string.editor_kind_list
        BlankKind.TIME -> R.string.editor_kind_time
        BlankKind.DATE -> R.string.editor_kind_date
        BlankKind.NUMBER -> R.string.editor_kind_number
    }

    fun kindHint(kind: BlankKind): Int = when (kind) {
        BlankKind.LIST -> R.string.editor_kind_list_hint
        BlankKind.TIME -> R.string.editor_kind_time_hint
        BlankKind.DATE -> R.string.editor_kind_date_hint
        BlankKind.NUMBER -> R.string.editor_kind_number_hint
    }

    /** The line that says what the keyboard will do for a kind that has no answers to type. */
    fun kindInfo(kind: BlankKind): Int? = when (kind) {
        BlankKind.LIST -> null
        BlankKind.TIME -> R.string.editor_info_time
        BlankKind.DATE -> R.string.editor_info_date
        BlankKind.NUMBER -> R.string.editor_info_number
    }
}

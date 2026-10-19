package io.github.selementpecher.shortcutkeyboard.ui.editor

import android.content.Context
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.template.RelativeUnit

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

    /** What a time counted from another time says on its chip: "When + 4 hours". */
    fun referenceLabel(context: Context, question: String, offsetMinutes: Long): String = when {
        offsetMinutes > 0 -> context.getString(R.string.editor_chip_later, question, duration(context, offsetMinutes))
        offsetMinutes < 0 -> context.getString(R.string.editor_chip_earlier, question, duration(context, offsetMinutes))
        else -> question
    }

    /** The name of a kind, for tiles and for a half-made chip: "A list", "A time", "A date", "A number", "How long". */
    fun kindName(kind: BlankKind): Int = when (kind) {
        BlankKind.LIST -> R.string.chain_kind_list
        BlankKind.TIME -> R.string.chain_kind_time
        BlankKind.DATE -> R.string.chain_kind_date
        BlankKind.NUMBER -> R.string.chain_kind_number
        BlankKind.TIME_AHEAD, BlankKind.TIME_AGO -> R.string.chain_kind_howlong
    }

    fun timeChoiceName(choice: TimeChoice): Int = when (choice) {
        TimeChoice.CLOCK -> R.string.chain_time_clock
        TimeChoice.FROM_NOW -> R.string.chain_time_from_now
        TimeChoice.AGO -> R.string.chain_time_ago
        TimeChoice.AFTER_OTHER -> R.string.chain_time_after_other
    }

    fun unitName(unit: RelativeUnit): Int = when (unit) {
        RelativeUnit.MINS -> R.string.chain_unit_mins
        RelativeUnit.HOURS -> R.string.chain_unit_hours
        RelativeUnit.DAYS -> R.string.chain_unit_days
    }

    /** The small line for a row of the check screen: "2 answers", "3 amounts", "Always 10 mins". */
    fun answersLine(context: Context, q: AskedQuestion): String? = when {
        q.fixedAmount != null -> context.getString(R.string.editor_check_always, q.fixedAmount)
        q.kind == BlankKind.LIST -> context.resources.getQuantityString(R.plurals.editor_check_answers, q.answerCount, q.answerCount)
        q.kind.isRelative -> context.resources.getQuantityString(R.plurals.editor_check_amounts, q.answerCount, q.answerCount)
        else -> null
    }
}

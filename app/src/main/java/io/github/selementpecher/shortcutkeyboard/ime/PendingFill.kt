package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import java.util.Locale

/**
 * A fill-in that was left to add a new answer in the app ("+ New"). The keyboard hides while the
 * app is open, so this is kept and picked up again when the keyboard comes back.
 */
data class PendingFill(
    val shortcutId: String,
    /** The values so far, by blank key. */
    val values: Map<String, String>,
    val index: Int,
    /** Key of the blank that asked for a new answer. */
    val blankKey: String,
    /** That blank's answers before the app opened. */
    val optionsBefore: List<String>,
    val createdAtMs: Long,
) {
    fun isFresh(nowMs: Long): Boolean = nowMs - createdAtMs in 0..MAX_AGE_MS

    /** What came back: the rebuilt fill-in, and whether it moved on past the blank that asked. */
    class Restored(val session: FillSession, val finished: Boolean)

    /**
     * Rebuilds the fill-in from the LATEST copy of the shortcut with the old values put back. If the
     * blank that asked now has a new answer, it is used and the fill-in moves on. [finished] is
     * true when that was the last question, so the next screen is the check.
     */
    fun restore(latest: Shortcut, locale: Locale = Locale.getDefault()): Restored {
        val session = FillSession(latest, locale = locale)
        for (variable in session.variables) {
            values[variable.key]?.let { session.setValue(variable, it) }
        }
        session.goTo(index)
        val asked = session.variables.firstOrNull { it.key == blankKey }
        if (asked != null && AnswerList.hasNewOption(optionsBefore, asked.options)) {
            val answer = asked.lastValue?.takeIf { it.isNotBlank() } ?: asked.options.last { it.isNotBlank() }
            session.setValue(asked, answer)
            session.goTo(session.variables.indexOf(asked))
            return Restored(session, finished = !session.next())
        }
        return Restored(session, finished = false)
    }

    companion object {
        const val MAX_AGE_MS = 10L * 60L * 1000L

        fun capture(session: FillSession, blankKey: String, optionsBefore: List<String>, nowMs: Long): PendingFill =
            PendingFill(
                shortcutId = session.shortcut.id,
                values = session.variables.mapNotNull { v -> session.valueOf(v)?.let { v.key to it } }.toMap(),
                index = session.index,
                blankKey = blankKey,
                optionsBefore = optionsBefore,
                createdAtMs = nowMs,
            )
    }
}

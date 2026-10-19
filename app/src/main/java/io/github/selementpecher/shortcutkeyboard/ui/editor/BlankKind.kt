package io.github.selementpecher.shortcutkeyboard.ui.editor

import io.github.selementpecher.shortcutkeyboard.ui.ShortcutDraft

/**
 * The kinds of changing part. The owner picks one of four ([topLevel]); "A time" then opens a second
 * choice, which is where [TIME_AHEAD] ("from now") and [TIME_AGO] come from.
 */
enum class BlankKind(val glyph: String) {
    /** Pick one of a few answers. */
    LIST("☰"),

    /** A clock time, starting at the time now. */
    TIME("🕒"),
    DATE("📅"),
    NUMBER("🔢"),

    /** "How long from now": the keyboard works out the clock time. */
    TIME_AHEAD("🕒"),

    /** "How long ago". */
    TIME_AGO("🕒");

    /** True for the two "how long" kinds. */
    val isRelative: Boolean get() = this == TIME_AHEAD || this == TIME_AGO

    companion object {
        /** The four kinds the owner chooses between. */
        val topLevel: List<BlankKind> = listOf(LIST, TIME, DATE, NUMBER)
    }
}

/**
 * The question a changing part asks, which is also the name the keyboard stores it under.
 * Plain Kotlin, no Android types.
 */
object BlankQuestions {
    private const val MAX_LENGTH = 40

    /** Questions offered as one-tap suggestions for each kind. */
    fun suggestions(kind: BlankKind): List<String> = when (kind) {
        BlankKind.LIST -> listOf("Who", "What", "How much", "Where", "Which")
        BlankKind.TIME -> listOf("When", "What time")
        BlankKind.DATE -> listOf("Date", "What day")
        BlankKind.NUMBER -> listOf("How many", "How much")
        BlankKind.TIME_AHEAD, BlankKind.TIME_AGO -> listOf("How long", "When")
    }

    fun default(kind: BlankKind): String = suggestions(kind).first()

    /**
     * Turns what was typed into a question that can be stored: no brackets or bars, nothing
     * that reads like a time offset (`+3`, `-5`), not "now" or "today", and different from every
     * name in [taken] (compared ignoring case; a clash gets " 2", " 3"...). An empty question
     * becomes the default for [kind].
     */
    fun clean(raw: String, kind: BlankKind, taken: Set<String>): String {
        val takenKeys = taken.map { it.trim().lowercase() }.toSet() + setOf("now", "today")
        var text = tidy(raw)
        if (text.isEmpty() || ShortcutDraft.checkBlankName(text) == ShortcutDraft.NameProblem.INVALID) {
            text = default(kind)
        }
        var candidate = text
        var n = 2
        while (candidate.lowercase() in takenKeys) {
            candidate = "$text $n"
            n++
        }
        return if (ShortcutDraft.checkBlankName(candidate) == null) candidate else uniqueDefault(kind, takenKeys)
    }

    private fun tidy(raw: String): String =
        raw.replace(Regex("[{}|\\r\\n\\t]"), " ")
            .replace(Regex("[+-](?=\\s*\\d)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_LENGTH)
            .trim()

    private fun uniqueDefault(kind: BlankKind, takenKeys: Set<String>): String {
        val base = default(kind)
        var candidate = base
        var n = 2
        while (candidate.lowercase() in takenKeys) {
            candidate = "$base $n"
            n++
        }
        return candidate
    }
}

package io.github.selementpecher.shortcutkeyboard.template

import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType

/**
 * The ready-made answers on a time ("When") or date question, chosen by the owner: 1 to [MAX], always from the past to the future.
 *
 * A time choice is minutes from now (0 = now, negative = ago, positive = from now). A date choice is days from
 * today. They are saved as numbers in the blank's options; no saved options means the usual ones.
 */
object QuickChoices {
    const val MAX = 6

    private val TIME_DEFAULT = listOf(-60L, -30L, -15L, 0L)
    private val DATE_DEFAULT = listOf(-1L, 0L, 1L)

    private val TIME_PRESETS = listOf(-180L, -120L, -60L, -45L, -30L, -15L, -10L, -5L, 0L, 15L, 30L, 60L, 120L)
    private val DATE_PRESETS = listOf(-7L, -3L, -2L, -1L, 0L, 1L, 2L, 3L, 7L)

    fun isQuick(type: VariableType): Boolean = type == VariableType.TIME || type == VariableType.DATE

    fun defaults(type: VariableType): List<Long> = if (type == VariableType.DATE) DATE_DEFAULT else TIME_DEFAULT

    /** Everything that can be offered for [type], from the past to the future. */
    fun presets(type: VariableType): List<Long> = if (type == VariableType.DATE) DATE_PRESETS else TIME_PRESETS

    fun of(variable: VariableDef): List<Long> = parse(variable.options, variable.type)

    /** The saved [options] as choices: valid, not repeated, at most [MAX], from the past to the future; the usual ones when none are saved. */
    fun parse(options: List<String>, type: VariableType): List<Long> {
        val allowed = presets(type).toSet()
        val values = options.mapNotNull { it.trim().toLongOrNull() }.filter { it in allowed }.distinct().sorted().take(MAX)
        return values.ifEmpty { defaults(type) }
    }

    /** The choice that is picked first: now / today when it is there, otherwise the earliest. */
    fun default(choices: List<Long>): Long = if (0L in choices) 0L else choices.first()

    fun encode(choices: List<Long>): List<String> = choices.map { it.toString() }

    /** Takes [value] out if it is chosen (never the last one), or adds it at the end (never more than [MAX]). */
    fun toggle(choices: List<Long>, value: Long): List<Long> = when {
        value in choices -> if (choices.size > 1) choices - value else choices
        choices.size < MAX -> (choices + value).sorted()
        else -> choices
    }

    /** True when the first tile should be twice as wide so the rows are full. */
    fun firstIsWide(tileCount: Int): Boolean = tileCount % 2 == 1
}

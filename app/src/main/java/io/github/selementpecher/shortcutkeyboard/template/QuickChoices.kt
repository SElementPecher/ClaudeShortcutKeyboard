package io.github.selementpecher.shortcutkeyboard.template

import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType

/**
 * The ready-made answers on a time ("When") or date question, chosen by the owner: 1 to [MAX].
 *
 * A time choice is minutes from now (0 = now, negative = ago, positive = from now). A date choice is days from
 * today. They are saved as numbers in the blank's options; no saved options means the usual ones.
 */
object QuickChoices {
    const val MAX = 6

    private val TIME_DEFAULT = listOf(0L, -15L, -30L, -60L)
    private val DATE_DEFAULT = listOf(0L, -1L, 1L)

    private val TIME_PRESETS = listOf(0L, -5L, -10L, -15L, -30L, -45L, -60L, -120L, -180L, 15L, 30L, 60L, 120L)
    private val DATE_PRESETS = listOf(0L, -1L, 1L, -2L, 2L, -3L, 3L, -7L, 7L)

    fun isQuick(type: VariableType): Boolean = type == VariableType.TIME || type == VariableType.DATE

    fun defaults(type: VariableType): List<Long> = if (type == VariableType.DATE) DATE_DEFAULT else TIME_DEFAULT

    /** Everything that can be offered for [type], in the order shown when choosing. */
    fun presets(type: VariableType): List<Long> = if (type == VariableType.DATE) DATE_PRESETS else TIME_PRESETS

    fun of(variable: VariableDef): List<Long> = parse(variable.options, variable.type)

    /** The saved [options] as choices: valid and not repeated, at most [MAX]; the usual ones when none are saved. */
    fun parse(options: List<String>, type: VariableType): List<Long> {
        val allowed = presets(type).toSet()
        val values = options.mapNotNull { it.trim().toLongOrNull() }.filter { it in allowed }.distinct().take(MAX)
        return values.ifEmpty { defaults(type) }
    }

    fun encode(choices: List<Long>): List<String> = choices.map { it.toString() }

    /** Takes [value] out if it is chosen (never the last one), or adds it at the end (never more than [MAX]). */
    fun toggle(choices: List<Long>, value: Long): List<Long> = when {
        value in choices -> if (choices.size > 1) choices - value else choices
        choices.size < MAX -> choices + value
        else -> choices
    }

    /** True when the first tile should be twice as wide so the rows are full. */
    fun firstIsWide(tileCount: Int): Boolean = tileCount % 2 == 1
}

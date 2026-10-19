package io.github.selementpecher.shortcutkeyboard.ui.editor

/** A length of time split into days, hours and minutes, for saying "4 hours" or "1 hour 30 minutes". */
data class DurationParts(val days: Int, val hours: Int, val minutes: Int) {
    companion object {
        /** The size of [minutes], ignoring its sign. */
        fun of(minutes: Long): DurationParts {
            val rest = Math.abs(minutes)
            return DurationParts(
                days = (rest / (24 * 60)).toInt(),
                hours = ((rest % (24 * 60)) / 60).toInt(),
                minutes = (rest % 60).toInt(),
            )
        }
    }
}

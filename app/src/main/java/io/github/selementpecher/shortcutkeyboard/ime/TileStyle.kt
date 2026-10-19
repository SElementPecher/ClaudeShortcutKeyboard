package io.github.selementpecher.shortcutkeyboard.ime

/** Small pure helpers for how tiles look and read. */
object TileStyle {
    /** How many strong colours shortcut tiles can have. */
    const val COLOUR_COUNT = 6

    /** A stable colour number (0..5) for a shortcut, from its id. */
    fun colourIndex(id: String): Int = Math.floorMod(id.hashCode(), COLOUR_COUNT)

    fun folderLabel(name: String): String = "📁 $name"

    /** The same colour, darker by [amount] (0..1), keeping its alpha. Used for pressed tiles. */
    fun darken(argb: Int, amount: Float = 0.18f): Int {
        val factor = 1f - amount.coerceIn(0f, 1f)
        val a = argb ushr 24
        val r = (((argb shr 16) and 0xFF) * factor).toInt()
        val g = (((argb shr 8) and 0xFF) * factor).toInt()
        val b = ((argb and 0xFF) * factor).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** "Kids › Meds" cut from the start, so the deepest folder is the part that stays. */
    fun pathText(names: List<String>, maxChars: Int = 40): String {
        val full = names.joinToString(" › ")
        return if (full.length <= maxChars) full else "…" + full.takeLast(maxChars - 1)
    }
}

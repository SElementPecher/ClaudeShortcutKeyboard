package io.github.selementpecher.shortcutkeyboard.ime

/** Small pure helpers for how tiles look and read. */
object TileStyle {
    const val FOLDER_GLYPH = "📁"

    /**
     * The picture on a tile: the chosen emoji, else the first letter of the name in upper case.
     * A folder with no picture gets a folder emoji.
     */
    fun glyphFor(icon: String, name: String, isFolder: Boolean): String {
        val chosen = icon.trim()
        if (chosen.isNotEmpty()) return chosen
        if (isFolder) return FOLDER_GLYPH
        val first = name.trim().codePoints().findFirst()
        return if (first.isPresent) String(Character.toChars(first.asInt)).uppercase() else "•"
    }

    /** The small cue on a folder tile. Not needed when the folder's own picture already is a folder. */
    fun folderBadge(glyph: String): String? = if (glyph == FOLDER_GLYPH) null else FOLDER_GLYPH

    /** The same colour, darker by [amount] (0..1), keeping its alpha. */
    fun darken(argb: Int, amount: Float = 0.18f): Int = mix(argb, 0xFF000000.toInt(), amount)

    /** The same colour, lighter by [amount] (0..1), keeping its alpha. */
    fun lighten(argb: Int, amount: Float = 0.18f): Int = mix(argb, 0xFFFFFFFF.toInt(), amount)

    /** The colour of a pressed tile: darker for light tiles, lighter for dark ones. */
    fun pressed(argb: Int): Int = if (luminance(argb) > 0.4f) darken(argb, 0.12f) else lighten(argb, 0.16f)

    /** Mixes [argb] towards [other] by [amount] (0..1); the alpha of [argb] is kept. */
    fun mix(argb: Int, other: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (argb shr shift) and 0xFF
            val b = (other shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return ((argb ushr 24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Rough brightness 0..1 of an opaque colour. */
    fun luminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /** "Kids › Meds" cut from the start, so the deepest folder is the part that stays. */
    fun pathText(names: List<String>, maxChars: Int = 40): String {
        val full = names.joinToString(" › ")
        return if (full.length <= maxChars) full else "…" + full.takeLast(maxChars - 1)
    }
}

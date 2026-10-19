package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.TileLayout

/** Sizes the keyboard works out from the Look and the screen. Plain Kotlin, no views. */
object KeyboardMetrics {
    /** Screens shorter than this (phones on their side) get the compact 3 x 2 layout. */
    const val COMPACT_SCREEN_HEIGHT_DP = 480

    /** Android 13, where the system draws keyboard-switcher icons in the navigation bar area. */
    private const val SWITCHER_SDK = 33

    fun isCompact(screenHeightDp: Int): Boolean = screenHeightDp in 1 until COMPACT_SCREEN_HEIGHT_DP

    /** (columns, rows) of the tile grid. Always six tiles. */
    fun gridShape(look: Look, compact: Boolean): Pair<Int, Int> =
        if (compact) 3 to 2 else look.keyboardColumns to look.keyboardRows

    /** Picture beside the name (true) or picture above the name (false). */
    fun pictureBesideName(look: Look, compact: Boolean): Boolean = compact || look.tileLayout == TileLayout.WIDE

    /**
     * The panel's height in dp. Bigger text (the owner's Large setting and the phone's font size
     * together) gets a little more room, never more than [MAX_EXTRA_DP], so tiles do not get cramped.
     */
    fun panelHeightDp(baseDp: Int, combinedTextScale: Float): Int =
        baseDp + Math.round(((combinedTextScale - 1f) * EXTRA_DP_PER_SCALE).coerceIn(0f, MAX_EXTRA_DP.toFloat()))

    private const val EXTRA_DP_PER_SCALE = 80f
    const val MAX_EXTRA_DP = 40

    /** Extra gap, in dp, kept clear above the system's keyboard-switcher icons. */
    fun safetyGapDp(sdk: Int): Int = if (sdk >= SWITCHER_SDK) 16 else 8

    /** The system's switcher icons are a touch target about this tall, in dp, measured from the screen's bottom edge. */
    private const val SWITCHER_ZONE_DP = 56

    /**
     * Padding under the tiles: the navigation bar inset (never negative) plus the safety gap, and
     * on Android 13+ never less than the switcher zone, so the icons cannot touch a tile even
     * with the slim gesture bar (whose inset is only about 24dp).
     */
    fun bottomPaddingPx(navigationInsetPx: Int, density: Float, sdk: Int): Int {
        val padded = maxOf(navigationInsetPx, 0) + Math.round(safetyGapDp(sdk) * density)
        val zone = if (sdk >= SWITCHER_SDK) Math.round(SWITCHER_ZONE_DP * density) else 0
        return maxOf(padded, zone)
    }
}

package io.github.selementpecher.shortcutkeyboard.look

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The 16 colours an owner can pick for a tile, made for each Look and for light and dark.
 *
 * Every pair is nudged until the text on it has a contrast of at least [MIN_CONTRAST], so any choice
 * stays easy to read. Plain Kotlin, no Android types.
 */
internal object Swatches {
    const val COUNT = 16
    const val MIN_CONTRAST = 4.8

    /** Hue (0–360) of each swatch, in the order shown. The last one is a quiet grey-blue. */
    private val HUES = floatArrayOf(2f, 24f, 42f, 56f, 80f, 125f, 155f, 176f, 192f, 210f, 228f, 250f, 272f, 300f, 338f, 215f)

    /** How much of the colour each swatch keeps (the last, "Slate", is nearly grey). */
    private val SATURATION = FloatArray(COUNT) { if (it == COUNT - 1) 0.18f else 1f }

    enum class Style { CALM_LIGHT, CALM_DARK, BOLD, SQUARES_LIGHT, SQUARES_DARK }

    fun build(style: Style): List<TileColors> = (0 until COUNT).map { i ->
        val h = HUES[i]
        val k = SATURATION[i]
        when (style) {
            Style.CALM_LIGHT -> pair(h, 0.20f * k, 0.97f, hsv(h, 0.75f * k, 0.22f), lighter = true)
            Style.CALM_DARK -> pair(h, 0.45f * k, 0.30f, hsv(h, 0.12f * k, 0.96f), lighter = false)
            Style.BOLD -> pair(h, 0.62f * k, 1.0f, BOLD_INK, lighter = true)
            Style.SQUARES_LIGHT -> pair(h, 0.17f * k, 0.98f, SQUARES_INK, lighter = true)
            Style.SQUARES_DARK -> pair(h, 0.50f * k, 0.26f, SQUARES_LIGHT_INK, lighter = false)
        }
    }

    private val BOLD_INK = rgb(0x0B, 0x0C, 0x10)
    private val SQUARES_INK = rgb(0x11, 0x18, 0x27)
    private val SQUARES_LIGHT_INK = rgb(0xF3, 0xF4, 0xF6)

    /** Background from hue/saturation/value, moved lighter or darker until [fg] is readable on it. */
    private fun pair(h: Float, s: Float, v: Float, fg: Int, lighter: Boolean): TileColors {
        var sat = s
        var value = v
        repeat(60) {
            val bg = hsv(h, sat, value)
            if (contrast(bg, fg) >= MIN_CONTRAST) return TileColors(bg, fg)
            if (lighter) {
                sat *= 0.94f
                value = min(1f, value + 0.01f)
            } else {
                value = max(0f, value - 0.012f)
            }
        }
        return TileColors(hsv(h, sat, value), fg)
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Opaque ARGB from hue (degrees), saturation and value (0–1). */
    fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return rgb(channel(r), channel(g), channel(b))
    }

    private fun luminance(color: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color shr 16 and 0xFF) + 0.7152 * channel(color shr 8 and 0xFF) + 0.0722 * channel(color and 0xFF)
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}

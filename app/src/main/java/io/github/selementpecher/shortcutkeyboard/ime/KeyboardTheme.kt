package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.Typeface
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.Palette
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf

/** The Look the keyboard is drawn in, with its colours for the phone's light or dark setting. */
internal class KeyboardTheme(context: Context, val look: Look, val compact: Boolean) {
    val palette: Palette = look.palette(context)
    private val density = context.resources.displayMetrics.density

    val pictureBesideName = KeyboardMetrics.pictureBesideName(look, compact)
    val nameTypeface: Typeface = typefaceOf(look.type.tileWeight)
    val headingTypeface: Typeface = typefaceOf(look.type.headingWeight)
    val bodyTypeface: Typeface = typefaceOf(if (look.type.tileWeight >= 700) 600 else 500)

    /** Tiles get a little less round on short screens, where they are short too. */
    val tileRadius: Float = dp(if (compact) minOf(look.shape.tileRadiusDp, 16) else look.shape.tileRadiusDp).toFloat()
    val cardRadius: Float = dp(look.shape.cardRadiusDp).toFloat()
    val strokePx: Int = if (look.shape.strokeDp > 0f) dp(look.shape.strokeDp).coerceAtLeast(1) else 0
    val elevationPx: Float = dp(look.shape.elevationDp).toFloat()

    fun dp(value: Int): Int = Math.round(value * density)

    fun dp(value: Float): Int = Math.round(value * density)
}

package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.Typeface
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.look.Palette
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf

/** The Look the keyboard is drawn in, with its colours for the phone's light or dark setting and the owner's text size. */
internal class KeyboardTheme(context: Context, val look: Look, val compact: Boolean) {
    val palette: Palette = look.palette(context)
    private val density = context.resources.displayMetrics.density

    /** 1.0, or a little more when the owner chose "Large" text. Every size from the Look goes through [sp]. */
    val textScale: Float = LookStore.textScale(context)

    /** Text size and system font size together, to decide how much room the panel needs. */
    val combinedScale: Float = textScale * context.resources.configuration.fontScale

    val animations: Boolean = Motion.enabled(context)

    val pictureBesideName = KeyboardMetrics.pictureBesideName(look, compact)
    val nameTypeface: Typeface = typefaceOf(look.type.tileWeight)
    val headingTypeface: Typeface = typefaceOf(look.type.headingWeight)
    val bodyTypeface: Typeface = typefaceOf(if (look.type.tileWeight >= 700) 600 else 500)

    /** Tiles get a little less round on short screens, where they are short too. */
    val tileRadius: Float = dp(if (compact) minOf(look.shape.tileRadiusDp, 16) else look.shape.tileRadiusDp).toFloat()
    val cardRadius: Float = dp(look.shape.cardRadiusDp).toFloat()
    val buttonRadius: Float = dp(look.shape.buttonRadiusDp).toFloat()
    val strokePx: Int = if (look.shape.strokeDp > 0f) dp(look.shape.strokeDp).coerceAtLeast(1) else 0
    val elevationPx: Float = dp(look.shape.elevationDp).toFloat()

    /** A text size from the Look, made bigger when the owner chose Large text. */
    fun sp(value: Float): Float = value * textScale

    fun dp(value: Int): Int = Math.round(value * density)

    fun dp(value: Float): Int = Math.round(value * density)

    companion object {
        /** A little extra letter spacing (+0.02 em) makes words easier to tell apart. */
        const val LETTER_SPACING = 0.02f
    }
}

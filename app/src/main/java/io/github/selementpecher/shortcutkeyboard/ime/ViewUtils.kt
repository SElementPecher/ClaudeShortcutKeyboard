package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView

/** Small helpers shared by the keyboard's views. */

internal fun Context.px(dimenRes: Int): Int = resources.getDimensionPixelSize(dimenRes)

internal fun TextView.setTextSizeRes(dimenRes: Int) {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(dimenRes))
}

/** A copy of the drawable [res] tinted with [color], or null if it cannot be loaded. */
internal fun Context.tintedDrawable(res: Int, color: Int): Drawable? =
    getDrawable(res)?.mutate()?.apply { setTint(color) }

/** The same short buzz the keys give, if the user has touch feedback on. */
internal fun View.keyFeedback() {
    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

/** Click listener with key-press feedback. */
internal fun View.onTap(action: () -> Unit) {
    isSoundEffectsEnabled = false
    setOnClickListener {
        it.keyFeedback()
        action()
    }
}

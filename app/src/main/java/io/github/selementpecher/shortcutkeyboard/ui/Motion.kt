package io.github.selementpecher.shortcutkeyboard.ui

import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Small, quiet movements: a press shrinks a tile a little, something new "pops" in.
 * Everything is skipped when the phone has animations turned off.
 */
internal object Motion {

    /** How long a press takes to shrink or grow back, in milliseconds. */
    const val PRESS_MS = 120L

    /** How long something new takes to pop in, in milliseconds. */
    const val POP_MS = 160L

    /** False when the phone's animation speed is set to "off" (animator duration scale 0). */
    fun enabled(context: Context): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    } catch (e: Exception) {
        true
    }

    /** Makes [view] shrink to [scale] while it is held down. Taps and long presses still work. */
    fun pressable(view: View, scale: Float = 0.97f) {
        view.setOnTouchListener { v, event ->
            if (enabled(v.context)) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale).setDuration(PRESS_MS).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(PRESS_MS).start()
                }
            }
            false
        }
    }

    /** Grows [view] in from a little smaller and transparent. For things that were just added. */
    fun pop(view: View) {
        if (!enabled(view.context)) return
        view.scaleX = 0.9f
        view.scaleY = 0.9f
        view.alpha = 0f
        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(POP_MS).setInterpolator(DecelerateInterpolator()).start()
    }
}

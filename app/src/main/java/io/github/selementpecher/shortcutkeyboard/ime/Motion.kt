package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.provider.Settings

/** Whether little animations (press, pop) should run. They are all skipped when the phone has animations turned off. */
internal object Motion {
    const val PRESS_MS = 120L
    const val POP_MS = 160L

    fun enabled(context: Context): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    } catch (e: RuntimeException) {
        true
    }
}

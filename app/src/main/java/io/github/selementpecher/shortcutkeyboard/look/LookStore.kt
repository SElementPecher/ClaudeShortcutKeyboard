package io.github.selementpecher.shortcutkeyboard.look

import android.content.Context
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Remembers which [Look] is chosen: one for the app and, if the owner mixes and matches, another for the keyboard
 * (by default the keyboard follows the app). Shared by the app and the keyboard (same process).
 *
 * Listeners run on whatever thread called [set]; post to the main thread before touching views.
 */
object LookStore {
    private const val PREFS = "look"
    private const val KEY = "look_id"
    private const val KEY_LARGE_TEXT = "large_text"
    private const val KEY_KEYBOARD = "keyboard_look_id"

    /** How much bigger text is when "Large text" is on. */
    const val LARGE_TEXT_SCALE = 1.15f

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun current(context: Context): Look {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Looks.byId(prefs.getString(KEY, null)).also { Highlighter.on = it.ruled }
    }

    fun set(context: Context, look: Look) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, look.id).apply()
        Highlighter.on = look.ruled
        listeners.forEach { it() }
    }

    /**
     * The keyboard's Look: its own choice, or the app's when it follows the app. Does not touch [Highlighter.on],
     * which belongs to the app's screens; the keyboard reads `look.ruled` itself.
     */
    fun keyboard(context: Context): Look {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Looks.byId(prefs.getString(KEY_KEYBOARD, null) ?: prefs.getString(KEY, null))
    }

    /** True while the keyboard simply uses the app's Look. */
    fun keyboardFollowsApp(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_KEYBOARD, null) == null

    /** Gives the keyboard its own [look], or null to follow the app again. */
    fun setKeyboard(context: Context, look: Look?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (look == null) remove(KEY_KEYBOARD) else putString(KEY_KEYBOARD, look.id)
        }.apply()
        listeners.forEach { it() }
    }

    /** True if the owner chose "Large" text size. */
    fun isLargeText(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LARGE_TEXT, false)

    /** 1.0, or [LARGE_TEXT_SCALE] when large text is on. Multiply every text size from the Look by this. */
    fun textScale(context: Context): Float = if (isLargeText(context)) LARGE_TEXT_SCALE else 1f

    fun setLargeText(context: Context, large: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LARGE_TEXT, large).apply()
        listeners.forEach { it() }
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }
}

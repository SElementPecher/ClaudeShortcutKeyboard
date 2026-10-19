package io.github.selementpecher.shortcutkeyboard.look

import android.content.Context
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Remembers which [Look] is chosen. Shared by the app and the keyboard (same process).
 *
 * Listeners run on whatever thread called [set]; post to the main thread before touching views.
 */
object LookStore {
    private const val PREFS = "look"
    private const val KEY = "look_id"

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun current(context: Context): Look {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Looks.byId(prefs.getString(KEY, null))
    }

    fun set(context: Context, look: Look) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, look.id).apply()
        listeners.forEach { it() }
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }
}

package io.github.selementpecher.shortcutkeyboard.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The saved shortcuts, shared by the keyboard and the app (same process).
 *
 * Kept in memory and written to a small JSON file in app storage on every change.
 */
object ShortcutRepository {
    private const val TAG = "ShortcutRepository"
    private const val FILE_NAME = "shortcuts.json"

    private var cache: List<Shortcut>? = null
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun newId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun all(context: Context): List<Shortcut> {
        cache?.let { return it }
        val loaded = load(context.applicationContext)
        cache = loaded
        return loaded
    }

    fun find(context: Context, id: String): Shortcut? = all(context).firstOrNull { it.id == id }

    /** Adds [shortcut], or replaces the one with the same id in place. */
    fun save(context: Context, shortcut: Shortcut) {
        update(context) { list ->
            val index = list.indexOfFirst { it.id == shortcut.id }
            if (index >= 0) list.toMutableList().also { it[index] = shortcut } else list + shortcut
        }
    }

    fun delete(context: Context, id: String) {
        update(context) { list -> list.filterNot { it.id == id } }
    }

    fun replaceAll(context: Context, shortcuts: List<Shortcut>) {
        update(context) { shortcuts }
    }

    /** Adds [shortcuts], replacing any existing ones with the same id. */
    fun merge(context: Context, shortcuts: List<Shortcut>) {
        update(context) { list ->
            // If the incoming list repeats an id, the last copy wins.
            val incoming = shortcuts.associateBy { it.id }
            list.map { incoming[it.id] ?: it } + incoming.values.filter { new -> list.none { it.id == new.id } }
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun update(context: Context, change: (List<Shortcut>) -> List<Shortcut>) {
        synchronized(this) {
            val updated = change(all(context))
            write(context.applicationContext, updated)
            cache = updated
        }
        listeners.forEach { it() }
    }

    private fun file(context: Context) = AtomicFile(File(context.filesDir, FILE_NAME))

    private fun load(context: Context): List<Shortcut> {
        val file = file(context)
        if (!file.baseFile.exists()) {
            // If this write fails (e.g. a full disk) the samples are still shown and saved on the next change.
            runCatching { write(context, SampleShortcuts.all) }.onFailure { Log.e(TAG, "Could not save samples", it) }
            return SampleShortcuts.all
        }
        return try {
            ShortcutJson.fromJson(String(file.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            // Keep the unreadable file so nothing is lost, and start empty rather than crash.
            Log.e(TAG, "Could not read $FILE_NAME", e)
            runCatching { file.baseFile.copyTo(File(context.filesDir, "$FILE_NAME.unreadable"), overwrite = true) }
            emptyList()
        }
    }

    private fun write(context: Context, shortcuts: List<Shortcut>) {
        val file = file(context)
        val stream = file.startWrite()
        try {
            stream.write(ShortcutJson.toJson(shortcuts).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}

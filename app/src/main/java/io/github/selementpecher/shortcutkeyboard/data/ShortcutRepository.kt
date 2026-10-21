package io.github.selementpecher.shortcutkeyboard.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.Node
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The saved folders and shortcuts, shared by the keyboard and the app (same process).
 *
 * Kept in memory and written to a small JSON file in app storage on every change.
 */
object ShortcutRepository {
    private const val TAG = "ShortcutRepository"
    private const val FILE_NAME = "shortcuts.json"
    private const val EXAMPLES_MARKER = "examples-added"

    private var cache: Library? = null
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun newId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun library(context: Context): Library {
        cache?.let { return it }
        val loaded = load(context.applicationContext)
        cache = loaded
        return loaded
    }

    /** Every shortcut, in any folder. */
    fun all(context: Context): List<Shortcut> = library(context).shortcuts

    fun folders(context: Context): List<Folder> = library(context).folders

    fun find(context: Context, id: String): Shortcut? = library(context).shortcut(id)

    fun findFolder(context: Context, id: String): Folder? = library(context).folder(id)

    /** What is inside [parentId] (null = top level), in display order. */
    fun children(context: Context, parentId: String?): List<Node> = library(context).children(parentId)

    /** Adds [shortcut], or replaces the one with the same id in place. */
    fun save(context: Context, shortcut: Shortcut) = update(context) { it.withShortcut(shortcut) }

    fun saveFolder(context: Context, folder: Folder) = update(context) { it.withFolder(folder) }

    fun delete(context: Context, id: String) = update(context) { it.withoutShortcut(id) }

    /** Adds the starter folders and quiKies that are missing, without touching anything you have. */
    fun addExamples(context: Context) = update(context) { it.withExamples(SampleShortcuts.library) }

    /** Adds the demo folder and its quiKeys (one per kind of changing part) that are missing. */
    fun addDemo(context: Context) = update(context) { it.withExamples(DemoShortcuts.library) }

    /** Deletes several shortcuts and folders in one go (one save). */
    fun deleteMany(context: Context, ids: Set<String>) = update(context) { it.withoutAll(ids) }

    /** Copies quiKies and folders (a folder with everything inside it); each copy is named with [suffix] added. */
    fun duplicateMany(context: Context, ids: Set<String>, suffix: String) =
        update(context) { it.withCopiesOf(ids, ::newId, suffix) }

    /** Moves several tiles into [newParentId] (null = top level) in one go. Says how many actually moved. */
    fun moveMany(context: Context, ids: Set<String>, newParentId: String?): Int {
        var moved = 0
        update(context) { lib ->
            val result = lib.movedAll(ids, newParentId)
            moved = ids.count { id -> (result.folder(id)?.parentId ?: result.shortcut(id)?.parentId) == newParentId && (lib.folder(id)?.parentId ?: lib.shortcut(id)?.parentId) != newParentId }
            result
        }
        return moved
    }

    /** Deletes a folder and everything inside it. */
    fun deleteFolder(context: Context, id: String) = update(context) { it.withoutFolder(id) }

    /** Moves a tile to [newParentId] (null = top level) at [index]. Returns false if that is not allowed. */
    fun move(context: Context, nodeId: String, newParentId: String?, index: Int): Boolean {
        var allowed = false
        update(context) { lib ->
            val moved = lib.moved(nodeId, newParentId, index)
            allowed = moved != null
            moved ?: lib
        }
        return allowed
    }

    fun replaceAll(context: Context, library: Library) = update(context) { library.repaired() }

    fun replaceAll(context: Context, shortcuts: List<Shortcut>) = replaceAll(context, Library(shortcuts = shortcuts))

    /** Adds what is in [incoming]. A shortcut or folder with an id already here replaces it. */
    fun merge(context: Context, incoming: Library) {
        update(context) { lib ->
            // If the incoming list repeats an id, the last copy wins.
            val newShortcuts = incoming.shortcuts.associateBy { it.id }
            val newFolders = incoming.folders.associateBy { it.id }
            Library(
                folders = lib.folders.map { newFolders[it.id] ?: it } + newFolders.values.filter { n -> lib.folders.none { it.id == n.id } },
                shortcuts = lib.shortcuts.map { newShortcuts[it.id] ?: it } + newShortcuts.values.filter { n -> lib.shortcuts.none { it.id == n.id } },
            ).repaired()
        }
    }

    fun merge(context: Context, shortcuts: List<Shortcut>) = merge(context, Library(shortcuts = shortcuts))

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    private fun update(context: Context, change: (Library) -> Library) {
        synchronized(this) {
            val updated = change(library(context))
            write(context.applicationContext, updated)
            cache = updated
        }
        listeners.forEach { it() }
    }

    private fun file(context: Context) = AtomicFile(File(context.filesDir, FILE_NAME))

    private fun load(context: Context): Library {
        val stored = readStored(context)
        // Every install gets the example quiKies, even when Android restored a backup: this marker lives in the
        // no-backup folder, so it is never restored and a reinstall always starts without it.
        val marker = File(context.noBackupFilesDir, EXAMPLES_MARKER)
        if (marker.exists()) return stored
        val withExamples = stored.withExamples(SampleShortcuts.library)
        runCatching {
            write(context, withExamples)
            marker.parentFile?.mkdirs()
            marker.writeText("1")
        }.onFailure { Log.e(TAG, "Could not save the examples", it) }
        // If saving failed they are still shown, and tried again next time.
        return withExamples
    }

    /** What is saved on the phone (restored from a backup or written earlier), or an empty library. */
    private fun readStored(context: Context): Library {
        val file = file(context)
        if (!file.baseFile.exists()) return Library()
        return try {
            ShortcutJson.libraryFromJson(String(file.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            // Keep the unreadable file so nothing is lost, and start empty rather than crash.
            Log.e(TAG, "Could not read $FILE_NAME", e)
            runCatching { file.baseFile.copyTo(File(context.filesDir, "$FILE_NAME.unreadable"), overwrite = true) }
            Library()
        }
    }

    private fun write(context: Context, library: Library) {
        val file = file(context)
        val stream = file.startWrite()
        try {
            stream.write(ShortcutJson.toJson(library).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }
}

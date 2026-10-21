package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library

/**
 * Folder rules for the app screens: the "Move to…" list, where you are, and cleaning a backup.
 *
 * Plain Kotlin with no Android types, so it can be unit tested.
 */
object FolderLogic {

    /** One line in the "Move to…" list. [folderId] is null for the top level ("Home"). */
    data class MoveTarget(
        val folderId: String?,
        val name: String,
        /** 0 for Home, 1 for a folder at the top level, and so on. Used to indent the line. */
        val depth: Int,
        /** True if the tile is already there, so moving it there would do nothing. */
        val isCurrent: Boolean,
        /** The folder's own picture (an emoji), or empty. */
        val icon: String = "",
    )

    /**
     * Home, then every folder as an indented tree (in screen order). Leaves out the tile itself
     * and, for a folder, everything inside it, because a folder cannot go inside itself.
     */
    fun moveTargets(library: Library, nodeId: String, homeName: String): List<MoveTarget> {
        val currentParent = (library.folder(nodeId) ?: library.shortcut(nodeId))?.parentId
        val result = ArrayList<MoveTarget>()
        result += MoveTarget(null, homeName, 0, isCurrent = currentParent == null)

        fun add(parentId: String?, depth: Int, seen: Set<String>) {
            for (node in library.children(parentId)) {
                if (node !is Folder || node.id == nodeId || node.id in seen) continue
                result += MoveTarget(node.id, node.name, depth, isCurrent = currentParent == node.id, icon = node.icon)
                add(node.id, depth + 1, seen + node.id)
            }
        }
        add(null, 1, emptySet())
        return result
    }

    /**
     * Where several chosen tiles ([ids], all from one folder) can be moved: Home and every folder, except the chosen
     * folders and anything inside them.
     */
    fun moveTargetsForMany(library: Library, ids: Set<String>, homeName: String): List<MoveTarget> {
        val currentParent = ids.firstNotNullOfOrNull { library.folder(it) ?: library.shortcut(it) }?.parentId
        val result = ArrayList<MoveTarget>()
        result += MoveTarget(null, homeName, 0, isCurrent = currentParent == null)

        fun add(parentId: String?, depth: Int, seen: Set<String>) {
            for (node in library.children(parentId)) {
                if (node !is Folder || node.id in ids || node.id in seen) continue
                result += MoveTarget(node.id, node.name, depth, isCurrent = currentParent == node.id, icon = node.icon)
                add(node.id, depth + 1, seen + node.id)
            }
        }
        add(null, 1, emptySet())
        return result
    }

    /** How many tiles are directly inside [folderId]. */
    fun itemCount(library: Library, folderId: String): Int = library.children(folderId).size

    /** "Home › Kids › Meds". Empty names show as [noName]. */
    fun pathText(library: Library, folderId: String?, homeName: String, noName: String, separator: String = " › "): String =
        (listOf(homeName) + library.path(folderId).map { it.name.ifBlank { noName } }).joinToString(separator)

    /** "Kids › Meds" for the line under the editor title, or null at the top level. */
    fun folderLine(library: Library, folderId: String?, noName: String, separator: String = " › "): String? {
        val path = library.path(folderId)
        if (path.isEmpty()) return null
        return path.joinToString(separator) { it.name.ifBlank { noName } }
    }

    /**
     * The folder to show: [wanted] if it still exists, otherwise the top level (null).
     */
    fun existingFolder(library: Library, wanted: String?): String? = wanted?.takeIf { library.folder(it) != null }

    /** Drops a second copy of any shortcut or folder id in a backup (the last copy wins). */
    fun withoutRepeats(library: Library): Library = Library(
        folders = library.folders.asReversed().distinctBy { it.id }.asReversed(),
        shortcuts = library.shortcuts.asReversed().distinctBy { it.id }.asReversed(),
    )

    fun isEmpty(library: Library): Boolean = library.folders.isEmpty() && library.shortcuts.isEmpty()
}

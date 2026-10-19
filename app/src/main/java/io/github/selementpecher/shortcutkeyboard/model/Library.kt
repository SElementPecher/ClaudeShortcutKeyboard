package io.github.selementpecher.shortcutkeyboard.model

/** Something that appears as a tile: a [Shortcut] or a [Folder]. */
sealed interface Node {
    val id: String
    val name: String
    val parentId: String?
    val order: Int

    /** A single emoji shown on the tile, or empty (then the tile shows the name's first letter). */
    val icon: String
}

/** A folder. Folders can hold shortcuts and other folders. */
data class Folder(
    override val id: String,
    override val name: String,
    override val parentId: String? = null,
    override val order: Int = 0,
    override val icon: String = "",
) : Node

/**
 * Every folder and shortcut, with the tree rules in one place (plain Kotlin, no Android).
 *
 * A [Library] is never changed: each change returns a new one.
 */
data class Library(
    val folders: List<Folder> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
) {
    fun folder(id: String?): Folder? = if (id == null) null else folders.firstOrNull { it.id == id }

    fun shortcut(id: String): Shortcut? = shortcuts.firstOrNull { it.id == id }

    /** What is inside [parentId] (null = the top level), in the order shown on screen. */
    fun children(parentId: String?): List<Node> =
        (folders.filter { it.parentId == parentId } + shortcuts.filter { it.parentId == parentId })
            .sortedWith(compareBy<Node> { it.order }.thenBy { it.name.lowercase() })

    /** The folders from the top level down to [folderId], e.g. Kids, Meds. Empty for the top level. */
    fun path(folderId: String?): List<Folder> {
        val result = ArrayList<Folder>()
        var current = folder(folderId)
        while (current != null && result.size <= folders.size) {
            result.add(0, current)
            current = folder(current.parentId)
        }
        return result
    }

    /** True if [folderId] is [ancestorId] or sits somewhere inside it. */
    fun isInside(folderId: String?, ancestorId: String): Boolean =
        folderId != null && path(folderId).any { it.id == ancestorId }

    /** Adds or replaces a shortcut. A new one goes at the end of its folder. */
    fun withShortcut(shortcut: Shortcut): Library {
        val index = shortcuts.indexOfFirst { it.id == shortcut.id }
        if (index >= 0) return copy(shortcuts = shortcuts.toMutableList().also { it[index] = shortcut })
        return copy(shortcuts = shortcuts + shortcut.copy(order = nextOrder(shortcut.parentId)))
    }

    /** Adds or replaces a folder. A new one goes at the end of its folder. */
    fun withFolder(folder: Folder): Library {
        val index = folders.indexOfFirst { it.id == folder.id }
        if (index >= 0) return copy(folders = folders.toMutableList().also { it[index] = folder })
        return copy(folders = folders + folder.copy(order = nextOrder(folder.parentId)))
    }

    fun withoutShortcut(id: String): Library = copy(shortcuts = shortcuts.filterNot { it.id == id })

    /** Removes a folder. What was inside moves up into the folder's own parent, so nothing is lost. */
    fun withoutFolder(id: String): Library {
        val removed = folder(id) ?: return this
        val moved = children(id)
        val without = copy(folders = folders.filterNot { it.id == id })
        return moved.fold(without) { lib, node -> lib.moved(node.id, removed.parentId, Int.MAX_VALUE) ?: lib }
    }

    /**
     * Moves a tile into [newParentId] (null = top level) at position [index]
     * (0 = first; a big number = last). Returns null if that is not allowed:
     * a folder cannot go inside itself or inside something it holds.
     */
    fun moved(nodeId: String, newParentId: String?, index: Int): Library? {
        val node: Node = folder(nodeId) ?: shortcut(nodeId) ?: return null
        if (newParentId != null && folder(newParentId) == null) return null
        if (node is Folder && newParentId != null && (newParentId == nodeId || isInside(newParentId, nodeId))) return null

        val oldParent = node.parentId
        val siblings = children(newParentId).filter { it.id != nodeId }
        val position = index.coerceIn(0, siblings.size)
        val newOrder = (siblings.take(position) + node + siblings.drop(position)).map { it.id }

        var result = this
        // Renumber the new parent's children, then the old parent's (a gap there does no harm, but keep it tidy).
        result = result.renumber(newParentId, newOrder, nodeId)
        if (oldParent != newParentId) result = result.renumber(oldParent, result.children(oldParent).map { it.id }, null)
        return result
    }

    private fun renumber(parentId: String?, ids: List<String>, reparent: String?): Library {
        val order = ids.withIndex().associate { (i, id) -> id to i }
        return copy(
            folders = folders.map { f ->
                when {
                    f.id == reparent -> f.copy(parentId = parentId, order = order.getValue(f.id))
                    f.parentId == parentId && f.id in order -> f.copy(order = order.getValue(f.id))
                    else -> f
                }
            },
            shortcuts = shortcuts.map { s ->
                when {
                    s.id == reparent -> s.copy(parentId = parentId, order = order.getValue(s.id))
                    s.parentId == parentId && s.id in order -> s.copy(order = order.getValue(s.id))
                    else -> s
                }
            },
        )
    }

    private fun nextOrder(parentId: String?): Int = (children(parentId).maxOfOrNull { it.order } ?: -1) + 1

    /** Repairs a library read from disk: tiles pointing at a missing folder, or at a loop, go to the top level. */
    fun repaired(): Library {
        val ids = folders.map { it.id }.toSet()
        val fixedFolders = folders.map { if (it.parentId != null && it.parentId !in ids) it.copy(parentId = null) else it }
        val step1 = copy(folders = fixedFolders)
        val withoutLoops = fixedFolders.map { f ->
            val loops = generateSequence(step1.folder(f.parentId)) { step1.folder(it.parentId) }.take(fixedFolders.size + 1).any { it.id == f.id }
            if (loops) f.copy(parentId = null) else f
        }
        val fixedShortcuts = shortcuts.map { if (it.parentId != null && it.parentId !in ids) it.copy(parentId = null) else it }
        return Library(withoutLoops, fixedShortcuts)
    }
}

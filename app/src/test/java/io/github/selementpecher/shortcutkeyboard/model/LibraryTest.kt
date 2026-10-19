package io.github.selementpecher.shortcutkeyboard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kids > Meds > Calpol > (Giddy 5ml, Zippy 10ml), Kids > Other > Thinking step, and Running late at the top. */
class LibraryTest {

    private fun shortcut(id: String, parent: String?, order: Int) =
        Shortcut(id = id, name = id, template = id, parentId = parent, order = order)

    private val library = Library(
        folders = listOf(
            Folder("kids", "Kids", null, 0),
            Folder("meds", "Meds", "kids", 0),
            Folder("calpol", "Calpol", "meds", 0),
            Folder("other", "Other", "kids", 1),
        ),
        shortcuts = listOf(
            shortcut("giddy", "calpol", 0),
            shortcut("zippy", "calpol", 1),
            shortcut("thinking", "other", 0),
            shortcut("late", null, 1),
        ),
    )

    private fun ids(parent: String?, lib: Library = library) = lib.children(parent).map { it.id }

    @Test
    fun `children are in display order and folders and shortcuts mix`() {
        assertEquals(listOf("kids", "late"), ids(null))
        assertEquals(listOf("meds", "other"), ids("kids"))
        assertEquals(listOf("giddy", "zippy"), ids("calpol"))
    }

    @Test
    fun `equal positions fall back to the name`() {
        val lib = Library(shortcuts = listOf(shortcut("b", null, 0), shortcut("a", null, 0)))
        assertEquals(listOf("a", "b"), ids(null, lib))
    }

    @Test
    fun `path runs from the top down`() {
        assertEquals(listOf("kids", "meds", "calpol"), library.path("calpol").map { it.id })
        assertTrue(library.path(null).isEmpty())
        assertTrue(library.path("missing").isEmpty())
    }

    @Test
    fun `isInside includes the folder itself and everything below`() {
        assertTrue(library.isInside("calpol", "kids"))
        assertTrue(library.isInside("kids", "kids"))
        assertTrue(!library.isInside("other", "meds"))
        assertTrue(!library.isInside(null, "kids"))
    }

    @Test
    fun `moving within a folder reorders it`() {
        val moved = library.moved("zippy", "calpol", 0)!!
        assertEquals(listOf("zippy", "giddy"), ids("calpol", moved))
    }

    @Test
    fun `moving into a folder puts it there and tidies the old one`() {
        val moved = library.moved("giddy", "other", 0)!!
        assertEquals(listOf("giddy", "thinking"), ids("other", moved))
        assertEquals(listOf("zippy"), ids("calpol", moved))
        assertEquals(0, moved.shortcut("zippy")!!.order)
    }

    @Test
    fun `a big index means last`() {
        val moved = library.moved("late", "kids", Int.MAX_VALUE)!!
        assertEquals(listOf("meds", "other", "late"), ids("kids", moved))
    }

    @Test
    fun `a folder can move into another folder with what it holds`() {
        val moved = library.moved("meds", "other", 0)!!
        assertEquals(listOf("kids", "meds", "calpol"), moved.path("calpol").map { it.id })
        assertEquals(listOf("meds", "thinking"), ids("other", moved))
    }

    @Test
    fun `a folder cannot go inside itself or inside something it holds`() {
        assertNull(library.moved("kids", "kids", 0))
        assertNull(library.moved("kids", "calpol", 0))
        assertNull(library.moved("meds", "calpol", 0))
    }

    @Test
    fun `moving to a missing folder or moving a missing tile is refused`() {
        assertNull(library.moved("giddy", "nope", 0))
        assertNull(library.moved("nope", null, 0))
    }

    @Test
    fun `moving to the top level works`() {
        val moved = library.moved("giddy", null, 0)!!
        assertEquals(listOf("giddy", "kids", "late"), ids(null, moved).let { it })
    }

    @Test
    fun `a new shortcut goes last in its folder and an edit keeps its place`() {
        val added = library.withShortcut(Shortcut(id = "new", name = "New", template = "x", parentId = "calpol"))
        assertEquals(listOf("giddy", "zippy", "new"), ids("calpol", added))
        val edited = added.withShortcut(added.shortcut("giddy")!!.copy(name = "Renamed"))
        assertEquals(listOf("giddy", "zippy", "new"), ids("calpol", edited))
        assertEquals("Renamed", edited.shortcut("giddy")!!.name)
    }

    @Test
    fun `a new folder goes last`() {
        val added = library.withFolder(Folder("work", "Work"))
        assertEquals(listOf("kids", "late", "work"), ids(null, added))
    }

    @Test
    fun `deleting a folder moves what was inside up one level`() {
        val without = library.withoutFolder("calpol")
        assertNull(without.folder("calpol"))
        assertEquals(listOf("giddy", "zippy"), ids("meds", without))
        assertEquals(setOf("giddy", "zippy", "thinking", "late"), without.shortcuts.map { it.id }.toSet())
    }

    @Test
    fun `deleting a top level folder moves its contents to the top level`() {
        val without = library.withoutFolder("kids")
        assertEquals(setOf("meds", "other", "late"), ids(null, without).toSet())
        assertEquals(listOf("meds", "calpol"), without.path("calpol").map { it.id }.drop(0))
        assertNotNull(without.folder("calpol"))
    }

    @Test
    fun `deleting a shortcut only removes it`() {
        assertEquals(listOf("zippy"), ids("calpol", library.withoutShortcut("giddy")))
    }

    @Test
    fun `repaired leaves a good library alone`() {
        assertEquals(library, library.repaired())
    }
}

package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderLogicTest {

    private val library = Library(
        folders = listOf(
            Folder("kids", "Kids", null, 0),
            Folder("meds", "Meds", "kids", 0),
            Folder("calpol", "Calpol", "meds", 0),
            Folder("home", "House", null, 1),
        ),
        shortcuts = listOf(
            Shortcut("s1", "Giddy 5ml", "x", parentId = "calpol"),
            Shortcut("s2", "Top", "y"),
        ),
    )

    @Test
    fun moveTargetsListEveryFolderAsATree() {
        val targets = FolderLogic.moveTargets(library, "s2", "Home")
        assertEquals(listOf("Home", "Kids", "Meds", "Calpol", "House"), targets.map { it.name })
        assertEquals(listOf(0, 1, 2, 3, 1), targets.map { it.depth })
        assertEquals(listOf(true, false, false, false, false), targets.map { it.isCurrent })
        assertNull(targets.first().folderId)
    }

    @Test
    fun moveTargetsSkipTheFolderItselfAndEverythingInside() {
        val targets = FolderLogic.moveTargets(library, "meds", "Home")
        assertEquals(listOf("Home", "Kids", "House"), targets.map { it.name })
        // "Meds" is in "Kids", so that is where it already is.
        assertTrue(targets.first { it.folderId == "kids" }.isCurrent)
    }

    @Test
    fun moveTargetsMarkTheCurrentFolderOfAShortcut() {
        val targets = FolderLogic.moveTargets(library, "s1", "Home")
        assertEquals(listOf("calpol"), targets.filter { it.isCurrent }.map { it.folderId })
    }

    @Test
    fun moveTargetsSurviveBrokenLoops() {
        val loop = Library(folders = listOf(Folder("a", "A", "b"), Folder("b", "B", "a")))
        // Neither folder is reachable from the top level, so only Home shows; no endless loop.
        assertEquals(listOf("Home"), FolderLogic.moveTargets(loop, "x", "Home").map { it.name })
    }

    @Test
    fun countsAndPaths() {
        assertEquals(1, FolderLogic.itemCount(library, "kids"))
        assertEquals(1, FolderLogic.itemCount(library, "calpol"))
        assertEquals(0, FolderLogic.itemCount(library, "home"))
        assertEquals("Home", FolderLogic.pathText(library, null, "Home", "(No name)"))
        assertEquals("Home › Kids › Meds", FolderLogic.pathText(library, "meds", "Home", "(No name)"))
        assertEquals("Kids › Meds", FolderLogic.folderLine(library, "meds", "(No name)"))
        assertNull(FolderLogic.folderLine(library, null, "(No name)"))
        assertNull(FolderLogic.folderLine(library, "gone", "(No name)"))
    }

    @Test
    fun blankFolderNamesGetAPlaceholder() {
        val blank = Library(folders = listOf(Folder("f", " ", null)))
        assertEquals("Home › (No name)", FolderLogic.pathText(blank, "f", "Home", "(No name)"))
    }

    @Test
    fun existingFolderFallsBackToTheTopLevel() {
        assertEquals("kids", FolderLogic.existingFolder(library, "kids"))
        assertNull(FolderLogic.existingFolder(library, "deleted"))
        assertNull(FolderLogic.existingFolder(library, null))
    }

    @Test
    fun backupRepeatsAreDroppedAndLastCopyWins() {
        val messy = Library(
            folders = listOf(Folder("f", "Old"), Folder("f", "New")),
            shortcuts = listOf(Shortcut("a", "One", "1"), Shortcut("b", "Two", "2"), Shortcut("a", "One again", "1")),
        )
        val clean = FolderLogic.withoutRepeats(messy)
        assertEquals(listOf("New"), clean.folders.map { it.name })
        assertEquals(listOf("Two", "One again"), clean.shortcuts.map { it.name })
        assertFalse(FolderLogic.isEmpty(clean))
        assertTrue(FolderLogic.isEmpty(Library()))
    }
}

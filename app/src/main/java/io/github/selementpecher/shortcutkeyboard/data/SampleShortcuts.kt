package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType

/** Examples added the first time the app runs, so there is something to try straight away. */
object SampleShortcuts {
    private const val KIDS = "sample-folder-kids"
    private const val MEDS = "sample-folder-meds"

    val folders: List<Folder> = listOf(
        Folder(id = KIDS, name = "Kids", order = 0),
        Folder(id = MEDS, name = "Meds", parentId = KIDS, order = 0),
    )

    val all: List<Shortcut> = listOf(
        Shortcut(
            id = "sample-medicine",
            name = "Medicine given",
            template = "{child}, {medicine}, {dose}, {time} next dose not before {time+4h}",
            variables = listOf(
                VariableDef("child", VariableType.CHOICE, options = listOf("Sam")),
                VariableDef("medicine", VariableType.CHOICE, options = listOf("Calpol", "Nurofen")),
                VariableDef("dose", VariableType.CHOICE, options = listOf("2.5ml", "5ml", "7.5ml", "10ml")),
                VariableDef("time", VariableType.TIME),
            ),
            parentId = MEDS,
            order = 0,
        ),
        Shortcut(
            id = "sample-sam-calpol",
            name = "Sam: Calpol 5ml",
            template = "Sam, Calpol, 5ml, {time} next dose not before {time+4h}",
            variables = listOf(VariableDef("time", VariableType.TIME)),
            parentId = MEDS,
            order = 1,
        ),
        Shortcut(
            id = "sample-running-late",
            name = "Running late",
            template = "Running late, sorry! I'll be there by {arrival}.",
            variables = listOf(VariableDef("arrival", VariableType.TIME)),
            order = 1,
        ),
    )

    val library: Library = Library(folders, all)
}

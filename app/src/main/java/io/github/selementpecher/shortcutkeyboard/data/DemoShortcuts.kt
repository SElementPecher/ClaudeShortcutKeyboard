package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType

/**
 * Demo data for showing and testing: one quiKey for each kind of changing part, named after its kind and holding
 * nothing else. Loaded from a hidden place in Settings, so a normal user does not come across it.
 */
object DemoShortcuts {
    private const val FOLDER = "demo-folder"

    private val amounts = listOf("5 mins", "10 mins", "1 hour")

    private fun demo(name: String, template: String, order: Int, icon: String, vararg variables: VariableDef) = Shortcut(
        id = "demo-" + name.lowercase().replace(' ', '-'),
        name = name,
        template = template,
        variables = variables.toList(),
        parentId = FOLDER,
        order = order,
        icon = icon,
    )

    val folders: List<Folder> = listOf(Folder(id = FOLDER, name = "Demo", order = 0, icon = "🧪"))

    val all: List<Shortcut> = listOf(
        demo("List", "{List}", 0, "☰", VariableDef("List", VariableType.CHOICE, options = listOf("Line 1", "Line 2", "Line 3"))),
        demo("Text", "{Text}", 1, "✏️", VariableDef("Text", VariableType.TEXT)),
        demo("Number", "{Number}", 2, "🔢", VariableDef("Number", VariableType.NUMBER)),
        demo("Time", "{Time}", 3, "🕒", VariableDef("Time", VariableType.TIME)),
        demo("Date", "{Date}", 4, "📅", VariableDef("Date", VariableType.DATE)),
        demo("From now", "{From now}", 5, "⏩", VariableDef("From now", VariableType.TIME_AHEAD, options = amounts, lastValue = amounts.first())),
        demo("Ago", "{Ago}", 6, "⏪", VariableDef("Ago", VariableType.TIME_AGO, options = amounts, lastValue = amounts.first())),
        demo("After time", "{Time} then {Time+1h}", 7, "⏭️", VariableDef("Time", VariableType.TIME)),
    )

    val library: Library = Library(folders, all)
}

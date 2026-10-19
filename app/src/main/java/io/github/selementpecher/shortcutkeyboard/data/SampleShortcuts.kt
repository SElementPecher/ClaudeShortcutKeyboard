package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType

/** Examples added the first time the app runs, so there is something to try straight away. */
object SampleShortcuts {
    private const val KIDS = "sample-folder-kids"
    private const val MEDS = "sample-folder-meds"
    private const val LATE = "sample-folder-late"

    private val children = listOf("Sam", "Alex")
    private val minutes = listOf("10 mins", "15 mins", "30 mins", "1 hour")

    val folders: List<Folder> = listOf(
        Folder(id = KIDS, name = "Kids", order = 0, icon = "🧒"),
        Folder(id = MEDS, name = "Meds", parentId = KIDS, order = 0, icon = "💊"),
        Folder(id = LATE, name = "Running late", order = 1, icon = "⏰"),
    )

    val all: List<Shortcut> = listOf(
        // ---- Kids > Meds ----
        Shortcut(
            id = "sample-medicine",
            name = "Medicine given",
            template = "{child}, {medicine}, {dose}, {time} next dose not before {time+4h}",
            variables = listOf(
                VariableDef("child", VariableType.CHOICE, options = children),
                VariableDef("medicine", VariableType.CHOICE, options = listOf("Calpol", "Nurofen")),
                VariableDef("dose", VariableType.CHOICE, options = listOf("2.5ml", "5ml", "7.5ml", "10ml")),
                VariableDef("time", VariableType.TIME),
            ),
            parentId = MEDS,
            order = 0,
            icon = "💊",
        ),
        // ---- Kids ----
        Shortcut(
            id = "sample-temperature",
            name = "Temperature",
            template = "{child}'s temperature at {time} was {temperature}.",
            variables = listOf(
                VariableDef("child", VariableType.CHOICE, options = children),
                VariableDef("time", VariableType.TIME),
                VariableDef("temperature", VariableType.NUMBER),
            ),
            parentId = KIDS,
            order = 1,
            icon = "🤒",
        ),
        Shortcut(
            id = "sample-off-sick",
            name = "Off sick",
            template = "{child} is off sick today and won't be in. Back on {date}.",
            variables = listOf(
                VariableDef("child", VariableType.CHOICE, options = children),
                VariableDef("date", VariableType.DATE),
            ),
            parentId = KIDS,
            order = 2,
            icon = "🏫",
        ),
        Shortcut(
            id = "sample-pickup",
            name = "Pick-up",
            template = "I'll pick up {child} at {time}.",
            variables = listOf(
                VariableDef("child", VariableType.CHOICE, options = children),
                VariableDef("time", VariableType.TIME),
            ),
            parentId = KIDS,
            order = 3,
            icon = "🚗",
        ),
        // ---- Running late ----
        Shortcut(
            id = "sample-running-late",
            name = "Running late",
            template = "Running late, sorry! I'll be there in {How long}.",
            variables = listOf(
                VariableDef("How long", VariableType.TIME_AHEAD, options = minutes, lastValue = minutes.first(), show = RelativeShow.BOTH),
            ),
            parentId = LATE,
            order = 0,
            icon = "🏃",
        ),
        Shortcut(
            id = "sample-on-my-way",
            name = "On my way",
            template = "On my way, I'll be there in {How long}.",
            variables = listOf(
                VariableDef("How long", VariableType.TIME_AHEAD, options = listOf("10 mins"), fixed = true),
            ),
            parentId = LATE,
            order = 1,
            icon = "🚶",
        ),
        Shortcut(
            id = "sample-meet-you-there",
            name = "Meet you there",
            template = "I'll meet you at {Place} at {When}.",
            variables = listOf(
                VariableDef("Place", VariableType.CHOICE, options = listOf("the station", "the school gate", "the café")),
                VariableDef("When", VariableType.TIME),
            ),
            parentId = LATE,
            order = 2,
            icon = "📍",
        ),
        Shortcut(
            id = "sample-train-delay",
            name = "Train or bus delay",
            template = "My {Which} is delayed by {How long}. Now due at {When}.",
            variables = listOf(
                VariableDef("Which", VariableType.CHOICE, options = listOf("train", "bus")),
                VariableDef("How long", VariableType.TIME_AHEAD, options = listOf("10 mins", "20 mins", "30 mins"), lastValue = "10 mins"),
                VariableDef("When", VariableType.TIME),
            ),
            parentId = LATE,
            order = 3,
            icon = "🚆",
        ),
        Shortcut(
            id = "sample-be-there-by",
            name = "Be there by",
            template = "I'll be there by {When}.",
            variables = listOf(VariableDef("When", VariableType.TIME)),
            parentId = LATE,
            order = 4,
            icon = "🕒",
        ),
        Shortcut(
            id = "sample-call-back",
            name = "Call back",
            template = "Can't talk now, I'll call you back at {When}.",
            variables = listOf(VariableDef("When", VariableType.TIME)),
            parentId = LATE,
            order = 5,
            icon = "📞",
        ),
        // ---- Top level ----
        Shortcut(
            id = "sample-sick-day",
            name = "Sick day",
            template = "I'm off sick today, back on {date}.",
            variables = listOf(VariableDef("date", VariableType.DATE)),
            order = 2,
            icon = "🤒",
        ),
    )

    val library: Library = Library(folders, all)
}

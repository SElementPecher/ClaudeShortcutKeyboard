package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType

/** Examples added the first time the app runs, so there is something to try straight away. */
object SampleShortcuts {
    val all: List<Shortcut> = listOf(
        Shortcut(
            id = "sample-medicine",
            name = "Medicine given",
            trigger = "/meds",
            template = "{child}, {medicine}, {dose}, {time} next dose not before {time+4h}",
            variables = listOf(
                VariableDef("child", VariableType.CHOICE, options = listOf("Sam")),
                VariableDef("medicine", VariableType.CHOICE, options = listOf("Calpol", "Nurofen")),
                VariableDef("dose", VariableType.CHOICE, options = listOf("2.5ml", "5ml", "7.5ml", "10ml")),
                VariableDef("time", VariableType.TIME),
            ),
        ),
        Shortcut(
            id = "sample-running-late",
            name = "Running late",
            trigger = "/late",
            template = "Running late, sorry! I'll be there by {arrival}.",
            variables = listOf(VariableDef("arrival", VariableType.TIME)),
        ),
    )
}

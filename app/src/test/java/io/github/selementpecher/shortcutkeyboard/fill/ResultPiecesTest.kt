package io.github.selementpecher.shortcutkeyboard.fill

import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultPiecesTest {
    private val clock = LocalDateTime.of(2026, 10, 16, 20, 20)

    private fun session(template: String, vararg vars: VariableDef): FillSession =
        FillSession(Shortcut(id = "s", name = "S", template = template, variables = vars.toList()), { clock }, Locale.UK)

    private fun pieces(s: FillSession) = ResultPieces.of(s, clock, Locale.UK)

    @Test
    fun splitsTheMedicineExampleIntoFixedAndFilled() {
        val s = session(
            "{child}, {medicine}, {dose}, {time} next dose not before {time+4h}",
            VariableDef("child", VariableType.CHOICE, listOf("Sam")),
            VariableDef("medicine", VariableType.CHOICE, listOf("Calpol")),
            VariableDef("dose", VariableType.CHOICE, listOf("5ml")),
            VariableDef("time", VariableType.TIME),
        )
        s.setValue(s.variables[0], "Sam")
        s.setValue(s.variables[1], "Calpol")
        s.setValue(s.variables[2], "5ml")
        val result = pieces(s)
        assertEquals(s.result(), result.joinToString("") { it.text })
        assertEquals(
            listOf(
                ResultPiece("Sam", 0), ResultPiece(", "), ResultPiece("Calpol", 1), ResultPiece(", "),
                ResultPiece("5ml", 2), ResultPiece(", "), ResultPiece("20:20", 3),
                ResultPiece(" next dose not before "), ResultPiece("00:20", 3),
            ),
            result,
        )
    }

    @Test
    fun aMessageWithNoBlanksIsOneFixedPiece() {
        val result = pieces(session("Running late, sorry!"))
        assertEquals(listOf(ResultPiece("Running late, sorry!")), result)
        assertFalse(result.single().isFilled)
    }

    @Test
    fun blanksWithNoValueAreLeftOut() {
        val s = session("Hi {name}!", VariableDef("name", VariableType.CHOICE, listOf("Sam")))
        val result = pieces(s)
        assertEquals("Hi !", result.joinToString("") { it.text })
        assertTrue(result.none { it.isFilled })
        // Both fixed halves are kept apart from nothing: they merge into one piece.
        assertEquals(1, result.size)
    }

    @Test
    fun builtInsCountAsChangingParts() {
        val result = pieces(session("Today is {today}"))
        assertEquals(2, result.size)
        assertEquals(ResultPiece("Today is "), result[0])
        assertEquals(0, result[1].blank)
        assertTrue(result[1].text.contains("2026"))
    }

    @Test
    fun theSameBlankKeepsItsNumber() {
        val s = session("{who} and {who}", VariableDef("who", VariableType.CHOICE, listOf("Sam")))
        s.setValue(s.variables[0], "Sam")
        assertEquals(listOf(0, -1, 0), pieces(s).map { it.blank })
    }

    @Test
    fun literalBracesStayFixedText() {
        val s = session("Use {{this}} for {x}", VariableDef("x", VariableType.CHOICE, listOf("a")))
        s.setValue(s.variables[0], "a")
        val result = pieces(s)
        assertEquals("Use {this} for a", result.joinToString("") { it.text })
        assertEquals(ResultPiece("a", 0), result.last())
    }

    @Test
    fun fixedAndAskedHowLongBlanksPrintLikeTheMessage() {
        val s = session(
            "Back in {How long}, taken {Ago} ago",
            VariableDef("How long", VariableType.TIME_AHEAD, listOf("10 mins"), show = RelativeShow.BOTH, fixed = true),
            VariableDef("Ago", VariableType.TIME_AGO, listOf("5 mins", "15 mins"), show = RelativeShow.CLOCK),
        )
        // The fixed blank is not a step, but it still prints and is highlighted.
        assertEquals(1, s.variables.size)
        s.setValue(s.variables[0], "15 mins")
        val result = pieces(s)
        assertEquals(s.result(), result.joinToString("") { it.text })
        assertEquals(
            listOf(
                ResultPiece("Back in "), ResultPiece("10 mins (20:30)", 0), ResultPiece(", taken "),
                ResultPiece("20:05", 1), ResultPiece(" ago"),
            ),
            result,
        )
    }

    @Test
    fun anUnansweredHowLongBlankIsLeftOut() {
        val s = session("In {n}!", VariableDef("n", VariableType.TIME_AHEAD, listOf("5 mins", "10 mins")))
        assertEquals(listOf(ResultPiece("In !")), pieces(s))
    }
}

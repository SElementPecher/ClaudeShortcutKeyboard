package io.github.selementpecher.shortcutkeyboard.ui.editor

import io.github.selementpecher.shortcutkeyboard.data.SampleShortcuts
import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class MessageDraftTest {

    private val now = LocalDateTime.of(2026, 10, 16, 20, 20)
    private val message = "Sam, Calpol, 5ml, 20:20 next dose not before 00:20"

    /** The selection of the first word equal to [word] in the plain text of [draft]. */
    private fun word(draft: MessageDraft, word: String, nth: Int = 0): Selection =
        draft.pieces().filterIsInstance<Piece.Word>().filter { it.text == word }[nth].selection

    private fun tapped(draft: MessageDraft, text: String, kind: BlankKind, question: String, more: List<String> = emptyList()) =
        draft.makeBlank(word(draft, text), kind, question, more)

    /** The whole example built the way the owner would. */
    private fun medicine(): MessageDraft {
        var d = MessageDraft.fromMessage(message)
        d = tapped(d, "Sam", BlankKind.LIST, "Who", listOf("Ben"))
        d = tapped(d, "Calpol", BlankKind.LIST, "What", listOf("Nurofen"))
        d = tapped(d, "5ml", BlankKind.LIST, "How much")
        d = tapped(d, "20:20", BlankKind.TIME, "When")
        val sel = word(d, "00:20")
        val relation = d.timeRelations(sel).single()
        return d.reuseTimeLater(sel, relation.blankId, relation.afterMinutes)
    }

    @Test
    fun theMessageIsCutIntoWordsAndGaps() {
        val pieces = MessageDraft.fromMessage("Hi Sam, ok").pieces()
        assertEquals(listOf("Hi", " ", "Sam", ", ", "ok"), pieces.map { (it as? Piece.Word)?.text ?: (it as Piece.Gap).text })
    }

    @Test
    fun anEmptyMessageHasNothingToTap() {
        val draft = MessageDraft.fromMessage("")
        assertTrue(draft.pieces().isEmpty())
        assertTrue(draft.hasNoBlanks)
    }

    @Test
    fun buildsTheTemplateTheKeyboardReads() {
        val shortcut = medicine().toShortcut("id", " Medicine given ", parentId = "meds", order = 3, icon = "💊")
        assertEquals("{Who}, {What}, {How much}, {When} next dose not before {When+4h}", shortcut.template)
        assertEquals("Medicine given", shortcut.name)
        assertEquals("meds", shortcut.parentId)
        assertEquals(3, shortcut.order)
        assertEquals("💊", shortcut.icon)
        assertEquals(listOf("Who", "What", "How much", "When"), shortcut.variables.map { it.name })
        assertEquals(listOf(VariableType.CHOICE, VariableType.CHOICE, VariableType.CHOICE, VariableType.TIME), shortcut.variables.map { it.type })
        assertEquals(listOf("Sam", "Ben"), shortcut.variables[0].options)
        assertEquals("Sam", shortcut.variables[0].lastValue)
        assertEquals(listOf("5ml"), shortcut.variables[2].options)
    }

    @Test
    fun theKeyboardCanFillTheShortcutWeBuilt() {
        val shortcut = medicine().toShortcut("id", "Medicine given")
        val session = FillSession(shortcut, now = { now }, locale = Locale.UK)
        assertEquals(listOf("Who", "What", "How much", "When"), session.variables.map { it.name })
        assertEquals("Sam, Calpol, 5ml, 20:20 next dose not before 00:20", session.result().replace("Sam, , ,", "Sam, Calpol, 5ml,"))
        session.setValue(session.variables[0], "Ben")
        session.setValue(session.variables[1], "Nurofen")
        session.setValue(session.variables[2], "10ml")
        assertEquals("Ben, Nurofen, 10ml, 20:20 next dose not before 00:20", session.result())
    }

    @Test
    fun theExampleMessageReadsLikeTheOriginal() {
        assertEquals(message, medicine().exampleMessage())
        assertEquals(message, medicine().plainText())
    }

    @Test
    fun timeLaterIsWorkedOutAutomatically() {
        var d = MessageDraft.fromMessage(message)
        d = tapped(d, "20:20", BlankKind.TIME, "When")
        val relation = d.timeRelations(word(d, "00:20")).single()
        assertEquals("When", relation.question)
        assertEquals(240L, relation.afterMinutes)
        assertEquals(-20L * 60, relation.beforeMinutes)
    }

    @Test
    fun timeRelationsOnlyForTimesWithATimeAlreadyInTheMessage() {
        val plain = MessageDraft.fromMessage(message)
        assertTrue(plain.timeRelations(word(plain, "00:20")).isEmpty())
        val withTime = tapped(plain, "20:20", BlankKind.TIME, "When")
        assertTrue(withTime.timeRelations(word(withTime, "Calpol")).isEmpty())
        assertTrue(withTime.timeRelations(word(withTime, "5ml")).isEmpty())
    }

    @Test
    fun timeRelationOfTheSameTimeIsZero() {
        var d = MessageDraft.fromMessage("at 08:00 and again 08:00")
        d = d.makeBlank(word(d, "08:00", 0), BlankKind.TIME, "When")
        val relation = d.timeRelations(word(d, "08:00", 0)).single()
        assertEquals(0L, relation.afterMinutes)
        assertEquals(0L, relation.beforeMinutes)
    }

    @Test
    fun timeBeforeUsesANegativeOffset() {
        var d = MessageDraft.fromMessage("Dinner 18:00, leave 17:30")
        d = tapped(d, "18:00", BlankKind.TIME, "Dinner time")
        val sel = word(d, "17:30")
        val relation = d.timeRelations(sel).single()
        assertEquals(-30L, relation.beforeMinutes)
        d = d.reuseTimeLater(sel, relation.blankId, relation.beforeMinutes)
        assertEquals("Dinner {Dinner time}, leave {Dinner time-30m}", d.toShortcut("i", "n").template)
    }

    @Test
    fun reuseTimeLaterNeedsATimeBlank() {
        val d = tapped(MessageDraft.fromMessage(message), "Sam", BlankKind.LIST, "Who")
        val id = d.blanks.single().id
        assertEquals(d, d.reuseTimeLater(word(d, "00:20"), id, 60))
    }

    @Test
    fun aNumberKeepsItsUnitAsPlainWords() {
        var d = MessageDraft.fromMessage("Give 5ml now")
        d = tapped(d, "5ml", BlankKind.NUMBER, "How many")
        val shortcut = d.toShortcut("i", "n")
        assertEquals("Give {How many}ml now", shortcut.template)
        assertEquals("5", shortcut.variables.single().lastValue)
        assertEquals(VariableType.NUMBER, shortcut.variables.single().type)
        assertEquals("Give 5ml now", d.plainText())
    }

    @Test
    fun aNumberWithMoneyKeepsTheSign() {
        var d = MessageDraft.fromMessage("Pay £12.50 today")
        d = tapped(d, "£12.50", BlankKind.NUMBER, "How much")
        assertEquals("Pay £{How much} today", d.toShortcut("i", "n").template)
    }

    @Test
    fun aDateBlankIsStoredAsADate() {
        var d = MessageDraft.fromMessage("See you on Monday")
        d = tapped(d, "Monday", BlankKind.DATE, "Date")
        val shortcut = d.toShortcut("i", "n")
        assertEquals("See you on {Date}", shortcut.template)
        assertEquals(VariableType.DATE, shortcut.variables.single().type)
    }

    @Test
    fun severalWordsCanBecomeOneAnswer() {
        var d = MessageDraft.fromMessage("Hello Mrs Jones, see you")
        var sel = word(d, "Jones")
        sel = d.extend(sel, before = true)!!
        assertEquals("Mrs Jones", d.textOf(sel))
        d = d.makeBlank(sel, BlankKind.LIST, "Who")
        assertEquals("Hello {Who}, see you", d.toShortcut("i", "n").template)
        assertEquals(listOf("Mrs Jones"), d.toShortcut("i", "n").variables.single().options)
    }

    @Test
    fun extendingStopsAtTheEndsAndAtOtherBlanks() {
        var d = MessageDraft.fromMessage("one two three")
        val two = word(d, "two")
        assertEquals("one two", d.textOf(d.extend(two, before = true)!!))
        assertEquals("two three", d.textOf(d.extend(two, before = false)!!))
        assertNull(d.extend(word(d, "one"), before = true))
        assertNull(d.extend(word(d, "three"), before = false))
        d = tapped(d, "one", BlankKind.LIST, "Who")
        // "two three" is now a separate piece of text: there is no word before it to add.
        val two2 = word(d, "two")
        assertNull(d.extend(two2, before = true))
    }

    @Test
    fun shrinkingTakesWordsBackOff() {
        val d = MessageDraft.fromMessage("one two three")
        val all = Selection(0, 0, 13)
        assertEquals("two three", d.textOf(d.shrink(all, fromStart = true)!!))
        assertEquals("one two", d.textOf(d.shrink(all, fromStart = false)!!))
        assertNull(d.shrink(word(d, "two"), fromStart = true))
    }

    @Test
    fun aWordRepeatedStaysOneBlank() {
        val shortcut = Shortcut(
            "id", "x", "Hi {child}, bye {child}!",
            listOf(VariableDef("child", options = listOf("Sam"), lastValue = "Sam")),
        )
        val d = MessageDraft.fromShortcut(shortcut)
        assertEquals(1, d.blanks.size)
        assertEquals(2, d.pieces().count { it is Piece.Chip })
        assertEquals("Hi Sam, bye Sam!", d.plainText())
        assertEquals(1, d.questions().size)
    }

    @Test
    fun theSampleShortcutsRoundTripExactly() {
        for (sample in SampleShortcuts.all) {
            val back = MessageDraft.fromShortcut(sample, now, Locale.UK)
                .toShortcut(sample.id, sample.name, sample.parentId, sample.order, sample.icon)
            assertEquals(sample.template, back.template)
            assertEquals(sample.variables.map { it.name }, back.variables.map { it.name })
            assertEquals(sample.variables.map { it.type }, back.variables.map { it.type })
            assertEquals(sample.variables.map { it.options }, back.variables.map { it.options })
            assertEquals(sample.parentId, back.parentId)
            assertEquals(sample.icon, back.icon)
        }
    }

    @Test
    fun theMedicineSampleShowsAsAnExampleMessage() {
        val sample = SampleShortcuts.all.first { it.id == "sample-medicine" }
        val d = MessageDraft.fromShortcut(sample, now, Locale.UK)
        assertEquals("Sam, Calpol, 2.5ml, 20:20 next dose not before 00:20".replace("Calpol, 2.5ml", "Calpol, 2.5ml"), d.exampleMessage())
        assertEquals(listOf("child", "medicine", "dose", "time"), d.questions().map { it.question })
        assertEquals(listOf(1, 2, 4, 0), d.questions().map { it.answerCount })
        assertEquals(listOf(BlankKind.LIST, BlankKind.LIST, BlankKind.LIST, BlankKind.TIME), d.questions().map { it.kind })
    }

    @Test
    fun anOldTextBlankLoadsAsAListButSavesAsText() {
        val shortcut = Shortcut("id", "x", "Note: {note}", listOf(VariableDef("note", VariableType.TEXT, lastValue = "hi")))
        val d = MessageDraft.fromShortcut(shortcut)
        assertEquals(BlankKind.LIST, d.blanks.single().kind)
        assertEquals(VariableType.TEXT, d.toShortcut("id", "x").variables.single().type)
    }

    @Test
    fun builtInsStayAsTheyAre() {
        val shortcut = Shortcut("id", "x", "On {today} at {now|h:mm a} for {who}", listOf(VariableDef("who", options = listOf("Sam"))))
        val d = MessageDraft.fromShortcut(shortcut, now, Locale.UK)
        val back = d.toShortcut("id", "x")
        assertEquals("On {today} at {now|h:mm a} for {who}", back.template)
        assertEquals(listOf("who"), back.variables.map { it.name })
        assertEquals(1, d.questions().size)
        assertTrue(d.plainText().contains("8:20 pm"))
    }

    @Test
    fun formatsAndNegativeOffsetsAreKept() {
        val shortcut = Shortcut(
            "id", "x", "At {t|h:mm a}, not before {t+4h|h:mm a}, early {t-15m}",
            listOf(VariableDef("t", VariableType.TIME, lastValue = "20:20")),
        )
        val d = MessageDraft.fromShortcut(shortcut, now, Locale.UK)
        assertEquals("At {t|h:mm a}, not before {t+4h|h:mm a}, early {t-15m}", d.toShortcut("id", "x").template)
        assertEquals("At 8:20 pm, not before 12:20 am, early 20:05", d.plainText())
    }

    @Test
    fun aTimeBlankWithoutAnExampleUsesTheTimeNow() {
        val sample = SampleShortcuts.all.first { it.id == "sample-sam-calpol" }
        assertEquals("Sam, Calpol, 5ml, 20:20 next dose not before 00:20", MessageDraft.fromShortcut(sample, now, Locale.UK).plainText())
    }

    @Test
    fun theTimeExampleIsSavedSoTheWordsCanBeFoundAgain() {
        val saved = medicine().toShortcut("id", "n")
        assertEquals("20:20", saved.variables.last().lastValue)
        val reloaded = MessageDraft.fromShortcut(saved, now, Locale.UK)
        assertEquals(message, reloaded.plainText())
    }

    @Test
    fun literalBracketsInTheMessageAreEscaped() {
        var d = MessageDraft.fromMessage("Use {curly} and a } too, ask Sam")
        d = tapped(d, "Sam", BlankKind.LIST, "Who")
        val shortcut = d.toShortcut("id", "n")
        assertEquals("Use {{curly}} and a }} too, ask {Who}", shortcut.template)
        val parsed = Template.parse(shortcut.template)
        assertEquals(listOf("Who"), parsed.variableNames)
        assertEquals("Use {curly} and a } too, ask Sam", parsed.render(mapOf("who" to "Sam")))
        // and it reads back the same way
        assertEquals("Use {curly} and a } too, ask Sam", MessageDraft.fromShortcut(shortcut).plainText())
    }

    @Test
    fun bracketsRightNextToABlankStillParse() {
        var d = MessageDraft.fromMessage("{Sam}")
        d = d.makeBlank(Selection(0, 1, 4), BlankKind.LIST, "Who")
        val template = d.toShortcut("id", "n").template
        assertEquals("{{{Who}}}", template)
        assertEquals("{Sam}", Template.parse(template).render(mapOf("who" to "Sam")))
    }

    @Test
    fun questionsAreCleanedAndUnique() {
        var d = MessageDraft.fromMessage("Sam and Ben, at 9:00")
        d = tapped(d, "Sam", BlankKind.LIST, "who")
        d = tapped(d, "Ben", BlankKind.LIST, "WHO")
        d = tapped(d, "9:00", BlankKind.TIME, "now")
        assertEquals(listOf("who", "WHO 2", "now 2"), d.blanks.map { it.question })
    }

    @Test
    fun renamingKeepsTheNamesUnique() {
        var d = MessageDraft.fromMessage("Sam and Ben")
        d = tapped(d, "Sam", BlankKind.LIST, "Who")
        d = tapped(d, "Ben", BlankKind.LIST, "Other")
        d = d.renameQuestion(d.blanks[1].id, "who")
        assertEquals("Who 2".lowercase(), d.blanks[1].question.lowercase())
        d = d.renameQuestion(d.blanks[0].id, "Who")
        assertEquals("Who", d.blanks[0].question)
        d = d.renameQuestion(d.blanks[0].id, "{}")
        assertEquals("Who", d.blanks[0].question)
        assertEquals("{Who} and {who 2}".lowercase(), d.toShortcut("i", "n").template.lowercase())
    }

    @Test
    fun answersAreCleanedAndOnlyListsHaveAnswers() {
        var d = MessageDraft.fromMessage("Sam at 9:00")
        d = tapped(d, "Sam", BlankKind.LIST, "Who")
        d = tapped(d, "9:00", BlankKind.TIME, "When")
        d = d.setAnswers(d.blanks[0].id, listOf(" Sam ", "", "sam", "Ben"))
        assertEquals(listOf("Sam", "Ben"), d.blanks[0].answers)
        assertEquals(d, d.setAnswers(d.blanks[1].id, listOf("x")))
        assertEquals(listOf(2, 0), d.questions().map { it.answerCount })
    }

    @Test
    fun rememberNewAnswersCanBeSwitchedOff() {
        var d = tapped(MessageDraft.fromMessage("Sam"), "Sam", BlankKind.LIST, "Who")
        d = d.setRemember(d.blanks.single().id, false)
        assertFalse(d.toShortcut("i", "n").variables.single().remember)
    }

    @Test
    fun makingAWordPlainAgainPutsTheExampleBack() {
        val d = medicine()
        val who = d.blanks.first { it.question == "Who" }
        val plain = d.unmakeBlank(who.id)
        assertEquals(message, plain.plainText())
        assertEquals(3, plain.blanks.size)
        assertEquals("Sam, {What}, {How much}, {When} next dose not before {When+4h}", plain.toShortcut("i", "n").template)
    }

    @Test
    fun makingATimeBlankPlainAgainTurnsEveryUseBackIntoWords() {
        val d = medicine()
        val time = d.blanks.first { it.question == "When" }
        val plain = d.unmakeBlank(time.id)
        assertEquals(message, plain.plainText())
        assertEquals("{Who}, {What}, {How much}, 20:20 next dose not before 00:20", plain.toShortcut("i", "n").template)
    }

    @Test
    fun makingOneTimeLaterPlainKeepsTheBlank() {
        val d = medicine()
        val chip = d.pieces().filterIsInstance<Piece.Chip>().last()
        assertEquals(240L, chip.offsetMinutes)
        val plain = d.unmakeRef(chip.segment)
        assertEquals(4, plain.blanks.size)
        assertEquals("{Who}, {What}, {How much}, {When} next dose not before 00:20", plain.toShortcut("i", "n").template)
        // the same word can then be tapped again
        assertEquals(240L, plain.timeRelations(word(plain, "00:20")).single().afterMinutes)
    }

    @Test
    fun unmakingTheOnlyUseOfABlankRemovesIt() {
        val d = tapped(MessageDraft.fromMessage("Sam"), "Sam", BlankKind.LIST, "Who")
        val plain = d.unmakeRef(0)
        assertTrue(plain.blanks.isEmpty())
        assertEquals("Sam", plain.plainText())
        assertEquals(listOf(Segment.Text("Sam")), plain.segments)
    }

    @Test
    fun retypingKeepsTheBlanksItCanFind() {
        val d = medicine()
        val result = d.withNewText("Hi! Sam, Calpol, 5ml, 20:20 next dose not before 00:20. Thanks")
        assertEquals(0, result.lost)
        val template = result.draft.toShortcut("i", "n").template
        assertEquals("Hi! {Who}, {What}, {How much}, {When} next dose not before {When+4h}. Thanks", template)
        assertEquals(d.blanks, result.draft.blanks)
    }

    @Test
    fun retypingWithoutChangesChangesNothing() {
        val d = medicine()
        val result = d.withNewText(d.plainText())
        assertEquals(0, result.lost)
        assertEquals(d, result.draft)
    }

    @Test
    fun wordsThatAreGoneNeedTappingAgain() {
        val d = medicine()
        val result = d.withNewText("Ben had Calpol at 20:20")
        // 5ml and 00:20 are gone. "Ben" is one of Who's answers, so it is found in Sam's place.
        assertEquals(2, result.lost)
        assertEquals(listOf("Who", "What", "When"), result.draft.blanks.map { it.question })
        assertEquals("{Who} had {What} at {When}", result.draft.toShortcut("i", "n").template)
    }

    @Test
    fun retypingMatchesWholeWordsInOrderOnly() {
        var d = MessageDraft.fromMessage("Sam met Sam")
        d = d.makeBlank(word(d, "Sam", 1), BlankKind.LIST, "Who")
        // Samuel does not match; the first whole "Sam" does
        val result = d.withNewText("Samuel and Sam, then Sam")
        assertEquals(0, result.lost)
        assertEquals("Samuel and {Who}, then Sam", result.draft.toShortcut("i", "n").template)
    }

    @Test
    fun retypingFindsAnyOfTheAnswersToo() {
        var d = tapped(MessageDraft.fromMessage("Hi Sam"), "Sam", BlankKind.LIST, "Who", listOf("Ben"))
        val result = d.withNewText("Hello Ben")
        assertEquals(0, result.lost)
        assertEquals("Hello {Who}", result.draft.toShortcut("i", "n").template)
    }

    @Test
    fun retypingAnEmptyMessageLosesEverything() {
        val result = medicine().withNewText("")
        assertEquals(5, result.lost)
        assertTrue(result.draft.blanks.isEmpty())
        assertTrue(result.draft.segments.isEmpty())
    }

    @Test
    fun savesAndRestoresThroughJson() {
        val d = medicine()
        val back = MessageDraft.fromJson(d.toJson())
        assertNotNull(back)
        assertEquals(d, back)
        val withFixed = MessageDraft.fromShortcut(Shortcut("i", "n", "Today {today|d MMM} {x}", listOf(VariableDef("x", options = listOf("a")))), now, Locale.UK)
        assertEquals(withFixed, MessageDraft.fromJson(withFixed.toJson()))
    }

    @Test
    fun badJsonGivesNothing() {
        assertNull(MessageDraft.fromJson("not json"))
        assertNull(MessageDraft.fromJson("{}"))
    }

    @Test
    fun theSavedShortcutDoesNotChangeWhenOpenedAndSavedAgain() {
        val saved = medicine().toShortcut("id", "Medicine given", "meds", 2, "💊")
        val again = MessageDraft.fromShortcut(saved, now, Locale.UK).toShortcut(saved.id, saved.name, saved.parentId, saved.order, saved.icon)
        assertEquals(saved, again)
    }

    @Test
    fun aMessageWithNoMarkedWordsIsJustText() {
        val shortcut = MessageDraft.fromMessage("On my way!").toShortcut("i", "n")
        assertEquals("On my way!", shortcut.template)
        assertTrue(shortcut.variables.isEmpty())
    }

    @Test
    fun ignoresSelectionsThatAreNotText() {
        val d = medicine()
        val chip = d.pieces().filterIsInstance<Piece.Chip>().first()
        assertEquals(d, d.makeBlank(Selection(chip.segment, 0, 1), BlankKind.LIST, "x"))
        assertEquals(d, d.makeBlank(Selection(99, 0, 1), BlankKind.LIST, "x"))
        assertEquals(d, d.makeBlank(Selection(0, 2, 2), BlankKind.LIST, "x"))
        assertEquals("", d.textOf(Selection(99, 0, 1)))
    }
}

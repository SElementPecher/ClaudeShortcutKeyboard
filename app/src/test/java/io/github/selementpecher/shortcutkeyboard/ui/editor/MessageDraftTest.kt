package io.github.selementpecher.shortcutkeyboard.ui.editor

import io.github.selementpecher.shortcutkeyboard.data.SampleShortcuts
import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.RelativeAmount
import io.github.selementpecher.shortcutkeyboard.template.RelativeUnit
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
    private val uk = Locale.UK
    private val message = "Sam, Calpol, 5ml, 20:20 next dose not before 00:20"

    private fun list(question: String, vararg answers: String) =
        BlankSpec(0, question, BlankKind.LIST, answers.toList(), example = answers.first(), lastValue = answers.first())

    /** Where [text] sits in the text box (a chip counts as one character). */
    private fun at(draft: MessageDraft, text: String, nth: Int = 0): IntRange {
        var from = -1
        repeat(nth + 1) { from = draft.placeholderText().indexOf(text, from + 1) }
        check(from >= 0) { "no $text in ${draft.placeholderText()}" }
        return from until from + text.length
    }

    private fun swap(d: MessageDraft, text: String, spec: BlankSpec): MessageDraft {
        val r = at(d, text)
        return d.replaceRange(r.first, r.last + 1, spec, now, uk)
    }

    /** The whole example built the way the owner would: type, select, add. */
    private fun medicine(): MessageDraft {
        var d = MessageDraft.fromMessage(message)
        d = swap(d, "Sam", list("Who", "Sam", "Ben"))
        d = swap(d, "Calpol", list("What", "Calpol", "Nurofen"))
        d = swap(d, "5ml", list("How much", "5ml"))
        d = swap(d, "20:20", BlankSpec(0, "When", BlankKind.TIME, example = "20:20"))
        val whenId = d.blanks.first { it.kind == BlankKind.TIME }.id
        val r = at(d, "00:20")
        return d.deleteRange(r.first, r.last + 1).insertReference(r.first, whenId, 240, now, uk)
    }

    // ---- positions and text ----

    @Test
    fun aNewDraftIsPlainWords() {
        val d = MessageDraft.fromMessage("Hi there")
        assertEquals("Hi there", d.placeholderText())
        assertEquals(8, d.length)
        assertTrue(d.hasNoBlanks)
        assertTrue(d.chips().isEmpty())
        assertTrue(MessageDraft.fromMessage("").segments.isEmpty())
    }

    @Test
    fun aChipIsOnePlaceholderCharacter() {
        val d = MessageDraft.fromMessage("Hi , ok").insertBlank(3, list("Who", "Sam"), now, uk)
        assertEquals("Hi ${MessageDraft.PLACEHOLDER}, ok", d.placeholderText())
        assertEquals("Hi Sam, ok", d.plainText())
        assertEquals(7, d.length)
        assertEquals(3, d.chipOffset(0))
        assertEquals(0, d.chipIndexAt(3))
        assertNull(d.chipIndexAt(2))
        assertEquals(-1, d.chipOffset(1))
        assertFalse(d.hasNoBlanks)
    }

    @Test
    fun insertAtTheStartTheMiddleAndTheEnd() {
        val base = MessageDraft.fromMessage("ab")
        assertEquals("${MessageDraft.PLACEHOLDER}ab", base.insertBlank(0, list("Who", "Sam"), now, uk).placeholderText())
        assertEquals("a${MessageDraft.PLACEHOLDER}b", base.insertBlank(1, list("Who", "Sam"), now, uk).placeholderText())
        assertEquals("ab${MessageDraft.PLACEHOLDER}", base.insertBlank(2, list("Who", "Sam"), now, uk).placeholderText())
        // Out of range offsets are pulled in.
        assertEquals("ab${MessageDraft.PLACEHOLDER}", base.insertBlank(99, list("Who", "Sam"), now, uk).placeholderText())
        assertEquals("${MessageDraft.PLACEHOLDER}ab", base.insertBlank(-4, list("Who", "Sam"), now, uk).placeholderText())
    }

    @Test
    fun insertIntoAnEmptyMessage() {
        val d = MessageDraft.fromMessage("").insertBlank(0, list("Who", "Sam"), now, uk)
        assertEquals(1, d.length)
        assertEquals("Sam", d.plainText())
    }

    @Test
    fun insertNeverCutsAChipInTwo() {
        val d = MessageDraft.fromMessage("ab").insertBlank(1, list("Who", "Sam"), now, uk)
            .insertBlank(2, list("What", "Tea"), now, uk)
        assertEquals("a${MessageDraft.PLACEHOLDER}${MessageDraft.PLACEHOLDER}b", d.placeholderText())
        assertEquals(2, d.blanks.size)
        assertEquals(listOf(1, 2), d.blanks.map { it.id })
    }

    @Test
    fun theDraftGivesTheNewBlankItsOwnIdAndAUniqueQuestion() {
        var d = MessageDraft.fromMessage("x")
        val first = d.nextBlankId
        d = d.insertBlank(1, list("Who", "Sam"), now, uk)
        assertEquals(first, d.blanks.single().id)
        d = d.insertBlank(1, list("who", "Ben"), now, uk)
        assertEquals(listOf("Who", "who 2"), d.blanks.map { it.question })
        assertEquals(first + 2, d.nextBlankId)
    }

    @Test
    fun replaceRangeSwapsWordsForAChip() {
        val d = swap(MessageDraft.fromMessage(message), "Sam", list("Who", "Sam", "Ben"))
        assertEquals("${MessageDraft.PLACEHOLDER}, Calpol, 5ml, 20:20 next dose not before 00:20", d.placeholderText())
        assertEquals(message, d.plainText())
    }

    @Test
    fun replaceRangeWithAChipInsideForgetsThatChip() {
        var d = MessageDraft.fromMessage("a b c").insertBlank(2, list("Who", "Sam"), now, uk)
        assertEquals(1, d.blanks.size)
        d = d.replaceRange(0, d.length, BlankSpec(0, "Where", BlankKind.LIST, listOf("Home"), example = "Home"), now, uk)
        assertEquals(listOf("Where"), d.blanks.map { it.question })
        assertEquals(1, d.length)
        assertEquals("Home", d.plainText())
    }

    @Test
    fun deleteRangeKeepsTheRest() {
        val d = MessageDraft.fromMessage("abcdef").deleteRange(2, 4)
        assertEquals("abef", d.placeholderText())
        assertEquals("abcdef", MessageDraft.fromMessage("abcdef").deleteRange(3, 3).placeholderText())
        assertEquals("", MessageDraft.fromMessage("abc").deleteRange(-5, 50).placeholderText())
    }

    @Test
    fun updateBlankChangesWhatIsShownEverywhere() {
        var d = MessageDraft.fromMessage("Hi , and ").insertBlank(3, list("Who", "Sam"), now, uk)
        val id = d.blanks.single().id
        d = d.updateBlank(id, d.blank(id)!!.copy(question = "Name", answers = listOf("Ben", "Sam"), example = "Ben"), now, uk)
        assertEquals("Hi Ben, and ", d.plainText())
        assertEquals("Name", d.blank(id)!!.question)
        assertEquals("{Name}", d.toShortcut("i", "n").template.substringAfter("Hi ").substringBefore(","))
    }

    @Test
    fun updateBlankKeepsQuestionsDifferent() {
        var d = MessageDraft.fromMessage("x").insertBlank(1, list("Who", "Sam"), now, uk).insertBlank(1, list("What", "Tea"), now, uk)
        val id = d.blanks.first { it.question == "What" }.id
        d = d.updateBlank(id, d.blank(id)!!.copy(question = "who"), now, uk)
        assertEquals("who 2", d.blank(id)!!.question)
        // The blank keeps its own name when it is only changed in other ways.
        d = d.updateBlank(id, d.blank(id)!!.copy(answers = listOf("Coffee")), now, uk)
        assertEquals("who 2", d.blank(id)!!.question)
    }

    @Test
    fun updateOfUnknownBlankDoesNothing() {
        val d = MessageDraft.fromMessage("x")
        assertEquals(d, d.updateBlank(9, list("Who", "Sam"), now, uk))
    }

    @Test
    fun removeBlankTakesOutEveryUse() {
        val d = medicine()
        val whenId = d.blanks.first { it.question == "When" }.id
        val removed = d.removeBlank(whenId)
        assertNull(removed.blank(whenId))
        assertEquals("Sam, Calpol, 5ml,  next dose not before ", removed.plainText())
        assertEquals(listOf("Who", "What", "How much"), removed.blanks.map { it.question })
    }

    @Test
    fun removeChipTakesOutOnlyThatChip() {
        val d = medicine()
        val last = d.chips().lastIndex
        val removed = d.removeChip(last)
        // The time is still used once (the reference went, the plain time stays).
        assertEquals(4, removed.blanks.size)
        assertEquals("Sam, Calpol, 5ml, 20:20 next dose not before ", removed.plainText())
        // Removing the only use of a blank forgets it.
        assertEquals(3, removed.removeChip(3).blanks.size)
    }

    @Test
    fun makePlainTurnsAChipIntoItsExample() {
        val d = medicine()
        val plain = d.makePlain(0)
        assertEquals(message, plain.plainText())
        assertEquals(3, plain.blanks.size)
        assertTrue(plain.placeholderText().startsWith("Sam, "))
        // The first chip is gone, so the next chip is now number 0.
        assertEquals("What", plain.blank((plain.chips()[0] as Segment.Ref).blankId)!!.question)
    }

    @Test
    fun makePlainKeepsABlankThatIsUsedAgain() {
        val d = medicine()
        val timeChips = d.chips().withIndex().filter { (it.value as? Segment.Ref)?.let { r -> d.blank(r.blankId)!!.question == "When" } == true }
        assertEquals(2, timeChips.size)
        val plain = d.makePlain(timeChips.last().index)
        assertEquals(4, plain.blanks.size)
        assertEquals(message, plain.plainText())
    }

    // ---- a reference to another time ----

    @Test
    fun aTimeAfterAnotherTimeIsAReference() {
        val d = medicine()
        val shortcut = d.toShortcut("id", "Medicine given")
        assertEquals("{Who}, {What}, {How much}, {When} next dose not before {When+4h}", shortcut.template)
        val ref = d.chips().last() as Segment.Ref
        assertEquals(240, ref.offsetMinutes)
        assertEquals("00:20", ref.shown)
    }

    @Test
    fun aTimeBeforeAnotherTimeIsAReferenceToo() {
        var d = MessageDraft.fromMessage("at , earlier ").insertBlank(3, BlankSpec(0, "When", BlankKind.TIME, example = "20:20"), now, uk)
        d = d.insertReference(d.length, d.blanks.single().id, -90, now, uk)
        assertEquals("at {When}, earlier {When-1h30m}", d.toShortcut("i", "n").template)
        assertEquals("at 20:20, earlier 18:50", d.plainText())
    }

    @Test
    fun aReferenceNeedsAClockTimeBlank() {
        val d = MessageDraft.fromMessage("x").insertBlank(1, list("Who", "Sam"), now, uk)
        assertEquals(d, d.insertReference(0, d.blanks.single().id, 60, now, uk))
        assertEquals(d, d.insertReference(0, 99, 60, now, uk))
    }

    @Test
    fun aReferenceCanBeMoved() {
        var d = medicine()
        val index = d.chips().lastIndex
        d = d.setChipOffset(index, 90, now, uk)
        assertEquals("{When+1h30m}", d.toShortcut("i", "n").template.substringAfter("before "))
        assertEquals("21:50", (d.chips().last() as Segment.Ref).shown)
    }

    @Test
    fun anOldShortcutWithAClockTimeOffsetStillLoads() {
        val old = Shortcut(
            "old", "Meds", "{Time} next at {Time+4h|h:mm a} then {Time-15m}",
            listOf(VariableDef("Time", VariableType.TIME, lastValue = "20:20")),
        )
        val d = MessageDraft.fromShortcut(old, now, uk)
        assertEquals(1, d.blanks.size)
        assertEquals(3, d.chips().size)
        assertEquals(listOf(0L, 240L, -15L), d.chips().map { (it as Segment.Ref).offsetMinutes })
        assertEquals("20:20 next at 12:20 AM then 20:05", d.plainText().replace("12:20 am", "12:20 AM"))
        assertEquals(old.template, d.toShortcut("old", "Meds").template)
    }

    // ---- editing in the text box ----

    @Test
    fun typingAroundChipsKeepsThem() {
        val d = MessageDraft.fromMessage("Hi ").insertBlank(3, list("Who", "Sam"), now, uk)
        val typed = d.withEditableText("Oh hi ${MessageDraft.PLACEHOLDER}!", d.chips())
        assertEquals("Oh hi Sam!", typed.plainText())
        assertEquals(1, typed.blanks.size)
        assertEquals(listOf("Oh hi ", "!"), typed.segments.filterIsInstance<Segment.Text>().map { it.text })
    }

    @Test
    fun deletingThePlaceholderRemovesTheChipAndItsBlank() {
        val d = MessageDraft.fromMessage("Hi ").insertBlank(3, list("Who", "Sam"), now, uk)
        val gone = d.withEditableText("Hi ", emptyList())
        assertEquals("Hi ", gone.plainText())
        assertTrue(gone.blanks.isEmpty())
        assertEquals(0, gone.chips().size)
    }

    @Test
    fun aBlankUsedTwiceSurvivesLosingOneChip() {
        val d = medicine()
        val keep = d.chips().dropLast(1)
        val typed = d.withEditableText(d.placeholderText().dropLast(1), keep)
        assertEquals(4, typed.blanks.size)
    }

    @Test
    fun aPlaceholderWithNoChipIsDropped() {
        val d = MessageDraft.fromMessage("x")
        assertEquals("ab", d.withEditableText("a${MessageDraft.PLACEHOLDER}b", emptyList()).plainText())
    }

    @Test
    fun twoChipsKeepTheirOrderWhenTextIsInsertedBetween() {
        val d = MessageDraft.fromMessage("").insertBlank(0, list("Who", "Sam"), now, uk).insertBlank(1, list("What", "Tea"), now, uk)
        val p = MessageDraft.PLACEHOLDER
        val typed = d.withEditableText("$p and $p", d.chips())
        assertEquals("Sam and Tea", typed.plainText())
        assertEquals(listOf("Who", "What"), typed.questions().map { it.question })
    }

    // ---- what the keyboard gets ----

    @Test
    fun buildsTheTemplateTheKeyboardReads() {
        val shortcut = medicine().toShortcut("id", " Medicine given ", parentId = "meds", order = 3, icon = "💊", color = 4)
        assertEquals("{Who}, {What}, {How much}, {When} next dose not before {When+4h}", shortcut.template)
        assertEquals("Medicine given", shortcut.name)
        assertEquals("meds", shortcut.parentId)
        assertEquals(3, shortcut.order)
        assertEquals("💊", shortcut.icon)
        assertEquals(4, shortcut.color)
        assertEquals(listOf("Who", "What", "How much", "When"), shortcut.variables.map { it.name })
        assertEquals(listOf(VariableType.CHOICE, VariableType.CHOICE, VariableType.CHOICE, VariableType.TIME), shortcut.variables.map { it.type })
        assertEquals(listOf("Sam", "Ben"), shortcut.variables[0].options)
        assertEquals("Sam", shortcut.variables[0].lastValue)
        assertEquals(listOf("5ml"), shortcut.variables[2].options)
    }

    @Test
    fun theKeyboardCanFillTheShortcutWeBuilt() {
        val shortcut = medicine().toShortcut("id", "Medicine given")
        val session = FillSession(shortcut, now = { now }, locale = uk)
        assertEquals(listOf("Who", "What", "How much", "When"), session.variables.map { it.name })
        session.setValue(session.variables[0], "Ben")
        session.setValue(session.variables[1], "Nurofen")
        session.setValue(session.variables[2], "10ml")
        assertEquals("Ben, Nurofen, 10ml, 20:20 next dose not before 00:20", session.result())
    }

    @Test
    fun curlyBracketsInPlainWordsAreEscaped() {
        val d = MessageDraft.fromMessage("Use {braces} } and {").insertBlank(0, list("Who", "Sam"), now, uk)
        val shortcut = d.toShortcut("i", "n")
        assertEquals("{Who}Use {{braces}} }} and {{", shortcut.template)
        val session = FillSession(shortcut, now = { now }, locale = uk)
        session.setValue(session.variables[0], "Sam")
        assertEquals("SamUse {braces} } and {", session.result())
        assertEquals(d.plainText(), MessageDraft.fromShortcut(shortcut, now, uk).plainText())
    }

    @Test
    fun onlyTheBlanksStillInTheMessageAreSaved() {
        val d = medicine().removeBlank(medicine().blanks.first { it.question == "What" }.id)
        assertEquals(listOf("Who", "How much", "When"), d.toShortcut("i", "n").variables.map { it.name })
    }

    @Test
    fun questionsListEachBlankOnceInOrder() {
        val q = medicine().questions()
        assertEquals(listOf("Who", "What", "How much", "When"), q.map { it.question })
        assertEquals(listOf(2, 2, 1, 0), q.map { it.answerCount })
        assertTrue(q.all { it.fixedAmount == null })
    }

    @Test
    fun anOldTextBlankLoadsAsAListAndStaysText() {
        val old = Shortcut("t", "T", "Hi {Name}", listOf(VariableDef("Name", VariableType.TEXT, lastValue = "Sam")))
        val d = MessageDraft.fromShortcut(old, now, uk)
        assertEquals(BlankKind.LIST, d.blanks.single().kind)
        assertEquals(VariableType.TEXT, d.toShortcut("t", "T").variables.single().type)
        assertEquals("Hi Sam", d.plainText())
    }

    @Test
    fun builtInsStayAsTheyAre() {
        val old = Shortcut("t", "T", "On {today} at {now|h:mm a} {Who}", listOf(VariableDef("Who", options = listOf("Sam"))))
        val d = MessageDraft.fromShortcut(old, now, uk)
        assertEquals(3, d.chips().size)
        assertTrue(d.chips()[0] is Segment.Fixed)
        assertEquals(old.template, d.toShortcut("t", "T").template)
        // A built-in chip can be made plain words too.
        assertEquals(2, d.makePlain(0).chips().size)
    }

    // ---- round trips ----

    @Test
    fun everySampleShortcutSurvivesTheEditor() {
        for (sample in SampleShortcuts.all) {
            val draft = MessageDraft.fromShortcut(sample, now, uk)
            val back = draft.toShortcut(sample.id, sample.name, sample.parentId, sample.order, sample.icon, sample.color)
            assertEquals(sample.id, sample.template, back.template)
            assertEquals(sample.id, sample.variables, back.variables)
            assertEquals(sample, back)
        }
    }

    @Test
    fun everySampleShortcutSurvivesTheTextBox() {
        for (sample in SampleShortcuts.all) {
            val draft = MessageDraft.fromShortcut(sample, now, uk)
            val retyped = draft.withEditableText(draft.placeholderText(), draft.chips())
            assertEquals(draft, retyped)
            assertEquals(sample.template, retyped.toShortcut(sample.id, sample.name, sample.parentId, sample.order, sample.icon).template)
        }
    }

    @Test
    fun everySampleShortcutSurvivesJson() {
        for (sample in SampleShortcuts.all) {
            val draft = MessageDraft.fromShortcut(sample, now, uk)
            assertEquals(draft, MessageDraft.fromJson(draft.toJson()))
        }
        assertNull(MessageDraft.fromJson("nope"))
    }

    @Test
    fun theMedicineExampleKeepsItsExampleText() {
        val draft = MessageDraft.fromShortcut(SampleShortcuts.all.first { it.id == "sample-medicine" }, now, uk)
        assertEquals("Sam, Calpol, 2.5ml, 20:20 next dose not before 00:20", draft.plainText())
        assertEquals(4, draft.blanks.size)
        assertEquals(5, draft.chips().size)
    }

    // ---- "how long" parts ----

    private fun ahead(show: RelativeShow, vararg amounts: RelativeAmount, question: String = "How long") =
        BlankSpec(0, question, BlankKind.TIME_AHEAD, show = show, amounts = amounts.toList())

    private val tenMins = RelativeAmount(10, RelativeUnit.MINS)
    private val oneHour = RelativeAmount(1, RelativeUnit.HOURS)
    private val twoDays = RelativeAmount(2, RelativeUnit.DAYS)

    @Test
    fun oneAmountMakesAFixedBlank() {
        val d = MessageDraft.fromMessage("Check in ").insertBlank(9, ahead(RelativeShow.AMOUNT, tenMins), now, uk)
        val def = d.toShortcut("i", "n").variables.single()
        assertEquals(VariableType.TIME_AHEAD, def.type)
        assertEquals(listOf("10 mins"), def.options)
        assertTrue(def.fixed)
        assertNull(def.lastValue)
        assertEquals(RelativeShow.AMOUNT, def.show)
        assertEquals("Check in 10 mins", d.plainText())
        assertEquals("10 mins", d.questions().single().fixedAmount)
        assertEquals(1, d.questions().single().answerCount)
    }

    @Test
    fun severalAmountsMakeAnAskedBlankWithTheFirstOfferedFirst() {
        val d = MessageDraft.fromMessage("Back ").insertBlank(5, ahead(RelativeShow.CLOCK, tenMins, oneHour, twoDays, question = "When"), now, uk)
        val def = d.toShortcut("i", "n").variables.single()
        assertEquals(listOf("10 mins", "1 hour", "2 days"), def.options)
        assertFalse(def.fixed)
        assertEquals("10 mins", def.lastValue)
        assertEquals(RelativeShow.CLOCK, def.show)
        assertEquals("Back 20:30", d.plainText())
        assertNull(d.questions().single().fixedAmount)
        assertEquals(3, d.questions().single().answerCount)
    }

    @Test
    fun theKeyboardSkipsAFixedBlankAndPrintsIt() {
        for ((show, printed) in listOf(
            RelativeShow.AMOUNT to "Call in 10 mins",
            RelativeShow.CLOCK to "Call in 20:30",
            RelativeShow.BOTH to "Call in 10 mins (20:30)",
        )) {
            val d = MessageDraft.fromMessage("Call in ").insertBlank(8, ahead(show, tenMins), now, uk)
            val session = FillSession(d.toShortcut("i", "n"), now = { now }, locale = uk)
            assertTrue(session.isEmpty)
            assertEquals(printed, session.result())
            assertEquals(printed, d.plainText())
        }
    }

    @Test
    fun theKeyboardAsksAnAskedBlankAndPrintsThePick() {
        val d = MessageDraft.fromMessage("Took it  ago").insertBlank(8, BlankSpec(0, "How long", BlankKind.TIME_AGO, show = RelativeShow.BOTH, amounts = listOf(tenMins, oneHour)), now, uk)
        val session = FillSession(d.toShortcut("i", "n"), now = { now }, locale = uk)
        assertEquals(listOf("How long"), session.variables.map { it.name })
        assertEquals("10 mins", session.valueOf(session.variables[0]))
        assertEquals("Took it 10 mins (20:10) ago", session.result())
        session.setValue(session.variables[0], "1 hour")
        assertEquals("Took it 1 hour (19:20) ago", session.result())
    }

    @Test
    fun aFixedAndAnAskedBlankTogether() {
        var d = MessageDraft.fromMessage("Dose , again in ")
        d = d.insertBlank(5, list("Which", "Calpol", "Nurofen"), now, uk)
        d = d.insertBlank(d.length, ahead(RelativeShow.BOTH, RelativeAmount(4, RelativeUnit.HOURS)), now, uk)
        val session = FillSession(d.toShortcut("i", "n"), now = { now }, locale = uk)
        assertEquals(listOf("Which"), session.variables.map { it.name })
        session.setValue(session.variables[0], "Nurofen")
        assertEquals("Dose Nurofen, again in 4 hours (00:20)", session.result())
        assertEquals(listOf("Which", "How long"), d.questions().map { it.question })
    }

    @Test
    fun howLongPartsSurviveTheEditorAndJson() {
        var d = MessageDraft.fromMessage("a  b ")
        d = d.insertBlank(2, ahead(RelativeShow.BOTH, tenMins, oneHour, question = "Wait"), now, uk)
        d = d.insertBlank(d.length, BlankSpec(0, "Gone", BlankKind.TIME_AGO, show = RelativeShow.CLOCK, amounts = listOf(twoDays)), now, uk)
        val shortcut = d.toShortcut("i", "n")
        val again = MessageDraft.fromShortcut(shortcut, now, uk)
        assertEquals(shortcut, again.toShortcut("i", "n"))
        assertEquals(d.blanks.map { it.copy(lastValue = null) }, again.blanks.map { it.copy(lastValue = null) })
        assertEquals(d, MessageDraft.fromJson(d.toJson()))
        assertEquals(d.plainText(), again.plainText())
    }

    @Test
    fun aLearnedLastAmountSurvivesEditingWhileItIsStillOnTheList() {
        val stored = Shortcut(
            "i", "n", "In {How long}",
            listOf(VariableDef("How long", VariableType.TIME_AHEAD, listOf("10 mins", "1 hour"), lastValue = "1 hour", show = RelativeShow.CLOCK)),
        )
        val back = MessageDraft.fromShortcut(stored, now, uk).toShortcut("i", "n")
        assertEquals(stored, back)
        // If the last used amount is taken off the list, the first one is offered.
        val d = MessageDraft.fromShortcut(stored, now, uk)
        val spec = d.blanks.single()
        val edited = d.updateBlank(spec.id, spec.copy(amounts = listOf(tenMins, twoDays)), now, uk)
        assertEquals("10 mins", edited.toShortcut("i", "n").variables.single().lastValue)
    }

    @Test
    fun makingAHowLongChipPlainUsesWhatItShows() {
        val d = MessageDraft.fromMessage("In ").insertBlank(3, ahead(RelativeShow.BOTH, tenMins), now, uk)
        assertEquals("In 10 mins (20:30)", d.makePlain(0).plainText())
        assertTrue(d.makePlain(0).blanks.isEmpty())
    }

    @Test
    fun aBrokenHowLongBlankStillLoads() {
        val stored = Shortcut("i", "n", "In {Wait}", listOf(VariableDef("Wait", VariableType.TIME_AHEAD, listOf("soon", "5 mins", "5 mins"))))
        val d = MessageDraft.fromShortcut(stored, now, uk)
        assertEquals(listOf("5 mins"), d.blanks.single().amounts.map { it.text() })
        val none = MessageDraft.fromShortcut(Shortcut("i", "n", "In {Wait}", listOf(VariableDef("Wait", VariableType.TIME_AGO))), now, uk)
        assertEquals("In [Wait]", none.plainText())
        assertNotNull(none.toShortcut("i", "n"))
    }

    @Test
    fun theExamplesOfDatesAndNumbers() {
        val d = MessageDraft.fromMessage("On , count ")
            .insertBlank(3, BlankSpec(0, "Date", BlankKind.DATE), now, uk)
        val withNumber = d.insertBlank(d.length, BlankSpec(0, "How many", BlankKind.NUMBER), now, uk)
        assertEquals("On 16 Oct 2026, count 1", withNumber.plainText())
        val def = withNumber.toShortcut("i", "n").variables
        assertEquals(listOf(VariableType.DATE, VariableType.NUMBER), def.map { it.type })
        assertNull(def[1].lastValue)
        val typed = withNumber.updateBlank(withNumber.blanks[1].id, withNumber.blanks[1].copy(example = "5", lastValue = "5"), now, uk)
        assertEquals("5", typed.toShortcut("i", "n").variables[1].lastValue)
    }
}

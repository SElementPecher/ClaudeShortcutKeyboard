package io.github.selementpecher.shortcutkeyboard.ui.editor

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
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

class AddFlowTest {

    private val now = LocalDateTime.of(2026, 10, 16, 20, 25)
    private val uk = Locale.UK

    private fun flow(
        taken: Set<String> = emptySet(),
        clock: List<ClockBlank> = emptyList(),
        selected: String = "",
    ) = AddFlow.start(taken, clock, selected, now, uk)

    private fun AddFlow.spec(): BlankSpec = (result() as AddResult.Blank).spec

    private fun AddFlow.typeAmount(text: String, unit: RelativeUnit = RelativeUnit.MINS) {
        setUnit(unit)
        typeNumber(text)
    }

    // ---- first screen ----

    @Test
    fun startsOnTheKindsWithNoChipYet() {
        val f = flow()
        assertEquals(FlowStep.KIND, f.step)
        assertNull(f.preview())
        assertFalse(f.canAdvance)
        assertFalse(f.canGoBack)
        assertFalse(f.back())
        assertTrue(f.crumbs().isEmpty())
        val base = MessageDraft.fromMessage("Hi")
        assertEquals(base, f.applyTo(base, 2))
        assertNull(f.result())
    }

    @Test
    fun onlyTheFourKindsCanBeChosenFirst() {
        val f = flow()
        f.chooseKind(BlankKind.TIME_AHEAD)
        assertEquals(FlowStep.KIND, f.step)
        f.chooseKind(BlankKind.TIME)
        assertEquals(FlowStep.TIME_KIND, f.step)
        // Choosing again on a later screen does nothing.
        f.chooseKind(BlankKind.LIST)
        assertEquals(FlowStep.TIME_KIND, f.step)
    }

    @Test
    fun theChipAppearsAsSoonAsTheKindIsChosen() {
        val f = flow()
        f.chooseKind(BlankKind.LIST)
        val chip = f.preview() as ChipPreview.Blank
        assertEquals(BlankKind.LIST, chip.spec.kind)
        assertFalse(chip.named)
        val d = f.applyTo(MessageDraft.fromMessage("Hi "), 3)
        assertEquals(1, d.chips().size)
        assertEquals("Hi ${MessageDraft.PLACEHOLDER}", d.placeholderText())
    }

    // ---- a list ----

    @Test
    fun aListGoesStraightToItsAnswers() {
        val f = flow()
        f.chooseKind(BlankKind.LIST)
        assertEquals(FlowStep.LIST_ANSWERS, f.step)
        assertEquals(listOf(Crumb.Kind(BlankKind.LIST)), f.crumbs())
        assertFalse(f.canAdvance)
        f.setAnswer(0, "Sam")
        f.addAnswerRow()
        f.setAnswer(1, "Ben")
        f.addAnswerRow()
        assertEquals(listOf("Sam", "Ben", ""), f.answers)
        assertTrue(f.canAdvance)
        assertNull(f.result())
        f.advance()
        assertTrue(f.isDone)
        val spec = f.spec()
        assertEquals("List", spec.question)
        assertEquals(BlankKind.LIST, spec.kind)
        assertEquals(listOf("Sam", "Ben"), spec.answers)
        assertEquals("Sam", spec.example)
        assertEquals("Sam", spec.lastValue)
    }

    @Test
    fun aListNeedsAtLeastOneAnswer() {
        val f = flow()
        f.chooseKind(BlankKind.LIST)
        f.advance()
        f.setAnswer(0, "   ")
        assertFalse(f.canAdvance)
        f.advance()
        assertFalse(f.isDone)
        f.setAnswer(0, "x")
        assertTrue(f.canAdvance)
    }

    @Test
    fun answersCanBeTakenOutButOneRowStays() {
        val f = flow()
        f.chooseKind(BlankKind.LIST)
        f.advance()
        f.setAnswer(0, "a"); f.addAnswerRow(); f.setAnswer(1, "b")
        f.removeAnswerRow(0)
        assertEquals(listOf("b"), f.answers)
        f.removeAnswerRow(0)
        assertEquals(listOf(""), f.answers)
        f.removeAnswerRow(7)
        assertEquals(listOf(""), f.answers)
        f.setAnswer(5, "ignored")
        assertEquals(listOf(""), f.answers)
    }

    @Test
    fun repeatedAnswersAreDropped() {
        val f = flow()
        f.chooseKind(BlankKind.LIST)
        f.advance()
        f.setAnswer(0, "Sam"); f.addAnswerRow(); f.setAnswer(1, "sam"); f.addAnswerRow(); f.setAnswer(2, " Ben ")
        f.advance()
        assertEquals(listOf("Sam", "Ben"), f.spec().answers)
    }

    @Test
    fun selectedWordsBecomeTheFirstAnswer() {
        val f = flow(selected = "  Sam ")
        f.chooseKind(BlankKind.LIST)
        f.advance()
        assertEquals(listOf("Sam"), f.answers)
        assertTrue(f.canAdvance)
        f.advance()
        assertEquals(listOf("Sam"), f.spec().answers)
    }

    @Test
    fun aListIsNamedByDefaultAndKeptApartFromSameNames() {
        val f = flow(taken = setOf("List"))
        f.chooseKind(BlankKind.LIST)
        f.setAnswer(0, "x"); f.advance()
        assertEquals("List\u200B", f.spec().question)
    }

    @Test
    fun aListKeepsTheNameItIsGivenAndIsCalledListWithout() {
        val named = flow()
        named.chooseKind(BlankKind.LIST)
        named.setQuestion("  Medicine ")
        named.setAnswer(0, "Calpol"); named.advance()
        assertEquals("Medicine", named.spec().question)
        val unnamed = flow()
        unnamed.chooseKind(BlankKind.LIST)
        unnamed.setQuestion("   ")
        unnamed.setAnswer(0, "Calpol"); unnamed.advance()
        assertEquals("List", unnamed.spec().question)
    }

    // ---- back and cancel ----

    @Test
    fun backGoesOneLevelAndKeepsWhatWasTyped() {
        val f = flow()
        f.chooseKind(BlankKind.LIST)
        f.setAnswer(0, "Sam")
        assertTrue(f.back())
        assertEquals(FlowStep.KIND, f.step)
        assertTrue(f.crumbs().isEmpty())
        assertFalse(f.back())
    }

    @Test
    fun backAfterFinishingReopensTheLastLevel() {
        val f = flow()
        f.chooseKind(BlankKind.DATE)
        f.advance()
        assertTrue(f.isDone)
        assertTrue(f.back())
        assertFalse(f.isDone)
        assertNull(f.result())
        assertEquals(FlowStep.QUESTION, f.step)
        assertTrue(f.back())
        assertEquals(FlowStep.KIND, f.step)
    }

    @Test
    fun backThroughTheTimeLevels() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.HOURS)
        assertEquals(
            listOf(Crumb.Kind(BlankKind.TIME), Crumb.Time(TimeChoice.FROM_NOW), Crumb.Unit(RelativeUnit.HOURS)),
            f.crumbs(),
        )
        f.back()
        assertEquals(listOf(Crumb.Kind(BlankKind.TIME), Crumb.Time(TimeChoice.FROM_NOW)), f.crumbs())
        assertEquals(FlowStep.UNIT, f.step)
        f.back()
        assertEquals(listOf(Crumb.Kind(BlankKind.TIME)), f.crumbs())
        assertEquals(FlowStep.TIME_KIND, f.step)
        f.chooseTime(TimeChoice.AGO)
        assertEquals(BlankKind.TIME_AGO, f.kind())
        f.back()
        f.back()
        assertEquals(FlowStep.KIND, f.step)
    }

    @Test
    fun cancelTakesTheChipAwayAndMakesNothing() {
        val f = flow()
        f.chooseKind(BlankKind.LIST)
        val base = MessageDraft.fromMessage("Hi ")
        assertEquals(1, f.applyTo(base, 3).chips().size)
        f.cancel()
        assertTrue(f.isCancelled)
        assertNull(f.result())
        assertNull(f.preview())
        assertEquals(base, f.applyTo(base, 3))
    }

    // ---- date and number ----

    @Test
    fun aDateAsksOnlyTheQuestion() {
        val f = flow()
        f.chooseKind(BlankKind.DATE)
        assertEquals(FlowStep.QUESTION, f.step)
        f.advance()
        val spec = f.spec()
        assertEquals("Date", spec.question)
        assertEquals(BlankKind.DATE, spec.kind)
        val g = flow()
        g.chooseKind(BlankKind.DATE)
        g.setQuestion("Due day")
        g.advance()
        assertEquals("Due day", g.spec().question)
    }

    @Test
    fun aNumberAsksOnlyTheQuestionAndUsesTheNumberInTheSelection() {
        val f = flow(selected = "5ml")
        f.chooseKind(BlankKind.NUMBER)
        assertEquals(FlowStep.QUESTION, f.step)
        f.advance()
        val spec = f.spec()
        assertEquals("How many", spec.question)
        assertEquals("5", spec.example)
        assertEquals("5", spec.lastValue)
        val g = flow(selected = "many")
        g.chooseKind(BlankKind.NUMBER)
        g.advance()
        assertEquals("", g.spec().example)
        assertNull(g.spec().lastValue)
    }

    // ---- a clock time ----

    @Test
    fun aClockTimeAsksTheQuestionWhen() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        assertEquals(FlowStep.TIME_KIND, f.step)
        assertEquals(BlankKind.TIME, f.kind())
        f.chooseTime(TimeChoice.CLOCK)
        assertEquals(FlowStep.QUESTION, f.step)
        f.advance()
        val spec = f.spec()
        assertEquals("When", spec.question)
        assertEquals(BlankKind.TIME, spec.kind)
        assertEquals("", spec.example)
    }

    @Test
    fun aSelectedClockTimeBecomesTheExample() {
        val f = flow(selected = "8.05")
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.CLOCK)
        f.advance()
        assertEquals("08:05", f.spec().example)
        val g = flow(selected = "tomorrow")
        g.chooseKind(BlankKind.TIME)
        g.chooseTime(TimeChoice.CLOCK)
        g.advance()
        assertEquals("", g.spec().example)
    }

    // ---- from now / ago ----

    @Test
    fun oneAmountFromNowIsFixedAndNeverAsksAQuestion() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        assertEquals(FlowStep.UNIT, f.step)
        assertEquals(listOf(RelativeUnit.MINS, RelativeUnit.HOURS, RelativeUnit.DAYS), f.availableUnits())
        f.chooseUnit(RelativeUnit.MINS)
        assertEquals(FlowStep.AMOUNTS, f.step)
        assertFalse(f.canAdvance)
        f.typeNumber("10")
        assertTrue(f.canAdvance)
        assertEquals(RelativeAmount(10, RelativeUnit.MINS), f.pendingAmount)
        f.advance()
        assertEquals(FlowStep.SHOW, f.step)
        assertFalse(f.isDone)
        f.chooseShow(RelativeShow.BOTH)
        assertTrue(f.isDone)
        val spec = f.spec()
        assertEquals(BlankKind.TIME_AHEAD, spec.kind)
        assertEquals(listOf(RelativeAmount(10, RelativeUnit.MINS)), spec.amounts)
        assertEquals(RelativeShow.BOTH, spec.show)
        assertEquals("How long", spec.question)
        assertTrue(spec.fixed)
    }

    @Test
    fun severalAmountsAreAskedAndNamedByDefault() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.MINS)
        f.typeNumber("10")
        assertTrue(f.addAnotherAmount())
        assertEquals("", f.digits)
        assertEquals(listOf(RelativeAmount(10, RelativeUnit.MINS)), f.amounts)
        f.typeAmount("1", RelativeUnit.HOURS)
        f.advance()
        assertEquals(FlowStep.SHOW, f.step)
        f.chooseShow(RelativeShow.CLOCK)
        assertTrue(f.isDone)
        val spec = f.spec()
        assertEquals(BlankQuestions.default(BlankKind.TIME_AHEAD), spec.question)
        assertEquals(listOf("10 mins", "1 hour"), spec.amounts.map { it.text() })
        assertEquals(RelativeShow.CLOCK, spec.show)
        assertFalse(spec.fixed)
    }

    @Test
    fun theQuestionOfSeveralAmountsDefaultsToHowLong() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.AGO)
        f.chooseUnit(RelativeUnit.MINS)
        f.typeNumber("5"); f.addAnotherAmount(); f.typeNumber("15")
        f.advance()
        f.chooseShow(RelativeShow.AMOUNT)
        f.advance()
        val spec = f.spec()
        assertEquals("How long", spec.question)
        assertEquals(BlankKind.TIME_AGO, spec.kind)
        assertEquals(listOf("5 mins", "15 mins"), spec.amounts.map { it.text() })
    }

    @Test
    fun aFixedBlankGetsAUniqueAutomaticName() {
        val f = flow(taken = setOf("How long", "How long\u200B"))
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.HOURS)
        f.typeNumber("4"); f.advance(); f.chooseShow(RelativeShow.AMOUNT)
        assertEquals("How long\u200B\u200B", f.spec().question)
    }

    @Test
    fun numbersMustBeOneToNineHundredNinetyNine() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.MINS)
        for (bad in listOf("", "0", "00", "abc", ".")) {
            f.typeNumber(bad)
            assertNull("[$bad]", f.pendingAmount)
            assertFalse("[$bad]", f.canAdvance)
            assertFalse("[$bad]", f.addAnotherAmount())
        }
        f.typeNumber("1000")
        assertEquals("100", f.digits)
        f.typeNumber("12abc")
        assertEquals("12", f.digits)
        f.typeNumber("999")
        assertEquals(RelativeAmount(999, RelativeUnit.MINS), f.pendingAmount)
        f.typeNumber("1")
        assertEquals(RelativeAmount(1, RelativeUnit.MINS), f.pendingAmount)
        f.typeNumber("007")
        assertEquals(RelativeAmount(7, RelativeUnit.MINS), f.pendingAmount)
    }

    @Test
    fun theSameAmountIsOnlyAddedOnce() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.MINS)
        f.typeNumber("10"); f.addAnotherAmount()
        f.typeNumber("10"); assertTrue(f.addAnotherAmount())
        assertEquals(1, f.amounts.size)
        f.typeNumber("60"); f.setUnit(RelativeUnit.MINS); f.addAnotherAmount()
        assertEquals(listOf("10 mins", "60 mins"), f.amounts.map { it.text() })
    }

    @Test
    fun amountsCanBeRemoved() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.MINS)
        f.typeNumber("10"); f.addAnotherAmount(); f.typeNumber("20"); f.addAnotherAmount()
        f.removeAmount(0)
        assertEquals(listOf("20 mins"), f.amounts.map { it.text() })
        f.removeAmount(5)
        assertEquals(1, f.amounts.size)
        // With nothing typed, the amount already added is enough to go on.
        assertTrue(f.canAdvance)
        f.advance()
        f.chooseShow(RelativeShow.AMOUNT)
        assertTrue(f.spec().fixed)
    }

    @Test
    fun theNumberStillInTheBoxCountsWhenGoingOn() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.MINS)
        f.typeNumber("10"); f.addAnotherAmount(); f.typeNumber("30")
        f.advance(); f.chooseShow(RelativeShow.AMOUNT); f.advance()
        assertEquals(listOf("10 mins", "30 mins"), f.spec().amounts.map { it.text() })
    }

    @Test
    fun eachAmountCanHaveItsOwnUnit() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.MINS)
        f.typeNumber("30"); f.addAnotherAmount()
        f.setUnit(RelativeUnit.HOURS); f.typeNumber("2"); f.addAnotherAmount()
        f.setUnit(RelativeUnit.DAYS); f.typeNumber("1")
        f.advance(); f.chooseShow(RelativeShow.AMOUNT); f.advance()
        assertEquals(listOf("30 mins", "2 hours", "1 day"), f.spec().amounts.map { it.text() })
    }

    @Test
    fun theShowScreenHasLiveExamples() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.MINS)
        f.typeNumber("10"); f.advance()
        assertEquals(
            mapOf(RelativeShow.AMOUNT to "10 mins", RelativeShow.CLOCK to "20:35", RelativeShow.BOTH to "10 mins (20:35)"),
            f.showExamples(),
        )
        val g = flow()
        g.chooseKind(BlankKind.TIME)
        g.chooseTime(TimeChoice.AGO)
        g.chooseUnit(RelativeUnit.HOURS)
        g.typeNumber("2"); g.advance()
        assertEquals("2 hours (18:25)", g.showExamples()[RelativeShow.BOTH])
        val h = flow()
        h.chooseKind(BlankKind.TIME)
        h.chooseTime(TimeChoice.FROM_NOW)
        h.chooseUnit(RelativeUnit.DAYS)
        h.typeNumber("2"); h.advance()
        assertEquals("2 days (18 Oct)", h.showExamples()[RelativeShow.BOTH])
    }

    @Test
    fun aSelectedAmountIsFilledIn() {
        val f = flow(selected = "in 2 hours")
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        assertEquals("2", f.digits)
        assertEquals(RelativeUnit.HOURS, f.unit)
        f.chooseUnit(RelativeUnit.HOURS)
        assertEquals(RelativeAmount(2, RelativeUnit.HOURS), f.pendingAmount)
        f.advance(); f.chooseShow(RelativeShow.CLOCK)
        assertEquals(listOf("2 hours"), f.spec().amounts.map { it.text() })
        val g = flow(selected = "banana")
        g.chooseKind(BlankKind.TIME)
        g.chooseTime(TimeChoice.AGO)
        assertEquals("", g.digits)
    }

    @Test
    fun theChipFollowsWhatIsTyped() {
        val f = flow()
        val base = MessageDraft.fromMessage("In ")
        f.chooseKind(BlankKind.TIME)
        assertEquals("In 20:25", f.applyTo(base, 3).plainText())
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.MINS)
        assertEquals(1, f.applyTo(base, 3).chips().size)
        f.typeNumber("10")
        assertEquals("In 10 mins", f.applyTo(base, 3).plainText())
        f.typeNumber("15")
        assertEquals("In 15 mins", f.applyTo(base, 3).plainText())
        f.advance()
        f.chooseShow(RelativeShow.CLOCK)
        assertEquals("In 20:40", f.applyTo(base, 3).plainText())
    }

    // ---- after another time ----

    @Test
    fun afterAnotherTimeIsOnlyOfferedWhenThereIsAClockTime() {
        val f = flow()
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.AFTER_OTHER)
        assertEquals(FlowStep.TIME_KIND, f.step)
    }

    @Test
    fun afterAnotherTimeWithOneClockTimeSkipsTheChoice() {
        val f = flow(clock = listOf(ClockBlank(4, "When")))
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.AFTER_OTHER)
        assertEquals(FlowStep.UNIT, f.step)
        assertEquals(listOf(RelativeUnit.MINS, RelativeUnit.HOURS), f.availableUnits())
        f.chooseUnit(RelativeUnit.DAYS)
        assertEquals(FlowStep.UNIT, f.step)
        f.chooseUnit(RelativeUnit.HOURS)
        assertEquals(FlowStep.AFTER_NUMBER, f.step)
        assertEquals("When", f.base?.question)
        assertFalse(f.canAdvance)
        f.typeNumber("4")
        f.advance()
        assertTrue(f.isDone)
        assertEquals(AddResult.Reference(4, 240), f.result())
    }

    @Test
    fun beforeAnotherTimeIsNegative() {
        val f = flow(clock = listOf(ClockBlank(4, "When")))
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.AFTER_OTHER)
        f.chooseUnit(RelativeUnit.MINS)
        f.setAfter(false)
        f.typeNumber("30")
        f.advance()
        assertEquals(AddResult.Reference(4, -30), f.result())
    }

    @Test
    fun withTwoClockTimesTheOwnerChoosesWhichOne() {
        val f = flow(clock = listOf(ClockBlank(1, "Start"), ClockBlank(3, "End")))
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.AFTER_OTHER)
        assertEquals(FlowStep.BASE, f.step)
        f.chooseBase(99)
        assertEquals(FlowStep.BASE, f.step)
        f.chooseBase(3)
        assertEquals(FlowStep.UNIT, f.step)
        f.chooseUnit(RelativeUnit.MINS)
        f.typeNumber("45"); f.advance()
        assertEquals(AddResult.Reference(3, 45), f.result())
        assertTrue(f.back())
        assertEquals(FlowStep.AFTER_NUMBER, f.step)
    }

    @Test
    fun theReferenceChipAppearsOnceTheTimeIsKnownAndFollowsTheNumber() {
        var base = MessageDraft.fromMessage("at , next ").insertBlank(3, BlankSpec(0, "When", BlankKind.TIME), now, uk)
        val id = base.blanks.single().id
        val f = flow(clock = listOf(ClockBlank(id, "When")))
        f.chooseKind(BlankKind.TIME)
        assertEquals(2, f.applyTo(base, base.length).chips().size)
        f.chooseTime(TimeChoice.AFTER_OTHER)
        assertTrue(f.preview() is ChipPreview.Reference)
        f.chooseUnit(RelativeUnit.HOURS)
        f.typeNumber("4")
        val d = f.applyTo(base, base.length)
        assertEquals("at 20:25, next 00:25", d.plainText())
        assertEquals(1, d.blanks.size)
        assertEquals("{When+4h}", d.toShortcut("i", "n").template.substringAfter("next "))
    }

    // ---- editing a chip ----

    @Test
    fun editingAListStartsAtItsAnswersWithTheNameFilledIn() {
        val spec = BlankSpec(2, "Who", BlankKind.LIST, listOf("Sam", "Ben"), example = "Sam", lastValue = "Ben")
        val f = AddFlow.edit(spec, 0, taken = setOf("What"), now = now, locale = uk)
        assertTrue(f.isEditing)
        assertEquals(FlowStep.LIST_ANSWERS, f.step)
        assertEquals("Who", f.question)
        assertEquals(listOf("Sam", "Ben"), f.answers)
        assertTrue(f.crumbs().isEmpty())
        assertFalse(f.back())
        f.addAnswerRow(); f.setAnswer(2, "Lou")
        f.setQuestion("Child")
        f.advance()
        val edited = f.spec()
        assertEquals(2, edited.id)
        assertEquals("Child", edited.question)
        assertEquals(listOf("Sam", "Ben", "Lou"), edited.answers)
        assertEquals("Ben", edited.lastValue)
        assertEquals("Sam", edited.example)
    }

    @Test
    fun editingKeepsTheExampleOnlyWhileItIsStillAnAnswer() {
        val spec = BlankSpec(1, "Who", BlankKind.LIST, listOf("Sam", "Ben"), example = "Sam", lastValue = "Sam")
        val f = AddFlow.edit(spec, 0, emptySet(), now = now, locale = uk)
        f.advance(); f.setAnswer(0, "Zed")
        f.advance()
        assertEquals("Zed", f.spec().example)
        assertEquals(listOf("Zed", "Ben"), f.spec().answers)
    }

    @Test
    fun editingAnOldTextBlankWithNoAnswersCanBeDone() {
        val spec = BlankSpec(1, "Name", BlankKind.LIST, emptyList(), example = "Sam", textType = true)
        val f = AddFlow.edit(spec, 0, emptySet(), now = now, locale = uk)
        f.advance()
        assertTrue(f.canAdvance)
        f.advance()
        assertTrue(f.spec().textType)
    }

    @Test
    fun editingTimeDateAndNumberStartsAtTheQuestion() {
        for (kind in listOf(BlankKind.TIME, BlankKind.DATE, BlankKind.NUMBER)) {
            val f = AddFlow.edit(BlankSpec(1, "Thing", kind, example = if (kind == BlankKind.TIME) "20:20" else ""), 0, emptySet(), now = now, locale = uk)
            assertEquals(FlowStep.QUESTION, f.step)
            f.setQuestion("Other")
            f.advance()
            val spec = f.spec()
            assertEquals(kind, spec.kind)
            assertEquals("Other", spec.question)
            if (kind == BlankKind.TIME) assertEquals("20:20", spec.example)
        }
    }

    @Test
    fun editingAFixedBlankAndAddingAnAmountMakesItAsked() {
        val spec = BlankSpec(1, "How long", BlankKind.TIME_AHEAD, show = RelativeShow.BOTH, amounts = listOf(RelativeAmount(10, RelativeUnit.MINS)))
        val f = AddFlow.edit(spec, 0, emptySet(), now = now, locale = uk)
        assertEquals(FlowStep.AMOUNTS, f.step)
        assertEquals(listOf("10 mins"), f.amounts.map { it.text() })
        f.typeNumber("30"); f.addAnotherAmount()
        f.advance()
        assertEquals(FlowStep.SHOW, f.step)
        assertEquals(RelativeShow.BOTH, f.show)
        f.chooseShow(RelativeShow.CLOCK)
        assertEquals(FlowStep.QUESTION, f.step)
        assertEquals("How long", f.question)
        f.advance()
        val edited = f.spec()
        assertEquals(listOf("10 mins", "30 mins"), edited.amounts.map { it.text() })
        assertFalse(edited.fixed)
        assertEquals(RelativeShow.CLOCK, edited.show)
        assertEquals(1, edited.id)
    }

    @Test
    fun editingAnAskedBlankDownToOneAmountMakesItFixed() {
        val spec = BlankSpec(1, "Wait", BlankKind.TIME_AGO, amounts = listOf(RelativeAmount(5, RelativeUnit.MINS), RelativeAmount(1, RelativeUnit.HOURS)))
        val f = AddFlow.edit(spec, 0, emptySet(), now = now, locale = uk)
        f.removeAmount(1)
        f.advance()
        f.chooseShow(RelativeShow.AMOUNT)
        assertTrue(f.isDone)
        val edited = f.spec()
        assertTrue(edited.fixed)
        assertEquals("Wait", edited.question)
    }

    @Test
    fun editingUpdatesTheChipInPlace() {
        val base = MessageDraft.fromMessage("Hi , ok").insertBlank(3, BlankSpec(0, "Who", BlankKind.LIST, listOf("Sam"), example = "Sam"), now, uk)
        val spec = base.blanks.single()
        val f = AddFlow.edit(spec, 0, emptySet(), now = now, locale = uk)
        f.advance(); f.setAnswer(0, "Ben")
        assertEquals("Hi Ben, ok", f.applyTo(base, 0).plainText())
        f.advance()
        val done = f.applyTo(base, 0)
        assertEquals(listOf("Ben"), done.blanks.single().answers)
        assertEquals(1, done.chips().size)
    }

    @Test
    fun editingAReferenceChangesItsOffset() {
        val clockSpec = BlankSpec(0, "When", BlankKind.TIME, example = "20:00")
        var base = MessageDraft.fromMessage("at , then ").insertBlank(3, clockSpec, now, uk)
        val id = base.blanks.single().id
        base = base.insertReference(base.length, id, 240, now, uk)
        val f = AddFlow.editReference(id, 240, 1, listOf(ClockBlank(id, "When")), now, uk)
        assertEquals(FlowStep.AFTER_NUMBER, f.step)
        assertEquals("4", f.digits)
        assertEquals(RelativeUnit.HOURS, f.unit)
        assertTrue(f.after)
        f.typeNumber("90"); f.setUnit(RelativeUnit.MINS); f.setAfter(false)
        f.advance()
        assertEquals(AddResult.Reference(id, -90), f.result())
        val d = f.applyTo(base, 0)
        assertEquals("at 20:00, then 18:30", d.plainText())
        assertEquals(1, d.blanks.size)
    }

    @Test
    fun anOddOffsetIsEditedInMinutes() {
        val f = AddFlow.editReference(1, -30, 0, listOf(ClockBlank(1, "When")), now, uk)
        assertEquals("30", f.digits)
        assertEquals(RelativeUnit.MINS, f.unit)
        assertFalse(f.after)
        val g = AddFlow.editReference(1, 150, 0, listOf(ClockBlank(1, "When")), now, uk)
        assertEquals("150", g.digits)
        assertEquals(RelativeUnit.MINS, g.unit)
    }

    // ---- turning the phone ----

    @Test
    fun theStateSurvivesJsonInTheMiddle() {
        val f = flow(taken = setOf("Who"), clock = listOf(ClockBlank(1, "When")), selected = "Sam")
        f.chooseKind(BlankKind.TIME)
        f.chooseTime(TimeChoice.FROM_NOW)
        f.chooseUnit(RelativeUnit.HOURS)
        f.typeNumber("2"); f.addAnotherAmount(); f.typeNumber("5"); f.setUnit(RelativeUnit.MINS)
        val back = AddFlow.fromJson(f.toJson(), now, uk)!!
        assertEquals(f.step, back.step)
        assertEquals(f.crumbs(), back.crumbs())
        assertEquals(f.amounts, back.amounts)
        assertEquals(f.digits, back.digits)
        assertEquals(f.unit, back.unit)
        assertTrue(back.canGoBack)
        back.advance()
        back.chooseShow(RelativeShow.BOTH)
        back.setQuestion("Wait"); back.advance()
        assertEquals(listOf("2 hours", "5 mins"), back.spec().amounts.map { it.text() })
        assertNull(AddFlow.fromJson("garbage", now, uk))
    }

    @Test
    fun anEditingStateSurvivesJson() {
        val spec = BlankSpec(3, "Who", BlankKind.LIST, listOf("Sam"), example = "Sam", lastValue = "Sam")
        val f = AddFlow.edit(spec, 2, setOf("What"), now = now, locale = uk)
        f.advance(); f.setAnswer(0, "Lou")
        val back = AddFlow.fromJson(f.toJson(), now, uk)!!
        assertTrue(back.isEditing)
        assertEquals(listOf("Lou"), back.answers)
        back.advance()
        assertEquals(3, back.spec().id)
        val r = AddFlow.editReference(1, 60, 4, listOf(ClockBlank(1, "When")), now, uk)
        assertEquals("1", AddFlow.fromJson(r.toJson(), now, uk)!!.digits)
    }

    @Test
    fun aCancelledAndADoneStateSurviveJson() {
        val f = flow()
        f.chooseKind(BlankKind.DATE); f.advance()
        assertTrue(AddFlow.fromJson(f.toJson(), now, uk)!!.isDone)
        val g = flow()
        g.chooseKind(BlankKind.DATE); g.cancel()
        assertTrue(AddFlow.fromJson(g.toJson(), now, uk)!!.isCancelled)
    }

    // ---- what the keyboard will do ----

    @Test
    fun aFlowMadeShortcutIsAskedAndPrintedByTheKeyboard() {
        var d = MessageDraft.fromMessage("Dose , again in , taken  ago")

        fun make(build: (AddFlow) -> Unit, taken: Set<String>, clock: List<ClockBlank> = emptyList()): AddResult.Blank {
            val f = AddFlow.start(taken, clock, "", now, uk)
            build(f)
            return f.result() as AddResult.Blank
        }

        val which = make({ f ->
            f.chooseKind(BlankKind.LIST); f.setQuestion("Which"); f.advance()
            f.setAnswer(0, "Calpol"); f.addAnswerRow(); f.setAnswer(1, "Nurofen"); f.advance()
        }, emptySet())
        d = d.insertBlank(5, which.spec, now, uk)

        val fixed = make({ f ->
            f.chooseKind(BlankKind.TIME); f.chooseTime(TimeChoice.FROM_NOW); f.chooseUnit(RelativeUnit.HOURS)
            f.typeNumber("4"); f.advance(); f.chooseShow(RelativeShow.BOTH)
        }, d.blanks.map { it.question }.toSet())
        d = d.insertBlank(d.placeholderText().indexOf(", taken") , fixed.spec, now, uk)

        val asked = make({ f ->
            f.chooseKind(BlankKind.TIME); f.chooseTime(TimeChoice.AGO); f.chooseUnit(RelativeUnit.MINS)
            f.typeNumber("10"); f.addAnotherAmount(); f.typeNumber("30"); f.advance()
            f.chooseShow(RelativeShow.BOTH); f.setQuestion("How long"); f.advance()
        }, d.blanks.map { it.question }.toSet())
        d = d.insertBlank(d.length - 4, asked.spec, now, uk)

        val shortcut = d.toShortcut("id", "Meds")
        assertEquals("Dose {Which}, again in {How long}, taken {How long\u200B} ago", shortcut.template)
        val session = FillSession(shortcut, now = { now }, locale = uk)
        assertEquals(listOf("Which", "How long\u200B"), session.variables.map { it.name })
        assertEquals(listOf(VariableType.CHOICE, VariableType.TIME_AGO), session.variables.map { it.type })
        assertEquals("10 mins", session.valueOf(session.variables[1]))
        session.setValue(session.variables[0], "Nurofen")
        session.setValue(session.variables[1], "30 mins")
        assertEquals("Dose Nurofen, again in 4 hours (00:25), taken 30 mins (19:55) ago", session.result())
        assertNotNull(session.updatedShortcut())
    }
}

package io.github.selementpecher.shortcutkeyboard.ui.editor

import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.template.RelativeAmount
import io.github.selementpecher.shortcutkeyboard.template.RelativeUnit
import io.github.selementpecher.shortcutkeyboard.template.Template
import io.github.selementpecher.shortcutkeyboard.ui.ShortcutDraft
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.Locale

/** A clock-time part already in the message, which a new time can be counted from. */
data class ClockBlank(val id: Int, val question: String)

/** The second choice after "A time". */
enum class TimeChoice {
    /** A clock time that starts at the time now. */
    CLOCK,

    /** A length of time from now. */
    FROM_NOW,

    /** A length of time ago. */
    AGO,

    /** A time counted from another time in the message. */
    AFTER_OTHER,
}

/** The screens ("levels") of the chain, in the order a choice leads to them. */
enum class FlowStep {
    /** A list · A time · A date · A number. */
    KIND,
    LIST_QUESTION,
    LIST_ANSWERS,

    /** Time · From now · Ago · After another time. */
    TIME_KIND,

    /** Which time to count from (only with more than one time in the message). */
    BASE,

    /** Mins · Hours · Days. */
    UNIT,

    /** Type the amounts (one is fixed, several are asked). */
    AMOUNTS,

    /** Type how long after / before another time. */
    AFTER_NUMBER,

    /** What the message should show: the amount, the time, or both. */
    SHOW,
    QUESTION,
}

/** One step of the path taken so far, for the quiet line under the title ("A time › From now › Mins"). */
sealed interface Crumb {
    data class Kind(val kind: BlankKind) : Crumb
    data class Time(val choice: TimeChoice) : Crumb
    data class Unit(val unit: RelativeUnit) : Crumb
}

/** What the chain makes. */
sealed interface AddResult {
    data class Blank(val spec: BlankSpec) : AddResult

    /** A time counted from the time of blank [blankId]: [offsetMinutes] later, or below zero for earlier. */
    data class Reference(val blankId: Int, val offsetMinutes: Long) : AddResult
}

/** What the half-made chip in the message should look like right now. */
sealed interface ChipPreview {
    /** [named] is false until the owner has said what it is, so the chip can be labelled by its kind instead. */
    data class Blank(val spec: BlankSpec, val named: Boolean) : ChipPreview
    data class Reference(val blankId: Int, val offsetMinutes: Long) : ChipPreview
}

/**
 * The chain of big choices that makes a changing part, one small decision per screen from general to specific:
 *
 *  - A list: question, then the answers.
 *  - A time: Time (a clock time, question "When") · From now / Ago (unit, then the amounts, then what the message
 *    shows, then the question if there are several amounts) · After another time (unit, then how long).
 *  - A date: question. A number: question.
 *
 * With one amount a "how long" part is fixed (never asked: the app works the time out itself); with several it is a
 * list the keyboard asks. Plain Kotlin, no Android types: the screen only draws [step] and calls the choices.
 *
 * Text that was selected when the chain was opened is used where it fits: as the first answer, the number,
 * the clock time or the amount.
 */
class AddFlow private constructor(
    private val taken: Set<String>,
    val clockBlanks: List<ClockBlank>,
    private val selected: String,
    private val now: LocalDateTime,
    private val locale: Locale,
    private val editing: Editing?,
) {

    /** Changing an existing chip instead of making a new one. */
    private sealed interface Editing {
        val chipIndex: Int

        data class OfBlank(val spec: BlankSpec, override val chipIndex: Int) : Editing
        data class OfReference(val blankId: Int, val offsetMinutes: Long, override val chipIndex: Int) : Editing
    }

    /** Where we are. Going back returns to an earlier one of these. */
    private data class Nav(
        val step: FlowStep = FlowStep.KIND,
        val top: BlankKind? = null,
        val time: TimeChoice? = null,
        val base: Int? = null,
        val unitChosen: RelativeUnit? = null,
    )

    private var nav = Nav()
    private val history = ArrayList<Nav>()

    // What has been typed or chosen. Kept when going back.
    private var typedQuestion = ""
    private val typedAnswers = ArrayList<String>().apply { add("") }
    private var typedDigits = ""
    private val chosenAmounts = ArrayList<RelativeAmount>()
    private var chosenShow = RelativeShow.AMOUNT
    private var example = ""
    private var amountUnit = RelativeUnit.MINS
    private var isAfter = true

    var isDone: Boolean = false
        private set

    /** True after [cancel]: nothing is made and [applyTo] gives the message back as it was. */
    var isCancelled: Boolean = false
        private set

    // ---- what the screen draws ----

    val step: FlowStep get() = nav.step
    val isEditing: Boolean get() = editing != null
    val question: String get() = typedQuestion
    val answers: List<String> get() = typedAnswers
    val digits: String get() = typedDigits
    val amounts: List<RelativeAmount> get() = chosenAmounts
    val show: RelativeShow get() = chosenShow

    /** The unit of the number being typed. */
    val unit: RelativeUnit get() = amountUnit

    /** True when a time counted from another time is being made, "after" (the default) or "before". */
    val after: Boolean get() = isAfter

    /** The time the new part counts from, for [FlowStep.AFTER_NUMBER]. */
    val base: ClockBlank? get() = clockBlanks.firstOrNull { it.id == nav.base }

    /** The kind chosen at the first screen, or null before that. */
    val top: BlankKind? get() = nav.top

    /** The time choice, or null before it is made. */
    val timeChoice: TimeChoice? get() = nav.time

    /** The path taken so far, e.g. "A time", "From now", "Mins". Empty when editing. */
    fun crumbs(): List<Crumb> = buildList {
        if (editing != null) return@buildList
        nav.top?.let { add(Crumb.Kind(it)) }
        nav.time?.let { add(Crumb.Time(it)) }
        nav.unitChosen?.let { add(Crumb.Unit(it)) }
    }

    /** The units the unit screen offers: days only make sense for "how long", not for a time after another. */
    fun availableUnits(): List<RelativeUnit> =
        if (nav.time == TimeChoice.AFTER_OTHER) listOf(RelativeUnit.MINS, RelativeUnit.HOURS) else RelativeUnit.entries.toList()

    /** The kind of part being made, or null for a time counted from another (that is not a new part). */
    fun kind(): BlankKind? = when (nav.top) {
        null -> editing?.let { (it as? Editing.OfBlank)?.spec?.kind }
        BlankKind.TIME -> when (nav.time) {
            null, TimeChoice.CLOCK -> BlankKind.TIME
            TimeChoice.FROM_NOW -> BlankKind.TIME_AHEAD
            TimeChoice.AGO -> BlankKind.TIME_AGO
            TimeChoice.AFTER_OTHER -> null
        }
        else -> nav.top
    }

    /** The amount being typed, or null when the box is empty or the number is not 1 to 999. */
    val pendingAmount: RelativeAmount? get() = typedDigits.toIntOrNull()?.let { RelativeAmount.of(it, amountUnit) }

    /** The three examples for [FlowStep.SHOW], worked out from the time now and the first amount. */
    fun showExamples(): Map<RelativeShow, String> {
        val amount = chosenAmounts.firstOrNull() ?: pendingAmount ?: RelativeAmount(10, RelativeUnit.MINS)
        val ahead = kind() != BlankKind.TIME_AGO
        return RelativeShow.entries.associateWith { amount.phrase(ahead, it, now, locale) }
    }

    /**
     * Where this screen is in a path of several screens: (this screen's number, how many there are), or null when
     * there is only one screen or the path is not known yet (right after choosing a time). Used for the progress bar.
     */
    fun progress(): Pair<Int, Int>? {
        val position = history.size + 1
        val total: Int? = when {
            isEditing -> if (nav.step == FlowStep.AMOUNTS || nav.step == FlowStep.SHOW) 2 else 1
            nav.top == BlankKind.LIST -> 2
            nav.top == BlankKind.TIME -> when (nav.time) {
                null -> null
                TimeChoice.CLOCK -> 2
                TimeChoice.FROM_NOW, TimeChoice.AGO -> 5
                TimeChoice.AFTER_OTHER -> if (clockBlanks.size > 1) 5 else 4
            }
            else -> 1
        }
        return total?.takeIf { it >= 2 }?.let { position.coerceAtMost(it) to it }
    }

    /** True when the main button of this screen can be pressed. */
    val canAdvance: Boolean
        get() = when (nav.step) {
            FlowStep.LIST_QUESTION, FlowStep.QUESTION -> true
            FlowStep.LIST_ANSWERS -> typedAnswers.any { it.isNotBlank() } || (editing as? Editing.OfBlank)?.spec?.textType == true
            FlowStep.AMOUNTS -> chosenAmounts.isNotEmpty() || pendingAmount != null
            // Changing an existing one: keep what it already shows without tapping it again.
            FlowStep.SHOW -> isEditing
            FlowStep.AFTER_NUMBER -> pendingAmount != null
            else -> false
        }

    // ---- the choices ----

    /** First screen: a list, a time, a date or a number. */
    fun chooseKind(kind: BlankKind) {
        if (nav.step != FlowStep.KIND || kind !in BlankKind.topLevel) return
        resetContent()
        val text = selected.trim()
        when (kind) {
            BlankKind.LIST -> if (text.isNotEmpty()) typedAnswers[0] = text
            BlankKind.NUMBER -> numberIn(text)?.let { example = it }
            else -> Unit
        }
        go(Nav(top = kind, step = when (kind) {
            BlankKind.LIST -> FlowStep.LIST_ANSWERS
            BlankKind.TIME -> FlowStep.TIME_KIND
            else -> FlowStep.QUESTION
        }))
    }

    /** Second screen for a time. */
    fun chooseTime(choice: TimeChoice) {
        if (nav.step != FlowStep.TIME_KIND) return
        if (choice == TimeChoice.AFTER_OTHER && clockBlanks.isEmpty()) return
        val text = selected.trim()
        when (choice) {
            TimeChoice.CLOCK -> Template.parseTime(text)?.let { example = Template.formatTimeValue(it) }
            TimeChoice.FROM_NOW, TimeChoice.AGO, TimeChoice.AFTER_OTHER -> RelativeAmount.parse(text)?.let {
                if (typedDigits.isEmpty() && chosenAmounts.isEmpty()) {
                    typedDigits = it.n.toString()
                    amountUnit = it.unit
                }
            }
        }
        val next = when (choice) {
            TimeChoice.CLOCK -> nav.copy(time = choice, step = FlowStep.QUESTION)
            TimeChoice.FROM_NOW, TimeChoice.AGO -> nav.copy(time = choice, step = FlowStep.UNIT)
            TimeChoice.AFTER_OTHER ->
                if (clockBlanks.size == 1) nav.copy(time = choice, base = clockBlanks.first().id, step = FlowStep.UNIT)
                else nav.copy(time = choice, step = FlowStep.BASE)
        }
        go(next)
    }

    /** Which time to count from, when there is more than one. */
    fun chooseBase(blankId: Int) {
        if (nav.step != FlowStep.BASE || clockBlanks.none { it.id == blankId }) return
        go(nav.copy(base = blankId, step = FlowStep.UNIT))
    }

    /** Mins, hours or days. */
    fun chooseUnit(unit: RelativeUnit) {
        if (nav.step != FlowStep.UNIT || unit !in availableUnits()) return
        amountUnit = unit
        go(nav.copy(unitChosen = unit, step = if (nav.time == TimeChoice.AFTER_OTHER) FlowStep.AFTER_NUMBER else FlowStep.AMOUNTS))
    }

    /** Amount, time or both. Goes on to the question when there are several amounts, otherwise finishes. */
    fun chooseShow(show: RelativeShow) {
        if (nav.step != FlowStep.SHOW) return
        chosenShow = show
        // The last choice for a "how long": there is nothing left to ask (the name is automatic).
        isDone = true
    }

    // ---- typing ----

    fun setQuestion(text: String) {
        typedQuestion = text
    }

    fun setAnswer(index: Int, text: String) {
        if (index in typedAnswers.indices) typedAnswers[index] = text
    }

    /** A new empty row for another answer. */
    fun addAnswerRow() {
        typedAnswers.add("")
    }

    /** Takes a row out; there is always at least one. */
    fun removeAnswerRow(index: Int) {
        if (index !in typedAnswers.indices) return
        typedAnswers.removeAt(index)
        if (typedAnswers.isEmpty()) typedAnswers.add("")
    }

    /** What was typed in the number box: digits only, at most three. */
    fun typeNumber(text: String) {
        typedDigits = text.filter { it in '0'..'9' }.take(3)
    }

    /** Changes the unit of the number being typed (and keeps what was typed). */
    fun setUnit(unit: RelativeUnit) {
        if (unit in availableUnits()) amountUnit = unit
    }

    /** Takes the number typed as one more amount and empties the box. False when there is no valid number to take. */
    fun addAnotherAmount(): Boolean {
        if (nav.step != FlowStep.AMOUNTS) return false
        val amount = pendingAmount ?: return false
        if (chosenAmounts.none { it.text() == amount.text() }) chosenAmounts += amount
        typedDigits = ""
        return true
    }

    fun removeAmount(index: Int) {
        if (index in chosenAmounts.indices) chosenAmounts.removeAt(index)
    }

    /** Time after (true) or before (false) the other time. */
    fun setAfter(after: Boolean) {
        isAfter = after
    }

    // ---- moving ----

    /** The main button of the screen: goes on, or finishes. Does nothing when [canAdvance] is false. */
    fun advance() {
        if (!canAdvance) return
        when (nav.step) {
            FlowStep.LIST_QUESTION -> go(nav.copy(step = FlowStep.LIST_ANSWERS))
            FlowStep.LIST_ANSWERS, FlowStep.QUESTION, FlowStep.AFTER_NUMBER, FlowStep.SHOW -> isDone = true
            FlowStep.AMOUNTS -> {
                addAnotherAmount()
                go(nav.copy(step = FlowStep.SHOW))
            }
            else -> Unit
        }
    }

    /** One level back. False when already on the first level: the screen should then close the chain. */
    fun back(): Boolean {
        if (isDone) {
            isDone = false
            return true
        }
        val previous = history.removeLastOrNull() ?: return false
        nav = previous
        isDone = false
        return true
    }

    /** True when [back] has a level to go to. */
    val canGoBack: Boolean get() = isDone || history.isNotEmpty()

    /** Gives up: the half-made chip goes away. */
    fun cancel() {
        isCancelled = true
        isDone = false
    }

    // ---- what it makes ----

    /** The finished part, or null until the chain is done. */
    fun result(): AddResult? {
        if (!isDone || isCancelled) return null
        if (nav.top == BlankKind.TIME && nav.time == TimeChoice.AFTER_OTHER || editing is Editing.OfReference) return reference()
        return AddResult.Blank(buildSpec())
    }

    /** What the half-made chip should look like now; null until a kind is chosen (when making a new part). */
    fun preview(): ChipPreview? {
        if (isCancelled) return null
        if (editing is Editing.OfReference || nav.top == BlankKind.TIME && nav.time == TimeChoice.AFTER_OTHER && nav.base != null) {
            val r = reference()
            return ChipPreview.Reference(r.blankId, r.offsetMinutes)
        }
        if (nav.top == null && editing == null) return null
        val spec = buildSpec()
        val named = when {
            editing != null -> true
            spec.kind.isRelative -> spec.amounts.isNotEmpty()
            else -> typedQuestion.isNotBlank()
        }
        return ChipPreview.Blank(spec, named)
    }

    /**
     * [base] (the message before the chain was opened) with the half-made or finished part in it: a new chip at
     * [offset], or the chip being changed updated. [base] without a chip before a kind is chosen.
     */
    fun applyTo(base: MessageDraft, offset: Int): MessageDraft {
        val chip = preview() ?: return base
        return when (editing) {
            is Editing.OfBlank -> (chip as? ChipPreview.Blank)?.let { base.updateBlank(editing.spec.id, it.spec, now, locale) } ?: base
            is Editing.OfReference -> (chip as? ChipPreview.Reference)?.let { base.setChipOffset(editing.chipIndex, it.offsetMinutes, now, locale) } ?: base
            null -> when (chip) {
                is ChipPreview.Blank -> base.insertBlank(offset, chip.spec, now, locale)
                is ChipPreview.Reference -> base.insertReference(offset, chip.blankId, chip.offsetMinutes, now, locale)
            }
        }
    }

    // ---- inside ----

    private fun go(next: Nav) {
        history += nav
        nav = next
        // Only a list asks for a name while making. The rest get their default name; tapping the pill renames.
        if (next.step == FlowStep.QUESTION && !isEditing) isDone = true
    }

    private fun resetContent() {
        typedQuestion = ""
        typedAnswers.clear()
        typedAnswers.add("")
        typedDigits = ""
        chosenAmounts.clear()
        chosenShow = RelativeShow.AMOUNT
        example = ""
        isAfter = true
        amountUnit = RelativeUnit.MINS
    }

    private fun reference(): AddResult.Reference {
        val id = (editing as? Editing.OfReference)?.blankId ?: nav.base ?: clockBlanks.first().id
        val minutes = pendingAmount?.minutes ?: 0L
        return AddResult.Reference(id, if (isAfter) minutes else -minutes)
    }

    private fun buildSpec(): BlankSpec {
        val original = (editing as? Editing.OfBlank)?.spec
        val kind = kind() ?: BlankKind.TIME
        val others = taken
        val answers = ShortcutDraft.cleanOptions(typedAnswers)
        // Amounts typed so far, with the number in the box counted too so the chip follows what is being typed.
        val amounts = (chosenAmounts + listOfNotNull(pendingAmount.takeIf { nav.step == FlowStep.AMOUNTS || nav.step == FlowStep.UNIT }))
            .distinctBy { it.text() }
        val asked = amounts.size >= 2
        val typed = typedQuestion.trim()
        val wanted = when {
            kind.isRelative && !asked -> original?.question ?: BlankQuestions.default(kind)
            typed.isNotEmpty() -> typed
            else -> original?.question ?: BlankQuestions.default(kind)
        }
        val question = BlankQuestions.clean(wanted, kind, others)
        val base = original?.copy(question = question, kind = kind) ?: BlankSpec(0, question, kind)
        return when (kind) {
            BlankKind.LIST -> {
                val first = answers.firstOrNull().orEmpty()
                val keepExample = original != null && original.example.isNotBlank() && answers.any { it.equals(original.example, true) }
                base.copy(
                    answers = answers,
                    example = if (keepExample) original!!.example else first,
                    lastValue = original?.lastValue?.takeIf { v -> answers.any { it.equals(v, true) } } ?: first.ifBlank { null },
                )
            }
            BlankKind.TIME -> base.copy(example = example.ifBlank { original?.example.orEmpty() })
            BlankKind.DATE -> base
            BlankKind.NUMBER -> base.copy(
                example = example.ifBlank { original?.example.orEmpty() },
                lastValue = example.ifBlank { null } ?: original?.lastValue,
            )
            BlankKind.TIME_AHEAD, BlankKind.TIME_AGO -> base.copy(amounts = amounts, show = chosenShow)
        }
    }

    // ---- saving the state (the phone may be turned) ----

    fun toJson(): String {
        val o = JSONObject()
        o.put("step", nav.step.name)
        o.put("top", nav.top?.name ?: JSONObject.NULL)
        o.put("time", nav.time?.name ?: JSONObject.NULL)
        o.put("base", nav.base ?: JSONObject.NULL)
        o.put("unitChosen", nav.unitChosen?.name ?: JSONObject.NULL)
        val hist = JSONArray()
        for (h in history) {
            hist.put(
                JSONObject().put("step", h.step.name).put("top", h.top?.name ?: JSONObject.NULL).put("time", h.time?.name ?: JSONObject.NULL)
                    .put("base", h.base ?: JSONObject.NULL).put("unitChosen", h.unitChosen?.name ?: JSONObject.NULL),
            )
        }
        o.put("history", hist)
        o.put("question", typedQuestion)
        o.put("answers", JSONArray(typedAnswers))
        o.put("digits", typedDigits)
        o.put("amounts", JSONArray(chosenAmounts.map { it.text() }))
        o.put("show", chosenShow.id)
        o.put("example", example)
        o.put("unit", amountUnit.name)
        o.put("after", isAfter)
        o.put("done", isDone)
        o.put("cancelled", isCancelled)
        o.put("selected", selected)
        o.put("taken", JSONArray(taken.toList()))
        o.put("clock", JSONArray(clockBlanks.map { JSONObject().put("id", it.id).put("q", it.question) }))
        when (editing) {
            is Editing.OfBlank -> o.put("editBlank", MessageDraft.specToJson(editing.spec)).put("editChip", editing.chipIndex)
            is Editing.OfReference -> o.put("editRefBlank", editing.blankId).put("editRefOffset", editing.offsetMinutes).put("editChip", editing.chipIndex)
            null -> Unit
        }
        return o.toString()
    }

    companion object {
        /** The longest number of digits an amount can have. */
        const val MAX_DIGITS = 3

        /**
         * Starts a new chain.
         *
         * @param taken the questions already used in the message (the new one must differ).
         * @param clockBlanks the clock-time parts in the message, for "After another time".
         * @param selected the words that were selected in the message, or "".
         */
        fun start(
            taken: Set<String>,
            clockBlanks: List<ClockBlank>,
            selected: String = "",
            now: LocalDateTime = LocalDateTime.now(),
            locale: Locale = Locale.getDefault(),
        ): AddFlow = AddFlow(taken, clockBlanks, selected, now, locale, null)

        /**
         * Starts a chain for a chip that is already in the message, filled in with what it is now. It begins at the
         * first thing that can be changed: the question (list), the amounts ("how long") or the question (the rest).
         * Changing the kind means removing the chip and adding another.
         *
         * @param taken the questions used by the OTHER parts.
         */
        fun edit(
            spec: BlankSpec,
            chipIndex: Int,
            taken: Set<String>,
            clockBlanks: List<ClockBlank> = emptyList(),
            now: LocalDateTime = LocalDateTime.now(),
            locale: Locale = Locale.getDefault(),
        ): AddFlow {
            val flow = AddFlow(taken, clockBlanks, "", now, locale, Editing.OfBlank(spec, chipIndex))
            flow.typedQuestion = spec.question
            flow.typedAnswers.clear()
            flow.typedAnswers.addAll(spec.answers.ifEmpty { listOf("") })
            flow.chosenAmounts.addAll(spec.amounts)
            flow.chosenShow = spec.show
            flow.example = spec.example
            flow.amountUnit = spec.amounts.firstOrNull()?.unit ?: RelativeUnit.MINS
            flow.nav = Nav(
                step = when {
                    spec.kind == BlankKind.LIST -> FlowStep.LIST_ANSWERS
                    spec.kind.isRelative -> FlowStep.AMOUNTS
                    else -> FlowStep.QUESTION
                },
                top = null,
            )
            return flow
        }

        /** Starts a chain for a time counted from another time ("When + 4 hours"). */
        fun editReference(
            blankId: Int,
            offsetMinutes: Long,
            chipIndex: Int,
            clockBlanks: List<ClockBlank>,
            now: LocalDateTime = LocalDateTime.now(),
            locale: Locale = Locale.getDefault(),
        ): AddFlow {
            val flow = AddFlow(emptySet(), clockBlanks, "", now, locale, Editing.OfReference(blankId, offsetMinutes, chipIndex))
            val size = Math.abs(offsetMinutes)
            val unit = if (size % 60 == 0L && size >= 60) RelativeUnit.HOURS else RelativeUnit.MINS
            val n = (size / unit.minutes).toInt()
            flow.amountUnit = unit
            flow.typedDigits = if (n in 1..RelativeAmount.MAX) n.toString() else ""
            flow.isAfter = offsetMinutes >= 0
            flow.nav = Nav(step = FlowStep.AFTER_NUMBER, top = null, time = TimeChoice.AFTER_OTHER, base = blankId)
            return flow
        }

        /** Rebuilds a chain saved with [toJson], or null if the text is not valid. */
        fun fromJson(text: String, now: LocalDateTime = LocalDateTime.now(), locale: Locale = Locale.getDefault()): AddFlow? = runCatching {
            val o = JSONObject(text)
            fun navOf(j: JSONObject) = Nav(
                step = FlowStep.valueOf(j.getString("step")),
                top = if (j.isNull("top")) null else BlankKind.valueOf(j.getString("top")),
                time = if (j.isNull("time")) null else TimeChoice.valueOf(j.getString("time")),
                base = if (j.isNull("base")) null else j.getInt("base"),
                unitChosen = if (j.isNull("unitChosen")) null else RelativeUnit.valueOf(j.getString("unitChosen")),
            )
            fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
            val clock = o.getJSONArray("clock").let { a -> (0 until a.length()).map { ClockBlank(a.getJSONObject(it).getInt("id"), a.getJSONObject(it).getString("q")) } }
            val editing: Editing? = when {
                o.has("editBlank") -> Editing.OfBlank(MessageDraft.specFromJson(o.getJSONObject("editBlank")), o.getInt("editChip"))
                o.has("editRefBlank") -> Editing.OfReference(o.getInt("editRefBlank"), o.getLong("editRefOffset"), o.getInt("editChip"))
                else -> null
            }
            val flow = AddFlow(strings(o.getJSONArray("taken")).toSet(), clock, o.getString("selected"), now, locale, editing)
            flow.nav = navOf(o)
            val hist = o.getJSONArray("history")
            for (i in 0 until hist.length()) flow.history += navOf(hist.getJSONObject(i))
            flow.typedQuestion = o.getString("question")
            flow.typedAnswers.clear()
            flow.typedAnswers.addAll(strings(o.getJSONArray("answers")).ifEmpty { listOf("") })
            flow.typedDigits = o.getString("digits")
            flow.chosenAmounts.addAll(strings(o.getJSONArray("amounts")).mapNotNull { RelativeAmount.parse(it) })
            flow.chosenShow = RelativeShow.fromId(o.getString("show"))
            flow.example = o.getString("example")
            flow.amountUnit = RelativeUnit.valueOf(o.getString("unit"))
            flow.isAfter = o.getBoolean("after")
            flow.isDone = o.getBoolean("done")
            flow.isCancelled = o.optBoolean("cancelled", false)
            flow
        }.getOrNull()

        /** The number at the start of [text] ("5ml" gives "5", "£12.50" gives "12.50"), or null. */
        internal fun numberIn(text: String): String? = Regex("""[0-9]+(?:[.,][0-9]+)?""").find(text)?.value
    }
}

package io.github.selementpecher.shortcutkeyboard.ui.editor

import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template
import io.github.selementpecher.shortcutkeyboard.ui.ShortcutDraft
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** One piece of the message: plain words, a changing word, or a built-in like the time now. */
sealed interface Segment {
    data class Text(val text: String) : Segment

    /**
     * A changing word. [shown] is the example text it stands for in the message, e.g. "00:20" for
     * a time four hours after "20:20".
     */
    data class Ref(
        val blankId: Int,
        val offsetMinutes: Long = 0,
        val format: String? = null,
        val shown: String,
    ) : Segment

    /** `{now}` or `{today}` from an older shortcut: kept as it is, not asked about. */
    data class Fixed(val raw: String, val shown: String) : Segment
}

/**
 * What the owner chose for one changing word.
 *
 * @property question what the keyboard asks, e.g. "Who". Also the name it is stored under.
 * @property example the word the owner tapped, e.g. "Sam" or "20:20".
 * @property textType an old "type it in" blank: still saved as one so nothing changes behind the owner's back.
 */
data class BlankSpec(
    val id: Int,
    val question: String,
    val kind: BlankKind,
    val answers: List<String> = emptyList(),
    val example: String = "",
    val remember: Boolean = true,
    val textType: Boolean = false,
    val lastValue: String? = null,
)

/** Where a run of words sits: [start] to [end] (not included) inside text segment number [segment]. */
data class Selection(val segment: Int, val start: Int, val end: Int)

/** What the message looks like when drawn as tappable words. */
sealed interface Piece {
    /** Spaces and punctuation between words. */
    data class Gap(val text: String) : Piece

    data class Word(val selection: Selection, val text: String) : Piece

    /** A changing word. [blankId] is null for a built-in. */
    data class Chip(val segment: Int, val blankId: Int?, val offsetMinutes: Long, val shown: String) : Piece
}

/** A time word that could be worked out from a time already in the message. */
data class TimeRelation(
    val blankId: Int,
    val question: String,
    /** Minutes later, 0 until 23h59. */
    val afterMinutes: Long,
    /** Minutes earlier, as a number below zero (or 0). */
    val beforeMinutes: Long,
)

/** One line of the check screen: a question the keyboard will ask. */
data class AskedQuestion(val blankId: Int, val question: String, val kind: BlankKind, val answerCount: Int)

/** The message after the owner re-typed it. [lost] changing words could not be found again. */
data class Remapped(val draft: MessageDraft, val lost: Int)

/**
 * A message being written, with the words that change marked. Immutable: every change returns a
 * new draft.
 *
 * The shortcut still stores a template with `{name}` and `{name+4h}`; this class builds it
 * ([toShortcut]) and reads it back ([fromShortcut]), so those brackets never show on screen.
 *
 * Plain Kotlin with no Android types, so it can be unit tested.
 */
data class MessageDraft(
    val segments: List<Segment>,
    val blanks: List<BlankSpec> = emptyList(),
    private val nextId: Int = (blanks.maxOfOrNull { it.id } ?: 0) + 1,
) {

    fun blank(id: Int?): BlankSpec? = blanks.firstOrNull { it.id == id }

    /** The message as plain words, with each changing word as its example. */
    fun plainText(): String = segments.joinToString("") {
        when (it) {
            is Segment.Text -> it.text
            is Segment.Ref -> it.shown
            is Segment.Fixed -> it.shown
        }
    }

    /** True when nothing in the message has been marked as changing. */
    val hasNoBlanks: Boolean get() = segments.none { it is Segment.Ref }

    /** The message with examples filled in, as the keyboard would first show it. */
    fun exampleMessage(): String = plainText()

    /** The questions the keyboard will ask, in the order the words appear. */
    fun questions(): List<AskedQuestion> =
        orderedBlanks().map { AskedQuestion(it.id, it.question, it.kind, if (it.kind == BlankKind.LIST) it.answers.size else 0) }

    /** The message cut into words, punctuation and changing-word chips. */
    fun pieces(): List<Piece> {
        val out = ArrayList<Piece>()
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is Segment.Text -> {
                    var at = 0
                    for (span in Words.spans(segment.text)) {
                        if (span.start > at) out += Piece.Gap(segment.text.substring(at, span.start))
                        out += Piece.Word(Selection(index, span.start, span.end), segment.text.substring(span.start, span.end))
                        at = span.end
                    }
                    if (at < segment.text.length) out += Piece.Gap(segment.text.substring(at))
                }
                is Segment.Ref -> out += Piece.Chip(index, segment.blankId, segment.offsetMinutes, segment.shown)
                is Segment.Fixed -> out += Piece.Chip(index, null, 0, segment.shown)
            }
        }
        return out
    }

    fun textOf(selection: Selection): String =
        (segments.getOrNull(selection.segment) as? Segment.Text)?.text
            ?.let { it.substring(selection.start.coerceIn(0, it.length), selection.end.coerceIn(0, it.length)) }
            .orEmpty()

    /** [selection] made one word longer at the front or the back, or null when there is no word left to add. */
    fun extend(selection: Selection, before: Boolean): Selection? {
        val text = (segments.getOrNull(selection.segment) as? Segment.Text)?.text ?: return null
        val spans = Words.spans(text)
        return if (before) {
            spans.lastOrNull { it.end <= selection.start }?.let { selection.copy(start = it.start) }
        } else {
            spans.firstOrNull { it.start >= selection.end }?.let { selection.copy(end = it.end) }
        }
    }

    /** [selection] made one word shorter at the front or the back, or null when only one word is left. */
    fun shrink(selection: Selection, fromStart: Boolean): Selection? {
        val text = (segments.getOrNull(selection.segment) as? Segment.Text)?.text ?: return null
        val inside = Words.spans(text).filter { it.start >= selection.start && it.end <= selection.end }
        if (inside.size < 2) return null
        return if (fromStart) selection.copy(start = inside[1].start) else selection.copy(end = inside[inside.size - 2].end)
    }

    /**
     * Turns the selected words into a changing word.
     *
     * @param question what to ask; cleaned and made different from the other questions.
     * @param moreAnswers for a list: further answers after the selected word, which is the first.
     */
    fun makeBlank(
        selection: Selection,
        kind: BlankKind,
        question: String,
        moreAnswers: List<String> = emptyList(),
    ): MessageDraft {
        val text = (segments.getOrNull(selection.segment) as? Segment.Text)?.text ?: return this
        val first = selection.start.coerceIn(0, text.length)
        var start = first
        var end = selection.end.coerceIn(start, text.length)
        if (start == end) return this
        if (kind == BlankKind.NUMBER) {
            // "5ml" becomes the number 5 followed by the plain word "ml".
            val chosen = text.substring(start, end)
            if (chosen.none { it.isWhitespace() }) {
                NUMBER_PART.find(chosen)?.let { start = first + it.range.first; end = first + it.range.last + 1 }
            }
        }
        val example = text.substring(start, end)
        val name = BlankQuestions.clean(question, kind, blanks.map { it.question }.toSet())
        val spec = BlankSpec(
            id = nextId,
            question = name,
            kind = kind,
            answers = if (kind == BlankKind.LIST) ShortcutDraft.cleanOptions(listOf(example) + moreAnswers) else emptyList(),
            example = example,
            lastValue = example,
        )
        val ref = Segment.Ref(spec.id, shown = example)
        return copy(
            segments = replaceSegment(selection.segment, text.substring(0, start), ref, text.substring(end)),
            blanks = blanks + spec,
            nextId = nextId + 1,
        )
    }

    /**
     * When the selected word is a time, the times already in the message it could be counted from:
     * "20:20" in a message that already has a time of 16:20 is four hours later.
     */
    fun timeRelations(selection: Selection): List<TimeRelation> {
        val word = textOf(selection)
        if (!CLOCK_WORD.matches(word)) return emptyList()
        val later = Template.parseTime(word) ?: return emptyList()
        return orderedBlanks().filter { it.kind == BlankKind.TIME }.mapNotNull { spec ->
            val base = Template.parseTime(spec.example) ?: return@mapNotNull null
            val after = minutesBetween(base, later)
            TimeRelation(spec.id, spec.question, after, if (after == 0L) 0 else after - MINUTES_PER_DAY)
        }
    }

    /** Makes the selected word the time of blank [blankId] moved by [offsetMinutes], instead of a new blank. */
    fun reuseTimeLater(selection: Selection, blankId: Int, offsetMinutes: Long): MessageDraft {
        val text = (segments.getOrNull(selection.segment) as? Segment.Text)?.text ?: return this
        if (blank(blankId)?.kind != BlankKind.TIME) return this
        val start = selection.start.coerceIn(0, text.length)
        val end = selection.end.coerceIn(start, text.length)
        if (start == end) return this
        val ref = Segment.Ref(blankId, offsetMinutes, shown = text.substring(start, end))
        return copy(segments = replaceSegment(selection.segment, text.substring(0, start), ref, text.substring(end)))
    }

    /** Puts every use of the blank back to plain words (its examples). */
    fun unmakeBlank(blankId: Int): MessageDraft {
        val spec = blank(blankId) ?: return this
        return copy(
            segments = merge(segments.map { if (it is Segment.Ref && it.blankId == blankId) Segment.Text(plainWord(spec, it)) else it }),
            blanks = blanks.filter { it.id != blankId },
        )
    }

    /** Puts one use of a changing word (the chip at [segment]) back to plain words. */
    fun unmakeRef(segment: Int): MessageDraft {
        val ref = segments.getOrNull(segment) as? Segment.Ref ?: return this
        val spec = blank(ref.blankId) ?: return this
        val replaced = segments.mapIndexed { i, s -> if (i == segment) Segment.Text(plainWord(spec, ref)) else s }
        return copy(segments = merge(replaced)).dropUnused()
    }

    fun renameQuestion(blankId: Int, question: String): MessageDraft {
        val spec = blank(blankId) ?: return this
        val others = blanks.filter { it.id != blankId }.map { it.question }.toSet()
        val name = BlankQuestions.clean(question, spec.kind, others)
        return replaceBlank(spec.copy(question = name))
    }

    /** Sets the answers of a list. The tapped word stays where the owner put it; blanks and repeats are dropped. */
    fun setAnswers(blankId: Int, answers: List<String>): MessageDraft {
        val spec = blank(blankId) ?: return this
        if (spec.kind != BlankKind.LIST) return this
        return replaceBlank(spec.copy(answers = ShortcutDraft.cleanOptions(answers)))
    }

    fun setRemember(blankId: Int, remember: Boolean): MessageDraft {
        val spec = blank(blankId) ?: return this
        return replaceBlank(spec.copy(remember = remember))
    }

    /**
     * The owner re-typed the message. Each changing word is looked for again by its example: the
     * next whole word, in order, that matches. Words not found are counted in [Remapped.lost] and
     * need tapping again.
     */
    fun withNewText(newText: String): Remapped {
        if (newText == plainText()) return Remapped(this, 0)
        val out = ArrayList<Segment>()
        val matchedIds = HashSet<Int>()
        var cursor = 0
        var lost = 0
        for (segment in segments) {
            if (segment is Segment.Text) continue
            val candidates = candidatesFor(segment)
            var found: Pair<Int, String>? = null
            for (candidate in candidates) {
                val at = indexOfWholeWord(newText, candidate, cursor)
                if (at >= 0) { found = at to candidate; break }
            }
            if (found == null) { lost++; continue }
            val (at, matched) = found
            if (at > cursor) out += Segment.Text(newText.substring(cursor, at))
            out += when (segment) {
                is Segment.Ref -> { matchedIds += segment.blankId; segment.copy(shown = newText.substring(at, at + matched.length)) }
                is Segment.Fixed -> segment.copy(shown = newText.substring(at, at + matched.length))
                is Segment.Text -> segment
            }
            cursor = at + matched.length
        }
        if (cursor < newText.length) out += Segment.Text(newText.substring(cursor))
        val draft = copy(segments = merge(out), blanks = blanks.filter { it.id in matchedIds })
        return Remapped(draft, lost)
    }

    /**
     * The shortcut to save. [id], [parentId], [order] and [icon] are carried through; only the
     * changing words that are still in the message are kept, in the order they appear.
     */
    fun toShortcut(id: String, name: String, parentId: String? = null, order: Int = 0, icon: String = "", color: Int = -1): Shortcut {
        val template = StringBuilder()
        for (segment in segments) {
            when (segment) {
                is Segment.Text -> template.append(escape(segment.text))
                is Segment.Ref -> {
                    val spec = blank(segment.blankId)
                    if (spec == null) template.append(escape(segment.shown)) else template.append(token(spec.question, segment))
                }
                is Segment.Fixed -> template.append('{').append(segment.raw).append('}')
            }
        }
        return Shortcut(
            id = id,
            name = name.trim(),
            template = template.toString(),
            variables = orderedBlanks().map { it.toVariable() },
            parentId = parentId,
            order = order,
            icon = icon,
            color = color,
        )
    }

    // ---- JSON, to survive turning the phone ----

    fun toJson(): String {
        val segs = JSONArray()
        for (s in segments) {
            segs.put(
                when (s) {
                    is Segment.Text -> JSONObject().put("t", "text").put("v", s.text)
                    is Segment.Ref -> JSONObject().put("t", "ref").put("id", s.blankId).put("off", s.offsetMinutes)
                        .put("fmt", s.format ?: JSONObject.NULL).put("shown", s.shown)
                    is Segment.Fixed -> JSONObject().put("t", "fixed").put("raw", s.raw).put("shown", s.shown)
                },
            )
        }
        val specs = JSONArray()
        for (b in blanks) {
            specs.put(
                JSONObject().put("id", b.id).put("q", b.question).put("kind", b.kind.name)
                    .put("answers", JSONArray(b.answers)).put("example", b.example).put("remember", b.remember)
                    .put("text", b.textType).put("last", b.lastValue ?: JSONObject.NULL),
            )
        }
        return JSONObject().put("segments", segs).put("blanks", specs).put("next", nextId).toString()
    }

    // ---- inside ----

    private fun orderedBlanks(): List<BlankSpec> {
        val seen = LinkedHashSet<Int>()
        for (s in segments) if (s is Segment.Ref) seen += s.blankId
        return seen.mapNotNull { blank(it) }
    }

    private fun replaceBlank(spec: BlankSpec) = copy(blanks = blanks.map { if (it.id == spec.id) spec else it })

    private fun dropUnused(): MessageDraft {
        val used = segments.filterIsInstance<Segment.Ref>().map { it.blankId }.toSet()
        return copy(blanks = blanks.filter { it.id in used })
    }

    private fun replaceSegment(index: Int, before: String, middle: Segment, after: String): List<Segment> {
        val out = ArrayList<Segment>()
        segments.forEachIndexed { i, s ->
            if (i == index) {
                if (before.isNotEmpty()) out += Segment.Text(before)
                out += middle
                if (after.isNotEmpty()) out += Segment.Text(after)
            } else out += s
        }
        return out
    }

    private fun plainWord(spec: BlankSpec, ref: Segment.Ref): String =
        if (ref.shown.isBlank() || ref.shown == "[${spec.question}]") spec.question else ref.shown

    private fun candidatesFor(segment: Segment): List<String> = when (segment) {
        is Segment.Fixed -> listOf(segment.shown)
        is Segment.Ref -> {
            val spec = blank(segment.blankId)
            buildList {
                add(segment.shown)
                if (spec != null && segment.offsetMinutes == 0L) {
                    add(spec.example)
                    addAll(spec.answers)
                }
            }.filter { it.isNotBlank() }.distinct()
        }
        is Segment.Text -> emptyList()
    }

    private fun BlankSpec.toVariable(): VariableDef = when (kind) {
        BlankKind.LIST -> VariableDef(question, if (textType) VariableType.TEXT else VariableType.CHOICE, answers, remember, lastValue)
        BlankKind.TIME -> VariableDef(question, VariableType.TIME, emptyList(), true, example.ifBlank { null })
        BlankKind.DATE -> VariableDef(question, VariableType.DATE, emptyList(), true, example.ifBlank { null })
        BlankKind.NUMBER -> VariableDef(question, VariableType.NUMBER, emptyList(), true, lastValue ?: example.ifBlank { null })
    }

    companion object {
        private const val MINUTES_PER_DAY = 24L * 60L
        private val NUMBER_PART = Regex("""[0-9]+(?:[.,][0-9]+)?""")
        private val CLOCK_WORD = Regex("""\d{1,2}[:.]\d{2}""")

        /** A new draft from a message typed in plain words. */
        fun fromMessage(text: String): MessageDraft =
            MessageDraft(if (text.isEmpty()) emptyList() else listOf(Segment.Text(text)))

        /**
         * Reads a stored shortcut back. Old "type it in" blanks load as lists. A blank used more
         * than once stays one blank. [now] and [locale] only decide the example text of times and dates.
         */
        fun fromShortcut(
            shortcut: Shortcut,
            now: LocalDateTime = LocalDateTime.now(),
            locale: Locale = Locale.getDefault(),
        ): MessageDraft {
            val template = Template.parse(shortcut.template)
            val specs = LinkedHashMap<String, BlankSpec>()
            var id = 1
            for (name in template.variableNames) {
                val def = shortcut.variable(name)
                    ?: VariableDef(name, Template.guessType(name, template.usesOffset(name)))
                specs[VariableDef.keyOf(name)] = specFor(id++, name, def)
            }
            val segments = ArrayList<Segment>()
            for (part in template.parts) {
                when (part) {
                    is Template.Part.Text -> segments += Segment.Text(part.text)
                    is Template.Part.Ref -> {
                        val spec = specs[part.key]
                        if (spec == null) {
                            segments += Segment.Fixed(part.raw, renderBuiltIn(part, now, locale))
                        } else {
                            segments += Segment.Ref(spec.id, part.offsetMinutes, part.format, shownFor(spec, part, now, locale))
                        }
                    }
                }
            }
            return MessageDraft(merge(segments), specs.values.toList())
        }

        fun fromJson(text: String): MessageDraft? = runCatching {
            val root = JSONObject(text)
            val segments = ArrayList<Segment>()
            val segs = root.getJSONArray("segments")
            for (i in 0 until segs.length()) {
                val o = segs.getJSONObject(i)
                segments += when (o.getString("t")) {
                    "text" -> Segment.Text(o.getString("v"))
                    "ref" -> Segment.Ref(o.getInt("id"), o.optLong("off"), if (o.isNull("fmt")) null else o.getString("fmt"), o.getString("shown"))
                    else -> Segment.Fixed(o.getString("raw"), o.getString("shown"))
                }
            }
            val specs = ArrayList<BlankSpec>()
            val bs = root.getJSONArray("blanks")
            for (i in 0 until bs.length()) {
                val o = bs.getJSONObject(i)
                val answers = o.getJSONArray("answers").let { a -> (0 until a.length()).map { a.getString(it) } }
                specs += BlankSpec(
                    id = o.getInt("id"),
                    question = o.getString("q"),
                    kind = BlankKind.valueOf(o.getString("kind")),
                    answers = answers,
                    example = o.optString("example", ""),
                    remember = o.optBoolean("remember", true),
                    textType = o.optBoolean("text", false),
                    lastValue = if (o.isNull("last")) null else o.getString("last"),
                )
            }
            MessageDraft(segments, specs, root.getInt("next"))
        }.getOrNull()

        private fun specFor(id: Int, name: String, def: VariableDef): BlankSpec {
            val question = def.name.trim().ifEmpty { name }
            return when (def.type) {
                VariableType.CHOICE, VariableType.TEXT -> BlankSpec(
                    id, question, BlankKind.LIST, ShortcutDraft.cleanOptions(def.options),
                    example = def.lastValue?.takeIf { it.isNotBlank() } ?: def.options.firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                    remember = def.remember, textType = def.type == VariableType.TEXT, lastValue = def.lastValue,
                )
                VariableType.TIME -> BlankSpec(id, question, BlankKind.TIME, example = def.lastValue.orEmpty(), lastValue = def.lastValue)
                VariableType.DATE -> BlankSpec(id, question, BlankKind.DATE, example = def.lastValue.orEmpty(), lastValue = def.lastValue)
                VariableType.NUMBER -> BlankSpec(id, question, BlankKind.NUMBER, example = def.lastValue.orEmpty(), lastValue = def.lastValue)
                // Placeholder until the editor knows the "how long" kinds: shown as a list of the saved amounts.
                VariableType.TIME_AHEAD, VariableType.TIME_AGO -> BlankSpec(
                    id, question, BlankKind.LIST, ShortcutDraft.cleanOptions(def.options),
                    example = def.options.firstOrNull().orEmpty(), lastValue = def.lastValue,
                )
            }
        }

        /** The example text for one use of a blank: what the owner typed, or a sample worked out from it. */
        private fun shownFor(spec: BlankSpec, ref: Template.Part.Ref, now: LocalDateTime, locale: Locale): String {
            if (ref.offsetMinutes == 0L && ref.format == null && spec.example.isNotBlank()) return spec.example
            val key = VariableDef.keyOf(spec.question)
            val values = HashMap<String, String>()
            val type: VariableType
            when (spec.kind) {
                BlankKind.TIME -> {
                    type = VariableType.TIME
                    val base = Template.parseTime(spec.example) ?: now.toLocalTime()
                    values[key] = Template.formatTimeValue(base)
                }
                BlankKind.DATE -> {
                    type = VariableType.DATE
                    values[key] = (Template.parseDate(spec.example) ?: now.toLocalDate()).toString()
                }
                else -> {
                    type = VariableType.TEXT
                    spec.example.takeIf { it.isNotBlank() }?.let { values[key] = it }
                }
            }
            val single = Template.parse(token(spec.question, ref.offsetMinutes, ref.format))
            return single.render(values, mapOf(key to type), now, locale) { "[${spec.question}]" }
        }

        private fun renderBuiltIn(ref: Template.Part.Ref, now: LocalDateTime, locale: Locale): String =
            Template.parse("{${ref.raw}}").render(emptyMap(), emptyMap(), now, locale) { ref.raw }

        private fun token(name: String, ref: Segment.Ref) = token(name, ref.offsetMinutes, ref.format)

        private fun token(name: String, offsetMinutes: Long, format: String?): String =
            "{" + name.trim() + Template.describeOffset(offsetMinutes) + (format?.let { "|$it" } ?: "") + "}"

        /** Literal curly brackets in the plain words must be doubled so they are not read as a blank. */
        private fun escape(text: String) = text.replace("{", "{{").replace("}", "}}")

        private fun merge(segments: List<Segment>): List<Segment> {
            val out = ArrayList<Segment>()
            for (s in segments) {
                val last = out.lastOrNull()
                if (s is Segment.Text && s.text.isEmpty()) continue
                if (s is Segment.Text && last is Segment.Text) out[out.size - 1] = Segment.Text(last.text + s.text) else out += s
            }
            return out
        }

        private fun minutesBetween(from: LocalTime, to: LocalTime): Long {
            val diff = (to.toSecondOfDay() - from.toSecondOfDay()) / 60
            return Math.floorMod(diff, 24 * 60).toLong()
        }

        /** The first place at or after [from] where [word] appears as a whole word, or -1. */
        internal fun indexOfWholeWord(text: String, word: String, from: Int): Int {
            var at = text.indexOf(word, from, ignoreCase = true)
            while (at >= 0) {
                val beforeOk = at == 0 || !(text[at - 1].isLetterOrDigit() && word.first().isLetterOrDigit())
                val end = at + word.length
                val afterOk = end >= text.length || !(text[end].isLetterOrDigit() && word.last().isLetterOrDigit())
                if (beforeOk && afterOk) return at
                at = text.indexOf(word, at + 1, ignoreCase = true)
            }
            return -1
        }
    }
}

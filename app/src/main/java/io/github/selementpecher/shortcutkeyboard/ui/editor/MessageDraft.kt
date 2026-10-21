package io.github.selementpecher.shortcutkeyboard.ui.editor

import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.RelativeAmount
import io.github.selementpecher.shortcutkeyboard.template.Template
import io.github.selementpecher.shortcutkeyboard.ui.ShortcutDraft
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.Locale

/** One piece of the message: plain words, a changing part (a chip), or a built-in like the time now. */
sealed interface Segment {
    data class Text(val text: String) : Segment

    /**
     * A changing part. [shown] is the example text it stands for in the message, e.g. "00:20" for
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

/** The example text a chip stands for. */
internal val Segment.shownText: String
    get() = when (this) {
        is Segment.Text -> text
        is Segment.Ref -> shown
        is Segment.Fixed -> shown
    }

/**
 * What the owner chose for one changing part.
 *
 * @property question what the keyboard asks, e.g. "Who". Also the name it is stored under.
 * @property example the example shown in the message, e.g. "Sam" or "20:20"; empty to work one out.
 * @property textType an old "type it in" blank: still saved as one so nothing changes behind the owner's back.
 * @property show for the "how long" kinds: print the amount, the clock time, or both.
 * @property amounts for the "how long" kinds: one amount is fixed (never asked), several are asked.
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
    val show: RelativeShow = RelativeShow.AMOUNT,
    val amounts: List<RelativeAmount> = emptyList(),
) {
    /** A "how long" part with one amount: the app works it out and never asks. */
    val fixed: Boolean get() = kind.isRelative && amounts.size == 1
}

/** One line of the check screen: a question the keyboard will ask (or a fixed part it will fill itself). */
data class AskedQuestion(
    val blankId: Int,
    val question: String,
    val kind: BlankKind,
    /** Answers of a list, or amounts of a "how long" part. 0 for the other kinds. */
    val answerCount: Int,
    /** For a fixed "how long" part: the amount it always uses, e.g. "10 mins". Not asked. */
    val fixedAmount: String? = null,
)

/**
 * A message being written, with the changing parts marked. Immutable: every change returns a new draft.
 *
 * In the text box every changing part is one placeholder character ([PLACEHOLDER]) carrying a chip, so
 * all positions here ("offsets") count a chip as one character. The shortcut itself still stores a
 * template with `{name}` and `{name+4h}`; this class builds it ([toShortcut]) and reads it back
 * ([fromShortcut]), so those brackets never show on screen.
 *
 * Plain Kotlin with no Android types, so it can be unit tested.
 */
data class MessageDraft(
    val segments: List<Segment>,
    val blanks: List<BlankSpec> = emptyList(),
    private val nextId: Int = (blanks.maxOfOrNull { it.id } ?: 0) + 1,
) {

    /** The id the next blank added to this draft will get. */
    val nextBlankId: Int get() = nextId

    fun blank(id: Int?): BlankSpec? = blanks.firstOrNull { it.id == id }

    /** The message as plain words, with each changing part as its example. */
    fun plainText(): String = segments.joinToString("") { it.shownText }

    /** True when nothing in the message has been marked as changing. */
    val hasNoBlanks: Boolean get() = segments.none { it is Segment.Ref }

    /** The questions the keyboard will ask (and the fixed parts), in the order the parts appear. */
    fun questions(): List<AskedQuestion> = orderedBlanks().map {
        AskedQuestion(
            blankId = it.id,
            question = it.question,
            kind = it.kind,
            answerCount = when {
                it.kind == BlankKind.LIST -> it.answers.size
                it.kind.isRelative -> it.amounts.size
                else -> 0
            },
            fixedAmount = if (it.fixed) it.amounts.first().text() else null,
        )
    }

    // ---- positions in the text box ----

    /** The text as the text box holds it: words, with one [PLACEHOLDER] for each chip. */
    fun placeholderText(): String = segments.joinToString("") { if (it is Segment.Text) it.text else PLACEHOLDER.toString() }

    /** How many characters the text box holds. */
    val length: Int get() = segments.sumOf { if (it is Segment.Text) it.text.length else 1 }

    /** The chips (everything that is not plain words), in order: the same order as the placeholders. */
    fun chips(): List<Segment> = segments.filter { it !is Segment.Text }

    /** Where chip number [index] sits in the text box. */
    fun chipOffset(index: Int): Int {
        var pos = 0
        var seen = 0
        for (s in segments) {
            if (s is Segment.Text) {
                pos += s.text.length
            } else {
                if (seen == index) return pos
                seen++
                pos++
            }
        }
        return -1
    }

    /** Which chip the placeholder at [offset] is, or null when that character is a plain letter. */
    fun chipIndexAt(offset: Int): Int? {
        var pos = 0
        var seen = 0
        for (s in segments) {
            if (s is Segment.Text) {
                pos += s.text.length
            } else {
                if (pos == offset) return seen
                seen++
                pos++
            }
        }
        return null
    }

    // ---- changing the message ----

    /**
     * Puts a new changing part at [offset] (a chip). The question is cleaned and made different from the others;
     * the draft gives it its own id ([nextBlankId] before the call).
     */
    fun insertBlank(offset: Int, spec: BlankSpec, now: LocalDateTime = LocalDateTime.now(), locale: Locale = Locale.getDefault()): MessageDraft {
        val id = nextId
        val question = BlankQuestions.clean(spec.question, spec.kind, blanks.map { it.question }.toSet())
        val full = spec.copy(id = id, question = question)
        val ref = Segment.Ref(id, shown = exampleFor(full, now, locale))
        val (before, after) = splitAt(offset.coerceIn(0, length))
        return copy(segments = merge(before + ref + after), blanks = blanks + full, nextId = nextId + 1)
    }

    /** Replaces the characters from [start] up to [end] (words and chips) with a new changing part. */
    fun replaceRange(start: Int, end: Int, spec: BlankSpec, now: LocalDateTime = LocalDateTime.now(), locale: Locale = Locale.getDefault()): MessageDraft {
        val a = start.coerceIn(0, length)
        val b = end.coerceIn(a, length)
        return deleteRange(a, b).insertBlank(a, spec, now, locale)
    }

    /** Removes the characters from [start] up to [end]. Parts that are no longer used are forgotten. */
    fun deleteRange(start: Int, end: Int): MessageDraft {
        val a = start.coerceIn(0, length)
        val b = end.coerceIn(a, length)
        if (a == b) return this
        val (before, rest) = splitAt(a)
        val (_, after) = MessageDraft(rest).splitAt(b - a)
        return copy(segments = merge(before + after)).dropUnused()
    }

    /** Changes a part: its question, answers, amounts... The examples shown for it are worked out again. */
    fun updateBlank(id: Int, spec: BlankSpec, now: LocalDateTime = LocalDateTime.now(), locale: Locale = Locale.getDefault()): MessageDraft {
        if (blank(id) == null) return this
        val others = blanks.filter { it.id != id }.map { it.question }.toSet()
        val full = spec.copy(id = id, question = BlankQuestions.clean(spec.question, spec.kind, others))
        return copy(
            segments = segments.map { s ->
                if (s is Segment.Ref && s.blankId == id) s.copy(shown = shownFor(full, s.offsetMinutes, s.format, now, locale)) else s
            },
            blanks = blanks.map { if (it.id == id) full else it },
        )
    }

    /** Takes a part out of the message, every place it is used. */
    fun removeBlank(id: Int): MessageDraft = copy(
        segments = merge(segments.filterNot { it is Segment.Ref && it.blankId == id }),
        blanks = blanks.filter { it.id != id },
    )

    /** Takes out the chip number [index] only. A part nothing uses any more is forgotten. */
    fun removeChip(index: Int): MessageDraft {
        var seen = 0
        val kept = segments.filter { s -> if (s is Segment.Text) true else (seen++ != index) }
        return copy(segments = merge(kept)).dropUnused()
    }

    /**
     * Puts a time counted from another time at [offset]: the time of blank [blankId] moved by [offsetMinutes]
     * (minutes later, or a number below zero for earlier). Does nothing if that is not a clock-time blank.
     */
    fun insertReference(
        offset: Int,
        blankId: Int,
        offsetMinutes: Long,
        now: LocalDateTime = LocalDateTime.now(),
        locale: Locale = Locale.getDefault(),
    ): MessageDraft {
        val spec = blank(blankId)
        if (spec == null || spec.kind != BlankKind.TIME) return this
        val ref = Segment.Ref(blankId, offsetMinutes, null, shownFor(spec, offsetMinutes, null, now, locale))
        val (before, after) = splitAt(offset.coerceIn(0, length))
        return copy(segments = merge(before + ref + after))
    }

    /** Moves chip number [index], a time counted from another, to a new offset. */
    fun setChipOffset(index: Int, offsetMinutes: Long, now: LocalDateTime = LocalDateTime.now(), locale: Locale = Locale.getDefault()): MessageDraft {
        var seen = 0
        return copy(
            segments = segments.map { s ->
                if (s is Segment.Text) s
                else if (seen++ == index && s is Segment.Ref) {
                    val spec = blank(s.blankId) ?: return@map s
                    s.copy(offsetMinutes = offsetMinutes, shown = shownFor(spec, offsetMinutes, s.format, now, locale))
                } else s
            },
        )
    }

    /**
     * The draft after the owner typed in the text box. [text] is what the box holds, [chips] the chips still in it,
     * in order (one for each [PLACEHOLDER]). A placeholder with no chip is dropped, a chip whose part is gone
     * becomes plain words, and parts nothing uses any more are forgotten.
     */
    fun withEditableText(text: String, chips: List<Segment>): MessageDraft {
        val out = ArrayList<Segment>()
        val plain = StringBuilder()
        var next = 0
        for (c in text) {
            if (c == PLACEHOLDER) {
                val chip = chips.getOrNull(next++) ?: continue
                if (plain.isNotEmpty()) { out += Segment.Text(plain.toString()); plain.setLength(0) }
                out += chip
            } else {
                plain.append(c)
            }
        }
        if (plain.isNotEmpty()) out += Segment.Text(plain.toString())
        return copy(segments = merge(out)).dropUnused()
    }

    /**
     * The shortcut to save. [id], [parentId], [order], [icon] and [color] are carried through; only the
     * parts that are still in the message are kept, in the order they appear.
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
        for (b in blanks) specs.put(specToJson(b))
        return JSONObject().put("segments", segs).put("blanks", specs).put("next", nextId).toString()
    }

    // ---- inside ----

    private fun orderedBlanks(): List<BlankSpec> {
        val seen = LinkedHashSet<Int>()
        for (s in segments) if (s is Segment.Ref) seen += s.blankId
        return seen.mapNotNull { blank(it) }
    }

    private fun dropUnused(): MessageDraft {
        val used = segments.filterIsInstance<Segment.Ref>().map { it.blankId }.toSet()
        return copy(blanks = blanks.filter { it.id in used })
    }

    /** The segments before and after [offset] (a chip counts as one character; a chip is never cut). */
    private fun splitAt(offset: Int): Pair<List<Segment>, List<Segment>> {
        val before = ArrayList<Segment>()
        val after = ArrayList<Segment>()
        var pos = 0
        for (s in segments) {
            val size = if (s is Segment.Text) s.text.length else 1
            when {
                pos + size <= offset -> before += s
                pos >= offset -> after += s
                else -> {
                    val cut = offset - pos
                    before += Segment.Text((s as Segment.Text).text.substring(0, cut))
                    after += Segment.Text(s.text.substring(cut))
                }
            }
            pos += size
        }
        return before to after
    }

    private fun BlankSpec.toVariable(): VariableDef = when (kind) {
        BlankKind.LIST -> VariableDef(question, if (textType) VariableType.TEXT else VariableType.CHOICE, answers, remember, lastValue)
        BlankKind.TIME -> VariableDef(question, VariableType.TIME, answers, true, example.ifBlank { null })
        BlankKind.DATE -> VariableDef(question, VariableType.DATE, answers, true, example.ifBlank { null })
        BlankKind.NUMBER -> VariableDef(question, VariableType.NUMBER, emptyList(), true, lastValue ?: example.ifBlank { null })
        BlankKind.TIME_AHEAD, BlankKind.TIME_AGO -> {
            val texts = amounts.map { it.text() }
            VariableDef(
                name = question,
                type = if (kind == BlankKind.TIME_AHEAD) VariableType.TIME_AHEAD else VariableType.TIME_AGO,
                options = texts,
                remember = remember,
                // The amount offered first: the last one used if it is still on the list, otherwise the first.
                lastValue = if (fixed) null else (lastValue?.takeIf { it in texts } ?: texts.firstOrNull()),
                show = show,
                fixed = fixed,
            )
        }
    }

    companion object {
        /** The one character in the text box that stands for a chip. */
        const val PLACEHOLDER = '￼'

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
                            segments += Segment.Ref(spec.id, part.offsetMinutes, part.format, shownFor(spec, part.offsetMinutes, part.format, now, locale))
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
            for (i in 0 until bs.length()) specs += specFromJson(bs.getJSONObject(i))
            MessageDraft(segments, specs, root.getInt("next"))
        }.getOrNull()

        internal fun specToJson(b: BlankSpec): JSONObject = JSONObject().put("id", b.id).put("q", b.question).put("kind", b.kind.name)
            .put("answers", JSONArray(b.answers)).put("example", b.example).put("remember", b.remember)
            .put("text", b.textType).put("last", b.lastValue ?: JSONObject.NULL)
            .put("show", b.show.id).put("amounts", JSONArray(b.amounts.map { it.text() }))

        internal fun specFromJson(o: JSONObject): BlankSpec {
            val answers = o.getJSONArray("answers").let { a -> (0 until a.length()).map { a.getString(it) } }
            val amounts = o.optJSONArray("amounts")?.let { a -> (0 until a.length()).mapNotNull { RelativeAmount.parse(a.getString(it)) } }.orEmpty()
            return BlankSpec(
                id = o.getInt("id"),
                question = o.getString("q"),
                kind = BlankKind.valueOf(o.getString("kind")),
                answers = answers,
                example = o.optString("example", ""),
                remember = o.optBoolean("remember", true),
                textType = o.optBoolean("text", false),
                lastValue = if (o.isNull("last")) null else o.getString("last"),
                show = RelativeShow.fromId(o.optString("show")),
                amounts = amounts,
            )
        }

        private fun specFor(id: Int, name: String, def: VariableDef): BlankSpec {
            val question = def.name.trim().ifEmpty { name }
            return when (def.type) {
                VariableType.CHOICE, VariableType.TEXT -> BlankSpec(
                    id, question, BlankKind.LIST, ShortcutDraft.cleanOptions(def.options),
                    example = def.lastValue?.takeIf { it.isNotBlank() } ?: def.options.firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                    remember = def.remember, textType = def.type == VariableType.TEXT, lastValue = def.lastValue,
                )
                VariableType.TIME -> BlankSpec(id, question, BlankKind.TIME, answers = def.options, example = def.lastValue.orEmpty(), lastValue = def.lastValue)
                VariableType.DATE -> BlankSpec(id, question, BlankKind.DATE, answers = def.options, example = def.lastValue.orEmpty(), lastValue = def.lastValue)
                VariableType.NUMBER -> BlankSpec(id, question, BlankKind.NUMBER, example = def.lastValue.orEmpty(), lastValue = def.lastValue)
                VariableType.TIME_AHEAD, VariableType.TIME_AGO -> BlankSpec(
                    id = id,
                    question = question,
                    kind = if (def.type == VariableType.TIME_AHEAD) BlankKind.TIME_AHEAD else BlankKind.TIME_AGO,
                    remember = def.remember,
                    lastValue = def.lastValue,
                    show = def.show,
                    amounts = def.options.mapNotNull { RelativeAmount.parse(it) }.distinctBy { it.text() },
                )
            }
        }

        /** The example a brand new part stands for in the message. */
        internal fun exampleFor(spec: BlankSpec, now: LocalDateTime, locale: Locale): String = shownFor(spec, 0, null, now, locale)

        /** The example text for one use of a part: what the owner typed, or a sample worked out from it. */
        private fun shownFor(spec: BlankSpec, offsetMinutes: Long, format: String?, now: LocalDateTime, locale: Locale): String {
            if (spec.kind.isRelative) {
                val amount = spec.amounts.firstOrNull() ?: return "[${spec.question}]"
                return amount.phrase(spec.kind == BlankKind.TIME_AHEAD, spec.show, now, locale)
            }
            if (offsetMinutes == 0L && format == null && spec.example.isNotBlank()) return spec.example
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
                BlankKind.NUMBER -> return "1"
                else -> {
                    type = VariableType.TEXT
                    spec.answers.firstOrNull { it.isNotBlank() }?.let { values[key] = it }
                }
            }
            val single = Template.parse(token(spec.question, offsetMinutes, format))
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
    }
}

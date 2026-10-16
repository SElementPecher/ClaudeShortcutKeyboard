package io.github.selementpecher.shortcutkeyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R

/**
 * The step-by-step panel for filling in a shortcut's blanks, one blank at a time.
 *
 * Header: close, "Medicine given · 2 of 4", Insert. Then a preview of the message, the blank's
 * name, a body that depends on the kind of blank, and Back / Next at the bottom. The panel only
 * draws what it is given in [Ui]; every tap goes back to the [Listener] (the keyboard service).
 */
@SuppressLint("ViewConstructor")
internal class FillPanelView(
    context: Context,
    private val listener: Listener,
) : LinearLayout(context) {

    interface Listener {
        fun onFillCancel()
        fun onFillInsert()
        fun onFillBack()
        fun onFillNext()
        fun onChoicePicked(value: String)
        fun onTypeNew()
        fun onShowList()
        fun onTimeAdjust(minutes: Long)
        fun onTimeNow()
        fun onDateAdjust(days: Long)

        /** Yesterday (-1), today (0) or tomorrow (1). */
        fun onDatePicked(daysFromToday: Long)
    }

    /** Everything the panel shows for one step. */
    class Ui(
        val title: String,
        val preview: CharSequence,
        val question: String,
        /** Changes whenever a different blank (or a new shortcut) is shown. */
        val stepKey: Any,
        val isFirst: Boolean,
        val isLast: Boolean,
        /** Little screen height (e.g. landscape): show less. */
        val compact: Boolean,
        val body: Body,
    )

    sealed interface Body {
        /** Pick from a list. [selected] is one of [options], or null. */
        data class Choice(val options: List<String>, val selected: String?) : Body

        /** Typing into the box with the keys below. */
        data class Typing(val text: String, val allSelected: Boolean, val canShowList: Boolean) : Body

        /** [value] is shown big, e.g. "20:20". */
        data class Time(val value: String) : Body

        /** [quickDay] is -1, 0 or 1 when the date is yesterday, today or tomorrow. */
        data class Date(val value: String, val weekday: String?, val quickDay: Long?) : Body
    }

    private val gap = context.px(R.dimen.kb_panel_gap)
    private val padding = context.px(R.dimen.kb_panel_padding)
    private val chipHeight = context.px(R.dimen.kb_panel_chip_height)
    private val buttonHeight = context.px(R.dimen.kb_panel_button_height)
    private val textColor = context.getColor(R.color.kb_key_text)
    private val hintColor = context.getColor(R.color.kb_hint_text)
    private val accentColor = context.getColor(R.color.kb_accent)
    private val onAccentColor = context.getColor(R.color.kb_on_accent)

    private val titleView = TextView(context)
    private val previewView = TextView(context)
    private val questionView = TextView(context)
    private val body = FrameLayout(context)
    private val bodyScroll = ScrollView(context)
    private val backButton: TextView
    private val nextButton: TextView

    private var bodyStepKey: Any? = null
    private var typingBox: FillInputView? = null
    private var typingCanShowList = false

    init {
        orientation = VERTICAL
        setPadding(padding, 0, padding, padding / 2)

        val headerHeight = context.px(R.dimen.kb_panel_header_height)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val close = ImageView(context).apply {
            setImageDrawable(context.tintedDrawable(R.drawable.kb_ic_close, textColor))
            scaleType = ImageView.ScaleType.CENTER
            setBackgroundResource(R.drawable.kb_icon_button_bg)
            contentDescription = context.getString(R.string.kb_cancel)
            onTap { listener.onFillCancel() }
        }
        header.addView(close, LayoutParams(headerHeight, headerHeight))
        titleView.apply {
            setTextColor(textColor)
            setTextSizeRes(R.dimen.kb_panel_title_text_size)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(padding, 0, padding, 0)
        }
        header.addView(titleView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val insert = button(context.getString(R.string.kb_insert), accent = true) { listener.onFillInsert() }
        header.addView(insert, LayoutParams(LayoutParams.WRAP_CONTENT, headerHeight - gap))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, headerHeight))

        previewView.apply {
            setTextColor(hintColor)
            setTextSizeRes(R.dimen.kb_panel_preview_text_size)
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 2
        }
        addView(previewView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        questionView.apply {
            setTextColor(textColor)
            setTextSizeRes(R.dimen.kb_panel_question_text_size)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, gap / 2, 0, 0)
        }
        addView(questionView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        bodyScroll.isVerticalScrollBarEnabled = true
        bodyScroll.overScrollMode = View.OVER_SCROLL_NEVER
        bodyScroll.addView(body, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bodyScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val footer = LinearLayout(context).apply { orientation = HORIZONTAL }
        backButton = button(context.getString(R.string.kb_back)) { listener.onFillBack() }
        nextButton = button(context.getString(R.string.kb_next)) { listener.onFillNext() }
        footer.addView(backButton, LayoutParams(0, buttonHeight, 1f).apply { marginEnd = gap / 2 })
        footer.addView(nextButton, LayoutParams(0, buttonHeight, 1f).apply { marginStart = gap / 2 })
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
    }

    /**
     * Shows [ui]. With [fixedHeight] the panel keeps that height and the body scrolls (used when
     * it replaces the keys); with null it wraps its content (used when typing, with keys below).
     */
    fun render(ui: Ui, fixedHeight: Int?) {
        titleView.text = ui.title
        previewView.text = ui.preview
        previewView.maxLines = if (ui.compact) 1 else 2
        previewView.visibility = if (ui.compact && ui.body is Body.Typing) View.GONE else View.VISIBLE
        questionView.text = ui.question
        backButton.visibility = if (ui.isFirst) View.INVISIBLE else View.VISIBLE
        nextButton.text = context.getString(if (ui.isLast) R.string.kb_insert else R.string.kb_next)
        styleButton(nextButton, accent = ui.isLast)
        applyHeight(fixedHeight)

        val stepChanged = ui.stepKey != bodyStepKey
        when (val content = ui.body) {
            is Body.Typing -> showTyping(content, rebuild = stepChanged || typingBox == null)
            else -> {
                typingBox = null
                body.removeAllViews()
                body.addView(
                    when (content) {
                        is Body.Choice -> choiceBody(content)
                        is Body.Time -> timeBody(content)
                        is Body.Date -> dateBody(content)
                        is Body.Typing -> error("handled above")
                    },
                    FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
                )
            }
        }
        if (stepChanged) bodyScroll.scrollTo(0, 0)
        bodyStepKey = ui.stepKey
    }

    private fun applyHeight(fixedHeight: Int?) {
        layoutParams?.let { params ->
            val wanted = fixedHeight ?: LayoutParams.WRAP_CONTENT
            if (params.height != wanted) {
                params.height = wanted
                layoutParams = params
            }
        }
        val scrollParams = bodyScroll.layoutParams as LayoutParams
        val weight = if (fixedHeight != null) 1f else 0f
        if (scrollParams.weight != weight) {
            scrollParams.weight = weight
            scrollParams.height = if (fixedHeight != null) 0 else LayoutParams.WRAP_CONTENT
            bodyScroll.layoutParams = scrollParams
        }
    }

    private fun showTyping(content: Body.Typing, rebuild: Boolean) {
        var box = typingBox
        if (rebuild || box == null || content.canShowList != typingCanShowList) {
            body.removeAllViews()
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, gap, 0, 0)
            }
            box = FillInputView(context)
            row.addView(box, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            if (content.canShowList) {
                val list = button(context.getString(R.string.kb_show_list)) { listener.onShowList() }
                row.addView(
                    list,
                    LayoutParams(LayoutParams.WRAP_CONTENT, context.px(R.dimen.kb_panel_input_height)).apply { marginStart = gap },
                )
            }
            body.addView(row, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            typingBox = box
            typingCanShowList = content.canShowList
        }
        box.show(content.text, content.allSelected, context.getString(R.string.kb_type_here))
    }

    private fun choiceBody(content: Body.Choice): View {
        val flow = FlowLayout(context).apply {
            horizontalSpacing = gap
            verticalSpacing = gap
            setPadding(0, gap, 0, 0)
        }
        for (option in content.options) {
            flow.addView(chip(option, selected = option == content.selected) { listener.onChoicePicked(option) })
        }
        flow.addView(chip(context.getString(R.string.kb_type_new), selected = false, accentText = true) { listener.onTypeNew() })
        return flow
    }

    private fun timeBody(content: Body.Time): View {
        val column = LinearLayout(context).apply { orientation = VERTICAL }
        val big = TextView(context).apply {
            text = content.value
            setTextColor(textColor)
            setTextSizeRes(R.dimen.kb_panel_big_text_size)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(0, gap / 2, 0, gap / 2)
        }
        column.addView(big, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        val steps = listOf(
            Triple(R.string.kb_time_minus_1h, R.string.kb_time_minus_1h_desc, -60L),
            Triple(R.string.kb_time_minus_15m, R.string.kb_time_minus_15m_desc, -15L),
            Triple(R.string.kb_time_minus_5m, R.string.kb_time_minus_5m_desc, -5L),
            null, // Now
            Triple(R.string.kb_time_plus_5m, R.string.kb_time_plus_5m_desc, 5L),
            Triple(R.string.kb_time_plus_15m, R.string.kb_time_plus_15m_desc, 15L),
            Triple(R.string.kb_time_plus_1h, R.string.kb_time_plus_1h_desc, 60L),
        )
        for (step in steps) {
            val button = if (step == null) {
                button(context.getString(R.string.kb_now), shrink = true) { listener.onTimeNow() }
            } else {
                button(context.getString(step.first), shrink = true) { listener.onTimeAdjust(step.third) }.apply {
                    contentDescription = context.getString(step.second)
                }
            }
            row.addView(button, LayoutParams(0, chipHeight, 1f).apply {
                marginStart = gap / 4
                marginEnd = gap / 4
            })
        }
        column.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return column
    }

    private fun dateBody(content: Body.Date): View {
        val column = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, gap, 0, 0)
        }
        val stepper = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val earlier = button(context.getString(R.string.kb_day_earlier)) { listener.onDateAdjust(-1) }.apply {
            contentDescription = context.getString(R.string.kb_day_earlier_desc)
        }
        val later = button(context.getString(R.string.kb_day_later)) { listener.onDateAdjust(1) }.apply {
            contentDescription = context.getString(R.string.kb_day_later_desc)
        }
        val shown = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
        }
        if (!content.weekday.isNullOrEmpty()) {
            shown.addView(TextView(context).apply {
                text = content.weekday
                setTextColor(hintColor)
                setTextSizeRes(R.dimen.kb_panel_preview_text_size)
                gravity = Gravity.CENTER
                maxLines = 1
            })
        }
        shown.addView(TextView(context).apply {
            text = content.value
            setTextColor(textColor)
            setTextSizeRes(R.dimen.kb_panel_date_text_size)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
        })
        stepper.addView(earlier, LayoutParams(LayoutParams.WRAP_CONTENT, chipHeight))
        stepper.addView(shown, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = gap
            marginEnd = gap
        })
        stepper.addView(later, LayoutParams(LayoutParams.WRAP_CONTENT, chipHeight))
        column.addView(stepper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val quick = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, gap, 0, 0)
        }
        val days = listOf(-1L to R.string.kb_yesterday, 0L to R.string.kb_today, 1L to R.string.kb_tomorrow)
        for ((offset, label) in days) {
            val chip = chip(context.getString(label), selected = content.quickDay == offset) { listener.onDatePicked(offset) }
            quick.addView(chip, LayoutParams(0, chipHeight, 1f).apply {
                marginStart = gap / 2
                marginEnd = gap / 2
            })
        }
        column.addView(quick, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return column
    }

    private fun chip(label: String, selected: Boolean, accentText: Boolean = false, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(if (accentText) accentColor else context.getColor(R.color.kb_chip_text))
            setTextSizeRes(R.dimen.kb_panel_chip_text_size)
            if (accentText || selected) setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(R.drawable.kb_chip_bg)
            isSelected = selected
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minHeight = chipHeight
            minWidth = chipHeight * 4 / 3
            setPadding(padding * 2, 0, padding * 2, 0)
            onTap(action)
        }

    /** A rounded button. [shrink] lets a short label shrink to fit a narrow button. */
    private fun button(label: String, accent: Boolean = false, shrink: Boolean = false, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextSizeRes(R.dimen.kb_panel_button_text_size)
            gravity = Gravity.CENTER
            maxLines = 1
            styleButton(this, accent)
            if (shrink) {
                setPadding(0, 0, 0, 0)
                setAutoSizeTextTypeUniformWithConfiguration(
                    resources.getDimensionPixelSize(R.dimen.kb_key_label_min_text_size),
                    resources.getDimensionPixelSize(R.dimen.kb_panel_button_text_size),
                    1,
                    TypedValue.COMPLEX_UNIT_PX,
                )
            } else {
                setPadding(padding * 2, 0, padding * 2, 0)
            }
            onTap(action)
        }

    private fun styleButton(view: TextView, accent: Boolean) {
        view.setBackgroundResource(if (accent) R.drawable.kb_key_accent_bg else R.drawable.kb_key_special_bg)
        view.setTextColor(if (accent) onAccentColor else textColor)
    }
}

package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.look.Highlighter
import io.github.selementpecher.shortcutkeyboard.look.handwritingFont
import io.github.selementpecher.shortcutkeyboard.look.HANDWRITING_EXTRA_SP
import io.github.selementpecher.shortcutkeyboard.look.IndexCardDrawable
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.marginLine
import io.github.selementpecher.shortcutkeyboard.look.RuledSnap
import io.github.selementpecher.shortcutkeyboard.look.RuledPageSnap
import io.github.selementpecher.shortcutkeyboard.look.RuledPaper
import io.github.selementpecher.shortcutkeyboard.look.pageBackground
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.look.Palette
import io.github.selementpecher.shortcutkeyboard.look.TileColors
import io.github.selementpecher.shortcutkeyboard.look.TileLayout
import io.github.selementpecher.shortcutkeyboard.look.folderColors
import io.github.selementpecher.shortcutkeyboard.look.scaled
import io.github.selementpecher.shortcutkeyboard.look.tileColorsFor
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf
import io.github.selementpecher.shortcutkeyboard.look.withType
import kotlin.math.max
import kotlin.math.min

/** What a tile stands for. */
internal enum class TileKind {
    /** A shortcut: one of the Look's tile colours, always the same one for the same key. */
    SHORTCUT,

    /** A folder: the Look's folder colours and a folder shape. */
    FOLDER,

    /** A dashed "add something" tile. */
    NEW,
}

/** The look of a [Ui.dialog] button. */
internal enum class ButtonStyle { PRIMARY, SECONDARY, TEXT, DANGER }

/** One button at the bottom of a dialog. [onClick] returns true to close the dialog (the default). */
internal class DialogButton(
    val text: String,
    val style: ButtonStyle = ButtonStyle.PRIMARY,
    val onClick: () -> Boolean = { true },
)

/**
 * The measurements of a tile and of the page around it, so the app and the keyboard draw the same tile
 * (what you see in the app is what you get on the keyboard). All in dp unless it says sp.
 */
internal object TileSpec {
    /** Space at the left and right edge of a screen, and between a screen's blocks of content. */
    const val SCREEN_MARGIN_DP = 20

    /** Space between tiles, across and down. */
    const val GRID_GAP_DP = 12

    /** Space inside a card. */
    const val CARD_PADDING_DP = 20

    /** Space between sections of a screen. */
    const val SECTION_GAP_DP = 24

    /** Wide tile: the least height it takes (it grows with larger text). */
    const val WIDE_MIN_HEIGHT_DP = 76

    /** Wide tile: the soft circle that holds the picture or first letter. */
    const val WIDE_PICTURE_DP = 40

    /** Wide tile: size of an emoji inside that circle, in sp. */
    const val WIDE_PICTURE_SP = 22f

    /** Wide tile: space between the circle and the name. */
    const val WIDE_GAP_DP = 10

    /** Wide tile: space left and right, and above and below. */
    const val WIDE_PADDING_H_DP = 12
    const val WIDE_PADDING_V_DP = 10

    /** Square tile: height equals width; the picture, in sp, sits above the name. */
    const val SQUARE_PICTURE_SP = 30f
    const val SQUARE_PADDING_DP = 8

    /** The folder's tab, drawn on top of its body. */
    const val FOLDER_TAB_WIDE_DP = 6f
    const val FOLDER_TAB_SQUARE_DP = 5f

    /** Folders on the Bold Look have an outline this thick. */
    const val BOLD_FOLDER_OUTLINE_DP = 2f
}

/** The letter or picture shown on a tile: the icon if there is one, else the name's first letter (upper case). */
internal fun glyphFor(icon: String, name: String, isFolder: Boolean = false): String {
    if (icon.isNotBlank()) return icon.trim()
    if (isFolder) return "📁"
    val first = name.trim().firstOrNull { it.isLetterOrDigit() } ?: return "•"
    return first.uppercaseChar().toString()
}

/**
 * The app's design system: builds every view in code from the current [Look], so the app matches the
 * keyboard. Make one with `Ui(this, LookStore.current(this))` (or [Ui.of]) in `onCreate`, keep it, and call
 * [isStale] in `onResume`: if it is true the Look, light/dark or text size changed, so call `recreate()`.
 *
 * Every text size in [look] is already multiplied by the owner's "Large text" choice
 * ([LookStore.textScale]) and never goes below a comfortable reading size, so use `look.type.*` as it is.
 * For a fixed size of your own, use [sp].
 */
internal class Ui(val context: Context, baseLook: Look) {

    private val textScale = LookStore.textScale(context)

    /** The Look with its text sizes made comfortable to read and scaled for "Large text". */
    val look: Look = baseLook.withType(
        baseLook.type.copy(
            bodySp = max(baseLook.type.bodySp, MIN_BODY_SP),
            smallSp = max(baseLook.type.smallSp, MIN_SMALL_SP),
        ).scaled(textScale),
    )

    /** Colours for the phone's current light or dark setting. */
    val palette: Palette = look.palette(context)

    private val density = context.resources.displayMetrics.density
    private val nightWhenBuilt = Look.isNight(context)

    /** dp to pixels. */
    fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    /** dp to pixels, keeping fractions. */
    fun dpf(value: Float): Float = value * density

    /** A text size in sp that follows the "Large text" choice. */
    fun sp(value: Float): Float = value * textScale

    /** True when the chosen Look, light/dark setting or text size is no longer the one this Ui was built with. */
    fun isStale(ignoreLook: Boolean = false): Boolean =
        (!ignoreLook && LookStore.current(context).id != look.id) || Look.isNight(context) != nightWhenBuilt || LookStore.textScale(context) != textScale

    val isWide: Boolean get() = look.tileLayout == TileLayout.WIDE

    /** How many tiles fit across the home screen: 2 for the wide Looks, 3 for squares. */
    val columns: Int get() = if (isWide) 2 else 3

    // ---------------------------------------------------------------- drawing helpers

    /** A rounded rectangle, optionally outlined or dashed. [radiusDp] 999 makes a pill. */
    fun shape(fill: Int, radiusDp: Float, strokeColor: Int = Color.TRANSPARENT, strokeDp: Float = 0f, dashed: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dpf(radiusDp)
            if (strokeDp > 0f) {
                if (dashed) setStroke(dpf(strokeDp).toInt().coerceAtLeast(1), strokeColor, dpf(6f), dpf(5f))
                else setStroke(dpf(strokeDp).toInt().coerceAtLeast(1), strokeColor)
            }
        }

    /** Wraps [content] in a quiet touch ripple that keeps the same rounded corners. */
    fun rippled(content: Drawable, radiusDp: Float): Drawable =
        RippleDrawable(ColorStateList.valueOf(withAlpha(palette.ink, 0x26)), content, shape(Color.WHITE, radiusDp))

    /** [base] with a very soft shadow under it (the Look's elevation), drawn in code so it shows everywhere. */
    fun softShadow(base: Drawable, radiusDp: Float, topInsetDp: Float = 0f): Drawable =
        if (look.shape.elevationDp > 0f) {
            SoftShadowDrawable(base, dpf(radiusDp), dpf(look.shape.elevationDp), if (Look.isNight(context)) 0x30 else 0x1C, dpf(topInsetDp))
        } else base

    /** [color] with its opacity set to [alpha] (0 to 255). */
    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /** [front] drawn over [back] by [amount] (0 to 1). */
    fun blend(back: Int, front: Int, amount: Float): Int {
        fun mix(shift: Int): Int {
            val b = (back shr shift) and 0xFF
            val f = (front shr shift) and 0xFF
            return (b + (f - b) * amount).toInt().coerceIn(0, 255)
        }
        return Color.rgb(mix(16), mix(8), mix(0))
    }

    /** Black or white, whichever is easier to read on [background]. */
    fun readableOn(background: Int): Int =
        if (luminanceOf(background) > 0.45f) Color.parseColor("#111111") else Color.WHITE

    private fun luminanceOf(color: Int): Float =
        (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f

    private fun outlinePx(): Int = dpf(look.shape.strokeDp).toInt().coerceAtLeast(1)

    /** An outline that can be seen on any surface: the Look's line colour, a little stronger. */
    /** The box of a text-only button: none, except in the Exercise book, where it is a solid white pill (no ruled lines show through). */
    private fun plainFill(radiusDp: Float): GradientDrawable =
        if (look.ruled) shape(palette.surface, radiusDp, buttonBorder(), 1.5f) else shape(Color.TRANSPARENT, radiusDp)

    private fun buttonBorder(): Int = blend(palette.outline, palette.inkSoft, 0.55f)

    /**
     * Puts [kind] in front of the words of [view] as part of the text, so the picture and the words are centred
     * together (a compound drawable would stick to the left edge of a wide button).
     */
    fun iconBeforeText(view: TextView, kind: Icons.Kind, color: Int) {
        val size = dp(22)
        val picture = icon(kind, 22, color).apply { setBounds(0, 0, size, size) }
        val shown = android.text.SpannableStringBuilder("  ").append(view.text)
        shown.setSpan(android.text.style.ImageSpan(picture, android.text.style.ImageSpan.ALIGN_BOTTOM), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        view.setCompoundDrawablesRelative(null, null, null, null)
        view.text = shown
    }

    /** A line icon from the shared family, [sizeDp] square, in [color]. */
    fun icon(kind: Icons.Kind, sizeDp: Int, color: Int): Drawable = Icons.drawable(kind, color, dp(sizeDp))

    // ---------------------------------------------------------------- layout helpers

    /** LinearLayout params with margins in dp. [width]/[height] may be MATCH_PARENT, WRAP_CONTENT or 0 with a [weight]. */
    fun lp(
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0,
        weight: Float = 0f,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height, weight).apply { setMargins(dp(start), dp(top), dp(end), dp(bottom)) }

    /** Adds [view] to this layout with [top] dp of space above it. */
    fun LinearLayout.add(view: View, top: Int = 0, bottom: Int = 0): View {
        val params = (view.layoutParams as? LinearLayout.LayoutParams) ?: lp()
        params.topMargin = dp(top)
        params.bottomMargin = dp(bottom)
        addView(view, params)
        return view
    }

    /** An empty gap of [heightDp]. */
    fun spacer(heightDp: Int): View = View(context).apply { layoutParams = lp(height = dp(heightDp)) }

    /** A thin line in the outline colour. */
    fun divider(): View = View(context).apply {
        setBackgroundColor(palette.outline)
        layoutParams = lp(height = max(1, dp(1)))
    }

    /** A vertical LinearLayout. */
    fun column(): LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    /** A horizontal LinearLayout with its children centred vertically. */
    fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    /**
     * Two or more buttons of equal width side by side. If their labels do not fit on one line (large text),
     * they stack instead, so a label never wraps.
     */
    fun buttonRow(vararg buttons: View, gapDp: Int = 12): LinearLayout = AdaptiveRow(context, dp(gapDp)).apply {
        layoutParams = lp()
        buttons.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    /** A slim progress bar, [fraction] (0 to 1) filled with the accent colour. */
    fun progressBar(fraction: Float, heightDp: Int = 6): View = SlimProgress(
        context, fraction.coerceIn(0f, 1f), palette.surfaceAlt.let { blend(palette.bg, palette.inkSoft, 0.22f) }, palette.accent.let { if (look.id == "bold" && !Look.isNight(context)) palette.ink else it },
    ).apply { layoutParams = lp(height = dp(heightDp)) }

    // ---------------------------------------------------------------- text

    /** Any text. [weight] is 100 to 900. Body-style text gets roomy lines and a little extra letter spacing. */
    fun text(text: CharSequence, sizeSp: Float, weight: Int, color: Int, lineSpacing: Float = READING_LINE_SPACING): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = typefaceOf(weight)
        setTextColor(color)
        setLineSpacing(0f, lineSpacing)
        letterSpacing = READING_LETTER_SPACING
        textAlignment = View.TEXT_ALIGNMENT_GRAVITY
        includeFontPadding = false
        layoutParams = lp()
    }

    /** The big screen title. */
    fun title(text: CharSequence): TextView = text(text, look.type.titleSp, look.type.headingWeight, palette.ink, HEADING_LINE_SPACING)

    /** A section heading. */
    fun heading(text: CharSequence): TextView = text(text, look.type.headingSp, look.type.headingWeight, palette.ink, HEADING_LINE_SPACING)

    /** Normal reading text. */
    fun body(text: CharSequence): TextView = text(text, look.type.bodySp, 400, palette.ink)

    /** Smaller, softer text for helper lines. Never below the Look's small size. */
    fun small(text: CharSequence): TextView = text(text, look.type.smallSp, 400, palette.inkSoft)

    /** A small bold caption above a field or group. */
    fun label(text: CharSequence): TextView {
        // Exercise book: a short subheading is written by hand and underlined with a pen stroke.
        if (look.ruled && text.length <= RULED_LABEL_MAX_CHARS) {
            val marked = android.text.SpannableString(text).apply {
                setSpan(PenUnderlineSpan(palette.ink, dpf(2f), dpf(3f)), 0, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return text(marked, look.type.smallSp + HANDWRITING_EXTRA_SP + 2f, 400, palette.ink).apply {
                handwritingFont(context)?.let { typeface = it }
            }
        }
        return text(text, look.type.smallSp, 600, palette.inkSoft)
    }

    // ---------------------------------------------------------------- buttons

    private fun buttonView(text: CharSequence, fg: Int, bg: Drawable, icon: Icons.Kind?, onClick: (() -> Unit)?): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, look.type.bodySp)
        typeface = typefaceOf(max(600, look.type.headingWeight))
        setTextColor(fg)
        letterSpacing = READING_LETTER_SPACING
        minHeight = dp(MAIN_ACTION_DP)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = bg
        isClickable = true
        isFocusable = true
        includeFontPadding = false
        if (icon != null) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon(icon, 22, fg), null, null, null)
            compoundDrawablePadding = dp(8)
        }
        setOnClickListener { onClick?.invoke() }
        Motion.pressable(this, 0.98f)
        layoutParams = lp()
    }

    /** The one main action of a screen: accent fill, at least 56dp tall. */
    fun primaryButton(text: CharSequence, icon: Icons.Kind? = null, onClick: (() -> Unit)? = null): TextView {
        val r = look.shape.buttonRadiusDp.toFloat()
        return buttonView(text, palette.onAccent, rippled(shape(palette.accent, r), r), icon, onClick)
    }

    /** A second choice: an outlined button. */
    fun secondaryButton(text: CharSequence, icon: Icons.Kind? = null, onClick: (() -> Unit)? = null): TextView {
        val r = look.shape.buttonRadiusDp.toFloat()
        // Exercise book: a solid white button, so the ruled lines never show through it.
        val bg = shape(if (look.ruled) palette.surface else Color.TRANSPARENT, r, buttonBorder(), max(1.5f, look.shape.strokeDp))
        return buttonView(text, palette.ink, rippled(bg, r), icon, onClick)
    }

    /** A plain text button for small choices such as Cancel. */
    fun textButton(text: CharSequence, icon: Icons.Kind? = null, onClick: (() -> Unit)? = null): TextView {
        val r = look.shape.buttonRadiusDp.toFloat()
        return buttonView(text, linkColor(), rippled(plainFill(r), r), icon, onClick)
    }

    /** Red text for deleting things (always followed by a question). No box, so it is never the loudest thing. */
    fun dangerButton(text: CharSequence, icon: Icons.Kind? = null, onClick: (() -> Unit)? = null): TextView {
        val r = look.shape.buttonRadiusDp.toFloat()
        return buttonView(text, palette.danger, rippled(plainFill(r), r), icon, onClick)
    }

    /** The colour of a plain text button: the accent, or ink where the accent is black (Bold, light). */
    fun linkColor(): Int = if (palette.accent == Color.parseColor("#0B0C10")) palette.ink else palette.accent

    /**
     * A ghost icon button: a 48dp hit area with no box. A soft round highlight shows only while pressed.
     * The picture is 24dp, from the shared [Icons] family.
     */
    fun iconButton(kind: Icons.Kind, description: String, tint: Int = palette.ink, onClick: () -> Unit): ImageView = ImageView(context).apply {
        setImageDrawable(icon(kind, 24, tint))
        scaleType = ImageView.ScaleType.CENTER
        // Exercise book: a solid paper-coloured disc, so the ruled lines never show through the picture.
        background = rippled(shape(if (look.ruled) palette.bg else Color.TRANSPARENT, 999f), 999f)
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }

    // ---------------------------------------------------------------- containers and inputs

    /** A rounded surface that holds a group of things. Add children to it. */
    fun card(paddingDp: Int = TileSpec.CARD_PADDING_DP): LinearLayout = column().apply {
        val r = look.shape.cardRadiusDp.toFloat()
        background = softShadow(shape(palette.surface, r, palette.outline, look.shape.strokeDp), r)
        setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
        clipChildren = false
        layoutParams = lp()
    }

    /** A text box with a hint. Single line unless [multiline]. */
    fun field(hint: CharSequence, multiline: Boolean = false): EditText = EditText(context).apply {
        this.hint = hint
        setTextSize(TypedValue.COMPLEX_UNIT_SP, look.type.bodySp)
        typeface = typefaceOf(400)
        setTextColor(palette.ink)
        setHintTextColor(palette.inkSoft)
        letterSpacing = READING_LETTER_SPACING
        setLineSpacing(0f, READING_LINE_SPACING)
        val r = min(look.shape.cardRadiusDp, 16).toFloat()
        val strokeDp = max(1.5f, look.shape.strokeDp)
        val rest = if (look.shape.strokeDp > 0f) shape(palette.surface, r, palette.outline, look.shape.strokeDp)
        else shape(palette.surfaceAlt, r)
        val focus = shape(if (look.shape.strokeDp > 0f) palette.surface else palette.surfaceAlt, r, palette.accent, max(2f, strokeDp))
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focus)
            addState(intArrayOf(), rest)
        }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        minHeight = dp(MAIN_ACTION_DP)
        gravity = if (multiline) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL
        inputType = if (multiline) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        if (multiline) {
            minLines = 4
            maxLines = 10
        } else {
            setSingleLine(true)
        }
        layoutParams = lp()
    }

    /** A pill to tap: filled with the accent when [selected]. At least 48dp tall. */
    fun chip(text: CharSequence, selected: Boolean = false, onClick: (() -> Unit)? = null): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, look.type.bodySp)
        typeface = typefaceOf(if (selected) max(600, look.type.headingWeight) else 500)
        setTextColor(if (selected) palette.onAccent else palette.ink)
        letterSpacing = READING_LETTER_SPACING
        minHeight = dp(48)
        setPadding(dp(18), dp(8), dp(18), dp(8))
        val r = 999f
        val bg = when {
            selected -> shape(palette.accent, r)
            look.shape.strokeDp > 0f -> shape(Color.TRANSPARENT, r, palette.outline, look.shape.strokeDp)
            // Exercise book: a white pill with a clear outline, never one that melts into the paper.
            look.ruled -> shape(palette.surface, r, palette.inkSoft, 1.5f)
            else -> shape(palette.surfaceAlt, r)
        }
        background = rippled(bg, r)
        isClickable = true
        isFocusable = true
        includeFontPadding = false
        setOnClickListener { onClick?.invoke() }
        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Words that wrap onto new lines, with [gapDp] between them. For chips. */
    fun flow(gapDp: Int = 8): UiFlow = UiFlow(context, dp(gapDp)).apply { layoutParams = lp() }

    /**
     * Rows of [columns] equal cells, filling the last row with empty space. Good for tiles.
     * Does not clip, so soft shadows show.
     */
    fun grid(columns: Int, cells: List<View>, gapDp: Int = TileSpec.GRID_GAP_DP): LinearLayout {
        val grid = UniformColumn(context)
        grid.clipChildren = false
        grid.clipToPadding = false
        cells.chunked(columns).forEachIndexed { index, chunk ->
            val line = EqualHeightRow(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                layoutParams = lp(top = if (index == 0) 0 else gapDp)
            }
            for (i in 0 until columns) {
                val cell = chunk.getOrNull(i) ?: android.widget.Space(context)
                (cell.parent as? ViewGroup)?.removeView(cell)
                line.addView(cell, lp(width = 0, height = ViewGroup.LayoutParams.WRAP_CONTENT, start = if (i == 0) 0 else gapDp, weight = 1f))
            }
            grid.addView(line)
        }
        return grid
    }

    /**
     * What a screen shows when there is nothing yet: a big picture ([emoji]), one sentence, and the one
     * button that fixes it. Add the button yourself, under it.
     */
    fun emptyState(emoji: String, title: CharSequence, sentence: CharSequence): LinearLayout = column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(24), dp(16), 0)
        addView(
            TextView(context).apply {
                text = emoji
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(52f))
                includeFontPadding = false
                background = shape(palette.surfaceAlt, 999f)
                layoutParams = LinearLayout.LayoutParams(dp(112), dp(112))
            },
        )
        add(heading(title).apply { gravity = Gravity.CENTER }, top = 20)
        add(body(sentence).apply { gravity = Gravity.CENTER; setTextColor(palette.inkSoft) }, top = 8)
    }

    // ---------------------------------------------------------------- tiles

    /**
     * A tile, the same as on the keyboard. WIDE Looks: the picture (or first letter) in a soft circle on the
     * left, the name beside it (two lines at most; the text shrinks, never below the small size, before it
     * is cut). SQUARE Look: a square tile with the picture above the name. Folders have a tab and a quiet
     * line such as "3 items". The tile grows taller for larger text. See [TileSpec] for the measurements.
     *
     * @param colorKey picks the tile colour (use the shortcut id) so it always looks the same.
     * @param subtitle an optional small line under the name, e.g. "3 items".
     */
    fun tile(
        name: String,
        icon: String,
        kind: TileKind,
        colorKey: String = name,
        color: Int = -1,
        subtitle: String? = null,
        description: String = name,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
    ): View {
        val colors = when (kind) {
            TileKind.SHORTCUT -> palette.tileColorsFor(colorKey, color)
            TileKind.FOLDER -> palette.folderColors(color)
            // Exercise book: solid paper-white, so the ruled lines never show through.
            TileKind.NEW -> TileColors(if (look.ruled) palette.surface else Color.TRANSPARENT, palette.inkSoft)
        }
        val radius = look.shape.tileRadiusDp.toFloat()
        val tile = if (isWide) AspectFrame(context, 0f, dp(TileSpec.WIDE_MIN_HEIGHT_DP)) else AspectFrame(context, 1f, 0)
        tile.clipChildren = false
        val folderLike = kind == TileKind.FOLDER
        // Exercise book: a folder is turned a little, like one dropped on the desk.
        if (folderLike && Highlighter.on) tile.rotation = Highlighter.stickyAngle("folder:" + colorKey) * 0.8f
        // Exercise book: a quiKey is an index card dropped on the desk, turned a touch.
        if (kind == TileKind.SHORTCUT && Highlighter.on) tile.rotation = Highlighter.stickyAngle("card:" + colorKey) * 0.5f
        val tabDp = if (isWide) TileSpec.FOLDER_TAB_WIDE_DP else TileSpec.FOLDER_TAB_SQUARE_DP
        val base: Drawable = when {
            kind == TileKind.NEW -> shape(if (look.ruled) palette.surface else Color.TRANSPARENT, radius, if (look.shape.strokeDp > 0f) palette.outline else palette.inkSoft, max(1.5f, look.shape.strokeDp), dashed = true)
            folderLike -> FolderDrawable(
                fill = colors.bg,
                strokeColor = if (look.id == "bold") palette.accent else palette.outline,
                strokePx = when {
                    look.id == "bold" -> dpf(TileSpec.BOLD_FOLDER_OUTLINE_DP)
                    look.shape.strokeDp > 0f -> dpf(look.shape.strokeDp)
                    else -> 0f
                },
                radiusPx = dpf(radius),
                tabPx = dpf(tabDp),
                tabFill = if (Highlighter.on) palette.tileColorsFor(colorKey, color).bg else 0,
                stackPx = if (Highlighter.on) dpf(6f) else 0f,
                pageEdge = palette.outline,
            )
            // Exercise book: an index card with a highlighter swipe in the tile's colour, instead of a filled tile.
            kind == TileKind.SHORTCUT && Highlighter.on -> IndexCardDrawable(
                fill = palette.surface,
                swipe = if (Look.isNight(context)) blend(colors.bg, colors.fg, 0.55f) else colors.bg,
                radiusPx = dpf(radius),
                swipeLeftPx = dpf((TileSpec.WIDE_PADDING_H_DP + TileSpec.WIDE_PICTURE_DP + TileSpec.WIDE_GAP_DP).toFloat()),
                swipeHeightPx = dpf(9f),
                insetPx = dpf(12f),
            )
            else -> shape(colors.bg, radius, palette.outline, if (look.shape.strokeDp > 0f) look.shape.strokeDp else 0f)
        }
        tile.background = rippled(if (kind == TileKind.NEW) base else softShadow(base, radius, if (folderLike) tabDp else 0f), radius)
        tile.contentDescription = description
        tile.isClickable = onClick != null || onLongClick != null
        tile.isFocusable = tile.isClickable
        tile.setOnClickListener(onClick?.let { handler -> View.OnClickListener { handler() } })
        if (onLongClick != null) tile.setOnLongClickListener { onLongClick(); true }
        if (tile.isClickable) Motion.pressable(tile)

        val glyph = if (kind == TileKind.NEW) "+" else glyphFor(icon, name, folderLike)
        val topPad = if (folderLike) dp(tabDp.toInt()) else 0
        val nameView = FitTextView(context, look.type.smallSp, look.type.tileSp).apply {
            text = name
            typeface = typefaceOf(look.type.tileWeight)
            setTextColor(colors.fg)
            setLineSpacing(0f, 1.1f)
            letterSpacing = READING_LETTER_SPACING
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = lp()
        }
        val subView = subtitle?.let {
            text(it, look.type.smallSp, 500, colors.fg, 1.1f).apply {
                alpha = 0.8f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        }
        if (isWide) {
            val inner = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                setPadding(dp(TileSpec.WIDE_PADDING_H_DP), dp(TileSpec.WIDE_PADDING_V_DP) + topPad, dp(TileSpec.WIDE_PADDING_H_DP), dp(TileSpec.WIDE_PADDING_V_DP))
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL)
            }
            val circle = TextView(context).apply {
                text = glyph
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(if (glyph.codePointAt(0) > 0x2000) TileSpec.WIDE_PICTURE_SP else 20f))
                typeface = typefaceOf(look.type.tileWeight)
                setTextColor(colors.fg)
                includeFontPadding = false
                background = shape(if (kind == TileKind.NEW) Color.TRANSPARENT else withAlpha(colors.fg, 0x26), 999f)
                layoutParams = LinearLayout.LayoutParams(dp(TileSpec.WIDE_PICTURE_DP), dp(TileSpec.WIDE_PICTURE_DP))
            }
            inner.addView(circle)
            val words = column().apply {
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = lp(width = 0, weight = 1f, start = TileSpec.WIDE_GAP_DP)
            }
            words.addView(nameView)
            subView?.let { words.addView(it.apply { layoutParams = lp(top = 2) }) }
            inner.addView(words)
            tile.addView(inner)
        } else {
            val inner = column().apply {
                clipChildren = false
                gravity = Gravity.CENTER
                setPadding(dp(TileSpec.SQUARE_PADDING_DP), dp(TileSpec.SQUARE_PADDING_DP) + topPad, dp(TileSpec.SQUARE_PADDING_DP), dp(TileSpec.SQUARE_PADDING_DP + 2))
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            inner.addView(
                TextView(context).apply {
                    text = glyph
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(TileSpec.SQUARE_PICTURE_SP))
                    typeface = typefaceOf(look.type.tileWeight)
                    setTextColor(colors.fg)
                    includeFontPadding = false
                    layoutParams = lp()
                },
            )
            inner.addView(nameView.apply { gravity = Gravity.CENTER; layoutParams = lp(top = 6) })
            subView?.let { inner.addView(it.apply { gravity = Gravity.CENTER; layoutParams = lp(top = 1) }) }
            tile.addView(inner)
        }
        tile.layoutParams = lp()
        return tile
    }

    // ---------------------------------------------------------------- screens

    /**
     * A full screen: a top bar (back button, [title], [trailing] views on the right), a scrolling [UiScreen.content]
     * column, and optionally a fixed [UiScreen.bottomBar] for the main actions. Call [UiScreen.install] from
     * `onCreate` to show it with the right edge-to-edge padding.
     *
     * @param large the title in the big title size (home screen).
     */
    fun screen(
        title: CharSequence,
        onBack: (() -> Unit)? = null,
        trailing: List<View> = emptyList(),
        large: Boolean = false,
        withBottomBar: Boolean = false,
        logo: Boolean = false,
    ): UiScreen {
        val rootPaper = if (look.ruled) RuledPaper(context, palette.bg, palette.outline) else null
        val root = column().apply {
            background = rootPaper ?: look.pageBackground(context, palette)
            // The red margin is drawn over everything, so no bar, card or button can cover it.
            foreground = look.marginLine(context, palette)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        // Exercise book: the header is a clean band of plain paper (no ruled lines through the title), a little taller.
        val topBar = column().apply { if (look.ruled) setBackgroundColor(palette.bg) }
        val bar = row().apply {
            setPadding(dp(if (onBack == null) TileSpec.SCREEN_MARGIN_DP else 8), dp(8), dp(8), dp(8))
            minimumHeight = dp(if (look.ruled) 72 else 56)
            gravity = Gravity.CENTER_VERTICAL
        }
        if (onBack != null) bar.addView(iconButton(Icons.Kind.BACK, context.getString(io.github.selementpecher.shortcutkeyboard.R.string.action_back), onClick = onBack))
        val titleView = (if (large) title(title).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, min(look.type.titleSp, sp(28f))) } else heading(title)).apply {
            maxLines = if (large) 2 else 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = lp(width = 0, weight = 1f, start = if (onBack == null) 0 else 6, end = 8)
        }
        if (logo) {
            // The app icon, then the name with "Key" in a pill: the part of the word that changes.
            val word = android.text.SpannableString(title).apply {
                val at = title.indexOf("Key")
                if (at >= 0) setSpan(RoundedHighlightSpan(palette.accent, palette.onAccent, dpf(7f), dpf(9f), dpf(1f), true, true), at, at + 3, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            titleView.text = word
            val mark = io.github.selementpecher.shortcutkeyboard.look.LogoMark(blend(palette.ink, palette.bg, 0.5f), palette.accent, palette.onAccent)
            mark.setBounds(0, 0, dp(44), dp(44))
            titleView.setCompoundDrawablesRelative(mark, null, null, null)
            titleView.compoundDrawablePadding = dp(10)
            titleView.gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(titleView)
        trailing.forEach { bar.addView(it) }
        topBar.addView(bar, lp())
        root.addView(topBar, lp())

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }
        val contentPaper = if (look.ruled) RuledPaper(context, palette.bg, palette.outline) else null
        val content = column().apply {
            setPadding(dp(TileSpec.SCREEN_MARGIN_DP), dp(8), dp(TileSpec.SCREEN_MARGIN_DP), dp(24))
            clipChildren = false
            clipToPadding = false
            if (contentPaper != null) {
                // The page's rules scroll with the content, and are lined up with the fixed ones (see RuledPageSnap).
                background = contentPaper
                tag = RuledSnap.PAPER
            }
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, lp(height = 0, weight = 1f))

        val bottom = column().apply { setPadding(dp(TileSpec.SCREEN_MARGIN_DP), dp(12), dp(TileSpec.SCREEN_MARGIN_DP), dp(12)) }
        val bottomWrap = column().apply {
            // Ruled paper shows through, so the margin and rules carry on behind the buttons.
            if (!look.ruled) setBackgroundColor(palette.bg)
            // Quiet: only the hairline Look keeps a rule above the buttons.
            if (look.shape.strokeDp > 0f) addView(divider())
            addView(bottom)
            visibility = if (withBottomBar) View.VISIBLE else View.GONE
        }
        root.addView(bottomWrap, lp())
        if (rootPaper != null && contentPaper != null) RuledPageSnap(root, rootPaper, contentPaper, scroll).install()
        return UiScreen(this, root, topBar, titleView, scroll, content, bottom, bottomWrap)
    }

    // ---------------------------------------------------------------- dialogs and sheets

    /**
     * A bottom sheet: rounded at the top, slides up, scrolls if tall. Fill [UiPanel.body], then [UiPanel.show].
     * [title] is optional.
     */
    fun sheet(activity: Activity, title: CharSequence? = null): UiPanel = makePanel(activity, title, bottom = true)

    /** A centred dialog card. Fill [UiPanel.body], then [UiPanel.show]. */
    fun dialog(activity: Activity, title: CharSequence? = null): UiPanel = makePanel(activity, title, bottom = false)

    /** A question with buttons stacked full width (big to tap). Returns the panel; call show(). */
    fun ask(activity: Activity, title: CharSequence, message: CharSequence? = null, buttons: List<DialogButton>): UiPanel {
        val panel = dialog(activity, title)
        if (message != null) panel.body.add(body(message).apply { setTextColor(palette.inkSoft) }, top = 8)
        panel.buttons(buttons)
        return panel
    }

    /** "Are you sure?" with a [confirmText] button (red when [danger]) and Cancel. */
    fun confirm(
        activity: Activity,
        title: CharSequence,
        message: CharSequence?,
        confirmText: CharSequence,
        cancelText: CharSequence,
        danger: Boolean = false,
        onConfirm: () -> Unit,
    ): UiPanel = ask(
        activity, title, message,
        listOf(
            DialogButton(cancelText.toString(), ButtonStyle.SECONDARY),
            DialogButton(confirmText.toString(), if (danger) ButtonStyle.DANGER else ButtonStyle.PRIMARY) { onConfirm(); true },
        ),
    )

    private fun makePanel(activity: Activity, title: CharSequence?, bottom: Boolean): UiPanel {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val body = column().apply { clipChildren = false }
        val shapeRadius = max(look.shape.cardRadiusDp, 20).toFloat()
        val surface = column().apply {
            val r = dpf(shapeRadius)
            background = GradientDrawable().apply {
                setColor(palette.surface)
                cornerRadii = if (bottom) floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) else FloatArray(8) { r }
                if (look.shape.strokeDp > 0f) setStroke(outlinePx(), palette.outline)
            }
            setPadding(dp(TileSpec.SCREEN_MARGIN_DP), dp(if (bottom) 12 else 24), dp(TileSpec.SCREEN_MARGIN_DP), dp(if (bottom) 20 else 16))
        }
        if (bottom) {
            // Grab handle: a small hint that this is a sheet.
            surface.addView(
                View(context).apply { background = shape(withAlpha(palette.inkSoft, 0x66), 999f) },
                LinearLayout.LayoutParams(dp(40), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14) },
            )
        }
        if (title != null) surface.addView(heading(title).apply { layoutParams = lp(bottom = 8) })
        val scroller = MaxHeightScrollView(context, activity.resources.displayMetrics.heightPixels * 0.72f).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        surface.addView(scroller, lp())
        val frame = FrameLayout(context).apply {
            if (!bottom) setPadding(dp(TileSpec.SCREEN_MARGIN_DP), 0, dp(TileSpec.SCREEN_MARGIN_DP), 0)
            addView(
                surface,
                FrameLayout.LayoutParams(
                    if (bottom) ViewGroup.LayoutParams.MATCH_PARENT else min(dp(420), activity.resources.displayMetrics.widthPixels - dp(40)),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    if (bottom) Gravity.BOTTOM else Gravity.CENTER,
                ),
            )
        }
        dialog.setContentView(frame)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(if (bottom) Gravity.BOTTOM else Gravity.CENTER)
            setDimAmount(if (Look.isNight(context)) 0.6f else 0.4f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (bottom) setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        return UiPanel(this, dialog, body)
    }

    companion object {
        /** Height of the main action buttons and fields, in dp. */
        const val MAIN_ACTION_DP = 56

        /** Reading text is never smaller than this (sp, before "Large text"). */
        const val MIN_BODY_SP = 17f
        const val MIN_SMALL_SP = 14f

        /** A label up to this long is a subheading; longer ones are sentences and keep the plain style. */
        const val RULED_LABEL_MAX_CHARS = 28

        /** Roomy lines and a touch of extra letter spacing help dyslexic readers. */
        const val READING_LINE_SPACING = 1.3f
        const val HEADING_LINE_SPACING = 1.15f
        const val READING_LETTER_SPACING = 0.02f

        /** A Ui for the Look chosen right now. */
        fun of(context: Context): Ui = Ui(context, LookStore.current(context))
    }
}

/** A built screen: put views in [content], main buttons in [bottomBar], then call [install]. */
internal class UiScreen(
    val ui: Ui,
    val root: LinearLayout,
    /** The block that is padded for the status bar. */
    val topBar: View,
    val titleView: TextView,
    val scroll: ScrollView,
    val content: LinearLayout,
    val bottomBar: LinearLayout,
    private val bottomWrap: View,
) {
    private var activity: Activity? = null

    /** Show or hide the bottom action area. */
    fun showBottomBar(show: Boolean) {
        bottomWrap.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** Make this the activity's content with edge-to-edge padding and the Look's background behind it. */
    fun install(activity: Activity) {
        this.activity = activity
        activity.window.setBackgroundDrawable(ColorDrawable(ui.palette.bg))
        activity.setContentView(root)
        EdgeToEdge.apply(activity, root, topBar)
    }

    /** Height of the bottom action area, so a message can sit above it. */
    private fun bottomBarHeight(): Int = if (bottomWrap.visibility == View.VISIBLE) bottomWrap.height else 0

    /** Shows a short message ("Saved") above the bottom buttons; it goes away by itself. */
    fun snack(text: CharSequence, error: Boolean = false) {
        val host = activity ?: return
        Snackbars.show(host, ui, text, error, bottomBarHeight())
    }

    /** Shows the message another screen left with [Snackbars.later], if there is one. */
    fun showWaitingSnack() {
        val host = activity ?: return
        Snackbars.showWaiting(host, ui, bottomBarHeight())
    }
}

/** A dialog or sheet made by [Ui.dialog] / [Ui.sheet]. Add views to [body]. */
internal class UiPanel(val ui: Ui, val dialog: Dialog, val body: LinearLayout) {

    /** Adds stacked full-width buttons under the body. */
    fun buttons(buttons: List<DialogButton>) {
        buttons.forEachIndexed { index, button ->
            val view = when (button.style) {
                ButtonStyle.PRIMARY -> ui.primaryButton(button.text)
                ButtonStyle.SECONDARY -> ui.secondaryButton(button.text)
                ButtonStyle.TEXT -> ui.textButton(button.text)
                ButtonStyle.DANGER -> ui.dangerButton(button.text)
            }
            view.setOnClickListener { if (button.onClick()) dialog.dismiss() }
            with(ui) { body.add(view, top = if (index == 0) 20 else 8) }
        }
    }

    fun show(): UiPanel {
        dialog.show()
        return this
    }

    fun dismiss() = dialog.dismiss()
}

/**
 * A frame at least [minHeightPx] tall and at least [ratio] times its width, growing if the content needs more.
 * Used for tiles, so large text makes a tile taller instead of cutting the name.
 */
internal class AspectFrame(context: Context, private val ratio: Float, private val minHeightPx: Int = 0) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        var wanted = max(max(measuredHeight, (width * ratio).toInt()), minHeightPx)
        // A grid row may ask for an exact height so that every tile in it is the same.
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) wanted = max(wanted, MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(wanted, MeasureSpec.EXACTLY))
    }
}

/**
 * Text that shrinks (down to [minSizePx]) until it fits [maxLines] without cutting a word in two, and only
 * then is cut with "…". The biggest size is [maxSizePx].
 */
internal class FitTextView(context: Context, private val minSizePx: Float, private val maxSizePx: Float) : TextView(context) {

    // The constructor takes sp values for convenience; they are turned into pixels on first use.
    private val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, minSizePx, resources.displayMetrics)
    private val maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, maxSizePx, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        if (available > 0 && text.isNotEmpty()) {
            var size = maxPx
            while (size > minPx && !fits(size, available)) size -= TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 0.5f, resources.displayMetrics)
            size = max(size, minPx)
            if (size != textSize) setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun fits(sizePx: Float, width: Int): Boolean {
        val paint = TextPaint(paint).apply { textSize = sizePx }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        if (layout.lineCount > maxLines) return false
        val last = layout.lineCount - 1
        if (layout.getEllipsisCount(last) > 0) return false
        // A word split across two lines does not count as fitting.
        for (line in 0 until last) {
            val end = layout.getLineEnd(line)
            if (end in 1 until text.length && !text[end - 1].isWhitespace() && text[end - 1] != '-' && !text[end].isWhitespace()) return false
        }
        return true
    }
}

/** A very soft shadow under a rounded [inner] drawable, drawn by hand (it needs no elevation, so it shows in screenshots too). */
internal class SoftShadowDrawable(
    private val inner: Drawable,
    private val radiusPx: Float,
    private val elevationPx: Float,
    private val alpha: Int,
    private val topInsetPx: Float = 0f,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    /** The room under the shape that the shadow uses; the shadow never goes outside the view, so nothing can cut it off. */
    private val room get() = (elevationPx * 1.5f).toInt()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        inner.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom - room)
    }

    override fun draw(canvas: Canvas) {
        val b = inner.bounds
        // Three soft layers, each a little lower than the one before and fainter.
        for (i in 2 downTo 0) {
            val drop = elevationPx * (0.5f + 0.5f * i)
            paint.color = Color.argb(alpha / (i + 1) + 2, 0, 0, 0)
            rect.set(b.left + elevationPx * 0.15f * i, b.top + topInsetPx + drop, b.right - elevationPx * 0.15f * i, b.bottom + drop)
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        }
        inner.draw(canvas)
    }

    override fun getOutline(outline: Outline) {
        inner.getOutline(outline)
    }

    override fun setAlpha(alpha: Int) {
        inner.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        inner.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** A slim rounded progress bar. */
internal class SlimProgress(context: Context, private val fraction: Float, private val track: Int, private val fill: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        paint.color = track
        rect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        if (fraction > 0f) {
            paint.color = fill
            rect.set(0f, 0f, max(h, width * fraction), h)
            canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        }
    }
}

/**
 * Buttons of equal width in a row. When a label would not fit on one line, the buttons are stacked
 * (full width, one under the other) instead of wrapping.
 */
internal class AdaptiveRow(context: Context, private val gapPx: Int) : LinearLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        orientation = HORIZONTAL
        applyLayout(horizontal = true)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (childrenWrap()) {
            orientation = VERTICAL
            applyLayout(horizontal = false)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun childrenWrap(): Boolean {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is TextView && child.lineCount > 1) return true
        }
        return false
    }

    private fun applyLayout(horizontal: Boolean) {
        for (i in 0 until childCount) {
            val params = getChildAt(i).layoutParams as LayoutParams
            if (horizontal) {
                params.width = 0
                params.weight = 1f
                params.setMargins(if (i == 0) 0 else gapPx / 2, 0, if (i == childCount - 1) 0 else gapPx / 2, 0)
            } else {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.weight = 0f
                params.setMargins(0, if (i == 0) 0 else gapPx, 0, 0)
            }
        }
    }
}

/** A horizontal row whose children all end up as tall as the tallest one. */
internal class EqualHeightRow(context: Context) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // A grid that wants every row the same height asks for an exact height.
        var tallest = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom else 0
        for (i in 0 until childCount) tallest = max(tallest, getChildAt(i).measuredHeight)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.measuredHeight != tallest) {
                child.measure(MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(tallest, MeasureSpec.EXACTLY))
            }
        }
        setMeasuredDimension(measuredWidth, tallest + paddingTop + paddingBottom)
    }
}

/** Rows stacked top to bottom, every row as tall as the tallest, so a grid of tiles is regular. */
internal class UniformColumn(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        var tallest = 0
        for (i in 0 until childCount) tallest = max(tallest, getChildAt(i).measuredHeight)
        var total = paddingTop + paddingBottom
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as MarginLayoutParams
            if (child.measuredHeight != tallest) {
                child.measure(MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(tallest, MeasureSpec.EXACTLY))
            }
            total += tallest + lp.topMargin + lp.bottomMargin
        }
        setMeasuredDimension(measuredWidth, total)
    }
}

/** A ScrollView that never grows taller than [maxHeightPx]. */
internal class MaxHeightScrollView(context: Context, private val maxHeightPx: Float) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeightPx.toInt(), MeasureSpec.AT_MOST))
    }
}

/** Children laid out left to right and wrapped onto new lines, with [gap] pixels between them. */
internal class UiFlow(context: Context, private val gap: Int) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var lineHeight = 0
        var widest = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChild(child, MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val w = child.measuredWidth
            if (x > 0 && x + w > maxWidth) {
                x = 0
                y += lineHeight + gap
                lineHeight = 0
            }
            x += w + gap
            widest = max(widest, x - gap)
            lineHeight = max(lineHeight, child.measuredHeight)
        }
        val height = y + lineHeight + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(widest + paddingLeft + paddingRight, widthMeasureSpec).let { if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthMeasureSpec) else it },
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val w = child.measuredWidth
            val h = child.measuredHeight
            if (x > 0 && x + w > maxWidth) {
                x = 0
                y += lineHeight + gap
                lineHeight = 0
            }
            child.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + w, paddingTop + y + h)
            x += w + gap
            lineHeight = max(lineHeight, h)
        }
    }
}

/** A folder: a rounded body with a small tab on the top left. */
internal class FolderDrawable(
    private val fill: Int,
    private val strokeColor: Int,
    private val strokePx: Float,
    private val radiusPx: Float,
    private val tabPx: Float,
    /** A colour for the tab, like a binder divider's (0 = same as the folder). */
    private val tabFill: Int = 0,
    /** Exercise book: how far two spare pages peek out behind the folder (0 = none), and the colour of their edges. */
    private val stackPx: Float = 0f,
    private val pageEdge: Int = 0,
) : Drawable() {

    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeJoin = Paint.Join.ROUND
    }

    private fun path(): Path {
        val b = bounds
        val inset = strokePx / 2f
        val left = b.left + inset + slack()
        val right = b.right - inset - stackPx - slack()
        val top = b.top + inset + slack()
        val bottom = b.bottom - inset - stackPx - slack()
        val bodyTop = top + tabPx
        val tabRight = left + (right - left) * 0.4f
        val r = min(radiusPx, (bottom - bodyTop) / 2f)
        val tr = min(r, tabPx + r / 2f)
        return Path().apply {
            addRoundRect(RectF(left, bodyTop, right, bottom), r, r, Path.Direction.CW)
            val tab = Path()
            tab.addRoundRect(RectF(left, top, tabRight, bodyTop + tr), floatArrayOf(tr, tr, tr, tr, 0f, 0f, 0f, 0f), Path.Direction.CW)
            op(tab, Path.Op.UNION)
        }
    }

    /** Room kept free round the folder, when it has spare pages, so a turned page never goes past the edge of the tile. */
    private fun slack(): Float = if (stackPx > 0f) stackPx * 0.4f else 0f

    /** Two spare pages behind the folder, each turned a little the other way, so it looks like a stack of paper. */
    private fun drawSparePages(canvas: Canvas, body: Path) {
        val box = RectF()
        body.computeBounds(box, true)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, stackPx / 5f)
            color = pageEdge
        }
        for (page in 2 downTo 1) {
            canvas.save()
            canvas.translate(stackPx * page * 0.4f, stackPx * page * 0.4f)
            canvas.rotate(if (page == 1) 1.2f else -1.2f, box.centerX(), box.centerY())
            canvas.drawPath(body, fillPaint)
            canvas.drawPath(body, edge)
            canvas.restore()
        }
    }

    /** Just the tab, for a binder-divider colour. */
    private fun tabPath(): Path {
        val b = bounds
        val inset = strokePx / 2f
        val left = b.left + inset + slack()
        val right = b.right - inset - stackPx - slack()
        val top = b.top + inset + slack()
        val tabRight = left + (right - left) * 0.4f
        val r = min(radiusPx, tabPx)
        return Path().apply {
            addRoundRect(RectF(left, top, tabRight, top + tabPx + 1f), floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
        }
    }

    override fun draw(canvas: Canvas) {
        val path = path()
        if (stackPx > 0f) drawSparePages(canvas, path)
        canvas.drawPath(path, fillPaint)
        if (tabFill != 0) {
            tabPaint.color = tabFill
            canvas.drawPath(tabPath(), tabPaint)
        }
        if (strokePx > 0f) canvas.drawPath(path, strokePaint)
    }

    override fun getOutline(outline: Outline) {
        val b = bounds
        outline.setRoundRect(b.left, b.top + tabPx.toInt(), b.right, b.bottom, radiusPx)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

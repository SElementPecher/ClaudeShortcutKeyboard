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
import android.text.InputType
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
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.look.Palette
import io.github.selementpecher.shortcutkeyboard.look.TileLayout
import io.github.selementpecher.shortcutkeyboard.look.tileColorsFor
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf
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

/** The letter or picture shown on a tile: the icon if there is one, else the name's first letter (upper case). */
internal fun glyphFor(icon: String, name: String, isFolder: Boolean = false): String {
    if (icon.isNotBlank()) return icon.trim()
    if (isFolder) return "📁"
    val first = name.trim().firstOrNull { it.isLetterOrDigit() } ?: return "•"
    return first.uppercaseChar().toString()
}

/**
 * The app's design system: builds every view in code from the current [Look], so the app matches the
 * keyboard. Make one with `Ui(this, LookStore.current(this))` in `onCreate`, keep it, and call [isStale]
 * in `onResume`: if it is true the Look (or light/dark) changed, so call `recreate()`.
 */
internal class Ui(val context: Context, val look: Look) {

    /** Colours for the phone's current light or dark setting. */
    val palette: Palette = look.palette(context)

    private val density = context.resources.displayMetrics.density
    private val nightWhenBuilt = Look.isNight(context)

    /** dp to pixels. */
    fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    /** dp to pixels, keeping fractions. */
    fun dpf(value: Float): Float = value * density

    /** True when the chosen Look or light/dark setting is no longer the one this Ui was built with. */
    fun isStale(): Boolean = LookStore.current(context).id != look.id || Look.isNight(context) != nightWhenBuilt

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

    // ---------------------------------------------------------------- text

    /** Any text. [weight] is 100 to 900. */
    fun text(text: CharSequence, sizeSp: Float, weight: Int, color: Int): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = typefaceOf(weight)
        setTextColor(color)
        setLineSpacing(0f, 1.15f)
        includeFontPadding = false
        layoutParams = lp()
    }

    /** The big screen title. */
    fun title(text: CharSequence): TextView = text(text, look.type.titleSp, look.type.headingWeight, palette.ink)

    /** A section heading. */
    fun heading(text: CharSequence): TextView = text(text, look.type.headingSp, look.type.headingWeight, palette.ink)

    /** Normal reading text. */
    fun body(text: CharSequence): TextView = text(text, look.type.bodySp, 400, palette.ink)

    /** Smaller, softer text for helper lines. Never below the Look's small size. */
    fun small(text: CharSequence): TextView = text(text, look.type.smallSp, 400, palette.inkSoft)

    /** A small bold caption above a field or group. */
    fun label(text: CharSequence): TextView = text(text, look.type.smallSp, 600, palette.inkSoft)

    // ---------------------------------------------------------------- buttons

    private fun buttonView(text: CharSequence, fg: Int, bg: Drawable, onClick: (() -> Unit)?): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, look.type.bodySp)
        typeface = typefaceOf(max(600, look.type.headingWeight))
        setTextColor(fg)
        minHeight = dp(MAIN_ACTION_DP)
        setPadding(dp(24), dp(8), dp(24), dp(8))
        background = bg
        isClickable = true
        isFocusable = true
        includeFontPadding = false
        setOnClickListener { onClick?.invoke() }
        layoutParams = lp()
    }

    /** The main action: accent fill, at least 56dp tall. */
    fun primaryButton(text: CharSequence, onClick: (() -> Unit)? = null): TextView {
        val r = look.shape.buttonRadiusDp.toFloat()
        return buttonView(text, palette.onAccent, rippled(shape(palette.accent, r), r), onClick)
    }

    /** A second choice: a quiet tinted (or outlined) button. */
    fun secondaryButton(text: CharSequence, onClick: (() -> Unit)? = null): TextView {
        val r = look.shape.buttonRadiusDp.toFloat()
        val bg = if (look.shape.strokeDp > 0f) shape(Color.TRANSPARENT, r, palette.outline, look.shape.strokeDp)
        else shape(palette.surfaceAlt, r)
        return buttonView(text, palette.ink, rippled(bg, r), onClick)
    }

    /** A plain text button for small choices such as Cancel. */
    fun textButton(text: CharSequence, onClick: (() -> Unit)? = null): TextView {
        val r = look.shape.buttonRadiusDp.toFloat()
        val fg = if (look.id == "bold" && palette.accent == Color.parseColor("#0B0C10")) palette.ink else palette.accent
        return buttonView(text, fg, rippled(shape(Color.TRANSPARENT, r), r), onClick)
    }

    /** An outlined red button for deleting things. */
    fun dangerButton(text: CharSequence, onClick: (() -> Unit)? = null): TextView {
        val r = look.shape.buttonRadiusDp.toFloat()
        val bg = shape(Color.TRANSPARENT, r, palette.danger, max(1.5f, look.shape.strokeDp))
        return buttonView(text, palette.danger, rippled(bg, r), onClick)
    }

    /** A round button (48dp) with a drawn icon, e.g. [UiIcon.Kind.BACK]. */
    fun iconButton(kind: UiIcon.Kind, description: String, onClick: () -> Unit): ImageView = ImageView(context).apply {
        setImageDrawable(UiIcon(kind, palette.ink, dp(24)))
        scaleType = ImageView.ScaleType.CENTER
        background = rippled(shape(Color.TRANSPARENT, 999f), 999f)
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }

    // ---------------------------------------------------------------- containers and inputs

    /** A rounded surface that holds a group of things. Add children to it. */
    fun card(paddingDp: Int = 20): LinearLayout = column().apply {
        val r = look.shape.cardRadiusDp.toFloat()
        background = shape(palette.surface, r, palette.outline, look.shape.strokeDp)
        setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp))
        if (look.shape.elevationDp > 0f) elevation = dpf(look.shape.elevationDp)
        layoutParams = lp()
    }

    /** A text box with a hint. Single line unless [multiline]. */
    fun field(hint: CharSequence, multiline: Boolean = false): EditText = EditText(context).apply {
        this.hint = hint
        setTextSize(TypedValue.COMPLEX_UNIT_SP, look.type.bodySp)
        typeface = typefaceOf(400)
        setTextColor(palette.ink)
        setHintTextColor(palette.inkSoft)
        val r = min(look.shape.cardRadiusDp, 16).toFloat()
        val strokeDp = max(1.5f, look.shape.strokeDp)
        val rest = if (look.shape.strokeDp > 0f) shape(palette.surface, r, palette.outline, look.shape.strokeDp)
        else shape(palette.surfaceAlt, r)
        val focus = shape(if (look.shape.strokeDp > 0f) palette.surface else palette.surfaceAlt, r, palette.accent, max(2f, strokeDp))
        background = android.graphics.drawable.StateListDrawable().apply {
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
        minHeight = dp(48)
        setPadding(dp(18), dp(8), dp(18), dp(8))
        val r = 999f
        val bg = when {
            selected -> shape(palette.accent, r)
            look.shape.strokeDp > 0f -> shape(Color.TRANSPARENT, r, palette.outline, look.shape.strokeDp)
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
    fun grid(columns: Int, cells: List<View>, gapDp: Int = 12): LinearLayout {
        val grid = column()
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
                val cell = chunk.getOrNull(i) ?: View(context).apply { visibility = View.INVISIBLE }
                (cell.parent as? ViewGroup)?.removeView(cell)
                line.addView(cell, lp(width = 0, height = ViewGroup.LayoutParams.WRAP_CONTENT, start = if (i == 0) 0 else gapDp, weight = 1f))
            }
            grid.addView(line)
        }
        return grid
    }

    // ---------------------------------------------------------------- tiles

    /**
     * A tile as on the keyboard. WIDE Looks: the picture (or first letter) in a soft circle at the top left, the
     * name below. SQUARE Look: a big centred picture with a small name under it. Folders have a folder shape.
     *
     * @param colorKey picks the tile colour (use the shortcut id) so it always looks the same.
     * @param subtitle an optional small line under the name, e.g. "3 items".
     */
    fun tile(
        name: String,
        icon: String,
        kind: TileKind,
        colorKey: String = name,
        subtitle: String? = null,
        description: String = name,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
    ): View {
        val colors = when (kind) {
            TileKind.SHORTCUT -> palette.tileColorsFor(colorKey)
            TileKind.FOLDER -> palette.folder
            TileKind.NEW -> io.github.selementpecher.shortcutkeyboard.look.TileColors(Color.TRANSPARENT, palette.inkSoft)
        }
        val radius = look.shape.tileRadiusDp.toFloat()
        val tile = AspectFrame(context, if (isWide) WIDE_RATIO else SQUARE_RATIO)
        tile.clipChildren = false
        val folderLike = kind == TileKind.FOLDER
        val base: Drawable = when {
            kind == TileKind.NEW -> shape(Color.TRANSPARENT, radius, palette.outline.let { if (look.shape.strokeDp > 0f) it else palette.inkSoft }, max(1.5f, look.shape.strokeDp), dashed = true)
            folderLike -> FolderDrawable(
                fill = colors.bg,
                strokeColor = if (look.id == "bold") palette.accent else palette.outline,
                strokePx = when {
                    look.id == "bold" -> dpf(2f)
                    look.shape.strokeDp > 0f -> dpf(look.shape.strokeDp)
                    else -> 0f
                },
                radiusPx = dpf(radius),
                tabPx = dpf(if (isWide) 9f else 7f),
            )
            else -> shape(colors.bg, radius, palette.outline, if (look.shape.strokeDp > 0f) look.shape.strokeDp else 0f)
        }
        tile.background = rippled(base, radius)
        if (look.shape.elevationDp > 0f && kind != TileKind.NEW) tile.elevation = dpf(look.shape.elevationDp)
        tile.contentDescription = description
        tile.isClickable = onClick != null || onLongClick != null
        tile.isFocusable = tile.isClickable
        tile.setOnClickListener(onClick?.let { handler -> View.OnClickListener { handler() } })
        if (onLongClick != null) tile.setOnLongClickListener { onLongClick(); true }

        val glyph = if (kind == TileKind.NEW) "+" else glyphFor(icon, name, folderLike)
        val topPad = if (folderLike) dp(if (isWide) 9 else 7) else 0
        val inner = column().apply {
            clipChildren = false
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val nameView = text(name, look.type.tileSp, look.type.tileWeight, colors.fg).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val subView = subtitle?.let {
            text(it, look.type.smallSp, 500, colors.fg).apply {
                alpha = 0.78f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        }
        if (isWide) {
            inner.setPadding(dp(16), dp(14) + topPad, dp(16), dp(14))
            val circle = TextView(context).apply {
                text = glyph
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (glyph.length > 1 || glyph.codePointAt(0) > 0x2000) 22f else 20f)
                typeface = typefaceOf(look.type.tileWeight)
                setTextColor(colors.fg)
                includeFontPadding = false
                background = shape(if (kind == TileKind.NEW) Color.TRANSPARENT else withAlpha(colors.fg, 0x26), 999f)
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            }
            inner.addView(circle)
            inner.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f).apply { height = 0; width = ViewGroup.LayoutParams.MATCH_PARENT })
            inner.addView(nameView.apply { layoutParams = lp(top = 10) })
            subView?.let { inner.addView(it.apply { layoutParams = lp(top = 2) }) }
        } else {
            inner.setPadding(dp(8), dp(8) + topPad, dp(8), dp(10))
            inner.gravity = Gravity.CENTER
            val big = TextView(context).apply {
                text = glyph
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                typeface = typefaceOf(look.type.tileWeight)
                setTextColor(colors.fg)
                includeFontPadding = false
                layoutParams = lp(height = ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            inner.addView(big)
            inner.addView(nameView.apply {
                gravity = Gravity.CENTER
                layoutParams = lp(top = 6)
            })
            subView?.let { inner.addView(it.apply { gravity = Gravity.CENTER; layoutParams = lp(top = 1) }) }
        }
        tile.addView(inner)
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
    ): UiScreen {
        val root = column().apply {
            setBackgroundColor(palette.bg)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val topBar = column()
        val bar = row().apply { setPadding(dp(if (onBack == null) 20 else 8), dp(8), dp(8), dp(8)) }
        if (onBack != null) bar.addView(iconButton(UiIcon.Kind.BACK, context.getString(io.github.selementpecher.shortcutkeyboard.R.string.action_back), onBack))
        val titleView = (if (large) title(title) else heading(title)).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = lp(width = 0, weight = 1f, start = if (onBack == null) 0 else 6, end = 8)
        }
        bar.addView(titleView)
        trailing.forEach { bar.addView(it) }
        topBar.addView(bar, lp(height = dp(56)))
        root.addView(topBar, lp())

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }
        val content = column().apply {
            setPadding(dp(20), dp(8), dp(20), dp(24))
            clipChildren = false
            clipToPadding = false
        }
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, lp(height = 0, weight = 1f))

        val bottom = column().apply { setPadding(dp(20), dp(12), dp(20), dp(12)) }
        val bottomWrap = column().apply {
            setBackgroundColor(palette.bg)
            addView(divider())
            addView(bottom)
            visibility = if (withBottomBar) View.VISIBLE else View.GONE
        }
        root.addView(bottomWrap, lp())
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
            DialogButton(confirmText.toString(), if (danger) ButtonStyle.DANGER else ButtonStyle.PRIMARY) { onConfirm(); true },
            DialogButton(cancelText.toString(), ButtonStyle.TEXT),
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
            setPadding(dp(20), dp(if (bottom) 12 else 22), dp(20), dp(if (bottom) 20 else 18))
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
            if (!bottom) setPadding(dp(20), 0, dp(20), 0)
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

        private const val WIDE_RATIO = 0.7f
        private const val SQUARE_RATIO = 1.0f

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
    /** Show or hide the bottom action area. */
    fun showBottomBar(show: Boolean) {
        bottomWrap.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** Make this the activity's content with edge-to-edge padding and the Look's background behind it. */
    fun install(activity: Activity) {
        activity.window.setBackgroundDrawable(ColorDrawable(ui.palette.bg))
        activity.setContentView(root)
        EdgeToEdge.apply(activity, root, topBar)
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

/** A frame whose height is at least [ratio] times its width, growing if the content needs more. */
internal class AspectFrame(context: Context, private val ratio: Float) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val wanted = max(measuredHeight, (width * ratio).toInt())
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(wanted, MeasureSpec.EXACTLY))
    }
}

/** A horizontal row whose children all end up as tall as the tallest one. */
internal class EqualHeightRow(context: Context) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var tallest = 0
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

/** Simple line icons drawn in code (no image files). */
internal class UiIcon(private val kind: Kind, color: Int, private val sizePx: Int) : Drawable() {

    enum class Kind { BACK, CLOSE, GEAR, PLUS, CHECK }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        paint.strokeWidth = s * 0.1f
        when (kind) {
            Kind.BACK -> {
                val path = Path().apply {
                    moveTo(cx + s * 0.12f, cy - s * 0.3f)
                    lineTo(cx - s * 0.18f, cy)
                    lineTo(cx + s * 0.12f, cy + s * 0.3f)
                }
                canvas.drawPath(path, paint)
            }
            Kind.CLOSE -> {
                val d = s * 0.26f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint)
                canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint)
            }
            Kind.PLUS -> {
                val d = s * 0.3f
                canvas.drawLine(cx - d, cy, cx + d, cy, paint)
                canvas.drawLine(cx, cy - d, cx, cy + d, paint)
            }
            Kind.CHECK -> {
                val path = Path().apply {
                    moveTo(cx - s * 0.3f, cy + s * 0.02f)
                    lineTo(cx - s * 0.08f, cy + s * 0.24f)
                    lineTo(cx + s * 0.32f, cy - s * 0.22f)
                }
                canvas.drawPath(path, paint)
            }
            Kind.GEAR -> {
                canvas.drawCircle(cx, cy, s * 0.2f, paint)
                paint.strokeWidth = s * 0.13f
                paint.strokeCap = Paint.Cap.BUTT
                for (i in 0 until 8) {
                    canvas.save()
                    canvas.rotate(45f * i, cx, cy)
                    canvas.drawLine(cx, cy - s * 0.3f, cx, cy - s * 0.42f, paint)
                    canvas.restore()
                }
                paint.strokeCap = Paint.Cap.ROUND
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** A folder: a rounded body with a small tab on the top left. */
internal class FolderDrawable(
    private val fill: Int,
    private val strokeColor: Int,
    private val strokePx: Float,
    private val radiusPx: Float,
    private val tabPx: Float,
) : Drawable() {

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
        val left = b.left + inset
        val right = b.right - inset
        val top = b.top + inset
        val bottom = b.bottom - inset
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

    override fun draw(canvas: Canvas) {
        val path = path()
        canvas.drawPath(path, fillPaint)
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

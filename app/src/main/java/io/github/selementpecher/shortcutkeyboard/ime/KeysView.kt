package io.github.selementpecher.shortcutkeyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.selementpecher.shortcutkeyboard.R
import java.util.EnumMap

/**
 * The key area: rows of [KeyView]s for the current [KeyboardPage].
 *
 * Each page is built once and kept; [render] just shows the right one and updates the few keys
 * that change (letters with shift, the shift key, the enter key).
 */
@SuppressLint("ViewConstructor")
internal class KeysView(
    context: Context,
    private val listener: KeyView.Listener,
) : FrameLayout(context) {

    /** How the enter key looks: a word such as "Send", or an icon. */
    data class EnterLook(val label: String?, val iconRes: Int, val description: String)

    private class Page(val view: LinearLayout, val keys: List<KeyView>)

    private val pages = EnumMap<KeyboardPage, Page>(KeyboardPage::class.java)
    private val gap = context.px(R.dimen.kb_key_gap)
    private val accentTextColor = context.getColor(R.color.kb_on_accent)

    private var builtWithGlobe: Boolean? = null
    private var builtKeyHeight = -1
    private var shownPage: KeyboardPage? = null
    private var shownShift: ShiftState.Mode? = null
    private var shownEnter: EnterLook? = null

    init {
        // QWERTY is always left to right, whatever the phone's language.
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setPadding(gap / 2, gap / 2, gap / 2, gap / 2)
    }

    /** Height of the whole key area for a given key height, gaps included. */
    fun heightFor(keyHeight: Int): Int = ROWS * (keyHeight + gap) + gap

    fun render(
        page: KeyboardPage,
        shift: ShiftState.Mode,
        labelFor: (Key) -> String,
        enter: EnterLook,
        showNextKeyboard: Boolean,
        keyHeight: Int,
    ) {
        if (builtWithGlobe != showNextKeyboard || builtKeyHeight != keyHeight) {
            stopAll()
            removeAllViews()
            pages.clear()
            shownPage = null
            builtWithGlobe = showNextKeyboard
            builtKeyHeight = keyHeight
        }
        val current = pages[page] ?: buildPage(page, showNextKeyboard, keyHeight).also {
            pages[page] = it
            addView(it.view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        val pageChanged = page != shownPage
        if (pageChanged) {
            for ((p, built) in pages) built.view.visibility = if (p == page) View.VISIBLE else View.GONE
        }
        for (key in current.keys) {
            when (key.key.kind) {
                KeyKind.CHARACTER -> {
                    val label = labelFor(key.key)
                    if (key.text.toString() != label) key.text = label
                }
                KeyKind.SHIFT -> if (pageChanged || shift != shownShift) showShift(key, shift)
                KeyKind.ENTER -> if (pageChanged || enter != shownEnter) showEnter(key, enter)
                else -> Unit
            }
        }
        shownPage = page
        shownShift = shift
        shownEnter = enter
    }

    /** Lets go of every key, stopping delete repeat and long presses (e.g. when the keyboard hides). */
    fun stopAll() {
        pages.values.forEach { page -> page.keys.forEach { it.stop() } }
    }

    private fun buildPage(page: KeyboardPage, showNextKeyboard: Boolean, keyHeight: Int): Page {
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val keys = ArrayList<KeyView>()
        for (row in KeyboardLayouts.rows(page, showNextKeyboard)) {
            val rowView = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (key in row) {
                val view = createKey(key)
                rowView.addView(view, LinearLayout.LayoutParams(0, keyHeight + gap, key.weight))
                keys += view
            }
            column.addView(rowView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        return Page(column, keys)
    }

    private fun createKey(key: Key): KeyView {
        val view = KeyView(context, key, listener, gap)
        when (key.kind) {
            KeyKind.CHARACTER -> view.text = key.label
            KeyKind.DELETE -> {
                view.icon = context.getDrawable(R.drawable.kb_ic_backspace)
                view.contentDescription = context.getString(R.string.kb_desc_delete)
            }
            KeyKind.SPACE -> view.contentDescription = context.getString(R.string.kb_desc_space)
            KeyKind.NEXT_KEYBOARD -> {
                view.icon = context.getDrawable(R.drawable.kb_ic_globe)
                view.contentDescription = context.getString(R.string.kb_desc_next_keyboard)
            }
            KeyKind.TO_SYMBOLS -> pageKey(view, key, R.string.kb_desc_symbols)
            KeyKind.TO_MORE_SYMBOLS -> pageKey(view, key, R.string.kb_desc_more_symbols)
            KeyKind.TO_LETTERS -> pageKey(view, key, R.string.kb_desc_letters)
            KeyKind.SHIFT, KeyKind.ENTER -> Unit // set in render()
        }
        return view
    }

    private fun pageKey(view: KeyView, key: Key, descriptionRes: Int) {
        view.text = key.label
        view.contentDescription = context.getString(descriptionRes)
    }

    private fun showShift(view: KeyView, mode: ShiftState.Mode) {
        val (iconRes, descriptionRes) = when (mode) {
            ShiftState.Mode.OFF -> R.drawable.kb_ic_shift to R.string.kb_desc_shift
            ShiftState.Mode.ONCE -> R.drawable.kb_ic_shift_on to R.string.kb_desc_shift_on
            ShiftState.Mode.LOCKED -> R.drawable.kb_ic_caps_lock to R.string.kb_desc_caps_lock
        }
        view.icon = context.getDrawable(iconRes)
        view.isActivated = mode != ShiftState.Mode.OFF
        view.contentDescription = context.getString(descriptionRes)
    }

    private fun showEnter(view: KeyView, look: EnterLook) {
        view.setTextColor(accentTextColor)
        if (look.label != null) {
            view.text = look.label
            view.icon = null
        } else {
            view.text = ""
            view.icon = if (look.iconRes != 0) context.getDrawable(look.iconRes) else null
        }
        view.contentDescription = look.description
    }

    companion object {
        const val ROWS = 4
    }
}

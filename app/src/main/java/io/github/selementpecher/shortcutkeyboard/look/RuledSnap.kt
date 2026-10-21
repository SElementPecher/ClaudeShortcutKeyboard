package io.github.selementpecher.shortcutkeyboard.look

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Makes text with no background of its own sit on a ruled line, like handwriting on paper.
 * "Plain" text is a TextView with no background, and none behind it (a tile, card or button is not plain).
 */
internal object RuledSnap {

    /** Marks the view whose background is the ruled paper itself, which does not count as a background. */
    val PAPER = Any()

    /** Buttons that should rest on a line like plain text, e.g. a bare red "Delete everything". */
    private val alsoSnap: MutableSet<TextView> = Collections.newSetFromMap(WeakHashMap())

    fun snapToo(view: TextView) {
        alsoSnap += view
    }

    fun isPlain(t: TextView, root: View): Boolean {
        if (t is EditText || t.visibility != View.VISIBLE || t.text.isNullOrEmpty() || t.isClickable) return false
        var v: View? = t
        while (v != null && v !== root) {
            if (v.visibility != View.VISIBLE) return false
            if (v.background != null && v.tag !== PAPER) return false
            v = v.parent as? View
        }
        return true
    }

    /** Every plain text under [root], in the order they are laid out. */
    fun plainTexts(root: ViewGroup): List<TextView> {
        val found = ArrayList<TextView>()
        fun walk(group: ViewGroup) {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is TextView) {
                    if (isPlain(child, root) || (child in alsoSnap && child.visibility == View.VISIBLE)) found += child
                } else if (child is ViewGroup && child.visibility == View.VISIBLE) walk(child)
            }
        }
        walk(root)
        return found
    }

    /** Where [t]'s first baseline is, measured from the top of [root] (not counting any scrolling). */
    fun baselineIn(root: View, t: TextView): Float {
        var y = t.baseline.toFloat()
        var v: View = t
        while (v !== root) {
            y += v.top
            v = v.parent as? View ?: break
        }
        return y
    }
}

/**
 * Keeps an app screen's text on the lines. Each plain text gets a line height equal to the rule spacing (so more lines
 * stay on the rules), then is pushed down, a little at a time, until its baseline rests on a rule. The paper inside the
 * scrolling area is shifted so its rules line up with the page's, so they stay lined up as it scrolls.
 */
internal class RuledPageSnap(
    private val root: ViewGroup,
    private val rootPaper: RuledPaper,
    private val contentPaper: RuledPaper,
    private val scroll: View,
) : ViewTreeObserver.OnPreDrawListener {

    private val styled: MutableSet<TextView> = Collections.newSetFromMap(WeakHashMap())
    private val tries = WeakHashMap<TextView, Int>()

    fun install() {
        root.viewTreeObserver.addOnPreDrawListener(this)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                root.viewTreeObserver.removeOnPreDrawListener(this@RuledPageSnap)
                root.viewTreeObserver.addOnPreDrawListener(this@RuledPageSnap)
            }

            override fun onViewDetachedFromWindow(v: View) {
                root.viewTreeObserver.removeOnPreDrawListener(this@RuledPageSnap)
            }
        })
    }

    override fun onPreDraw(): Boolean {
        val gap = rootPaper.gapPx
        // The scrolling paper's first rule is where the page's rules are.
        contentPaper.phasePx = ((gap - scroll.top % gap) % gap)
        for (t in RuledSnap.plainTexts(root)) {
            if (styled.add(t)) {
                val fm = t.paint.fontMetrics
                t.setLineSpacing(max(0f, gap - (fm.descent - fm.ascent)), 1f)
                return false
            }
            val base = RuledSnap.baselineIn(root, t)
            val line = ceil((base - 1f) / gap) * gap
            val delta = line - base
            val used = tries[t] ?: 0
            if (delta > 1f && used < MAX_TRIES) {
                tries[t] = used + 1
                t.setPadding(t.paddingLeft, t.paddingTop + delta.roundToInt(), t.paddingRight, t.paddingBottom)
                return false
            }
        }
        return true
    }

    private companion object {
        const val MAX_TRIES = 4
    }
}

/**
 * For the keyboard, which has a fixed height and cannot move its parts: the rules are shifted so one passes under the
 * first plain text (the question), instead of moving the text.
 */
internal class RuledKeyboardSnap(private val root: ViewGroup, private val paper: RuledPaper) : ViewTreeObserver.OnPreDrawListener {

    fun install() {
        root.viewTreeObserver.addOnPreDrawListener(this)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                root.viewTreeObserver.removeOnPreDrawListener(this@RuledKeyboardSnap)
                root.viewTreeObserver.addOnPreDrawListener(this@RuledKeyboardSnap)
            }

            override fun onViewDetachedFromWindow(v: View) {
                root.viewTreeObserver.removeOnPreDrawListener(this@RuledKeyboardSnap)
            }
        })
    }

    override fun onPreDraw(): Boolean {
        val gap = paper.gapPx
        val first = RuledSnap.plainTexts(root).firstOrNull()
        paper.phasePx = if (first == null) 0f else ((RuledSnap.baselineIn(root, first) % gap) + gap) % gap
        return true
    }
}

package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.selementpecher.shortcutkeyboard.look.Icons
import kotlin.math.max

/**
 * A short message at the bottom of the screen ("Saved", "Moved", a problem), in the Look's colours.
 * It goes away by itself after about 2.5 seconds, or at a tap anywhere on it.
 */
internal object Snackbars {

    private const val SHOW_MS = 2500L
    private const val TAG_VIEW = "snackbar"

    private class Waiting(val text: String, val error: Boolean)

    private var waiting: Waiting? = null

    /**
     * Keeps a message to show on the next screen that calls [showWaiting], for when the screen that
     * wants to say it closes straight away (for example "Saved" after Save).
     */
    fun later(text: String, error: Boolean = false) {
        waiting = Waiting(text, error)
    }

    /** Shows the message kept by [later], if any. */
    fun showWaiting(activity: Activity, ui: Ui, bottomPx: Int = 0) {
        val message = waiting ?: return
        waiting = null
        show(activity, ui, message.text, message.error, bottomPx)
    }

    /**
     * @param bottomPx extra space above the bottom edge, e.g. the height of a button bar, so the message
     *   does not cover it. The navigation bar is already counted.
     */
    fun show(activity: Activity, ui: Ui, text: CharSequence, error: Boolean = false, bottomPx: Int = 0) {
        val host = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        host.findViewWithTag<View>(TAG_VIEW)?.let { host.removeView(it) }

        val palette = ui.palette
        val fill = if (error) palette.danger else palette.ink
        val fg = if (error) ui.readableOn(palette.danger) else palette.bg
        val radius = if (ui.look.shape.buttonRadiusDp >= 100) 999f else ui.look.shape.buttonRadiusDp.toFloat().coerceAtLeast(14f)

        val bar = LinearLayout(activity).apply {
            tag = TAG_VIEW
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(56)
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(10))
            background = ui.shape(fill, radius)
            elevation = ui.dpf(6f)
            isClickable = true
            isFocusable = true
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            contentDescription = text
        }
        if (!error) {
            bar.addView(
                ImageView(activity).apply { setImageDrawable(ui.icon(Icons.Kind.CHECK, 22, fg)) },
                LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)).apply { marginEnd = ui.dp(12) },
            )
        }
        bar.addView(
            ui.text(text, ui.look.type.bodySp, 600, fg).apply { layoutParams = ui.lp(width = 0, weight = 1f) },
        )

        val margin = ui.dp(20)
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        params.setMargins(margin, 0, margin, navigationInset(activity) + bottomPx + ui.dp(16))
        host.addView(bar, params)

        val animate = Motion.enabled(activity)
        fun hide() {
            if (bar.parent == null) return
            if (animate) {
                bar.animate().alpha(0f).translationY(ui.dpf(12f)).setDuration(Motion.PRESS_MS)
                    .withEndAction { (bar.parent as? ViewGroup)?.removeView(bar) }.start()
            } else {
                (bar.parent as? ViewGroup)?.removeView(bar)
            }
        }
        bar.setOnClickListener { hide() }
        bar.postDelayed({ hide() }, SHOW_MS)
        if (animate) {
            bar.alpha = 0f
            bar.translationY = ui.dpf(16f)
            bar.animate().alpha(1f).translationY(0f).setDuration(Motion.POP_MS).start()
        }
        bar.announceForAccessibility(text)
    }

    @Suppress("DEPRECATION")
    private fun navigationInset(activity: Activity): Int {
        val insets = activity.window.decorView.rootWindowInsets ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            max(0, insets.getInsets(android.view.WindowInsets.Type.systemBars()).bottom)
        } else {
            insets.systemWindowInsetBottom
        }
    }
}

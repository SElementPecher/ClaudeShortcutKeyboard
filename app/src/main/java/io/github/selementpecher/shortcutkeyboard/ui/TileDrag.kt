package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.ime.DragPlanner
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Press and hold a tile on the home screen to pick it up. While the finger is down the tile follows it:
 * over another tile it will take that tile's place, over the middle of a folder it goes inside, and over the
 * "up" pill (inside a folder) it moves up one level. Letting go without moving opens the tile's menu instead.
 *
 * Start with [start] from the tile's long press, and send every touch to [handle] from `dispatchTouchEvent`.
 */
internal class TileDrag(
    private val activity: Activity,
    private val ui: Ui,
    private val scroll: ScrollView,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        /** The tiles of the folder on screen, in order: id and view. */
        fun tiles(): List<Pair<String, View>>

        fun folderIds(): Set<String>

        /** The words on the "up" pill, or null when there is nowhere to go up to (top level). */
        fun upLabel(): String?

        fun onDrop(outcome: DragPlanner.Outcome)

        /** Picked up and put down without moving: show the menu. */
        fun onHold(id: String)
    }

    private val slop = ViewConfiguration.get(activity).scaledTouchSlop
    private val host: ViewGroup get() = activity.findViewById(android.R.id.content)

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    private var draggedId: String? = null
    private var draggedView: View? = null
    private var ghost: ImageView? = null
    private var upPill: TextView? = null
    private var hover: View? = null
    private var hoverIndex: Int? = null
    private var hoverCentre = false
    private var overUp = false
    private var moved = false
    private var scrolling = false

    val isActive: Boolean get() = draggedId != null

    /** The tile that was carried most recently (still set while its drop is being applied). */
    var lastDraggedId: String? = null
        private set

    /** Sends the screen's touches here. Returns true while a tile is being carried (the touch is used up). */
    fun handle(event: MotionEvent): Boolean {
        lastX = event.rawX
        lastY = event.rawY
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = lastX
            downY = lastY
            downTime = event.downTime
        }
        if (!isActive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (hypot(lastX - downX, lastY - downY) > slop * 2) moved = true
                follow()
            }
            MotionEvent.ACTION_UP -> finish(drop = true)
            MotionEvent.ACTION_CANCEL -> finish(drop = false)
        }
        return true
    }

    /** Picks the tile up. The finger must still be down. */
    fun start(id: String, tile: View) {
        if (isActive) return
        draggedId = id
        draggedView = tile
        moved = false

        // The tile's own press is over: the finger now belongs to the drag.
        val cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, lastX, lastY, 0)
        activity.window.decorView.dispatchTouchEvent(cancel)
        cancel.recycle()
        scroll.requestDisallowInterceptTouchEvent(true)

        val picture = Bitmap.createBitmap(tile.width.coerceAtLeast(1), tile.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        tile.draw(Canvas(picture))
        ghost = ImageView(activity).apply {
            setImageBitmap(picture)
            layoutParams = FrameLayout.LayoutParams(tile.width, tile.height)
            elevation = ui.dpf(14f)
            scaleX = 1.06f
            scaleY = 1.06f
            alpha = 0.95f
            isClickable = false
        }
        host.addView(ghost)
        tile.alpha = 0.25f

        callbacks.upLabel()?.let { label ->
            upPill = TextView(activity).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, ui.look.type.bodySp)
                setTextColor(ui.palette.ink)
                typeface = io.github.selementpecher.shortcutkeyboard.look.typefaceOf(600)
                background = ui.shape(ui.palette.surface, 999f, ui.palette.outline, 1.5f)
                elevation = ui.dpf(8f)
                setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(12))
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            }
            host.addView(upPill)
            val at = IntArray(2)
            scroll.getLocationOnScreen(at)
            val hostAt = IntArray(2)
            host.getLocationOnScreen(hostAt)
            upPill?.translationY = (at[1] - hostAt[1] + ui.dp(8)).toFloat()
        }
        follow()
        scrollLoop()
    }

    private fun follow() {
        val view = ghost ?: return
        val hostAt = IntArray(2)
        host.getLocationOnScreen(hostAt)
        view.translationX = lastX - hostAt[0] - view.layoutParams.width / 2f
        view.translationY = lastY - hostAt[1] - view.layoutParams.height / 2f
        updateHover()
    }

    private fun rectOf(view: View): Rect? {
        val r = Rect()
        return if (view.getGlobalVisibleRect(r)) r else null
    }

    private fun updateHover() {
        val x = lastX.toInt()
        val y = lastY.toInt()
        val pill = upPill
        val onPill = pill != null && rectOf(pill)?.contains(x, y) == true
        var found: View? = null
        var index: Int? = null
        var centre = false
        if (!onPill) {
            callbacks.tiles().forEachIndexed { i, (id, view) ->
                if (id == draggedId) return@forEachIndexed
                val r = rectOf(view) ?: return@forEachIndexed
                if (r.contains(x, y)) {
                    found = view
                    index = i
                    centre = abs(x - r.exactCenterX()) <= r.width() / 4f && abs(y - r.exactCenterY()) <= r.height() / 4f
                }
            }
        }
        // Over the dragged tile's own place: "put it back".
        if (found == null && !onPill) {
            val own = draggedView?.let { rectOf(it) }
            if (own != null && own.contains(x, y)) {
                index = callbacks.tiles().indexOfFirst { it.first == draggedId }.takeIf { it >= 0 }
            }
        }
        if (found !== hover || index != hoverIndex || centre != hoverCentre || onPill != overUp) {
            clearHighlight()
            hover = found
            hoverIndex = index
            hoverCentre = centre
            overUp = onPill
            highlight()
        }
    }

    private fun highlight() {
        val radius = ui.look.shape.tileRadiusDp.toFloat()
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ui.dpf(radius)
            setColor(Color.TRANSPARENT)
            setStroke(ui.dp(3), ui.palette.accent)
        }
        hover?.let { view ->
            view.foreground = ring
            val intoFolder = hoverCentre && view.let { v -> callbacks.tiles().firstOrNull { it.second === v }?.first in callbacks.folderIds() }
            if (intoFolder) {
                view.scaleX = 1.05f
                view.scaleY = 1.05f
            }
        }
        if (overUp) upPill?.background = ui.shape(withAccentSoft(), 999f, ui.palette.accent, 2f)
    }

    private fun withAccentSoft(): Int = ui.withAlpha(ui.palette.accent, 0x40)

    private fun clearHighlight() {
        hover?.let {
            it.foreground = null
            it.scaleX = 1f
            it.scaleY = 1f
        }
        upPill?.background = ui.shape(ui.palette.surface, 999f, ui.palette.outline, 1.5f)
    }

    /** Scrolls the page while the finger is near the top or bottom edge. */
    private fun scrollLoop() {
        if (scrolling) return
        scrolling = true
        val tick = object : Runnable {
            override fun run() {
                if (!isActive) {
                    scrolling = false
                    return
                }
                val at = IntArray(2)
                scroll.getLocationOnScreen(at)
                val zone = ui.dp(88)
                val step = ui.dp(10)
                val top = at[1]
                val bottom = at[1] + scroll.height
                val dy = when {
                    lastY < top + zone -> -step
                    lastY > bottom - zone -> step
                    else -> 0
                }
                if (dy != 0) {
                    scroll.scrollBy(0, dy)
                    updateHover()
                }
                scroll.postOnAnimation(this)
            }
        }
        scroll.postOnAnimation(tick)
    }

    private fun finish(drop: Boolean) {
        val id = draggedId ?: return
        lastDraggedId = id
        val ids = callbacks.tiles().map { it.first }
        val outcome = if (drop) {
            DropPlan.outcome(ids, callbacks.folderIds(), id, hoverIndex, hoverCentre, overUp)
        } else {
            null
        }
        val wasMoved = moved
        clearHighlight()
        ghost?.let { host.removeView(it) }
        upPill?.let { host.removeView(it) }
        draggedView?.alpha = 1f
        ghost = null
        upPill = null
        hover = null
        hoverIndex = null
        overUp = false
        draggedId = null
        draggedView = null
        scroll.requestDisallowInterceptTouchEvent(false)
        if (!drop) return
        when {
            outcome != null -> callbacks.onDrop(outcome)
            !wasMoved -> callbacks.onHold(id)
        }
    }

    /** Puts everything back (the screen is going away). */
    fun cancel() {
        if (isActive) finish(drop = false)
    }
}

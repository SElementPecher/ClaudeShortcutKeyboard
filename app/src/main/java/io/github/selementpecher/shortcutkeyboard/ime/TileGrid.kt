package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup

/**
 * Six tiles in a fixed grid (2 x 3, or 3 x 2 on short screens). It never scrolls.
 *
 * When [dragCallbacks] is set, a long press on a draggable tile starts a drag. The grid works
 * out what the finger is over (see [DragGeometry]) and tells the callbacks; the callbacks
 * decide what that means. A plain tap still reaches the tile as a normal click.
 */
internal class TileGrid(context: Context, private val theme: KeyboardTheme) : ViewGroup(context) {

    interface DragCallbacks {
        /** A long press on [slot]. Return false to refuse (nothing is picked up). */
        fun onDragStart(slot: Int, rawX: Float, rawY: Float): Boolean

        fun onDragMove(target: DragTarget, rawX: Float, rawY: Float)

        /** Held at an edge: flip the page by [direction] (-1 or +1). */
        fun onEdgeFlip(direction: Int)

        fun onDragEnd(cancelled: Boolean)

        fun canFlip(direction: Int): Boolean
    }

    var dragCallbacks: DragCallbacks? = null

    /** Tells whether a screen position is over the back tile. */
    var isOverBack: (Float, Float) -> Boolean = { _, _ -> false }

    private var cols = 2
    private var rows = 3
    private val gap = theme.dp(TILE_GAP_DP)
    private val tiles = List(GridPaging.PER_PAGE) { TileView(context, theme).also { v -> addView(v) } }
    private var specs: List<TileSpec?> = emptyList()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val dwell = EdgeDwell()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var downSlot: Int? = null
    private var dragging = false
    private var lastEdge = 0
    private val screenPosition = IntArray(2)

    private val longPress = Runnable { startDrag() }
    private val edgeTick = object : Runnable {
        override fun run() {
            if (!dragging) return
            if (dwell.update(lastEdge, SystemClock.uptimeMillis())) dragCallbacks?.onEdgeFlip(lastEdge)
            postDelayed(this, EDGE_TICK_MS)
        }
    }

    init {
        // Shadows (the Calm Look) are drawn just outside the tiles.
        clipChildren = false
        clipToPadding = false
    }

    fun setLayoutShape(columns: Int, rowCount: Int) {
        cols = columns
        rows = rowCount
        requestLayout()
    }

    fun bind(newSpecs: List<TileSpec?>) {
        specs = newSpecs
        tiles.forEachIndexed { i, tile ->
            val spec = newSpecs.getOrNull(i)
            if (spec == null) {
                tile.visibility = INVISIBLE
                tile.isClickable = false
                tile.contentDescription = null
                tile.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            } else {
                tile.visibility = VISIBLE
                tile.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                tile.bind(spec)
            }
        }
    }

    /** The view of the tile in [slot], e.g. to measure it for the drag ghost. */
    fun tileAt(slot: Int): TileView = tiles[slot]

    private fun geometry() = GridGeometry(cols, rows, width.toFloat(), height.toFloat(), gap.toFloat())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val g = GridGeometry(cols, rows, w.toFloat(), h.toFloat(), gap.toFloat())
        val cellW = MeasureSpec.makeMeasureSpec(g.cellWidth.toInt(), MeasureSpec.EXACTLY)
        val cellH = MeasureSpec.makeMeasureSpec(g.cellHeight.toInt(), MeasureSpec.EXACTLY)
        tiles.forEach { it.measure(cellW, cellH) }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val g = geometry()
        tiles.forEachIndexed { i, tile ->
            val x = ((i % cols) * (g.cellWidth + gap)).toInt()
            val y = ((i / cols) * (g.cellHeight + gap)).toInt()
            tile.layout(x, y, x + g.cellWidth.toInt(), y + g.cellHeight.toInt())
        }
    }

    // ---- Dragging ----

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (dragCallbacks == null) return super.dispatchTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downTime = ev.downTime
                downSlot = geometry().slotAt(ev.x, ev.y)?.takeIf { specs.getOrNull(it)?.draggable == true }
                removeCallbacks(longPress)
                if (downSlot != null) postDelayed(longPress, LONG_PRESS_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    reportTarget(ev.rawX, ev.rawY)
                    return true
                }
                if (Math.abs(ev.x - downX) > touchSlop || Math.abs(ev.y - downY) > touchSlop) removeCallbacks(longPress)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (dragging) {
                    finishDrag(cancelled = ev.actionMasked == MotionEvent.ACTION_CANCEL)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun startDrag() {
        val slot = downSlot ?: return
        val callbacks = dragCallbacks ?: return
        getLocationOnScreen(screenPosition)
        if (!callbacks.onDragStart(slot, screenPosition[0] + downX, screenPosition[1] + downY)) return
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        // Tell the tile the touch is over, so letting go does not also tap it.
        val cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, downY, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        dwell.reset()
        lastEdge = 0
        reportTarget(screenPosition[0] + downX, screenPosition[1] + downY)
        postDelayed(edgeTick, EDGE_TICK_MS)
    }

    private fun reportTarget(rawX: Float, rawY: Float) {
        val callbacks = dragCallbacks ?: return
        getLocationOnScreen(screenPosition)
        val target = DragGeometry.target(
            geometry(),
            rawX - screenPosition[0],
            rawY - screenPosition[1],
            isOverBack(rawX, rawY),
            callbacks.canFlip(-1),
            callbacks.canFlip(1),
        )
        lastEdge = (target as? DragTarget.Edge)?.direction ?: 0
        callbacks.onDragMove(target, rawX, rawY)
    }

    private fun finishDrag(cancelled: Boolean) {
        dragging = false
        removeCallbacks(edgeTick)
        dwell.reset()
        dragCallbacks?.onDragEnd(cancelled)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        removeCallbacks(edgeTick)
        if (dragging) finishDrag(cancelled = true)
        super.onDetachedFromWindow()
    }

    companion object {
        const val TILE_GAP_DP = 10
        private const val LONG_PRESS_MS = 350L
        private const val EDGE_TICK_MS = 100L
    }
}

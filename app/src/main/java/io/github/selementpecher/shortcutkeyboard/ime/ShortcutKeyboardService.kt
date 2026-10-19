package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.fill.ResultPieces
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.Node
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template
import io.github.selementpecher.shortcutkeyboard.ui.AddOptionActivity
import io.github.selementpecher.shortcutkeyboard.ui.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * The keyboard: a grid of six big tiles (folders and shortcuts), a one-question-at-a-time
 * fill-in flow, and a check screen with an Add button. There are no letter keys; the person
 * keeps their normal keyboard for typing and comes here to add a shortcut.
 *
 * This class holds the state and decides what happens; [KeyboardView] draws it, and the paging
 * and drag rules live in plain Kotlin ([GridPaging], [DragPlanner], ...).
 */
class ShortcutKeyboardService : InputMethodService() {

    private enum class Screen { GRID, QUESTION, CHECK }

    private val handler = Handler(Looper.getMainLooper())
    private var destroyed = false
    private var ui: KeyboardView? = null
    private var compact = false

    // Where we are. Kept while the service lives, so the keyboard reopens in the same folder.
    private var folderId: String? = null
    private var page = 0
    private var screen = Screen.GRID

    // Filling in a shortcut.
    private var session: FillSession? = null
    private var answerPage = 0
    private var otherOpen = false
    private var number: NumberEntry? = null
    private var pending: PendingFill? = null
    private var swallowBackUp = false

    // Dragging a tile.
    private var drag: DragPlanner? = null
    private var dragNodes: Map<String, Node> = emptyMap()
    private var dragSignature: Any? = null

    private val repositoryListener: () -> Unit = {
        // May be called from any thread; the views must only be touched on the main thread.
        handler.post { if (!destroyed && screen == Screen.GRID && drag == null) renderGrid() }
    }

    // The owner can change the Look in the app while the keyboard is alive: rebuild the view.
    private val lookListener: () -> Unit = {
        handler.post { if (!destroyed && ui != null) setInputView(onCreateInputView()) }
    }

    override fun onCreate() {
        super.onCreate()
        ShortcutRepository.addListener(repositoryListener)
        LookStore.addListener(lookListener)
    }

    override fun onDestroy() {
        destroyed = true
        ShortcutRepository.removeListener(repositoryListener)
        LookStore.removeListener(lookListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** A full-screen keyboard in landscape would hide the app, and there is nothing to type into it. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        // Called again after a change such as dark mode: the state stays, the views are rebuilt.
        val screenHeight = resources.configuration.screenHeightDp
        compact = KeyboardMetrics.isCompact(screenHeight)
        val view = KeyboardView(this, compact, LookStore.current(this))
        view.gridDragCallbacks = dragCallbacks
        ui = view
        styleNavigationBar()
        cancelDrag()
        renderCurrent()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val saved = pending
        if (saved != null) {
            if (info?.packageName == packageName) {
                // Our own "add an answer" box has no letters here: hand over to the normal keyboard.
                handler.post { switchBack(fromAbc = false) }
                renderCurrent()
                return
            }
            pending = null
            if (saved.isFresh(SystemClock.elapsedRealtime()) && restorePending(saved)) return
        } else if (!restarting) {
            session = null
            screen = Screen.GRID
        }
        renderCurrent()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        cancelDrag()
        // The fill-in is dropped unless we are leaving to add an answer (see [pending]).
        session = null
        screen = Screen.GRID
        super.onFinishInputView(finishingInput)
    }

    /** The back button leaves the fill-in instead of hiding the keyboard. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            swallowBackUp = session != null && isInputViewShown
            if (swallowBackUp) {
                if ((event?.repeatCount ?: 0) == 0) leaveFill()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && swallowBackUp) {
            swallowBackUp = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // ---- Data ----

    private fun library(): Library = try {
        ShortcutRepository.library(this)
    } catch (e: Exception) {
        Log.e(TAG, "Could not load shortcuts", e)
        Library()
    }

    /** Falls back to the top level if the folder we were in has been deleted. */
    private fun checkedFolder(library: Library): String? {
        if (folderId != null && library.folder(folderId) == null) {
            folderId = null
            page = 0
        }
        return folderId
    }

    // ---- Screen 1: the grid ----

    private fun renderCurrent() {
        val current = session
        when {
            screen == Screen.QUESTION && current != null -> showQuestion()
            screen == Screen.CHECK && current != null -> showCheck()
            else -> {
                screen = Screen.GRID
                renderGrid()
            }
        }
    }

    private fun renderGrid() {
        val view = ui ?: return
        val library = library()
        val folder = checkedFolder(library)
        val planner = drag
        val ordered: List<Node> = if (planner != null) {
            planner.order.mapNotNull { dragNodes[it] }
        } else {
            library.children(folder)
        }
        // The "+ New" tile is always the last one.
        val entries: List<Node?> = ordered + null
        page = GridPaging.clampPage(page, entries.size)
        val tiles = GridPaging.pageItems(entries, page).map { node ->
            when {
                node == null -> newTile(getString(R.string.kb_new), getString(R.string.kb_desc_new_shortcut), ::openApp)
                planner != null && node.id == planner.draggedId ->
                    TileSpec("", TileLook(TileLook.Kind.EMPTY), getString(R.string.kb_desc_empty_slot))
                else -> nodeTile(node, library, highlighted = planner?.dropFolderId == node.id)
            }
        }
        view.showGrid(
            GridModel(
                inFolder = folder != null,
                pathText = TileStyle.pathText(library.path(folder).map { it.name }),
                page = page,
                pageCount = GridPaging.pageCount(entries.size),
                tiles = tiles,
                isEmpty = entries.size == 1 && planner == null,
                backHighlighted = planner?.overBack == true,
                onBack = ::goUpOneLevel,
                onPrevious = { flipPage(-1) },
                onNext = { flipPage(1) },
                onAbc = { switchBack(fromAbc = true) },
                onSettings = ::openApp,
            ),
        )
    }

    private fun nodeTile(node: Node, library: Library, highlighted: Boolean = false): TileSpec = when (node) {
        is Folder -> TileSpec(
            label = node.name.ifBlank { getString(R.string.kb_untitled) },
            look = TileLook(TileLook.Kind.FOLDER, highlighted = highlighted, custom = node.color),
            glyph = TileStyle.glyphFor(node.icon, node.name, isFolder = true),
            subtitle = resources.getQuantityString(R.plurals.kb_folder_items, library.children(node.id).size, library.children(node.id).size),
            description = getString(R.string.kb_desc_folder, node.name),
            draggable = true,
            id = node.id,
            onClick = { enterFolder(node.id) },
        )
        is Shortcut -> TileSpec(
            label = node.name.ifBlank { getString(R.string.kb_untitled) },
            look = TileLook(TileLook.Kind.SHORTCUT, colourKey = node.id, custom = node.color),
            glyph = TileStyle.glyphFor(node.icon, node.name, isFolder = false),
            description = getString(R.string.kb_desc_shortcut, node.name),
            draggable = true,
            id = node.id,
            onClick = { openShortcut(node) },
        )
    }

    private fun newTile(label: String, description: String, onClick: () -> Unit) =
        TileSpec(label, TileLook(TileLook.Kind.NEW), description, icon = Icons.Kind.PLUS, onClick = onClick)

    private fun enterFolder(id: String) {
        folderId = id
        page = 0
        renderGrid()
    }

    private fun goUpOneLevel() {
        folderId = library().folder(folderId)?.parentId
        page = 0
        renderGrid()
    }

    private fun flipPage(direction: Int) {
        val count = (drag?.order?.size ?: library().children(folderId).size) + 1
        page = GridPaging.clampPage(page + direction, count)
        renderGrid()
    }

    /** Opens the app showing the folder we are in. */
    private fun openApp() {
        try {
            val intent = Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FOLDER_ID, folderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open the app", e)
        }
    }

    // ---- Dragging ----

    private val dragCallbacks = object : TileGrid.DragCallbacks {
        override fun onDragStart(slot: Int, rawX: Float, rawY: Float): Boolean {
            val view = ui ?: return false
            val library = library()
            val ordered = library.children(checkedFolder(library))
            val node = GridPaging.pageItems(ordered + null, page).getOrNull(slot) ?: return false
            val tile = view.gridTile(slot) ?: return false
            drag = DragPlanner(
                ids = ordered.map { it.id },
                draggedId = node.id,
                folderIds = ordered.filterIsInstance<Folder>().map { it.id }.toSet(),
            )
            dragNodes = ordered.associateBy { it.id }
            dragSignature = null
            val spec = nodeTile(node, library)
            view.showGhost(TileSpec(spec.label, spec.look, spec.description, spec.glyph, spec.subtitle), tile.width, tile.height, rawX, rawY)
            renderGrid()
            return true
        }

        override fun onDragMove(target: DragTarget, rawX: Float, rawY: Float) {
            val planner = drag ?: return
            ui?.moveGhost(rawX, rawY)
            planner.update(page, target)
            val signature = Triple(planner.order, planner.dropFolderId, planner.overBack)
            if (signature != dragSignature) {
                dragSignature = signature
                renderGrid()
            }
        }

        override fun onEdgeFlip(direction: Int) {
            if (drag != null) flipPage(direction)
        }

        override fun canFlip(direction: Int): Boolean {
            val count = (drag?.order?.size ?: return false) + 1
            return if (direction < 0) GridPaging.hasPrevious(page) else GridPaging.hasNext(page, count)
        }

        override fun onDragEnd(cancelled: Boolean) {
            val planner = drag ?: return
            ui?.hideGhost()
            drag = null
            dragSignature = null
            if (!cancelled && !planner.isUnchanged()) applyDrop(planner)
            renderGrid()
        }
    }

    private fun applyDrop(planner: DragPlanner) {
        try {
            val id = planner.draggedId
            // A refused move (such as a folder into itself) just leaves everything as it was.
            when (val outcome = planner.outcome()) {
                is DragPlanner.Outcome.IntoFolder -> ShortcutRepository.move(this, id, outcome.folderId, Int.MAX_VALUE)
                DragPlanner.Outcome.UpOneLevel -> {
                    val parent = ShortcutRepository.findFolder(this, folderId ?: return)?.parentId
                    ShortcutRepository.move(this, id, parent, Int.MAX_VALUE)
                }
                is DragPlanner.Outcome.Reorder -> ShortcutRepository.move(this, id, folderId, outcome.index)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not move the tile", e)
        }
    }

    private fun cancelDrag() {
        if (drag == null) return
        drag = null
        dragSignature = null
        ui?.hideGhost()
    }

    // ---- Screen 2: one question at a time ----

    private fun openShortcut(shortcut: Shortcut) {
        val fresh = FillSession(shortcut, locale = locale())
        session = fresh
        resetQuestionState()
        if (fresh.isEmpty) showCheck() else showQuestion()
    }

    private fun resetQuestionState() {
        answerPage = 0
        otherOpen = false
        number = null
    }

    private fun showQuestion() {
        val view = ui ?: return
        val current = session ?: return renderGrid()
        val variable = current.current ?: return showCheck()
        screen = Screen.QUESTION
        val body = when (variable.type) {
            VariableType.CHOICE, VariableType.TEXT -> answersBody(current, variable)
            VariableType.TIME -> if (otherOpen) timeAdjustBody(current, variable) else timeBody(current, variable)
            VariableType.DATE -> if (otherOpen) dateAdjustBody(current, variable) else dateBody(current, variable)
            VariableType.NUMBER -> numberBody(current, variable)
            // Placeholder until the "how long" screens exist: show the saved amounts as a list.
            VariableType.TIME_AHEAD, VariableType.TIME_AGO -> answersBody(current, variable)
        }
        view.showStep(
            StepModel(
                shortcutName = current.shortcut.name.ifBlank { getString(R.string.kb_untitled) },
                step = current.index,
                steps = current.variables.size,
                question = questionText(variable),
                onBack = ::stepBack,
                onCancel = ::leaveFill,
                body = body,
            ),
        )
    }

    private fun questionText(variable: VariableDef): String {
        val name = variable.name.trim().replaceFirstChar { it.titlecase(locale()) }
        return getString(
            when (variable.type) {
                VariableType.TIME -> R.string.kb_question_time
                VariableType.DATE -> R.string.kb_question_date
                VariableType.NUMBER -> R.string.kb_question_number
                else -> R.string.kb_question
            },
            name,
        )
    }

    private fun answersBody(current: FillSession, variable: VariableDef): StepBody {
        val answers = AnswerList.build(variable)
        val entries: List<Answer?> = answers + null
        answerPage = GridPaging.clampPage(answerPage, entries.size)
        val firstOnPage = GridPaging.globalIndex(answerPage, 0)
        val tiles = GridPaging.pageItems(entries, answerPage).mapIndexed { slot, answer ->
            if (answer == null) {
                newTile(getString(R.string.kb_new), getString(R.string.kb_desc_new_answer), ::addNewAnswer)
            } else {
                TileSpec(
                    label = answer.text,
                    look = TileLook(TileLook.Kind.ANSWER, colourIndex = firstOnPage + slot, selected = answer.isLast),
                    glyph = if (answer.isLast) LAST_ANSWER_GLYPH else null,
                    description = if (answer.isLast) getString(R.string.kb_desc_last_answer, answer.text) else answer.text,
                    onClick = {
                        current.setValue(variable, answer.text)
                        advance()
                    },
                )
            }
        }
        return StepBody.Tiles(
            tiles = tiles,
            page = answerPage,
            pageCount = GridPaging.pageCount(entries.size),
            hint = if (answers.isEmpty()) getString(R.string.kb_add_first_answer) else null,
            onPrevious = { flipAnswerPage(-1) },
            onNext = { flipAnswerPage(1) },
        )
    }

    private fun flipAnswerPage(direction: Int) {
        answerPage += direction
        showQuestion()
    }

    private fun timeBody(current: FillSession, variable: VariableDef): StepBody {
        fun choice(minutesAgo: Long, label: String, kind: TileLook.Kind) = TileSpec(
            label = label,
            look = TileLook(kind, colourIndex = (minutesAgo / 15).toInt()),
            description = label.replace('\n', ' '),
            onClick = {
                TimeChoices.pick(current, variable, minutesAgo)
                advance()
            },
        )
        val now = Template.formatTimeValue(LocalTime.now())
        val tiles = listOf(
            choice(0, getString(R.string.kb_time_now, now), TileLook.Kind.OK),
            choice(15, getString(R.string.kb_time_ago_15), TileLook.Kind.ANSWER),
            choice(30, getString(R.string.kb_time_ago_30), TileLook.Kind.ANSWER),
            choice(60, getString(R.string.kb_time_ago_60), TileLook.Kind.ANSWER),
            TileSpec(
                getString(R.string.kb_time_other),
                TileLook(TileLook.Kind.SPECIAL),
                onClick = { otherOpen = true; showQuestion() },
            ),
        )
        return StepBody.Tiles(tiles, 0, 1, null, {}, {})
    }

    private fun timeAdjustBody(current: FillSession, variable: VariableDef): StepBody {
        fun nudge(label: Int, minutes: Long) = TileSpec(
            getString(label),
            TileLook(TileLook.Kind.ANSWER, colourIndex = if (minutes < 0) 1 else 0),
            onClick = { current.adjustTime(variable, minutes); showQuestion() },
        )
        val value = current.valueOf(variable).orEmpty()
        val later = TimeChoices.laterText(current.template, variable, value)
        return StepBody.Adjust(
            value = value,
            later = later?.let { getString(R.string.kb_later, it) },
            buttons = listOf(
                nudge(R.string.kb_hour_earlier, -60),
                nudge(R.string.kb_minutes_earlier, -5),
                nudge(R.string.kb_minutes_later, 5),
                nudge(R.string.kb_hour_later, 60),
            ),
            ok = okTile(),
        )
    }

    private fun dateBody(current: FillSession, variable: VariableDef): StepBody {
        fun choice(label: Int, days: Long) = TileSpec(
            getString(label),
            TileLook(if (days == 0L) TileLook.Kind.OK else TileLook.Kind.ANSWER, colourIndex = if (days < 0) 1 else 0),
            onClick = {
                DateChoices.pick(current, variable, LocalDate.now(), days)
                advance()
            },
        )
        val tiles = listOf(
            choice(R.string.kb_today, 0),
            choice(R.string.kb_yesterday, -1),
            choice(R.string.kb_tomorrow, 1),
            TileSpec(
                getString(R.string.kb_date_other),
                TileLook(TileLook.Kind.SPECIAL),
                onClick = { otherOpen = true; showQuestion() },
            ),
        )
        return StepBody.Tiles(tiles, 0, 1, null, {}, {})
    }

    private fun dateAdjustBody(current: FillSession, variable: VariableDef): StepBody {
        fun nudge(label: Int, days: Long) = TileSpec(
            getString(label),
            TileLook(TileLook.Kind.ANSWER, colourIndex = if (days < 0) 1 else 0),
            onClick = { current.adjustDate(variable, days); showQuestion() },
        )
        return StepBody.Adjust(
            value = current.displayValue(variable),
            later = null,
            buttons = listOf(nudge(R.string.kb_day_earlier, -1), nudge(R.string.kb_day_later, 1)),
            ok = okTile(),
        )
    }

    private fun okTile() = TileSpec(
        getString(R.string.kb_ok),
        TileLook(TileLook.Kind.OK, pill = true),
        icon = Icons.Kind.CHECK,
        textSp = 26f,
        onClick = { otherOpen = false; advance() },
    )

    private fun numberBody(current: FillSession, variable: VariableDef): StepBody {
        val entry = number ?: NumberEntry(current.valueOf(variable)).also { number = it }
        return StepBody.Number(
            value = entry.text,
            onKey = { key -> entry.press(key); showQuestion() },
            onBackspace = { entry.backspace(); showQuestion() },
            onOk = {
                if (entry.text.isEmpty()) current.clearValue(variable) else current.setValue(variable, entry.text)
                advance()
            },
        )
    }

    /** Goes on to the next question, or to the check after the last one. */
    private fun advance() {
        val current = session ?: return
        resetQuestionState()
        if (current.isLast) showCheck() else {
            current.next()
            showQuestion()
        }
    }

    private fun stepBack() {
        val current = session ?: return renderGrid()
        if (otherOpen) {
            otherOpen = false
            showQuestion()
            return
        }
        resetQuestionState()
        if (current.isFirst) leaveFill() else {
            current.back()
            showQuestion()
        }
    }

    /** Drops the fill-in and shows the grid, in the folder of the shortcut. */
    private fun leaveFill() {
        session?.let { folderId = it.shortcut.parentId }
        session = null
        resetQuestionState()
        screen = Screen.GRID
        renderGrid()
    }

    // ---- Screen 3: check ----

    private fun showCheck() {
        val view = ui ?: return
        val current = session ?: return renderGrid()
        screen = Screen.CHECK
        view.showCheck(
            CheckModel(
                shortcutName = current.shortcut.name.ifBlank { getString(R.string.kb_untitled) },
                pieces = ResultPieces.of(current, locale = locale()),
                onBack = {
                    if (current.isEmpty) {
                        leaveFill()
                    } else {
                        current.goTo(current.variables.size - 1)
                        resetQuestionState()
                        showQuestion()
                    }
                },
                onCancel = ::leaveFill,
                onAdd = ::addToApp,
            ),
        )
    }

    private fun addToApp() {
        val current = session ?: return
        val ic = currentInputConnection
        if (ic != null) {
            ic.commitText(current.result(), 1)
            rememberValues(current)
        }
        leaveFill()
        switchBack(fromAbc = false)
    }

    /** Saves the values used (and new answers) so they are offered first next time. */
    private fun rememberValues(current: FillSession) {
        try {
            val saved = ShortcutRepository.find(this, current.shortcut.id) ?: return
            // Changed in the app while we were filling in: keep those changes instead.
            if (saved != current.shortcut) return
            val updated = current.updatedShortcut()
            if (updated != saved) ShortcutRepository.save(this, updated)
        } catch (e: Exception) {
            Log.w(TAG, "Could not remember the values used", e)
        }
    }

    // ---- "+ New" answer: leave to the app and come back ----

    private fun addNewAnswer() {
        val current = session ?: return
        val variable = current.current ?: return
        pending = PendingFill.capture(current, variable.key, variable.options, SystemClock.elapsedRealtime())
        try {
            startActivity(AddOptionActivity.intent(this, current.shortcut.id, variable.name))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open the add-answer screen", e)
            pending = null
            return
        }
        // That screen has a text box, and this keyboard has no letters: hand over.
        handler.post { switchBack(fromAbc = false) }
    }

    /** Puts a fill-in back after the app was open. Returns false if the shortcut is gone. */
    private fun restorePending(saved: PendingFill): Boolean {
        val latest = try {
            ShortcutRepository.find(this, saved.shortcutId)
        } catch (e: Exception) {
            null
        } ?: return false
        val restored = saved.restore(latest, locale())
        session = restored.session
        resetQuestionState()
        if (restored.finished) showCheck() else showQuestion()
        return true
    }

    // ---- Switching keyboards ----

    /**
     * Goes back to the keyboard used before this one. If that is not possible, and the person
     * tapped "ABC" themselves, shows the keyboard picker instead.
     */
    private fun switchBack(fromAbc: Boolean) {
        val switched = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToPreviousInputMethod()
            } else {
                val token = window?.window?.attributes?.token
                @Suppress("DEPRECATION")
                token != null && inputMethodManager()?.switchToLastInputMethod(token) == true
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not switch keyboard", e)
            false
        }
        if (!switched && fromAbc) {
            try {
                inputMethodManager()?.showInputMethodPicker()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not show the keyboard picker", e)
            }
        }
    }

    private fun inputMethodManager(): InputMethodManager? =
        getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    // ---- Window ----

    /** Navigation bar in the keyboard's colour, with dark buttons in light mode so they stay visible. */
    private fun styleNavigationBar() {
        val imeWindow = window?.window ?: return
        // The colour behind the buttons is the Look's panel colour; dark buttons only when that is light.
        val panel = LookStore.current(this).palette(this).bg
        val night = TileStyle.luminance(panel) < 0.5f
        try {
            if (Build.VERSION.SDK_INT < VERSION_EDGE_TO_EDGE) {
                imeWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                @Suppress("DEPRECATION")
                imeWindow.navigationBarColor = panel
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                imeWindow.insetsController?.setSystemBarsAppearance(
                    if (night) 0 else WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                )
            } else {
                val decor = imeWindow.decorView
                @Suppress("DEPRECATION")
                val flags = decor.systemUiVisibility
                @Suppress("DEPRECATION")
                decor.systemUiVisibility = if (night) {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not style the navigation bar", e)
        }
    }

    private fun locale(): Locale =
        resources.configuration.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()

    companion object {
        private const val TAG = "ShortcutKeyboard"

        private const val LAST_ANSWER_GLYPH = "★"

        /** Android 15, where apps (and keyboards) are always drawn edge to edge. */
        private const val VERSION_EDGE_TO_EDGE = 35
    }
}

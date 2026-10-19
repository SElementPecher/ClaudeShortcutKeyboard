package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.ime.DragPlanner
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf
import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.Node
import io.github.selementpecher.shortcutkeyboard.model.Shortcut

/**
 * The app's home screen, also opened from the keyboard's settings button.
 *
 * Shows the shortcuts and folders of one folder level as tiles in the chosen Look, a short setup card
 * until the keyboard is turned on and picked, and two big buttons to make something new.
 *
 * Start with [EXTRA_FOLDER_ID] to open inside a folder.
 */
class MainActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var screen: UiScreen

    /** The folder being shown (null = top level, called "Home"). */
    private var currentFolderId: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var panel: UiPanel? = null

    private lateinit var selectButton: View
    private lateinit var drag: TileDrag

    /** The tiles on screen, in order (for picking one up and dropping it). */
    private var shownTiles: List<Pair<String, View>> = emptyList()
    private var shownFolderIds: Set<String> = emptySet()

    /** Choosing several tiles to delete. */
    private var selecting = false
    private val selected = LinkedHashSet<String>()

    private val repositoryListener: () -> Unit = {
        // May be called from any thread, e.g. when the keyboard saves what it learned.
        mainHandler.post { if (!isDestroyed) render() }
    }

    /** Ticks the setup steps as soon as the keyboard is turned on or picked. */
    private val keyboardSettingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            if (!isDestroyed) render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this, LookStore.current(this))
        currentFolderId = if (savedInstanceState != null) savedInstanceState.getString(STATE_FOLDER) else intent?.getStringExtra(EXTRA_FOLDER_ID)

        val gear = ui.iconButton(Icons.Kind.GEAR, getString(R.string.settings_open)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        selectButton = ui.iconButton(Icons.Kind.CHECK, getString(R.string.select_open)) { startSelecting(null) }
        screen = ui.screen(getString(R.string.app_name), trailing = listOf(selectButton, gear), large = true, withBottomBar = true)
        screen.install(this)
        drag = TileDrag(this, ui, screen.scroll, object : TileDrag.Callbacks {
            override fun tiles() = shownTiles
            override fun folderIds() = shownFolderIds
            override fun upLabel(): String? {
                val library = ShortcutRepository.library(this@MainActivity)
                val folder = library.folder(currentFolderId) ?: return null
                val parent = library.folder(folder.parentId)?.name?.ifBlank { null } ?: getString(R.string.home_name)
                return getString(R.string.drag_up, parent)
            }
            override fun onDrop(outcome: DragPlanner.Outcome) = applyDrop(outcome)
            override fun onHold(id: String) {
                val library = ShortcutRepository.library(this@MainActivity)
                (library.folder(id) ?: library.shortcut(id))?.let { showActions(it) }
            }
        })
        ShortcutRepository.addListener(repositoryListener)
    }

    override fun onStart() {
        super.onStart()
        for (key in listOf(Settings.Secure.ENABLED_INPUT_METHODS, Settings.Secure.DEFAULT_INPUT_METHOD)) {
            try {
                contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, keyboardSettingsObserver)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not watch $key", e)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Single top: the keyboard opens us again, maybe for another folder.
        if (intent.hasExtra(EXTRA_FOLDER_ID)) {
            currentFolderId = intent.getStringExtra(EXTRA_FOLDER_ID)
            if (!isDestroyed) render()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::drag.isInitialized && drag.handle(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back stops choosing, then goes up a folder, then leaves.
        when {
            selecting -> stopSelecting()
            currentFolderId != null -> goUp()
            else -> super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (ui.isStale()) {
            recreate()
            return
        }
        render()
        screen.root.post { if (!isDestroyed) screen.showWaitingSnack() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The keyboard picker is a dialog over this screen, so there is no onResume after it.
        if (hasFocus && ::screen.isInitialized) render()
    }

    override fun onStop() {
        if (::drag.isInitialized) drag.cancel()
        contentResolver.unregisterContentObserver(keyboardSettingsObserver)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentFolderId?.let { outState.putString(STATE_FOLDER, it) }
    }

    override fun onDestroy() {
        ShortcutRepository.removeListener(repositoryListener)
        panel?.dismiss()
        panel = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- drawing the screen

    private fun render() {
        val library = ShortcutRepository.library(this)
        // A folder deleted elsewhere (e.g. from the keyboard): fall back to the top level.
        currentFolderId = FolderLogic.existingFolder(library, currentFolderId)
        // Tiles deleted elsewhere can no longer be chosen.
        selected.retainAll { library.folder(it) != null || library.shortcut(it) != null }
        val content = screen.content
        content.removeAllViews()
        with(ui) {
            val enabled = KeyboardSetup.isEnabled(this@MainActivity)
            // One step only: once the keyboard is turned on, the whole box goes away.
            if (selecting) content.add(selectionHeader(library)) else if (!enabled) content.add(setupCard())

            val folder = library.folder(currentFolderId)
            if (folder != null && !selecting) content.add(folderHeader(library, folder), top = 16)

            val children = library.children(currentFolderId)
            if (children.isEmpty() && selecting) selecting = false
            screen.showBottomBar(children.isNotEmpty())
            selectButton.visibility = if (children.isNotEmpty() && !selecting) View.VISIBLE else View.GONE
            buildBottomBar()
            if (children.isEmpty()) {
                shownTiles = emptyList()
                shownFolderIds = emptySet()
                content.add(emptyState(inFolder = folder != null), top = 16)
            } else {
                val tiles = children.map { node -> tileFor(node, library).also { if (shownIds.isNotEmpty() && node.id !in shownIds) Motion.pop(it) } }
                shownTiles = children.map { it.id }.zip(tiles)
                shownFolderIds = children.filterIsInstance<Folder>().map { it.id }.toSet()
                content.add(grid(columns, tiles), top = if (folder != null && !selecting) 12 else 20)
                if (!selecting) content.add(small(getString(R.string.home_tip)).apply { gravity = Gravity.CENTER }, top = 20)
            }
            shownIds = children.map { it.id }.toSet()
        }
    }

    /** The two buttons at the bottom: make something new, or (while choosing) delete what is chosen. */
    private fun buildBottomBar() {
        screen.bottomBar.removeAllViews()
        with(ui) {
            if (selecting) {
                val count = selected.size
                val delete = dangerFilledButton(resources.getQuantityString(R.plurals.select_delete, count, count)) { confirmDeleteSelected() }
                delete.isEnabled = count > 0
                delete.alpha = if (count > 0) 1f else 0.4f
                val cancel = secondaryButton(getString(R.string.dialog_cancel)) { stopSelecting() }
                screen.bottomBar.addView(buttonRow(delete, cancel))
            } else {
                val newShortcut = primaryButton(getString(R.string.shortcut_new)) { openEditor(null) }
                val newFolder = secondaryButton(getString(R.string.folder_new)) { askFolder(null) }
                screen.bottomBar.addView(buttonRow(newShortcut, newFolder))
            }
        }
    }

    /** A red filled button for the one thing that deletes (only while choosing). */
    private fun Ui.dangerFilledButton(text: CharSequence, onClick: () -> Unit): TextView {
        val r = look.shape.buttonRadiusDp.toFloat()
        return primaryButton(text, Icons.Kind.TRASH) { onClick() }.apply {
            background = rippled(shape(palette.danger, r), r)
            setTextColor(readableOn(palette.danger))
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon(Icons.Kind.TRASH, 22, readableOn(palette.danger)), null, null, null)
        }
    }

    /** "3 selected" with Select all / Clear, shown at the top while choosing. */
    private fun selectionHeader(library: Library): View = with(ui) {
        val total = library.children(currentFolderId).size
        val line = row()
        line.addView(
            text(resources.getQuantityString(R.plurals.select_count, selected.size, selected.size), look.type.headingSp, look.type.headingWeight, palette.ink).apply {
                layoutParams = lp(width = 0, weight = 1f)
            },
        )
        val allChosen = selected.size >= total
        line.addView(
            textButton(getString(if (allChosen) R.string.select_none else R.string.select_all)) {
                val ids = library.children(currentFolderId).map { it.id }
                if (allChosen) selected.removeAll(ids.toSet()) else selected.addAll(ids)
                render()
            }.apply { layoutParams = lp(width = ViewGroup.LayoutParams.WRAP_CONTENT) },
        )
        line
    }

    // ---------------------------------------------------------------- choosing several

    private fun startSelecting(first: String?) {
        selecting = true
        selected.clear()
        first?.let { selected.add(it) }
        render()
        screen.scroll.scrollTo(0, 0)
    }

    private fun stopSelecting() {
        selecting = false
        selected.clear()
        render()
    }

    private fun toggle(id: String) {
        if (!selected.remove(id)) selected.add(id)
        val y = screen.scroll.scrollY
        render()
        screen.scroll.post { screen.scroll.scrollTo(0, y) }
    }

    private fun confirmDeleteSelected() {
        if (selected.isEmpty()) return
        val library = ShortcutRepository.library(this)
        val count = selected.size
        val hasFolder = selected.any { library.folder(it) != null }
        show(
            ui.confirm(
                this,
                title = resources.getQuantityString(R.plurals.delete_many_title, count, count),
                message = getString(if (hasFolder) R.string.delete_many_message_folders else R.string.delete_many_message),
                confirmText = getString(R.string.delete_confirm),
                cancelText = getString(R.string.dialog_cancel),
                danger = true,
            ) { deleteSelected() },
        )
    }

    private fun deleteSelected() {
        val ids = selected.toSet()
        try {
            ShortcutRepository.deleteMany(this, ids)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete", e)
            screen.snack(getString(R.string.edit_save_failed), error = true)
            return
        }
        selecting = false
        selected.clear()
        render()
        screen.snack(resources.getQuantityString(R.plurals.deleted_many, ids.size, ids.size))
    }

    // ---------------------------------------------------------------- picking up and dropping

    private fun applyDrop(outcome: DragPlanner.Outcome) {
        val library = ShortcutRepository.library(this)
        val id = drag.lastDraggedId ?: return
        val moved = try {
            when (outcome) {
                is DragPlanner.Outcome.IntoFolder -> ShortcutRepository.move(this, id, outcome.folderId, Int.MAX_VALUE)
                DragPlanner.Outcome.UpOneLevel -> ShortcutRepository.move(this, id, library.folder(currentFolderId)?.parentId, Int.MAX_VALUE)
                is DragPlanner.Outcome.Reorder -> ShortcutRepository.move(this, id, currentFolderId, outcome.index)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not move", e)
            false
        }
        render()
        if (!moved) screen.snack(getString(R.string.move_failed), error = true)
        else if (outcome !is DragPlanner.Outcome.Reorder) screen.snack(getString(R.string.move_done))
    }

    /** What was on screen the last time, so only a tile that was just added pops in. */
    private var shownIds: Set<String> = emptySet()

    private fun tileFor(node: Node, library: Library): View {
        val name = node.name.ifBlank { getString(R.string.shortcut_no_name) }
        val chosen = node.id in selected
        lateinit var view: View
        view = when (node) {
            is Folder -> {
                val count = FolderLogic.itemCount(library, node.id)
                val items = if (count == 0) getString(R.string.folder_empty_row) else resources.getQuantityString(R.plurals.folder_items, count, count)
                ui.tile(
                    name = name, icon = node.icon, kind = TileKind.FOLDER, colorKey = node.id, color = node.color, subtitle = items,
                    description = getString(R.string.folder_row_description, name, items),
                    onClick = { if (selecting) toggle(node.id) else openFolder(node.id) },
                    onLongClick = { if (selecting) toggle(node.id) else drag.start(node.id, view) },
                )
            }
            is Shortcut -> ui.tile(
                name = name, icon = node.icon, kind = TileKind.SHORTCUT, colorKey = node.id, color = node.color,
                description = getString(R.string.shortcut_row_description, name, ""),
                onClick = { if (selecting) toggle(node.id) else openEditor(node.id) },
                onLongClick = { if (selecting) toggle(node.id) else drag.start(node.id, view) },
            )
        }
        if (selecting) {
            view.contentDescription = getString(if (chosen) R.string.select_tile_chosen else R.string.select_tile_not_chosen, name)
            (view as? FrameLayout)?.addView(selectBadge(chosen))
        }
        return view
    }

    /** The round tick in the corner of a tile while choosing: filled when chosen, an empty ring when not. */
    private fun selectBadge(chosen: Boolean): View = with(ui) {
        ImageView(this@MainActivity).apply {
            scaleType = ImageView.ScaleType.CENTER
            if (chosen) {
                background = shape(palette.accent, 999f)
                setImageDrawable(icon(Icons.Kind.CHECK, 18, palette.onAccent))
            } else {
                background = shape(withAlpha(palette.surface, 0xCC), 999f, palette.inkSoft, 2f)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) }
        }
    }

    /** The one setup step. It disappears once the keyboard is turned on. */
    private fun setupCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.setup_title)))
        card.add(step(1, getString(R.string.setup_step1), getString(R.string.setup_step1_button), done = false, current = true) { openKeyboardSettings() }, top = 16)
        card.add(small(getString(R.string.setup_privacy)), top = 20)
        card
    }

    private fun step(number: Int, label: String, buttonText: String, done: Boolean, current: Boolean, onClick: () -> Unit): View = with(ui) {
        val line = row()
        val badgeFill = if (done) palette.success else if (current) palette.accent else palette.surfaceAlt
        val badge = TextView(this@MainActivity).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, look.type.bodySp)
            typeface = typefaceOf(700)
            includeFontPadding = false
            background = shape(badgeFill, 999f)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            if (done) {
                setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
                foreground = icon(Icons.Kind.CHECK, 22, readableOn(badgeFill)).also { it.setBounds(0, 0, dp(22), dp(22)) }
                foregroundGravity = Gravity.CENTER
            } else {
                text = number.toString()
                setTextColor(if (current) readableOn(badgeFill) else palette.inkSoft)
            }
        }
        line.addView(badge)
        line.addView(body(label).apply {
            typeface = typefaceOf(600)
            layoutParams = lp(width = 0, weight = 1f, start = 14)
            if (!done && !current) setTextColor(palette.inkSoft)
        })
        val box = column()
        box.contentDescription = getString(if (done) R.string.setup_step_done else R.string.setup_step_todo, label)
        box.addView(line)
        if (current) box.add(primaryButton(buttonText) { onClick() }, top = 12)
        box
    }

    /** "✓ Keyboard is ready": small and calm, with the one tip below it. */
    private fun readyLine(): View = with(ui) {
        val box = column().apply {
            val r = look.shape.cardRadiusDp.coerceAtMost(20).toFloat()
            background = shape(blend(palette.bg, palette.success, 0.12f), r)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val line = row()
        line.addView(
            ImageView(this@MainActivity).apply {
                setImageDrawable(icon(Icons.Kind.CHECK, 18, readableOn(palette.success)))
                scaleType = ImageView.ScaleType.CENTER
                background = shape(palette.success, 999f)
            },
            LinearLayout.LayoutParams(dp(28), dp(28)),
        )
        line.addView(text(getString(R.string.setup_ready_text), look.type.bodySp, 700, palette.ink).apply {
            layoutParams = lp(width = 0, weight = 1f, start = 12)
        })
        box.addView(line)
        box.add(small(getString(R.string.setup_how_to)).apply { setTextColor(palette.ink); alpha = 0.78f }, top = 8)
        box.contentDescription = getString(R.string.setup_ready_text) + ". " + getString(R.string.setup_how_to)
        box
    }

    private fun folderHeader(library: Library, folder: Folder): View = with(ui) {
        val home = getString(R.string.home_name)
        val noName = getString(R.string.shortcut_no_name)
        val parentName = library.folder(folder.parentId)?.name?.ifBlank { noName } ?: home
        val line = row()
        // A ghost chevron: it lines up with the screen margin, so pull it left by the button's own padding.
        line.addView(iconButton(Icons.Kind.BACK, getString(R.string.action_up_description, parentName)) { goUp() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = -dp(12) })
        line.addView(text(FolderLogic.pathText(library, currentFolderId, home, noName), look.type.headingSp, look.type.headingWeight, palette.ink, Ui.HEADING_LINE_SPACING).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            layoutParams = lp(width = 0, weight = 1f, start = 0)
        })
        line
    }

    /** Nothing here yet: a big picture, one sentence, and one button. */
    private fun emptyState(inFolder: Boolean): View = with(ui) {
        val box = emptyState(
            if (inFolder) "📂" else "✨",
            getString(R.string.home_empty_title),
            getString(if (inFolder) R.string.folder_empty else R.string.shortcuts_empty),
        )
        box.add(primaryButton(getString(R.string.shortcut_new), Icons.Kind.PLUS) { openEditor(null) }, top = 28)
        box.add(secondaryButton(getString(R.string.folder_new), Icons.Kind.FOLDER) { askFolder(null) }, top = 12)
        box
    }

    // ---------------------------------------------------------------- setup actions

    private fun openKeyboardSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            screen.snack(getString(R.string.setup_no_settings), error = true)
        }
    }

    private fun showKeyboardPicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    // ---------------------------------------------------------------- folders

    private fun goUp() {
        val library = ShortcutRepository.library(this)
        currentFolderId = library.folder(currentFolderId)?.parentId
        render()
        screen.scroll.scrollTo(0, 0)
    }

    private fun openFolder(id: String) {
        currentFolderId = id
        render()
        screen.scroll.scrollTo(0, 0)
    }

    private fun openEditor(id: String?) {
        val intent = Intent(this, EditShortcutActivity::class.java)
        if (id != null) intent.putExtra(EditShortcutActivity.EXTRA_ID, id)
        else currentFolderId?.let { intent.putExtra(EditShortcutActivity.EXTRA_PARENT_ID, it) }
        startActivity(intent)
    }

    // ---------------------------------------------------------------- long press actions

    private fun show(newPanel: UiPanel) {
        panel?.dismiss()
        panel = newPanel
        newPanel.show()
    }

    /** Press and hold: edit or rename, move, delete. */
    private fun showActions(node: Node) {
        val name = node.name.ifBlank { getString(R.string.shortcut_no_name) }
        val sheet = ui.sheet(this, name)
        val buttons = ArrayList<DialogButton>()
        if (node is Folder) {
            buttons += DialogButton(getString(R.string.row_open), ButtonStyle.SECONDARY) { openFolder(node.id); true }
            buttons += DialogButton(getString(R.string.row_rename), ButtonStyle.SECONDARY) { askFolder(node); true }
        } else {
            buttons += DialogButton(getString(R.string.row_edit), ButtonStyle.SECONDARY) { openEditor(node.id); true }
        }
        buttons += DialogButton(getString(R.string.row_move), ButtonStyle.SECONDARY) { showMove(node); true }
        buttons += DialogButton(getString(R.string.row_select), ButtonStyle.SECONDARY) { startSelecting(node.id); true }
        buttons += DialogButton(getString(R.string.row_delete), ButtonStyle.DANGER) { confirmDelete(node); true }
        buttons += DialogButton(getString(R.string.dialog_cancel), ButtonStyle.TEXT)
        sheet.buttons(buttons)
        show(sheet)
    }

    /** "Move to…": Home and every folder as an indented list. */
    private fun showMove(node: Node) {
        val library = ShortcutRepository.library(this)
        val targets = FolderLogic.moveTargets(library, node.id, getString(R.string.home_name))
        val noName = getString(R.string.shortcut_no_name)
        val sheet = ui.sheet(this, getString(R.string.move_title, node.name.ifBlank { noName }))
        with(ui) {
            targets.forEachIndexed { index, target ->
                val base = target.name.ifBlank { noName }
                val label = if (target.isCurrent) getString(R.string.move_here, base) else base
                val glyph = if (target.folderId == null) "🏠" else target.icon.ifBlank { "📁" }
                val rowView = row().apply {
                    minimumHeight = dp(56)
                    setPadding(dp(14 + 18 * target.depth), dp(8), dp(14), dp(8))
                    val r = 14f
                    background = rippled(shape(if (target.isCurrent) palette.surfaceAlt else Color.TRANSPARENT, r), r)
                    isClickable = !target.isCurrent
                    isFocusable = !target.isCurrent
                    alpha = if (target.isCurrent) 0.6f else 1f
                    contentDescription = label
                    setOnClickListener { sheet.dismiss(); moveTo(node, target.folderId) }
                }
                rowView.addView(text(glyph, look.type.bodySp + 3, 400, palette.ink).apply { layoutParams = lp(width = ViewGroup.LayoutParams.WRAP_CONTENT) })
                rowView.addView(body(label).apply {
                    layoutParams = lp(width = 0, weight = 1f, start = 12)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                sheet.body.add(rowView, top = if (index == 0) 0 else 2)
            }
            sheet.body.add(textButton(getString(R.string.dialog_cancel)) { sheet.dismiss() }, top = 12)
        }
        show(sheet)
    }

    private fun moveTo(node: Node, folderId: String?) {
        val moved = try {
            ShortcutRepository.move(this, node.id, folderId, Int.MAX_VALUE)
        } catch (e: Exception) {
            Log.w(TAG, "Could not move", e)
            false
        }
        render()
        screen.snack(getString(if (moved) R.string.move_done else R.string.move_failed), error = !moved)
    }

    private fun confirmDelete(node: Node) {
        val name = node.name.ifBlank { getString(R.string.shortcut_no_name) }
        val isFolder = node is Folder
        show(
            ui.confirm(
                this,
                title = if (isFolder) getString(R.string.folder_delete_title, name) else getString(R.string.delete_title, name),
                message = getString(if (isFolder) R.string.folder_delete_message else R.string.delete_message),
                confirmText = getString(R.string.delete_confirm),
                cancelText = getString(R.string.dialog_cancel),
                danger = true,
            ) { delete(node) },
        )
    }

    private fun delete(node: Node) {
        try {
            if (node is Folder) ShortcutRepository.deleteFolder(this, node.id) else ShortcutRepository.delete(this, node.id)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete", e)
            screen.snack(getString(R.string.edit_save_failed), error = true)
            return
        }
        render()
        screen.snack(getString(if (node is Folder) R.string.folder_deleted else R.string.shortcut_deleted))
    }

    // ---------------------------------------------------------------- new folder / rename

    /** A box for the name and picture of a new folder ([existing] = null) or to change one. */
    private fun askFolder(existing: Folder?) {
        val dialog = ui.dialog(this, getString(if (existing == null) R.string.folder_new_title else R.string.folder_rename_title))
        var icon = existing?.icon.orEmpty()
        var color = existing?.color ?: -1
        val name = ui.field(getString(R.string.folder_name_hint)).apply {
            existing?.let { setText(it.name); setSelection(length()) }
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val error = ui.small(getString(R.string.folder_name_required)).apply {
            setTextColor(ui.palette.danger)
            visibility = View.GONE
        }
        val picture = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(ui.palette.ink)
            background = ui.rippled(ui.shape(ui.palette.surfaceAlt, 999f), 999f)
            contentDescription = getString(R.string.folder_picture_change)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)).apply { marginEnd = ui.dp(12) }
        }
        fun showIcon() {
            picture.text = glyphFor(icon, name.text.toString().ifBlank { "?" }, isFolder = true)
        }
        showIcon()
        picture.setOnClickListener {
            IconPicker.show(this, ui, icon) { chosen -> icon = chosen; showIcon() }
        }
        with(ui) {
            val line = row()
            line.addView(picture)
            line.addView(name.apply { layoutParams = lp(width = 0, weight = 1f) })
            dialog.body.add(line, top = 12)
            dialog.body.add(error, top = 8)
            dialog.body.add(ColorPicker.collapsible(ui, color) { color = it }, top = 16)
            dialog.buttons(
                listOf(
                    DialogButton(getString(if (existing == null) R.string.folder_create else R.string.folder_rename_save)) {
                        val text = name.text.toString().trim()
                        if (text.isEmpty()) {
                            error.visibility = View.VISIBLE
                            name.requestFocus()
                            false
                        } else {
                            saveFolder(existing, text, icon, color)
                        }
                    },
                    DialogButton(getString(R.string.dialog_cancel), ButtonStyle.TEXT),
                ),
            )
        }
        show(dialog)
        name.requestFocus()
    }

    private fun saveFolder(existing: Folder?, name: String, icon: String, color: Int): Boolean {
        try {
            if (existing == null) {
                ShortcutRepository.saveFolder(this, Folder(ShortcutRepository.newId(), name, currentFolderId, icon = icon, color = color))
            } else {
                // Use the stored copy, in case it was moved meanwhile.
                val latest = ShortcutRepository.findFolder(this, existing.id) ?: existing
                ShortcutRepository.saveFolder(this, latest.copy(name = name, icon = icon, color = color))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not save folder", e)
            screen.snack(getString(R.string.folder_save_failed), error = true)
            return false
        }
        render()
        screen.snack(getString(if (existing == null) R.string.folder_made else R.string.folder_saved))
        return true
    }

    companion object {
        /** The id of the folder to open. Leave it out for the top level. */
        const val EXTRA_FOLDER_ID = "folder_id"

        private const val TAG = "MainActivity"
        private const val STATE_FOLDER = "folder"
    }
}

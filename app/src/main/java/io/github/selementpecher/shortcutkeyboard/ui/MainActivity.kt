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
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
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

        val gear = ui.iconButton(UiIcon.Kind.GEAR, getString(R.string.settings_open)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        screen = ui.screen(getString(R.string.app_name), trailing = listOf(gear), large = true, withBottomBar = true)
        screen.bottomBar.orientation = LinearLayout.HORIZONTAL
        with(ui) {
            screen.bottomBar.addView(primaryButton(getString(R.string.shortcut_new)) { openEditor(null) }.apply {
                layoutParams = lp(width = 0, weight = 1f, end = 6)
                setPadding(dp(10), dp(8), dp(10), dp(8))
            })
            screen.bottomBar.addView(secondaryButton(getString(R.string.folder_new)) { askFolder(null) }.apply {
                layoutParams = lp(width = 0, weight = 1f, start = 6)
                setPadding(dp(10), dp(8), dp(10), dp(8))
            })
        }
        screen.install(this)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back goes up a folder first, then leaves.
        if (currentFolderId != null) goUp() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (ui.isStale()) {
            recreate()
            return
        }
        render()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The keyboard picker is a dialog over this screen, so there is no onResume after it.
        if (hasFocus && ::screen.isInitialized) render()
    }

    override fun onStop() {
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
        val content = screen.content
        content.removeAllViews()
        with(ui) {
            val enabled = KeyboardSetup.isEnabled(this@MainActivity)
            val ready = enabled && KeyboardSetup.isSelected(this@MainActivity)
            if (ready) content.add(readyLine()) else content.add(setupCard(enabled))

            val folder = library.folder(currentFolderId)
            if (folder != null) content.add(folderHeader(library, folder), top = 20)

            val children = library.children(currentFolderId)
            if (children.isEmpty()) {
                content.add(emptyState(inFolder = folder != null), top = 28)
            } else {
                val tiles = children.map { tileFor(it, library) }
                content.add(grid(columns, tiles), top = if (folder != null) 12 else 20)
                content.add(small(getString(R.string.home_tip)).apply { gravity = Gravity.CENTER }, top = 20)
            }
        }
    }

    private fun tileFor(node: Node, library: Library): View {
        val name = node.name.ifBlank { getString(R.string.shortcut_no_name) }
        return when (node) {
            is Folder -> {
                val count = FolderLogic.itemCount(library, node.id)
                val items = if (count == 0) getString(R.string.folder_empty_row) else resources.getQuantityString(R.plurals.folder_items, count, count)
                ui.tile(
                    name = name, icon = node.icon, kind = TileKind.FOLDER, colorKey = node.id, color = node.color, subtitle = items,
                    description = getString(R.string.folder_row_description, name, items),
                    onClick = { openFolder(node.id) },
                    onLongClick = { showActions(node) },
                )
            }
            is Shortcut -> ui.tile(
                name = name, icon = node.icon, kind = TileKind.SHORTCUT, colorKey = node.id, color = node.color,
                description = getString(R.string.shortcut_row_description, name, ""),
                onClick = { openEditor(node.id) },
                onLongClick = { showActions(node) },
            )
        }
    }

    /** The two setup steps, with ticks that update live. */
    private fun setupCard(enabled: Boolean): View = with(ui) {
        val selected = enabled && KeyboardSetup.isSelected(this@MainActivity)
        val card = card()
        card.add(heading(getString(R.string.setup_title)))
        card.add(step(1, getString(R.string.setup_step1), getString(R.string.setup_step1_button), done = enabled, available = true) { openKeyboardSettings() }, top = 16)
        card.add(step(2, getString(R.string.setup_step2), getString(R.string.setup_step2_button), done = selected, available = enabled) { showKeyboardPicker() }, top = 16)
        card.add(small(getString(R.string.setup_privacy)), top = 16)
        card
    }

    private fun step(number: Int, label: String, buttonText: String, done: Boolean, available: Boolean, onClick: () -> Unit): View = with(ui) {
        val line = row()
        val badgeFill = if (done) palette.success else if (available) palette.accent else palette.surfaceAlt
        val badge = TextView(this@MainActivity).apply {
            text = if (done) "✓" else number.toString()
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, look.type.bodySp)
            typeface = typefaceOf(700)
            setTextColor(if (done || available) readableOn(badgeFill) else palette.inkSoft)
            background = shape(badgeFill, 999f)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }
        line.addView(badge)
        val text = body(label).apply {
            typeface = typefaceOf(600)
            layoutParams = lp(width = 0, weight = 1f, start = 14)
            if (!available) setTextColor(palette.inkSoft)
        }
        line.addView(text)
        val box = column()
        box.contentDescription = getString(if (done) R.string.setup_step_done else R.string.setup_step_todo, label)
        box.addView(line)
        if (!done) {
            val button = if (available) primaryButton(buttonText) { onClick() } else secondaryButton(buttonText)
            if (!available) {
                button.isEnabled = false
                button.alpha = 0.5f
            }
            box.add(button, top = 10)
        }
        box
    }

    private fun readyLine(): View = with(ui) {
        val box = column().apply {
            val r = look.shape.cardRadiusDp.coerceAtMost(20).toFloat()
            background = shape(blend(palette.bg, palette.success, 0.14f), r)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val line = row()
        line.addView(TextView(this@MainActivity).apply {
            text = "✓"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = typefaceOf(800)
            setTextColor(readableOn(palette.success))
            background = shape(palette.success, 999f)
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        })
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
        val back = iconButton(UiIcon.Kind.BACK, getString(R.string.action_up_description, parentName)) { goUp() }
        back.background = rippled(shape(palette.surfaceAlt, 999f), 999f)
        line.addView(back)
        line.addView(text(FolderLogic.pathText(library, currentFolderId, home, noName), look.type.bodySp, 700, palette.ink).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            layoutParams = lp(width = 0, weight = 1f, start = 12)
        })
        line
    }

    private fun emptyState(inFolder: Boolean): View = with(ui) {
        val box = column().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(12), 0, dp(12), 0) }
        box.add(heading(getString(R.string.home_empty_title)).apply { gravity = Gravity.CENTER })
        box.add(body(getString(if (inFolder) R.string.folder_empty else R.string.shortcuts_empty)).apply {
            gravity = Gravity.CENTER
            setTextColor(palette.inkSoft)
        }, top = 8)
        box
    }

    // ---------------------------------------------------------------- setup actions

    private fun openKeyboardSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.setup_no_settings, Toast.LENGTH_LONG).show()
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
        if (!moved) Toast.makeText(this, R.string.move_failed, Toast.LENGTH_LONG).show()
        render()
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
            Toast.makeText(this, R.string.edit_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, if (node is Folder) R.string.folder_deleted else R.string.shortcut_deleted, Toast.LENGTH_SHORT).show()
        render()
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
            dialog.body.add(label(getString(R.string.color_label)), top = 18)
            dialog.body.add(ColorPicker.view(ui, color) { color = it }, top = 8)
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
            Toast.makeText(this, R.string.folder_save_failed, Toast.LENGTH_LONG).show()
            return false
        }
        render()
        return true
    }

    companion object {
        /** The id of the folder to open. Leave it out for the top level. */
        const val EXTRA_FOLDER_ID = "folder_id"

        private const val TAG = "MainActivity"
        private const val STATE_FOLDER = "folder"
    }
}

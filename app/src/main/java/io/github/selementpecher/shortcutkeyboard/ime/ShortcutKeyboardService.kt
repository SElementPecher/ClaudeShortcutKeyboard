package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.fill.TriggerDetector
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template
import io.github.selementpecher.shortcutkeyboard.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The keyboard.
 *
 * From top to bottom: the shortcut bar, the fill-in panel (only while filling in a shortcut's
 * blanks) and the keys. While a blank is being typed, the keys type into the panel's box instead
 * of the app; everything else goes straight to the app's text field.
 *
 * All state lives here and in [KeyboardController]; the views are rebuilt from it whenever the
 * system asks for a new input view (for example after switching to dark mode).
 */
class ShortcutKeyboardService :
    InputMethodService(),
    KeyView.Listener,
    ShortcutBarView.Listener,
    FillPanelView.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val keyboard = KeyboardController()
    private var shortcuts: List<Shortcut> = emptyList()
    private var destroyed = false

    // Views, made again by onCreateInputView.
    private var bar: ShortcutBarView? = null
    private var panel: FillPanelView? = null
    private var keys: KeysView? = null
    private var keyHeight = 0
    private var barHeight = 0
    private var compact = false

    // The app's text field.
    private var fieldPage = KeyboardPage.LETTERS
    private var enterAction = EnterAction.ENTER_KEY
    private var isPasswordField = false
    private var showNextKeyboard = false
    private var selectionStart = -1
    private var selectionEnd = -1
    private var swallowBackUp = false

    // Filling in a shortcut.
    private var session: FillSession? = null

    /** The box being typed into, or null when the current blank is not being typed. */
    private var typing: TextBuffer? = null
    private var typingIsNumber = false
    private var typingCapsAtStart = false
    private var capsAtMessageStart = false

    /** Blanks typed into during this fill-in, so going back to one does not select its text. */
    private val typedKeys = HashSet<String>()

    private val repositoryListener: () -> Unit = {
        // May be called from any thread; the views must only be touched on the main thread.
        handler.post { if (!destroyed) onShortcutsChanged() }
    }

    override fun onCreate() {
        super.onCreate()
        ShortcutRepository.addListener(repositoryListener)
    }

    override fun onDestroy() {
        destroyed = true
        ShortcutRepository.removeListener(repositoryListener)
        handler.removeCallbacksAndMessages(null)
        keys?.stopAll()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        keys?.stopAll()
        val screenHeight = resources.configuration.screenHeightDp
        compact = screenHeight in 1 until COMPACT_SCREEN_HEIGHT_DP
        keyHeight = px(if (compact) R.dimen.kb_key_height_compact else R.dimen.kb_key_height)
        barHeight = px(if (compact) R.dimen.kb_bar_height_compact else R.dimen.kb_bar_height)

        val newBar = ShortcutBarView(this, this)
        val newPanel = FillPanelView(this, this)
        val newKeys = KeysView(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.kb_background))
            addView(newBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barHeight))
            addView(newPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(newKeys, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            setOnApplyWindowInsetsListener { view, insets -> applyInsets(view, insets) }
        }
        bar = newBar
        panel = newPanel
        keys = newKeys
        styleNavigationBar()
        shortcuts = loadShortcuts()
        render()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val inputType = info?.inputType ?: InputType.TYPE_NULL
        selectionStart = info?.initialSelStart ?: -1
        selectionEnd = info?.initialSelEnd ?: -1
        fieldPage = EditorRules.startPage(inputType)
        enterAction = EditorRules.enterAction(info?.imeOptions ?: 0, inputType)
        isPasswordField = EditorRules.isPassword(inputType)
        showNextKeyboard = offersNextKeyboard()
        if (!restarting) {
            // A new text field: start afresh.
            session = null
            typing = null
            keyboard.reset(fieldPage)
        }
        shortcuts = loadShortcuts()
        if (typing == null) updateAutoCaps()
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        keys?.stopAll()
        if (session != null) {
            session = null
            typing = null
            keyboard.reset(fieldPage)
            render()
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selectionStart = newSelStart
        selectionEnd = newSelEnd
        if (session == null) {
            updateAutoCaps()
            renderKeys()
        }
    }

    /** The back button closes the fill-in panel first, instead of hiding the keyboard. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            swallowBackUp = session != null && isInputViewShown
            if (swallowBackUp) {
                if ((event?.repeatCount ?: 0) == 0) closeFill()
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

    // ---- Keys ----

    override fun onKey(key: Key, timeMillis: Long) {
        val inBox = typing != null
        when (val output = keyboard.onKey(key, timeMillis)) {
            is KeyOutput.Type -> if (inBox) typeIntoBox(output.text) else typeIntoApp(output.text)
            KeyOutput.Delete -> if (inBox) deleteInBox() else deleteInApp()
            KeyOutput.Space -> if (inBox) typeIntoBox(" ") else spaceInApp()
            KeyOutput.Enter -> if (inBox) onFillNext() else enterInApp()
            KeyOutput.NextKeyboard -> switchToNextKeyboard()
            KeyOutput.None -> Unit
        }
        renderKeys()
    }

    override fun onSpaceLongPress() {
        showKeyboardPicker()
    }

    private fun typeIntoApp(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        updateAutoCaps()
    }

    /** Space opens a shortcut when the word before the cursor is its trigger word. */
    private fun spaceInApp() {
        val ic = currentInputConnection ?: return
        if (!isPasswordField && selectionStart == selectionEnd) {
            val match = TriggerDetector.find(ic.getTextBeforeCursor(TRIGGER_LOOKBACK, 0), shortcuts)
            if (match != null) {
                ic.deleteSurroundingText(match.length, 0)
                openShortcut(match.shortcut)
                return
            }
        }
        ic.commitText(" ", 1)
        updateAutoCaps()
    }

    private fun deleteInApp() {
        val ic = currentInputConnection ?: return
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            ic.commitText("", 1)
            // Until the app reports back, assume the selection is gone.
            selectionStart = minOf(selectionStart, selectionEnd)
            selectionEnd = selectionStart
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        updateAutoCaps()
    }

    private fun enterInApp() {
        val ic = currentInputConnection ?: return
        when {
            enterAction.isEditorAction -> ic.performEditorAction(enterAction.editorAction)
            enterAction == EnterAction.NEW_LINE -> ic.commitText("\n", 1)
            else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        updateAutoCaps()
    }

    /** Capital letter at the start of a sentence, as the text field (or the blank's place in the message) asks. */
    private fun updateAutoCaps() {
        val buffer = typing
        val caps = if (buffer != null) {
            !typingIsNumber && if (buffer.isEmpty || buffer.isAllSelected) typingCapsAtStart else buffer.isAfterSentenceEnd
        } else {
            appWantsCaps()
        }
        keyboard.shift.autoCapitalize(caps)
    }

    private fun appWantsCaps(): Boolean {
        val ic = currentInputConnection ?: return false
        val inputType = currentInputEditorInfo?.inputType ?: return false
        if (inputType == InputType.TYPE_NULL) return false
        return ic.getCursorCapsMode(inputType) != 0
    }

    private fun offersNextKeyboard(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            shouldOfferSwitchingToNextInputMethod()
        } else {
            val token = window?.window?.attributes?.token
            @Suppress("DEPRECATION")
            token != null && inputMethodManager()?.shouldOfferSwitchingToNextInputMethod(token) == true
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Could not check for other keyboards", e)
        false
    }

    private fun switchToNextKeyboard() {
        val switched = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToNextInputMethod(false)
            } else {
                val token = window?.window?.attributes?.token
                @Suppress("DEPRECATION")
                token != null && inputMethodManager()?.switchToNextInputMethod(token, false) == true
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not switch keyboard", e)
            false
        }
        if (!switched) showKeyboardPicker()
    }

    private fun showKeyboardPicker() {
        try {
            inputMethodManager()?.showInputMethodPicker()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not show the keyboard picker", e)
        }
    }

    private fun inputMethodManager(): InputMethodManager? =
        getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    // ---- Shortcut bar ----

    override fun onShortcutChosen(shortcut: Shortcut) {
        openShortcut(shortcut)
    }

    override fun onOpenApp() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not open the app", e)
        }
    }

    private fun onShortcutsChanged() {
        shortcuts = loadShortcuts()
        bar?.setShortcuts(shortcuts)
    }

    private fun loadShortcuts(): List<Shortcut> = try {
        ShortcutRepository.all(this)
    } catch (e: Exception) {
        Log.e(TAG, "Could not load shortcuts", e)
        emptyList()
    }

    // ---- Filling in ----

    /** Inserts a shortcut with no blanks straight away, otherwise opens the fill-in panel. */
    private fun openShortcut(shortcut: Shortcut) {
        val ic = currentInputConnection ?: return
        val newSession = FillSession(shortcut, locale = locale())
        if (newSession.isEmpty) {
            ic.commitText(newSession.result(), 1)
            updateAutoCaps()
            renderKeys()
            return
        }
        keys?.stopAll()
        capsAtMessageStart = appWantsCaps()
        typedKeys.clear()
        session = newSession
        enterStep()
    }

    /** Shows the current blank: typing for text and numbers, a list, or the time/date buttons. */
    private fun enterStep() {
        val session = session ?: return
        val variable = session.current ?: return closeFill()
        typing = null
        when (variable.type) {
            VariableType.TEXT, VariableType.NUMBER -> startTyping(variable, session.valueOf(variable))
            VariableType.CHOICE -> {
                // Nothing to pick from yet: go straight to typing.
                if (variable.options.none { it.isNotBlank() } && session.valueOf(variable).isNullOrBlank()) {
                    startTyping(variable, null)
                }
            }
            VariableType.TIME, VariableType.DATE -> Unit
        }
        render()
    }

    private fun startTyping(variable: VariableDef, prefill: String?) {
        val session = session ?: return
        // A remembered value starts selected, so typing replaces it; text typed this time does not.
        typing = TextBuffer(prefill.orEmpty(), selectAll = !prefill.isNullOrEmpty() && variable.key !in typedKeys)
        typingIsNumber = variable.type == VariableType.NUMBER
        typingCapsAtStart = !typingIsNumber && when (FillPreview.positionOf(session.template, variable.key)) {
            FillPreview.BlankPosition.MESSAGE_START -> capsAtMessageStart
            FillPreview.BlankPosition.SENTENCE_START -> true
            FillPreview.BlankPosition.MID_SENTENCE -> false
        }
        keyboard.reset(if (typingIsNumber) KeyboardPage.SYMBOLS else KeyboardPage.LETTERS)
        updateAutoCaps()
    }

    private fun typeIntoBox(text: String) {
        val session = session ?: return
        val variable = session.current ?: return
        val buffer = typing ?: return
        buffer.insert(text)
        session.setValue(variable, buffer.text)
        typedKeys += variable.key
        updateAutoCaps()
        render()
    }

    private fun deleteInBox() {
        val session = session ?: return
        val variable = session.current ?: return
        val buffer = typing ?: return
        if (buffer.deleteBackward()) {
            session.setValue(variable, buffer.text)
            typedKeys += variable.key
        }
        updateAutoCaps()
        render()
    }

    private fun insertFill() {
        val session = session ?: return
        val ic = currentInputConnection
        if (ic != null) {
            ic.commitText(session.result(), 1)
            rememberValues(session)
        }
        closeFill()
    }

    /** Saves the values used (and new list options) so they are offered first next time. */
    private fun rememberValues(session: FillSession) {
        try {
            val saved = ShortcutRepository.find(this, session.shortcut.id) ?: return
            // Changed in the app while the panel was open: keep those changes instead.
            if (saved != session.shortcut) return
            val updated = session.updatedShortcut()
            if (updated != saved) ShortcutRepository.save(this, updated)
        } catch (e: Exception) {
            Log.w(TAG, "Could not remember the values used", e)
        }
    }

    private fun closeFill() {
        session = null
        typing = null
        keys?.stopAll()
        keyboard.reset(fieldPage)
        updateAutoCaps()
        render()
    }

    override fun onFillCancel() = closeFill()

    override fun onFillInsert() = insertFill()

    override fun onFillBack() {
        val session = session ?: return
        if (session.back()) enterStep()
    }

    override fun onFillNext() {
        val session = session ?: return
        if (session.isLast) {
            insertFill()
        } else {
            session.next()
            enterStep()
        }
    }

    override fun onChoicePicked(value: String) {
        val session = session ?: return
        val variable = session.current ?: return
        session.setValue(variable, value)
        if (!session.isLast) session.next()
        enterStep()
    }

    override fun onTypeNew() {
        val variable = session?.current ?: return
        startTyping(variable, null)
        render()
    }

    override fun onShowList() {
        typing = null
        render()
    }

    override fun onTimeAdjust(minutes: Long) {
        val session = session ?: return
        session.current?.let { session.adjustTime(it, minutes) }
        render()
    }

    override fun onTimeNow() {
        val session = session ?: return
        session.current?.let { session.setTimeToNow(it) }
        render()
    }

    override fun onDateAdjust(days: Long) {
        val session = session ?: return
        session.current?.let { session.adjustDate(it, days) }
        render()
    }

    override fun onDatePicked(daysFromToday: Long) {
        val session = session ?: return
        session.current?.let { session.setDate(it, LocalDate.now().plusDays(daysFromToday)) }
        render()
    }

    // ---- Drawing ----

    private fun render() {
        val session = session
        val filling = session?.current != null
        bar?.let {
            it.setShortcuts(shortcuts)
            it.visibility = if (filling) View.GONE else View.VISIBLE
        }
        panel?.let { view ->
            if (session != null && filling) {
                view.visibility = View.VISIBLE
                // Without the keys, keep the keyboard the same height so it does not jump about.
                val fixedHeight = if (typing == null) barHeight + (keys?.heightFor(keyHeight) ?: 0) else null
                view.render(fillUi(session), fixedHeight)
            } else {
                view.visibility = View.GONE
            }
        }
        keys?.visibility = if (!filling || typing != null) View.VISIBLE else View.GONE
        renderKeys()
    }

    private fun renderKeys() {
        keys?.render(keyboard.page, keyboard.shift.mode, keyboard::labelFor, enterLook(), showNextKeyboard, keyHeight)
    }

    private fun enterLook(): KeysView.EnterLook {
        val session = session
        if (session != null && typing != null) {
            val label = getString(if (session.isLast) R.string.kb_insert else R.string.kb_action_next)
            return KeysView.EnterLook(label, 0, label)
        }
        val labelRes = when (enterAction) {
            EnterAction.GO -> R.string.kb_action_go
            EnterAction.SEARCH -> R.string.kb_action_search
            EnterAction.SEND -> R.string.kb_action_send
            EnterAction.NEXT -> R.string.kb_action_next
            EnterAction.DONE -> R.string.kb_action_done
            EnterAction.PREVIOUS -> R.string.kb_action_previous
            EnterAction.NEW_LINE, EnterAction.ENTER_KEY -> 0
        }
        return if (labelRes != 0) {
            val label = getString(labelRes)
            KeysView.EnterLook(label, 0, label)
        } else {
            KeysView.EnterLook(null, R.drawable.kb_ic_enter, getString(R.string.kb_desc_enter))
        }
    }

    private fun fillUi(session: FillSession): FillPanelView.Ui {
        val variable = checkNotNull(session.current)
        val locale = locale()
        val buffer = typing
        val body = when {
            buffer != null -> FillPanelView.Body.Typing(
                text = buffer.text,
                allSelected = buffer.isAllSelected,
                canShowList = variable.type == VariableType.CHOICE && variable.options.any { it.isNotBlank() },
            )
            variable.type == VariableType.TIME -> FillPanelView.Body.Time(session.displayValue(variable))
            variable.type == VariableType.DATE -> {
                val date = Template.parseDate(session.valueOf(variable).orEmpty())
                FillPanelView.Body.Date(
                    value = session.displayValue(variable),
                    weekday = date?.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                    quickDay = date?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }?.takeIf { it in -1L..1L },
                )
            }
            else -> {
                val options = choiceOptions(session, variable)
                val value = session.valueOf(variable)
                FillPanelView.Body.Choice(options, options.firstOrNull { it.equals(value, ignoreCase = true) })
            }
        }
        val name = session.shortcut.name.ifBlank { getString(R.string.kb_untitled) }
        return FillPanelView.Ui(
            title = getString(R.string.kb_fill_title, name, session.index + 1, session.variables.size),
            preview = preview(session, variable, locale),
            question = variable.name.replaceFirstChar { it.titlecase(locale) },
            stepKey = Pair(session, session.index),
            isFirst = session.isFirst,
            isLast = session.isLast,
            compact = compact,
            body = body,
        )
    }

    /** The saved options, with the current value first if it is a new one (typed, or remembered). */
    private fun choiceOptions(session: FillSession, variable: VariableDef): List<String> {
        val options = variable.options.filter { it.isNotBlank() }
        val value = session.valueOf(variable)?.takeIf { it.isNotBlank() }
        val all = if (value != null && options.none { it.equals(value, ignoreCase = true) }) listOf(value) + options else options
        return all.distinctBy { it.lowercase(Locale.ROOT) }
    }

    /** The message so far, with the blank being filled in shown in bold and underlined. */
    private fun preview(session: FillSession, variable: VariableDef, locale: Locale): CharSequence {
        val result = FillPreview.build(session, variable.key, LocalDateTime.now(), locale)
        val text = SpannableString(result.text)
        val strong = getColor(R.color.kb_key_text)
        for (range in result.highlights) {
            val end = range.last + 1
            text.setSpan(StyleSpan(Typeface.BOLD), range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(UnderlineSpan(), range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(strong), range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    // ---- Window ----

    /** Keeps the keys clear of the navigation bar when the keyboard is drawn behind it. */
    private fun applyInsets(view: View, insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val nav = insets.getInsets(WindowInsets.Type.navigationBars())
            view.setPadding(nav.left, 0, nav.right, nav.bottom)
        } else {
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, 0, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        return insets
    }

    /** Navigation bar in the keyboard's colour, with dark buttons in light mode so they stay visible. */
    private fun styleNavigationBar() {
        val imeWindow = window?.window ?: return
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        try {
            if (Build.VERSION.SDK_INT < VERSION_EDGE_TO_EDGE) {
                imeWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                @Suppress("DEPRECATION")
                imeWindow.navigationBarColor = getColor(R.color.kb_background)
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

        /** Characters before the cursor to look at for a trigger word. */
        private const val TRIGGER_LOOKBACK = 64

        /** Screens shorter than this (phones in landscape) get smaller keys. */
        private const val COMPACT_SCREEN_HEIGHT_DP = 480

        /** Android 15, where apps (and keyboards) are always drawn edge to edge. */
        private const val VERSION_EDGE_TO_EDGE = 35
    }
}

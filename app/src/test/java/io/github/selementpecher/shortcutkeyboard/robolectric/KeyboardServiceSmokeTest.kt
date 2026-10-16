package io.github.selementpecher.shortcutkeyboard.robolectric

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.github.selementpecher.shortcutkeyboard.ime.ShortcutKeyboardService
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The keyboard starts and builds its view without crashing. Runs on CI only (needs Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardServiceSmokeTest {

    @Test
    fun keyboardCreatesItsView() {
        val controller = Robolectric.buildService(ShortcutKeyboardService::class.java).create()
        val view = controller.get().onCreateInputView()
        assertNotNull(view)
        controller.destroy()
    }

    @Test
    fun keyboardStartsInAFieldWithNoConnection() {
        val controller = Robolectric.buildService(ShortcutKeyboardService::class.java).create()
        val service = controller.get()
        assertNotNull(service.onCreateInputView())

        // There is no app text field here, so currentInputConnection is null: this must not crash.
        val text = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        service.onStartInputView(text, false)
        service.onFinishInputView(true)

        val number = EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }
        service.onStartInputView(number, false)
        service.onFinishInputView(true)

        controller.destroy()
    }
}

package io.github.selementpecher.shortcutkeyboard.data

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendAfterAddTest {
    @Test
    fun onlyASendActionCanBePressed() {
        assertTrue(SendAfterAdd.canSend(EditorInfo.IME_ACTION_SEND))
        assertFalse(SendAfterAdd.canSend(EditorInfo.IME_ACTION_DONE))
        assertFalse(SendAfterAdd.canSend(EditorInfo.IME_ACTION_SEARCH))
        assertFalse(SendAfterAdd.canSend(EditorInfo.IME_ACTION_GO))
        assertFalse(SendAfterAdd.canSend(EditorInfo.IME_ACTION_NONE))
    }

    @Test
    fun aBoxThatKeepsEnterForNewLinesIsNotSent() {
        assertFalse(SendAfterAdd.canSend(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION))
    }

    @Test
    fun otherFlagsDoNotHideTheAction() {
        assertTrue(SendAfterAdd.canSend(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI))
    }
}

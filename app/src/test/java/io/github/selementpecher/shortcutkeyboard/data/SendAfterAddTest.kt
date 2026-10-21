package io.github.selementpecher.shortcutkeyboard.data

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
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

class SendPlanTest {
    @Test
    fun aBoxWithSendIsSentWhateverTheApp() {
        assertEquals(SendAfterAdd.Plan.Send, SendAfterAdd.plan(EditorInfo.IME_ACTION_SEND, "com.whatsapp"))
        assertEquals(SendAfterAdd.Plan.Send, SendAfterAdd.plan(EditorInfo.IME_ACTION_SEND, "com.example.anything"))
    }

    @Test
    fun aKnownChatAppWithoutSendSaysWhichSettingToTurnOn() {
        val plan = SendAfterAdd.plan(EditorInfo.IME_FLAG_NO_ENTER_ACTION, "com.whatsapp")
        assertEquals(SendAfterAdd.Plan.TurnOnInApp(SendAfterAdd.AppSetting("WhatsApp", "Enter is send", "Settings › Chats")), plan)
    }

    @Test
    fun anyOtherAppOnlyAdds() {
        assertEquals(SendAfterAdd.Plan.AddOnly, SendAfterAdd.plan(EditorInfo.IME_ACTION_DONE, "com.example.notes"))
        assertEquals(SendAfterAdd.Plan.AddOnly, SendAfterAdd.plan(EditorInfo.IME_ACTION_NONE, null))
    }
}

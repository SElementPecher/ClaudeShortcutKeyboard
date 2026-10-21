package io.github.selementpecher.shortcutkeyboard.data

import android.content.Context
import android.view.inputmethod.EditorInfo

/**
 * The "send after adding" choice. When on, tapping Add also presses the app's own Send button, but only where the
 * text box says it has one (an editor action of Send). A phone keyboard cannot reliably send anywhere else.
 */
object SendAfterAdd {
    private const val PREFS = "keyboard"
    private const val KEY = "send_after_add"

    fun isOn(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    /**
     * Chat apps that only show a Send action when their own "Enter to send" setting is on. Until it is, quiKey cannot
     * send there, so the keyboard says which setting to turn on. Keyed by the app's ID; the wording is the app's own.
     */
    val appsWithEnterSetting: Map<String, AppSetting> = mapOf(
        "com.whatsapp" to AppSetting("WhatsApp", "Enter is send", "Settings › Chats"),
        "com.whatsapp.w4b" to AppSetting("WhatsApp Business", "Enter is send", "Settings › Chats"),
        "org.telegram.messenger" to AppSetting("Telegram", "Send by Enter", "Settings › Chat Settings"),
        "org.telegram.messenger.web" to AppSetting("Telegram", "Send by Enter", "Settings › Chat Settings"),
        "org.thoughtcrime.securesms" to AppSetting("Signal", "Enter key sends", "Settings › Chats"),
    )

    /** What Add does in the box with these [imeOptions], in the app [packageName], when sending is turned on. */
    fun plan(imeOptions: Int, packageName: String?): Plan = when {
        canSend(imeOptions) -> Plan.Send
        packageName != null && packageName in appsWithEnterSetting -> Plan.TurnOnInApp(appsWithEnterSetting.getValue(packageName))
        else -> Plan.AddOnly
    }

    /** An app's own setting that makes its message box show a Send action. */
    data class AppSetting(val appName: String, val setting: String, val where: String)

    sealed interface Plan {
        /** The box has a Send action: Add presses it after adding the words. */
        data object Send : Plan

        /** A known chat app with its "Enter to send" setting off: Add only adds, and the keyboard says how to turn it on. */
        data class TurnOnInApp(val setting: AppSetting) : Plan

        /** Anywhere else: Add only adds the words. */
        data object AddOnly : Plan
    }

    /** True when the text box with these [imeOptions] has a Send action that can be pressed. */
    fun canSend(imeOptions: Int): Boolean =
        (imeOptions and EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_SEND &&
            (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
}

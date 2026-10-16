package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

/**
 * Lets a screen draw behind the status and navigation bars, then pads it so nothing is hidden.
 *
 * On Android 15 and newer every app is drawn edge to edge and `adjustResize` no longer makes room
 * for the keyboard, so the screen handles the insets itself. We opt in from Android 11 so it
 * behaves the same there. On older phones the window keeps the system's own padding and the
 * listener just sees no insets.
 */
internal object EdgeToEdge {

    /**
     * @param root the screen's root view: padded at the sides, and at the bottom by the larger of
     *   the navigation bar and the on-screen keyboard. Shrinking it lets its ScrollView keep the
     *   field being typed in on screen.
     * @param topBar padded at the top by the status bar.
     */
    fun apply(activity: Activity, root: View, topBar: View) {
        val window = activity.window
        val night = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            // Dark bar icons on the light theme, light ones on the dark theme.
            val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (night) 0 else lightBars, lightBars)
        } else if (!night) {
            useDarkNavigationButtons(window)
        }

        root.setOnApplyWindowInsetsListener { view, insets ->
            val padding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                Padding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            } else {
                legacyPadding(insets)
            }
            topBar.setPadding(topBar.paddingLeft, padding.top, topBar.paddingRight, topBar.paddingBottom)
            view.setPadding(padding.left, view.paddingTop, padding.right, padding.bottom)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) WindowInsets.CONSUMED else insets
        }
        root.requestApplyInsets()
    }

    private class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** Before Android 11 the bottom inset already includes the keyboard. */
    @Suppress("DEPRECATION")
    private fun legacyPadding(insets: WindowInsets) = Padding(
        insets.systemWindowInsetLeft,
        insets.systemWindowInsetTop,
        insets.systemWindowInsetRight,
        insets.systemWindowInsetBottom,
    )

    /** The theme colours the navigation bar light, so its buttons need to be dark (Android 8–10). */
    @Suppress("DEPRECATION")
    private fun useDarkNavigationButtons(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val decor = window.decorView
            decor.systemUiVisibility = decor.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            // Android 8.0 can't show dark navigation buttons, so keep the bar dark behind them.
            window.navigationBarColor = Color.BLACK
        }
    }
}

package io.github.selementpecher.shortcutkeyboard.robolectric

import io.github.selementpecher.shortcutkeyboard.look.Highlighter
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.look.Looks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The keyboard can follow the app's Look or have its own. Runs on CI only (needs Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LookMixTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test
    fun theKeyboardFollowsTheAppUntilGivenItsOwnLook() {
        LookStore.set(app, Looks.BOLD)
        assertTrue(LookStore.keyboardFollowsApp(app))
        assertEquals(Looks.BOLD.id, LookStore.keyboard(app).id)
        LookStore.set(app, Looks.CALM)
        assertEquals(Looks.CALM.id, LookStore.keyboard(app).id)
    }

    @Test
    fun appAndKeyboardCanDiffer() {
        LookStore.set(app, Looks.CALM)
        LookStore.setKeyboard(app, Looks.EXERCISE)
        assertFalse(LookStore.keyboardFollowsApp(app))
        assertEquals(Looks.CALM.id, LookStore.current(app).id)
        assertEquals(Looks.EXERCISE.id, LookStore.keyboard(app).id)
        // Reading the keyboard's Look does not switch the app's screens to the Exercise book style.
        assertFalse(Highlighter.on)
        LookStore.setKeyboard(app, null)
        assertEquals(Looks.CALM.id, LookStore.keyboard(app).id)
    }
}

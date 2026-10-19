package io.github.selementpecher.shortcutkeyboard.robolectric

import android.app.Activity
import android.content.Intent
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.ui.EditShortcutActivity
import io.github.selementpecher.shortcutkeyboard.ui.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Each app screen opens without crashing. Runs on CI only (needs Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivitySmokeTest {

    @Test
    fun mainActivityLaunches() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertOpen(controller.get())
        close(controller)
    }

    @Test
    fun editShortcutActivityLaunchesForANewShortcut() {
        val controller = Robolectric.buildActivity(EditShortcutActivity::class.java).setup()
        assertOpen(controller.get())
        close(controller)
    }

    @Test
    fun editShortcutActivityLaunchesForASavedShortcut() {
        val context = RuntimeEnvironment.getApplication()
        val shortcut = Shortcut(
            id = "smoke-test",
            name = "Smoke test",
            template = "{child} had {medicine} at {time}, next at {time+4h} on {today}",
            variables = listOf(
                VariableDef("child", VariableType.CHOICE, options = listOf("Sam"), lastValue = "Sam"),
                VariableDef("medicine", VariableType.TEXT),
                VariableDef("time", VariableType.TIME),
            ),
        )
        ShortcutRepository.save(context, shortcut)

        val intent = Intent(context, EditShortcutActivity::class.java).putExtra(EXTRA_ID, shortcut.id)
        val controller = Robolectric.buildActivity(EditShortcutActivity::class.java, intent).setup()
        assertOpen(controller.get())
        close(controller)
    }

    @Test
    fun editShortcutActivityLaunchesForAnIdThatIsGone() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, EditShortcutActivity::class.java).putExtra(EXTRA_ID, "no-such-shortcut")
        val controller = Robolectric.buildActivity(EditShortcutActivity::class.java, intent).setup()
        assertOpen(controller.get())
        close(controller)
    }

    private fun assertOpen(activity: Activity) {
        assertNotNull(activity.window.decorView)
        assertFalse(activity.isFinishing)
    }

    /** Ends the lifecycle so no listener is left behind for the next test. */
    private fun close(controller: ActivityController<out Activity>) {
        controller.pause().stop().destroy()
    }

    private companion object {
        /** The extra the spec gives EditShortcutActivity for the shortcut to change. */
        const val EXTRA_ID = "id"
    }
}

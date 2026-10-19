package io.github.selementpecher.shortcutkeyboard.robolectric

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.ui.EditShortcutActivity
import io.github.selementpecher.shortcutkeyboard.ui.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun editShortcutActivitySurvivesTurningThePhone() {
        val newOne = Robolectric.buildActivity(EditShortcutActivity::class.java).setup()
        newOne.recreate()
        assertOpen(newOne.get())
        close(newOne)

        val context = RuntimeEnvironment.getApplication()
        ShortcutRepository.save(context, Shortcut("turn-test", "Turn test", "Hi {who}", listOf(VariableDef("who", options = listOf("Sam")))))
        val intent = Intent(context, EditShortcutActivity::class.java).putExtra(EXTRA_ID, "turn-test")
        val saved = Robolectric.buildActivity(EditShortcutActivity::class.java, intent).setup()
        saved.recreate()
        assertOpen(saved.get())
        close(saved)
    }

    @Test
    fun editShortcutActivityOpensAShortcutWithHowLongParts() {
        val context = RuntimeEnvironment.getApplication()
        val shortcut = Shortcut(
            id = "relative-test",
            name = "Relative test",
            template = "Call in {Wait} or {How long} ago",
            variables = listOf(
                VariableDef("Wait", VariableType.TIME_AHEAD, listOf("10 mins"), show = RelativeShow.BOTH, fixed = true),
                VariableDef("How long", VariableType.TIME_AGO, listOf("5 mins", "1 hour"), lastValue = "1 hour"),
            ),
        )
        ShortcutRepository.save(context, shortcut)
        val intent = Intent(context, EditShortcutActivity::class.java).putExtra(EXTRA_ID, shortcut.id)
        val controller = Robolectric.buildActivity(EditShortcutActivity::class.java, intent).setup()
        assertOpen(controller.get())
        controller.recreate()
        assertOpen(controller.get())
        close(controller)
    }

    @Test
    fun editShortcutActivitySurvivesTurningThePhoneInTheMiddleOfAddingSomething() {
        val controller = Robolectric.buildActivity(EditShortcutActivity::class.java).setup()
        val activity = controller.get()
        fun edits(): List<EditText> {
            val out = ArrayList<EditText>()
            fun walk(v: View) { if (v is EditText) out += v; if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
            walk(controller.get().window.decorView)
            return out
        }
        fun tap(text: String) {
            fun find(v: View): View? {
                if (v is TextView && v !is EditText && v.text.toString() == text) return v
                if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
                return null
            }
            var v: View? = find(controller.get().window.decorView)
            while (v != null && !v.isClickable) v = v.parent as? View
            checkNotNull(v) { "no $text" }.performClick()
        }
        edits()[0].setText("Pay back")
        tap("Next")
        edits()[0].setText("Call in ")
        tap("Add something that changes")
        tap("A time")
        tap("From now")
        tap("Mins")
        edits().last().setText("10")
        controller.recreate()
        assertOpen(controller.get())
        // The chain is still open: the amount is where it was left.
        assertTrue(edits().last().text.toString() == "10")
        assertTrue(activity !== controller.get())
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

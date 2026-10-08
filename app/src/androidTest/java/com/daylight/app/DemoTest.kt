package com.daylight.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Intent
import android.widget.EditText
import android.widget.Switch
import android.view.View
import android.widget.TextView
import android.widget.ScrollView
import android.os.SystemClock

/** Drives the actual UI and live API. Also provides a repeatable screen-recording walkthrough. */
@RunWith(AndroidJUnit4::class)
class DemoTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: MainActivity
    private fun pause(ms: Long = 2000) = SystemClock.sleep(ms)
    private fun ui(work: () -> Unit) = instrumentation.runOnMainSync(work)
    private fun fill(id: Int, value: String) = ui { activity.findViewById<EditText>(id).setText(value) }
    private fun tap(id: Int) {
        ui { activity.findViewById<View>(id).requestRectangleOnScreen(android.graphics.Rect(0,0,200,100),true) }
        pause(500); ui { activity.findViewById<View>(id).performClick() }; pause(1200)
    }
    private fun waitFor(id: Int) {
        val deadline=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<deadline) {
            var ready=false; ui { ready=activity.findViewById<View>(id)!=null }
            if(ready) { pause(); return }; pause(200)
        }
        fail("Screen did not display view $id")
    }
    @Test fun completeWalkthrough() {
        val intent=Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity=instrumentation.startActivitySync(intent) as MainActivity
        val email="demo${System.currentTimeMillis()}@daylight.example"
        pause(3500)
        tap(MainActivity.AUTH_TOGGLE)
        fill(MainActivity.NAME,"Alex"); pause(900); fill(MainActivity.EMAIL,email); pause(900); fill(MainActivity.PASSWORD,"SmallSteps2026!"); pause(2500)
        tap(MainActivity.SUBMIT); waitFor(MainActivity.SETTINGS); pause(3500)
        tap(MainActivity.HABIT_FIRST); pause(3000)
        tap(MainActivity.SETTINGS); waitFor(MainActivity.SAVE)
        fill(MainActivity.NAME,"Alex Morgan"); fill(MainActivity.GOAL,"4"); pause(1800)
        tap(MainActivity.DARK); tap(MainActivity.NUDGES); pause(2000)
        tap(MainActivity.SAVE)
        val deadline=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<deadline) {
            var saved=false; ui { saved=activity.findViewById<TextView>(MainActivity.STATUS)?.text.toString().contains("Settings saved") }
            if(saved) break; pause(200)
        }
        pause(3500); tap(MainActivity.HOME); waitFor(MainActivity.SETTINGS); pause(3500)
        tap(MainActivity.SETTINGS); tap(MainActivity.LOGOUT); waitFor(MainActivity.SUBMIT); pause(2500)
        fill(MainActivity.EMAIL,email); fill(MainActivity.PASSWORD,"wrongpassword"); tap(MainActivity.SUBMIT); pause(2000)
        ui { assertTrue(activity.findViewById<TextView>(MainActivity.STATUS).text.toString().contains("incorrect")) }
        fill(MainActivity.PASSWORD,"SmallSteps2026!"); pause(2000); tap(MainActivity.SUBMIT); waitFor(MainActivity.SETTINGS); pause(3000)
        ui { assertTrue(activity.findViewById<TextView>(MainActivity.HABIT_FIRST).text.toString().startsWith("✓")) }
        tap(MainActivity.SETTINGS); waitFor(MainActivity.SAVE)
        ui {
            assertEquals("Alex Morgan",activity.findViewById<EditText>(MainActivity.NAME).text.toString())
            assertEquals("4",activity.findViewById<EditText>(MainActivity.GOAL).text.toString())
            assertTrue(activity.findViewById<Switch>(MainActivity.DARK).isChecked)
            assertFalse(activity.findViewById<Switch>(MainActivity.NUDGES).isChecked)
        }
        pause(3500); tap(MainActivity.HOME); pause(4000)
    }
}

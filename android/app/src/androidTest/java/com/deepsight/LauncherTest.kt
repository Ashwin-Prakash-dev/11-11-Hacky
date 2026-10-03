package com.deepsight

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opening the app from the phone's launcher must open the app itself. The debug screens (Gemma, Analyze) stay reachable
 * with `adb shell am start -n com.deepsight/.<Activity>`, but must not be launcher entries.
 */
@RunWith(AndroidJUnit4::class)
class LauncherTest {
    @Test
    fun onlyMainActivityIsALauncherEntry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val entries = context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
        assertEquals(listOf("com.deepsight.MainActivity"), entries)
    }
}

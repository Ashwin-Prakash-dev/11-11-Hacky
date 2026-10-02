package com.deepsight

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.capture.CaseStore
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.engine.pack.PackLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The app's case run (CaseRunner, real packs from the APK) on breast golden fields. Needs the BreakHis PNGs pushed as
 * `breast_<name>.png` into the app's external files dir (from ml/packs/breast_breakhis/golden/); otherwise skipped.
 * Expected values: golden/<name>.json from the Python reference. Results: adb logcat -s DeepSightBreast
 */
@RunWith(AndroidJUnit4::class)
class BreastCaseDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** name to (label, p_malignant) from golden/expected.json. */
    private val reference = linkedMapOf(
        "SOB_B_F-14-25197-400-062" to ("malignant" to 0.944285),
        "SOB_B_TA-14-16184-400-014" to ("benign" to 0.011977),
        "SOB_M_DC-14-12312-400-010" to ("malignant" to 0.998295),
    )

    @Test
    fun wholeFieldPackRunsLikeMalariaAndTriagesProvisionally() = runBlocking {
        val photos = reference.keys.associateWith { File(context.getExternalFilesDir(null), "breast_$it.png") }
        assumeTrue("breast fields not pushed", photos.values.all(File::isFile))
        assertEquals(true, DemoPacks.isReady("breast_breakhis"))
        val store = CaseStore(File(context.cacheDir, "breast-case-test").apply { deleteRecursively() })
        val runner = CaseRunner(PackLoader.fromAssets(context.assets))
        assertEquals(true, runner.packs().any { it.id == "breast_breakhis" })
        for ((name, photo) in photos) {
            photo.inputStream().use { store.import(name, it, "png") }
            val (fields, case) = runner.run("breast_breakhis", name, store.fields(name))
            val field = fields.single()
            val (label, pMalignant) = reference.getValue(name)
            Log.i(TAG, "$name: ${field.objects} score ${field.imageScore} counts ${field.counts} triage ${case.triage.level} ${case.triage.ruleId} timing ${field.timingMs}")
            assertEquals(label, field.objects.single().label)
            assertNull(field.objects.single().bbox)
            assertEquals(pMalignant, field.imageScore!!, 0.02)
            // malignant_seen flags a malignant prediction; anything else is NEEDS_EXPERT, never NORMAL_SCREEN
            val expected = if (label == "malignant") TriageLevel.ABNORMAL_FLAG to "malignant_seen" else TriageLevel.NEEDS_EXPERT to "engine.no_rule_matched"
            assertEquals(expected, case.triage.level to case.triage.ruleId)
        }
    }

    private companion object {
        const val TAG = "DeepSightBreast"
    }
}

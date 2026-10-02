package com.deepsight

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.capture.CaseStore
import com.deepsight.engine.pack.PackLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The app's case run on the six annotated reserved malaria fields (docs/datasets.md), against the Python reference
 * (`ml/eval/eval_annotated_fields.py`, pack model, `nlm` segmentation). Needs the photos pushed as
 * `annot_<id>.jpg` into the app's external files dir; otherwise skipped. Results: adb logcat -s DeepSightAnnot
 */
@RunWith(AndroidJUnit4::class)
class AnnotatedFieldsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "annotated-fields-test").apply { deleteRecursively() }

    /** id to (cells, parasitized) from the Python reference. */
    private val reference = linkedMapOf(
        "golden_positive" to (110 to 9), "golden_negative" to (214 to 0), "golden_sparse" to (81 to 10),
        "demo_positive" to (217 to 14), "demo_negative" to (212 to 3), "demo_sparse" to (94 to 10),
    )

    @Test
    fun countsMatchThePythonReference() = runBlocking {
        val photos = reference.keys.associateWith { File(context.getExternalFilesDir(null), "annot_$it.jpg") }
        assumeTrue("annotated fields not pushed", photos.values.all(File::isFile))
        val store = CaseStore(root)
        val runner = CaseRunner(PackLoader.fromAssets(context.assets))
        val failures = mutableListOf<String>()
        for ((id, photo) in photos) {
            photo.inputStream().use { store.import(id, it, "jpg") } // the app's import: bytes copied unchanged
            val (fields, case) = runner.run("malaria_thin", id, store.fields(id))
            val field = fields.single()
            val cells = field.objects.size
            val parasitized = field.counts["parasitized"] ?: 0
            val (pyCells, pyParasitized) = reference.getValue(id)
            Log.i(TAG, "$id: quality ${field.quality.pass} ${field.quality.reasons}, cells $cells (py $pyCells), " +
                "parasitized $parasitized (py $pyParasitized), triage ${case.triage.level} ${case.triage.ruleId}, timing ${field.timingMs}")
            if (abs(cells - pyCells) > 0.03 * pyCells || abs(parasitized - pyParasitized) > 2) {
                failures += "$id: cells $cells vs $pyCells, parasitized $parasitized vs $pyParasitized"
            }
        }
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }

    private companion object {
        const val TAG = "DeepSightAnnot"
    }
}

package com.deepsight.engine.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.PackLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real-field integration guard for detector -> crop -> classifier on both Android execution providers. */
@RunWith(AndroidJUnit4::class)
class LeukaemiaFieldDeviceTest {
    @Test
    fun detectsAndClassifiesCellsInBloodField() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = assets.open(FIELD).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }
            ?: error("$FIELD could not be decoded")

        val results = OnnxModel.Accelerator.entries.map { accelerator ->
            val pack = PackLoader.fromAssets(assets).load("leukaemia_wbc")
            FieldPipeline(pack, accelerator, CellFinders.forPack(pack)).use { pipeline ->
                pipeline.analyzeField("device-case", "field-01", bitmap).also { field ->
                    Log.i(
                        TAG,
                        "$accelerator quality=${field.quality} objects=${field.objects.size} counts=${field.counts} timing=${field.timingMs}",
                    )
                    assertTrue("$accelerator field should pass quality", field.quality.pass)
                    assertEquals("$accelerator regression counts", EXPECTED_COUNTS, field.counts)
                    assertEquals("$accelerator retained objects", 1, field.objects.size)
                    assertEquals("$accelerator retained class", "benign", field.objects.single().label)
                }
            }
        }

        assertEquals(results[0].counts, results[1].counts)
    }

    private companion object {
        const val FIELD = "packs/leukaemia_wbc/golden/field_rbcnet.jpg"
        const val TAG = "DeepSightLeukaemia"
        val EXPECTED_COUNTS = mapOf(
            "early_pre_b_like" to 0,
            "pre_b_like" to 0,
            "pro_b_like" to 0,
            "benign" to 1,
        )
    }
}

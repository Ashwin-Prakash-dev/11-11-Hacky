package com.deepsight.engine.pipeline

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.PackLoader
import com.deepsight.engine.router.RouterGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouterGuardDeviceTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun mismatchBlocksPackAndDrivesDeterministicTriage() {
        verifyBlocked(
            RouterResult(RouterVerdict.MISMATCH, 0.9, "fungal"),
            Contracts.RULE_ROUTER_MISMATCH,
        )
    }

    @Test
    fun rejectBlocksPackAndDrivesDeterministicTriage() {
        verifyBlocked(
            RouterResult(RouterVerdict.REJECT, 0.8),
            Contracts.RULE_ROUTER_REJECT,
        )
    }

    private fun verifyBlocked(routerResult: RouterResult, expectedRule: String) {
        val pack = PackLoader.fromAssets(assets, packsRoot = "").load("smoke")
        val router = RouterGuard { _, _ -> routerResult }
        FieldPipeline(pack, OnnxModel.Accelerator.CPU, routerGuard = router).use { pipeline ->
            val field = pipeline.analyzeField("case-1", "field-1", sharpBitmap())

            assertTrue(field.quality.pass)
            assertEquals(routerResult, field.router)
            assertTrue(field.objects.isEmpty())
            assertEquals(setOf("quality", "router", "total"), field.timingMs.keys)
            assertEquals(expectedRule, pipeline.closeCase("case-1", listOf(field)).triage.ruleId)
        }
    }

    private fun sharpBitmap(): Bitmap {
        val side = 8
        val pixels = IntArray(side * side) { index ->
            val gray = if ((index % side + index / side) % 2 == 0) 64 else 192
            (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
}

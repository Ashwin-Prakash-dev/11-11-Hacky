package com.deepsight.engine.golden

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.PackLoader
import com.deepsight.engine.pipeline.FieldPipeline
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A pack that isn't golden-tested on the device doesn't appear in the demo (docs/architecture.md).
 * Runs every `<pack>/golden/<name>.png` through the real pipeline and compares with `<name>.json` (a contract
 * field_result from the Python reference) within `golden/tolerance.json`. One row per case; a pack with no
 * valid case, a stub model or a missing CellFinder fails its row. Logs `DeepSightGolden` lines for adb logcat.
 */
@RunWith(Parameterized::class)
class PackGoldenTest(private val packPath: String, private val case: String?) {
    private val packId = packPath.substringAfterLast('/')
    private val packsRoot = packPath.substringBeforeLast('/', "")
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun golden() {
        val result = runCatching { check() }
        val message = result.exceptionOrNull()?.let { it.message ?: it.toString() }
        Log.i(TAG, "$packPath/${case ?: "(no golden)"} ${if (message == null) "PASS" else "FAIL $message"}")
        if (message != null) fail(message)
    }

    private fun check() {
        val name = case ?: throw AssertionError("pack '$packPath' has no golden/<name>.png + <name>.json case")
        val dir = "$packPath/golden"
        val expected = Contracts.parseFieldResult(assets.readText("$dir/$name.json"))
        val tolerance = if ("tolerance.json" in assets.list(dir).orEmpty()) {
            Contracts.json.decodeFromString(GoldenTolerance.serializer(), assets.readText("$dir/tolerance.json"))
        } else {
            GoldenTolerance()
        }
        val bitmap = assets.open("$dir/$name.png").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }) }
            ?: throw AssertionError("$dir/$name.png could not be decoded")

        val pack = PackLoader.fromAssets(assets, packsRoot = packsRoot).load(packId)
        val actual = FieldPipeline(pack, OnnxModel.Accelerator.CPU).use { it.analyzeField(expected.caseId, expected.fieldId, bitmap) }
        val diffs = GoldenComparator.compare(expected, actual, tolerance, bitmap.width, bitmap.height)
        if (diffs.isNotEmpty()) throw AssertionError(diffs.joinToString("; "))
    }

    companion object {
        private const val TAG = "DeepSightGolden"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}/{1}")
        fun cases(): List<Array<Any?>> {
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            // smoke/ is a test-only pack at the asset root; real packs are staged from ml/packs under packs/.
            val packs = (assets.list("").orEmpty().filter { "manifest.json" in assets.list(it).orEmpty() } +
                assets.list("packs").orEmpty().map { "packs/$it" }).sorted()
            return packs.flatMap { pack ->
                val files = assets.list("$pack/golden").orEmpty().toSet()
                val names = files.filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }.filter { "$it.json" in files }.sorted()
                if (names.isEmpty()) listOf(arrayOf<Any?>(pack, null)) else names.map { arrayOf<Any?>(pack, it) }
            }
        }

        private fun AssetManager.readText(path: String) = open(path).use { it.reader().readText() }
    }
}

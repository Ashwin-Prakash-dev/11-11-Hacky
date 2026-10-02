package com.deepsight

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.PackLoader
import com.deepsight.report.gemma.GemmaRunner
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Spike S3: Gemma 4 E2B on LiteRT-LM inside our app process (com.deepsight), on the phone.
 * Needs the model in the app's external files dir (adb push, see STATUS.md); otherwise skipped.
 * Results: adb logcat -s DeepSightGemma
 */
@RunWith(AndroidJUnit4::class)
class GemmaOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val model = File(context.getExternalFilesDir(null), MODEL_FILE)

    @Before
    fun modelInstalled() = assumeTrue("$MODEL_FILE is not in ${model.parent}", model.isFile)

    @Test
    fun streamsOnGpu() = streams(GemmaRunner.Backend.GPU, speculativeDecoding = false)

    @Test
    fun streamsOnGpuWithMultiTokenPrediction() = streams(GemmaRunner.Backend.GPU, speculativeDecoding = true)

    @Test
    fun streamsOnCpu() = streams(GemmaRunner.Backend.CPU, speculativeDecoding = false)

    /** Plan section 12 memory risk: Gemma and an ONNX pack loaded in the same process at the same time. */
    @Test
    fun memoryWithGemmaAndOnnxPackLoaded() {
        val pack = PackLoader.fromAssets(context.assets).load("malaria_thin")
        OnnxModel(pack.modelBytes).use { onnx ->
            val shape = requireNotNull(pack.manifest.input.shape)
            val perCell = shape.drop(1).reduce(Int::times)
            onnx.run(FloatArray(256 * perCell), longArrayOf(256L) + shape.drop(1).map(Int::toLong))   // a full batch, like one field
            log("meminfo, ONNX pack only:\n${shell("dumpsys meminfo ${context.packageName}")}")
            GemmaRunner(model.path, GemmaRunner.Backend.GPU, cacheDir = context.cacheDir.path, speculativeDecoding = false).use { gemma ->
                gemma.load()
                val result = gemma.generate(PROMPT, MAX_OUTPUT_TOKENS) {}
                assertTrue(result.text.isNotBlank())
                onnx.run(FloatArray(256 * perCell), longArrayOf(256L) + shape.drop(1).map(Int::toLong))  // both usable together
                log("meminfo, Gemma (GPU) + ONNX pack:\n${shell("dumpsys meminfo ${context.packageName}")}")
                log("MemAvailable with both loaded: ${shell("cat /proc/meminfo").lines().first { it.startsWith("MemAvailable") }}")
            }
        }
    }

    private fun streams(backend: GemmaRunner.Backend, speculativeDecoding: Boolean) {
        val label = "$backend${if (speculativeDecoding) "+MTP" else ""}"
        GemmaRunner(model.path, backend, cacheDir = context.cacheDir.path, speculativeDecoding = speculativeDecoding).use { gemma ->
            val loadMs = gemma.load()
            val chunks = mutableListOf<String>()
            val result = gemma.generate(PROMPT, MAX_OUTPUT_TOKENS) { chunks += it }
            log("$label: load $loadMs ms, first text ${result.firstTextMs} ms, total ${result.totalMs} ms, ${chunks.size} chunks, " +
                "stats ${result.stats}")
            log("$label text: ${result.text.replace('\n', ' ')}")
            assertTrue("$label produced no text", result.text.isNotBlank())
            assertTrue("$label did not stream (${chunks.size} chunk)", chunks.size > 1)
        }
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).let { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.reader().readText() }
        }

    private fun log(message: String) = message.chunked(3500).forEach { Log.i(TAG, it) }   // logcat truncates long lines

    private companion object {
        const val TAG = "DeepSightGemma"
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MAX_OUTPUT_TOKENS = 160

        /** A report-shaped prompt from contracts/examples/case_result.malaria_thin.json; S3 only measures streaming. */
        val PROMPT = """
            You write a short screening note for a clinician. Use only the facts below. The triage level is decided;
            repeat it exactly and do not change it. Do not diagnose. Three sentences.
            Test: malaria thin smear. Fields passed: 1 of 2. Counts: parasitized 1, uninfected 2.
            Triage: ABNORMAL_FLAG (rule parasite_seen, provisional thresholds).
        """.trimIndent()
    }
}

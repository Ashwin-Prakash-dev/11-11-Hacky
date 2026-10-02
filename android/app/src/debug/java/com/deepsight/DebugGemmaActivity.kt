package com.deepsight

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.PackLoader
import com.deepsight.report.gemma.GemmaRunner
import com.deepsight.ui.theme.DeepSightTheme
import java.io.File
import java.util.concurrent.Executors

/**
 * Debug builds only, spike S3: Gemma (LiteRT-LM) streaming in our own app, optionally with the malaria ONNX pack
 * loaded alongside so `adb shell dumpsys meminfo com.deepsight` sees both. Everything stays loaded until the screen
 * closes. Gemma only narrates; it never decides triage.
 *
 * adb: push the model to /sdcard/Android/data/com.deepsight/files/gemma-4-E2B-it.litertlm, then
 * adb shell am start -n com.deepsight/.DebugGemmaActivity --es backend gpu --ez mtp false --ez onnx true --ez autorun true
 * and read adb logcat -s DeepSightGemma
 */
class DebugGemmaActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()          // GemmaRunner and OnnxModel are not thread-safe
    private var gemma: GemmaRunner? = null
    private var onnx: OnnxModel? = null

    private var backend by mutableStateOf(GemmaRunner.Backend.GPU)
    private var mtp by mutableStateOf(false)
    private var withOnnx by mutableStateOf(true)
    private var prompt by mutableStateOf(DEFAULT_PROMPT)
    private var busy by mutableStateOf(false)
    private var status by mutableStateOf("")
    private var output by mutableStateOf("")
    private var stats by mutableStateOf(emptyList<String>())

    private val model by lazy { File(getExternalFilesDir(null), MODEL_FILE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent.getStringExtra("backend")?.let { backend = if (it.equals("cpu", ignoreCase = true)) GemmaRunner.Backend.CPU else GemmaRunner.Backend.GPU }
        mtp = intent.getBooleanExtra("mtp", mtp)
        withOnnx = intent.getBooleanExtra("onnx", withOnnx)
        status = if (model.isFile) "Model: ${model.path} (%.2f GB)".format(model.length() / 1e9) else "Missing: push $MODEL_FILE to ${model.parent}"
        if (intent.getBooleanExtra("autorun", false) && model.isFile) run()
        setContent {
            DeepSightTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Gemma (debug)", style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GemmaRunner.Backend.entries.forEach { b ->
                                FilterChip(selected = backend == b, onClick = { backend = b }, label = { Text(b.name) }, enabled = gemma == null)
                            }
                            FilterChip(selected = mtp, onClick = { mtp = !mtp }, label = { Text("MTP") }, enabled = gemma == null)
                            FilterChip(selected = withOnnx, onClick = { withOnnx = !withOnnx }, label = { Text("+ ONNX pack") }, enabled = onnx == null)
                        }
                        OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("Prompt") }, enabled = !busy)
                        Button(onClick = ::run, enabled = !busy && model.isFile, modifier = Modifier.fillMaxWidth()) {
                            Text(if (gemma == null) "Load and generate" else "Generate again")
                        }
                        Text(status)
                        Text(output)
                        stats.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    private fun run() {
        busy = true
        output = ""
        stats = emptyList()
        val chosenBackend = backend
        val chosenMtp = mtp
        val chosenOnnx = withOnnx
        val text = prompt
        worker.execute {
            val lines = mutableListOf<String>()
            val result = runCatching {
                if (chosenOnnx && onnx == null) {
                    ui("Loading the malaria ONNX pack...")
                    val pack = PackLoader.fromAssets(assets).load("malaria_thin")
                    val shape = requireNotNull(pack.manifest.input.shape)
                    val perCell = shape.drop(1).reduce(Int::times)
                    onnx = OnnxModel(pack.modelBytes).also {
                        it.run(FloatArray(256 * perCell), longArrayOf(256L) + shape.drop(1).map(Int::toLong))   // one field's batch
                    }
                    lines += "ONNX pack: malaria_thin loaded, 256-cell batch run"
                }
                if (gemma == null) {
                    ui("Loading Gemma on $chosenBackend${if (chosenMtp) " with MTP" else ""}...")
                    val runner = GemmaRunner(model.path, chosenBackend, cacheDir = cacheDir.path, speculativeDecoding = chosenMtp)
                    lines += "Gemma $chosenBackend${if (chosenMtp) "+MTP" else ""}: load ${runner.load()} ms"
                    gemma = runner
                }
                ui("Generating...")
                gemma!!.generate(text, MAX_OUTPUT_TOKENS) { piece -> runOnUiThread { output += piece } }
            }
            result.onSuccess { r ->
                lines += "first text ${r.firstTextMs} ms, total ${r.totalMs} ms, ${r.chunks} chunks"
                r.stats?.let {
                    lines += "LiteRT-LM: init %.2f s, first token %.2f s, prefill %d tok at %.1f tok/s, decode %d tok at %.1f tok/s".format(
                        it.initSeconds, it.timeToFirstTokenSeconds, it.prefillTokens, it.prefillTokensPerSecond, it.decodeTokens, it.decodeTokensPerSecond,
                    )
                }
                lines.forEach { Log.i(TAG, it) }
                Log.i(TAG, "text: ${r.text.replace('\n', ' ')}")
            }.onFailure { Log.e(TAG, "Gemma failed", it) }
            runOnUiThread {
                stats = lines
                status = result.exceptionOrNull()?.let { "Failed: ${it.message}" } ?: "Done. Models stay loaded until you leave this screen."
                busy = false
            }
        }
    }

    private fun ui(message: String) = runOnUiThread { status = message }

    override fun onDestroy() {
        worker.execute {
            gemma?.close()
            onnx?.close()
        }
        worker.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DeepSightGemma"
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MAX_OUTPUT_TOKENS = 160
        val DEFAULT_PROMPT = """
            You write a short screening note for a clinician. Use only the facts below. The triage level is decided;
            repeat it exactly and do not change it. Do not diagnose. Three sentences.
            Test: malaria thin smear. Fields passed: 1 of 2. Counts: parasitized 1, uninfected 2.
            Triage: ABNORMAL_FLAG (rule parasite_seen, provisional thresholds).
        """.trimIndent()
    }
}

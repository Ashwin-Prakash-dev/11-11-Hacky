package com.deepsight

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.pack.PackLoader
import com.deepsight.engine.pipeline.CellFinders
import com.deepsight.engine.pipeline.FieldPipeline
import com.deepsight.ui.theme.DeepSightTheme
import java.io.File
import java.util.concurrent.Executors

/**
 * Debug builds only: pick one field photo, run the real engine (FieldPipeline with the NLM red-cell finder, then
 * triage) and show every cell's box. Not part of the case flow (#30). Launch from the "DeepSight debug" icon or
 * adb shell am start -n com.deepsight/.DebugAnalyzeActivity
 */
class DebugAnalyzeActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()          // FieldPipeline is not thread-safe
    private var pipeline: FieldPipeline? = null
    private var manifest: PackManifest? = null

    private var status by mutableStateOf("Loading $PACK_ID...")
    private var busy by mutableStateOf(true)
    private var overlay by mutableStateOf<ImageBitmap?>(null)
    private var lines by mutableStateOf(emptyList<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        worker.execute {
            val loaded = runCatching {
                PackLoader.fromAssets(assets).load(PACK_ID).let { it.manifest to FieldPipeline(it, cellFinder = CellFinders.forPack(it)) }
            }
            runOnUiThread {
                loaded.onSuccess { (m, p) ->
                    manifest = m
                    pipeline = p
                    status = "Pick a thin-smear field photo"
                    busy = false
                    // adb: push a photo to /sdcard/Android/data/com.deepsight/files/ and pass --es path <that file>
                    intent.getStringExtra("path")?.let { path -> analyze { File(path) } }
                }.onFailure { status = "$PACK_ID is not installed in this build: ${it.message}" }
            }
        }
        setContent {
            DeepSightTheme {
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) analyze { copy(uri) }
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Malaria field (debug)", style = MaterialTheme.typography.headlineSmall)
                        Button(
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Pick field photo") }
                        Text(status)
                        overlay?.let { Image(it, "Field with cell boxes", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                        lines.forEach { Text(it) }
                        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    /** Copies the picked photo byte for byte, keeping its EXIF orientation. */
    private fun copy(uri: Uri): File = File(cacheDir, "debug_field").also { file ->
        contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    private fun analyze(source: () -> File) {
        busy = true
        status = "Analyzing..."
        overlay = null
        lines = emptyList()
        worker.execute {
            val result = runCatching {
                val bitmap = decode(source())
                val field = pipeline!!.analyzeField("debug-case", "field-01", bitmap)
                Triple(field, draw(bitmap, field), describe(field, bitmap.width, bitmap.height)).also { bitmap.recycle() }
            }
            result.onSuccess { (_, _, text) -> text.forEach { Log.i(TAG, it) } }.onFailure { Log.e(TAG, "analysis failed", it) }
            runOnUiThread {
                result.onSuccess { (_, image, text) ->
                    overlay = image
                    lines = text
                    status = "Done"
                }.onFailure { status = "Failed: ${it.message}" }
                busy = false
            }
        }
    }

    /** Android's decoder, then the photo's EXIF rotation, as cv2.imread does in the Python reference. */
    private fun decode(file: File): Bitmap {
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: error("Could not decode the photo")
        val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return raw
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, false).also { raw.recycle() }
    }

    private fun describe(field: FieldResult, width: Int, height: Int): List<String> {
        val m = manifest!!
        val q = field.quality
        val text = mutableListOf(
            "Pack: ${m.id} ${m.version}, $width x $height px",
            "Quality: ${if (q.pass) "pass" else "REJECTED ${q.reasons}"} (blur %.1f, clipped %.2f)".format(q.blurScore, q.exposureScore),
        )
        if (q.pass) {
            val cells = field.objects.size
            val parasitized = field.counts["parasitized"] ?: 0
            val high = field.objects.count { it.label == "parasitized" && it.score > 0.8 }
            text += if (cells == 0) "No cells found (or NLM's segmentation asked for a retake)"
            else "Cells: $cells, parasitized $parasitized (%.1f%%), above 0.8: $high".format(100.0 * parasitized / cells)
            text += "Image score: ${field.imageScore?.let { "%.3f".format(it) } ?: "none"}, uncertain: " +
                (field.uncertainty?.let { if (it.flag) "yes (${it.reason})" else "no" } ?: "n/a")
        }
        val case = pipeline!!.closeCase("debug-case", listOf(field))
        text += "Triage: ${case.triage.level} (rule ${case.triage.ruleId})" + if (case.triage.provisional) ", provisional thresholds" else ""
        text += "Time: ${field.timingMs.entries.joinToString { "${it.key} ${it.value} ms" }}"
        return text
    }

    /** The field at 1/4 size with one box per cell: green p <= 0.5, orange 0.5-0.8, red > 0.8. */
    private fun draw(field: Bitmap, result: FieldResult): ImageBitmap {
        val preview = Bitmap.createScaledBitmap(field, field.width / 4, field.height / 4, true).copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(preview)
        val paint = Paint().apply { style = Paint.Style.STROKE }
        result.objects.forEach { o ->
            val (x, y, w, h) = o.bbox ?: return@forEach
            val p = if (o.label == "parasitized") o.score else 1.0 - o.score
            paint.color = when {
                p > 0.8 -> Color.RED
                p > 0.5 -> Color.rgb(255, 165, 0)
                else -> Color.rgb(0, 200, 0)
            }
            paint.strokeWidth = if (p > 0.5) 3f else 1.5f
            val pw = preview.width
            val ph = preview.height
            canvas.drawRect((x * pw).toFloat(), (y * ph).toFloat(), ((x + w) * pw).toFloat(), ((y + h) * ph).toFloat(), paint)
        }
        return preview.asImageBitmap()
    }

    override fun onDestroy() {
        worker.execute { pipeline?.close() }
        worker.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val PACK_ID = "malaria_thin"
        const val TAG = "DeepSightDebug"   // results also go to logcat for adb-driven runs
    }
}

package com.deepsight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import com.deepsight.capture.FieldImage
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.pack.PackLoader
import com.deepsight.engine.pipeline.CellFinders
import com.deepsight.engine.pipeline.FieldPipeline
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Field ids are unique across cases: Room keys fields by field_id alone. The index is the file's, so Delete leaves gaps. */
fun fieldId(caseId: String, index: Int) = "${caseId}_field_$index"

/** One analysed case: every field's result and the triaged case result. */
data class CaseRun(val fields: List<FieldResult>, val case: CaseResult)

/**
 * Runs a case through the real engine off the main thread. Keeps the last pack's [FieldPipeline], so its model loads
 * once across cases; picking another pack frees it first, so only one model is in memory.
 */
class CaseRunner(private val loader: PackLoader) {
    private val lock = Mutex() // FieldPipeline is not thread-safe
    private var catalog: List<PackManifest>? = null
    private var current: Pair<String, FieldPipeline>? = null

    /** How many times a pack was loaded; for tests. */
    var loads = 0
        private set

    /** Installed packs for the picker. The first call reads and hashes every model; later calls reuse that. */
    suspend fun packs(): List<PackManifest> = lock.withLock {
        catalog ?: withContext(Dispatchers.IO) {
            val found = loader.discover()
            found.rejected.forEach { Log.w(TAG, "pack ${it.id} not offered: ${it.reason}") }
            found.installed
        }.also { catalog = it }
    }

    suspend fun run(packId: String, caseId: String, images: List<FieldImage>): CaseRun = lock.withLock {
        withContext(Dispatchers.Default) {
            val pipeline = pipeline(packId)
            val fields = images.map { image ->
                val bitmap = decode(image.file)
                try {
                    pipeline.analyzeField(caseId, fieldId(caseId, image.index), bitmap)
                } catch (e: OutOfMemoryError) {
                    error("${image.file.name} is too large to analyse (${bitmap.width} x ${bitmap.height})")
                } finally {
                    bitmap.recycle()
                }
            }
            CaseRun(fields, pipeline.closeCase(caseId, fields))
        }
    }

    private fun pipeline(packId: String): FieldPipeline {
        current?.let { (id, pipeline) ->
            if (id == packId) return pipeline
            current = null
            // A model that never loaded throws again on close (FieldPipeline's lazy model); it must not block the next pack.
            runCatching { pipeline.close() }.onFailure { Log.w(TAG, "closing pack $id failed", it) }
        }
        val pack = loader.load(packId).also { loads++ }
        return FieldPipeline(pack, cellFinder = CellFinders.forPack(pack.manifest)).also { current = packId to it }
    }

    companion object {
        private const val TAG = "DeepSight"

        @Volatile private var instance: CaseRunner? = null

        /** ponytail: one runner per process and its pipeline is never closed; it lives until the process dies. */
        fun get(context: Context): CaseRunner = instance ?: synchronized(this) {
            instance ?: CaseRunner(PackLoader.fromAssets(context.applicationContext.assets)).also { instance = it }
        }

        /** Full resolution, then the EXIF rotation, as cv2.imread does in the Python reference (DebugAnalyzeActivity too). */
        private fun decode(file: File): Bitmap {
            val raw = try {
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            } catch (e: OutOfMemoryError) {
                null
            } ?: error("Could not decode ${file.name}")
            val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return raw
            }
            return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, false)
                .also { raw.recycle() }
        }
    }
}

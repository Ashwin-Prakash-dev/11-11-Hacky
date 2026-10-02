package com.deepsight.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.deepsight.engine.contract.DetectedObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes [file] to about [maxSide] px on its long side, then applies its EXIF rotation, as CaseRunner does for analysis. */
fun decodeDisplayBitmap(file: File, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return raw
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true).also { if (it !== raw) raw.recycle() }
}

/**
 * The field photo with the model's cell boxes: [positiveLabel] cells in the alert colour, the others faint.
 * Boxes are the contract's normalised [x, y, w, h] on the EXIF-rotated field (contracts/result.schema.json).
 */
@Composable
fun FieldImage(file: File, objects: List<DetectedObject>, positiveLabel: String?, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, file) { value = withContext(Dispatchers.IO) { decodeDisplayBitmap(file, maxSide = 1280) } }
    val strongAlert = Color(0xFFD32F2F)
    val other = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: (4f / 3f)
    Box(
        modifier.fillMaxWidth().aspectRatio(ratio).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator(Modifier.size(28.dp))
            return@Box
        }
        Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            objects.forEach { o ->
                val b = o.bbox ?: return@forEach
                val flagged = o.label == positiveLabel
                drawRect(
                    color = if (flagged) strongAlert else other,
                    topLeft = Offset(b[0].toFloat() * size.width, b[1].toFloat() * size.height),
                    size = Size(b[2].toFloat() * size.width, b[3].toFloat() * size.height),
                    style = Stroke(width = if (flagged) 2.5.dp.toPx() else 1.dp.toPx()),
                )
            }
        }
    }
}

/** Legend under a [FieldImage]. */
@Composable
fun FieldImageLegend(positiveLabel: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(Color(0xFFD32F2F), positiveLabel)
        LegendItem(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), "other cells")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.size(12.dp).background(color, MaterialTheme.shapes.extraSmall)) {}
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

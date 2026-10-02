package com.deepsight.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.deepsight.CaseUiState
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.components.EmptyState
import com.deepsight.ui.components.NoticeRow
import com.deepsight.ui.components.SectionHeader
import com.deepsight.ui.components.decodeDisplayBitmap
import com.deepsight.ui.theme.Mono
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Step 1 of a case: import or capture fields, then analyse. Fields are the files in the case directory. */
@Composable
fun CaseScreen(
    state: CaseUiState,
    onImport: (Uri) -> Unit,
    captureFile: () -> File?,
    onCaptured: () -> Unit,
    onDelete: (FieldImage) -> Unit,
    onAnalyse: () -> Unit,
    modifier: Modifier = Modifier,
    onResume: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onResume() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var camera by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onImport(uri) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera = it }

    if (camera) {
        CameraView(newFile = captureFile, onSaved = { onCaptured(); camera = false }, onClose = { camera = false }, modifier = modifier)
        return
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(state.pack.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(state.caseId, style = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PatientSummary(state)
            Steps(current = if (state.running) 2 else 1)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !state.running,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(DeepSightIcons.Gallery, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Import image")
                }
                FilledTonalButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (granted) camera = true else permission.launch(Manifest.permission.CAMERA)
                    },
                    enabled = !state.running,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(DeepSightIcons.Camera, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Capture")
                }
            }
            SectionHeader(
                "Fields",
                supporting = if (state.images.isEmpty()) "Each photo is checked for focus and exposure before the model runs."
                else "${state.images.size} added. More fields give a more reliable screen.",
            )
            if (state.images.isEmpty()) {
                EmptyState(DeepSightIcons.Gallery, "No fields yet", "Import a photo of a stained smear, or capture one through the microscope eyepiece.")
            } else {
                state.images.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { FieldTile(it, enabled = !state.running, onDelete = { onDelete(it) }, modifier = Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            state.error?.let { NoticeRow(it, DeepSightIcons.Warning, color = MaterialTheme.colorScheme.error) }
        }
        AnalyseBar(state, onAnalyse)
    }
}

@Composable
private fun PatientSummary(state: CaseUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(DeepSightIcons.Person, contentDescription = null, Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.patient.fullName, style = MaterialTheme.typography.titleMedium)
                Text(state.patient.uid, style = Mono)
                Text("DOB ${state.patient.dateOfBirth} · ${state.patient.bloodGroup}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Steps(current: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Add fields", "Analysis", "Review").forEachIndexed { i, label ->
            val step = i + 1
            val active = step <= current
            Surface(
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape,
            ) { Text("$step", style = MaterialTheme.typography.labelLarge, modifier = Modifier.size(24.dp).padding(top = 3.dp), textAlign = TextAlign.Center) }
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (step == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (step < 3) Box(Modifier.width(12.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
private fun FieldTile(image: FieldImage, enabled: Boolean, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, image.file) { value = withContext(Dispatchers.IO) { decodeDisplayBitmap(image.file, maxSide = 480) } }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Field ${image.index}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Field ${image.index}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete, enabled = enabled) { Icon(DeepSightIcons.Delete, contentDescription = "Delete field ${image.index}") }
        }
    }
}

@Composable
private fun AnalyseBar(state: CaseUiState, onAnalyse: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val progress = state.progress
            if (state.running && progress != null) {
                val (done, total) = progress
                LinearProgressIndicator(progress = { if (total == 0) 0f else (done - 0.5f).coerceAtLeast(0f) / total }, modifier = Modifier.fillMaxWidth())
                Text(if (done == 0) "Loading the model…" else "Analysing field $done of $total…", style = MaterialTheme.typography.bodyMedium)
            } else if (state.images.isEmpty()) {
                Text("Add at least one field to analyse.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAnalyse, enabled = state.images.isNotEmpty() && !state.running, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (state.running) "Analysing…" else "Analyse")
            }
        }
    }
}

@Composable
private fun CameraView(newFile: () -> File?, onSaved: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val activity = LocalContext.current as ComponentActivity
    val imageCapture = remember { ImageCapture.Builder().build() }
    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val preview = Preview.Builder().build().apply { surfaceProvider = view.surfaceProvider }
                        future.get().apply {
                            unbindAll()
                            bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onClose) { Text("Cancel") }
            Button(onClick = {
                val file = newFile() ?: return@Button
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(activity),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved()
                        override fun onError(e: ImageCaptureException) {
                            Toast.makeText(activity, "Capture failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }, modifier = Modifier.height(56.dp)) {
                Icon(DeepSightIcons.Camera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Take photo")
            }
        }
    }
}

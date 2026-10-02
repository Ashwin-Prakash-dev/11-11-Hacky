package com.deepsight.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

/** Import-or-capture screen for one case. Fields are the files in the case directory. */
@Composable
fun CaptureScreen(store: CaseStore, caseId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var fields by remember { mutableStateOf(store.fields(caseId)) }
    var camera by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            val input = context.contentResolver.openInputStream(uri)
            if (input != null) store.import(caseId, input, ext)
            else Toast.makeText(context, "Could not read that image", Toast.LENGTH_LONG).show()
            fields = store.fields(caseId)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera = it }

    if (camera) {
        CameraView(
            newFile = { store.nextFile(caseId, "jpg") },
            onSaved = { fields = store.fields(caseId); camera = false },
            onClose = { camera = false },
            modifier = modifier,
        )
        return
    }

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Import image") }
            Button(onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) camera = true else permission.launch(Manifest.permission.CAMERA)
            }) { Text("Capture") }
        }
        Text("Fields: ${fields.size}")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(fields, key = { it.file.name }) { f ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Thumbnail(f.file)
                    Text("Field ${f.index} (${f.file.extension})", Modifier.weight(1f))
                    Button(onClick = { f.file.delete(); fields = store.fields(caseId) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(file: File) {
    val bitmap = remember(file) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 128) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp))
    else Box(Modifier.size(64.dp))
}

@Composable
private fun CameraView(newFile: () -> File, onSaved: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
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
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = onClose) { Text("Cancel") }
            Button(onClick = {
                val file = newFile()
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
            }) { Text("Take photo") }
        }
    }
}

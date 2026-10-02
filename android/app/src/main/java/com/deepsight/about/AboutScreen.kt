package com.deepsight.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.theme.Mono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SOURCE_URL = "https://github.com/Ashwin-Prakash-dev/11-11-Hacky"
const val GPL_ASSET = "GPL-3.0.txt"
const val NLM_NOTICE_ASSET = "packs/malaria_thin/NOTICE_NLM.txt"

/**
 * What the app is, privacy, and the GPLv3 "Appropriate Legal Notices" (§0 and §5d): the copyright notice, that there is
 * no warranty, that the work may be conveyed under the GPL, and how to read the licence. Credits NLM as its notice asks.
 */
@Composable
fun AboutScreen(onOpenDocument: (title: String, asset: String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.medium) {
                Icon(DeepSightIcons.Science, contentDescription = null, Modifier.padding(12.dp).size(32.dp))
            }
            Column {
                Text("DeepSight", style = MaterialTheme.typography.headlineSmall)
                Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        InfoCard("About this app") {
            Body(
                "DeepSight is screening support for microscopy. It checks each field's image quality, counts and classifies cells " +
                    "with an on-device model, applies the fixed triage rules of the test pack, and drafts a short report. " +
                    "A clinician always signs off. It is not a diagnostic device, and its thresholds are provisional.",
            )
        }
        InfoCard("Privacy") {
            Body("Everything runs on this phone. The app has no internet permission, so images, results and reports never leave the device.")
        }
        InfoCard("Licence") {
            Body("DeepSight. Copyright © 2026 the DeepSight authors.")
            Body(
                "This program is free software: you can redistribute it and/or modify it under the terms of the GNU General " +
                    "Public License, version 3, as published by the Free Software Foundation.",
            )
            Body(
                "This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the " +
                    "implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.",
            )
            Body("Source code: $SOURCE_URL")
            OutlinedButton(onClick = { onOpenDocument("GNU GPL v3", GPL_ASSET) }) { Text("Read the GNU GPL v3") }
        }
        InfoCard("Credits") {
            Body("Malaria cell detection and the thin-smear model: courtesy of the U.S. National Library of Medicine (NLM Malaria Screener).")
            OutlinedButton(onClick = { onOpenDocument("NLM notice", NLM_NOTICE_ASSET) }) { Text("Read the NLM notice") }
            Body("Report model: Gemma 4 E2B (Apache-2.0), run on the phone by Google LiteRT-LM (Apache-2.0).")
            Body("Built with ONNX Runtime (MIT), OpenCV (Apache-2.0), CameraX, Room and Jetpack Compose (Apache-2.0), and Material icons (Apache-2.0).")
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) = ElevatedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

/** A text file from the APK's assets (the licence, a notice), selectable. */
@Composable
fun DocumentScreen(asset: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val text by produceState<String?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }.getOrElse { "Could not open $asset: ${it.message}" }
        }
    }
    val loaded = text
    if (loaded == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    SelectionContainer(modifier.fillMaxSize()) {
        Text(loaded, style = Mono, modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp))
    }
}

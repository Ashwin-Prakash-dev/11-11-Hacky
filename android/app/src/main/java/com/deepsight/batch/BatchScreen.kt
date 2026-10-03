package com.deepsight.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepsight.data.CaseStatus
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.ThemePreviews
import com.deepsight.ui.components.EmptyState
import com.deepsight.ui.components.PillTone
import com.deepsight.ui.components.SectionHeader
import com.deepsight.ui.components.StatusPill
import com.deepsight.ui.theme.DeepSightTheme

/**
 * Batch-tab submissions only, in the order the shared queue runs them. Bulk image selection isn't specified yet, so
 * its controls are disabled. A finished batch opens for sign-off ([onOpen]).
 */
@Composable
fun BatchScreen(
    batches: List<BatchItem>,
    onOpen: (String) -> Unit,
    onUseSingle: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectImages: () -> Unit = {},
    draftImages: Int = 0,
    onContinue: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.medium) {
                        Icon(DeepSightIcons.Batch, contentDescription = null, Modifier.padding(10.dp).size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    StatusPill("Router: placeholder", tone = PillTone.CAUTION)
                }
                Text("Screen many fields at once", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Add many field images at once. A router suggests a test module for each, you check and change them, then submit them together.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onSelectImages) {
                    Icon(DeepSightIcons.Gallery, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select images")
                }
            }
        }
        if (draftImages > 0) {
            OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continue allocating ${if (draftImages == 1) "1 image" else "$draftImages images"}")
            }
        }
        SectionHeader("Batches", supporting = if (batches.isEmpty()) null else "Run one at a time, in the order they were submitted.")
        if (batches.isEmpty()) EmptyState(DeepSightIcons.Batch, title = "No batches yet", body = "Batches you run will be listed here.")
        batches.forEach { BatchRow(it, onOpen) }
        TextButton(onClick = onUseSingle, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Screen one case in Single") }
    }
}

@Composable
private fun BatchRow(item: BatchItem, onOpen: (String) -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.patient ?: "No patient", style = MaterialTheme.typography.titleMedium)
            Text(item.packName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                item.statusLine(),
                style = MaterialTheme.typography.labelLarge,
                color = if (item.status == CaseStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            item.progress?.let { (done, total) ->
                if (total > 0) LinearProgressIndicator(progress = { (done - 0.5f).coerceAtLeast(0f) / total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
    if (item.status == CaseStatus.DONE) Card(onClick = { onOpen(item.caseId) }, colors = colors, modifier = Modifier.fillMaxWidth()) { content() }
    else Card(colors = colors, modifier = Modifier.fillMaxWidth()) { content() }
}

@ThemePreviews
@Composable
private fun BatchScreenPreview() = DeepSightTheme {
    BatchScreen(
        listOf(
            BatchItem("c1", "Ada Example · P-0000-0001", "Malaria (thin smear)", CaseStatus.RUNNING, 2 to 4, null, null),
            BatchItem("c2", "Ben Sample · P-0000-0002", "Malaria (thin smear)", CaseStatus.QUEUED, null, 1, null),
        ),
        onOpen = {}, onUseSingle = {},
    )
}

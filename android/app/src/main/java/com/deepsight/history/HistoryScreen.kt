package com.deepsight.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepsight.HistoryItem
import com.deepsight.data.CaseStatus
import com.deepsight.ReportUiState
import com.deepsight.SavedCaseUiState
import com.deepsight.result.ResultScreen
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.components.EmptyState
import com.deepsight.ui.components.TriageTone
import com.deepsight.ui.components.triageStyle
import com.deepsight.ui.theme.LocalTriageColors
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(items: List<HistoryItem>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        EmptyState(DeepSightIcons.History, "No signed-off cases yet", "Cases appear here after a clinician signs them off.", modifier.padding(16.dp))
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.caseId }) { HistoryRow(it, onOpen) }
    }
}

/** One case: triage, queue status (with the error when it failed) and sign-off. Also a patient's test list. */
@Composable
fun HistoryRow(item: HistoryItem, onOpen: (String) -> Unit) {
    val colors = LocalTriageColors.current
    val dot = when (item.level?.let { triageStyle(it).tone }) {
        TriageTone.ALERT -> colors.alert.content
        TriageTone.CAUTION -> colors.caution.content
        TriageTone.CLEAR -> colors.clear.content
        null -> MaterialTheme.colorScheme.outline
    }
    Card(onClick = { onOpen(item.caseId) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(dot, CircleShape))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.packName, style = MaterialTheme.typography.titleMedium)
                Text(if (item.classificationOnly) "Classification only" else item.level?.name ?: "No triage", style = MaterialTheme.typography.labelLarge)
                STATUS[item.status]?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                item.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Text(
                    listOfNotNull(
                        item.signedBy?.let { "Signed by $it${item.decision?.let { d -> " ($d)" } ?: ""}" },
                        item.signedAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
                            ?: "Submitted ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.createdAt))}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(DeepSightIcons.Forward, contentDescription = null)
        }
    }
}

/** A signed-off case, read-only: same layout as the live result, without Recapture. */
@Composable
fun SavedCaseScreen(state: SavedCaseUiState?, modifier: Modifier = Modifier) {
    if (state == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    ResultScreen(
        case = state.case, fields = state.fields, report = state.report?.let { ReportUiState.Done(it) }, signOff = state.signOff,
        onRecapture = {}, onSignOff = {}, modifier = modifier,
        testName = state.packName, images = state.images, positiveLabel = state.positiveLabel, canRecapture = false, analysedAt = state.analysedAt,
        classificationOnly = state.classificationOnly,
        patientLabel = state.patientLabel,
        pack = state.pack,
        wholeFieldClassification = state.pack?.let { it.taskType.name == "CLASSIFIER" && it.preprocess.source.name == "FIELD" } == true,
        batchReview = state.batchReview,
    )
}

/** What an unsigned case is waiting for; signed cases show their sign-off instead. */
private val STATUS = mapOf(
    CaseStatus.QUEUED to "Queued",
    CaseStatus.RUNNING to "Analysing…",
    CaseStatus.DONE to "Ready for sign-off",
    CaseStatus.FAILED to "Analysis failed",
)

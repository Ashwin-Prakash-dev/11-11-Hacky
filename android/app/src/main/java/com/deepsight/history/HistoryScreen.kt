package com.deepsight.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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

/** Keeps the selection across rotation. */
private val SelectionSaver = Saver<Set<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toSet() })

/**
 * Every case, newest first. Press and hold a finished case to select it, tap to select or unselect more, then delete the
 * selection after a confirmation ([onDelete]). Cases the queue is still working on can't be selected.
 */
@Composable
fun HistoryScreen(items: List<HistoryItem>, onOpen: (String) -> Unit, modifier: Modifier = Modifier, onDelete: (Set<String>) -> Unit = {}) {
    if (items.isEmpty()) {
        EmptyState(DeepSightIcons.History, "No signed-off cases yet", "Cases appear here after a clinician signs them off.", modifier.padding(16.dp))
        return
    }
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<String>()) }
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(items) { selected = prunedSelection(selected, items) } // deleted or newly busy cases drop out
    val selecting = selected.isNotEmpty()

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(selected.size, onCancel = { selected = emptySet() }, onDelete = { confirming = true })
        } else if (items.any { canDelete(it.status) }) {
            Text(
                "Press and hold a case to select it for deletion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.caseId }) { item ->
                HistoryRow(
                    item,
                    onOpen = { id -> if (selecting) { if (canDelete(item.status)) selected = toggled(selected, id) } else onOpen(id) },
                    selecting = selecting,
                    selected = item.caseId in selected,
                    onLongClick = if (canDelete(item.status)) ({ selected = toggled(selected, item.caseId) }) else null,
                )
            }
        }
    }

    if (confirming) {
        val chosen = items.filter { it.caseId in selected }
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(deleteTitle(chosen.size)) },
            text = { Text(deleteMessage(chosen)) },
            confirmButton = {
                Button(
                    onClick = { onDelete(chosen.map { it.caseId }.toSet()); selected = emptySet(); confirming = false },
                    modifier = Modifier.testTag("confirm-delete"),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$count selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.testTag("delete-selected"),
            ) {
                Icon(DeepSightIcons.Delete, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete")
            }
        }
    }
}

/** One case: triage, queue status (with the error when it failed) and sign-off. Also a patient's test list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(
    item: HistoryItem,
    onOpen: (String) -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalTriageColors.current
    val dot = when (item.level?.let { triageStyle(it).tone }) {
        TriageTone.ALERT -> colors.alert.content
        TriageTone.CAUTION -> colors.caution.content
        TriageTone.CLEAR -> colors.clear.content
        null -> MaterialTheme.colorScheme.outline
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().testTag("history-${item.caseId}").clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = { onOpen(item.caseId) }, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = null, enabled = canDelete(item.status))
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.size(12.dp).background(dot, CircleShape))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.packName, style = MaterialTheme.typography.titleMedium)
                Text(if (item.classificationOnly) "Cell classification" else item.level?.name ?: "No triage", style = MaterialTheme.typography.labelLarge)
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
            if (!selecting) Icon(DeepSightIcons.Forward, contentDescription = null)
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
    )
}

/** What an unsigned case is waiting for; signed cases show their sign-off instead. */
private val STATUS = mapOf(
    CaseStatus.QUEUED to "Queued",
    CaseStatus.RUNNING to "Analysing…",
    CaseStatus.DONE to "Ready for sign-off",
    CaseStatus.FAILED to "Analysis failed",
)

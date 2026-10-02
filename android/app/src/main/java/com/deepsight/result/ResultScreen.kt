package com.deepsight.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepsight.ReportUiState
import com.deepsight.ai.ReportSource
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.FieldResult
import com.deepsight.report.cleanNarrative
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.components.FieldImage
import com.deepsight.ui.components.FieldImageLegend
import com.deepsight.ui.components.NoticeRow
import com.deepsight.ui.components.PillTone
import com.deepsight.ui.components.SectionHeader
import com.deepsight.ui.components.StatTile
import com.deepsight.ui.components.StatusPill
import com.deepsight.ui.components.TriageBadge
import com.deepsight.ui.theme.Mono
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * A case's result: summary and triage, report, fields, sign-off. Stateless except for the sign-off form.
 * [report] is null when there is none (a case signed before reports were saved). [images] maps field ids to photos.
 */
@Composable
fun ResultScreen(
    case: CaseResult,
    fields: List<FieldResult>,
    report: ReportUiState?,
    signOff: SignOff?,
    onRecapture: (fieldId: String) -> Unit,
    onSignOff: (SignOff) -> Unit,
    modifier: Modifier = Modifier,
    testName: String? = null,
    images: Map<String, File> = emptyMap(),
    positiveLabel: String? = null,
    canRecapture: Boolean = true,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(case, testName)
        ReportCard(report)
        SectionHeader("Fields", supporting = "${fields.size} analysed · ${case.fieldsPassed} passed the quality check")
        fields.forEach { FieldCard(it, images[it.fieldId], positiveLabel, canRecapture, onRecapture) }
        SignOffCard(case.caseId, signOff, reportPending = report is ReportUiState.Writing, onSignOff = onSignOff)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryCard(case: CaseResult, testName: String?) = ElevatedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(testName ?: case.packId, style = MaterialTheme.typography.titleMedium)
            Text(case.caseId, style = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TriageBadge(case.triage.level, Modifier.fillMaxWidth())
        if (case.triage.provisional) {
            NoticeRow("PROVISIONAL: thresholds not clinically validated", DeepSightIcons.Warning, color = MaterialTheme.colorScheme.error)
        }
        val tiles = listOf("${case.fieldsPassed} / ${case.fieldIds.size}" to "Fields passed") +
            case.counts.entries.map { "${it.value}" to it.key }
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, label) -> StatTile(value, label, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text("Rule: ${case.triage.ruleId}", style = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (case.uncertainty.flag) "Uncertain: ${case.uncertainty.reason ?: "no reason given"}" else "Not flagged uncertain",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportCard(report: ReportUiState?) = ElevatedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(DeepSightIcons.Report, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Report", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            when {
                report is ReportUiState.Writing -> StatusPill("Writing…", icon = DeepSightIcons.Ai, tone = PillTone.ACCENT)
                report is ReportUiState.Done && report.report.source == ReportSource.GEMMA -> StatusPill("AI summary", icon = DeepSightIcons.Ai, tone = PillTone.GOOD)
                report is ReportUiState.Done -> StatusPill("Template")
            }
        }
        when (report) {
            null -> Text("No report was saved with this case.", style = MaterialTheme.typography.bodyMedium)
            is ReportUiState.Writing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    cleanNarrative(report.text).ifEmpty { "Gemma is writing the report on this phone…" },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            is ReportUiState.Done -> {
                Text(report.report.text, style = MaterialTheme.typography.bodyLarge)
                if (report.report.source == ReportSource.GEMMA) {
                    NoticeRow("Written by Gemma on this phone from the result above. The triage level is set by fixed rules; Gemma only words it.", DeepSightIcons.Info)
                } else {
                    report.report.note?.let { NoticeRow(it, DeepSightIcons.Info) }
                }
            }
        }
    }
}

/** "case-123_field_2" → "Field 2"; other ids (contract examples) as they are. */
private fun fieldName(fieldId: String) = fieldId.substringAfterLast("_field_", "").takeIf { it.isNotEmpty() }?.let { "Field $it" } ?: fieldId

@Composable
private fun FieldCard(field: FieldResult, image: File?, positiveLabel: String?, canRecapture: Boolean, onRecapture: (String) -> Unit) =
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fieldName(field.fieldId), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (field.quality.pass) {
                    StatusPill("Quality pass", icon = DeepSightIcons.Pass, tone = PillTone.GOOD)
                } else {
                    StatusPill("Rejected: ${field.quality.reasons.joinToString { it.name.lowercase() }}", icon = DeepSightIcons.Warning, tone = PillTone.ALERT)
                }
            }
            if (image != null && image.isFile) {
                FieldImage(image, field.objects, positiveLabel)
                if (positiveLabel != null && field.objects.isNotEmpty()) FieldImageLegend(positiveLabel)
            }
            field.router?.let { Text(routerMessage(it), style = MaterialTheme.typography.bodyMedium) }
            if (field.quality.pass) {
                Text(
                    "Counts: ${field.counts.entries.joinToString(" · ") { "${it.key} ${it.value}" }.ifEmpty { "none" }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            field.timingMs["total"]?.let {
                Text("Analysed in %.1f s on this phone".format(it / 1000.0), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!field.quality.pass && canRecapture) {
                OutlinedButton(onClick = { onRecapture(field.fieldId) }) {
                    Icon(DeepSightIcons.Camera, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Recapture")
                }
            }
        }
    }

@Composable
private fun SignOffCard(caseId: String, signOff: SignOff?, reportPending: Boolean, onSignOff: (SignOff) -> Unit) = ElevatedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(DeepSightIcons.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Clinician sign-off", style = MaterialTheme.typography.titleMedium)
        }
        if (signOff != null) {
            NoticeRow(
                "${signOff.decision.name} by ${signOff.signedBy} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(signOff.signedAt))}",
                DeepSightIcons.Pass, color = MaterialTheme.colorScheme.onSurface,
            )
            if (signOff.note.isNotBlank()) Text(signOff.note, style = MaterialTheme.typography.bodyMedium)
            Text("The triage level above is unchanged by sign-off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        var name by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var decision by remember { mutableStateOf(SignOffDecision.ACCEPT) }
        OutlinedTextField(name, { name = it }, label = { Text("Clinician name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignOffDecision.entries.forEach {
                FilterChip(decision == it, { decision = it }, label = { Text(it.name.lowercase().replaceFirstChar(Char::uppercase)) })
            }
        }
        OutlinedTextField(
            note, { note = it },
            label = { Text(if (decision == SignOffDecision.OVERRIDE) "Note (required)" else "Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (reportPending) Text("Sign-off opens when the report is ready.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            enabled = !reportPending && name.isNotBlank() && (decision == SignOffDecision.ACCEPT || note.isNotBlank()),
            onClick = { onSignOff(SignOff(caseId, name.trim(), System.currentTimeMillis(), decision, note.trim())) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Sign off") }
    }
}

package com.deepsight.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.RouterVerdict

/** Stateless except for the sign-off form. [report] is null until the report module (#24) is wired in. */
@Composable
fun ResultScreen(
    case: CaseResult,
    fields: List<FieldResult>,
    report: String?,
    signOff: SignOff?,
    onRecapture: (fieldId: String) -> Unit,
    onSignOff: (SignOff) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CaseCard(case)
        fields.forEach { FieldCard(it, onRecapture) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Report", style = MaterialTheme.typography.titleSmall)
                Text(report ?: "Report pending (#24).")
            }
        }
        SignOffCard(case.caseId, signOff, onSignOff)
    }
}

@Composable
private fun CaseCard(case: CaseResult) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(case.triage.level.name, style = MaterialTheme.typography.headlineSmall)
        if (case.triage.provisional) {
            Text("PROVISIONAL: thresholds not clinically validated", color = MaterialTheme.colorScheme.error)
        }
        Text("Rule: ${case.triage.ruleId}")
        Text("Fields passed: ${case.fieldsPassed} of ${case.fieldIds.size}")
        Text("Counts: ${case.counts.entries.joinToString { "${it.key} ${it.value}" }.ifEmpty { "none" }}")
        Text(if (case.uncertainty.flag) "Uncertain: ${case.uncertainty.reason ?: "no reason given"}" else "Not flagged uncertain")
    }
}

@Composable
private fun FieldCard(field: FieldResult, onRecapture: (String) -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(field.fieldId, style = MaterialTheme.typography.titleSmall)
        if (field.quality.pass) {
            Text("Quality: pass")
        } else {
            Text("Rejected: ${field.quality.reasons.joinToString { it.name.lowercase() }}", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { onRecapture(field.fieldId) }) { Text("Recapture") }
        }
        field.router?.let { r ->
            Text(
                when (r.verdict) {
                    RouterVerdict.MATCH -> "Image matches the selected test"
                    RouterVerdict.MISMATCH -> "Image looks like another test: ${r.predicted ?: "unknown"}"
                    RouterVerdict.REJECT -> "Image is not a recognised test type"
                },
            )
        }
        if (field.quality.pass) {
            Text("Counts: ${field.counts.entries.joinToString { "${it.key} ${it.value}" }.ifEmpty { "none" }}")
        }
    }
}

@Composable
private fun SignOffCard(caseId: String, signOff: SignOff?, onSignOff: (SignOff) -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Clinician sign-off", style = MaterialTheme.typography.titleSmall)
        if (signOff != null) {
            Text("${signOff.decision.name} by ${signOff.signedBy}")
            if (signOff.note.isNotBlank()) Text(signOff.note)
            Text("The triage level above is unchanged by sign-off.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        var name by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var decision by remember { mutableStateOf(SignOffDecision.ACCEPT) }
        OutlinedTextField(name, { name = it }, label = { Text("Clinician name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignOffDecision.entries.forEach {
                FilterChip(decision == it, { decision = it }, label = { Text(it.name.lowercase()) })
            }
        }
        OutlinedTextField(
            note, { note = it },
            label = { Text(if (decision == SignOffDecision.OVERRIDE) "Note (required)" else "Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = name.isNotBlank() && (decision == SignOffDecision.ACCEPT || note.isNotBlank()),
            onClick = { onSignOff(SignOff(caseId, name.trim(), System.currentTimeMillis(), decision, note.trim())) },
        ) { Text("Sign off") }
    }
}

package com.deepsight.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepsight.PatientUiState
import com.deepsight.history.HistoryRow
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.components.EmptyState

/** One patient and every test they've had, newest first; a row opens like one in History. */
@Composable
fun PatientScreen(state: PatientUiState?, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val patient = state?.patient
    if (patient == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(patient.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${patient.uid} · ${patient.ageOn(System.currentTimeMillis())} y · ${patient.sex.name} · born ${patient.dob}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.tests.isEmpty()) item { EmptyState(DeepSightIcons.History, "No tests yet", "Tests appear here when a batch is submitted for this patient.") }
        items(state.tests, key = { it.caseId }) { HistoryRow(it, onOpen) }
    }
}

package com.deepsight.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.components.EmptyState
import com.deepsight.ui.components.NoticeRow
import com.deepsight.ui.components.SectionHeader
import com.deepsight.ui.theme.Mono

@Composable
fun PatientChoiceScreen(packName: String, onExisting: () -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(packName, style = MaterialTheme.typography.headlineSmall)
        Text("Who is this screening for?", style = MaterialTheme.typography.bodyLarge)
        PatientActionCard("Existing patient", "Search by name or UID", onExisting)
        PatientActionCard("New patient", "Create a session-only POC profile", onNew)
        NoticeRow("Patient profiles are stored in memory for this POC. CSV or text storage can be connected later.", DeepSightIcons.Info)
    }
}

@Composable
private fun PatientActionCard(title: String, supporting: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(DeepSightIcons.Person, contentDescription = null, Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(DeepSightIcons.Forward, contentDescription = null)
        }
    }
}

@Composable
fun PatientSearchScreen(
    profiles: List<PatientProfile>,
    onSelect: (PatientProfile) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String = "Search the patient directory by name or UID.",
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = filterPatients(profiles, query)
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Patients", supporting = supporting)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name or UID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (matches.isEmpty()) {
            EmptyState(DeepSightIcons.Person, "No patients found", "Check the spelling or UID and try again.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(matches, key = { it.uid }) { patient -> PatientRow(patient, onSelect) }
            }
        }
    }
}

@Composable
private fun PatientRow(patient: PatientProfile, onSelect: (PatientProfile) -> Unit) {
    Card(
        onClick = { onSelect(patient) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(DeepSightIcons.Person, contentDescription = null, Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(patient.fullName, style = MaterialTheme.typography.titleMedium)
                Text(patient.uid, style = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("DOB ${patient.dateOfBirth} · ${patient.bloodGroup}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(DeepSightIcons.Forward, contentDescription = null)
        }
    }
}

@Composable
fun PatientDetailsScreen(patient: PatientProfile?, modifier: Modifier = Modifier) {
    if (patient == null) {
        EmptyState(DeepSightIcons.Person, "Patient not found", "This session does not contain that UID.", modifier.padding(16.dp))
        return
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(DeepSightIcons.Person, contentDescription = null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(patient.fullName, style = MaterialTheme.typography.headlineSmall)
        Detail("UID", patient.uid, mono = true)
        Detail("Date of birth", patient.dateOfBirth)
        Detail("Blood group", patient.bloodGroup)
        NoticeRow("POC profile · available only for the current app session", DeepSightIcons.Info)
    }
}

@Composable
private fun Detail(label: String, value: String, mono: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (mono) Mono else MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun NewPatientScreen(existingUids: Set<String>, onContinue: (PatientProfile) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var uid by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }
    var blood by rememberSaveable { mutableStateOf("") }
    val validation = validatePatient(PatientFormValues(name, uid, dob, blood), existingUids)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("New patient", supporting = "Create a profile before adding microscope images.")
        NoticeRow("POC only—this profile is not saved after the app process closes.", DeepSightIcons.Info)
        PatientField("Full name", name, { name = it }, validation.name.takeIf { name.isNotEmpty() })
        PatientField("Patient UID", uid, { uid = it }, validation.uid.takeIf { uid.isNotEmpty() })
        PatientField("Date of birth (YYYY-MM-DD)", dob, { dob = it }, validation.dateOfBirth.takeIf { dob.isNotEmpty() })
        PatientField(
            "Blood group", blood, { blood = it }, validation.bloodGroup.takeIf { blood.isNotEmpty() },
            supporting = "A+, A-, B+, B-, AB+, AB-, O+, O-",
        )
        Button(
            onClick = { validation.profile?.let(onContinue) },
            enabled = validation.isValid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue to images") }
    }
}

@Composable
private fun PatientField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = when {
            error != null -> ({ Text(error) })
            supporting != null -> ({ Text(supporting) })
            else -> null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
}

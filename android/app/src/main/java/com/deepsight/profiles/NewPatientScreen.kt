package com.deepsight.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.ThemePreviews
import com.deepsight.ui.components.NoticeRow
import com.deepsight.ui.theme.DeepSightTheme

/** Name, date of birth and sex; the app assigns the P-XXXX-XXXX ID. [error] is Patient's validation message from the last save. */
@Composable
fun NewPatientScreen(error: String?, onSave: (name: String, dob: String, sex: Sex) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf<Sex?>(null) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            dob, { dob = it }, label = { Text("Date of birth") }, placeholder = { Text("yyyy-MM-dd") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        Text("Sex", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { s ->
                FilterChip(selected = sex == s, onClick = { sex = s }, label = { Text(LABELS.getValue(s)) })
            }
        }
        Text("The app assigns the patient ID. Write it on the slip.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { NoticeRow(it, DeepSightIcons.Warning, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { sex?.let { onSave(name, dob, it) } }, enabled = sex != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Save patient")
        }
    }
}

private val LABELS = mapOf(Sex.F to "Female", Sex.M to "Male", Sex.OTHER to "Other")

@ThemePreviews
@Composable
private fun NewPatientScreenPreview() = DeepSightTheme { Surface { NewPatientScreen("Enter the patient's name", onSave = { _, _, _ -> }) } }

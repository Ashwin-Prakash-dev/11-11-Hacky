package com.deepsight.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.ThemePreviews
import com.deepsight.ui.components.EmptyState
import com.deepsight.ui.components.NoticeRow
import com.deepsight.ui.components.PillTone
import com.deepsight.ui.components.StatusPill
import com.deepsight.ui.theme.DeepSightTheme

/** Patient profiles with a search bar. UI only: it displays [profiles] and filters them as the user types. */
@Composable
fun ProfilesScreen(profiles: List<PatientProfile>, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = searchProfiles(profiles, query)
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search by name or ID") },
                leadingIcon = { Icon(DeepSightIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(DeepSightIcons.Clear, contentDescription = "Clear search") }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            NoticeRow("Sample data for the preview. Profiles are not stored yet.", DeepSightIcons.Info)
            Text(
                if (query.isBlank()) "${profiles.size} profiles" else "${shown.size} of ${profiles.size} profiles",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (shown.isEmpty()) {
            EmptyState(DeepSightIcons.Person, "No profiles match", "Try a different name or ID.", Modifier.padding(16.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { ProfileRow(it) }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: PatientProfile) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = CircleShape) {
                Text(initials(profile.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).width(32.dp), maxLines = 1)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text("${profile.id} · ${profile.ageYears} y · ${profile.sex}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (profile.caseCount == 0) "No cases yet"
                    else "${profile.caseCount} ${if (profile.caseCount == 1) "case" else "cases"}${profile.lastScreening?.let { " · last $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            profile.lastLevel?.let { LevelPill(it) }
        }
    }
}

@Composable
private fun LevelPill(level: TriageLevel) {
    val tone = when (level) {
        TriageLevel.ABNORMAL_FLAG -> PillTone.ALERT
        TriageLevel.NEEDS_EXPERT -> PillTone.CAUTION
        TriageLevel.NORMAL_SCREEN -> PillTone.GOOD
    }
    StatusPill(level.name, tone = tone)
}

@ThemePreviews
@Composable
private fun ProfilesScreenPreview() = DeepSightTheme { Surface { ProfilesScreen(SampleProfiles.all) } }

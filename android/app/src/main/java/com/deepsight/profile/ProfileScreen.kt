package com.deepsight.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.ThemePreviews
import com.deepsight.ui.components.NoticeRow
import com.deepsight.ui.components.SectionHeader
import com.deepsight.ui.theme.DeepSightTheme

/** Who is using the phone. UI only for now: profiles live in memory (see [Profiles]) and nothing else reads them yet. */
@Composable
fun ProfileScreen(
    profiles: Profiles,
    onAdd: (String, ProfileRole) -> Unit,
    onSelect: (String) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActiveProfileCard(profiles.active)
        SectionHeader("Profiles on this phone")
        NoticeRow("Profiles aren't saved yet: they clear when the app closes.", DeepSightIcons.Info)
        profiles.all.forEach { ProfileRow(it, selected = it.id == profiles.activeId, onSelect = { onSelect(it.id) }) }
        OutlinedButton(onClick = { adding = true }) {
            Icon(DeepSightIcons.Add, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add profile")
        }
        SectionHeader("App")
        OutlinedCard(onClick = onAbout, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(DeepSightIcons.Info, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Text("About DeepSight and licences", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(DeepSightIcons.Forward, contentDescription = null)
            }
        }
    }
    if (adding) AddProfileDialog(onDismiss = { adding = false }, onAdd = { name, role -> onAdd(name, role); adding = false })
}

@Composable
private fun ActiveProfileCard(active: Profile?) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = CircleShape) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    if (active != null) Text(active.initials, style = MaterialTheme.typography.titleLarge)
                    else Icon(DeepSightIcons.Person, contentDescription = null, Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(active?.name ?: "No profile selected", style = MaterialTheme.typography.titleLarge)
                Text(active?.role?.label ?: "Add a profile below to choose who is using this phone.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: Profile, selected: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(profile.role.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddProfileDialog(onDismiss: () -> Unit, onAdd: (String, ProfileRole) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(ProfileRole.HEALTH_WORKER) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileRole.entries.forEach { FilterChip(selected = role == it, onClick = { role = it }, label = { Text(it.label) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, role) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@ThemePreviews
@Composable
private fun ProfileScreenPreview() = DeepSightTheme {
    ProfileScreen(
        Profiles().add("Asha Kumar", ProfileRole.CLINICIAN).add("Ravi", ProfileRole.HEALTH_WORKER),
        onAdd = { _, _ -> }, onSelect = {}, onAbout = {},
    )
}

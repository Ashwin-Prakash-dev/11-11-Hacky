package com.deepsight.home

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.deepsight.PackItem
import com.deepsight.ai.AiStatus
import com.deepsight.engine.contract.PackManifest
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.components.PillTone
import com.deepsight.ui.components.SectionHeader
import com.deepsight.ui.components.StatusPill

@Composable
fun HomeScreen(
    packs: List<PackItem>?,
    aiStatus: AiStatus,
    historyCount: Int,
    profileCount: Int,
    onPick: (PackManifest) -> Unit,
    onHistory: () -> Unit,
    onProfiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Hero(aiStatus)
        SectionHeader("Choose test", supporting = "Test packs installed on this phone")
        when {
            packs == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Loading tests…", style = MaterialTheme.typography.bodyMedium)
            }
            packs.isEmpty() -> Text("No test packs installed.", style = MaterialTheme.typography.bodyMedium)
        }
        packs?.forEach { PackCard(it, onPick) }
        SectionHeader("Records")
        ProfilesCard(profileCount, onProfiles)
        HistoryCard(historyCount, onHistory)
    }
}

@Composable
private fun Hero(aiStatus: AiStatus) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.medium) {
                Icon(DeepSightIcons.Science, contentDescription = null, Modifier.padding(10.dp).size(28.dp))
            }
            Text("Offline microscopy screening", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Add a microscope field and get a quality check, a triage flag and a short report. Everything runs on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("On device", icon = DeepSightIcons.OnDevice, tone = PillTone.ACCENT)
                when (aiStatus) {
                    AiStatus.Ready -> StatusPill("AI report ready", icon = DeepSightIcons.Ai, tone = PillTone.GOOD)
                    AiStatus.Loading -> StatusPill("AI report loading…", icon = DeepSightIcons.Ai)
                    is AiStatus.Missing -> StatusPill("Template reports", icon = DeepSightIcons.Report)
                    is AiStatus.Failed -> StatusPill("AI report unavailable", icon = DeepSightIcons.Warning, tone = PillTone.CAUTION)
                }
            }
        }
    }
}

@Composable
private fun PackCard(item: PackItem, onPick: (PackManifest) -> Unit) {
    val ready = item.ready
    Card(
        onClick = { onPick(item.manifest) },
        enabled = ready,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListRow(
            icon = DeepSightIcons.Science,
            title = item.manifest.displayName,
            supporting = if (ready) "Ready · pack ${item.manifest.version}" else "Not yet validated on a phone",
            trailing = { if (ready) Icon(DeepSightIcons.Forward, contentDescription = null) else StatusPill("Not validated", tone = PillTone.NEUTRAL) },
            dim = !ready,
        )
    }
}

@Composable
private fun ProfilesCard(count: Int, onProfiles: () -> Unit) {
    OutlinedCard(onClick = onProfiles, modifier = Modifier.fillMaxWidth()) {
        ListRow(
            icon = DeepSightIcons.Person,
            title = "Profiles",
            supporting = "$count patient profiles · search by name or ID",
            trailing = { Icon(DeepSightIcons.Forward, contentDescription = null) },
        )
    }
}

@Composable
private fun HistoryCard(count: Int, onHistory: () -> Unit) {
    OutlinedCard(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
        ListRow(
            icon = DeepSightIcons.History,
            title = "History",
            supporting = when (count) { 0 -> "No signed-off cases yet"; 1 -> "1 signed-off case"; else -> "$count signed-off cases" },
            trailing = { Icon(DeepSightIcons.Forward, contentDescription = null) },
        )
    }
}

@Composable
private fun ListRow(icon: ImageVector, title: String, supporting: String, trailing: @Composable () -> Unit, dim: Boolean = false) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = if (dim) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.small,
        ) { Icon(icon, contentDescription = null, Modifier.padding(8.dp).size(24.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

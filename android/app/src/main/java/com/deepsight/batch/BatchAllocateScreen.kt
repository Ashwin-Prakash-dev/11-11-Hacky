package com.deepsight.batch

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.deepsight.engine.contract.PackManifest
import com.deepsight.profiles.Patient
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.components.EmptyState
import com.deepsight.ui.components.NoticeRow
import com.deepsight.ui.components.PillTone
import com.deepsight.ui.components.SectionHeader
import com.deepsight.ui.components.StatusPill
import com.deepsight.ui.components.decodeDisplayBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun imagesText(n: Int) = if (n == 1) "1 image" else "$n images"
internal fun modulesText(n: Int) = if (n == 1) "1 module" else "$n modules"

/** The submit button's label: what exactly is about to be analysed. */
internal fun submitLabel(images: Int, modules: Int) = "Analyse ${imagesText(images)} in ${modulesText(modules)}"

/**
 * Step 2 of a batch: which module each image goes to. The router's suggestions are shown, every image can be moved,
 * and nothing is submitted until a person has ticked the verification box (the router can be wrong).
 */
@Composable
fun BatchAllocateScreen(
    draft: BatchDraft,
    packs: List<PackManifest>,
    patients: List<Patient>,
    busy: Boolean,
    error: String?,
    onReassign: (imageId: Int, packId: String?) -> Unit,
    onRemove: (imageId: Int) -> Unit,
    onPatient: (uid: String?) -> Unit,
    onVerified: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (draft.images.isEmpty()) {
        EmptyState(DeepSightIcons.Gallery, "No images selected", "Go back and choose the images to screen.", modifier.padding(16.dp))
        return
    }
    val names = packs.associate { it.id to it.displayName }
    val groups = draft.groups(packs.map { it.id })
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IntroCard(draft)
        PatientCard(draft.patientUid, patients, onPatient)
        if (draft.unassigned.isNotEmpty()) {
            SectionHeader("Not allocated · ${imagesText(draft.unassigned.size)}", supporting = "The router did not recognise these. Choose a module for each.")
            draft.unassigned.forEach { ImageRow(it, packs, names, onReassign, onRemove) }
        }
        groups.forEach { (packId, images) ->
            SectionHeader("${names[packId] ?: packId} · ${imagesText(images.size)}")
            images.forEach { ImageRow(it, packs, names, onReassign, onRemove) }
        }
        VerifyCard(draft, onVerified)
        error?.let { NoticeRow(it, DeepSightIcons.Warning, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSubmit, enabled = draft.canSubmit && !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Submitting…")
            } else {
                Text(submitLabel(draft.images.size, groups.size))
            }
        }
        TextButton(onClick = onClear, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Clear all images") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IntroCard(draft: BatchDraft) = ElevatedCard(
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    modifier = Modifier.fillMaxWidth(),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check the allocation", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            StatusPill("Router: placeholder", tone = PillTone.CAUTION)
        }
        Text(
            "${imagesText(draft.images.size)} added. The router suggests a module for each image, but for now it is a placeholder that picks at random, " +
                "so check every one. You can move an image to another module.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (draft.changedCount > 0) Text("${draft.changedCount} moved by you", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PatientCard(selected: String?, patients: List<Patient>, onPatient: (String?) -> Unit) = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth(),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Patient", style = MaterialTheme.typography.titleMedium)
        if (patients.isEmpty()) {
            Text("No patients yet. Add one in the Single tab first; every case belongs to a patient.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        var open by remember { mutableStateOf(false) }
        val current = patients.firstOrNull { it.uid == selected }
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("patient-menu")) {
                Icon(DeepSightIcons.Person, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(current?.let { "${it.name} · ${it.uid}" } ?: "Choose a patient", modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                patients.forEach { p ->
                    DropdownMenuItem(
                        text = { Text("${p.name} · ${p.uid}") },
                        onClick = { onPatient(p.uid); open = false },
                        modifier = Modifier.testTag("patient-${p.uid}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageRow(image: DraftImage, packs: List<PackManifest>, names: Map<String, String>, onReassign: (Int, String?) -> Unit, onRemove: (Int) -> Unit) {
    val bitmap by produceState<Bitmap?>(null, image.file) { value = withContext(Dispatchers.IO) { decodeDisplayBitmap(image.file, maxSide = 240) } }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(image.file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    image.suggested?.let { "Router suggested: ${names[it] ?: it}" } ?: "Router did not recognise this image",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (image.changedByUser) StatusPill("Changed by you", tone = PillTone.ACCENT)
                ModuleMenu(image, packs, names, onReassign)
            }
            IconButton(onClick = { onRemove(image.id) }) { Icon(DeepSightIcons.Delete, contentDescription = "Remove ${image.file.name}") }
        }
    }
}

@Composable
private fun ModuleMenu(image: DraftImage, packs: List<PackManifest>, names: Map<String, String>, onReassign: (Int, String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("change-${image.id}")) {
            Text(image.assigned?.let { names[it] ?: it } ?: "Choose module")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            packs.forEach { pack ->
                DropdownMenuItem(
                    text = { Text(pack.displayName) },
                    onClick = { onReassign(image.id, pack.id); open = false },
                    modifier = Modifier.testTag("choose-${image.id}-${pack.id}"),
                )
            }
        }
    }
}

@Composable
private fun VerifyCard(draft: BatchDraft, onVerified: (Boolean) -> Unit) = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth(),
) {
    val canVerify = draft.unassigned.isEmpty()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Verify", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().toggleable(value = draft.verified, enabled = canVerify, role = Role.Checkbox, onValueChange = onVerified),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = draft.verified, onCheckedChange = null, enabled = canVerify)
            Spacer(Modifier.width(12.dp))
            Text("I checked that every image is in the right module", style = MaterialTheme.typography.bodyMedium)
        }
        if (!canVerify) {
            NoticeRow("Choose a module for every image first.", DeepSightIcons.Info)
        } else {
            Text(
                "Images are analysed only after you verify. Changing anything afterwards asks you to verify again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

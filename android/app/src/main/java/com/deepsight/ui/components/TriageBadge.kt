package com.deepsight.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.ui.ThemePreviews
import com.deepsight.ui.theme.DeepSightTheme
import com.deepsight.ui.theme.LocalTriageColors

/** The case's triage level, colour-coded, with its plain-language meaning. Shows the exact contract string. */
@Composable
fun TriageBadge(level: TriageLevel, modifier: Modifier = Modifier) {
    val style = triageStyle(level)
    val colors = LocalTriageColors.current.let {
        when (style.tone) {
            TriageTone.ALERT -> it.alert
            TriageTone.CAUTION -> it.caution
            TriageTone.CLEAR -> it.clear
        }
    }
    Surface(color = colors.container, contentColor = colors.content, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(level.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(style.meaning, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@ThemePreviews
@Composable
private fun TriageBadgePreview() = DeepSightTheme {
    Surface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TriageLevel.entries.forEach { TriageBadge(it, Modifier.fillMaxWidth()) }
        }
    }
}

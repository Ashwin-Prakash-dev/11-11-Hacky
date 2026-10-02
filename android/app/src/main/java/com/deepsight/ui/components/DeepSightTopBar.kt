package com.deepsight.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.ThemePreviews
import com.deepsight.ui.theme.DeepSightTheme

/** App bar with a visible back button whenever [canGoBack]; the system back gesture does the same. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSightTopBar(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = onBack) { Icon(DeepSightIcons.Back, contentDescription = "Back") }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    )
}

@ThemePreviews
@Composable
private fun DeepSightTopBarPreview() = DeepSightTheme { DeepSightTopBar("Result", canGoBack = true, onBack = {}) }

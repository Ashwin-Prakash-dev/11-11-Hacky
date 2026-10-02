package com.deepsight.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/** Light and dark previews of one composable, in Android Studio only. */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

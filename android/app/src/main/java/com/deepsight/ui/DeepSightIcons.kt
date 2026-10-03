package com.deepsight.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.deepsight.R

/** Every icon the app uses, in one place (design-system pattern). Drawables cover what material-icons-core lacks. */
object DeepSightIcons {
    val Back = Icons.AutoMirrored.Rounded.ArrowBack
    val Forward = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val Info = Icons.Outlined.Info
    val OnDevice = Icons.Outlined.Lock
    val Delete = Icons.Outlined.Delete
    val Person = Icons.Outlined.Person
    val Pass = Icons.Rounded.CheckCircle
    val Warning = Icons.Rounded.Warning
    val Add = Icons.Rounded.Add

    val Camera: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_camera)
    val Gallery: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_gallery)
    val History: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_history)
    val Report: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_report)
    val Science: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_science)
    val Ai: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_ai)
    val Batch: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_batch)
}

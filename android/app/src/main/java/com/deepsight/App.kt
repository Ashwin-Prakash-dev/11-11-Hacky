package com.deepsight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepsight.about.AboutScreen
import com.deepsight.about.DocumentScreen
import com.deepsight.capture.CaseScreen
import com.deepsight.history.HistoryScreen
import com.deepsight.history.SavedCaseScreen
import com.deepsight.home.HomeScreen
import com.deepsight.profiles.ProfilesScreen
import com.deepsight.profiles.SampleProfiles
import com.deepsight.result.ResultScreen
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.components.DeepSightTopBar
import com.deepsight.ui.components.DisclaimerBar

/** Home → case → result and sign-off → history; about and the licence from home. State lives in [AppViewModel]. */
@Composable
fun DeepSightApp(vm: AppViewModel) {
    val stack by vm.stack.collectAsStateWithLifecycle()
    val route = stack.last()
    BackHandler(enabled = stack.size > 1) { vm.back() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DeepSightTopBar(
                title = title(route),
                canGoBack = stack.size > 1,
                onBack = { vm.back() },
                actions = {
                    if (route == Route.Home) IconButton(onClick = { vm.open(Route.About) }) { Icon(DeepSightIcons.Info, contentDescription = "About") }
                },
            )
        },
        bottomBar = { DisclaimerBar() },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Crossfade(targetState = route, label = "screen") { screen -> Screen(screen, vm) }
        }
    }
}

@Composable
private fun title(route: Route): String = when (route) {
    Route.Home -> stringResource(R.string.app_name)
    Route.Case -> "New case"
    Route.Result -> "Result"
    Route.History -> "History"
    is Route.SavedCase -> "Signed-off case"
    Route.About -> "About"
    Route.Profiles -> "Profiles"
    is Route.Document -> route.title
}

@Composable
private fun Screen(route: Route, vm: AppViewModel) {
    when (route) {
        Route.Home -> {
            val packs by vm.packs.collectAsStateWithLifecycle()
            val ai by vm.aiStatus.collectAsStateWithLifecycle()
            val history by vm.history.collectAsStateWithLifecycle()
            HomeScreen(
                packs, ai, history.size, SampleProfiles.all.size,
                onPick = vm::startCase, onHistory = { vm.open(Route.History) }, onProfiles = { vm.open(Route.Profiles) },
            )
        }
        Route.Case -> {
            val case by vm.case.collectAsStateWithLifecycle()
            case?.let {
                CaseScreen(it, onImport = vm::importImage, captureFile = vm::captureFile, onCaptured = vm::onCaptured, onDelete = vm::deleteImage, onAnalyse = vm::analyse, onResume = vm::refreshImages)
            }
        }
        Route.Result -> {
            val result by vm.result.collectAsStateWithLifecycle()
            result?.let { r ->
                ResultScreen(
                    r.run.case, r.run.fields, r.report, r.signOff, onRecapture = vm::recapture, onSignOff = vm::signOff,
                    testName = r.pack.displayName, images = r.images, positiveLabel = r.pack.output.imageScoreLabel,
                )
            }
        }
        Route.History -> {
            val history by vm.history.collectAsStateWithLifecycle()
            HistoryScreen(history, onOpen = vm::openSaved)
        }
        is Route.SavedCase -> {
            val saved by vm.saved.collectAsStateWithLifecycle()
            SavedCaseScreen(saved?.takeIf { it.case.caseId == route.caseId })
        }
        Route.Profiles -> ProfilesScreen(SampleProfiles.all)
        Route.About -> AboutScreen(onOpenDocument = { title, asset -> vm.open(Route.Document(title, asset)) })
        is Route.Document -> DocumentScreen(route.asset)
    }
}

package com.deepsight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepsight.about.AboutScreen
import com.deepsight.about.DocumentScreen
import com.deepsight.batch.BatchScreen
import com.deepsight.capture.CaseScreen
import com.deepsight.history.HistoryScreen
import com.deepsight.history.SavedCaseScreen
import com.deepsight.home.HomeScreen
import com.deepsight.profiles.ProfilesScreen
import com.deepsight.profiles.NewPatientScreen
import com.deepsight.profiles.PatientScreen
import com.deepsight.profile.ProfileScreen
import com.deepsight.result.ResultScreen
import com.deepsight.ui.DeepSightIcons
import com.deepsight.ui.components.DeepSightTopBar
import com.deepsight.ui.components.DisclaimerBar

/**
 * Three tabs, each with its own back stack ([NavState]): Batch, Single (home → patient → case → result and sign-off →
 * history; the patient list) and Profile (the phone's users, about and the licences). State lives in [AppViewModel].
 */
@Composable
fun DeepSightApp(vm: AppViewModel) {
    val nav by vm.nav.collectAsStateWithLifecycle()
    val route = nav.route
    BackHandler(enabled = nav.back() != null) { vm.back() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DeepSightTopBar(
                title = title(route),
                canGoBack = nav.stack.size > 1,
                onBack = { vm.back() },
                actions = {
                    if (route == Route.Home) IconButton(onClick = { vm.open(Route.About) }) { Icon(DeepSightIcons.Info, contentDescription = "About") }
                },
            )
        },
        bottomBar = {
            Column {
                DisclaimerBar()
                TabBar(nav.tab, onSelect = vm::selectTab)
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Crossfade(targetState = route, label = "screen") { screen -> Screen(screen, vm) }
        }
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tabIcon(tab), contentDescription = null) },
                label = { Text(tabLabel(tab)) },
            )
        }
    }
}

private fun tabLabel(tab: Tab): String = when (tab) {
    Tab.BATCH -> "Batch"
    Tab.SINGLE -> "Single"
    Tab.PROFILE -> "Profile"
}

@Composable
private fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.BATCH -> DeepSightIcons.Batch
    Tab.SINGLE -> DeepSightIcons.Science
    Tab.PROFILE -> DeepSightIcons.Person
}

@Composable
private fun title(route: Route): String = when (route) {
    Route.Batch -> "Batch upload"
    Route.Profile -> "Profiles"
    Route.Home -> stringResource(R.string.app_name)
    Route.Case -> "New case"
    Route.Result -> "Result"
    Route.History -> "History"
    is Route.SavedCase -> "Signed-off case"
    Route.About -> "About"
    Route.Profiles -> "Patients"
    Route.PickPatient -> "Choose patient"
    is Route.Patient -> "Patient"
    Route.NewPatient -> "New patient"
    is Route.Document -> route.title
}

@Composable
private fun Screen(route: Route, vm: AppViewModel) {
    when (route) {
        Route.Batch -> {
            val batches by vm.batches.collectAsStateWithLifecycle()
            BatchScreen(batches, onOpen = vm::openBatch, onUseSingle = { vm.selectTab(Tab.SINGLE) })
        }
        Route.Profile -> {
            val profiles by vm.profiles.collectAsStateWithLifecycle()
            ProfileScreen(profiles, onAdd = vm::addProfile, onSelect = vm::selectProfile, onAbout = { vm.open(Route.About) })
        }
        Route.Home -> {
            val packs by vm.packs.collectAsStateWithLifecycle()
            val ai by vm.aiStatus.collectAsStateWithLifecycle()
            val history by vm.history.collectAsStateWithLifecycle()
            val patients by vm.patients.collectAsStateWithLifecycle()
            HomeScreen(
                packs, ai, history.size, patients.size,
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
                    testName = r.pack.displayName, images = r.images, positiveLabel = r.pack.output.imageScoreLabel, analysedAt = r.run.analysedAt, canRecapture = r.canRecapture,
                    classificationOnly = r.pack.triage.rules.all { it.level.name == "NEEDS_EXPERT" },
                    patientLabel = r.patientLabel,
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
        Route.Profiles -> {
            val patients by vm.patients.collectAsStateWithLifecycle()
            ProfilesScreen(patients, onSelect = { vm.open(Route.Patient(it.id)) })
        }
        Route.PickPatient -> {
            val patients by vm.patients.collectAsStateWithLifecycle()
            ProfilesScreen(patients, onSelect = { vm.selectPatient(it.id) }, onNew = { vm.open(Route.NewPatient) })
        }
        is Route.Patient -> {
            val profile by remember(route.uid) { vm.patientProfile(route.uid) }.collectAsStateWithLifecycle(null)
            PatientScreen(profile, onOpen = vm::openSaved)
        }
        Route.NewPatient -> {
            val error by vm.patientError.collectAsStateWithLifecycle()
            NewPatientScreen(error, onSave = vm::createPatient)
        }
        Route.About -> AboutScreen(onOpenDocument = { title, asset -> vm.open(Route.Document(title, asset)) })
        is Route.Document -> DocumentScreen(route.asset)
    }
}

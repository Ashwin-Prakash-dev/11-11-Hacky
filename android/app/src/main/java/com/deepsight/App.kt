package com.deepsight

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.util.Log
import com.deepsight.capture.CaptureScreen
import com.deepsight.capture.CaseStore
import com.deepsight.data.CaseDao
import com.deepsight.data.CaseEntity
import com.deepsight.data.FieldEntity
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.PackManifest
import com.deepsight.result.ResultScreen
import com.deepsight.result.SignOff
import com.deepsight.result.signOff
import com.deepsight.ui.components.DeepSightTopBar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class Screen { CHOOSE, CASE, RESULT, HISTORY }

/** Choose test → case → result and sign-off → history. ponytail: in-memory back stack and history; lost on rotation/process death. */
@Composable
fun DeepSightApp(runner: CaseRunner, dao: CaseDao) {
    val stack = remember { mutableStateListOf(Screen.CHOOSE) }
    val history = remember { mutableStateListOf<CaseResult>() }
    val screen = stack.last()
    val scope = rememberCoroutineScope()
    val files = LocalContext.current.filesDir.resolve("cases")
    val store = remember(files) { CaseStore(files) }
    var packs by remember { mutableStateOf<List<PackManifest>?>(null) }
    var pack by remember { mutableStateOf<PackManifest?>(null) }
    var caseId by remember { mutableStateOf("") }
    var run by remember { mutableStateOf<CaseRun?>(null) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { packs = runner.packs() }
    // Signed-off cases saved earlier come back into the list (oldest first, like new sign-offs).
    LaunchedEffect(Unit) {
        history.addAll(dao.history().first().reversed().mapNotNull { it.caseResultJson?.let(Contracts::parseCaseResult) })
    }
    fun go(next: Screen) = stack.add(next)
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { DeepSightTopBar(stringResource(R.string.app_name), canGoBack = stack.size > 1, onBack = { stack.removeAt(stack.lastIndex) }) },
        bottomBar = {
            Text(
                stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (screen) {
                Screen.CHOOSE -> ChooseScreen(
                    packs,
                    onPick = { pack = it; caseId = "case-${System.currentTimeMillis()}"; error = null; go(Screen.CASE) },
                    onHistory = { go(Screen.HISTORY) },
                )
                Screen.CASE -> CaseScreen(store, caseId, running, error, onDone = {
                    val images = store.fields(caseId)
                    if (images.isEmpty()) {
                        error = "Import or capture at least one image first."
                        return@CaseScreen
                    }
                    val started = caseId
                    running = true
                    error = null
                    scope.launch {
                        // Only the case still on screen may show its result or error: the user may have backed out.
                        fun current() = caseId == started && stack.last() == Screen.CASE
                        try {
                            val result = runner.run(checkNotNull(pack).id, started, images)
                            if (current()) { run = result; go(Screen.RESULT) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("DeepSight", "case $started failed", e)
                            if (current()) error = "Analysis failed: ${e.message}"
                        } catch (e: OutOfMemoryError) {
                            Log.e("DeepSight", "case $started ran out of memory", e)
                            if (current()) error = "Analysis failed: not enough memory for these images."
                        } finally {
                            running = false
                        }
                    }
                })
                Screen.RESULT -> {
                    val (fields, case) = checkNotNull(run)
                    val images = remember(case.caseId) { store.fields(case.caseId).associateBy { fieldId(case.caseId, it.index) } }
                    var signOff by remember { mutableStateOf<SignOff?>(null) }
                    LaunchedEffect(case.caseId) { signOff = dao.caseById(case.caseId)?.signOff() }
                    ResultScreen(
                        case, fields, report = null, signOff = signOff,
                        // The rejected image goes, so the next run doesn't reject it again; capture lives on the case screen.
                        onRecapture = { images[it]?.file?.delete(); stack.removeAt(stack.lastIndex) },
                        onSignOff = {
                            history.add(case)
                            scope.launch { saveSignedOff(dao, store, fields, case, it) }
                            stack.clear(); stack.addAll(listOf(Screen.CHOOSE, Screen.HISTORY))
                        },
                    )
                }
                Screen.HISTORY -> HistoryScreen(history)
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall)

@Composable
private fun ChooseScreen(tests: List<PackManifest>?, onPick: (PackManifest) -> Unit, onHistory: () -> Unit) {
    Title("Choose test")
    when {
        tests == null -> Text("Loading tests…")
        tests.isEmpty() -> Text("No test packs installed.")
    }
    tests?.forEach { Button(onClick = { onPick(it) }, modifier = Modifier.fillMaxWidth()) { Text(it.displayName) } }
    Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("History") }
}

@Composable
private fun ColumnScope.CaseScreen(store: CaseStore, caseId: String, running: Boolean, error: String?, onDone: () -> Unit) {
    Title("Case $caseId")
    CaptureScreen(store, caseId, Modifier.weight(1f))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = onDone, enabled = !running, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Analysing…" else "Show result") }
}

@Composable
private fun HistoryScreen(history: List<CaseResult>) {
    Title("History")
    if (history.isEmpty()) Text("No signed-off cases yet")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(history) { Card(Modifier.fillMaxWidth()) { Text("${it.caseId}: ${it.triage.level}", Modifier.padding(12.dp)) } }
    }
}

/** Persists the case, its fields (each with its image file) and the case_result JSON as the contract wrote it. */
private suspend fun saveSignedOff(dao: CaseDao, store: CaseStore, fields: List<FieldResult>, result: CaseResult, signOff: SignOff) {
    val images = store.fields(result.caseId).associateBy { fieldId(result.caseId, it.index) }
    val rows = fields.map { f -> FieldEntity(f.fieldId, result.caseId, images[f.fieldId]?.file?.path, Contracts.encode(f)) }
    val case = CaseEntity(result.caseId, result.packId, signOff.signedAt, Contracts.encode(result))
    dao.upsert(signOff.applyTo(case), rows)
}

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Screen { CHOOSE, CASE, RESULT, HISTORY }

/** Choose test → case → result and sign-off → history. ponytail: in-memory back stack and history; lost on rotation/process death. */
@Composable
fun DeepSightApp(engine: FakeEngine, dao: CaseDao) {
    val stack = remember { mutableStateListOf(Screen.CHOOSE) }
    val history = remember { mutableStateListOf<CaseResult>() }
    val screen = stack.last()
    val scope = rememberCoroutineScope()
    val files = LocalContext.current.filesDir.resolve("cases")
    // Signed-off cases saved earlier come back into the list (oldest first, like new sign-offs).
    LaunchedEffect(Unit) {
        history.addAll(dao.history().first().reversed().mapNotNull { it.caseResultJson?.let(Contracts::parseCaseResult) })
    }
    fun go(next: Screen) = stack.add(next)
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                Screen.CHOOSE -> ChooseScreen(engine.tests, onPick = { go(Screen.CASE) }, onHistory = { go(Screen.HISTORY) })
                Screen.CASE -> CaseScreen(engine.fields(), onDone = { go(Screen.RESULT) })
                Screen.RESULT -> {
                    val case = engine.caseResult()
                    var signOff by remember { mutableStateOf<SignOff?>(null) }
                    LaunchedEffect(case.caseId) { signOff = dao.caseById(case.caseId)?.signOff() }
                    ResultScreen(
                        case, engine.fields(), report = null, signOff = signOff,
                        onRecapture = { stack.removeAt(stack.lastIndex) }, // back to the case screen, where capture lives
                        onSignOff = {
                            history.add(case)
                            scope.launch { saveSignedOff(dao, CaseStore(files), engine.fields(), case, it) }
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
private fun ChooseScreen(tests: List<PackManifest>, onPick: () -> Unit, onHistory: () -> Unit) {
    Title("Choose test")
    tests.forEach { Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text(it.displayName) } }
    Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("History") }
}

@Composable
private fun ColumnScope.CaseScreen(fields: List<FieldResult>, onDone: () -> Unit) {
    val caseId = fields.first().caseId
    Title("Case $caseId")
    // Real images (import/capture) live in filesDir; the list below is still the fake engine's until #30.
    val store = CaseStore(LocalContext.current.filesDir.resolve("cases"))
    CaptureScreen(store, caseId, Modifier.weight(1f))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(fields) { f ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(f.fieldId)
                    Text(if (f.quality.pass) "Passed" else "Rejected: ${f.quality.reasons.joinToString { it.name.lowercase() }}")
                }
            }
        }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Show result") }
}

@Composable
private fun HistoryScreen(history: List<CaseResult>) {
    Title("History")
    if (history.isEmpty()) Text("No signed-off cases yet")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(history) { Card(Modifier.fillMaxWidth()) { Text("${it.caseId}: ${it.triage.level}", Modifier.padding(12.dp)) } }
    }
}

/** Persists the case, its fields and the case_result JSON as the contract wrote it. Fake-engine fields get captured images by position. */
private suspend fun saveSignedOff(dao: CaseDao, store: CaseStore, fields: List<FieldResult>, result: CaseResult, signOff: SignOff) {
    val images = store.fields(result.caseId)
    val rows = fields.mapIndexed { i, f -> FieldEntity(f.fieldId, result.caseId, images.getOrNull(i)?.file?.path, Contracts.encode(f)) }
    val case = CaseEntity(result.caseId, result.packId, signOff.signedAt, Contracts.encode(result))
    dao.upsert(signOff.applyTo(case), rows)
}

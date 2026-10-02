package com.deepsight

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.PackManifest

private enum class Screen { CHOOSE, CASE, RESULT, REVIEW, HISTORY }

/** Choose test → case → result → review and sign-off → history. ponytail: in-memory back stack and history; lost on rotation/process death. */
@Composable
fun DeepSightApp(engine: FakeEngine) {
    val stack = remember { mutableStateListOf(Screen.CHOOSE) }
    val history = remember { mutableStateListOf<CaseResult>() }
    val screen = stack.last()
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
                Screen.RESULT -> ResultScreen(engine.caseResult(), onReview = { go(Screen.REVIEW) })
                Screen.REVIEW -> ReviewScreen(engine.caseResult(), onSignOff = {
                    history.add(it)
                    stack.clear(); stack.addAll(listOf(Screen.CHOOSE, Screen.HISTORY))
                })
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
private fun CaseScreen(fields: List<FieldResult>, onDone: () -> Unit) {
    Title("Case ${fields.first().caseId}")
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
private fun ResultScreen(case: CaseResult, onReview: () -> Unit) {
    Title("Result")
    // Triage is shown exactly as the engine returned it; the UI never derives or changes it.
    Text("Triage: ${case.triage.level}")
    Text("Rule: ${case.triage.ruleId}" + if (case.triage.provisional) " (provisional)" else "")
    Text("Fields passed: ${case.fieldsPassed} of ${case.fieldIds.size}")
    Text("Counts: ${case.counts.entries.joinToString { "${it.key} ${it.value}" }}")
    if (case.uncertainty.flag) Text("Uncertain: ${case.uncertainty.reason}")
    Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) { Text("Review and sign off") }
}

@Composable
private fun ReviewScreen(case: CaseResult, onSignOff: (CaseResult) -> Unit) {
    Title("Review")
    Text("Case ${case.caseId}: ${case.triage.level}")
    Button(onClick = { onSignOff(case) }, modifier = Modifier.fillMaxWidth()) { Text("Sign off") }
}

@Composable
private fun HistoryScreen(history: List<CaseResult>) {
    Title("History")
    if (history.isEmpty()) Text("No signed-off cases yet")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(history) { Card(Modifier.fillMaxWidth()) { Text("${it.caseId}: ${it.triage.level}", Modifier.padding(12.dp)) } }
    }
}

package com.deepsight

import android.app.Application
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepsight.ai.AiStatus
import com.deepsight.ai.CaseReportText
import com.deepsight.ai.ReportService
import com.deepsight.ai.ReportSource
import com.deepsight.ai.ReportWriter
import com.deepsight.capture.CaseStore
import com.deepsight.capture.FieldImage
import com.deepsight.data.CaseDb
import com.deepsight.data.CaseEntity
import com.deepsight.data.FieldEntity
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.result.SignOff
import com.deepsight.result.signOff
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the user is. The back stack lives in [AppViewModel], so it survives rotation. */
sealed interface Route {
    data object Home : Route
    data object Case : Route
    data object Result : Route
    data object History : Route
    data class SavedCase(val caseId: String) : Route
    data object About : Route
    data class Document(val title: String, val asset: String) : Route
}

/** A pack on the home screen; [ready] packs passed their golden tests on a phone (DemoPacks). */
data class PackItem(val manifest: PackManifest, val ready: Boolean)

data class CaseUiState(
    val pack: PackManifest,
    val caseId: String,
    val images: List<FieldImage> = emptyList(),
    val running: Boolean = false,
    /** (field being analysed, total) while [running]. */
    val progress: Pair<Int, Int>? = null,
    val error: String? = null,
)

sealed interface ReportUiState {
    /** Gemma is streaming; [text] is the raw text so far. */
    data class Writing(val text: String) : ReportUiState
    data class Done(val report: CaseReportText) : ReportUiState
}

data class ResultUiState(
    val pack: PackManifest,
    val run: CaseRun,
    val images: Map<String, File>,
    val report: ReportUiState,
    val signOff: SignOff? = null,
)

data class HistoryItem(
    val caseId: String,
    val packName: String,
    val level: TriageLevel?,
    val signedAt: Long?,
    val signedBy: String?,
    val decision: String?,
    val classificationOnly: Boolean = false,
)

data class SavedCaseUiState(
    val packName: String,
    val positiveLabel: String?,
    val case: CaseResult,
    val fields: List<FieldResult>,
    val images: Map<String, File>,
    val report: CaseReportText?,
    val signOff: SignOff?,
    val classificationOnly: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val runner = CaseRunner.get(app)
    private val narrator = ReportService.narrator(app)
    private val writer = ReportWriter(narrator)
    private val dao = CaseDb.get(app).dao()
    private val store = CaseStore(app.filesDir.resolve("cases"))

    private val _stack = MutableStateFlow<List<Route>>(listOf(Route.Home))
    val stack: StateFlow<List<Route>> = _stack.asStateFlow()

    private val _packs = MutableStateFlow<List<PackItem>?>(null)
    val packs: StateFlow<List<PackItem>?> = _packs.asStateFlow()

    val aiStatus: StateFlow<AiStatus> = narrator.status

    private val _case = MutableStateFlow<CaseUiState?>(null)
    val case: StateFlow<CaseUiState?> = _case.asStateFlow()

    private val _result = MutableStateFlow<ResultUiState?>(null)
    val result: StateFlow<ResultUiState?> = _result.asStateFlow()

    private val _saved = MutableStateFlow<SavedCaseUiState?>(null)
    val saved: StateFlow<SavedCaseUiState?> = _saved.asStateFlow()

    val history: StateFlow<List<HistoryItem>> = combine(dao.history(), _packs) { rows, packs ->
        val manifests = packs.orEmpty().associate { it.manifest.id to it.manifest }
        rows.map { row ->
            val manifest = manifests[row.packId]
            HistoryItem(
                caseId = row.caseId, packName = manifest?.displayName ?: row.packId,
                level = row.caseResultJson?.let { runCatching { Contracts.parseCaseResult(it).triage.level }.getOrNull() },
                signedAt = row.signedAt, signedBy = row.signedBy, decision = row.decision,
                classificationOnly = manifest?.triage?.rules?.all { it.level == TriageLevel.NEEDS_EXPERT } == true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var analysis: Job? = null
    private var reporting: Job? = null

    init {
        viewModelScope.launch { _packs.value = runner.packs().map { PackItem(it, DemoPacks.isReady(it.id)) } }
    }

    // Navigation

    fun open(route: Route) = _stack.update { it + route }

    /** Returns false at the root, so the activity can close. */
    fun back(): Boolean {
        val stack = _stack.value
        if (stack.size <= 1) return false
        when (stack.last()) {
            Route.Case -> analysis?.cancel()
            Route.Result -> reporting?.cancel()
            else -> Unit
        }
        _stack.value = stack.dropLast(1)
        return true
    }

    // Case

    fun startCase(pack: PackManifest) {
        _case.value = CaseUiState(pack, caseId = "case-${System.currentTimeMillis()}")
        open(Route.Case)
    }

    /** Re-reads the case directory: after import, capture or delete, and when the case screen comes back to the foreground. */
    fun refreshImages() = _case.update { c -> c?.copy(images = store.fields(c.caseId)) }

    fun importImage(uri: Uri) {
        val c = _case.value ?: return
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val ok = withContext(Dispatchers.IO) {
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "jpg"
                resolver.openInputStream(uri)?.let { store.import(c.caseId, it, ext) } != null // bytes copied unchanged
            }
            refreshImages()
            if (!ok) _case.update { it?.copy(error = "Could not read that image.") }
        }
    }

    /** Where the camera writes the next field. */
    fun captureFile(): File? = _case.value?.let { store.nextFile(it.caseId, "jpg") }

    fun onCaptured() {
        refreshImages()
        _case.update { it?.copy(error = null) }
    }

    fun deleteImage(image: FieldImage) {
        image.file.delete()
        refreshImages()
    }

    fun analyse() {
        val c = _case.value ?: return
        if (c.images.isEmpty() || c.running) return
        analysis = viewModelScope.launch {
            _case.update { it?.copy(running = true, error = null, progress = 0 to c.images.size) }
            // Only the case still on screen may show its result: the user may have backed out.
            fun current() = _case.value?.caseId == c.caseId && _stack.value.last() == Route.Case
            try {
                val run = runner.run(c.pack.id, c.caseId, c.images) { done, total -> _case.update { it?.copy(progress = done to total) } }
                if (current()) {
                    val images = store.fields(c.caseId).associate { fieldId(c.caseId, it.index) to it.file }
                    _result.value = ResultUiState(c.pack, run, images, ReportUiState.Writing(""))
                    open(Route.Result)
                    if (!c.pack.triage.rules.all { it.level == TriageLevel.NEEDS_EXPERT }) writeReport(c.pack, run)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "case ${c.caseId} failed", e)
                if (current()) _case.update { it?.copy(error = "Analysis failed: ${e.message}") }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "case ${c.caseId} ran out of memory", e)
                if (current()) _case.update { it?.copy(error = "Analysis failed: not enough memory for these images.") }
            } finally {
                _case.update { if (it?.caseId == c.caseId) it.copy(running = false, progress = null) else it }
            }
        }
    }

    private fun writeReport(pack: PackManifest, run: CaseRun) {
        reporting?.cancel()
        reporting = viewModelScope.launch {
            val report = writer.write(run.case, run.fields, pack.displayName) { piece ->
                _result.update { r ->
                    val writing = r?.report as? ReportUiState.Writing
                    if (r?.run === run && writing != null) r.copy(report = ReportUiState.Writing(writing.text + piece)) else r
                }
            }
            Log.i(TAG, "report for ${run.case.caseId}: ${report.source}${report.note?.let { " ($it)" } ?: ""}")
            _result.update { r -> if (r?.run === run) r.copy(report = ReportUiState.Done(report)) else r }
        }
    }

    /** The rejected image goes, so the next run doesn't reject it again; capture lives on the case screen. */
    fun recapture(fieldId: String) {
        _result.value?.images?.get(fieldId)?.delete()
        reporting?.cancel()
        _stack.update { s -> if (s.lastOrNull() == Route.Result) s.dropLast(1) else s }
        refreshImages()
    }

    fun signOff(signOff: SignOff) {
        val r = _result.value ?: return
        val report = (r.report as? ReportUiState.Done)?.report
        _result.update { it?.copy(signOff = signOff) }
        viewModelScope.launch { save(r, signOff, report) }
        _stack.value = listOf(Route.Home, Route.History)
    }

    /** The case, its fields (each with its image file), the case_result JSON as the contract wrote it, and the report seen. */
    private suspend fun save(r: ResultUiState, signOff: SignOff, report: CaseReportText?) {
        val case = r.run.case
        val rows = r.run.fields.map { f -> FieldEntity(f.fieldId, case.caseId, r.images[f.fieldId]?.path, Contracts.encode(f)) }
        val entity = CaseEntity(
            case.caseId, case.packId, signOff.signedAt, Contracts.encode(case),
            reportText = report?.text, reportSource = report?.source?.name?.lowercase(),
        )
        dao.upsert(signOff.applyTo(entity), rows)
    }

    // History

    fun openSaved(caseId: String) {
        _saved.value = null
        open(Route.SavedCase(caseId))
        viewModelScope.launch {
            val row = dao.caseById(caseId) ?: return@launch
            val case = row.caseResultJson?.let(Contracts::parseCaseResult) ?: return@launch
            val fieldRows = dao.fields(caseId)
            val pack = _packs.value.orEmpty().firstOrNull { it.manifest.id == row.packId }?.manifest
            _saved.value = SavedCaseUiState(
                packName = pack?.displayName ?: row.packId,
                positiveLabel = pack?.output?.imageScoreLabel,
                case = case,
                fields = fieldRows.map { Contracts.parseFieldResult(it.fieldResultJson) },
                images = fieldRows.mapNotNull { f -> f.imagePath?.let { f.fieldId to File(it) } }.toMap(),
                report = row.reportText?.let { CaseReportText(it, if (row.reportSource == "gemma") ReportSource.GEMMA else ReportSource.TEMPLATE) },
                signOff = row.signOff(),
                classificationOnly = pack?.triage?.rules?.all { it.level == TriageLevel.NEEDS_EXPERT } == true,
            )
        }
    }

    private companion object {
        const val TAG = "DeepSight"
    }
}

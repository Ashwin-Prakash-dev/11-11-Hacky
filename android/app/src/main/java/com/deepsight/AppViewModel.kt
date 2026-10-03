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
import com.deepsight.data.CaseStatus
import com.deepsight.data.create
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.profiles.Patient
import com.deepsight.profiles.PatientProfile
import com.deepsight.profiles.Sex
import com.deepsight.profiles.profilesOf
import com.deepsight.result.SignOff
import com.deepsight.result.sign
import com.deepsight.result.signOff
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    data object Profiles : Route
    /** Before a case: choose the patient the batch belongs to, or add one. */
    data object PickPatient : Route
    data object NewPatient : Route
    data class Document(val title: String, val asset: String) : Route
}

/** A pack on the home screen; [ready] packs passed their golden tests on a phone (DemoPacks). */
data class PackItem(val manifest: PackManifest, val ready: Boolean)

data class CaseUiState(
    val pack: PackManifest,
    val caseId: String,
    val images: List<FieldImage> = emptyList(),
    /** Submitted to the queue and not finished: waiting or analysing. */
    val running: Boolean = false,
    /** (field being analysed, total) while the queue runs this case. */
    val progress: Pair<Int, Int>? = null,
    val error: String? = null,
    val patient: Patient? = null,
    /** Batches queued ahead of this one (the running one included) while it waits. */
    val ahead: Int? = null,
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
    /** Only from the case screen: Recapture goes back there to capture again. Not for a result opened from History. */
    val canRecapture: Boolean = true,
)

data class HistoryItem(
    val caseId: String, val packName: String, val level: TriageLevel?, val signedAt: Long?, val signedBy: String?, val decision: String?,
    val status: CaseStatus,
)

data class SavedCaseUiState(
    val packName: String,
    val positiveLabel: String?,
    val case: CaseResult,
    val fields: List<FieldResult>,
    val images: Map<String, File>,
    val report: CaseReportText?,
    val signOff: SignOff?,
    val analysedAt: Long?,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val runner = CaseRunner.get(app)
    private val narrator = ReportService.narrator(app)
    private val writer = ReportWriter(narrator)
    private val dao = CaseDb.get(app).dao()
    private val patientDao = CaseDb.get(app).patientDao()
    private val queue = CaseQueue.get(app)
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
        val names = packs.orEmpty().associate { it.manifest.id to it.manifest.displayName }
        rows.map { row ->
            HistoryItem(
                caseId = row.caseId, packName = names[row.packId] ?: row.packId,
                level = row.caseResultJson?.let { runCatching { Contracts.parseCaseResult(it).triage.level }.getOrNull() },
                signedAt = row.signedAt, signedBy = row.signedBy, decision = row.decision, status = row.status,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val profiles: StateFlow<List<PatientProfile>> = combine(patientDao.all(), dao.patientCases()) { patients, cases ->
        profilesOf(patients, cases, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _patientError = MutableStateFlow<String?>(null)
    val patientError: StateFlow<String?> = _patientError.asStateFlow()

    /** The test chosen on Home, waiting for its patient. */
    private var pendingPack: PackManifest? = null
    private var reporting: Job? = null

    init {
        viewModelScope.launch { _packs.value = runner.packs().map { PackItem(it, DemoPacks.isReady(it.id)) } }
        viewModelScope.launch {
            queue.state.collect { q ->
                _case.update { c ->
                    if (c == null || !c.running) return@update c
                    val waiting = q.queued.indexOf(c.caseId)
                    when {
                        q.running == c.caseId -> c.copy(progress = q.progress ?: (0 to c.images.size), ahead = null)
                        waiting >= 0 -> c.copy(progress = null, ahead = waiting + if (q.running != null) 1 else 0)
                        else -> c
                    }
                }
            }
        }
    }

    // Navigation

    fun open(route: Route) = _stack.update { it + route }

    /** Returns false at the root, so the activity can close. */
    fun back(): Boolean {
        val stack = _stack.value
        if (stack.size <= 1) return false
        if (stack.last() == Route.Result) reporting?.cancel() // a queued or running analysis carries on
        _stack.value = stack.dropLast(1)
        return true
    }

    // Case

    /** Every case belongs to a patient: pick one (or add one) first. */
    fun startCase(pack: PackManifest) {
        pendingPack = pack
        _patientError.value = null
        open(Route.PickPatient)
    }

    fun selectPatient(uid: String) {
        viewModelScope.launch { patientDao.byUid(uid)?.let(::caseFor) }
    }

    /** Patient validates the form; its message is shown on the form. A new patient goes straight into the case. */
    fun createPatient(name: String, dob: String, sex: Sex) {
        viewModelScope.launch {
            try {
                caseFor(patientDao.create(name, dob, sex, System.currentTimeMillis()))
                _patientError.value = null
            } catch (e: IllegalArgumentException) {
                _patientError.value = e.message
            }
        }
    }

    private fun caseFor(patient: Patient) {
        val pack = pendingPack ?: return
        _case.value = CaseUiState(pack, caseId = "case-${System.currentTimeMillis()}", patient = patient)
        _stack.update { s -> s.filterNot { it == Route.NewPatient } + Route.Case }
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

    /** Hands the batch to the queue: the technician can back out and start the next patient while it waits or runs. */
    fun analyse() {
        val c = _case.value ?: return
        if (c.images.isEmpty() || c.running) return
        _case.update { it?.copy(running = true, error = null, progress = null, ahead = null) }
        viewModelScope.launch {
            queue.submit(c.patient?.uid, c.pack.id, c.caseId)
            val row = dao.observe(c.caseId).first { it?.status == CaseStatus.DONE || it?.status == CaseStatus.FAILED }!!
            _case.update { if (it?.caseId == c.caseId) it.copy(running = false, progress = null, ahead = null) else it }
            // Only the case still on screen may show its result: the user may have backed out.
            if (_case.value?.caseId != c.caseId || _stack.value.last() != Route.Case) return@launch
            if (row.status == CaseStatus.FAILED) _case.update { it?.copy(error = "Analysis failed: ${row.error}") } else showResult(c.caseId, canRecapture = true)
        }
    }

    /** The result as the queue stored it, ready for review and sign-off. */
    private suspend fun showResult(caseId: String, canRecapture: Boolean) {
        val row = dao.caseById(caseId) ?: return
        val case = row.caseResultJson?.let(Contracts::parseCaseResult) ?: return
        val pack = _packs.value.orEmpty().firstOrNull { it.manifest.id == row.packId }?.manifest ?: return
        val fieldRows = dao.fields(caseId).associateBy { it.fieldId }
        val fields = case.fieldIds.mapNotNull { fieldRows[it] } // the run's order; field_id sorts field_10 before field_2
        val run = CaseRun(fields.map { Contracts.parseFieldResult(it.fieldResultJson) }, case, row.analysedAt ?: 0L)
        val images = fields.mapNotNull { f -> f.imagePath?.let { f.fieldId to File(it) } }.toMap()
        _result.value = ResultUiState(pack, run, images, ReportUiState.Writing(""), canRecapture = canRecapture)
        open(Route.Result)
        writeReport(pack, run)
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
        // The queue already stored the case, its fields and case_result; signing adds the sign-off and the report seen.
        viewModelScope.launch { dao.sign(signOff, report?.text, report?.source?.name?.lowercase()) }
        _stack.value = listOf(Route.Home, Route.History)
    }

    // History

    /** A signed case opens read-only; a finished, unsigned one opens for review and sign-off; one still queued doesn't open. */
    fun openSaved(caseId: String) {
        val status = history.value.firstOrNull { it.caseId == caseId }?.status
        if (status == CaseStatus.DONE) {
            viewModelScope.launch { showResult(caseId, canRecapture = false) } // no case screen behind it to capture on
            return
        }
        if (status != CaseStatus.SIGNED) return
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
                analysedAt = row.analysedAt,
            )
        }
    }

    private companion object {
        const val TAG = "DeepSight"
    }
}

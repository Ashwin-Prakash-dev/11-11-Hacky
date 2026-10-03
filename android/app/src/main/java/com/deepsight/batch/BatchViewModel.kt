package com.deepsight.batch

import android.app.Application
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepsight.CaseQueue
import com.deepsight.CaseRunner
import com.deepsight.DemoPacks
import com.deepsight.capture.CaseStore
import com.deepsight.data.CaseDb
import com.deepsight.engine.contract.PackManifest
import com.deepsight.profiles.Patient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The batch being prepared on the Batch tab: images picked from the gallery, allocated to modules by the [router], checked
 * and changed by a person, then submitted to the queue as one case per module. Holds no analysis state: the queue does.
 */
class BatchViewModel(app: Application) : AndroidViewModel(app) {
    private val runner = CaseRunner.get(app)
    private val queue = CaseQueue.get(app)
    private val store = CaseStore(app.filesDir.resolve("cases"))
    private val staging = File(app.filesDir, "batch-staging")

    /** PLACEHOLDER router: random. Replace with the trained router (#22) when it exists. */
    var router: FieldRouter = RandomFieldRouter()

    private val _draft = MutableStateFlow(BatchDraft())
    val draft: StateFlow<BatchDraft> = _draft.asStateFlow()

    private val _packs = MutableStateFlow<List<PackManifest>>(emptyList())
    /** The modules an image can be allocated to: packs that pass the demo gate on a phone. */
    val packs: StateFlow<List<PackManifest>> = _packs.asStateFlow()

    val patients: StateFlow<List<Patient>> = CaseDb.get(app).patientDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { _packs.value = runner.packs().filter { DemoPacks.isReady(it.id) } }
    }

    /** Copies the picked images into app storage (bytes unchanged), then lets the router suggest a module for each. */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _error.value = null
            val resolver = getApplication<Application>().contentResolver
            val files = withContext(Dispatchers.IO) {
                staging.mkdirs()
                uris.mapIndexedNotNull { i, uri ->
                    runCatching {
                        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "jpg"
                        File(staging, "img_${System.currentTimeMillis()}_$i.$ext").also { file ->
                            (resolver.openInputStream(uri) ?: error("cannot open $uri")).use { input -> file.outputStream().use { input.copyTo(it) } }
                        }
                    }.onFailure { Log.w(TAG, "could not read $uri", it) }.getOrNull()
                }
            }
            if (files.size < uris.size) _error.value = "${uris.size - files.size} image(s) could not be read and were skipped."
            // The packs load once at start-up; wait for them so the router has modules to choose from.
            val ids = packs.value.map { it.id }.ifEmpty { runner.packs().filter { DemoPacks.isReady(it.id) }.also { _packs.value = it }.map { it.id } }
            _draft.update { it.add(files, router, ids) }
        }
    }

    fun reassign(imageId: Int, packId: String?) = _draft.update { it.reassign(imageId, packId) }

    fun remove(imageId: Int) {
        _draft.value.images.firstOrNull { it.id == imageId }?.file?.delete()
        _draft.update { it.remove(imageId) }
    }

    fun setPatient(uid: String?) = _draft.update { it.withPatient(uid) }

    fun setVerified(checked: Boolean) = _draft.update { it.setVerified(checked) }

    fun clear() {
        staging.deleteRecursively()
        _draft.value = BatchDraft()
        _error.value = null
    }

    /** Sends the verified allocation to the queue, one case per module, together. Calls [onDone] with the cases made. */
    fun submit(onDone: (List<SubmittedBatch>) -> Unit) {
        val draft = _draft.value
        if (!draft.canSubmit || _busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val submitted = withContext(Dispatchers.IO) {
                    BatchSubmitter(store, { uid, pack, caseId -> queue.submit(uid, pack, caseId) }).submit(draft, packs.value.map { it.id })
                }
                clear()
                onDone(submitted)
            } catch (e: Exception) {
                Log.e(TAG, "batch submit failed", e)
                _error.value = "Could not submit the batch: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "DeepSight"
    }
}

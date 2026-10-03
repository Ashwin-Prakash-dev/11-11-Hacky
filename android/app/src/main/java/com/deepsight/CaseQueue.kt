package com.deepsight

import android.content.Context
import android.util.Log
import com.deepsight.capture.CaseStore
import com.deepsight.capture.FieldImage
import com.deepsight.data.CaseDao
import com.deepsight.data.CaseDb
import com.deepsight.data.CaseEntity
import com.deepsight.data.CaseStatus
import com.deepsight.data.FieldEntity
import com.deepsight.engine.contract.Contracts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The batch running, its progress (fields started, total), and the batches waiting behind it in order. */
data class QueueState(val running: String? = null, val progress: Pair<Int, Int>? = null, val queued: List<String> = emptyList())

/** [CaseRunner.run]'s shape, so tests can pass a fake. */
typealias RunCase = suspend (packId: String, caseId: String, images: List<FieldImage>, onProgress: (Int, Int) -> Unit) -> CaseRun

/**
 * Batches (one patient's fields for one test) run one at a time, first in first out, in a process-level scope: leaving
 * the case screen doesn't cancel them. The case row's status is the durable copy of the queue: a new queue first re-runs
 * what a killed process left QUEUED or RUNNING. Order comes from [pending], not from CaseRunner's lock.
 */
class CaseQueue(
    private val dao: CaseDao,
    private val store: CaseStore,
    private val run: RunCase,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val pending = Channel<String>(Channel.UNLIMITED)
    private val recovered = CompletableDeferred<Unit>()
    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    init {
        scope.launch {
            dao.unfinished().forEach { enqueue(it.caseId) }
            recovered.complete(Unit)
            for (caseId in pending) process(caseId)
        }
    }

    /** Stores the case as QUEUED (re-queues a stored one: a recapture runs the whole batch again) behind every earlier batch. */
    suspend fun submit(patientUid: String?, packId: String, caseId: String) {
        recovered.await() // what a killed process left goes first
        dao.enqueue(CaseEntity(caseId, packId, createdAt = clock(), patientUid = patientUid, status = CaseStatus.QUEUED))
        enqueue(caseId)
    }

    private fun enqueue(caseId: String) {
        _state.update { it.copy(queued = it.queued + caseId) }
        pending.trySend(caseId) // never fails: UNLIMITED and never closed
    }

    private suspend fun process(caseId: String) {
        _state.update { it.copy(running = caseId, progress = null, queued = it.queued - caseId) }
        try {
            val row = dao.caseById(caseId)
            // Queued twice (recovery racing a submit, or a recapture while waiting) and the first pass already ran it.
            if (row == null || row.status != CaseStatus.QUEUED && row.status != CaseStatus.RUNNING) return
            dao.setStatus(caseId, CaseStatus.RUNNING)
            val images = store.fields(caseId)
            val result = run(row.packId, caseId, images) { done, total -> _state.update { it.copy(progress = done to total) } }
            val paths = images.associate { fieldId(caseId, it.index) to it.file.path }
            val fields = result.fields.map { FieldEntity(it.fieldId, caseId, paths[it.fieldId], Contracts.encode(it)) }
            dao.finish(caseId, Contracts.encode(result.case), result.analysedAt, fields)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) { // OutOfMemoryError too: the next batch must still run
            Log.e(TAG, "case $caseId failed", e)
            dao.setStatus(caseId, CaseStatus.FAILED, e.message ?: e.javaClass.simpleName)
        } finally {
            _state.update { it.copy(running = null, progress = null) }
        }
    }

    companion object {
        private const val TAG = "DeepSight"

        @Volatile private var instance: CaseQueue? = null

        /** One queue per process, like [CaseRunner.get]; creating it starts the consumer and the restart recovery. */
        fun get(context: Context): CaseQueue = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                CaseQueue(CaseDb.get(app).dao(), CaseStore(app.filesDir.resolve("cases")), CaseRunner.get(app)::run)
            }.also { instance = it }
        }
    }
}

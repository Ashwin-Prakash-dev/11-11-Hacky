package com.deepsight.ai

import android.content.Context
import android.util.Log
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.FieldResult
import com.deepsight.report.checkNarrative
import com.deepsight.report.cleanNarrative
import com.deepsight.report.gemma.GemmaRunner
import com.deepsight.report.gemma.GemmaStopped
import com.deepsight.report.gemmaPrompt
import com.deepsight.report.templateReport
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Whether the on-device report model can narrate. */
sealed interface AiStatus {
    data class Missing(val path: String) : AiStatus
    data object Loading : AiStatus
    data object Ready : AiStatus
    data class Failed(val message: String) : AiStatus
}

enum class ReportSource { GEMMA, TEMPLATE }

/** The report shown and saved with the sign-off. [note] says why the template was used. */
data class CaseReportText(val text: String, val source: ReportSource, val note: String? = null)

/** Narrates a prompt, streaming pieces to [onText]. Throws on failure; honours coroutine cancellation. */
interface Narrator {
    val status: StateFlow<AiStatus>
    suspend fun narrate(prompt: String, onText: (String) -> Unit): String
}

/**
 * Gemma words the case; the triage stays the engine's (AGENTS.md). Anything other than a narration that passes
 * [checkNarrative] gives the deterministic template, with the reason (docs/architecture.md, Report).
 */
class ReportWriter(private val narrator: Narrator, private val waitForModelMs: Long = 45_000) {
    suspend fun write(case: CaseResult, fields: List<FieldResult>, testName: String, onText: (String) -> Unit): CaseReportText {
        val template = templateReport(case, fields, testName)
        fun fallback(why: String) = CaseReportText(template, ReportSource.TEMPLATE, "$why, so the template report is shown.")
        val status = withTimeoutOrNull(waitForModelMs) { narrator.status.first { it != AiStatus.Loading } } ?: AiStatus.Loading
        when (status) {
            is AiStatus.Missing -> return fallback("Gemma is not installed on this phone")
            is AiStatus.Failed -> return fallback("Gemma could not start (${status.message})")
            AiStatus.Loading -> return fallback("Gemma is still loading")
            AiStatus.Ready -> Unit
        }
        val raw = try {
            narrator.narrate(gemmaPrompt(case, fields, testName), onText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GemmaStopped) {
            return fallback(if (e.timedOut) "The AI summary took too long" else "The AI summary was stopped")
        } catch (e: Exception) {
            return fallback("The AI summary failed (${e.message})")
        }
        val text = cleanNarrative(raw)
        val check = checkNarrative(text, case.triage.level)
        return if (check.ok) CaseReportText(text, ReportSource.GEMMA) else fallback("The AI summary ${check.reason}")
    }
}

/** Gemma 4 E2B on LiteRT-LM, GPU with multi-token prediction: the fastest setup measured in spike S3. */
class GemmaNarrator(private val model: File, private val cacheDir: File, private val scope: CoroutineScope) : Narrator {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "gemma") }.asCoroutineDispatcher() // GemmaRunner is not thread-safe
    private val _status = MutableStateFlow<AiStatus>(if (model.isFile) AiStatus.Loading else AiStatus.Missing(model.path))
    override val status: StateFlow<AiStatus> = _status.asStateFlow()
    private var runner: GemmaRunner? = null

    /** Loads the model in the background (about 12 s warm, 27 s on the first launch after install, S3). */
    fun preload() {
        if (!model.isFile) return
        scope.launch(thread) {
            val started = System.nanoTime()
            runCatching { GemmaRunner(model.path, GemmaRunner.Backend.GPU, cacheDir.path, speculativeDecoding = true).also { it.load() } }
                .onSuccess {
                    runner = it
                    _status.value = AiStatus.Ready
                    Log.i(TAG, "Gemma ready in ${(System.nanoTime() - started) / 1_000_000} ms")
                }
                .onFailure {
                    Log.e(TAG, "Gemma failed to load", it)
                    _status.value = AiStatus.Failed(it.message ?: it.javaClass.simpleName)
                }
        }
    }

    override suspend fun narrate(prompt: String, onText: (String) -> Unit): String = withContext(thread) {
        val gemma = checkNotNull(runner) { "Gemma is not loaded" }
        val job = coroutineContext.job
        gemma.generate(prompt, MAX_OUTPUT_TOKENS, TIMEOUT_MS, isCancelled = { !job.isActive }, onText = onText).text
    }

    private companion object {
        const val TAG = "DeepSightReport"
        const val MAX_OUTPUT_TOKENS = 160
        const val TIMEOUT_MS = 30_000L
    }
}

/** One report model per process, loaded at app start; it stays resident with the ONNX pack (S3 measured both together). */
object ReportService {
    const val MODEL_FILE = "gemma-4-E2B-it.litertlm"

    @Volatile private var narrator: GemmaNarrator? = null

    fun narrator(context: Context): GemmaNarrator = narrator ?: synchronized(this) {
        narrator ?: GemmaNarrator(
            model = File(context.applicationContext.getExternalFilesDir(null), MODEL_FILE),
            cacheDir = context.applicationContext.cacheDir,
            scope = CoroutineScope(SupervisorJob()),
        ).also { it.preload(); narrator = it }
    }
}

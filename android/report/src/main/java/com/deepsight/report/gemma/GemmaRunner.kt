package com.deepsight.report.gemma

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.util.concurrent.CountDownLatch
import com.google.ai.edge.litertlm.Backend as LiteRtBackend

/** LiteRT-LM's own timing for the last message (Conversation.getBenchmarkInfo). */
data class GemmaStats(
    val initSeconds: Double,
    val timeToFirstTokenSeconds: Double,
    val prefillTokens: Int,
    val decodeTokens: Int,
    val prefillTokensPerSecond: Double,
    val decodeTokensPerSecond: Double,
)

/** One generated text: [firstTextMs] and [totalMs] are wall-clock times from sending the prompt. */
data class GemmaResult(val text: String, val chunks: Int, val firstTextMs: Long, val totalMs: Long, val stats: GemmaStats?)

/**
 * A Gemma model (.litertlm) on LiteRT-LM. Gemma only narrates the report: it never decides or changes triage
 * (AGENTS.md). The model file is too large for the APK, so [modelPath] points into the app's storage.
 *
 * [load] takes seconds to tens of seconds; call it and [generate] off the main thread. Not thread-safe.
 * [maxNumTokens] bounds the context (prompt + output), which bounds the KV cache's memory.
 * [speculativeDecoding] is multi-token prediction: null uses the model's default.
 */
class GemmaRunner(
    private val modelPath: String,
    private val backend: Backend,
    private val cacheDir: String? = null,
    private val maxNumTokens: Int = DEFAULT_MAX_NUM_TOKENS,
    private val speculativeDecoding: Boolean? = null,
) : AutoCloseable {
    enum class Backend { CPU, GPU }

    private var engine: Engine? = null

    /** Loads the model; returns the wall-clock milliseconds it took. */
    @OptIn(ExperimentalApi::class)
    fun load(): Long {
        check(engine == null) { "already loaded" }
        val start = System.nanoTime()
        // Both flags are read when an Engine is created, so set them right before.
        ExperimentalFlags.enableBenchmark = true
        ExperimentalFlags.enableSpeculativeDecoding = speculativeDecoding
        val config = EngineConfig(
            modelPath = modelPath,
            backend = when (backend) {
                Backend.CPU -> LiteRtBackend.CPU()
                Backend.GPU -> LiteRtBackend.GPU()
            },
            maxNumTokens = maxNumTokens,
            cacheDir = cacheDir,
        )
        engine = Engine(config).also { it.initialize() }
        return (System.nanoTime() - start) / 1_000_000
    }

    /**
     * Generates a reply to [prompt] in a fresh conversation, calling [onText] with each streamed piece of text.
     * Blocks until the model finishes or [maxOutputTokens] is reached.
     */
    @OptIn(ExperimentalApi::class)
    fun generate(prompt: String, maxOutputTokens: Int, onText: (String) -> Unit): GemmaResult {
        val loaded = checkNotNull(engine) { "call load() first" }
        loaded.createConversation().use { conversation ->
            val done = CountDownLatch(1)
            val text = StringBuilder()
            var chunks = 0
            var firstTextMs = -1L
            var failure: Throwable? = null
            val start = System.nanoTime()
            conversation.sendMessageAsync(
                prompt,
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val piece = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                        if (piece.isEmpty()) return
                        if (firstTextMs < 0) firstTextMs = (System.nanoTime() - start) / 1_000_000
                        chunks++
                        text.append(piece)
                        onText(piece)
                    }

                    override fun onDone() = done.countDown()

                    override fun onError(throwable: Throwable) {
                        failure = throwable
                        done.countDown()
                    }
                },
                maxOutputToken = maxOutputTokens,
            )
            done.await()
            failure?.let { throw it }
            val totalMs = (System.nanoTime() - start) / 1_000_000
            val stats = runCatching { conversation.getBenchmarkInfo() }.getOrNull()?.let {
                GemmaStats(
                    initSeconds = it.initTimeInSecond,
                    timeToFirstTokenSeconds = it.timeToFirstTokenInSecond,
                    prefillTokens = it.lastPrefillTokenCount,
                    decodeTokens = it.lastDecodeTokenCount,
                    prefillTokensPerSecond = it.lastPrefillTokensPerSecond,
                    decodeTokensPerSecond = it.lastDecodeTokensPerSecond,
                )
            }
            return GemmaResult(text.toString(), chunks, firstTextMs, totalMs, stats)
        }
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    companion object {
        /** Prompt plus output for a short report; far below the model's 32K, to keep the KV cache small. */
        const val DEFAULT_MAX_NUM_TOKENS = 2048
    }
}

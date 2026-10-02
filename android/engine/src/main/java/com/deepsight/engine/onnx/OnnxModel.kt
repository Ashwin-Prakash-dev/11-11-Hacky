package com.deepsight.engine.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/** One ONNX model on ONNX Runtime with a single float input. Not thread-safe: one instance per pack. */
class OnnxModel(
    modelBytes: ByteArray,
    val accelerator: Accelerator = Accelerator.XNNPACK,
    threads: Int = 4,
) : AutoCloseable {
    enum class Accelerator { CPU, XNNPACK }

    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        when (accelerator) {
            Accelerator.CPU -> setIntraOpNumThreads(threads)
            Accelerator.XNNPACK -> {
                // ORT's XNNPACK guidance: XNNPACK owns the worker threads, ORT's own pool stays at 1 without spinning.
                addConfigEntry("session.intra_op.allow_spinning", "0")
                addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
                setIntraOpNumThreads(1)
            }
        }
    }
    private val session = env.createSession(modelBytes, options)
    private val inputName = session.inputNames.single()

    /** Runs one batch. [input] is row-major with [shape]; returns the first output, flattened. */
    fun run(input: FloatArray, shape: LongArray): FloatArray =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val output = (result.get(0) as OnnxTensor).floatBuffer
                FloatArray(output.remaining()).also { output.get(it) }
            }
        }

    override fun close() {
        session.close()
        options.close()
    }
}

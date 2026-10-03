package com.deepsight.batch

import java.io.File
import kotlin.random.Random

/**
 * Suggests which test module a field image belongs to, for [BatchDraft]. Only a suggestion: a person checks and may
 * change every allocation before anything is analysed, because a router can be wrong.
 *
 * The real implementation is the trained router (issue #22, `ml/train/train_router.py`); it will answer null for an
 * image that is no known test (its reject class). Until it exists, [RandomFieldRouter] stands in.
 */
fun interface FieldRouter {
    /** The id of one of [packIds], or null when the image is not recognised as any of them. */
    fun route(image: File, packIds: List<String>): String?
}

/**
 * PLACEHOLDER: picks a module at random and never looks at the image. It exists so the batch flow (upload, allocate,
 * verify, submit) can be built and tested before the router is trained. Its suggestions mean nothing.
 */
class RandomFieldRouter(private val random: Random = Random.Default) : FieldRouter {
    override fun route(image: File, packIds: List<String>): String? = packIds.randomOrNull(random)
}

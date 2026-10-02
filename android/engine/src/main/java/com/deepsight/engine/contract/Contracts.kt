package com.deepsight.engine.contract

import kotlinx.serialization.json.Json

/** JSON for the contracts in <repo>/contracts. Version 1.x only adds optional fields. */
object Contracts {
    const val VERSION = "1.0"

    // Rule ids the engine emits itself. Manifest rule ids can't contain '.', so they never collide.
    const val RULE_INSUFFICIENT_FIELDS = "engine.insufficient_fields"
    const val RULE_ROUTER_REJECT = "engine.router_reject"
    const val RULE_ROUTER_MISMATCH = "engine.router_mismatch"
    const val RULE_NO_MATCH = "engine.no_rule_matched"
    const val RULE_UNCERTAIN = "engine.uncertain"

    val json = Json {
        ignoreUnknownKeys = true // newer 1.x files may carry fields this build doesn't know
        encodeDefaults = true // always write the full result shape, nulls included
    }

    fun parseManifest(text: String): PackManifest = json.decodeFromString(PackManifest.serializer(), text)

    fun parseFieldResult(text: String): FieldResult = json.decodeFromString(FieldResult.serializer(), text)

    fun parseCaseResult(text: String): CaseResult = json.decodeFromString(CaseResult.serializer(), text)

    fun encode(result: FieldResult): String = json.encodeToString(FieldResult.serializer(), result)

    fun encode(result: CaseResult): String = json.encodeToString(CaseResult.serializer(), result)
}

package com.deepsight

import android.content.Context
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.PackManifest

/** Stand-in for Track C's engine: returns the contracts/examples JSON unchanged, whatever the input. */
class FakeEngine(context: Context) {
    private val assets = context.applicationContext.assets
    private fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }

    val tests: List<PackManifest> = listOf(Contracts.parseManifest(read("manifest.malaria_thin.json")))

    fun fields(): List<FieldResult> =
        listOf("field_result.malaria_thin.json", "field_result.rejected.json").map { Contracts.parseFieldResult(read(it)) }

    fun caseResult(): CaseResult = Contracts.parseCaseResult(read("case_result.malaria_thin.json"))
}

package com.deepsight.engine.contract

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses contracts/examples and writes Kotlin-encoded copies to build/contract-out for validate.py. */
class ContractExamplesTest {
    private val examples = File(checkNotNull(System.getProperty("contractsDir")) { "run through Gradle" }, "examples")
    private val outDir = File(checkNotNull(System.getProperty("contractOutDir")) { "run through Gradle" }).apply { mkdirs() }

    private fun read(name: String) = File(examples, name).readText()

    @Test
    fun exampleManifestHasNoProblems() {
        val manifest = Contracts.parseManifest(read("manifest.malaria_thin.json"))
        assertEquals("malaria_thin", manifest.id)
        assertEquals(emptyList<String>(), manifest.problems())
    }

    @Test
    fun problemsCatchUnknownRuleLabel() {
        val manifest = Contracts.parseManifest(read("manifest.malaria_thin.json"))
        val rule = manifest.triage.rules.first()
        val badRule = rule.copy(all = listOf(rule.all.first().copy(labels = listOf("parasite"))))
        val broken = manifest.copy(triage = manifest.triage.copy(rules = listOf(badRule)))
        assertTrue(broken.problems().toString(), broken.problems().any { "unknown label 'parasite'" in it })
    }

    @Test
    fun fieldResultsRoundTrip() {
        for (name in listOf("field_result.malaria_thin.json", "field_result.rejected.json")) {
            val parsed = Contracts.parseFieldResult(read(name))
            val encoded = Contracts.encode(parsed)
            assertEquals(name, parsed, Contracts.parseFieldResult(encoded))
            File(outDir, name).writeText(encoded)
        }
    }

    @Test
    fun caseResultRoundTrips() {
        val parsed = Contracts.parseCaseResult(read("case_result.malaria_thin.json"))
        assertEquals(TriageLevel.ABNORMAL_FLAG, parsed.triage.level)
        val encoded = Contracts.encode(parsed)
        assertEquals(parsed, Contracts.parseCaseResult(encoded))
        File(outDir, "case_result.malaria_thin.json").writeText(encoded)
    }
}

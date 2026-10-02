package com.deepsight.engine.pack

import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PackLoaderTest {
    private val examples = File(checkNotNull(System.getProperty("contractsDir")) { "run through Gradle" }, "examples")
    private val exampleManifest = File(examples, "manifest.malaria_thin.json").readText()
    private val modelBytes = "small test model".encodeToByteArray()

    @Test
    fun loadsExampleManifestAndModel() {
        val loader = loader(mapOf(packFile("malaria_thin", MANIFEST) to exampleManifest.encodeToByteArray(), packFile("malaria_thin", MODEL) to modelBytes))

        val pack = loader.load("malaria_thin")

        assertEquals("malaria_thin", pack.manifest.id)
        assertTrue(modelBytes.contentEquals(pack.modelBytes))
    }

    @Test
    fun rejectsManifestWithContractProblems() {
        val broken = exampleManifest.replace("\"contract_version\": \"1.0\"", "\"contract_version\": \"2.0\"")
        val loader = loader(mapOf(packFile("malaria_thin", MANIFEST) to broken.encodeToByteArray(), packFile("malaria_thin", MODEL) to modelBytes))

        val error = expectLoadFailure { loader.load("malaria_thin") }

        assertTrue(error.message.orEmpty(), "contract_version 2.0 is not 1.x" in error.message.orEmpty())
    }

    @Test
    fun verifiesModelSha256WhenPresent() {
        val validManifest = withSha256(exampleManifest, sha256(modelBytes))
        val invalidManifest = withSha256(exampleManifest, "0".repeat(64))

        assertTrue(modelBytes.contentEquals(loader(files(validManifest)).load("malaria_thin").modelBytes))
        val error = expectLoadFailure { loader(files(invalidManifest)).load("malaria_thin") }
        assertTrue(error.message.orEmpty(), "SHA-256 mismatch" in error.message.orEmpty())
    }

    @Test
    fun rejectsDirectoryAndManifestIdMismatch() {
        val loader = loader(mapOf(packFile("fungal", MANIFEST) to exampleManifest.encodeToByteArray(), packFile("fungal", MODEL) to modelBytes))

        val error = expectLoadFailure { loader.load("fungal") }

        assertTrue(error.message.orEmpty(), "does not match directory id 'fungal'" in error.message.orEmpty())
    }

    @Test
    fun rejectsUnsafeModelPath() {
        val broken = exampleManifest.replace("\"file\": \"model.onnx\"", "\"file\": \"../model.onnx\"")
        val loader = loader(mapOf(packFile("malaria_thin", MANIFEST) to broken.encodeToByteArray()))

        val error = expectLoadFailure { loader.load("malaria_thin") }

        assertTrue(error.message.orEmpty(), "unsafe model path" in error.message.orEmpty())
    }

    @Test
    fun discoveryReturnsOnlyRunnablePacksAndReportsRejections() {
        val alpha = exampleManifest.replace("malaria_thin", "alpha_pack").replace("Malaria (thin smear)", "Alpha")
        val zeta = exampleManifest.replace("malaria_thin", "zeta_pack").replace("Malaria (thin smear)", "Zeta")
        val broken = exampleManifest.replace("\"contract_version\": \"1.0\"", "\"contract_version\": \"2.0\"")
        val loader = loader(
            mapOf(
                packFile("zeta_pack", MANIFEST) to zeta.encodeToByteArray(),
                packFile("zeta_pack", MODEL) to modelBytes,
                packFile("broken_pack", MANIFEST) to broken.encodeToByteArray(),
                packFile("alpha_pack", MANIFEST) to alpha.encodeToByteArray(),
                packFile("alpha_pack", MODEL) to modelBytes,
            ),
        )

        val catalog = loader.discover()

        assertEquals(listOf("alpha_pack", "zeta_pack"), catalog.installed.map { it.id })
        assertEquals(listOf("broken_pack"), catalog.rejected.map { it.id })
        assertTrue(catalog.rejected.single().reason, "contract_version 2.0 is not 1.x" in catalog.rejected.single().reason)
    }

    @Test
    fun discoveryRejectsPackWhoseModelChecksumDoesNotMatch() {
        val manifest = withSha256(exampleManifest, "0".repeat(64))
        val loader = loader(files(manifest))

        val catalog = loader.discover()

        assertEquals(emptyList<String>(), catalog.installed.map { it.id })
        assertEquals(listOf("malaria_thin"), catalog.rejected.map { it.id })
        assertTrue(catalog.rejected.single().reason, "SHA-256 mismatch" in catalog.rejected.single().reason)
    }

    @Test
    fun missingModelIsReportedAsPackFailure() {
        val loader = loader(mapOf(packFile("malaria_thin", MANIFEST) to exampleManifest.encodeToByteArray()))

        val error = expectLoadFailure { loader.load("malaria_thin") }

        assertTrue(error.message.orEmpty(), "model.onnx" in error.message.orEmpty())
    }

    private fun files(manifest: String) = mapOf(
        packFile("malaria_thin", MANIFEST) to manifest.encodeToByteArray(),
        packFile("malaria_thin", MODEL) to modelBytes,
    )

    private fun loader(files: Map<String, ByteArray>, ids: List<String> = files.keys.mapNotNull(::packId).distinct()) =
        PackLoader(
            listPackIds = { ids },
            readBytes = { packId, relativePath ->
                val path = packFile(packId, relativePath)
                files[path] ?: throw FileNotFoundException(path)
            },
        )

    private fun packId(path: String): String? = path.removePrefix("packs/").substringBefore('/').takeIf { path.startsWith("packs/") }

    private fun packFile(id: String, name: String) = "packs/$id/$name"

    private fun withSha256(manifest: String, sha256: String) = manifest.replace("\"sha256\": null", "\"sha256\": \"$sha256\"")

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun expectLoadFailure(block: () -> Unit): PackLoadException {
        try {
            block()
            fail("expected PackLoadException")
        } catch (error: PackLoadException) {
            return error
        }
        error("unreachable")
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val MODEL = "model.onnx"
    }
}

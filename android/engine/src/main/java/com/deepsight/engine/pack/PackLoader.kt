package com.deepsight.engine.pack

import android.content.res.AssetManager
import com.deepsight.engine.contract.Compute
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.PackRuntime
import java.security.MessageDigest

/** A validated ONNX pack loaded from the app's packaged assets. */
data class LoadedPack(
    val manifest: PackManifest,
    val modelBytes: ByteArray,
)

data class RejectedPack(
    val id: String,
    val reason: String,
)

/** Packs safe to show in the picker, plus invalid asset directories for diagnostics. */
data class PackCatalog(
    val installed: List<PackManifest>,
    val rejected: List<RejectedPack>,
)

class PackLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Loads MVP ONNX packs from `packs/<id>/`.
 *
 * Production uses [fromAssets]. The function constructor keeps parsing and checksum validation
 * covered by local JVM tests without depending on Android's AssetManager implementation.
 */
class PackLoader(
    private val listPackIds: () -> List<String>,
    private val readBytes: (packId: String, relativePath: String) -> ByteArray,
) {
    /** Verifies each installed pack before exposing it to the test picker. */
    fun discover(): PackCatalog {
        val installed = mutableListOf<PackManifest>()
        val rejected = mutableListOf<RejectedPack>()
        for (packId in listPackIds().distinct().sorted()) {
            try {
                installed += load(packId).manifest
            } catch (error: PackLoadException) {
                rejected += RejectedPack(packId, error.message ?: "pack rejected")
            }
        }
        return PackCatalog(
            installed = installed.sortedWith(compareBy(PackManifest::displayName, PackManifest::id)),
            rejected = rejected,
        )
    }

    /** Loads and verifies the selected model. The caller owns any OnnxModel it creates from it. */
    @Throws(PackLoadException::class)
    fun load(packId: String): LoadedPack {
        val manifest = readManifest(packId)
        val modelFile = manifest.model.file
            ?: throw PackLoadException("Pack '$packId' has no model.file")
        val modelBytes = readPackFile(packId, modelFile, "model")
        manifest.model.sha256?.let { expected ->
            val actual = sha256(modelBytes)
            if (actual != expected) {
                throw PackLoadException("Pack '$packId' model SHA-256 mismatch: expected $expected, got $actual")
            }
        }
        return LoadedPack(manifest, modelBytes)
    }

    private fun readManifest(packId: String): PackManifest {
        requireSafeId(packId)
        val manifestBytes = readPackFile(packId, MANIFEST_FILE, "manifest")
        val manifest = try {
            Contracts.parseManifest(String(manifestBytes, Charsets.UTF_8))
        } catch (error: Exception) {
            throw PackLoadException("Pack '$packId' manifest is not valid JSON: ${error.message}", error)
        }
        val problems = manifest.problems()
        if (problems.isNotEmpty()) {
            throw PackLoadException("Pack '$packId' manifest problems: ${problems.joinToString("; ")}")
        }
        if (manifest.id != packId) {
            throw PackLoadException("Pack manifest id '${manifest.id}' does not match directory id '$packId'")
        }
        if (manifest.runtime != PackRuntime.ONNX || manifest.compute != Compute.PHONE) {
            throw PackLoadException("Pack '$packId' is not an on-phone ONNX pack")
        }
        val modelFile = manifest.model.file
            ?: throw PackLoadException("Pack '$packId' has no model.file")
        requireSafeRelativePath(packId, modelFile)
        return manifest
    }

    private fun readPackFile(packId: String, relativePath: String, kind: String): ByteArray {
        return try {
            readBytes(packId, relativePath)
        } catch (error: Exception) {
            throw PackLoadException("Pack '$packId' $kind file '$relativePath' could not be read", error)
        }
    }

    private fun requireSafeId(packId: String) {
        if (!PACK_ID.matches(packId)) throw PackLoadException("Unsafe pack id '$packId'")
    }

    private fun requireSafeRelativePath(packId: String, path: String) {
        val segments = path.split('/')
        if (path.startsWith('/') || segments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw PackLoadException("Pack '$packId' has unsafe model path '$path'")
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val PACKS_ROOT = "packs"
        private const val MANIFEST_FILE = "manifest.json"
        private val PACK_ID = Regex("[a-z][a-z0-9_]*")

        fun fromAssets(assets: AssetManager, packsRoot: String = PACKS_ROOT): PackLoader {
            val root = packsRoot.trim('/')
            fun assetPath(packId: String, relativePath: String) =
                listOf(root, packId, relativePath).filter(String::isNotEmpty).joinToString("/")
            return PackLoader(
                listPackIds = { assets.list(root)?.toList().orEmpty() },
                readBytes = { packId, relativePath ->
                    assets.open(assetPath(packId, relativePath)).use { it.readBytes() }
                },
            )
        }
    }
}

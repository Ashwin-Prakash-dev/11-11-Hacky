package com.deepsight.capture

import java.io.File
import java.io.InputStream

/** One field image stored in a case directory. */
data class FieldImage(val index: Int, val file: File)

/** Case images live at `<root>/<caseId>/field_<n>.<ext>`; listing the directory lists the fields. */
class CaseStore(private val root: File) {
    private val name = Regex("""field_(\d+)\.\w+""")

    fun fields(caseId: String): List<FieldImage> =
        File(root, caseId).listFiles().orEmpty()
            .mapNotNull { f -> name.matchEntire(f.name)?.let { FieldImage(it.groupValues[1].toInt(), f) } }
            .sortedBy { it.index }

    /** Reserves the next field path (file not created), e.g. for CameraX to write into. */
    fun nextFile(caseId: String, ext: String): File {
        val dir = File(root, caseId).apply { mkdirs() }
        val next = (fields(caseId).maxOfOrNull { it.index } ?: 0) + 1
        return File(dir, "field_$next.$ext")
    }

    /** Removes a case's folder and its images. An id that would point outside the store is ignored. */
    fun delete(caseId: String) {
        val dir = File(root, caseId).canonicalFile
        if (dir.parentFile == root.canonicalFile) dir.deleteRecursively()
    }

    /** Copies bytes as-is: no re-encode, so the quality gate sees the original pixels. */
    fun import(caseId: String, input: InputStream, ext: String): File =
        nextFile(caseId, ext).also { f -> input.use { i -> f.outputStream().use { i.copyTo(it) } } }
}

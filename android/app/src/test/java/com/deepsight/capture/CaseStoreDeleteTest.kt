package com.deepsight.capture

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseStoreDeleteTest {
    @Test
    fun deletingACaseRemovesItsImagesAndNothingElse() {
        val root = Files.createTempDirectory("cases").toFile()
        val store = CaseStore(root)
        store.import("c1", byteArrayOf(1).inputStream(), "png")
        store.import("c1", byteArrayOf(2).inputStream(), "jpg")
        store.import("c2", byteArrayOf(3).inputStream(), "png")

        store.delete("c1")

        assertFalse(File(root, "c1").exists())
        assertEquals(emptyList<FieldImage>(), store.fields("c1"))
        assertEquals(1, store.fields("c2").size)
    }

    @Test
    fun deletingACaseThatHasNoFolderIsHarmless() {
        val store = CaseStore(Files.createTempDirectory("cases").toFile())
        store.delete("never-existed")
        assertTrue(store.fields("never-existed").isEmpty())
    }

    @Test
    fun aCaseIdCannotReachOutsideTheStore() {
        val root = Files.createTempDirectory("cases").toFile()
        val outside = File(root.parentFile, "outside-${System.nanoTime()}").apply { mkdirs(); File(this, "keep.txt").writeText("keep") }
        CaseStore(root).delete("../${outside.name}")
        assertTrue("a path-like id must not delete other folders", File(outside, "keep.txt").exists())
    }
}

package com.deepsight.capture

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CaseStoreTest {
    @Test
    fun importsBytesUnchangedAndNumbersFields() {
        val store = CaseStore(Files.createTempDirectory("cases").toFile())
        val bytes = byteArrayOf(1, 2, 3, 4)
        val a = store.import("c1", bytes.inputStream(), "png")
        val b = store.import("c1", bytes.inputStream(), "jpg")
        assertArrayEquals(bytes, a.readBytes())
        assertEquals(listOf("field_1.png", "field_2.jpg"), store.fields("c1").map { it.file.name })
        assertEquals(b, store.fields("c1").last().file)
        assertEquals(emptyList<FieldImage>(), store.fields("other"))
        a.delete()
        assertEquals(listOf("field_2.jpg"), store.fields("c1").map { it.file.name })
        assertEquals("field_3.png", store.nextFile("c1", "png").name) // numbers are never reused
    }
}

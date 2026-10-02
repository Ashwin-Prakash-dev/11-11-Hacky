package com.deepsight

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.capture.CaseStore
import com.deepsight.engine.pack.PackLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The app's case run on the engine's smoke pack (random weights): real ONNX Runtime, real files, real triage. */
@RunWith(AndroidJUnit4::class)
class CaseRunnerTest {
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "case-runner-test").apply { deleteRecursively() }
    private val store = CaseStore(root)
    private val runner = CaseRunner(PackLoader.fromAssets(testAssets, packsRoot = "testpacks"))

    private fun addSmokeField(caseId: String) = testAssets.open("testpacks/smoke/golden/case1.png").use { store.import(caseId, it, "png") }

    @Test
    fun runsEveryStoredFieldThenClosesTheCase() = runBlocking {
        addSmokeField("c1")
        addSmokeField("c1")
        store.fields("c1").first().file.delete() // Delete leaves a gap: ids follow the file index, not the position

        val (fields, case) = runner.run("smoke", "c1", store.fields("c1"))

        assertEquals(listOf(fieldId("c1", 2)), fields.map { it.fieldId })
        assertTrue(fields.single().quality.pass)
        assertNotNull(fields.single().imageScore)
        assertEquals("c1", case.caseId)
        assertEquals(listOf(fieldId("c1", 2)), case.fieldIds)
        assertEquals(1, case.fieldsPassed)
    }

    @Test
    fun secondCaseReusesTheLoadedPack() = runBlocking {
        addSmokeField("c1")
        addSmokeField("c2")
        val first = runner.run("smoke", "c1", store.fields("c1")).fields.single()
        val second = runner.run("smoke", "c2", store.fields("c2")).fields.single()
        assertEquals(first.imageScore, second.imageScore)
        assertEquals(1, runner.loads)
    }

    @Test
    fun undecodableImageFailsWithItsFileName() = runBlocking {
        val bad = store.nextFile("c1", "jpg").apply { writeText("not an image") }
        assertEquals(null, BitmapFactory.decodeFile(bad.path))
        val error = runCatching { runner.run("smoke", "c1", store.fields("c1")) }.exceptionOrNull()
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().contains(bad.name))
    }

    @Test
    fun packThatFailsToLoadDoesNotBreakTheNextPack() = runBlocking {
        addSmokeField("c1")
        assertTrue(runCatching { runner.run("broken", "c1", store.fields("c1")) }.isFailure) // garbage model bytes
        assertTrue(runner.run("smoke", "c1", store.fields("c1")).fields.single().quality.pass)
    }
}

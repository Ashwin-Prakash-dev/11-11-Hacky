package com.deepsight.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.profiles.Patient
import com.deepsight.profiles.Sex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaseDaoTest {
    @Test
    fun caseAndFieldsRoundTripUnchanged() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CaseDb::class.java).build()
        val dao = db.dao()
        val case = CaseEntity("c1", "malaria_thin", 1L, """{"a":1}""", "dr", 2L, "agree", "ok")
        val field = FieldEntity("f1", "c1", "/files/f1.jpg", """{"b":2}""")
        dao.upsert(case, listOf(field))
        assertEquals(listOf(case), dao.history().first())
        assertEquals(listOf(field), dao.fields("c1"))
        db.close()
    }

    @Test
    fun aPatientsCasesAreNewestFirstAndEmitAgainWhenOneFinishes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CaseDb::class.java).build()
        val dao = db.dao()
        db.patientDao().insert(Patient("P-0000-0001", "Ada Example", "1990-05-17", Sex.F, createdAt = 1L))
        db.patientDao().insert(Patient("P-0000-0002", "Ben Sample", "1980-01-02", Sex.M, createdAt = 1L))
        dao.enqueue(CaseEntity("old", "malaria_thin", 1L, patientUid = "P-0000-0001"))
        dao.enqueue(CaseEntity("new", "malaria_thin", 2L, patientUid = "P-0000-0001"))
        dao.enqueue(CaseEntity("other", "malaria_thin", 3L, patientUid = "P-0000-0002"))

        val emissions = mutableListOf<List<CaseEntity>>()
        val collecting = launch { dao.casesFor("P-0000-0001").take(2).toList(emissions) }
        withTimeout(10_000) { while (emissions.isEmpty()) delay(10) }
        dao.setResult("new", """{"x":1}""", analysedAt = 5L) // what the queue does when a batch finishes
        withTimeout(10_000) { collecting.join() }
        val (queued, done) = emissions
        assertEquals(listOf("new", "old"), queued.map { it.caseId })
        assertEquals(CaseStatus.QUEUED, queued.first().status)
        assertEquals(CaseStatus.DONE, done.first().status)
        db.close()
    }
}

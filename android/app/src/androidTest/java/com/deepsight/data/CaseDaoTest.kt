package com.deepsight.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
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
}

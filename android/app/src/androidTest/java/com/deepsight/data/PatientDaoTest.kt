package com.deepsight.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.profiles.Patient
import com.deepsight.profiles.Sex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatientDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CaseDb::class.java).build()
    private val patients = db.patientDao()

    @After
    fun close() = db.close()

    @Test
    fun insertedPatientIsFoundByUid() = runBlocking {
        val ada = Patient("P-0000-0001", "Ada Example", "1990-05-17", Sex.F, createdAt = 1L)
        patients.insert(ada)
        assertEquals(ada, patients.byUid("P-0000-0001"))
        assertNull(patients.byUid("P-0000-0002"))
    }

    @Test
    fun aTakenUidIsRefusedNotOverwritten() = runBlocking {
        val ada = Patient("P-0000-0001", "Ada Example", "1990-05-17", Sex.F, createdAt = 1L)
        patients.insert(ada)
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { patients.insert(ada.copy(name = "Ben Sample")) }
        }
        assertEquals("Ada Example", patients.byUid("P-0000-0001")!!.name)
    }

    @Test
    fun createDrawsANewUidOnAClash() = runBlocking {
        patients.insert(Patient("P-0000-0001", "Ada Example", "1990-05-17", Sex.F, createdAt = 1L))
        val uids = ArrayDeque(listOf("P-0000-0001", "P-0000-0002"))
        val ben = patients.create(" Ben Sample ", "1980-01-02", Sex.M, now = 2L) { uids.removeFirst() }
        assertEquals("P-0000-0002", ben.uid)
        assertEquals("Ben Sample", ben.name)
        assertEquals(ben, patients.byUid("P-0000-0002"))
    }
}

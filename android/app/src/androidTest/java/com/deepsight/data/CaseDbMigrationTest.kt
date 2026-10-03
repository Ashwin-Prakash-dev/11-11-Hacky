package com.deepsight.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.profiles.Patient
import com.deepsight.profiles.Sex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Phones that already hold cases keep them across every schema change (v1 → v2 → v3 → v4 → v5). */
@RunWith(AndroidJUnit4::class)
class CaseDbMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun version1CasesSurviveAndGainEmptyReportColumns() = runBlocking {
        context.deleteDatabase(name)
        // Room's own v1 schema (generated CaseDb_Impl before the migration), with one signed-off case.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `cases` (`case_id` TEXT NOT NULL, `pack_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `case_result_json` TEXT, `signed_by` TEXT, `signed_at` INTEGER, `decision` TEXT, `note` TEXT, PRIMARY KEY(`case_id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `fields` (`field_id` TEXT NOT NULL, `case_id` TEXT NOT NULL, `image_path` TEXT, `field_result_json` TEXT NOT NULL, PRIMARY KEY(`field_id`), FOREIGN KEY(`case_id`) REFERENCES `cases`(`case_id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fields_case_id` ON `fields` (`case_id`)")
            db.execSQL("INSERT INTO cases VALUES ('case-1', 'malaria_thin', 1000, '{}', 'Dr Old', 1000, 'accept', '')")
            db.execSQL("INSERT INTO fields VALUES ('case-1_field_1', 'case-1', NULL, '{}')")
            db.version = 1
        }

        val db = CaseDb.build(context, name)
        try {
            val dao = db.dao()
            val old = dao.caseById("case-1")!!
            assertEquals("Dr Old", old.signedBy)
            assertNull(old.reportText)
            assertNull(old.reportSource)
            assertNull(old.analysedAt)
            assertEquals(1, dao.fields("case-1").size)

            dao.upsert(old.copy(reportText = "Triage: ABNORMAL_FLAG.", reportSource = "template", analysedAt = 1_790_998_807_000L))
            assertEquals("Triage: ABNORMAL_FLAG.", dao.caseById("case-1")!!.reportText)
            assertEquals(1_790_998_807_000L, dao.caseById("case-1")!!.analysedAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun version3CasesSurviveAsSignedWithoutAPatient() = runBlocking {
        context.deleteDatabase(name)
        // Room's own v3 schema (generated CaseDb_Impl before MIGRATION_3_4), with one signed-off case and its field.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `cases` (`case_id` TEXT NOT NULL, `pack_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `case_result_json` TEXT, `signed_by` TEXT, `signed_at` INTEGER, `decision` TEXT, `note` TEXT, `report_text` TEXT, `report_source` TEXT, `analysed_at` INTEGER, PRIMARY KEY(`case_id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `fields` (`field_id` TEXT NOT NULL, `case_id` TEXT NOT NULL, `image_path` TEXT, `field_result_json` TEXT NOT NULL, PRIMARY KEY(`field_id`), FOREIGN KEY(`case_id`) REFERENCES `cases`(`case_id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fields_case_id` ON `fields` (`case_id`)")
            db.execSQL("INSERT INTO cases VALUES ('case-3', 'malaria_thin', 1000, '{}', 'Dr Old', 2000, 'accept', '', 'Report.', 'template', 1500)")
            db.execSQL("INSERT INTO fields VALUES ('case-3_field_1', 'case-3', '/files/f1.jpg', '{}')")
            db.version = 3
        }

        val db = CaseDb.build(context, name)
        try {
            val dao = db.dao()
            val old = dao.caseById("case-3")!!
            assertEquals(CaseStatus.SIGNED, old.status)
            assertNull(old.patientUid)
            assertNull(old.error)
            assertEquals(SubmissionSource.SINGLE, old.submissionSource)
            assertEquals("Dr Old", old.signedBy)
            assertEquals(1500L, old.analysedAt)
            assertEquals(listOf("/files/f1.jpg"), dao.fields("case-3").map { it.imagePath })

            val patient = Patient("P-0000-0001", "Ada Example", "1990-05-17", Sex.F, createdAt = 3000L)
            db.patientDao().insert(patient)
            assertEquals(patient, db.patientDao().byUid(patient.uid))
        } finally {
            db.close()
        }
    }

    @Test
    fun version4CasesMigrateAsSingleSubmissions() = runBlocking {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `patients` (`patient_uid` TEXT NOT NULL, `name` TEXT NOT NULL, `dob` TEXT NOT NULL, `sex` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`patient_uid`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `cases` (`case_id` TEXT NOT NULL, `pack_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `case_result_json` TEXT, `signed_by` TEXT, `signed_at` INTEGER, `decision` TEXT, `note` TEXT, `report_text` TEXT, `report_source` TEXT, `analysed_at` INTEGER, `patient_uid` TEXT, `status` TEXT NOT NULL DEFAULT 'SIGNED', `error` TEXT, PRIMARY KEY(`case_id`), FOREIGN KEY(`patient_uid`) REFERENCES `patients`(`patient_uid`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cases_patient_uid` ON `cases` (`patient_uid`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `fields` (`field_id` TEXT NOT NULL, `case_id` TEXT NOT NULL, `image_path` TEXT, `field_result_json` TEXT NOT NULL, PRIMARY KEY(`field_id`), FOREIGN KEY(`case_id`) REFERENCES `cases`(`case_id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fields_case_id` ON `fields` (`case_id`)")
            db.execSQL("INSERT INTO cases VALUES ('single-v4', 'leukaemia_wbc', 1000, '{}', NULL, NULL, NULL, NULL, NULL, NULL, 1100, NULL, 'DONE', NULL)")
            db.version = 4
        }

        val db = CaseDb.build(context, name)
        try {
            assertEquals(SubmissionSource.SINGLE, db.dao().caseById("single-v4")!!.submissionSource)
            assertEquals(emptyList<CaseEntity>(), db.dao().batchSubmissions().first())
        } finally {
            db.close()
        }
    }
}

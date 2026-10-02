package com.deepsight.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Phones that already hold signed-off cases (schema v1) keep them when the report columns arrive (v2). */
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
            assertEquals(1, dao.fields("case-1").size)

            dao.upsert(old.copy(reportText = "Triage: ABNORMAL_FLAG.", reportSource = "template"))
            assertEquals("Triage: ABNORMAL_FLAG.", dao.caseById("case-1")!!.reportText)
        } finally {
            db.close()
        }
    }
}

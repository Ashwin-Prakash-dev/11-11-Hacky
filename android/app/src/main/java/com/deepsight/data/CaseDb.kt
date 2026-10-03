package com.deepsight.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.deepsight.profiles.Patient
import com.deepsight.profiles.PatientUid
import com.deepsight.profiles.Sex
import kotlinx.coroutines.flow.Flow

/**
 * One case. [caseResultJson] is contract case_result JSON as written by Contracts.encode; null until triage has run. Sign-off
 * columns are null until a clinician signs. [reportText] is the report the clinician saw when signing; [reportSource] is
 * `gemma` or `template` (schema v2). [patientUid] is null only for cases saved before patients existed (schema v4);
 * [status] follows QUEUED → RUNNING → DONE or FAILED ([error]) → SIGNED, and older cases are SIGNED.
 */
@Entity(
    tableName = "cases",
    foreignKeys = [ForeignKey(Patient::class, ["patient_uid"], ["patient_uid"])], // no cascade: a case outlives nothing
    indices = [Index("patient_uid")],
)
data class CaseEntity(
    @PrimaryKey @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "case_result_json") val caseResultJson: String? = null,
    @ColumnInfo(name = "signed_by") val signedBy: String? = null,
    @ColumnInfo(name = "signed_at") val signedAt: Long? = null,
    val decision: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "report_text") val reportText: String? = null,
    @ColumnInfo(name = "report_source") val reportSource: String? = null,
    /** Epoch millis when the analysis finished; null for cases saved before it was recorded (schema v3). */
    @ColumnInfo(name = "analysed_at") val analysedAt: Long? = null,
    @ColumnInfo(name = "patient_uid") val patientUid: String? = null,
    @ColumnInfo(defaultValue = "SIGNED") val status: CaseStatus = CaseStatus.SIGNED,
    val error: String? = null,
)

enum class CaseStatus { QUEUED, RUNNING, DONE, FAILED, SIGNED }

/** One field. [imagePath] is null when the field has no image (fake engine fields until #30); must be a file under filesDir (copy picker/camera images in): content:// URIs lose permission after a restart. */
@Entity(
    tableName = "fields",
    foreignKeys = [ForeignKey(CaseEntity::class, ["case_id"], ["case_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("case_id")],
)
data class FieldEntity(
    @PrimaryKey @ColumnInfo(name = "field_id") val fieldId: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "field_result_json") val fieldResultJson: String,
)

@Dao
interface CaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(field: FieldEntity)

    @Transaction
    suspend fun upsert(entity: CaseEntity, fields: List<FieldEntity>) {
        upsert(entity)
        fields.forEach { upsert(it) }
    }

    @Query("SELECT * FROM cases ORDER BY created_at DESC")
    fun history(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE case_id = :caseId")
    suspend fun caseById(caseId: String): CaseEntity?

    @Query("SELECT * FROM fields WHERE case_id = :caseId ORDER BY field_id")
    suspend fun fields(caseId: String): List<FieldEntity>

    // Queue and sign-off: UPDATEs only. A REPLACE deletes the case row first, which drops columns it doesn't set
    // (patient_uid, created_at) and cascades to the case's fields.

    @Update
    suspend fun update(entity: CaseEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: CaseEntity): Long

    @Query("UPDATE cases SET status = :status, error = :error WHERE case_id = :caseId")
    suspend fun setStatus(caseId: String, status: CaseStatus, error: String? = null)

    /** A new case goes in QUEUED; a case already stored (a recapture) is re-queued with its patient and created_at kept. */
    @Transaction
    suspend fun enqueue(entity: CaseEntity) {
        if (insertIfAbsent(entity.copy(status = CaseStatus.QUEUED)) == -1L) setStatus(entity.caseId, CaseStatus.QUEUED)
    }

    /** What a restarted queue must still run, in submit order. */
    @Query("SELECT * FROM cases WHERE status IN ('QUEUED', 'RUNNING') ORDER BY created_at, rowid")
    suspend fun unfinished(): List<CaseEntity>

    @Query("DELETE FROM fields WHERE case_id = :caseId")
    suspend fun deleteFields(caseId: String)

    @Query("UPDATE cases SET case_result_json = :caseResultJson, analysed_at = :analysedAt, status = 'DONE', error = NULL WHERE case_id = :caseId")
    suspend fun setResult(caseId: String, caseResultJson: String, analysedAt: Long)

    /** The run's fields replace the previous run's (a recapture deletes images), with the case result, in one transaction. */
    @Transaction
    suspend fun finish(caseId: String, caseResultJson: String, analysedAt: Long, fields: List<FieldEntity>) {
        deleteFields(caseId)
        fields.forEach { upsert(it) }
        setResult(caseId, caseResultJson, analysedAt)
    }

    @Query("SELECT * FROM cases WHERE case_id = :caseId")
    fun observe(caseId: String): Flow<CaseEntity?>

    @Query("SELECT * FROM cases WHERE patient_uid IS NOT NULL ORDER BY created_at DESC")
    fun patientCases(): Flow<List<CaseEntity>>
}

@Dao
interface PatientDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(patient: Patient)

    @Query("SELECT * FROM patients WHERE patient_uid = :uid")
    suspend fun byUid(uid: String): Patient?

    @Query("SELECT * FROM patients ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Patient>>

    /** For tests that seed the app's own database; fails while a case still references the patient. */
    @Query("DELETE FROM patients WHERE patient_uid = :uid")
    suspend fun delete(uid: String)
}

/** Validates (Patient's init throws IllegalArgumentException), then stores under a fresh UID, drawing again on a clash. */
suspend fun PatientDao.create(name: String, dob: String, sex: Sex, now: Long, uid: () -> String = { PatientUid.generate() }): Patient {
    repeat(5) {
        val patient = Patient(uid(), name.trim(), dob.trim(), sex, now)
        try {
            insert(patient)
            return patient
        } catch (e: SQLiteConstraintException) {
            // UID already taken on this phone; 40 bits make this rare
        }
    }
    error("Could not find a free patient ID")
}

@Database(entities = [CaseEntity::class, FieldEntity::class, Patient::class], version = 4, exportSchema = false)
abstract class CaseDb : RoomDatabase() {
    abstract fun dao(): CaseDao
    abstract fun patientDao(): PatientDao

    companion object {
        @Volatile private var instance: CaseDb? = null

        /** v1 → v2: the report shown at sign-off. Existing cases keep every column; their report is null. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `report_text` TEXT")
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `report_source` TEXT")
            }
        }

        /** v2 → v3: when the analysis ran. Existing cases keep every column; their time is null. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `analysed_at` INTEGER")
            }
        }

        /** v3 → v4: patients, and each case's patient and queue status. Existing cases were written at sign-off: SIGNED, no patient. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `patients` (`patient_uid` TEXT NOT NULL, `name` TEXT NOT NULL, `dob` TEXT NOT NULL, `sex` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`patient_uid`))")
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `patient_uid` TEXT REFERENCES `patients`(`patient_uid`) ON UPDATE NO ACTION ON DELETE NO ACTION")
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'SIGNED'")
                db.execSQL("ALTER TABLE `cases` ADD COLUMN `error` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cases_patient_uid` ON `cases` (`patient_uid`)")
            }
        }

        fun build(context: Context, name: String = "cases.db"): CaseDb =
            Room.databaseBuilder(context.applicationContext, CaseDb::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

        fun get(context: Context): CaseDb = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }
    }
}

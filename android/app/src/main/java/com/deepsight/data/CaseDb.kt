package com.deepsight.data

import android.content.Context
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
import kotlinx.coroutines.flow.Flow

/** One case. [caseResultJson] is contract case_result JSON as written by Contracts.encode; null until triage has run. Sign-off columns are null until a clinician signs. */
@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "case_result_json") val caseResultJson: String? = null,
    @ColumnInfo(name = "signed_by") val signedBy: String? = null,
    @ColumnInfo(name = "signed_at") val signedAt: Long? = null,
    val decision: String? = null,
    val note: String? = null,
)

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
}

@Database(entities = [CaseEntity::class, FieldEntity::class], version = 1, exportSchema = false)
abstract class CaseDb : RoomDatabase() {
    abstract fun dao(): CaseDao

    companion object {
        @Volatile private var instance: CaseDb? = null

        fun get(context: Context): CaseDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CaseDb::class.java, "cases.db").build().also { instance = it }
        }
    }
}

package com.deepsight.profiles

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.SecureRandom
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Random
import java.util.TimeZone

enum class Sex { F, M, OTHER }

/**
 * A stored patient (Room `patients`, schema v4). The only patient validator: the new-patient form builds one and shows
 * the [IllegalArgumentException] message. [dob] is an ISO `yyyy-MM-dd` calendar date, not in the future.
 */
@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey @ColumnInfo(name = "patient_uid") val uid: String,
    val name: String,
    val dob: String,
    val sex: Sex,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    init {
        require(name.isNotBlank()) { "Enter the patient's name" }
        require(PatientUid.isValid(uid)) { "Patient ID must look like P-XXXX-XXXX" }
        val date = requireNotNull(dateOf(dob)) { "Enter the date of birth as yyyy-MM-dd" }
        require(date <= dateOf(Calendar.getInstance())) { "Date of birth is in the future" }
    }

    /** Whole years at [now] in [zone]; the birthday itself counts. */
    fun ageOn(now: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val born = dateOf(dob)!!
        val today = dateOf(Calendar.getInstance(zone).apply { timeInMillis = now })
        return today / 10_000 - born / 10_000 - if (today % 10_000 < born % 10_000) 1 else 0
    }

    private companion object {
        val ISO = Regex("""(\d{4})-(\d{2})-(\d{2})""")

        /** yyyyMMdd as an Int, so dates compare as numbers; null if not a real date. API 24 has no java.time (no desugaring). */
        fun dateOf(iso: String): Int? {
            val (y, m, d) = ISO.matchEntire(iso)?.destructured?.toList()?.map(String::toInt) ?: return null
            val real = runCatching { GregorianCalendar(y, m - 1, d).apply { isLenient = false }.timeInMillis }.isSuccess
            return if (real) y * 10_000 + m * 100 + d else null
        }

        fun dateOf(c: Calendar) = c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }
}

/** `P-XXXX-XXXX`: 8 Crockford base32 characters (40 bits), grouped so they can be read and copied onto a slip. Unique per phone only. */
object PatientUid {
    const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val FORMAT = Regex("P-[$ALPHABET]{4}-[$ALPHABET]{4}")

    fun generate(random: Random = SecureRandom()): String {
        val chars = String(CharArray(8) { ALPHABET[random.nextInt(ALPHABET.length)] })
        return "P-${chars.take(4)}-${chars.drop(4)}"
    }

    fun isValid(uid: String) = FORMAT.matches(uid)
}

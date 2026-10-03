package com.deepsight.profiles

import com.deepsight.data.CaseEntity
import com.deepsight.data.CaseStatus
import com.deepsight.engine.contract.TriageLevel
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilesOfTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val ada = Patient("P-0000-0001", "Ada Example", "1990-05-17", Sex.F, createdAt = 0L)
    private val ben = Patient("P-0000-0002", "Ben Sample", "1965-01-02", Sex.M, createdAt = 0L)
    private val now = 1_790_985_600_000L // 2026-10-03 00:00 UTC

    private fun case(id: String, patient: String?, createdAt: Long, level: String?) = CaseEntity(
        id, "malaria_thin", createdAt, patientUid = patient, status = if (level == null) CaseStatus.QUEUED else CaseStatus.DONE,
        caseResultJson = level?.let {
            """{"case_id":"$id","pack_id":"malaria_thin","pack_version":"0.1.0","field_ids":[],"fields_passed":0,"counts":{},""" +
                """"image_score":null,"uncertainty":{"flag":false,"reason":null},"triage":{"level":"$it","rule_id":"r","provisional":true}}"""
        },
    )

    @Test
    fun countsCasesAndShowsTheNewestLevelAndDate() {
        val cases = listOf( // newest first, as CaseDao.patientCases returns them
            case("c3", ada.uid, createdAt = now - 86_400_000L, level = "NEEDS_EXPERT"),
            case("c2", ben.uid, createdAt = now - 2 * 86_400_000L, level = "NORMAL_SCREEN"),
            case("c1", ada.uid, createdAt = now - 3 * 86_400_000L, level = "ABNORMAL_FLAG"),
        )
        val (a, b) = profilesOf(listOf(ada, ben), cases, now, utc, Locale.US)
        assertEquals(PatientProfile("P-0000-0001", "Ada Example", 36, "F", 2, TriageLevel.NEEDS_EXPERT, "2 Oct 2026"), a)
        assertEquals(PatientProfile("P-0000-0002", "Ben Sample", 61, "M", 1, TriageLevel.NORMAL_SCREEN, "1 Oct 2026"), b)
    }

    @Test
    fun aPatientWithoutCasesOrWithOnlyAQueuedOneHasNoLevel() {
        val (a, b) = profilesOf(listOf(ada, ben), listOf(case("c1", ben.uid, now, level = null)), now, utc, Locale.US)
        assertEquals(0, a.caseCount)
        assertEquals(null, a.lastLevel)
        assertEquals(null, a.lastScreening)
        assertEquals(1, b.caseCount)
        assertEquals(null, b.lastLevel)
        assertEquals("3 Oct 2026", b.lastScreening)
    }
}

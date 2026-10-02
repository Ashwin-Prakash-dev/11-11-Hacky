package com.deepsight.result

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.contract.QualityResult
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.engine.contract.TriageResult
import com.deepsight.engine.contract.UncertaintyResult
import com.deepsight.ui.ThemePreviews
import com.deepsight.ui.theme.DeepSightTheme

// Android Studio previews only; the values mirror contracts/examples (one passed field, one rejected for blur).
private val previewFields = listOf(
    FieldResult(
        caseId = "case-0001", fieldId = "field-01", packId = "malaria_thin", packVersion = "0.1.0",
        quality = QualityResult(pass = true, blurScore = 14.2, exposureScore = 0.1),
        router = RouterResult(RouterVerdict.MATCH, 0.97),
        counts = mapOf("parasitized" to 1, "uninfected" to 2),
    ),
    FieldResult(
        caseId = "case-0001", fieldId = "field-02", packId = "malaria_thin", packVersion = "0.1.0",
        quality = QualityResult(pass = false, blurScore = 3.1, exposureScore = 0.1, reasons = listOf(QualityReason.BLUR)),
    ),
)

private val previewCase = CaseResult(
    caseId = "case-0001", packId = "malaria_thin", packVersion = "0.1.0", fieldIds = listOf("field-01", "field-02"),
    fieldsPassed = 1, counts = mapOf("parasitized" to 1, "uninfected" to 2),
    uncertainty = UncertaintyResult(flag = false),
    triage = TriageResult(TriageLevel.ABNORMAL_FLAG, "parasite_seen", provisional = true),
)

@ThemePreviews
@Composable
private fun ResultScreenPreview() = DeepSightTheme {
    Surface {
        ResultScreen(previewCase, previewFields, report = null, signOff = null, onRecapture = {}, onSignOff = {})
    }
}

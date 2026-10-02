package com.deepsight.result

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouterResultScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun mismatchNamesTheOtherTest() {
        show(RouterResult(RouterVerdict.MISMATCH, 0.9, "fungal"))

        rule.onNodeWithText("Image looks like another test: fungal").assertExists()
    }

    @Test
    fun rejectExplainsThatTheImageIsUnrecognised() {
        show(RouterResult(RouterVerdict.REJECT, 0.9))

        rule.onNodeWithText("Image is not a recognised test type").assertExists()
    }

    private fun show(router: RouterResult) {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }
        val case = Contracts.parseCaseResult(read("case_result.malaria_thin.json"))
        val field = Contracts.parseFieldResult(read("field_result.malaria_thin.json")).copy(router = router)
        rule.setContent {
            MaterialTheme {
                ResultScreen(
                    case = case,
                    fields = listOf(field),
                    report = null,
                    signOff = null,
                    onRecapture = {},
                    onSignOff = {},
                )
            }
        }
    }
}

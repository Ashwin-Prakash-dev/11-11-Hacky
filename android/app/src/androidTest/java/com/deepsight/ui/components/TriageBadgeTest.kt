package com.deepsight.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.ui.theme.DeepSightTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TriageBadgeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun showsTheExactLevelAndItsMeaning() {
        rule.setContent { DeepSightTheme { TriageBadge(TriageLevel.NEEDS_EXPERT) } }

        rule.onNodeWithText("NEEDS_EXPERT").assertExists() // the exact contract string, as the report check expects
        rule.onNodeWithText(triageStyle(TriageLevel.NEEDS_EXPERT).meaning).assertExists()
    }

    @Test
    fun worksOutsideTheAppTheme() {
        rule.setContent { MaterialTheme { TriageBadge(TriageLevel.ABNORMAL_FLAG) } }

        rule.onNodeWithText("ABNORMAL_FLAG").assertExists()
    }
}

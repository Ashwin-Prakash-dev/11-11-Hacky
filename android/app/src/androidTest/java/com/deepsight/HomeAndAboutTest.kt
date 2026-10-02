package com.deepsight

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.engine.pack.PackLoader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Packs that aren't validated can't be opened; About shows the GPL notices and opens the licence and NLM notice from the APK. */
@RunWith(AndroidJUnit4::class)
class HomeAndAboutTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String, substring: Boolean = false) =
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun unvalidatedPacksStayOnHome() {
        val packs = PackLoader.fromAssets(rule.activity.assets).discover().installed
        waitFor(packs.first().displayName)
        packs.filterNot { DemoPacks.isReady(it.id) }.forEach {
            rule.onNodeWithText(it.displayName).performScrollTo().performClick()
            rule.onNodeWithText("Choose test").assertExists()
            rule.onNodeWithText("Import image").assertDoesNotExist()
        }
    }

    /** The About screen says the app has no internet permission; the installed APK must not request one. */
    @Test
    fun appRequestsNoNetworkPermission() {
        val info = rule.activity.packageManager.getPackageInfo(rule.activity.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE").forEach {
            org.junit.Assert.assertFalse("$it requested: $requested", it in requested)
        }
    }

    @Test
    fun aboutShowsTheLegalNoticesAndOpensTheLicence() {
        rule.onNodeWithContentDescription("About").performClick()
        rule.onNodeWithText("GNU General Public License, version 3", substring = true).assertExists()
        rule.onNodeWithText("WITHOUT ANY WARRANTY", substring = true).assertExists()
        rule.onNodeWithText("courtesy of the U.S. National Library of Medicine", substring = true).performScrollTo().assertExists()

        rule.onNodeWithText("Read the GNU GPL v3").performScrollTo().performClick()
        waitFor("GNU GENERAL PUBLIC LICENSE", substring = true)
        rule.onNodeWithContentDescription("Back").performClick()

        rule.onNodeWithText("Read the NLM notice").performScrollTo().performClick()
        waitFor("National Library of Medicine", substring = true)
        rule.onNodeWithText("Could not open", substring = true).assertDoesNotExist()
    }
}

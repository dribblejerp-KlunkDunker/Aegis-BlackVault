package com.example.screenshots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.MainActivity
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboGif
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Screenshots and GIFs of the real app (MainActivity + real ViewModel + Room seed data) at phone
 * and Chromebook widths. Images are written only when recording:
 *   ./gradlew :app:recordRoborazziDebug   ->  app/build/outputs/roborazzi/
 * A plain `testDebugUnitTest` run executes the same navigation but writes nothing.
 *
 * The Command deck and mind map run endless animations, so these tests drive the Compose clock
 * by hand instead of waiting for idle (which would never come).
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val out = "build/outputs/roborazzi".also { java.io.File(it).mkdirs() }

    private val tabs = listOf(
        "nav_tab_command" to "1_command",
        "nav_tab_mindmap" to "2_mindmap",
        "nav_tab_psych" to "3_psych",
        "nav_tab_scraper" to "4_scraper",
        "nav_tab_insights" to "5_insights"
    )

    /** Advances the Compose clock and the main looper until [condition] holds (Room loads on background threads). */
    private fun pumpUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        composeTestRule.mainClock.autoAdvance = false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for the seeded dossiers to load" }
            composeTestRule.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
    }

    private fun settle(ms: Long = 800) {
        repeat((ms / 100).toInt()) {
            composeTestRule.mainClock.advanceTimeBy(100)
            ShadowLooper.idleMainLooper()
        }
    }

    private fun waitForSeedData() = pumpUntil {
        composeTestRule.onAllNodesWithText("NULL_ROUTER", substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun captureEveryTab(prefix: String) {
        waitForSeedData()
        tabs.forEach { (tag, name) ->
            composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
            settle()
            composeTestRule.onRoot().captureRoboImage("$out/${prefix}_$name.png")
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel5)
    fun phone_every_tab() = captureEveryTab("phone")

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun chromebook_every_tab() = captureEveryTab("chromebook")

    private fun captureGovernance(prefix: String) {
        waitForSeedData()
        composeTestRule.onNodeWithTag("nav_tab_psych", useUnmergedTree = true).performClick()
        settle()
        composeTestRule.onNodeWithTag("psych_subtab_GOV").performClick()
        settle()
        composeTestRule.onRoot().captureRoboImage("$out/${prefix}_6_governance.png")
    }

    @Test
    @Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel5)
    fun phone_governance_tab() = captureGovernance("phone")

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun chromebook_governance_tab() = captureGovernance("chromebook")

    /** Project bar, the new-project dialog, and the empty state a new project opens to. */
    private fun captureProjects(prefix: String, viaDialog: Boolean) = try {
        captureProjectsInner(prefix, viaDialog)
    } finally {
        // The active project is remembered in settings and the database outlives this test, so put
        // the default project back; otherwise later tests open in the new, empty project.
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("aegis_settings", android.content.Context.MODE_PRIVATE)
            .edit().putString("active_project_id", com.example.model.ProjectEntity.DEFAULT_ID).commit()
    }

    /**
     * Project menu, new-project dialog and the empty state a new project opens to.
     * On the phone layout Compose never reports idle while the dialog's text field is shown under
     * Robolectric, so typing is only done at Chromebook width; the phone run creates the project
     * through the same ViewModel call the dialog makes.
     */
    private fun captureProjectsInner(prefix: String, viaDialog: Boolean) {
        waitForSeedData()
        composeTestRule.onNodeWithTag("nav_tab_psych", useUnmergedTree = true).performClick()
        settle()
        composeTestRule.onNodeWithTag("project_menu_btn", useUnmergedTree = true).performClick()
        settle()
        // The menu is a popup (its own window), so capture the whole screen rather than one root.
        captureScreenRoboImage("$out/${prefix}_7_project_menu.png")
        composeTestRule.onNodeWithTag("project_option_default", useUnmergedTree = true).performClick()
        settle()
        if (viaDialog) {
            composeTestRule.onNodeWithTag("project_new_btn", useUnmergedTree = true).performClick()
            settle()
            composeTestRule.onNodeWithTag("project_new_name").performTextInput("Self-audit")
            settle()
            captureScreenRoboImage("$out/${prefix}_8_new_project_dialog.png")
            composeTestRule.onNodeWithTag("project_new_confirm").performClick()
        } else {
            composeTestRule.runOnUiThread {
                androidx.lifecycle.ViewModelProvider(composeTestRule.activity)[com.example.ui.AegisViewModel::class.java]
                    .createProject("Self-audit")
            }
        }
        pumpUntil { composeTestRule.onAllNodesWithTag("empty_project_state").fetchSemanticsNodes().isNotEmpty() }
        settle()
        composeTestRule.onRoot().captureRoboImage("$out/${prefix}_9_empty_project.png")
    }

    @Test
    @Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel5)
    fun phone_projects() = captureProjects("phone", viaDialog = false)

    @Test
    @Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun chromebook_projects() = captureProjects("chromebook", viaDialog = true)

    @Test
    @Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel5)
    fun phone_tab_tour_gif() {
        waitForSeedData()
        composeTestRule.onRoot().captureRoboGif(composeTestRule, "$out/phone_tour.gif") {
            tabs.forEach { (tag, _) ->
                composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
                settle(1_200)
            }
        }
    }
}

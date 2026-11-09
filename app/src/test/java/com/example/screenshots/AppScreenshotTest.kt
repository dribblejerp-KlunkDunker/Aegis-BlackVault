package com.example.screenshots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.MainActivity
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboGif
import com.github.takahirom.roborazzi.captureRoboImage
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
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val out = "build/outputs/roborazzi"

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

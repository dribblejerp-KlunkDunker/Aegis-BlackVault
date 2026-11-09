package com.example.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.FakeGithubApi
import com.example.model.ConsentStatus
import com.example.model.ScraperPlatformMode
import com.example.model.SubjectProfile
import com.example.network.OsintScraperService
import com.example.ui.screens.PsychometricScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboGif
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shows the consent gate working on a real (canned, offline) GitHub scrape: the playbook is withheld
 * while the subject is Unverified, appears after "Self-analysis", and is withheld again on switching back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ConsentGateScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val out = "build/outputs/roborazzi".also { java.io.File(it).mkdirs() }
    private val scraper = OsintScraperService(customClient = FakeGithubApi.client())

    private var subject by mutableStateOf<SubjectProfile?>(null)

    private fun setUpScreen() {
        val gated = runBlocking {
            scraper.scrapeAndBuildProfile(FakeGithubApi.LOGIN, ScraperPlatformMode.GITHUB).profile
        }
        subject = gated
        composeTestRule.setContent {
            MyApplicationTheme {
                subject?.let { current ->
                    PsychometricScreen(
                        activeSubject = current,
                        comparisonSubject = current,
                        allSubjects = listOf(current),
                        selectedSpectrumToken = "to",
                        onSelectSpectrumToken = {},
                        onSelectComparisonSubject = {},
                        onSetConsent = { status ->
                            subject = runBlocking {
                                scraper.recomputeFromArtifacts(current.copy(consentStatus = status))
                            }
                        }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /** The playbook sits below the LIWC header in a lazy list, so it is only composed once scrolled to. */
    private fun scrollPlaybookTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
        composeTestRule.waitForIdle()
    }

    @Test
    fun consent_gate_stills() {
        setUpScreen()
        scrollPlaybookTo(hasTestTag("restricted_notice"))
        composeTestRule.onNodeWithTag("restricted_notice").assertExists()
        composeTestRule.onRoot().captureRoboImage("$out/consent_1_unverified_withheld.png")

        composeTestRule.onNodeWithTag("consent_SELF").performClick()
        composeTestRule.waitForIdle()
        assertTrue(subject!!.restrictedOutputsUnlocked)
        scrollPlaybookTo(hasText("WHAT MAKES", substring = true))
        composeTestRule.onNodeWithTag("restricted_notice").assertDoesNotExist()
        composeTestRule.onRoot().captureRoboImage("$out/consent_2_self_unlocked.png")
    }

    @Test
    fun consent_gate_gif() {
        setUpScreen()
        scrollPlaybookTo(hasTestTag("restricted_notice"))
        composeTestRule.onRoot().captureRoboGif(composeTestRule, "$out/consent_gate.gif") {
            composeTestRule.onNodeWithTag("consent_SELF").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.mainClock.advanceTimeBy(1_000)
            composeTestRule.onNodeWithTag("consent_UNVERIFIED").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.mainClock.advanceTimeBy(1_000)
        }
    }
}

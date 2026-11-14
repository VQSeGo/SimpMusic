package com.maxrave.simpmusic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile on a connected phone:
 *   ./gradlew :androidApp:generateBaselineProfile
 * Cold start to Home, the home feed, Now Playing (opened from the mini player and scrolled) and
 * the Library tab. The result is androidApp/src/main/generated/baselineProfiles/baseline-prof.txt.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(
            packageName = targetPackage(),
            includeInStartupProfile = false,
            maxIterations = 5,
            stableIterations = 3,
        ) {
            startToHome()
            scrollHome()
            openAndScrollPlayer()
            openLibrary()
        }
}

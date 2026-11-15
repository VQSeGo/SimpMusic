package com.maxrave.simpmusic.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

// The journeys run on whatever locale the phone has, so every label is matched in English and
// Russian. A fresh copy of the app is signed out: it shows the "YouTube Music needs sign-in"
// dialog and the guest home (Quick picks), which is enough to start playback.

/** Package of the app under test, `targetAppId` from :baselineprofile/build.gradle.kts. */
fun targetPackage(): String =
    InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId was not passed as an instrumentation runner argument")

private fun labels(vararg names: String): Pattern = Pattern.compile(names.joinToString("|") { Pattern.quote(it) })

private val CANCEL = labels("Cancel", "Отменить")
private val PLAY_ALL = labels("Play all", "Воспроизвести все")
private val LIBRARY = labels("Library", "Библиотека")
private val HOME = labels("Home", "Главная")
private val FAVORITE = Pattern.compile("Favorite.*")

private const val TIMEOUT = 15_000L

/** Starts the app (notification permission granted up front) and closes the sign-in dialog. */
fun MacrobenchmarkScope.startToHome() {
    device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
    pressHome()
    startActivityAndWait()
    dismissSignInDialog()
    device.wait(Until.hasObject(By.text(HOME)), TIMEOUT)
}

fun MacrobenchmarkScope.dismissSignInDialog() {
    device.wait(Until.findObject(By.text(CANCEL)), 5_000)?.click()
    device.waitForIdle()
}

/** The home feed: waits for the guest feed to load and scrolls it down and back up. */
fun MacrobenchmarkScope.scrollHome() {
    device.wait(Until.hasObject(By.text(PLAY_ALL)), TIMEOUT)
    repeat(2) { flingScrollable(Direction.DOWN) }
    repeat(2) { flingScrollable(Direction.UP) }
}

/** Starts Quick picks with "Play all" unless something already plays, and returns the mini player. */
fun MacrobenchmarkScope.ensureMiniPlayer(): UiObject2? {
    miniPlayer()?.let { return it }
    device.wait(Until.findObject(By.text(PLAY_ALL)), TIMEOUT)?.click() ?: return null
    device.wait(Until.hasObject(By.desc(FAVORITE)), TIMEOUT)
    return miniPlayer()
}

/** The mini player is the widest clickable that holds the heart button. */
private fun MacrobenchmarkScope.miniPlayer(): UiObject2? =
    device
        .findObjects(By.clickable(true).hasDescendant(By.desc(FAVORITE), 4))
        .maxByOrNull { it.visibleBounds.width() }

/** Opens Now Playing from the mini player, scrolls its content down and up, and closes it. */
fun MacrobenchmarkScope.openAndScrollPlayer() {
    val mini = ensureMiniPlayer() ?: return
    (mini.findObject(By.clazz("android.widget.TextView")) ?: mini).click()
    device.waitForIdle()
    // Let the sheet settle and the artwork pager / lyrics load.
    Thread.sleep(2_000)
    val x = device.displayWidth / 2
    val bottom = device.displayHeight * 3 / 4
    val top = device.displayHeight / 4
    repeat(4) {
        device.swipe(x, bottom, x, top, 20)
        device.waitForIdle()
        device.swipe(x, top, x, bottom, 20)
        device.waitForIdle()
    }
    device.pressBack()
    device.waitForIdle()
}

/** Opens the Library tab, scrolls it and goes back to Home. */
fun MacrobenchmarkScope.openLibrary() {
    device.wait(Until.findObject(By.text(LIBRARY)), TIMEOUT)?.click() ?: return
    device.waitForIdle()
    Thread.sleep(1_500)
    // The list node is replaced while it scrolls (the bottom bar folds), so look it up again for
    // every fling instead of holding a UiObject2 that goes stale.
    for (direction in listOf(Direction.DOWN, Direction.UP)) {
        flingScrollable(direction)
    }
    device.findObject(By.text(HOME))?.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.flingScrollable(direction: Direction) {
    try {
        val list = device.findObject(By.scrollable(true)) ?: return
        list.setGestureMargin(device.displayWidth / 5)
        list.fling(direction)
    } catch (_: StaleObjectException) {
        // The node was swapped mid-gesture; the profile only needs the scroll code to run.
    }
    device.waitForIdle()
}

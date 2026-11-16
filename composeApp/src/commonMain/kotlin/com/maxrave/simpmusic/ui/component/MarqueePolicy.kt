package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.compositionLocalOf

/**
 * How many times a long title scrolls (`basicMarquee(iterations = ...)`) before it stops and sits at
 * its start.
 *
 * A marquee that never stops redraws its row about 60 times a second for as long as the title is on
 * screen — on a paused player that was the only thing keeping the whole screen drawing (a Home or
 * Library page with one long artist line sat at 60 frames a second with nothing playing). While music
 * plays the mini player's progress ring is animating anyway, so the endless scroll costs nothing
 * extra and stays; once playback stops, [AppTheme][com.maxrave.simpmusic.ui.theme.AppTheme] hands
 * out [RestingMarqueeIterations] and the screen settles to no frames at all.
 *
 * The default is "forever", so a composition outside [AppTheme] behaves as it always did.
 */
val LocalMarqueeIterations = compositionLocalOf { Int.MAX_VALUE }

/** Scrolls a long title gets while nothing is playing; the changed value also restarts a running marquee. */
const val RestingMarqueeIterations = 2

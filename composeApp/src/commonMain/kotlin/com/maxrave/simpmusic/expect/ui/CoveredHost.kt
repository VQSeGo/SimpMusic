package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

/**
 * Stops drawing the view that hosts the calling composable while [covered] is true, and draws it
 * again the moment it turns false. Composition, effects and state keep running underneath, so the
 * host is current the instant it shows again.
 *
 * For a host that something opaque covers completely: on Android a sheet is a window of its own, and
 * the window under it is still drawn in full on every frame anything in it changes, although nobody
 * can see it. A no-op where the platform has no such host.
 */
@Composable
expect fun SkipDrawingHostWhile(covered: Boolean)

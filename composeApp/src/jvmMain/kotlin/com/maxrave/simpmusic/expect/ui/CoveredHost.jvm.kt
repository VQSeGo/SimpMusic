package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

// Desktop draws one scene per window, and its fullscreen lyrics page is a Popup in that scene.
@Composable
actual fun SkipDrawingHostWhile(covered: Boolean) = Unit

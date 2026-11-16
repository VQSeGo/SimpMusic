package com.maxrave.simpmusic.expect.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
actual fun SkipDrawingHostWhile(covered: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, covered) {
        // INVISIBLE, not GONE: the view keeps its size and layout, it only stops drawing, and an
        // invalidation of an invisible view schedules no frame.
        if (covered) view.visibility = View.INVISIBLE
        onDispose {
            if (covered) view.visibility = View.VISIBLE
        }
    }
}

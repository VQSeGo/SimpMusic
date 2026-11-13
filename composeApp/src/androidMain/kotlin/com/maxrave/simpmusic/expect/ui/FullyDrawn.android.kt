package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.maxrave.simpmusic.extension.findActivity

@Composable
actual fun rememberReportFullyDrawn(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { context.findActivity().reportFullyDrawn() } }
}

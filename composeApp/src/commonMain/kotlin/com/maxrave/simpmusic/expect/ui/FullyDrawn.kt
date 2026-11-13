package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

/**
 * Returns a function that tells the system the first screen is ready (Android `reportFullyDrawn`,
 * which logs `Fully drawn …` and feeds startup metrics). A no-op where the platform has no such call.
 * Call it once per process.
 */
@Composable
expect fun rememberReportFullyDrawn(): () -> Unit

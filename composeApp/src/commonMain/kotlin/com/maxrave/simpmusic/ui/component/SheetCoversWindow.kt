package com.maxrave.simpmusic.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Reports whether a full-height ModalBottomSheet lies fully open and at rest over the whole window:
 * true once it has settled open, false from the first pixel it moves (a drag, the slide away), when
 * it leaves, and while [spansWindow] is false (a sheet narrower than the window shows what is under
 * it at both sides). The caller stops drawing what the sheet hides (SkipDrawingHostWhile): on Android
 * a sheet is a window of its own, and the window under it is otherwise drawn in full on every frame
 * anything in it changes, though nobody can see it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheetCoversWindow(
    sheetState: SheetState,
    spansWindow: Boolean,
    onCoversWindowChange: (Boolean) -> Unit,
) {
    val latestOnCoversWindowChange by rememberUpdatedState(onCoversWindowChange)
    LaunchedEffect(sheetState, spansWindow) {
        // The offset the sheet came to rest at when it opened. currentValue turns Expanded only once
        // the opening animation has finished and stays Expanded through a drag that has not crossed
        // the threshold yet, so "covered" also needs the offset to still be that one: the window under
        // it shows from the first pixel of a drag.
        var restingOffset: Float? = null
        snapshotFlow { sheetState.currentValue to runCatching { sheetState.requireOffset() }.getOrNull() }
            .map { (value, offset) ->
                if (value != SheetValue.Expanded || offset == null) {
                    restingOffset = null
                    false
                } else {
                    val resting = restingOffset ?: offset.also { restingOffset = it }
                    spansWindow && offset <= resting + 0.5f
                }
            }.distinctUntilChanged()
            .collect { latestOnCoversWindowChange(it) }
    }
    DisposableEffect(Unit) {
        onDispose { latestOnCoversWindowChange(false) }
    }
}

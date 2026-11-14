package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Longer than material3's own flatten (DecreasingAmplitudeAnimationSpec, 500 ms), so the wave
// keeps travelling until it is flat and never visibly freezes mid-flatten.
private const val WAVE_SETTLE_MS = 1_000L

/**
 * The `waveSpeed` to hand a material3 wavy progress indicator.
 *
 * material3 runs the wave's phase as an infinite animation that a zero amplitude does not stop: the
 * linear indicator runs it whenever the speed is above zero, the circular one keeps the one it
 * started. A flat, paused bar therefore went on redrawing its whole window on every frame. The
 * speed drops to 0 once the bar has had time to flatten after [waving] turns false, at once when
 * [visible] is false (nothing can see it), and comes back the moment [waving] does.
 */
@Composable
fun wavyIndicatorSpeed(
    waving: Boolean,
    speed: Dp,
    visible: Boolean = true,
): Dp {
    var settled by remember { mutableStateOf(!waving) }
    LaunchedEffect(waving) {
        if (waving) {
            settled = false
        } else {
            delay(WAVE_SETTLE_MS)
            settled = true
        }
    }
    return if (!visible || (!waving && settled)) 0.dp else speed
}

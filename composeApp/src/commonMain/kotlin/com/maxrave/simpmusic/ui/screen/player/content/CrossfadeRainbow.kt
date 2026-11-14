package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.maxrave.simpmusic.extension.hsvToColor

/**
 * The seek bar's colour during a crossfade: the hue cycles once a second, faded in from white over
 * the first 300 ms.
 *
 * Call it ONLY while crossfading (`if (crossfading) crossfadeRainbowColor() else …`). The cycle is
 * an infinite transition, and when the shell ran one unconditionally and read its hue, the whole
 * player recomposed on every frame for as long as it was open, crossfade or not.
 */
@Composable
internal fun crossfadeRainbowColor(): Color {
    val transition = rememberInfiniteTransition(label = "crossfadeRainbow")
    val hue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rainbowHue",
    )
    val fromWhite = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fromWhite.animateTo(1f, tween(300)) }
    return lerp(Color.White, hsvToColor(hue, 1f, 1f), fromWhite.value)
}

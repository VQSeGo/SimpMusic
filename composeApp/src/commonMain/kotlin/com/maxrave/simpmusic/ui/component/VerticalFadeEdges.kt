package com.maxrave.simpmusic.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * True alpha fade at the top/bottom edges of a scrolling region (DstIn mask): content dissolves
 * into whatever is behind it without painting a color and without touching the wrapped component.
 *
 * Both ramps follow smoothstep, the curve [com.maxrave.simpmusic.extension.smoothScrimBrush] uses and
 * for the reason it gives: a linear ramp has a corner where it leaves 0, and the eye reads that corner
 * as an edge.
 */
internal fun Modifier.verticalFadeEdges(
    topFade: Dp,
    bottomFade: Dp,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val topPx = topFade.toPx().coerceAtMost(size.height / 2f)
            val bottomPx = bottomFade.toPx().coerceAtMost(size.height / 2f)
            val topStop = if (size.height > 0f) topPx / size.height else 0f
            val bottomStop = if (size.height > 0f) 1f - bottomPx / size.height else 1f
            drawRect(
                brush = Brush.verticalGradient(colorStops = fadeEdgeStops(topStop, bottomStop)),
                blendMode = BlendMode.DstIn,
            )
        }

private const val FADE_EDGE_STEPS = 24

private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

// Transparent → opaque over [0, topStop], held, then opaque → transparent over [bottomStop, 1]. A
// zero-length ramp is skipped rather than drawn as a pile of stops at one position.
private fun fadeEdgeStops(
    topStop: Float,
    bottomStop: Float,
): Array<Pair<Float, Color>> =
    buildList {
        if (topStop > 0f) {
            for (i in 0..FADE_EDGE_STEPS) {
                val t = i / FADE_EDGE_STEPS.toFloat()
                add(topStop * t to Color.Black.copy(alpha = smoothstep(t)))
            }
        } else {
            add(0f to Color.Black)
        }
        if (bottomStop < 1f) {
            for (i in 0..FADE_EDGE_STEPS) {
                val t = i / FADE_EDGE_STEPS.toFloat()
                add(bottomStop + (1f - bottomStop) * t to Color.Black.copy(alpha = 1f - smoothstep(t)))
            }
        } else {
            add(1f to Color.Black)
        }
    }.toTypedArray()

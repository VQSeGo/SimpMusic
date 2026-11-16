package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The translucent pill the floating controls sit on (back button, like/⋯ pair over artwork,
 * the lyrics and analytics overlays): surfaceContainerHighest at 80%, clipped to [shape].
 */
@Composable
fun Modifier.translucentSurface(shape: Shape = CircleShape): Modifier =
    this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))

/** A [translucentSurface] box wrapping arbitrary [content], e.g. a pill of icon buttons. */
@Composable
fun TranslucentContainer(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.translucentSurface(shape),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** A single icon on a [translucentSurface], e.g. the circular back button of the detail screens. */
@Composable
fun TranslucentIconButton(
    imageVector: ImageVector,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    tint: Color = Color.White,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    TranslucentContainer(modifier = modifier, shape = shape) {
        RippleIconButton(
            imageVector = imageVector,
            tint = tint,
            contentDescription = contentDescription,
            onClick = onClick,
        )
    }
}

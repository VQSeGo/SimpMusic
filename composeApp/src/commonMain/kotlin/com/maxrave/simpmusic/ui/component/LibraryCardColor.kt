package com.maxrave.simpmusic.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.getPlatform
import com.maxrave.simpmusic.ui.theme.LocalIsDarkTheme

/**
 * The container every card on the Your library tab shares. [WrappedEntryCard]'s container, except
 * on a light Desktop: there the panel underneath already is a light grey, and the card would only
 * show as a shadow, so it lifts to white — the same rule the grouped Settings cards follow.
 */
@Composable
internal fun libraryCardColor(): Color =
    if (getPlatform() == Platform.Desktop && !LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

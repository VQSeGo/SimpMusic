package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.materialkolor.ktx.themeColorOrNull
import com.maxrave.simpmusic.expect.ui.toReadableBitmap

// The artwork is scored at this size: the seed depends on which colours cover the sleeve and how
// strongly, which a full-resolution read would only make slower to find.
private const val SEED_SAMPLE_PX = 112

/**
 * The Now Playing scheme's seed, chosen the way Material You seeds from a wallpaper: quantize the
 * artwork and score its colours by population and chroma. Null for a colourless cover or a bitmap
 * that cannot be read. Slow enough to keep off the main thread.
 */
internal fun ImageBitmap.artworkSeedColor(): Color? =
    runCatching { toReadableBitmap(SEED_SAMPLE_PX, SEED_SAMPLE_PX).themeColorOrNull() }.getOrNull()

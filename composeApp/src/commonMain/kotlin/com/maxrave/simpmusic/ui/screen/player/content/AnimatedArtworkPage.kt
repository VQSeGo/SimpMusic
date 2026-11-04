package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.simpmusic.expect.ui.MediaPlayerView
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.extension.smoothScrimBrush
import com.maxrave.simpmusic.ui.screen.player.content.applemusic.AppleMusicMeshBackdrop
import com.maxrave.simpmusic.ui.screen.player.content.applemusic.appleMusicFadeBetween
import com.maxrave.simpmusic.ui.screen.player.content.applemusic.rememberAppleMusicMesh
import com.maxrave.simpmusic.ui.theme.seed
import com.maxrave.simpmusic.viewModel.NowPlayingScreenData

/**
 * Apple Music's animated artwork, for the Material 3 Expressive page to lay over itself: the clip
 * edge to edge at the top, dissolving into a mesh of its own colours, with the band under the style's top bar (which ends at [topChrome]) blurred and
 * darkened so the bar's white glyphs survive a bright sleeve. Fills its parent, the style's first
 * screen, and fades into [pageColor] at the bottom so scrolling past the fold shows no seam.
 *
 * Nothing shows until the clip's still is up: the style's own page stays in view until then, and
 * this page fades in over it instead of jumping from a square cover to a full-width one. [cover]
 * seeds the mesh until the still has been read.
 */
@Composable
internal fun AppleMusicAnimatedArtworkPage(
    canvas: NowPlayingScreenData.CanvasData,
    cover: ImageBitmap?,
    topChrome: Dp,
    pageColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    var clipStill by remember(canvas.url) { mutableStateOf<ImageBitmap?>(null) }
    var clipFrameHeight by remember(canvas.url) { mutableStateOf(0.dp) }
    val mesh = rememberAppleMusicMesh(artwork = clipStill ?: cover, seed = canvas.url.hashCode())
    val pageAlpha by animateFloatAsState(
        targetValue = if (clipFrameHeight > 0.dp && mesh != null) 1f else 0f,
        animationSpec = tween(ARTWORK_FADE_IN_MS),
        label = "animatedArtworkPageIn",
    )
    BoxWithConstraints(modifier = modifier.fillMaxSize().alpha(pageAlpha)) {
        val artworkFade = minOf(maxWidth, maxHeight * ARTWORK_MAX_HEIGHT_FRACTION) * ARTWORK_FADE_FRACTION
        val topBand = topChrome + TOP_BAND_STRIP
        AppleMusicMeshBackdrop(mesh = mesh, seam = clipFrameHeight)
        AnimatedArtworkFrame(
            canvas = canvas,
            heightLimit = maxHeight * ANIMATED_ARTWORK_MAX_HEIGHT_FRACTION,
            topBandSolid = statusBarHeight,
            topBand = topBand,
            alpha = 1f,
            onStill = { still, height ->
                clipStill = still
                clipFrameHeight = height
            },
        )
        // The page laid back over the clip's lower part, as in AppleMusicMainView: a mask never
        // reaches the video surface.
        if (clipFrameHeight > 0.dp) {
            AppleMusicMeshBackdrop(
                mesh = mesh,
                seam = clipFrameHeight,
                modifier = Modifier.appleMusicFadeBetween(transparentAt = clipFrameHeight - artworkFade, opaqueAt = clipFrameHeight),
            )
        }
        // The wash under the top bar, held through the status bar and eased out by the band's end:
        // the bar's title sits inside it, so it cannot fall off as fast as the grabber's does.
        val washAlpha by animateFloatAsState(
            targetValue = TOP_WASH_MIN_ALPHA + (TOP_WASH_MAX_ALPHA - TOP_WASH_MIN_ALPHA) * (mesh?.topLuminance ?: 1f),
            animationSpec = tween(TOP_WASH_FADE_MS),
            label = "animatedArtworkTopWash",
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(topBand)
                    .background(
                        smoothScrimBrush(
                            from = Color.Black.copy(alpha = washAlpha),
                            to = Color.Black.copy(alpha = 0f),
                            startFraction = statusBarHeight / topBand,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        smoothScrimBrush(
                            from = pageColor.copy(alpha = 0f),
                            to = pageColor,
                            startFraction = PAGE_COLOR_FADE_START,
                        ),
                    ),
        )
    }
}

/**
 * Apple Music's animated artwork in a frame of its own shape, full width and top-aligned, so none
 * of it is cropped. The clip's still sits under it: it sizes the frame (its proportions ARE the
 * clip's) and shows while the clip loads, so the frame never opens as a black box. [onStill] hands
 * the still and the frame's height back for the page's mesh, and for the copy of the page that
 * AppleMusicMainView lays over the frame's lower part — the frame itself carries no fade mask,
 * because a mask never reaches the video surface.
 */
@Composable
private fun BoxScope.AnimatedArtworkFrame(
    canvas: NowPlayingScreenData.CanvasData,
    heightLimit: Dp,
    topBandSolid: Dp,
    topBand: Dp,
    alpha: Float,
    onStill: (ImageBitmap?, Dp) -> Unit,
    topInset: Dp = 0.dp,
) {
    BoxWithConstraints(modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset).fillMaxWidth()) {
        var aspect by remember(canvas.url) { mutableFloatStateOf(DEFAULT_ANIMATED_ARTWORK_ASPECT) }
        val frameWidth = maxWidth
        val frameHeight = (frameWidth / aspect).coerceAtMost(heightLimit)
        // No still to wait for (an older cached row): show the frame straight away.
        LaunchedEffect(canvas.url, canvas.thumbUrl) {
            if (canvas.thumbUrl == null) onStill(null, frameHeight)
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(frameHeight)
                    .alpha(alpha),
        ) {
            val stillRequest =
                canvas.thumbUrl?.let { still ->
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(still)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(still)
                        .build()
                }
            stillRequest?.let { request ->
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = {
                        val image = it.result.image
                        if (image.width > 0 && image.height > 0) aspect = image.width.toFloat() / image.height
                        onStill(image.toImageBitmap(), (frameWidth / aspect).coerceAtMost(heightLimit))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // The frame already has the clip's own shape, so "crop to bounds" crops nothing; it is
            // the path that sizes the surface from Media3's own presentation state (see the canvas
            // note in AppleMusicArtworkPage) and keeps a shutter up until the first frame is ready.
            MediaPlayerView(
                url = canvas.url,
                cropToBounds = true,
                modifier = Modifier.fillMaxSize(),
            )
            // Nothing can blur the video surface itself, so its band is the still's, blurred: at 32dp
            // it is a wash of the clip's colours, which barely move in the strip under the status bar.
            stillRequest?.let { TopBandBlur(model = it, solidTo = topBandSolid, fadeTo = topBand) }
        }
    }
}

/**
 * The band under the status bar and the grabber, blurred: a sharp, busy sleeve there fights their
 * glyphs. A Modifier.blur copy of the picture, solid down to [solidTo] and gone by [fadeTo] — the
 * artist page's technique. Below Android 12 blur is a no-op, the copy is then pixel-identical to
 * the picture under it, and only the page's dark wash remains. Nothing when [fadeTo] is zero.
 */
@Composable
private fun TopBandBlur(
    model: Any?,
    solidTo: Dp,
    fadeTo: Dp,
    modifier: Modifier = Modifier,
) {
    if (fadeTo <= 0.dp) return
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .fillMaxSize()
                .appleMusicFadeBetween(transparentAt = fadeTo, opaqueAt = solidTo)
                .blur(TOP_BAND_BLUR),
    )
}

// The top band's length below the status bar, the wash's range (16% on a black sleeve to 65% on a
// white one), its change on a skip (the mesh's own crossfade), and the band's blur.
private val TOP_BAND_STRIP = 32.dp
private const val TOP_WASH_MIN_ALPHA = 0.16f
private const val TOP_WASH_MAX_ALPHA = 0.65f
private const val TOP_WASH_FADE_MS = 900
private val TOP_BAND_BLUR = 32.dp

// The bottom share of the artwork that dissolves into the page.
private const val ARTWORK_FADE_FRACTION = 0.42f

// A full-width square cover fits a portrait page with room to spare; only a landscape window is
// short enough for this cap to bite.
private const val ARTWORK_MAX_HEIGHT_FRACTION = 0.62f

// A 3:4 animated artwork runs under the title row by design; this only stops a landscape window
// from turning it into the whole page.
private const val ANIMATED_ARTWORK_MAX_HEIGHT_FRACTION = 0.75f

// Apple's tall motion artwork (motionDetailTall), which the artwork source prefers — the frame's
// shape until the clip's still says otherwise.
private const val DEFAULT_ANIMATED_ARTWORK_ASPECT = 3f / 4f

// How long an animated artwork takes to replace the cover once it is ready.
private const val ARTWORK_FADE_IN_MS = 420

// Where, down the first screen, the animated artwork's page starts fading into the page below the
// fold — late enough that the mesh still reaches the controls at the bottom of the screen.
private const val PAGE_COLOR_FADE_START = 0.92f

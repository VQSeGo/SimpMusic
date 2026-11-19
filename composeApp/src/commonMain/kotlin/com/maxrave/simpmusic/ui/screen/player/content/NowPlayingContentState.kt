package com.maxrave.simpmusic.ui.screen.player.content

import com.maxrave.domain.mediaservice.player.DownloadedClip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.maxrave.domain.data.entities.NewFormatEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.streams.TimeLine
import com.maxrave.domain.mediaservice.handler.ControlState
import com.maxrave.simpmusic.extension.GradientOffset
import com.maxrave.simpmusic.viewModel.NowPlayingScreenData
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

// Backdrop behind the player. A dark surface rather than pure black: #000000 reads as a hole
// next to the artwork-tinted gradient and cards, which is why Spotify sits its player on a
// near-black surface instead. Used for the gradient's end colour, the fade-to target and the
// area below the gradient so all three match exactly and leave no seam.
internal val PlayerBackdropColor = Color(0xFF121212)

private val RICH_SYNC_TIMESTAMP_REGEX = Regex("""<\d{2}:\d{2}\.\d{2,3}>\s*""")
private val WHITESPACE_REGEX = Regex("""\s+""")

// Word-by-word lyrics carry a timestamp per word; replace each with a space
// (not ""), then collapse — otherwise the words run together.
// Shared by every Now Playing content style (Spotify + M3 Expressive).
internal fun String.stripRichSyncTimestamps(): String =
    replace(RICH_SYNC_TIMESTAMP_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

/**
 * "154 kbps · 48 kHz" — the Apple Music style's quality line, read off the format YouTube served
 * for the track now playing: its `bitrate` (the figure the Info sheet prints in bps) and its
 * `sampleRate`. A figure the format does not carry is dropped rather than guessed, and null comes
 * back when neither is known, so the line hides instead of showing a placeholder.
 */
internal fun NewFormatEntity?.toAudioQualityLabel(): String? {
    val format = this ?: return null
    val parts =
        buildList {
            format.bitrate?.takeIf { it > 0 }?.let { add("${(it / 1000.0).roundToInt()} kbps") }
            format.sampleRate?.takeIf { it > 0 }?.let { add(it.toKhzLabel()) }
        }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

// 48000 → "48 kHz", 44100 → "44.1 kHz": one decimal, and none when it would be ".0".
private fun Int.toKhzLabel(): String {
    val tenths = (this + 50) / 100
    return if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
}

/**
 * Everything a Now Playing content layer reads. The shell ([com.maxrave.simpmusic.ui.screen.player.NowPlayingScreenContent])
 * owns the ViewModel collection, palette animation, sheets/dialogs and gesture state machines;
 * a content composable only renders from this snapshot.
 *
 * The values that move while a track plays — the position ([timelineState], every 50 ms), the
 * seek bar ([sliderValue]) and the inline lyric line ([currentLyricLineIndex]) — are NOT copied in.
 * They are read through their State holders at the getter, so a read lands in the composable that
 * actually draws the value and only that scope recomposes. Copied in as plain values, the shell had
 * to read them itself, and every tick rebuilt this object and recomposed the whole player.
 */
@Stable
class NowPlayingContentState(
    val screenData: NowPlayingScreenData,
    val controllerState: ControlState,
    private val timelineSource: State<TimeLine>,
    /** The position for the lyrics card, which holds still while the fullscreen lyrics page covers the player. */
    val timelineFlow: StateFlow<TimeLine>,
    val shouldShowVideo: Boolean,
    val isUserLoggedIn: Boolean,
    /** The album of the track playing, or null when it has none to open. */
    val albumId: String? = null,
    val artworkQueue: List<Track>,
    val currentOrderIndex: Int,
    val artworkPagerState: PagerState,
    val startColor: Animatable<Color, AnimationVector4D>,
    val endColor: Animatable<Color, AnimationVector4D>,
    val spotShadowColor: Color,
    val gradientOffset: GradientOffset,
    private val sliderValueSource: State<Float>,
    private val currentLyricLineIndexSource: State<Int>,
    val shouldShowToolbar: Boolean,
    val isInPipMode: Boolean,
    val mainScrollState: ScrollState,
    val isExpanded: Boolean,
    val dismissIcon: ImageVector,
    /** "154 kbps · 48 kHz" for the stream now playing, or null while unknown — see [toAudioQualityLabel]. */
    val audioQualityLabel: String? = null,
    /**
     * The user's lyrics delay, applied at read time. [currentLyricLineIndex] already has it baked in;
     * this is for anything that times WITHIN a line (the Apple Music lyric strip's word sweep).
     */
    val lyricsOffsetMs: Long = 0L,
    /**
     * Width / height of the video now playing, 16:9 until the player knows it. Every style sizes
     * its video frame from this one value, so a frame and the spacer that measures it cannot drift.
     */
    val videoAspectRatio: Float = 16f / 9,
    /** This track's artwork seed when it is already known (see SharedViewModel.artworkSeed). */
    val artworkSeed: Color? = null,
    /**
     * The track is a clip downloaded as audio only and "watch video" is on: the cover gets a button that
     * shows its video from the network, or hides it again. Null hides the button.
     */
    val downloadedClip: DownloadedClip? = null,
) {
    val timelineState: TimeLine get() = timelineSource.value

    /** The seek bar's position on the 0..100 scale: the player's, or the finger's while scrubbing. */
    val sliderValue: Float get() = sliderValueSource.value

    /** Index of the synced lyric line being heard, the offset already applied; -1 when there is none. */
    val currentLyricLineIndex: Int get() = currentLyricLineIndexSource.value
}

/**
 * Everything a Now Playing content layer can do. All callbacks land in the shell, which owns
 * the ViewModel, the navController and the sheet/dialog visibility flags.
 */
@Stable
class NowPlayingContentActions(
    val onUIEvent: (UIEvent) -> Unit,
    val onSeekToQueueIndex: (Int) -> Unit,
    val onArtworkBitmap: (ImageBitmap) -> Unit,
    val onSliderChange: (Float) -> Unit,
    val onSliderChangeFinished: () -> Unit,
    /** Opens the track's artist, or asks which one when several are credited. */
    val onNavigateToArtist: () -> Unit,
    val onNavigateToAlbum: () -> Unit,
    val onOpenListenTogether: () -> Unit,
    val onShowMoreSheet: () -> Unit,
    val onShowQueue: () -> Unit,
    val onShowInfo: () -> Unit,
    val onShowAddToPlaylist: () -> Unit,
    val onShowFullscreenLyrics: () -> Unit,
    /** Writes the lyrics timing offset of the track playing. */
    val onLyricsOffsetChange: (Int) -> Unit,
    val onEnterFullscreenVideo: () -> Unit,
    /** Shows (true) or hides the video of the downloaded clip playing, see [NowPlayingContentState.downloadedClip]. */
    val onShowDownloadedClip: (Boolean) -> Unit = {},
    val onDismiss: () -> Unit,
    val onToolbarVisibilityChange: (Boolean) -> Unit,
    /** Reorders the queue. `from`/`to` are absolute indices into [NowPlayingContentState.artworkQueue]. */
    val onMoveQueueItem: (from: Int, to: Int) -> Unit,
    /** Removes one queue entry. `index` is an absolute index into [NowPlayingContentState.artworkQueue]. */
    val onRemoveQueueItem: (index: Int) -> Unit,
    /** Opens the full song sheet for one queue entry; `index` is absolute, as above. */
    val onQueueItemMore: (index: Int, track: Track) -> Unit,
)

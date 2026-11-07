package com.maxrave.simpmusic.ui.screen.player

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.snapshotFlow
import com.maxrave.domain.data.model.browse.album.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * Pure helpers for the Spotify-style artwork pager on [NowPlayingScreen].
 *
 * Extracted to keep the sync/dispatch logic unit-testable without Compose runtime.
 */

/**
 * Maps the currently playing track back to its position in the queue (`listTracks`).
 *
 * - [playerOrderIndex] is `MediaPlayerHandler#currentOrderIndex()`, i.e. the PLAYER's own
 *   position, and it is preferred because it is the only answer that survives a queue holding
 *   the same `videoId` twice. Nothing dedupes the queue — an endless/radio tail, or "Add to
 *   queue" on a track already in it, is enough — and searching by id then returns the LAST copy
 *   while the player sits on an earlier one. Everything downstream inherits that: the Apple
 *   Music queue cuts its "up next" list at `index + 1` and swallows every track in between, a
 *   swipe on the artwork pager sends Next from the wrong slot, and a row's ⋯ opens on a
 *   different song than the one touched.
 * - It is cross-checked against [nowPlayingVideoId] rather than trusted outright: while the
 *   queue is being rebuilt, `listTracks` and the player timeline are briefly out of step, and an
 *   index that points at some other track is worse than the id search. Failing that check falls
 *   back to the search below, so this is never worse than what it replaced.
 * - The search uses `videoId` (already prefix-stripped by
 *   `MediaServiceHandlerImpl#getDataOfNowPlayingState`). Do **not** pass
 *   `nowPlayingState.mediaItem.mediaId` — for video items it carries the
 *   `MERGING_DATA_TYPE.VIDEO` ("Video") prefix and the lookup will silently fail.
 * - Uses `indexOfLast`, mirroring what `MediaServiceHandlerImpl#currentOrderIndex` itself does
 *   in the one case it has no better answer either (shuffle on, where the timeline index belongs
 *   to a different order than `listTracks`).
 * - Coerces to `0` when the track isn't found, so the pager stays on the first slot
 *   instead of throwing during a transient queue/now-playing mismatch.
 */
internal fun deriveOrderIndex(
    queue: List<Track>,
    nowPlayingVideoId: String?,
    playerOrderIndex: Int,
): Int {
    if (queue.isEmpty()) return 0
    if (playerOrderIndex in queue.indices &&
        (nowPlayingVideoId.isNullOrEmpty() || queue[playerOrderIndex].videoId == nowPlayingVideoId)
    ) {
        return playerOrderIndex
    }
    if (nowPlayingVideoId.isNullOrEmpty()) return 0
    return queue
        .indexOfLast { it.videoId == nowPlayingVideoId }
        .coerceAtLeast(0)
}

/**
 * One stable key per queue entry, for the artwork pager.
 *
 * A key must not contain the page index. A pager follows its current page by key, so with stable
 * keys a reorder (shuffle on or off, a radio trimming its history) moves the page along in the same
 * measure pass and the cover stays put. With the index in the key every moved entry is a page the
 * pager has never seen: it shows whichever track now holds that index for a frame, then jumps to a
 * freshly composed page, and the cover blinks.
 *
 * Keys have to be unique, and a queue can hold one song twice, hence the count of earlier copies.
 */
internal fun artworkPageKeys(videoIds: List<String>): List<String> {
    val seen = HashMap<String, Int>(videoIds.size)
    return videoIds.map { videoId ->
        val copy = seen[videoId] ?: 0
        seen[videoId] = copy + 1
        "artwork_${videoId}_$copy"
    }
}

/**
 * Brings the pager back to the track the player is on whenever it rests anywhere else.
 *
 * The pager follows its page by key, so a queue replaced under it takes it along with the old
 * track. When the new track lands on the old one's index and the old one right after it, the index
 * does not change, nothing tells the pager to move, and it rests on the next track's cover. A scroll
 * cut short leaves it out of step just the same, between two pages.
 *
 * The wait gives a finished swipe time to reach the player: until the track changes, the pager is
 * ahead of it on purpose.
 */
internal suspend fun PagerState.stayOnPlayerTrack(
    orderIndex: () -> Int,
    queueSize: () -> Int,
    moveTo: suspend (Int) -> Unit,
) {
    fun outOfStep(): Boolean {
        val index = orderIndex()
        return !isScrollInProgress &&
            index in 0 until queueSize() &&
            (currentPage != index || abs(currentPageOffsetFraction) > PAGE_REST_TOLERANCE)
    }
    while (true) {
        snapshotFlow { outOfStep() }.first { it }
        delay(ARTWORK_RESYNC_DELAY_MS)
        if (!outOfStep()) continue
        try {
            moveTo(orderIndex())
        } catch (e: CancellationException) {
            // A drag taking over the pager cancels the scroll, not this loop.
            currentCoroutineContext().ensureActive()
        }
    }
}

private const val ARTWORK_RESYNC_DELAY_MS = 800L
private const val PAGE_REST_TOLERANCE = 0.01f

/**
 * What the player should do when the pager settles on a new page.
 *
 * - [Next] / [Previous] keep the existing crossfade flow on Android intact
 *   (CrossfadeExoPlayerAdapter relies on `player.next()` / `player.previous()`).
 * - [Skip] jumps to a non-adjacent index via `playMediaItemInMediaSource`, which
 *   internally handles unshuffling.
 */
internal sealed interface ArtworkSeekAction {
    data object Next : ArtworkSeekAction

    data object Previous : ArtworkSeekAction

    data class Skip(val index: Int) : ArtworkSeekAction

    /** Same page — caller should ignore (the LaunchedEffect filter normally prevents this). */
    data object NoOp : ArtworkSeekAction
}

internal fun computeSeekAction(
    newPage: Int,
    currentOrderIndex: Int,
): ArtworkSeekAction =
    when (newPage - currentOrderIndex) {
        0 -> ArtworkSeekAction.NoOp
        1 -> ArtworkSeekAction.Next
        -1 -> ArtworkSeekAction.Previous
        else -> ArtworkSeekAction.Skip(newPage)
    }

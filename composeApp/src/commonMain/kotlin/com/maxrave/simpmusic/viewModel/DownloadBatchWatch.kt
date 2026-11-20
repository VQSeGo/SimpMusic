package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.entities.DownloadState.STATE_DOWNLOADED
import com.maxrave.domain.data.entities.DownloadState.STATE_DOWNLOADING
import com.maxrave.domain.data.entities.DownloadState.STATE_PREPARING
import kotlinx.coroutines.delay

/** Where a collection's download batch stands, as [DownloadBatchWatch.status] reads it. */
enum class BatchStatus {
    /** The mark is not "downloading", or every track is downloaded: nothing to settle here. */
    IDLE,

    /** Some track is downloading or queued. */
    RUNNING,

    /** Marked "downloading", but no track has been seen downloading or queued yet. */
    STARTING,

    /** Marked "downloading", tracks were in flight and none is now, yet not all are downloaded. */
    ENDED,
}

/**
 * Tells when a playlist or an album marked "downloading" has no batch behind the mark any more.
 *
 * `DownloadHandler.downloadTask` cannot be read as "finished and not downloaded" the moment a batch
 * starts: it still holds the old "not downloaded" of an earlier failed try (or nothing at all, on Android
 * the entries come from the download manager a moment after the request), so a mark reset on the first
 * emission would drop before a single transfer began. The watch therefore waits for a track to be seen
 * in flight first ([BatchStatus.RUNNING]); only after that does "none in flight, not all downloaded"
 * mean the batch is over ([BatchStatus.ENDED]), a track that failed, is gone or is closed in the region.
 * One instance per watching job: a new batch starts with a new instance.
 */
class DownloadBatchWatch {
    private var seenInFlight = false

    /** [trackStates]: the `downloadTask` state of every track of the collection (null: nothing reported). */
    fun status(
        trackStates: List<Int?>,
        mark: Int,
    ): BatchStatus {
        if (mark != STATE_DOWNLOADING || trackStates.isEmpty()) {
            seenInFlight = false
            return BatchStatus.IDLE
        }
        if (trackStates.any { it == STATE_DOWNLOADING || it == STATE_PREPARING }) {
            seenInFlight = true
            return BatchStatus.RUNNING
        }
        if (trackStates.all { it == STATE_DOWNLOADED }) {
            seenInFlight = false
            return BatchStatus.IDLE
        }
        return if (seenInFlight) BatchStatus.ENDED else BatchStatus.STARTING
    }

    companion object {
        /**
         * How long a batch that was seen in flight may stay quiet before it counts as over: between two
         * tracks of one batch (Android adds them one by one) nothing is in flight for a moment.
         */
        const val ENDED_SETTLE_MS = 2_000L

        /**
         * How long a mark may say "downloading" with no track ever seen in flight before it counts as
         * stuck: a batch of tracks that were all refused at once (a live stream), or one that finished
         * while the screen was closed. Long enough for Android's service to report the first track.
         */
        const val STARTING_GRACE_MS = 15_000L
    }
}

/**
 * True once the collection's "downloading" mark should go back to "not downloaded", so that the download
 * can be tried again. Suspends for the settle time first, so the caller has to be a `collectLatest` body:
 * the next change of the states cancels the wait.
 */
suspend fun DownloadBatchWatch.markShouldEnd(
    trackStates: List<Int?>,
    mark: Int,
): Boolean =
    when (status(trackStates, mark)) {
        BatchStatus.ENDED -> {
            delay(DownloadBatchWatch.ENDED_SETTLE_MS)
            true
        }
        BatchStatus.STARTING -> {
            delay(DownloadBatchWatch.STARTING_GRACE_MS)
            true
        }
        BatchStatus.IDLE, BatchStatus.RUNNING -> false
    }

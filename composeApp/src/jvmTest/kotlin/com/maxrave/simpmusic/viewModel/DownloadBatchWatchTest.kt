package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.entities.DownloadState.STATE_DOWNLOADED
import com.maxrave.domain.data.entities.DownloadState.STATE_DOWNLOADING
import com.maxrave.domain.data.entities.DownloadState.STATE_NOT_DOWNLOADED
import com.maxrave.domain.data.entities.DownloadState.STATE_PREPARING
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadBatchWatchTest {
    @Test
    fun staleNotDownloadedAtStartIsNotTheEnd() {
        val watch = DownloadBatchWatch()
        // The batch has just started: the map still holds the old failure of one track.
        val stale = listOf(STATE_NOT_DOWNLOADED, STATE_DOWNLOADED, null)
        assertEquals(BatchStatus.STARTING, watch.status(stale, STATE_DOWNLOADING))
    }

    @Test
    fun batchEndsOnlyAfterATrackWasSeenInFlight() {
        val watch = DownloadBatchWatch()
        assertEquals(BatchStatus.STARTING, watch.status(listOf(STATE_NOT_DOWNLOADED, STATE_NOT_DOWNLOADED), STATE_DOWNLOADING))
        assertEquals(BatchStatus.RUNNING, watch.status(listOf(STATE_DOWNLOADING, STATE_PREPARING), STATE_DOWNLOADING))
        // Between two tracks: queued counts as in flight.
        assertEquals(BatchStatus.RUNNING, watch.status(listOf(STATE_DOWNLOADED, STATE_PREPARING), STATE_DOWNLOADING))
        // One finished, the other failed.
        assertEquals(BatchStatus.ENDED, watch.status(listOf(STATE_DOWNLOADED, STATE_NOT_DOWNLOADED), STATE_DOWNLOADING))
    }

    @Test
    fun aTrackNeverReportedStillEndsTheBatch() {
        val watch = DownloadBatchWatch()
        watch.status(listOf(STATE_DOWNLOADING, null), STATE_DOWNLOADING)
        assertEquals(BatchStatus.ENDED, watch.status(listOf(STATE_DOWNLOADED, null), STATE_DOWNLOADING))
    }

    @Test
    fun everyTrackDownloadedIsNotAnEndOfAFailedBatch() {
        val watch = DownloadBatchWatch()
        watch.status(listOf(STATE_DOWNLOADING), STATE_DOWNLOADING)
        assertEquals(BatchStatus.IDLE, watch.status(listOf(STATE_DOWNLOADED, STATE_DOWNLOADED), STATE_DOWNLOADING))
    }

    @Test
    fun otherMarksAreLeftAlone() {
        val watch = DownloadBatchWatch()
        assertEquals(BatchStatus.IDLE, watch.status(listOf(STATE_NOT_DOWNLOADED), STATE_NOT_DOWNLOADED))
        assertEquals(BatchStatus.IDLE, watch.status(listOf(STATE_NOT_DOWNLOADED), STATE_DOWNLOADED))
        assertEquals(BatchStatus.IDLE, watch.status(emptyList(), STATE_DOWNLOADING))
    }

    @Test
    fun aNewMarkForgetsTheOldBatch() {
        val watch = DownloadBatchWatch()
        watch.status(listOf(STATE_DOWNLOADING), STATE_DOWNLOADING)
        watch.status(listOf(STATE_NOT_DOWNLOADED), STATE_NOT_DOWNLOADED)
        // The user taps download again: the stale failure must not end the new batch at once.
        assertEquals(BatchStatus.STARTING, watch.status(listOf(STATE_NOT_DOWNLOADED), STATE_DOWNLOADING))
    }
}

package com.maxrave.simpmusic.ui.screen.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ArtworkPagerSyncTest {
    @Test
    fun `without the resync a new queue drags the pager off the playing track`() =
        runComposeUiTest {
            var queue by mutableStateOf(listOf("old", "x"))
            lateinit var pager: PagerState
            setContent {
                pager = rememberPagerState(pageCount = { queue.size })
                val keys = artworkPageKeys(queue)
                HorizontalPager(pager, Modifier.size(100.dp), key = { keys.getOrNull(it) ?: "artwork_empty_$it" }) { Box(Modifier.fillMaxSize()) }
            }
            waitForIdle()

            queue = listOf("new", "old", "x")
            waitForIdle()

            assertEquals(1, pager.currentPage)
        }

    @Test
    fun `a new queue with the old track next leaves the pager on the playing track`() =
        runComposeUiTest {
            var queue by mutableStateOf(listOf("old", "x"))
            lateinit var pager: PagerState
            setContent {
                pager = rememberPagerState(pageCount = { queue.size })
                val keys = artworkPageKeys(queue)
                HorizontalPager(pager, Modifier.size(100.dp), key = { keys.getOrNull(it) ?: "artwork_empty_$it" }) { Box(Modifier.fillMaxSize()) }
                LaunchedEffect(pager) {
                    pager.stayOnPlayerTrack(
                        orderIndex = { 0 },
                        queueSize = { queue.size },
                        moveTo = { pager.scrollToPage(it) },
                    )
                }
            }
            waitForIdle()

            queue = listOf("new", "old", "x")
            mainClock.advanceTimeBy(2_000)
            waitForIdle()

            assertEquals(0, pager.currentPage)
        }
}

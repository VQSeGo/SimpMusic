package com.maxrave.simpmusic.utils

import com.maxrave.domain.data.model.metadata.Line
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.simpmusic.utils.LyricsSource.BETTER_LYRICS
import com.maxrave.simpmusic.utils.LyricsSource.LRCLIB
import com.maxrave.simpmusic.utils.LyricsSource.SIMPMUSIC
import com.maxrave.simpmusic.utils.LyricsSource.SPOTIFY
import com.maxrave.simpmusic.utils.LyricsSource.YOUTUBE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsSourceQueueTest {
    private fun List<LyricsSourceEntry>.order() = map { it.source }

    private fun List<LyricsSourceEntry>.off() = filterNot { it.enabled }.map { it.source }

    @Test
    fun `nothing saved reads as no queue`() {
        assertNull(LyricsSourceQueue.decode(null))
        assertNull(LyricsSourceQueue.decode(""))
        assertNull(LyricsSourceQueue.decode("  "))
    }

    @Test
    fun `a saved queue reads back as written`() {
        val queue =
            listOf(
                LyricsSourceEntry(LRCLIB, true),
                LyricsSourceEntry(SPOTIFY, false),
                LyricsSourceEntry(YOUTUBE, true),
                LyricsSourceEntry(SIMPMUSIC, false),
                LyricsSourceEntry(BETTER_LYRICS, true),
            )
        assertEquals("lrclib:1,spotify:0,youtube:1,simpmusic:0,better_lyrics:1", LyricsSourceQueue.encode(queue))
        assertEquals(queue, LyricsSourceQueue.decode(LyricsSourceQueue.encode(queue)))
    }

    @Test
    fun `unknown and repeated ids are skipped, missing sources join the end switched on`() {
        val queue = LyricsSourceQueue.decode("musixmatch:1,spotify:0,lrclib:0,spotify:1")!!
        assertEquals(listOf(SPOTIFY, LRCLIB, SIMPMUSIC, BETTER_LYRICS, YOUTUBE), queue.order())
        assertEquals(listOf(SPOTIFY, LRCLIB), queue.off())
    }

    @Test
    fun `a string with no known id is every source in the default order`() {
        val queue = LyricsSourceQueue.decode("canvas:1")!!
        assertEquals(LyricsSource.entries, queue.order())
        assertTrue(queue.off().isEmpty())
    }

    @Test
    fun `migration puts the old main provider first and keeps the old fallback order`() {
        val queue = LyricsSourceQueue.migrate("lrclib", spotifyFallbackOn = true)
        assertEquals(listOf(LRCLIB, SIMPMUSIC, SPOTIFY, BETTER_LYRICS, YOUTUBE), queue.order())
        assertTrue(queue.off().isEmpty())
    }

    @Test
    fun `migration keeps Spotify off unless it was asked before`() {
        assertEquals(listOf(SPOTIFY), LyricsSourceQueue.migrate("youtube", spotifyFallbackOn = false).off())
        val spotifyMain = LyricsSourceQueue.migrate("spotify", spotifyFallbackOn = false)
        assertEquals(SPOTIFY, spotifyMain.first().source)
        assertTrue(spotifyMain.off().isEmpty())
    }

    @Test
    fun `migration from an unknown or missing provider starts with SimpMusic`() {
        assertEquals(SIMPMUSIC, LyricsSourceQueue.migrate("ai", spotifyFallbackOn = false).first().source)
        assertEquals(SIMPMUSIC, LyricsSourceQueue.migrate(null, spotifyFallbackOn = false).first().source)
    }

    @Test
    fun `a saved queue wins over the old settings`() {
        val queue = LyricsSourceQueue.resolve("youtube:1", oldMain = "lrclib", spotifyFallbackOn = false)
        assertEquals(YOUTUBE, queue.first().source)
        assertTrue(queue.off().isEmpty())
    }

    @Test
    fun `an answer with no text is no lyrics`() {
        fun lyrics(vararg words: String) = Lyrics(lines = words.map { Line("0", "0", words = it) }, syncType = "LINE_SYNCED")
        assertFalse(Lyrics(lines = null, syncType = null).hasText())
        assertFalse(lyrics().hasText())
        assertFalse(lyrics(" ", "").hasText())
        assertFalse(lyrics("la").copy(error = true).hasText())
        assertTrue(lyrics("", "la").hasText())
    }

    @Test
    fun `only an unsynced or untyped answer is plain`() {
        assertTrue(Lyrics(lines = null, syncType = "LINE_SYNCED").isSynced())
        assertTrue(Lyrics(lines = null, syncType = "RICH_SYNCED").isSynced())
        assertTrue(Lyrics(lines = null, syncType = "SYLLABLE_SYNCED").isSynced())
        assertFalse(Lyrics(lines = null, syncType = "UNSYNCED").isSynced())
        assertFalse(Lyrics(lines = null, syncType = null).isSynced())
    }
}

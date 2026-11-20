package com.maxrave.simpmusic.utils

import com.maxrave.simpmusic.utils.LyricsSource.BETTER_LYRICS
import com.maxrave.simpmusic.utils.LyricsSource.LRCLIB
import com.maxrave.simpmusic.utils.LyricsSource.SPOTIFY
import com.maxrave.simpmusic.utils.LyricsSource.YOUTUBE
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsChoiceTest {
    @Test
    fun `picks read back as written, the latest last`() {
        val choices =
            LyricsChoices.with(
                LyricsChoices.with(emptyMap(), "kjTmkExfxs0", LyricsChoice(SPOTIFY, 1)),
                "a-B_c",
                LyricsChoice(LRCLIB, 0),
            )
        val raw = LyricsChoices.encode(choices)
        assertEquals("kjTmkExfxs0=spotify:1;a-B_c=lrclib:0", raw)
        assertEquals(choices, LyricsChoices.decode(raw))
        // Picking again for a track moves it to the end with the new pick.
        val again = LyricsChoices.with(choices, "kjTmkExfxs0", LyricsChoice(YOUTUBE, 0))
        assertEquals("a-B_c=lrclib:0;kjTmkExfxs0=youtube:0", LyricsChoices.encode(again))
    }

    @Test
    fun `unknown sources and broken entries are skipped`() {
        assertEquals(emptyMap(), LyricsChoices.decode(null))
        assertEquals(emptyMap(), LyricsChoices.decode(""))
        assertEquals(
            mapOf("b" to LyricsChoice(LRCLIB, 2)),
            LyricsChoices.decode("a=musixmatch:1;=lrclib:0;c=lrclib:x;d=spotify:-1;b=lrclib:2"),
        )
    }

    @Test
    fun `only the latest picks are kept`() {
        var choices = emptyMap<String, LyricsChoice>()
        repeat(LyricsChoices.LIMIT + 3) { choices = LyricsChoices.with(choices, "v$it", LyricsChoice(LRCLIB, 0)) }
        assertEquals(LyricsChoices.LIMIT, choices.size)
        assertEquals("v3", choices.keys.first())
    }

    @Test
    fun `the walk goes on from the lyrics on screen and comes round to them`() {
        val sources = listOf(LRCLIB, SPOTIFY, BETTER_LYRICS, YOUTUBE)
        assertEquals(
            listOf(
                LyricsWalkStep(SPOTIFY, 2, 4),
                LyricsWalkStep(BETTER_LYRICS, 0, 1),
                LyricsWalkStep(YOUTUBE, 0, 1),
                LyricsWalkStep(LRCLIB, 0, 4),
                LyricsWalkStep(SPOTIFY, 0, 1),
            ),
            otherLyricsWalk(sources, LyricsChoice(SPOTIFY, 1), variantsPerSource = 4),
        )
        // A source with one answer has no next result of its own.
        assertEquals(
            listOf(LyricsWalkStep(LRCLIB, 0, 4), LyricsWalkStep(SPOTIFY, 0, 4), LyricsWalkStep(BETTER_LYRICS, 0, 1)),
            otherLyricsWalk(sources, LyricsChoice(YOUTUBE, 0), variantsPerSource = 4),
        )
    }

    @Test
    fun `without a known pick the walk takes the queue from the top`() {
        val sources = listOf(SPOTIFY, YOUTUBE)
        val all = listOf(LyricsWalkStep(SPOTIFY, 0, 3), LyricsWalkStep(YOUTUBE, 0, 1))
        assertEquals(all, otherLyricsWalk(sources, null, variantsPerSource = 3))
        // The source on screen was switched off since.
        assertEquals(all, otherLyricsWalk(sources, LyricsChoice(LRCLIB, 0), variantsPerSource = 3))
    }
}

package com.maxrave.simpmusic.ui.screen.player

import kotlin.test.Test
import kotlin.test.assertEquals

class ArtworkPageKeysTest {
    @Test
    fun `a reorder keeps every track's key`() {
        val before = artworkPageKeys(listOf("a", "b", "c"))
        val after = artworkPageKeys(listOf("c", "a", "b"))

        assertEquals(before.toSet(), after.toSet())
        assertEquals(before[2], after[0])
    }

    @Test
    fun `copies of one song get different keys`() {
        val keys = artworkPageKeys(listOf("a", "b", "a", "a"))

        assertEquals(keys.size, keys.toSet().size)
    }
}

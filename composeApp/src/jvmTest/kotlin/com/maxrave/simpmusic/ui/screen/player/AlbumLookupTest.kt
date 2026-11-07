package com.maxrave.simpmusic.ui.screen.player

import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.searchResult.songs.Album
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.SongsResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumLookupTest {
    private val video =
        SongEntity(
            videoId = "video",
            title = "Это прёт",
            artistId = listOf("hamil-video", "kasta"),
            artistName = listOf("Hamil", "Каста"),
            isAvailable = true,
            isExplicit = false,
            likeStatus = "",
            thumbnails = null,
            videoType = "MUSIC_VIDEO_TYPE_OMV",
            category = null,
            resultType = null,
            duration = "",
            durationSeconds = 0,
        )

    private fun result(
        title: String,
        artist: Artist,
        albumId: String,
    ) = SongsResult(
        album = Album(id = albumId, name = albumId),
        artists = listOf(artist),
        category = null,
        duration = null,
        durationSeconds = null,
        feedbackTokens = null,
        isExplicit = false,
        resultType = null,
        thumbnails = null,
        title = title,
        videoId = "song",
        videoType = null,
        year = "",
    )

    @Test
    fun `finds the song of a music video through a shared artist`() {
        val results =
            listOf(
                result("Прочь из дома", Artist("kasta", "Каста"), "other"),
                result("Это прет (Remastered)", Artist("kasta", "Каста"), "hz"),
            )

        assertEquals("hz", results.albumIdOf(video))
    }

    @Test
    fun `a namesake by someone else is not the song`() {
        val results = listOf(result("Это прёт", Artist("stranger", "Кто-то"), "wrong"))

        assertNull(results.albumIdOf(video))
    }
}

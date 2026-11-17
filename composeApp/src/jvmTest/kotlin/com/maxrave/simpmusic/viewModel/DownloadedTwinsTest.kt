package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Album
import com.maxrave.domain.data.model.searchResult.songs.Artist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadedTwinsTest {
    private fun track(
        id: String,
        title: String,
        artists: List<String> = listOf("Каста"),
        seconds: Int? = 200,
        album: String? = "Быль в глаза",
    ) = Track(
        album = album?.let { Album(id = "MPREb_other", name = it) },
        artists = artists.map { Artist(id = null, name = it) },
        duration = null,
        durationSeconds = seconds,
        isAvailable = true,
        isExplicit = false,
        likeStatus = null,
        thumbnails = null,
        title = title,
        videoId = id,
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = null,
    )

    private fun song(
        id: String,
        title: String,
        artists: List<String> = listOf("Каста"),
        seconds: Int = 200,
        album: String? = "Быль в глаза",
    ) = SongEntity(
        videoId = id,
        albumName = album,
        artistName = artists,
        duration = "",
        durationSeconds = seconds,
        isAvailable = true,
        isExplicit = false,
        likeStatus = "INDIFFERENT",
        title = title,
        videoType = "",
        category = null,
        resultType = null,
        downloadState = DownloadState.STATE_DOWNLOADED,
    )

    @Test
    fun `same recording on another edition is found`() {
        val twins =
            findDownloadedTwins(
                tracks = listOf(track("new", "Другая встреча", seconds = 152)),
                downloaded = listOf(song("old", "другая  встреча ", seconds = 152)),
                albumTitle = "Быль в глаза",
            )
        assertEquals("old", twins["new"]?.videoId)
    }

    @Test
    fun `a track downloaded itself needs no twin`() {
        val twins =
            findDownloadedTwins(
                tracks = listOf(track("same", "Встреча")),
                downloaded = listOf(song("same", "Встреча"), song("old", "Встреча")),
                albumTitle = "Быль в глаза",
            )
        assertEquals(emptyMap(), twins)
    }

    @Test
    fun `a shared artist is enough, the clip version credits more of them`() {
        val twins =
            findDownloadedTwins(
                tracks = listOf(track("clip", "Метла", artists = listOf("Хамиль", "Змей", "Каста"), seconds = 239, album = "ХЗ")),
                downloaded = listOf(song("audio", "Метла", artists = listOf("Хамиль"), seconds = 239, album = "ХЗ")),
                albumTitle = "ХЗ",
            )
        assertEquals("audio", twins["clip"]?.videoId)
    }

    @Test
    fun `another album, another length, another artist or another title is not a twin`() {
        val tracks = listOf(track("t", "Муза", seconds = 255))
        assertNull(findDownloadedTwins(tracks, listOf(song("a", "Муза", seconds = 255, album = "Сборник")), "Быль в глаза")["t"])
        assertNull(findDownloadedTwins(tracks, listOf(song("a", "Муза", seconds = 300)), "Быль в глаза")["t"])
        assertNull(findDownloadedTwins(tracks, listOf(song("a", "Муза", artists = listOf("Другой"), seconds = 255)), "Быль в глаза")["t"])
        assertNull(findDownloadedTwins(tracks, listOf(song("a", "Муза (Remix)", seconds = 255)), "Быль в глаза")["t"])
    }

    @Test
    fun `an unknown length matches and the album title stands in for a track without one`() {
        val twins =
            findDownloadedTwins(
                tracks = listOf(track("t", "Ды-ды-дым", seconds = null, album = null)),
                downloaded = listOf(song("a", "Ды-ды-дым", seconds = 0)),
                albumTitle = "Быль в глаза",
            )
        assertEquals("a", twins["t"]?.videoId)
    }
}

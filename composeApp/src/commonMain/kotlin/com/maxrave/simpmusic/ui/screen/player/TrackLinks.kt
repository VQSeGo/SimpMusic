package com.maxrave.simpmusic.ui.screen.player

import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.SongsResult

/** The credited artists that can be opened. More than one, and the player asks which. */
internal fun SongEntity.linkedArtists(): List<Artist> =
    artistName.orEmpty().mapIndexedNotNull { index, name ->
        artistId?.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { Artist(name = name, id = it) }
    }

/**
 * The album of the search result that is [song]: the same title and an artist in common. Compared
 * loosely, because a music video and its song rarely spell either the same: the video's "Это прёт" by
 * Hamil is the song's "Это прёт" by Хамиль, and only their other artists match.
 */
internal fun List<SongsResult>.albumIdOf(song: SongEntity): String? {
    val title = song.title.comparableTitle()
    val artistIds = song.artistId.orEmpty().toSet()
    val artistNames = song.artistName.orEmpty().map { it.lowercase() }.toSet()
    return firstOrNull { result ->
        result.title?.comparableTitle() == title &&
            result.artists.orEmpty().any { it.id in artistIds || it.name.lowercase() in artistNames }
    }?.album?.id?.takeIf { it.isNotEmpty() }
}

private fun String.comparableTitle(): String =
    replace(Regex("[(\\[].*?[)\\]]"), "").replace('ё', 'е').replace('Ё', 'Е').trim().lowercase()

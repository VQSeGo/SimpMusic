package com.maxrave.simpmusic.ui.screen.player

import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.searchResult.songs.Artist

/** The credited artists that can be opened. More than one, and the player asks which. */
internal fun SongEntity.linkedArtists(): List<Artist> =
    artistName.orEmpty().mapIndexedNotNull { index, name ->
        artistId?.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let { Artist(name = name, id = it) }
    }

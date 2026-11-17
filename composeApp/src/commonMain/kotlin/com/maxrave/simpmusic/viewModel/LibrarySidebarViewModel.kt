package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.LOCAL_PLAYLIST_ID
import com.maxrave.domain.data.model.library.LibraryCollectionPreview
import com.maxrave.domain.data.model.library.LibraryOverview
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.CacheRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.simpmusic.ui.navigation.destination.list.AlbumDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.ArtistDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.LocalPlaylistDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.PlaylistDestination
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

/**
 * Feeds the Desktop sidebar: the pinned collections, everything else the user keeps (local and
 * saved playlists, saved albums, followed artists) as one list, which queue is
 * playing, and whether the sidebar is folded. Local playlists are an observed Room query, so a new
 * one shows at once; the other three sources are one-shot reads, read again on [refresh].
 */
class LibrarySidebarViewModel(
    private val dataStoreManager: DataStoreManager,
    commonRepository: CommonRepository,
    localPlaylistRepository: LocalPlaylistRepository,
    playlistRepository: PlaylistRepository,
    albumRepository: AlbumRepository,
    artistRepository: ArtistRepository,
    private val cacheRepository: CacheRepository,
    private val songRepository: SongRepository,
) : BaseViewModel() {
    val overview: StateFlow<LibraryOverview?> =
        commonRepository
            .getLibraryOverview()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val refreshes = MutableStateFlow(0)

    /** The Cached row's count and artwork: the player cache is no table to observe, so it is read on [refresh]. */
    private val _cachedPreview = MutableStateFlow<LibraryCollectionPreview?>(null)
    val cachedPreview: StateFlow<LibraryCollectionPreview?> = _cachedPreview.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<LibrarySidebarItem>> =
        refreshes
            .flatMapLatest {
                combine(
                    localPlaylistRepository.observeAllLocalPlaylists(),
                    playlistRepository.getLikedPlaylists(),
                    albumRepository.getLikedAlbums(),
                    artistRepository.getFollowedArtists(),
                ) { local, playlists, albums, artists ->
                    local.map {
                        LibrarySidebarItem(
                            key = "local_${it.id}",
                            kind = LibrarySidebarKind.PLAYLIST,
                            title = it.title,
                            subtitle = null,
                            trackCount = it.tracks?.size ?: 0,
                            thumbnail = it.thumbnail,
                            addedAt = it.inLibrary,
                            destination = LocalPlaylistDestination(it.id),
                            queueId = LOCAL_PLAYLIST_ID + it.id,
                        )
                    } +
                        playlists.map {
                            LibrarySidebarItem(
                                key = "playlist_${it.id}",
                                kind = LibrarySidebarKind.PLAYLIST,
                                title = it.title,
                                subtitle = it.author,
                                thumbnail = it.thumbnails,
                                addedAt = it.favoriteAt ?: it.inLibrary,
                                destination = PlaylistDestination(it.id),
                                queueId = it.id,
                            )
                        } +
                        albums.map {
                            LibrarySidebarItem(
                                key = "album_${it.browseId}",
                                kind = LibrarySidebarKind.ALBUM,
                                title = it.title,
                                subtitle = it.artistName?.joinToString(", "),
                                thumbnail = it.thumbnails,
                                addedAt = it.favoriteAt ?: it.inLibrary,
                                destination = AlbumDestination(it.browseId),
                                queueId = it.browseId,
                            )
                        } +
                        artists.map {
                            LibrarySidebarItem(
                                key = "artist_${it.channelId}",
                                kind = LibrarySidebarKind.ARTIST,
                                title = it.name,
                                subtitle = null,
                                thumbnail = it.thumbnails,
                                addedAt = it.followedAt ?: it.inLibrary,
                                destination = ArtistDestination(it.channelId),
                                queueId = null,
                            )
                        }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The id the playing queue was started from, in the same shape LibrarySidebarItem.queueId uses.
    val playingQueueId: StateFlow<String?> =
        mediaPlayerHandler.queueData
            .map { it?.data?.playlistId?.removePrefix("VL") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val collapsed: StateFlow<Boolean> =
        dataStoreManager
            .getString(COLLAPSED_KEY)
            .map { it == DataStoreManager.TRUE }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun refresh() {
        refreshes.update { it + 1 }
        viewModelScope.launch {
            val songs = loadCachedSongs(cacheRepository, songRepository)
            _cachedPreview.value = LibraryCollectionPreview(songs.size, songs.mapNotNull { it.thumbnails }.take(3))
        }
    }

    fun setCollapsed(collapsed: Boolean) {
        viewModelScope.launch {
            dataStoreManager.putString(COLLAPSED_KEY, if (collapsed) DataStoreManager.TRUE else DataStoreManager.FALSE)
        }
    }

    private companion object {
        const val COLLAPSED_KEY = "desktop_sidebar_collapsed"
    }
}

enum class LibrarySidebarKind { PLAYLIST, ARTIST, ALBUM }

data class LibrarySidebarItem(
    val key: String,
    val kind: LibrarySidebarKind,
    val title: String,
    // The playlist's author or the album's artists; null where the kind says enough.
    val subtitle: String?,
    val thumbnail: String?,
    val addedAt: LocalDateTime,
    val destination: Any,
    // What the player's queue carries as its playlist id while this item plays; null if it never does.
    val queueId: String?,
    // Only local playlists show their size, since they have no author to show instead.
    val trackCount: Int? = null,
)

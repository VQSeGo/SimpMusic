package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.browse.artist.Albums
import com.maxrave.domain.data.model.browse.artist.ArtistBrowse
import com.maxrave.domain.data.model.browse.artist.ResultAlbum
import com.maxrave.domain.data.model.browse.artist.ResultSingle
import com.maxrave.domain.data.model.browse.artist.Related
import com.maxrave.domain.data.model.browse.artist.ResultPlaylist
import com.maxrave.domain.data.model.browse.artist.Singles
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.extension.now
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.simpmusic.extension.toArtistScreenData
import com.maxrave.simpmusic.viewModel.ArtistScreenState.Error
import com.maxrave.simpmusic.viewModel.ArtistScreenState.Loading
import com.maxrave.simpmusic.viewModel.ArtistScreenState.Success
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.radio
import simpmusic.composeapp.generated.resources.shuffle
import simpmusic.composeapp.generated.resources.sync_follow_failed
import simpmusic.composeapp.generated.resources.subscribed_on_youtube
import simpmusic.composeapp.generated.resources.unsubscribed_on_youtube
import org.jetbrains.compose.resources.getString

class ArtistViewModel(
    private val artistRepository: ArtistRepository,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
) : BaseViewModel() {
    // How many liked songs credit this artist — the "Liked songs" row above Popular (issue #2524)
    // shows only while this is above zero. Observed, so liking a song from the page updates it.
    private val _likedSongCount: MutableStateFlow<Int> = MutableStateFlow(0)
    val likedSongCount: StateFlow<Int> = _likedSongCount
    private var likedSongsJob: Job? = null

    private var _followed: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var followed: StateFlow<Boolean> = _followed

    private val _artistScreenState: MutableStateFlow<ArtistScreenState> = MutableStateFlow(Loading)
    val artistScreenState: StateFlow<ArtistScreenState> = _artistScreenState

    fun browseArtist(channelId: String) {
        _artistScreenState.value = Loading
        _followed.value = false
        _likedSongCount.value = 0
        likedSongsJob?.cancel()
        likedSongsJob =
            viewModelScope.launch {
                songRepository.getLikedSongsByArtist(channelId).collect { _likedSongCount.value = it.size }
            }
        viewModelScope.launch {
            artistRepository.getArtistData(channelId).collect { browse ->
                val data = browse.data
                when (browse) {
                    is Resource.Success if (data != null) -> {
                        data.channelId?.let { channelId ->
                            insertArtist(
                                ArtistEntity(
                                    channelId,
                                    data.name,
                                    data.thumbnails
                                        ?.lastOrNull()
                                        ?.url,
                                ),
                            )
                        }
                        val screenData = data.toArtistScreenData()
                        _artistScreenState.value = Success(screenData)
                        loadAllReleases(screenData)
                    }

                    is Resource.Error ->
                        _artistScreenState.value = Error(browse.message ?: "Error")

                    else -> {
                        _artistScreenState.value = Error("Error")
                    }
                }
            }
        }
    }

    /**
     * The artist page lists ten albums and ten singles at most; the rest are on the pages behind
     * "More". Those replace the shelves once they arrive, and a page that fails leaves the ten.
     */
    private fun loadAllReleases(artist: ArtistScreenData) {
        val moreId = "MPAD${artist.channelId ?: return}"
        if (!artist.albums?.results.isNullOrEmpty()) {
            viewModelScope.launch {
                val albums = albumRepository.getAlbumMore(moreId, MoreAlbumsViewModel.ALBUM_PARAM).firstOrNull()?.second.orEmpty()
                if (albums.size <= artist.albums.results.size) return@launch
                val results = albums.map { ResultAlbum(it.browseId, it.isExplicit, it.thumbnails, it.title, it.year) }
                updateScreenData(artist.channelId) { it.copy(albums = it.albums?.copy(results = results)) }
            }
        }
        if (!artist.singles?.results.isNullOrEmpty()) {
            viewModelScope.launch {
                val singles = albumRepository.getAlbumMore(moreId, MoreAlbumsViewModel.SINGLE_PARAM).firstOrNull()?.second.orEmpty()
                if (singles.size <= artist.singles.results.size) return@launch
                val results = singles.map { ResultSingle(it.browseId, it.thumbnails, it.title, it.year) }
                updateScreenData(artist.channelId) { it.copy(singles = it.singles?.copy(results = results)) }
            }
        }
    }

    private fun updateScreenData(
        channelId: String?,
        change: (ArtistScreenData) -> ArtistScreenData,
    ) {
        _artistScreenState.update { state ->
            if (state is Success && state.data.channelId == channelId) Success(change(state.data)) else state
        }
    }

    fun insertArtist(artist: ArtistEntity) {
        viewModelScope.launch {
            artistRepository.insertArtist(artist)
            artistRepository.updateArtistInLibrary(now(), artist.channelId)
            delay(100)
            artistRepository.getArtistById(artist.channelId).collect { artistEntity ->
                if (artistEntity != null) {
                    artist.thumbnails?.let {
                        artistRepository.updateArtistImage(artistEntity.channelId, it)
                    }
                    _followed.value = artistEntity.followed
                    log("insertArtist: ${artistEntity.followed}")
                }
            }
        }
    }

    fun updateFollowed(
        followed: Int,
        channelId: String,
    ) {
        viewModelScope.launch {
            _followed.value = (followed == 1)
            // Only null stays quiet: signed out, so nothing was attempted.
            val synced = artistRepository.updateFollowedStatus(channelId, followed)
            when (synced) {
                true ->
                    makeToast(
                        getString(
                            if (followed == 1) {
                                Res.string.subscribed_on_youtube
                            } else {
                                Res.string.unsubscribed_on_youtube
                            },
                        ),
                    )

                false -> {
                    // The repository has put the follow back, so the button goes with it.
                    _followed.value = followed != 1
                    makeToast(getString(Res.string.sync_follow_failed))
                }
                null -> Unit
            }
            log("updateFollowed: ${_followed.value}, synced: $synced")
        }
    }

    fun onRadioClick(endpoint: YouTubeWatchEndpoint) {
        viewModelScope.launch {
            songRepository.getRadioFromEndpoint(endpoint).collectLatest { res ->
                val data = res.data
                when (res) {
                    is Resource.Success if data != null && data.first.isNotEmpty() -> {
                        setQueueData(
                            QueueData.Data(
                                listTracks = data.first,
                                firstPlayedTrack = data.first.first(),
                                playlistId = endpoint.playlistId,
                                playlistName = "\"${artistScreenState.value.data.title}\" ${getString(Res.string.radio)}",
                                playlistType = PlaylistType.RADIO,
                                continuation = data.second,
                            ),
                        )
                        loadMediaItem(
                            data.first.first(),
                            Config.PLAYLIST_CLICK,
                            0,
                        )
                    }

                    else -> {
                        makeToast(res.message)
                    }
                }
            }
        }
    }

    fun onShuffleClick(endpoint: YouTubeWatchEndpoint) {
        viewModelScope.launch {
            songRepository.getRadioFromEndpoint(endpoint).collectLatest { res ->
                val data = res.data
                when (res) {
                    is Resource.Success if data != null && data.first.isNotEmpty() -> {
                        setQueueData(
                            QueueData.Data(
                                listTracks = data.first,
                                firstPlayedTrack = data.first.first(),
                                playlistId = endpoint.playlistId,
                                playlistName = "\"${artistScreenState.value.data.title}\" ${getString(Res.string.shuffle)}",
                                playlistType = PlaylistType.RADIO,
                                continuation = data.second,
                            ),
                        )
                        loadMediaItem(
                            data.first.first(),
                            Config.PLAYLIST_CLICK,
                            0,
                        )
                    }

                    else -> {
                        makeToast(res.message)
                    }
                }
            }
        }
    }
}

data class ArtistScreenData(
    val title: String? = null,
    val imageUrl: String? = null,
    val subscribers: String? = null,
    val playCount: String? = null,
    val isChannel: Boolean = false,
    val channelId: String? = null,
    val radioParam: YouTubeWatchEndpoint? = null,
    val shuffleParam: YouTubeWatchEndpoint? = null,
    val description: String? = null,
    val listSongParam: String? = null,
    val popularSongs: List<Track> = emptyList(),
    val singles: Singles? = null,
    val albums: Albums? = null,
    val video: ArtistBrowse.Videos? = null,
    val related: Related? = null,
    val featuredOn: List<ResultPlaylist> = emptyList(),
)

sealed class ArtistScreenState(
    val data: ArtistScreenData = ArtistScreenData(),
    val message: String? = null,
) {
    data object Loading : ArtistScreenState()

    class Success(
        data: ArtistScreenData,
    ) : ArtistScreenState(data)

    class Error(
        message: String,
    ) : ArtistScreenState(message = message)
}
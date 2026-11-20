package com.maxrave.simpmusic.viewModel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.browse.album.AlbumBrowse
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.browse.artist.ResultAlbum
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.extension.now
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.AutoDownloadGuard
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.toAlbumEntity
import com.maxrave.domain.utils.toArrayListTrack
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.domain.utils.toTrack
import com.maxrave.logger.LogLevel
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.album
import simpmusic.composeapp.generated.resources.downloaded
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.playlist_is_empty

class AlbumViewModel(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
) : BaseViewModel() {
    private val downloadUtils: DownloadHandler by inject<DownloadHandler>()
    private val dataStoreManager: DataStoreManager by inject<DataStoreManager>()
    private val _uiState: MutableStateFlow<AlbumUIState> = MutableStateFlow(AlbumUIState.initial())
    val uiState: StateFlow<AlbumUIState> = _uiState

    private var job: Job? = null
    private var loadJob: Job? = null
    private var albumFlowFor: String? = null
    private var collectDownloadStateJob: Job? = null

    init {
        // Another edition of this album may hold the downloaded copy of a track: the same recording has a
        // different videoId there, and the edition of the track playing is not always the one downloaded.
        viewModelScope.launch {
            combine(
                _uiState.map { it.listTrack to it.title }.distinctUntilChanged(),
                songRepository.observeDownloadedSongs(),
            ) { (tracks, title), downloaded ->
                findDownloadedTwins(tracks, downloaded, title) to downloaded.mapTo(HashSet()) { it.videoId }
            }.distinctUntilChanged()
                .collectLatest { (twins, downloadedIds) ->
                    // The song rows count, not only downloadTask: on desktop that map holds only this run's
                    // downloads, so an album downloaded before a restart never showed as downloaded.
                    val before = _uiState.value
                    _uiState.update { state ->
                        val everyTrackDownloaded =
                            state.listTrack.isNotEmpty() &&
                                state.listTrack.all {
                                    it.videoId in twins ||
                                        it.videoId in downloadedIds ||
                                        downloadUtils.downloadTask.value[it.videoId] == DownloadState.STATE_DOWNLOADED
                                }
                        state.copy(
                            downloadedTwins = twins,
                            downloadState =
                                if (everyTrackDownloaded) DownloadState.STATE_DOWNLOADED else state.downloadState,
                        )
                    }
                    val after = _uiState.value
                    if (after.downloadState == DownloadState.STATE_DOWNLOADED &&
                        before.downloadState != DownloadState.STATE_DOWNLOADED &&
                        after.browseId.isNotEmpty()
                    ) {
                        albumRepository.updateAlbumDownloadState(after.browseId, DownloadState.STATE_DOWNLOADED)
                    }
                }
        }
    }

    /**
     * Opens [browseId]: the saved copy from the database first, when there is one, then the
     * network answer. The network replaces the saved copy only where it differs, so a list that
     * did not change keeps its scroll position and nothing visible moves. Without a saved copy the
     * screen waits for the network as before, and a network error closes it with a message.
     *
     * Opening another album cancels the previous load, and every write checks that the screen still
     * shows the album it was loaded for, so a late answer for the old album cannot land on the new one.
     */
    fun updateBrowseId(browseId: String) {
        loadJob?.cancel()
        if (uiState.value.browseId != browseId) {
            // Another album: nothing of the previous one may show under the new id.
            job?.cancel()
            collectDownloadStateJob?.cancel()
            albumFlowFor = null
            _uiState.value = AlbumUIState.initial().copy(browseId = browseId)
        }
        val started = TimeSource.Monotonic.markNow()
        loadJob =
            viewModelScope.launch {
                val localShown = showLocalAlbum(browseId, started)
                albumRepository.getAlbumData(browseId).collectLatest { res ->
                    if (uiState.value.browseId != browseId) return@collectLatest
                    when (res) {
                        is Resource.Success -> {
                            val data = res.data
                            if (data != null) {
                                val current = uiState.value
                                val sameTracks = current.listTrack.map { it.videoId } == data.tracks.map { it.videoId }
                                if (localShown) {
                                    val same =
                                        sameTracks &&
                                            current.title == data.title &&
                                            current.thumbnail == data.thumbnails?.lastOrNull()?.url &&
                                            current.trackCount == data.trackCount
                                    Logger.i(
                                        "Offline",
                                        "album $browseId network ${if (same) "same" else "replaced"} " +
                                            "${started.elapsedNow().inWholeMilliseconds} ms",
                                    )
                                }
                                _uiState.update {
                                    it.copy(
                                        browseId = browseId,
                                        audioPlaylistId = data.audioPlaylistId,
                                        title = data.title,
                                        thumbnail = data.thumbnails?.lastOrNull()?.url,
                                        artists = data.artists,
                                        year = data.year ?: now().year.toString(),
                                        trackCount = data.trackCount,
                                        description = data.description,
                                        length = data.duration ?: "",
                                        // The same tracks in the same order: keep the list object, so the
                                        // rows are not rebuilt under the user's finger.
                                        listTrack = if (sameTracks && it.listTrack.isNotEmpty()) it.listTrack else data.tracks,
                                        otherVersion = data.otherVersion,
                                        loadState = LocalPlaylistState.PlaylistLoadState.Success,
                                    )
                                }
                                val localAlbum = albumRepository.getAlbum(browseId).lastOrNull()
                                if (uiState.value.browseId != browseId) return@collectLatest
                                if (localAlbum != null) {
                                    _uiState.update {
                                        it.copy(
                                            downloadState = localAlbum.downloadState,
                                            liked = localAlbum.liked,
                                        )
                                    }
                                    albumRepository.updateAlbumInLibrary(now(), browseId)
                                    refreshSavedAlbum(browseId, localAlbum, data, started)
                                } else {
                                    albumRepository.insertAlbum(data.toAlbumEntity(browseId)).singleOrNull().let {
                                        log("Insert Album $it")
                                        data.tracks.forEach { track ->
                                            songRepository
                                                .insertSong(
                                                    track.toSongEntity().copy(
                                                        inLibrary = Config.REMOVED_SONG_DATE_TIME,
                                                    ),
                                                ).singleOrNull()
                                                ?.let {
                                                    log("Insert Song $it")
                                                }
                                        }
                                    }
                                }
                                getAlbumFlow(browseId)
                            } else if (localShown) {
                                Logger.i(
                                    "Offline",
                                    "album $browseId network failed, local stays ${started.elapsedNow().inWholeMilliseconds} ms: null data",
                                )
                            } else {
                                makeToast(getString(Res.string.error) + ": Null data")
                                _uiState.update {
                                    it.copy(
                                        loadState = LocalPlaylistState.PlaylistLoadState.Error,
                                    )
                                }
                            }
                        }

                        is Resource.Error -> {
                            if (localShown) {
                                Logger.i(
                                    "Offline",
                                    "album $browseId network failed, local stays ${started.elapsedNow().inWholeMilliseconds} ms: ${res.message}",
                                )
                                return@collectLatest
                            }
                            // No usable saved copy was shown (none, or one without its tracks): what the
                            // database has is still better than closing the screen.
                            val albumEntity = albumRepository.getAlbum(browseId).singleOrNull()
                            if (uiState.value.browseId != browseId) return@collectLatest
                            if (albumEntity != null) {
                                _uiState.update { localAlbumState(it, albumEntity, localTracks(albumEntity)) }
                            } else {
                                log("Error: ${res.message}", LogLevel.ERROR)
                                makeToast(getString(Res.string.error) + ": ${res.message}")
                                _uiState.update {
                                    it.copy(
                                        loadState = LocalPlaylistState.PlaylistLoadState.Error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    /**
     * Shows the saved copy of [browseId] if the database has one with its tracks. Returns whether it
     * did. A screen already showing this album (back from another screen) is left alone: the network
     * answer that follows refreshes it.
     */
    private suspend fun showLocalAlbum(
        browseId: String,
        started: TimeMark,
    ): Boolean {
        if (uiState.value.loadState == LocalPlaylistState.PlaylistLoadState.Success) return true
        val albumEntity = albumRepository.getAlbum(browseId).singleOrNull() ?: return false
        val tracks = localTracks(albumEntity)
        if (tracks.isEmpty() || uiState.value.browseId != browseId) return false
        _uiState.update { localAlbumState(it, albumEntity, tracks) }
        Logger.i("Offline", "album $browseId local shown ${started.elapsedNow().inWholeMilliseconds} ms")
        getAlbumFlow(browseId)
        return true
    }

    /**
     * Writes the network's answer onto the saved copy of the album: title, cover, year, artists and
     * the track list, with a song row for every track the copy did not have. Liked, download state
     * and library date are the device's own and are not touched. Nothing is written when the copy
     * already says the same.
     */
    private suspend fun refreshSavedAlbum(
        browseId: String,
        saved: AlbumEntity,
        data: AlbumBrowse,
        started: TimeMark,
    ) {
        if (data.tracks.isEmpty()) return
        val known = saved.tracks.orEmpty().toSet()
        data.tracks.filter { it.videoId !in known }.forEach { track ->
            songRepository
                .insertSong(track.toSongEntity().copy(inLibrary = Config.REMOVED_SONG_DATE_TIME))
                .singleOrNull()
                ?.let { log("Insert Song $it") }
        }
        if (albumRepository.refreshAlbumContent(data.toAlbumEntity(browseId))) {
            Logger.i("Offline", "album $browseId saved copy updated ${started.elapsedNow().inWholeMilliseconds} ms")
        }
    }

    private suspend fun localTracks(albumEntity: AlbumEntity): List<Track> =
        (
            songRepository
                .getSongsByListVideoId(albumEntity.tracks ?: emptyList())
                .singleOrNull() ?: emptyList()
        ).toArrayListTrack()

    private fun localAlbumState(
        state: AlbumUIState,
        albumEntity: AlbumEntity,
        tracks: List<Track>,
    ): AlbumUIState =
        state.copy(
            browseId = albumEntity.browseId,
            audioPlaylistId = albumEntity.audioPlaylistId,
            title = albumEntity.title,
            thumbnail = albumEntity.thumbnails,
            artists =
                albumEntity.artistName.orEmpty().mapIndexed { index, name ->
                    Artist(id = albumEntity.artistId?.getOrNull(index), name = name)
                },
            year = albumEntity.year ?: now().year.toString(),
            trackCount = albumEntity.trackCount,
            description = albumEntity.description,
            length = albumEntity.duration ?: "",
            listTrack = tracks,
            downloadState = albumEntity.downloadState,
            liked = albumEntity.liked,
            loadState = LocalPlaylistState.PlaylistLoadState.Success,
        )

    fun setBrush(brush: List<Color>) {
        _uiState.update {
            it.copy(
                colors = brush,
            )
        }
    }

    fun setAlbumLike() {
        viewModelScope.launch {
            albumRepository.updateAlbumLiked(uiState.value.browseId, if (!uiState.value.liked) 1 else 0)
            _uiState.update {
                it.copy(
                    liked = !it.liked,
                )
            }
        }
    }

    private fun getAlbumFlow(browseId: String) {
        // Started by the saved copy already: restarting would only replay the same values.
        if (albumFlowFor == browseId && job?.isActive == true) return
        albumFlowFor = browseId
        job?.cancel()
        collectDownloadStateJob?.cancel()
        job =
            viewModelScope.launch {
                albumRepository.getAlbumAsFlow(browseId).collectLatest { album ->
                    if (album != null) {
                        _uiState.update {
                            it.copy(
                                downloadState = album.downloadState,
                                liked = album.liked,
                            )
                        }
                    }
                }
            }
        collectDownloadStateJob =
            viewModelScope.launch {
                downloadUtils.downloadTask.collectLatest { downloadTask ->
                    // An empty list is not a downloaded album.
                    if (uiState.value.listTrack.isEmpty()) return@collectLatest
                    var count = 0
                    uiState.value.listTrack.forEach { track ->
                        if (downloadTask[track.videoId] == DownloadState.STATE_DOWNLOADED ||
                            track.videoId in uiState.value.downloadedTwins
                        ) {
                            count++
                        }
                    }
                    if (count == uiState.value.listTrack.size) {
                        albumRepository.updateAlbumDownloadState(uiState.value.browseId, DownloadState.STATE_DOWNLOADED)
                        _uiState.update {
                            it.copy(
                                downloadState = DownloadState.STATE_DOWNLOADED,
                            )
                        }
                    }
                }
            }
    }

    /** The track as played: the downloaded copy from another edition of the album where there is one. */
    private fun Track.asPlayed(): Track = uiState.value.downloadedTwins[videoId]?.toTrack() ?: this

    fun playTrack(track: Track) {
        setQueueData(
            QueueData.Data(
                listTracks = uiState.value.listTrack.mapTo(ArrayList()) { it.asPlayed() },
                firstPlayedTrack = track.asPlayed(),
                playlistId = uiState.value.browseId.replaceFirst("VL", ""),
                playlistName = "${getString(Res.string.album)} \"${uiState.value.title}\"",
                playlistType = PlaylistType.ALBUM,
                continuation = null,
            ),
        )
        val index = uiState.value.listTrack.indexOf(track)
        loadMediaItem(track.asPlayed(), Config.ALBUM_CLICK, if (index == -1) 0 else index)
    }

    fun shuffle() {
        if (uiState.value.listTrack.isEmpty()) {
            makeToast(getString(Res.string.playlist_is_empty))
            return
        }
        val shuffleList = uiState.value.listTrack.map { it.asPlayed() }.shuffled()
        val randomIndex = shuffleList.indices.random()
        setQueueData(
            QueueData.Data(
                listTracks = shuffleList.toCollection(ArrayList()),
                firstPlayedTrack = shuffleList[randomIndex],
                playlistId = uiState.value.browseId.replaceFirst("VL", ""),
                playlistName = "${getString(Res.string.album)} \"${uiState.value.title}\"",
                // Not ALBUM: shuffling has already thrown away the running order, so the reason to
                // keep transitions gapless is gone. Crossfade behaves as it would for any playlist.
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        loadMediaItem(shuffleList[randomIndex], Config.ALBUM_CLICK, randomIndex)
    }

    fun downloadFullAlbum() {
        viewModelScope.launch {
            // Insert all song to database
            uiState.value.listTrack.forEach { track ->
                songRepository.insertSong(track.toSongEntity()).singleOrNull()?.let {
                    log("Insert Song $it")
                }
            }
            val fullListSong =
                songRepository
                    .getSongsByListVideoId(uiState.value.listTrack.map { it.videoId })
                    .singleOrNull() ?: emptyList()
            log("Full list song: $fullListSong")
            if (fullListSong.isEmpty()) {
                makeToast(getString(Res.string.playlist_is_empty))
                return@launch
            }
            // A track whose copy is downloaded from another edition of the album is not fetched again.
            val listJob =
                fullListSong.filter {
                    it.downloadState != DownloadState.STATE_DOWNLOADED && it.videoId !in uiState.value.downloadedTwins
                }
            log("List job: $listJob")
            if (listJob.isEmpty()) {
                // Every track is downloaded already: the album is, too (the button stayed on "download").
                albumRepository.updateAlbumDownloadState(uiState.value.browseId, DownloadState.STATE_DOWNLOADED)
                _uiState.update { it.copy(downloadState = DownloadState.STATE_DOWNLOADED) }
                makeToast(getString(Res.string.downloaded))
                return@launch
            }
            albumRepository.updateAlbumDownloadState(uiState.value.browseId, DownloadState.STATE_DOWNLOADING)
            // The user asks for the whole album, tracks whose download they removed included.
            AutoDownloadGuard.clearRemoved(dataStoreManager, listJob.map { it.videoId })
            listJob.forEach {
                log("Download: ${it.videoId} ${it.thumbnails}")
                downloadUtils.downloadTrack(
                    it.videoId,
                    it.title,
                    it.thumbnails ?: "",
                )
            }
        }
    }
}

data class AlbumUIState(
    val browseId: String = "",
    val audioPlaylistId: String = "",
    val title: String = "",
    val thumbnail: String? = null,
    val colors: List<Color> = listOf(Color.Black, Color.Black),
    val artists: List<Artist> = emptyList(),
    val year: String = now().year.toString(),
    val downloadState: Int = DownloadState.STATE_NOT_DOWNLOADED,
    val liked: Boolean = false,
    val trackCount: Int = 0,
    val description: String? = null,
    val length: String = "",
    val listTrack: List<Track> = emptyList(),
    val otherVersion: List<ResultAlbum> = emptyList(),
    /** Track videoId to the downloaded copy of that recording on another edition of the album. */
    val downloadedTwins: Map<String, SongEntity> = emptyMap(),
    val loadState: LocalPlaylistState.PlaylistLoadState = LocalPlaylistState.PlaylistLoadState.Loading,
) {
    companion object {
        fun initial(): AlbumUIState = AlbumUIState()
    }
}
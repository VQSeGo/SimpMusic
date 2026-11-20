@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.maxrave.simpmusic.viewModel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.DownloadState.STATE_DOWNLOADED
import com.maxrave.domain.data.entities.DownloadState.STATE_DOWNLOADING
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.collectionDownloadState
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.browse.playlist.Author
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.data.model.browse.playlist.PlaylistState
import com.maxrave.domain.extension.now
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.AutoDownloadGuard
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.collectLatestResource
import com.maxrave.domain.utils.isRadioPlaylistId
import com.maxrave.domain.utils.toListVideoId
import com.maxrave.domain.utils.toPlaylistEntity
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.domain.utils.toTrack
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.viewModel.PlaylistUIState.Error
import com.maxrave.simpmusic.viewModel.PlaylistUIState.Loading
import com.maxrave.simpmusic.viewModel.PlaylistUIState.Success
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.auto_created_by_youtube_music
import simpmusic.composeapp.generated.resources.downloading
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.playlist
import simpmusic.composeapp.generated.resources.playlist_is_empty
import simpmusic.composeapp.generated.resources.radio
import simpmusic.composeapp.generated.resources.radio_not_available
import simpmusic.composeapp.generated.resources.remove_from_youtube_playlist_failed
import simpmusic.composeapp.generated.resources.removed_from_YouTube_playlist
import simpmusic.composeapp.generated.resources.shuffle
import simpmusic.composeapp.generated.resources.shuffle_not_available
import simpmusic.composeapp.generated.resources.synced
import simpmusic.composeapp.generated.resources.syncing
import simpmusic.composeapp.generated.resources.view_count
import simpmusic.composeapp.generated.resources.youtube_playlist_delete_failed
import simpmusic.composeapp.generated.resources.youtube_playlist_deleted

class PlaylistViewModel(
    private val songRepository: SongRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val playlistRepository: PlaylistRepository,
) : BaseViewModel() {
    val downloadUtils: DownloadHandler by inject<DownloadHandler>()
    private val dataStoreManager: DataStoreManager by inject<DataStoreManager>()
    private var _uiState = MutableStateFlow<PlaylistUIState>(Loading)
    val uiState: StateFlow<PlaylistUIState> = _uiState

    private var _listColors = MutableStateFlow<List<Color>>(emptyList())
    val listColors: StateFlow<List<Color>> = _listColors

    private var _continuation = MutableStateFlow<String?>(null)
    val continuation: StateFlow<String?> = _continuation

    private var _playlistEntity: MutableStateFlow<PlaylistEntity?> = MutableStateFlow(null)
    var playlistEntity: StateFlow<PlaylistEntity?> = _playlistEntity

    val downloadState = _playlistEntity.map { it?.downloadState ?: 0 }.stateIn(viewModelScope, WhileSubscribed(1000), 0)
    val liked = _playlistEntity.map { it?.liked == true }.stateIn(viewModelScope, WhileSubscribed(1000), false)

    private var _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks

    private var _tracksListState = MutableStateFlow<ListState>(ListState.IDLE)
    val tracksListState: StateFlow<ListState> = _tracksListState

    private var collectDownloadedJob: Job? = null
    private var _downloadedList = MutableStateFlow<List<String>>(emptyList())
    val downloadedList: StateFlow<List<String>> = _downloadedList

    private var playlistEntityJob: Job? = null
    private var loadJob: Job? = null

    // Entry ids (setVideoId) of the open playlist's rows as the network sent them, by videoId. The
    // rows on screen can be the saved copy's, which carry none.
    private val setVideoIds = mutableMapOf<String, String>()

    // Set once the open playlist has been deleted from the account; the screen closes on it.
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    // The playlist the screen is loading or showing. Every write of a load checks it, so the answer
    // for a playlist the user has already left cannot land on the next one.
    private var loadingId: String? = null
    private var newUpdateJob: Job? = null
    private var checkDownloadedPlaylist: Job? = null

    init {
        viewModelScope.launch {
            val listTrackStringJob =
                launch(Dispatchers.IO) {
                    downloadState
                        .collectLatest { state ->
                            newUpdateJob?.cancel()
                            val id = playlistEntity.value?.id ?: return@collectLatest
                            if (state == STATE_DOWNLOADING || state == STATE_DOWNLOADED) {
                                getFullTracks { tracks ->
                                    newUpdateJob =
                                        launch {
                                            val listSongs = songRepository.getSongsByListVideoId(tracks.toListVideoId()).firstOrNull() ?: emptyList()
                                            if (state == STATE_DOWNLOADED && listSongs.isNotEmpty()) {
                                                listSongs.filter { it.downloadState != STATE_DOWNLOADED }.let { notDownloaded ->
                                                    if (notDownloaded.isNotEmpty()) {
                                                        // Not a track whose download the user removed, and each track
                                                        // only once per launch (see AutoDownloadGuard).
                                                        val toQueue =
                                                            AutoDownloadGuard.pick(
                                                                dataStoreManager,
                                                                notDownloaded.map { it.videoId },
                                                                listSongs.filter { it.downloadState == STATE_DOWNLOADED }.map { it.videoId },
                                                            )
                                                        if (toQueue.isNotEmpty()) {
                                                            downloadTracks(toQueue)
                                                            updatePlaylistDownloadState(id, STATE_DOWNLOADING)
                                                        }
                                                    } else {
                                                        updatePlaylistDownloadState(id, STATE_DOWNLOADED)
                                                    }
                                                }
                                            }
                                            // Per-track states, not the raw audio/video pairs: a clip counts as
                                            // downloaded once its audio is, the video half comes on top.
                                            downloadUtils.downloadTask.collectLatest { task ->
                                                val resolved = collectionDownloadState(tracks.map { task[it.videoId] })
                                                if ((resolved == STATE_DOWNLOADING || resolved == STATE_DOWNLOADED) &&
                                                    resolved != playlistEntity.value?.downloadState
                                                ) {
                                                    updatePlaylistDownloadState(id, resolved)
                                                }
                                            }
                                        }
                                }
                            }
                        }
                }
            listTrackStringJob.join()
        }
    }

    private fun updatePlaylistDownloadState(
        id: String,
        state: Int,
    ) {
        viewModelScope.launch {
            playlistRepository.updatePlaylistDownloadState(id, state)
            delay(500)
            _playlistEntity.update {
                it?.copy(downloadState = state)
            }
        }
    }

    private fun downloadTracks(listJob: List<String>) {
        viewModelScope.launch {
            listJob.forEach { videoId ->
                songRepository.getSongById(videoId).singleOrNull()?.let { song ->
                    if (song.downloadState != STATE_DOWNLOADED) {
                        downloadUtils.downloadTrack(videoId, song.title, song.thumbnails ?: "")
                    }
                }
            }
        }
    }

    private fun resetData() {
        _uiState.value = Loading
        _playlistEntity.value = null
        _downloadedList.value = emptyList()
        _listColors.value = emptyList()
        setVideoIds.clear()
        _deleted.value = false
        checkDownloadedPlaylist?.cancel()
        checkDownloadedPlaylist = null
        playlistEntityJob?.cancel()
    }

    /**
     * Opens playlist [id]. A radio comes from the network only, as before. Any other playlist shows
     * its saved copy from the database at once, when there is one, and then the network answer:
     * the track list is replaced only when it differs, so an unchanged list keeps its rows and the
     * scroll position. A network error leaves the saved copy on screen; without one the screen
     * closes with a message, as before.
     */
    fun getData(id: String) {
        loadJob?.cancel()
        resetData()
        loadingId = id
        val started = TimeSource.Monotonic.markNow()
        loadJob = viewModelScope.launch {
            // Check radio
            if (id.isRadioPlaylistId()) {
                playlistRepository
                    .getRadio(
                        id,
                        radioString = getString(Res.string.radio),
                        defaultDescription = getString(Res.string.auto_created_by_youtube_music),
                        viewString = getString(Res.string.view_count),
                    ).collect { res ->
                        val data = res.data
                        when (res) {
                            is Resource.Success if (data != null) -> {
                                Logger.d(tag) { "Radio data: ${Logger.clip(data)}" }
                                _uiState.value =
                                    Success(
                                        data =
                                            PlaylistState(
                                                id = data.first.id,
                                                title = data.first.title,
                                                isRadio = true,
                                                author = data.first.author,
                                                thumbnail =
                                                    data.first.thumbnails
                                                        .lastOrNull()
                                                        ?.url,
                                                description = data.first.description,
                                                trackCount = data.first.trackCount,
                                                year = data.first.year,
                                            ),
                                    )
                                _tracks.value = data.first.tracks
                                _continuation.value = data.second
                                if (data.second.isNullOrEmpty()) _tracksListState.value = ListState.PAGINATION_EXHAUST
                                playlistRepository.insertRadioPlaylist(data.first.toPlaylistEntity())
                            }

                            else -> {
                                _uiState.value = Error(res.message ?: "Empty response")
                            }
                        }
                    }
            } else {
                val localShown = showLocalPlaylist(id, started)
                // This is an online playlist
                playlistRepository
                    .getPlaylistData(id, getString(Res.string.view_count))
                    .collect { res ->
                        if (loadingId != id) return@collect
                        val data = res.data
                        when (res) {
                            is Resource.Success if (data != null) -> {
                                Logger.d(tag) { "Playlist data: ${Logger.clip(data)}" }
                                log("Playlist endpoint: ${data.first.shuffleEndpoint}")
                                rememberSetVideoIds(data.first.tracks)
                                val shown = _tracks.value.map { it.videoId }
                                val fresh = data.first.tracks.map { it.videoId }
                                when {
                                    !localShown -> {
                                        _tracks.value = data.first.tracks
                                        setContinuation(data.second)
                                    }
                                    // The same list: rows, download marks and scroll stay as they are;
                                    // only the next page comes from the network.
                                    shown == fresh -> {
                                        setContinuation(data.second)
                                        Logger.i("Offline", "playlist $id network same ${started.elapsedNow().inWholeMilliseconds} ms")
                                    }
                                    // The saved copy holds the whole playlist (paged through for a
                                    // download) and the network's first page is its beginning:
                                    // keep the whole list, there is nothing to page.
                                    data.second != null && shown.size > fresh.size && shown.subList(0, fresh.size) == fresh -> {
                                        Logger.i("Offline", "playlist $id network same ${started.elapsedNow().inWholeMilliseconds} ms")
                                    }
                                    else -> {
                                        _tracks.value = data.first.tracks
                                        setContinuation(data.second)
                                        Logger.i("Offline", "playlist $id network replaced ${started.elapsedNow().inWholeMilliseconds} ms")
                                    }
                                }
                                _uiState.value =
                                    Success(
                                        data =
                                            PlaylistState(
                                                id = data.first.id,
                                                title = data.first.title,
                                                isRadio = false,
                                                author = data.first.author,
                                                thumbnail =
                                                    data.first.thumbnails
                                                        .lastOrNull()
                                                        ?.url,
                                                description = data.first.description,
                                                trackCount = data.first.trackCount,
                                                year = data.first.year,
                                                shuffleEndpoint = data.first.shuffleEndpoint,
                                                radioEndpoint = data.first.radioEndpoint,
                                                isOwned = data.first.isOwned,
                                            ),
                                    )
                                getPlaylistEntity(id = data.first.id, playlistBrowse = data.first, complete = data.second.isNullOrEmpty())
                            }

                            else -> {
                                if (localShown) {
                                    Logger.i(
                                        "Offline",
                                        "playlist $id network failed, local stays ${started.elapsedNow().inWholeMilliseconds} ms: ${res.message}",
                                    )
                                } else {
                                    getPlaylistEntity(id)
                                }
                            }
                        }
                    }
            }
        }
    }

    fun getContinuationTrack(
        playlistId: String,
        continuation: String?,
    ) {
        viewModelScope.launch {
            if (continuation.isNullOrEmpty()) {
                _tracksListState.value = ListState.PAGINATION_EXHAUST
                return@launch
            } else {
                _tracksListState.value = ListState.PAGINATING
                songRepository
                    .getContinueTrack(
                        playlistId,
                        continuation,
                        fromPlaylist = true,
                    ).collectLatest { res ->
                        res.first?.forEach { track ->
                            songRepository
                                .insertSong(
                                    track
                                        .toSongEntity()
                                        .copy(
                                            inLibrary = Config.REMOVED_SONG_DATE_TIME,
                                        ),
                                ).singleOrNull()
                                ?.let {
                                    log("Insert song: $it")
                                }
                        }
                        rememberSetVideoIds(res.first.orEmpty())
                        _tracks.update {
                            val newList = it.toMutableList()
                            newList.addAll(res.first ?: emptyList())
                            newList
                        }
                        if (res.second.isNullOrEmpty()) {
                            _continuation.value = null
                            _tracksListState.value = ListState.PAGINATION_EXHAUST
                        } else {
                            _continuation.value = res.second
                            _tracksListState.value = ListState.IDLE
                        }
                    }
            }
        }
    }

    private fun getPlaylistEntity(
        id: String,
        playlistBrowse: PlaylistBrowse? = null,
        // The network sent the whole playlist, not a first page with more to come.
        complete: Boolean = false,
    ) {
        playlistEntityJob?.cancel()
        playlistEntityJob =
            viewModelScope.launch {
                val playlistEntity = playlistRepository.getPlaylist(id).firstOrNull()
                if (playlistBrowse != null) {
                    if (playlistEntity == null) {
                        playlistRepository.insertAndReplacePlaylist(
                            playlistBrowse.toPlaylistEntity(),
                        )
                        delay(500)
                        playlistRepository.getPlaylist(id).collectLatest { playlist ->
                            _playlistEntity.value = playlist
                            playlistRepository.updatePlaylistInLibrary(
                                playlistId = id,
                                inLibrary = now(),
                            )
                        }
                    } else {
                        _playlistEntity.value = playlistEntity
                        playlistRepository.updatePlaylistInLibrary(
                            playlistId = id,
                            inLibrary = now(),
                        )
                    }
                    playlistBrowse.tracks.forEach { tracks ->
                        songRepository
                            .insertSong(
                                tracks.toSongEntity().copy(
                                    inLibrary = Config.REMOVED_SONG_DATE_TIME,
                                ),
                            ).firstOrNull()
                            ?.let {
                                log("Insert song: $it")
                            }
                    }
                    if (playlistEntity != null) {
                        // The saved copy follows the network (title, cover, count, and the list when
                        // the network sent all of it), after its songs exist; liked and download state stay.
                        if (playlistRepository.refreshPlaylistContent(playlistBrowse.toPlaylistEntity(), complete)) {
                            Logger.i("Offline", "playlist $id saved copy updated")
                        }
                    }
                } else if (playlistEntity != null) {
                    applyLocalPlaylist(playlistEntity, playlistEntity.tracks?.let { tracksOf(it) } ?: emptyList())
                } else {
                    _uiState.value = Error("Empty response")
                }
            }
    }

    private fun setContinuation(continuation: String?) {
        _continuation.value = continuation
        _tracksListState.value = if (continuation.isNullOrEmpty()) ListState.PAGINATION_EXHAUST else ListState.IDLE
    }

    private suspend fun tracksOf(videoIds: List<String>): List<Track> =
        songRepository.getSongsByListVideoId(videoIds).singleOrNull()?.map { it.toTrack() } ?: emptyList()

    /**
     * Shows the saved copy of playlist [id] if the database has one with its tracks; returns whether
     * it did. Its whole saved list is shown and nothing is paged after it.
     */
    private suspend fun showLocalPlaylist(
        id: String,
        started: TimeMark,
    ): Boolean {
        val entity = playlistRepository.getPlaylist(id).firstOrNull() ?: return false
        val tracks = tracksOf(entity.tracks ?: return false)
        if (tracks.isEmpty() || loadingId != id) return false
        applyLocalPlaylist(entity, tracks)
        Logger.i("Offline", "playlist $id local shown ${started.elapsedNow().inWholeMilliseconds} ms")
        return true
    }

    private fun applyLocalPlaylist(
        playlistEntity: PlaylistEntity,
        tracks: List<Track>,
    ) {
        _playlistEntity.value = playlistEntity
        _uiState.value =
            Success(
                data =
                    PlaylistState(
                        id = playlistEntity.id,
                        title = playlistEntity.title,
                        isRadio = false,
                        author =
                            Author(
                                id = "",
                                name = playlistEntity.author ?: "",
                            ),
                        thumbnail = playlistEntity.thumbnails,
                        description = playlistEntity.description,
                        trackCount = playlistEntity.trackCount,
                        year = playlistEntity.year ?: now().year.toString(),
                    ),
            )
        _tracks.value = tracks
        _continuation.value = null
        _tracksListState.value = ListState.PAGINATION_EXHAUST
        // After the screen is filled: this write can wait seconds behind other database work.
        viewModelScope.launch {
            val writeStarted = TimeSource.Monotonic.markNow()
            playlistRepository.updatePlaylistInLibrary(
                playlistId = playlistEntity.id,
                inLibrary = now(),
            )
            Logger.d(tag) { "playlist ${playlistEntity.id} inLibrary written in ${writeStarted.elapsedNow().inWholeMilliseconds} ms" }
        }
        if (playlistEntity.downloadState != STATE_DOWNLOADED) {
            checkDownloadedPlaylist?.cancel()
            checkDownloadedPlaylist =
                viewModelScope.launch {
                    val listSong =
                        songRepository
                            .getSongsByListVideoId(
                                playlistEntity.tracks ?: emptyList(),
                            ).firstOrNull()
                    Logger.d(tag, "List song: $listSong")
                    if (!listSong.isNullOrEmpty() && listSong.size == playlistEntity.tracks?.size &&
                        listSong.all {
                            it.downloadState == STATE_DOWNLOADED
                        }
                    ) {
                        updatePlaylistDownloadState(playlistEntity.id, STATE_DOWNLOADED)
                    }
                }
        }
    }

    fun setBrush(listColors: List<Color>) {
        _listColors.value = listColors
    }

    fun updatePlaylistLiked(
        liked: Boolean,
        id: String,
    ) {
        viewModelScope.launch {
            val tempLiked = if (liked) 1 else 0
            playlistRepository.updatePlaylistLiked(id, tempLiked)
            _playlistEntity.update {
                it?.copy(
                    liked = tempLiked == 1,
                )
            }
            getFullTracks { }
        }
    }

    fun onUIEvent(event: PlaylistUIEvent) {
        val data = uiState.value.data ?: return
        when (event) {
            is PlaylistUIEvent.ItemClick -> {
                val videoId = event.videoId
                val loadedList = tracks.value
                val clickedSong = loadedList.first { it.videoId == videoId }
                val index = loadedList.indexOf(clickedSong)
                setQueueData(
                    QueueData.Data(
                        listTracks = loadedList.toCollection(arrayListOf<Track>()),
                        firstPlayedTrack = clickedSong,
                        playlistId = data.id,
                        playlistName = "${
                            getString(
                                Res.string.playlist,
                            )
                        } \"${data.title}\"",
                        playlistType = PlaylistType.PLAYLIST,
                        continuation = continuation.value,
                    ),
                )
                loadMediaItem(
                    clickedSong,
                    Config.PLAYLIST_CLICK,
                    index,
                )
            }

            PlaylistUIEvent.PlayAll -> {
                val loadedList = tracks.value
                if (loadedList.isEmpty()) {
                    makeToast(
                        getString(Res.string.playlist_is_empty),
                    )
                    return
                }
                val clickedSong = loadedList.first()
                setQueueData(
                    QueueData.Data(
                        listTracks = loadedList.toCollection(arrayListOf<Track>()),
                        firstPlayedTrack = clickedSong,
                        playlistId = data.id,
                        playlistName = "${
                            getString(
                                Res.string.playlist,
                            )
                        } \"${data.title}\"",
                        playlistType = PlaylistType.PLAYLIST,
                        continuation = continuation.value,
                    ),
                )
                loadMediaItem(
                    clickedSong,
                    Config.PLAYLIST_CLICK,
                    0,
                )
            }

            PlaylistUIEvent.Shuffle -> {
                val shuffleEndpoint = data.shuffleEndpoint
                if (shuffleEndpoint == null) {
                    makeToast(
                        getString(Res.string.shuffle_not_available),
                    )
                    return
                } else {
                    viewModelScope.launch {
                        songRepository.getRadioFromEndpoint(shuffleEndpoint).collectLatest { res ->
                            val result = res.data
                            when (res) {
                                is Resource.Success if (result != null) -> {
                                    Logger.d(tag, "Shuffle data: ${result.first.size}")
                                    setQueueData(
                                        QueueData.Data(
                                            listTracks = result.first.toCollection(arrayListOf<Track>()),
                                            firstPlayedTrack = result.first.firstOrNull() ?: return@collectLatest,
                                            playlistId = shuffleEndpoint.playlistId,
                                            playlistName = "\"${data.title}\" ${getString(Res.string.shuffle)}",
                                            playlistType = PlaylistType.RADIO,
                                            continuation = result.second,
                                        ),
                                    )
                                    loadMediaItem(
                                        result.first.firstOrNull() ?: return@collectLatest,
                                        Config.RADIO_CLICK,
                                        0,
                                    )
                                }

                                else -> {
                                    makeToast(
                                        res.message ?: getString(Res.string.error),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PlaylistUIEvent.StartRadio -> {
                val radioEndpoint = data.radioEndpoint
                if (radioEndpoint == null) {
                    makeToast(
                        getString(Res.string.radio_not_available),
                    )
                    return
                } else {
                    viewModelScope.launch {
                        songRepository.getRadioFromEndpoint(radioEndpoint).collectLatest { res ->
                            val result = res.data
                            when (res) {
                                is Resource.Success if (result != null) -> {
                                    Logger.d(tag, "Radio data: ${result.first.size}")
                                    setQueueData(
                                        QueueData.Data(
                                            listTracks = result.first.toCollection(arrayListOf<Track>()),
                                            firstPlayedTrack = result.first.firstOrNull() ?: return@collectLatest,
                                            playlistId = radioEndpoint.playlistId,
                                            playlistName = "\"${data.title}\" ${getString(Res.string.radio)}",
                                            playlistType = PlaylistType.RADIO,
                                            continuation = result.second,
                                        ),
                                    )
                                    loadMediaItem(
                                        result.first.firstOrNull() ?: return@collectLatest,
                                        Config.RADIO_CLICK,
                                        0,
                                    )
                                }

                                else -> {
                                    makeToast(
                                        res.message ?: getString(Res.string.error),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PlaylistUIEvent.Download -> {
                downloadFullPlaylist()
            }

            PlaylistUIEvent.Favorite -> {
                updatePlaylistLiked(!liked.value, data.id)
            }
        }
    }

    fun getFullTracks(callback: (List<Track>) -> Unit) {
        viewModelScope.launch {
            if (tracksListState.value == ListState.PAGINATION_EXHAUST) {
                _playlistEntity.value
                    ?.copy(
                        tracks = tracks.value.toListVideoId(),
                        trackCount = tracks.value.size,
                    )?.let {
                        playlistRepository.insertAndReplacePlaylist(it)
                    }
                callback(tracks.value)
            } else {
                val id = uiState.value.data?.id ?: return@launch
                tracksListState.collectLatest { state ->
                    if (state == ListState.PAGINATION_EXHAUST) {
                        _playlistEntity.value
                            ?.copy(
                                tracks = tracks.value.toListVideoId(),
                                trackCount = tracks.value.size,
                            )?.let {
                                playlistRepository.insertAndReplacePlaylist(it)
                            }
                        callback(tracks.value)
                    } else if (state != ListState.PAGINATING) {
                        getContinuationTrack(id, continuation.value)
                    }
                }
            }
        }
    }

    fun saveToLocal(tracks: List<Track>) {
        viewModelScope.launch {
            val data = uiState.value.data ?: return@launch
            localPlaylistRepository
                .syncYouTubePlaylistToLocalPlaylist(
                    data,
                    tracks,
                    getString(Res.string.synced),
                    getString(Res.string.error),
                ).collectLatestResource(
                    onSuccess = {
                        makeToast(it)
                    },
                    onLoading = {
                        makeToast(getString(Res.string.syncing))
                    },
                    onError = {
                        makeToast(it)
                    },
                )
        }
    }

    fun downloadFullPlaylist() {
        viewModelScope.launch {
            val id = playlistEntity.value?.id ?: return@launch
            makeToast(getString(Res.string.downloading))
            updatePlaylistDownloadState(id, STATE_DOWNLOADING)
            getFullTracks { tracks ->
                // The user asks for all of them, those whose download they removed too.
                viewModelScope.launch { AutoDownloadGuard.clearRemoved(dataStoreManager, tracks.map { it.videoId }) }
                tracks.forEach {
                    viewModelScope.launch {
                        downloadUtils.downloadTrack(it.videoId, it.title, it.thumbnails?.lastOrNull()?.url ?: "")
                    }
                }
            }
        }
    }

    fun updatePlaylistTitle(
        title: String,
        id: String,
    ) {
        viewModelScope.launch {
            playlistRepository
                .updateYourYouTubePlaylistTitle(
                    id,
                    title,
                ).collect {
                    when (it) {
                        is Resource.Success -> {
                            getData(id)
                            makeToast(it.data ?: getString(Res.string.synced))
                        }

                        is Resource.Error -> {
                            makeToast(it.message ?: getString(Res.string.error))
                        }
                    }
                }
        }
    }

    private fun rememberSetVideoIds(tracks: List<Track>) {
        tracks.forEach { track -> track.setVideoId?.let { id -> setVideoIds.getOrPut(track.videoId) { id } } }
    }

    /** Removes [track] from the open playlist in the account; its row goes once YouTube agrees. */
    fun removeFromYouTubePlaylist(track: Track) {
        val data = uiState.value.data ?: return
        if (!data.isOwned) return
        viewModelScope.launch {
            playlistRepository
                .removeYouTubePlaylistItem(data.id, track.videoId, track.setVideoId ?: setVideoIds[track.videoId])
                .collect { res ->
                    when (res) {
                        is Resource.Success -> {
                            val removed = res.data
                            if (loadingId == data.id) {
                                _tracks.update { list ->
                                    val index =
                                        list
                                            .indexOfFirst { (it.setVideoId ?: setVideoIds[it.videoId]) == removed && it.videoId == track.videoId }
                                            .takeIf { it >= 0 }
                                            ?: list.indexOfFirst { it.videoId == track.videoId }
                                    if (index < 0) list else list.toMutableList().apply { removeAt(index) }
                                }
                                setVideoIds.remove(track.videoId)
                                _playlistEntity.update { entity ->
                                    entity?.copy(
                                        tracks = entity.tracks?.toMutableList()?.apply { remove(track.videoId) },
                                        trackCount = (entity.trackCount - 1).coerceAtLeast(0),
                                    )
                                }
                                (uiState.value as? Success)?.data?.takeIf { it.id == data.id }?.let { shown ->
                                    _uiState.value = Success(shown.copy(trackCount = (shown.trackCount - 1).coerceAtLeast(0)))
                                }
                            }
                            makeToast(getString(Res.string.removed_from_YouTube_playlist))
                        }

                        is Resource.Error -> {
                            makeToast(getString(Res.string.remove_from_youtube_playlist_failed))
                        }
                    }
                }
        }
    }

    /** Deletes the open playlist from the account; [deleted] turns true when it is gone. */
    fun deleteYouTubePlaylist() {
        val data = uiState.value.data ?: return
        if (!data.isOwned) return
        viewModelScope.launch {
            playlistRepository.deleteYouTubePlaylist(data.id).collect { res ->
                when (res) {
                    is Resource.Success -> {
                        makeToast(getString(Res.string.youtube_playlist_deleted))
                        if (loadingId == data.id) _deleted.value = true
                    }

                    is Resource.Error -> {
                        makeToast(getString(Res.string.youtube_playlist_delete_failed))
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        collectDownloadedJob?.cancel()
        playlistEntityJob?.cancel()
    }
}

sealed class PlaylistUIState(
    val data: PlaylistState? = null,
    val message: String? = null,
) {
    data object Loading : PlaylistUIState()

    class Success(
        data: PlaylistState,
    ) : PlaylistUIState(
            data = data,
        )

    class Error(
        message: String? = null,
    ) : PlaylistUIState(
            message = message,
        )
}

sealed class PlaylistUIEvent {
    data object PlayAll : PlaylistUIEvent()

    data object Shuffle : PlaylistUIEvent()

    data object StartRadio : PlaylistUIEvent()

    data class ItemClick(
        val videoId: String,
    ) : PlaylistUIEvent()

    data object Favorite : PlaylistUIEvent()

    data object Download : PlaylistUIEvent()
}

enum class ListState {
    IDLE,
    LOADING,
    PAGINATING,
    ERROR,
    PAGINATION_EXHAUST,
}
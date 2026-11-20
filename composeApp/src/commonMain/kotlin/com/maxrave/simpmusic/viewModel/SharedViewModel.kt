package com.maxrave.simpmusic.viewModel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.common.Config.ALBUM_CLICK
import com.maxrave.common.Config.DOWNLOAD_CACHE
import com.maxrave.common.Config.PLAYLIST_CLICK
import com.maxrave.common.Config.RECOVER_TRACK_QUEUE
import com.maxrave.common.Config.SHARE
import com.maxrave.common.Config.SONG_CLICK
import com.maxrave.common.Config.VIDEO_CLICK
import com.maxrave.common.LibraryChipType
import com.maxrave.common.MERGING_DATA_TYPE
import com.maxrave.common.SELECTED_LANGUAGE
import com.maxrave.common.STATUS_DONE
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.LyricsEntity
import com.maxrave.domain.data.entities.NewFormatEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.entities.SongInfoEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.download.DownloadProgress
import com.maxrave.domain.data.model.intent.GenericIntent
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.data.model.streams.TimeLine
import com.maxrave.domain.data.model.update.UpdateData
import com.maxrave.domain.data.player.LiveStreamRegistry
import com.maxrave.domain.extension.decodeHtmlEntities
import com.maxrave.domain.extension.isVideo
import com.maxrave.domain.extension.toGenericMediaItem
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.manager.DataStoreManager.Values.TRUE
import com.maxrave.domain.mediaservice.handler.ControlState
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.NowPlayingTrackState
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.domain.mediaservice.handler.SimpleMediaState
import com.maxrave.domain.mediaservice.handler.SleepTimerState
import com.maxrave.domain.mediaservice.handler.addToQueueBlock
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.CacheRepository
import com.maxrave.domain.repository.DiscordRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.LyricsCanvasRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.repository.StreamRepository
import com.maxrave.domain.repository.UpdateRepository
import com.maxrave.domain.mediaservice.handler.currentLyricsOffsetMs
import com.maxrave.domain.utils.LikeFailure
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.YouTubeLikeStatus
import com.maxrave.domain.utils.isEndlessQueueId
import com.maxrave.domain.utils.toLyrics
import com.maxrave.domain.utils.toLyricsEntity
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.domain.utils.toTrack
import com.maxrave.logger.LogLevel
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.expect.getDownloadFolderPath
import com.maxrave.simpmusic.expect.isOnUnmeteredNetwork
import com.maxrave.simpmusic.expect.ui.toByteArray
import com.maxrave.simpmusic.getPlatform
import com.maxrave.simpmusic.ui.screen.player.albumIdOf
import com.maxrave.simpmusic.utils.LyricsSource
import com.maxrave.simpmusic.utils.LyricsSourceEntry
import com.maxrave.simpmusic.utils.LyricsSourceQueue
import com.maxrave.simpmusic.utils.VersionManager
import com.maxrave.simpmusic.utils.hasText
import com.maxrave.simpmusic.utils.isSynced
import com.maxrave.simpmusic.utils.pickUpdateDownloadUrl
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import com.maxrave.simpmusic.ui.screen.player.content.artworkSeedColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.core.component.inject
import simpmusic.composeapp.generated.resources.album_not_found
import simpmusic.composeapp.generated.resources.log_in_to_like
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.youtube_playlist_create_failed
import simpmusic.composeapp.generated.resources.youtube_playlist_created
import simpmusic.composeapp.generated.resources.added_local_playlist
import simpmusic.composeapp.generated.resources.added_to_queue
import simpmusic.composeapp.generated.resources.disliked
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.discord_login_failed
import simpmusic.composeapp.generated.resources.login_success
import simpmusic.composeapp.generated.resources.play_next
import simpmusic.composeapp.generated.resources.shared
import simpmusic.composeapp.generated.resources.updated
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// DataStore key for the kotlin-footguns star prompt. Cleared on every version bump,
// so the ask comes back after an update - same policy as OPEN_APP_TIME.
private const val SHOW_ANALYTICS_TAB_KEY = "show_analytics_tab"

// The lyrics source queue in the log: which source was asked for which track, and what it gave.
private const val LYRICS_QUEUE_TAG = "LyricsQueue"

@OptIn(ExperimentalCoroutinesApi::class)
class SharedViewModel(
    private val dataStoreManager: DataStoreManager,
    private val streamRepository: StreamRepository,
    private val updateRepository: UpdateRepository,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val playlistRepository: PlaylistRepository,
    private val lyricsCanvasRepository: LyricsCanvasRepository,
    private val cacheRepository: CacheRepository,
    private val discordRepository: DiscordRepository,
    private val artistRepository: ArtistRepository,
    private val searchRepository: SearchRepository,
) : BaseViewModel() {
    var isFirstLiked: Boolean = false
    var isFirstMiniplayer: Boolean = false
    var isFirstSuggestions: Boolean = false
    var showedUpdateDialog: Boolean = false


    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate

    private var _liked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val liked: SharedFlow<Boolean> = _liked.asSharedFlow()

    var isServiceRunning: Boolean = false

    private var _sleepTimerState = MutableStateFlow(SleepTimerState(false, 0))
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState

    private var regionCode: String? = null
    private var language: String? = null

    private var _format: MutableStateFlow<NewFormatEntity?> = MutableStateFlow(null)
    val format: SharedFlow<NewFormatEntity?> = _format.asSharedFlow()

    /**
     * Which extractor and cipher decoder produced the current track's URLs, shown in the info sheet.
     *
     * Read alongside the format rather than stored with it: the format row is cached and reused,
     * while this describes the extraction that happened in this run of the app.
     */
    private val _extractSource: MutableStateFlow<String?> = MutableStateFlow(null)
    val extractSource: StateFlow<String?> = _extractSource.asStateFlow()

    private val _intent: MutableStateFlow<GenericIntent?> = MutableStateFlow(null)
    val intent: StateFlow<GenericIntent?> = _intent

    private val _showNotificationPermissionDialog = MutableStateFlow(false)
    val showNotificationPermissionDialog: StateFlow<Boolean> = _showNotificationPermissionDialog

    // One-shot: the Desktop capsule asks the Now Playing panel, which hosts the page, to open
    // full-screen lyrics. The panel consumes it once shown.
    private val _fullscreenLyricsRequest = MutableStateFlow(false)
    val fullscreenLyricsRequest: StateFlow<Boolean> = _fullscreenLyricsRequest

    fun requestFullscreenLyrics() {
        _fullscreenLyricsRequest.value = true
    }

    fun consumeFullscreenLyricsRequest() {
        _fullscreenLyricsRequest.value = false
    }

    private var getFormatFlowJob: Job? = null

    var playlistId: MutableStateFlow<String?> = MutableStateFlow(null)

    var isFullScreen: Boolean = false

    private var _nowPlayingState = MutableStateFlow<NowPlayingTrackState?>(null)
    val nowPlayingState: StateFlow<NowPlayingTrackState?> = _nowPlayingState

    fun getQueueDataState() = mediaPlayerHandler.queueData

    private var _controllerState =
        MutableStateFlow<ControlState>(
            ControlState(
                isPlaying = false,
                isShuffle = false,
                repeatState = RepeatState.None,
                isLiked = false,
                isNextAvailable = false,
                isPreviousAvailable = false,
                isCrossfading = false,
                volume = 1f,
            ),
        )
    val controllerState: StateFlow<ControlState> = _controllerState
    private val _getVideo: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val getVideo: StateFlow<Boolean> = _getVideo

    private var _timeline =
        MutableStateFlow<TimeLine>(
            TimeLine(
                current = -1L,
                total = -1L,
                bufferedPercent = 0,
                loading = true,
            ),
        )
    val timeline: StateFlow<TimeLine> = _timeline

    private var _nowPlayingScreenData =
        MutableStateFlow<NowPlayingScreenData>(
            NowPlayingScreenData.initial(),
        )
    val nowPlayingScreenData: StateFlow<NowPlayingScreenData> = _nowPlayingScreenData

    private var _likeStatus = MutableStateFlow(YouTubeLikeStatus.INDIFFERENT)
    val likeStatus: StateFlow<YouTubeLikeStatus> = _likeStatus
    private var likeStatusJob: Job? = null

    // When the lyrics of a track were asked for, for the `Offline: lyrics` lines: how long until a
    // text was on screen. Reset on every track change.
    private var lyricsRequestedAt: Pair<String, TimeMark>? = null

    private fun lyricsElapsedMs(videoId: String): Long? =
        lyricsRequestedAt?.takeIf { it.first == videoId }?.second?.elapsedNow()?.inWholeMilliseconds

    // The track whose dislike request is still waiting for YouTube. An id rather than a flag, so the
    // pending state belongs to that track only and the next one starts out dislikeable.
    private val _dislikingVideoId = MutableStateFlow<String?>(null)
    val isDislikePending: StateFlow<Boolean> =
        combine(_dislikingVideoId, mediaPlayerHandler.nowPlaying) { pending, now ->
            pending != null && pending == now?.mediaId
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Which body of the Apple Music player was open last — held by ENUM NAME so this class stays
     * ignorant of the UI enum, which is internal to the player package.
     *
     * It cannot live in the composable: that player is inside a ModalBottomSheet, so dismissing
     * the sheet disposes the whole tree and takes any rememberSaveable with it — the tab snapped
     * back to the artwork every single time it was reopened. This class is a Koin `single`, so it
     * outlives the sheet while still resetting on app restart, which is the right lifetime for
     * "where I was a moment ago".
     */
    private val _lastPlayerViewTab = MutableStateFlow<String?>(null)
    val lastPlayerViewTab: StateFlow<String?> = _lastPlayerViewTab

    fun setLastPlayerViewTab(tabName: String) {
        _lastPlayerViewTab.value = tabName
    }

    fun setIntent(intent: GenericIntent?) {
        _intent.value = intent
    }

    /**
     * Finishes a Discord login from the `simpmusic://discord-auth` redirect (Full builds). The login
     * screen is already open and closes itself once a login is stored.
     */
    fun completeDiscordLogin(callbackUrl: String) {
        viewModelScope.launch {
            if (discordRepository.completeLogin(callbackUrl)) {
                makeToast(getString(Res.string.login_success))
            } else {
                makeToast(getString(Res.string.discord_login_failed))
            }
        }
    }

    fun showNotificationPermissionDialog() {
        _showNotificationPermissionDialog.value = true
    }

    fun dismissNotificationPermissionDialog(doNotShowAgain: Boolean) {
        _showNotificationPermissionDialog.value = false
        if (doNotShowAgain) {
            putString("notification_permission_do_not_ask", "true")
        }
    }

    private fun getLikeStatus(videoId: String?) {
        // Only the latest track may write: a slower reply for the previous track would otherwise
        // land on this one.
        likeStatusJob?.cancel()
        likeStatusJob =
            viewModelScope.launch {
                if (videoId != null) {
                    _likeStatus.value = YouTubeLikeStatus.INDIFFERENT
                    songRepository.getLikeStatus(videoId.removePrefix(MERGING_DATA_TYPE.VIDEO)).collectLatest { status ->
                        _likeStatus.value = status
                    }
                }
            }
    }

    fun getString(key: String): String? = runBlocking { dataStoreManager.getString(key).first() }

    // A Library tab asked for from outside the Library screen (a deep link); the screen selects it
    // the next time it composes and clears the request.
    private val _libraryTabRequest = MutableStateFlow<LibraryChipType?>(null)
    val libraryTabRequest: StateFlow<LibraryChipType?> = _libraryTabRequest.asStateFlow()

    fun requestLibraryTab(tab: LibraryChipType?) {
        _libraryTabRequest.value = tab
    }

    val analyticsTabEnabled: Flow<Boolean> =
        dataStoreManager.getString(SHOW_ANALYTICS_TAB_KEY).map { it != "false" }

    fun setAnalyticsTabEnabled(show: Boolean) {
        putString(SHOW_ANALYTICS_TAB_KEY, show.toString())
    }

    fun putString(
        key: String,
        value: String,
    ) {
        runBlocking { dataStoreManager.putString(key, value) }
    }

    fun setSleepTimer(minutes: Int) {
        mediaPlayerHandler.sleepStart(minutes)
    }

    fun stopSleepTimer() {
        mediaPlayerHandler.sleepStop()
    }

    private var _downloadState: MutableStateFlow<DownloadHandler.Download?> = MutableStateFlow(null)
    var downloadState: StateFlow<DownloadHandler.Download?> = _downloadState.asStateFlow()

    fun checkIsRestoring() {
        viewModelScope.launch {
            val downloadedCacheKeys = cacheRepository.getAllCacheKeys(DOWNLOAD_CACHE)
            songRepository.getDownloadedSongs().first().let { songs ->
                songs?.forEach { song ->
                    if (!downloadedCacheKeys.contains(song.videoId)) {
                        songRepository.updateDownloadState(
                            song.videoId,
                            DownloadState.STATE_NOT_DOWNLOADED,
                        )
                    }
                }
            }
            playlistRepository.getAllDownloadedPlaylist().first().let { list ->
                for (data in list) {
                    when (data) {
                        is AlbumEntity -> {
                            val tracks = data.tracks ?: emptyList()
                            if (tracks.isEmpty() ||
                                (
                                    !downloadedCacheKeys.containsAll(
                                        tracks,
                                    )
                                )
                            ) {
                                albumRepository.updateAlbumDownloadState(
                                    data.browseId,
                                    DownloadState.STATE_NOT_DOWNLOADED,
                                )
                            }
                        }

                        is PlaylistEntity -> {
                            val tracks = data.tracks ?: emptyList()
                            if (tracks.isEmpty() ||
                                (
                                    !downloadedCacheKeys.containsAll(
                                        tracks,
                                    )
                                )
                            ) {
                                playlistRepository.updatePlaylistDownloadState(
                                    data.id,
                                    DownloadState.STATE_NOT_DOWNLOADED,
                                )
                            }
                        }

                        is LocalPlaylistEntity -> {
                            val tracks = data.tracks ?: emptyList()
                            if (tracks.isEmpty() ||
                                (
                                    !downloadedCacheKeys.containsAll(
                                        tracks,
                                    )
                                )
                            ) {
                                localPlaylistRepository.updateLocalPlaylistDownloadState(
                                    DownloadState.STATE_NOT_DOWNLOADED,
                                    data.id,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun insertLyrics(lyrics: LyricsEntity) {
        viewModelScope.launch {
            lyricsCanvasRepository.insertLyrics(lyrics)
        }
    }

    // The read of the saved lyrics of the track playing. One at a time: a newer track's read replaces it.
    private var savedLyricsJob: Job? = null

    /**
     * Shows the lyrics saved the last time this track played, at once, while the providers are
     * still being asked: without a network they are all there is, and with one they are on screen
     * until a provider answers ([updateLyrics] replaces them, or keeps them when the text is the same).
     */
    private fun showSavedLyrics(
        videoId: String,
        duration: Int,
    ) {
        savedLyricsJob?.cancel()
        savedLyricsJob =
            viewModelScope.launch {
                val saved = lyricsCanvasRepository.getSavedLyrics(videoId).firstOrNull() ?: return@launch
                // The track changed during the read, or a provider was quicker: neither is ours to cover.
                if (_nowPlayingState.value?.songEntity?.videoId != videoId) return@launch
                if (_nowPlayingScreenData.value.lyricsData != null) return@launch
                Logger.i("Offline", "lyrics $videoId saved shown ${lyricsElapsedMs(videoId)} ms")
                updateLyrics(videoId, duration, saved.toLyrics(), false, LyricsProvider.OFFLINE)
            }
    }

    /**
     * Every provider failed for [videoId]. Only now, and only when not even saved lyrics are on
     * screen, is the song known to have no lyrics, which is what offers Add lyrics.
     */
    private fun onLyricsProvidersFailed(videoId: String) {
        viewModelScope.launch {
            // Offline every provider fails at once, which can be before the saved copy is read.
            savedLyricsJob?.join()
            if (_nowPlayingState.value?.songEntity?.videoId != videoId) return@launch
            if (_nowPlayingScreenData.value.lyricsData != null) return@launch
            _nowPlayingScreenData.update { it.copy(lyricsNotFound = true) }
            Logger.i("Offline", "lyrics $videoId not found ${lyricsElapsedMs(videoId)} ms")
        }
    }

    /**
     * Lyrics typed into Add lyrics take the offline lyrics' path: [updateLyrics] caches and shows
     * them, and contributes them when Help build lyrics database is on. The cache write of its own
     * keeps them for a song that ended while they were being typed, which [updateLyrics] skips.
     */
    fun saveUserLyrics(
        videoId: String,
        lyrics: Lyrics,
    ) {
        updateLyrics(videoId, (timeline.value.total / 1000).toInt(), lyrics, false, LyricsProvider.OFFLINE)
        insertLyrics(lyrics.toLyricsEntity(videoId))
    }

    fun loadSharedMediaItem(videoId: String) {
        viewModelScope.launch {
            val localSong = songRepository.getSongById(videoId).firstOrNull()
            if (localSong != null) {
                val track = localSong.toTrack()
                mediaPlayerHandler.setQueueData(
                    QueueData.Data(
                        listTracks = arrayListOf(track),
                        firstPlayedTrack = track,
                        playlistId = "RDAMVM$videoId",
                        playlistName = getString(Res.string.shared),
                        playlistType = PlaylistType.RADIO,
                        continuation = null,
                    ),
                )
                loadMediaItemFromTrack(track, SONG_CLICK)
            } else {
                // The stream resolves while the metadata page loads (2.5 s on the phone, 2026-11-16):
                // the player asks only once the page is in, and then joins this request.
                streamRepository.prefetchStream(videoId)
                streamRepository.getFullMetadata(videoId).collectLatest { response ->
                    val track = response.data
                    when (response) {
                        is Resource.Success if (track != null) -> {
                            mediaPlayerHandler.setQueueData(
                                QueueData.Data(
                                    listTracks = arrayListOf(track),
                                    firstPlayedTrack = track,
                                    playlistId = "RDAMVM$videoId",
                                    playlistName = getString(Res.string.shared),
                                    playlistType = PlaylistType.RADIO,
                                    continuation = null,
                                ),
                            )
                            loadMediaItemFromTrack(track, SONG_CLICK)
                        }

                        else -> {
                            log("Load shared media item error: ${response.message}", LogLevel.WARN)
                            makeToast("${getString(Res.string.error)}: ${response.message}")
                        }
                    }
                }
            }
        }
    }

    fun loadMediaItemFromTrack(
        track: Track,
        type: String,
        index: Int? = null,
    ) {
        viewModelScope.launch {
            mediaPlayerHandler.clearMediaItems()
            // A new row stays out of "Recently added" until the track really plays (the handler sets
            // inLibrary then); insertSong keeps an existing row as it is.
            songRepository.insertSong(track.toSongEntity().copy(inLibrary = Config.REMOVED_SONG_DATE_TIME)).lastOrNull()?.let {
                println("insertSong: $it")
                songRepository
                    .getSongById(track.videoId)
                    .collect { songEntity ->
                        if (songEntity != null) {
                            Logger.d("Check like", "loadMediaItemFromTrack ${songEntity.liked}")
                            _liked.value = songEntity.liked
                        }
                    }
            }
            track.durationSeconds?.let {
                songRepository.updateDurationSeconds(
                    it,
                    track.videoId,
                )
            }
            withContext(Dispatchers.Main) {
                mediaPlayerHandler.addMediaItem(track.toGenericMediaItem(), playWhenReady = type != RECOVER_TRACK_QUEUE)
            }

            when (type) {
                SONG_CLICK -> {
                    mediaPlayerHandler.getRelated(track.videoId)
                }

                VIDEO_CLICK -> {
                    mediaPlayerHandler.getRelated(track.videoId)
                }

                SHARE -> {
                    mediaPlayerHandler.getRelated(track.videoId)
                }

                PLAYLIST_CLICK -> {
                    if (index == null) {
//                                        fetchSourceFromQueue(downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = 0)
                    } else {
//                                        fetchSourceFromQueue(index!!, downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = index)
                    }
                }

                ALBUM_CLICK -> {
                    if (index == null) {
//                                        fetchSourceFromQueue(downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = 0)
                    } else {
//                                        fetchSourceFromQueue(index!!, downloaded = downloaded ?: 0)
                        loadPlaylistOrAlbum(index = index)
                    }
                }
            }
        }
    }

    fun onUIEvent(uiEvent: UIEvent) =
        viewModelScope.launch {
            when (uiEvent) {
                UIEvent.Backward -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.Backward,
                    )
                }

                UIEvent.Forward -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Forward)
                }

                UIEvent.PlayPause -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.PlayPause,
                    )
                }

                UIEvent.Next -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next)
                }

                UIEvent.Previous -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.Previous,
                    )
                }

                UIEvent.SkipToPrevious -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.SkipToPrevious,
                    )
                }

                UIEvent.Stop -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Stop)
                }

                is UIEvent.UpdateProgress -> {
                    mediaPlayerHandler.onPlayerEvent(
                        PlayerEvent.UpdateProgress(
                            uiEvent.newProgress,
                        ),
                    )
                }

                UIEvent.Repeat -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Repeat)
                }

                UIEvent.Shuffle -> {
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.Shuffle)
                }

                UIEvent.ToggleLike -> {
                    Logger.d(tag, "ToggleLike")
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.ToggleLike)
                }

                is UIEvent.UpdateVolume -> {
                    val newVolume = uiEvent.newVolume
                    // Apply to the player first: persisting to DataStore is a suspending disk write
                    // and must not sit between the user's gesture and the audible change.
                    mediaPlayerHandler.onPlayerEvent(PlayerEvent.UpdateVolume(newVolume))
                    dataStoreManager.setPlayerVolume(newVolume)
                }
            }
        }

    override fun onCleared() {
        Logger.d("Check onCleared", "onCleared")
    }

    fun getLocation() {
        regionCode = runBlocking { dataStoreManager.location.first() }
        language = runBlocking { dataStoreManager.getString(SELECTED_LANGUAGE).first() }
    }

    private fun checkAllDownloadingLocalPlaylists() {
        viewModelScope.launch {
            localPlaylistRepository.getAllDownloadingLocalPlaylists().collectLatest { playlists ->
                playlists.forEach { playlist ->
                    localPlaylistRepository.updateDownloadState(playlist.id, 0, successMessage = getString(Res.string.updated)).lastOrNull()
                }
            }
        }
    }

    private fun checkAllDownloadingPlaylists() {
        viewModelScope.launch {
            playlistRepository.getAllDownloadingPlaylist().collectLatest { list ->
                list.forEach { data ->
                    when (data) {
                        is AlbumEntity -> {
                            albumRepository.updateAlbumDownloadState(data.browseId, 0)
                        }

                        is PlaylistEntity -> {
                            playlistRepository.updatePlaylistDownloadState(data.id, 0)
                        }

                        else -> {
                            // Skip
                        }
                    }
                }
            }
        }
    }

    private fun checkAllDownloadingSongs() {
        viewModelScope.launch {
            songRepository.getDownloadingSongs().collect { songs ->
                songs?.forEach { song ->
                    songRepository.updateDownloadState(
                        song.videoId,
                        DownloadState.STATE_NOT_DOWNLOADED,
                    )
                }
            }
            songRepository.getPreparingSongs().collect { songs ->
                songs.forEach { song ->
                    songRepository.updateDownloadState(
                        song.videoId,
                        DownloadState.STATE_NOT_DOWNLOADED,
                    )
                }
            }
        }
    }

    private var smartDownloadsJob: Job? = null

    /**
     * Smart downloads, asked for by MainActivity.onStart: every time the app comes onto the screen,
     * the only moment Android lets it start the download service. Not from init: the Glance widgets
     * create this ViewModel with the app in the background. Opening the app many times a day stays
     * cheap: three settings are read, the pass itself runs at most once a day (the run is stamped
     * before it starts), and never twice at once.
     */
    fun runSmartDownloadsIfDue() {
        if (getPlatform() != Platform.Android) return
        if (smartDownloadsJob?.isActive == true) return
        smartDownloadsJob =
            viewModelScope.launch {
                if (dataStoreManager.smartDownloadsEnabled.first() != TRUE) return@launch
                if (dataStoreManager.localTrackingEnabled.first() != TRUE) return@launch
                val nowMillis = Clock.System.now().toEpochMilliseconds()
                val sinceLastRun = nowMillis - dataStoreManager.smartDownloadsLastRun.first()
                // A last run stamped in the future (the clock was moved back) does not block the pass.
                if (sinceLastRun in 0 until 1.days.inWholeMilliseconds) return@launch
                if (!isOnUnmeteredNetwork()) return@launch
                // Stamped first: a pass that fails is not retried at every launch, and a call arriving
                // while this one runs finds the day already done.
                dataStoreManager.setSmartDownloadsLastRun(nowMillis)
                songRepository.runSmartDownloads(dataStoreManager.smartDownloadsCount.first())
            }
    }

    private fun getFormat(mediaId: String?) {
        if (mediaId != _format.value?.videoId && !mediaId.isNullOrEmpty()) {
            _format.value = null
            _extractSource.value = streamRepository.getExtractSource(mediaId)
            getFormatFlowJob?.cancel()
            getFormatFlowJob =
                viewModelScope.launch {
                    streamRepository.getFormatFlow(mediaId).cancellable().collectLatest { f ->
                        Logger.d(tag, "Get format for $mediaId: itag ${f?.itag}, expires ${f?.expiredTime}")
                        if (f != null) {
                            _format.emit(f)
                        } else {
                            _format.emit(null)
                        }
                        // Re-read on every emission: the first one usually lands before the
                        // extractor has finished, so the source is only known on a later pass.
                        _extractSource.value = streamRepository.getExtractSource(mediaId)
                    }
                }
        }
    }

    private var songInfoJob: Job? = null

    fun getSongInfo(mediaId: String?) {
        songInfoJob?.cancel()
        songInfoJob =
            viewModelScope.launch {
                if (mediaId != null) {
                    songRepository.getSongInfo(mediaId).collect { song ->
                        _nowPlayingScreenData.update {
                            it.copy(
                                songInfoData = song,
                            )
                        }
                    }
                }
            }
    }

    private var _updateResponse = MutableStateFlow<UpdateData?>(null)
    val updateResponse: StateFlow<UpdateData?> = _updateResponse

    fun checkForUpdate() {
        // Raised before the coroutine starts, so Home's launch banner, which reads it right after
        // startup, can never miss a check that is already under way but not yet marked.
        _isCheckingUpdate.value = true
        viewModelScope.launch {
            dataStoreManager.putString(
                "CheckForUpdateAt",
                System.currentTimeMillis().toString(),
            )
            updateRepository.checkForGithubReleaseUpdate().collectLatest { response ->
                val data = response.data
                when (response) {
                    is Resource.Success if (data != null) -> {
                        val newer = VersionManager.isBehind(data.tagName)
                        Logger.i(
                            "Update",
                            "latest ${data.tagName} current ${VersionManager.getVersionName()} newer=$newer " +
                                "url=${if (newer) pickUpdateDownloadUrl(data.assets, data.pageUrl, getPlatform().osName()) else "-"}",
                        )
                        _updateResponse.value = data
                        showedUpdateDialog = true
                    }

                    // A repository without a release answers 404 and a missing network throws: neither
                    // is worth more than a log line, there is nothing for the user to do about it.
                    else -> {
                        Logger.i("Update", "check failed: ${response.message}")
                    }
                }
                _isCheckingUpdate.value = false
            }
        }.invokeOnCompletion {
            // Also for a check that throws: the flag must never stay raised.
            _isCheckingUpdate.value = false
        }
    }

    fun stopPlayer() {
        _nowPlayingScreenData.value = NowPlayingScreenData.initial()
        _nowPlayingState.value = null
        mediaPlayerHandler.resetSongAndQueue()
        onUIEvent(UIEvent.Stop)
    }

    fun createLocalPlaylist(title: String) {
        viewModelScope.launch {
            localPlaylistRepository
                .insertLocalPlaylist(
                    LocalPlaylistEntity(title = title),
                    getString(Res.string.added_local_playlist),
                ).lastOrNull()
            makeToast(getString(Res.string.added_local_playlist))
        }
    }

    /** Creates a private playlist in the YouTube Music account; it shows in the library lists at once. */
    fun createYouTubePlaylist(title: String) {
        viewModelScope.launch {
            playlistRepository.createYouTubePlaylist(title).collect { res ->
                when (res) {
                    is Resource.Success -> makeToast(getString(Res.string.youtube_playlist_created))
                    is Resource.Error -> makeToast(getString(Res.string.youtube_playlist_create_failed))
                }
            }
        }
    }

    private fun loadPlaylistOrAlbum(index: Int? = null) {
        mediaPlayerHandler.loadPlaylistOrAlbum(index)
    }

    private fun updateLyrics(
        videoId: String,
        duration: Int, // 0 if translated lyrics
        inputLyrics: Lyrics?,
        isTranslatedLyrics: Boolean,
        lyricsProvider: LyricsProvider = LyricsProvider.SIMPMUSIC,
    ) {
        // An answer for a track that is no longer playing: it must not land on the next one (nor
        // judge, as a translation, the next one's lyrics against itself).
        if (_nowPlayingState.value?.songEntity?.videoId != videoId) return
        if (inputLyrics == null) {
            _nowPlayingScreenData.update {
                it.copy(
                    lyricsData = null,
                )
            }
            return
        }

        val lyrics =
            inputLyrics.copy(
                lines =
                    inputLyrics.lines?.map { line ->
                        line.copy(
                            words = decodeHtmlEntities(line.words),
                        )
                    },
            )

        if (isTranslatedLyrics) {
            val originalLyrics = _nowPlayingScreenData.value.lyricsData?.lyrics
            val originalLines = originalLyrics?.lines
            val lyricsLines = lyrics.lines
            if (originalLyrics != null && originalLines != null && lyricsLines != null) {
                var timeSyncErrorCount = 0
                val totalLines = originalLines.size

                if (originalLines.size == lyricsLines.size) {
                    // Line counts match: compare by index (1:1 mapping)
                    originalLines.forEachIndexed { index, originalLine ->
                        val originalTime = originalLine.startTimeMs.toLongOrNull() ?: 0L
                        val translatedLine = lyricsLines[index]
                        val translatedTime = translatedLine.startTimeMs.toLongOrNull() ?: 0L
                        val timeDiff = abs(originalTime - translatedTime)

                        if (timeDiff > 1000L) {
                            timeSyncErrorCount++
                        }
                    }
                } else {
                    // Line count mismatch: use timestamp-based matching with used-line tracking
                    val usedIndices = mutableSetOf<Int>()
                    originalLines.forEach { originalLine ->
                        val originalTime = originalLine.startTimeMs.toLongOrNull() ?: 0L
                        var bestIndex = -1
                        var bestDiff = Long.MAX_VALUE
                        lyricsLines.forEachIndexed { index, line ->
                            if (index !in usedIndices) {
                                val diff = abs((line.startTimeMs.toLongOrNull() ?: 0L) - originalTime)
                                if (diff < bestDiff) {
                                    bestDiff = diff
                                    bestIndex = index
                                }
                            }
                        }
                        if (bestIndex >= 0) {
                            usedIndices.add(bestIndex)
                            if (bestDiff > 1000L) {
                                timeSyncErrorCount++
                            }
                        } else {
                            timeSyncErrorCount++
                        }
                    }
                }

                // Use percentage-based threshold: reject if >25% of lines are out of sync
                val syncErrorRatio = if (totalLines > 0) timeSyncErrorCount.toFloat() / totalLines else 0f
                if (syncErrorRatio > 0.25f || (totalLines > 0 && timeSyncErrorCount > totalLines / 2)) {
                    Logger.w(
                        tag,
                        "Translated lyrics out of sync: $timeSyncErrorCount/$totalLines lines with time diff > 1s (${(syncErrorRatio * 100).toInt()}%)",
                    )

                    _nowPlayingScreenData.update {
                        it.copy(
                            lyricsData =
                                it.lyricsData?.copy(
                                    translatedLyrics = null,
                                ),
                        )
                    }

                    viewModelScope.launch {
                        lyricsCanvasRepository.removeTranslatedLyrics(
                            videoId,
                            dataStoreManager.translationLanguage.first(),
                        )
                        log("Removed out-of-sync translated lyrics for $videoId")
                    }
                    return
                }
            }
        }

        if (_nowPlayingState.value?.songEntity?.videoId == videoId) {
            when (isTranslatedLyrics) {
                true -> {
                    _nowPlayingScreenData.update {
                        it.copy(
                            lyricsData =
                                it.lyricsData?.copy(
                                    translatedLyrics = lyrics to lyricsProvider,
                                ),
                        )
                    }
                }

                false -> {
                    val shown = _nowPlayingScreenData.value.lyricsData
                    val fromNetwork = lyricsProvider != LyricsProvider.OFFLINE
                    val replacesSaved = fromNetwork && shown?.lyricsProvider == LyricsProvider.OFFLINE
                    if (shown != null && shown.lyrics.lines == lyrics.lines && shown.lyrics.syncType == lyrics.syncType) {
                        // The same text again: a provider confirming the saved copy, or a second
                        // fetch of the same song. The lines and their translation stay as they are,
                        // so nothing redraws; only the source in the caption moves on.
                        if (shown.lyricsProvider != lyricsProvider) {
                            _nowPlayingScreenData.update {
                                it.copy(lyricsData = it.lyricsData?.copy(lyricsProvider = lyricsProvider))
                            }
                        }
                        if (replacesSaved) {
                            Logger.i("Offline", "lyrics $videoId network same $lyricsProvider ${lyricsElapsedMs(videoId)} ms")
                        }
                        return
                    }
                    _nowPlayingScreenData.update {
                        it.copy(
                            lyricsData =
                                NowPlayingScreenData.LyricsData(
                                    lyrics = lyrics,
                                    lyricsProvider = lyricsProvider,
                                ),
                        )
                    }
                    when {
                        replacesSaved -> Logger.i("Offline", "lyrics $videoId network replaced $lyricsProvider ${lyricsElapsedMs(videoId)} ms")
                        fromNetwork -> Logger.i("Offline", "lyrics $videoId network shown $lyricsProvider ${lyricsElapsedMs(videoId)} ms")
                    }
                    // Saved lyrics come from the database, and Add lyrics writes its own copy.
                    if (!fromNetwork) return
                    viewModelScope.launch {
                        lyricsCanvasRepository.insertLyrics(
                            LyricsEntity(
                                videoId = videoId,
                                error = false,
                                lines = lyrics.lines,
                                syncType = lyrics.syncType,
                            ),
                        )
                    }
                }
            }
        }
    }

    // The walk through the lyrics source queue for the track playing, and the track it was started for.
    // One per track: the timeline job fires again whenever the player reports a different length, and the
    // chain used to start afresh each time (and without a network answer four to six `not found` lines
    // per track). Only an [explicit] request, a saved source queue, starts a second one.
    private var lyricsChainJob: Job? = null
    private var lyricsChainFor: String? = null

    private fun getLyricsFromFormat(
        isVideo: Boolean,
        song: SongEntity,
        duration: Int,
        explicit: Boolean = false,
    ) {
        // A live broadcast has nothing to sync lyrics to — the "duration" mpv reports for it is only
        // its seek window — so no provider is asked. Every caller comes through here.
        if (LiveStreamRegistry.isLive(song.videoId)) return
        // The timeline job still pairs the new length with the previous track for a moment: whatever
        // came back would be dropped by updateLyrics, so it is not asked for.
        if (_nowPlayingState.value?.songEntity?.videoId != song.videoId) return
        if (!explicit && lyricsChainFor == song.videoId) return
        lyricsChainFor = song.videoId
        // A fetch in flight is not "no lyrics": saving the source queue re-fetches the same song, and
        // Add lyrics offered meanwhile could be saved over the lyrics the queue finds.
        _nowPlayingScreenData.update { it.copy(lyricsNotFound = false) }
        if (lyricsRequestedAt?.first != song.videoId || _nowPlayingScreenData.value.lyricsData != null) {
            lyricsRequestedAt = song.videoId to TimeSource.Monotonic.markNow()
        }
        // A saved source queue keeps what is on screen; a new track starts from its saved copy.
        if (_nowPlayingScreenData.value.lyricsData == null) showSavedLyrics(song.videoId, duration)
        lyricsChainJob?.cancel()
        lyricsChainJob = viewModelScope.launch {
            val videoId = song.videoId
            Logger.d(tag, "Get Lyrics From Format for $videoId")
            val artistName = song.artistName
            val artist =
                if (artistName?.firstOrNull() != null &&
                    artistName
                        .firstOrNull()
                        ?.contains("Various Artists") == false
                ) {
                    artistName.firstOrNull()
                } else {
                    mediaPlayerHandler.nowPlaying
                        .first()
                        ?.metadata
                        ?.artist
                        ?: ""
                }
            val queue = LyricsSourceQueue.flow(dataStoreManager).first()
            val spotifyLoggedIn = dataStoreManager.spdc.first().isNotEmpty()
            askLyricsQueue(song, artist ?: "", duration, queue, spotifyLoggedIn)
        }
    }

    /** What one source answered: its text, and the translation that came with it (YouTube's). */
    private class LyricsAnswer(
        val provider: LyricsProvider,
        val lyrics: Lyrics,
        val translation: Lyrics? = null,
    )

    /**
     * Asks the sources switched on in [queue], in its order, until one has synced lyrics. A source with
     * nothing (no match, an error, or a success with no line of text — Spotify answers that way) hands
     * over to the next. Plain lyrics are kept and shown at once, unless synced ones are on screen
     * already (the saved copy); the queue goes on for a synced text, and the plain one stays if none
     * turns up. Only when no source has any text is the song without lyrics.
     */
    private suspend fun askLyricsQueue(
        song: SongEntity,
        artist: String,
        duration: Int,
        queue: List<LyricsSourceEntry>,
        spotifyLoggedIn: Boolean,
    ) {
        val videoId = song.videoId
        Logger.d(LYRICS_QUEUE_TAG, "$videoId queue ${queue.filter { it.enabled }.joinToString(" > ") { it.source.name }}")
        var plain: LyricsAnswer? = null
        var plainShown = false
        for (entry in queue) {
            if (!entry.enabled) continue
            if (entry.source == LyricsSource.SPOTIFY && !spotifyLoggedIn) {
                Logger.d(LYRICS_QUEUE_TAG, "$videoId SPOTIFY skipped: not logged in")
                continue
            }
            // The chain is cancelled on a track change; this covers the moment before that lands.
            if (_nowPlayingState.value?.songEntity?.videoId != videoId) return
            val answer = askLyricsSource(entry.source, song, artist, duration)
            Logger.d(
                LYRICS_QUEUE_TAG,
                "$videoId ${entry.source} -> " +
                    when {
                        answer == null -> "nothing"
                        answer.lyrics.isSynced() -> "synced ${answer.lyrics.lines?.size} lines"
                        else -> "plain ${answer.lyrics.lines?.size} lines"
                    },
            )
            if (answer == null) continue
            if (answer.lyrics.isSynced()) {
                showLyricsAnswer(videoId, duration, answer)
                return
            }
            if (plain == null) {
                plain = answer
                if (_nowPlayingScreenData.value.lyricsData
                        ?.lyrics
                        ?.isSynced() != true
                ) {
                    showLyricsAnswer(videoId, duration, answer)
                    plainShown = true
                }
            }
        }
        when {
            plain == null -> onLyricsProvidersFailed(videoId)
            !plainShown -> showLyricsAnswer(videoId, duration, plain)
        }
    }

    /** One source's lyrics for [song], or null when it has none to give. */
    private suspend fun askLyricsSource(
        source: LyricsSource,
        song: SongEntity,
        artist: String,
        duration: Int,
    ): LyricsAnswer? {
        val answer =
            try {
                when (source) {
                    LyricsSource.SIMPMUSIC ->
                        lyricsCanvasRepository
                            .getSimpMusicLyrics(song.videoId)
                            .firstOrNull()
                            ?.successData()
                            ?.let { LyricsAnswer(LyricsProvider.SIMPMUSIC, it) }

                    LyricsSource.LRCLIB ->
                        lyricsCanvasRepository
                            .getLrclibLyricsData(artist, song.title, duration)
                            .firstOrNull()
                            ?.successData()
                            ?.let { LyricsAnswer(LyricsProvider.LRCLIB, it) }

                    LyricsSource.BETTER_LYRICS ->
                        lyricsCanvasRepository
                            .getBetterLyrics(artist, song.title, duration)
                            .firstOrNull()
                            ?.successData()
                            ?.let { LyricsAnswer(LyricsProvider.BETTER_LYRICS, it) }

                    // Spotify's flow can end without a word (no tokens), which firstOrNull reads as nothing.
                    LyricsSource.SPOTIFY ->
                        lyricsCanvasRepository
                            .getSpotifyLyrics(dataStoreManager, "${song.title} $artist", duration)
                            .firstOrNull()
                            ?.successData()
                            ?.let { LyricsAnswer(LyricsProvider.SPOTIFY, it) }

                    LyricsSource.YOUTUBE ->
                        lyricsCanvasRepository
                            .getYouTubeCaption(dataStoreManager.youtubeSubtitleLanguage.first(), song.videoId)
                            .firstOrNull()
                            ?.successData()
                            ?.let { (lyrics, translation) -> LyricsAnswer(LyricsProvider.YOUTUBE, lyrics, translation) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(LYRICS_QUEUE_TAG, "${song.videoId} $source failed: ${e.message}")
                null
            }
        return answer?.takeIf { it.lyrics.hasText() }
    }

    private fun <T> Resource<T>.successData(): T? = (this as? Resource.Success)?.data

    /**
     * Shows [answer] (which also saves it for offline use) and its translation: YouTube's came with
     * it, SimpMusic's is asked for now. The other sources have none.
     */
    private suspend fun showLyricsAnswer(
        videoId: String,
        duration: Int,
        answer: LyricsAnswer,
    ) {
        updateLyrics(videoId, duration, answer.lyrics, false, answer.provider)
        when (answer.provider) {
            LyricsProvider.YOUTUBE -> answer.translation?.let { updateLyrics(videoId, duration, it, true, LyricsProvider.YOUTUBE) }
            LyricsProvider.SIMPMUSIC -> getSimpMusicTranslatedLyrics(videoId)
            else -> Unit
        }
    }

    private suspend fun getSimpMusicTranslatedLyrics(videoId: String) {
        val translationLanguage =
            dataStoreManager.translationLanguage.first()
        lyricsCanvasRepository.getSimpMusicTranslatedLyrics(videoId, translationLanguage).collectLatest { response ->
            val data = response.data
            when (response) {
                is Resource.Success if (data != null) -> {
                    // Word-by-word (RICH_SYNCED) translations are skipped: only line-level or plain
                    // translated lyrics are shown.
                    if (data.syncType == "RICH_SYNCED") {
                        Logger.d(tag, "SimpMusic translated lyrics are RICH_SYNCED, not shown")
                    } else {
                        Logger.d(tag, "Get SimpMusic Translated Lyrics Success")
                        updateLyrics(
                            videoId,
                            0,
                            data,
                            true,
                            LyricsProvider.SIMPMUSIC,
                        )
                    }
                }

                else -> {
                    Logger.d(tag, "Get SimpMusic Translated Lyrics Error: ${response.message}")
                }
            }
        }
    }

    /**
     * Looks up the album of the track playing, for when neither its row nor the queue has one. A radio
     * hands a music video no album at all; the song it is the video of has one, and a search finds it.
     */
    fun findAlbumOfNowPlaying(onFound: (String) -> Unit) {
        val song = nowPlayingState.value?.songEntity ?: return
        viewModelScope.launch {
            val query = (listOf(song.title) + song.artistName.orEmpty()).joinToString(" ")
            val albumId =
                searchRepository
                    .getSearchDataSong(query)
                    .firstOrNull()
                    ?.data
                    ?.albumIdOf(song)
            // The search takes a few seconds: a track skipped meanwhile must not open the previous one's album.
            if (nowPlayingState.value?.songEntity?.videoId != song.videoId) return@launch
            if (albumId != null) onFound(albumId) else makeToast(getString(Res.string.album_not_found))
        }
    }

    /** The lyrics source queue was saved: the track playing asks it again, keeping what is on screen. */
    private fun reaskLyricsQueue() {
        viewModelScope.launch {
            val songEntity = nowPlayingState.value?.songEntity ?: return@launch
            val isVideo = nowPlayingState.value?.mediaItem?.isVideo() ?: false
            getLyricsFromFormat(isVideo, songEntity, timeline.value.total.toInt() / 1000, explicit = true)
        }
    }

    private var _recreateActivity: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val recreateActivity: StateFlow<Boolean> = _recreateActivity

    fun activityRecreate() {
        _recreateActivity.value = true
    }

    fun activityRecreateDone() {
        _recreateActivity.value = false
    }

    fun addListToQueue(listTrack: ArrayList<Track>) {
        viewModelScope.launch {
            // A radio, a mix or an Endless queue has no end to append to, so a single song (the swipe
            // on a song row) goes right after the current one instead.
            val endlessQueue = dataStoreManager.endlessQueue.first() == TRUE
            if (listTrack.size == 1 && mediaPlayerHandler.queueData.value.addToQueueBlock(endlessQueue) != null) {
                mediaPlayerHandler.playNext(listTrack.first())
                makeToast(getString(Res.string.play_next))
            } else {
                mediaPlayerHandler.loadMoreCatalog(listTrack)
                makeToast(getString(Res.string.added_to_queue))
            }
        }
    }

    /**
     * Dislikes [videoId], the track the sheet was opened for. While the request is out the action
     * reads "Disliking…" and cannot be pressed again; a failure puts it back as it was.
     */
    fun addToYouTubeDisliked(videoId: String) {
        if (videoId.isEmpty() || _dislikingVideoId.value == videoId) return
        _dislikingVideoId.value = videoId
        viewModelScope.launch {
            try {
                songRepository.addToYouTubeDisliked(videoId).collect { response ->
                    if (response == 200) {
                        // The reply can arrive after the player moved on; it belongs to the
                        // track it was sent for, so it only shows if that track is still playing.
                        // A music video plays under the "Video"-prefixed id, the menu sends the bare one.
                        if (mediaPlayerHandler.nowPlaying.value?.mediaId?.removePrefix(MERGING_DATA_TYPE.VIDEO) == videoId) {
                            _likeStatus.value = YouTubeLikeStatus.DISLIKE
                        }
                        makeToast(getString(Res.string.disliked))
                    } else {
                        makeToast(getString(Res.string.error))
                    }
                }
            } finally {
                _dislikingVideoId.compareAndSet(videoId, null)
            }
        }
    }

    fun getLocalTrackingEnabled() = dataStoreManager.localTrackingEnabled

    // Drives the Mix for you tab: YouTube hands an anonymous session no mixes at all.
    fun getYouTubeLoggedIn() = dataStoreManager.loggedIn

    fun getThemeMode() = dataStoreManager.themeMode

    fun getThemeColorSource() = dataStoreManager.themeColorSource

    fun getCustomThemeColor() = dataStoreManager.customThemeColor

    fun getLyricsStyle() = dataStoreManager.lyricsStyle

    // One flow for the view model's life: callers collect it from composition.
    private val currentLyricsOffset by lazy { songRepository.currentLyricsOffsetMs(mediaPlayerHandler) }

    fun getLyricsOffsetMs() = currentLyricsOffset

    /** Sets the offset of the track playing; every other track keeps its own. */
    fun setLyricsOffsetMs(offsetMs: Int) {
        val videoId =
            mediaPlayerHandler.nowPlayingState.value.mediaItem.mediaId
                .removePrefix(MERGING_DATA_TYPE.VIDEO)
        if (videoId.isEmpty()) return
        viewModelScope.launch {
            songRepository.setLyricsOffsetMs(videoId, offsetMs)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            dataStoreManager.setThemeMode(mode)
        }
    }

    fun setThemeColorSource(source: String) {
        viewModelScope.launch {
            dataStoreManager.setThemeColorSource(source)
        }
    }

    fun setCustomThemeColor(argbHex: String) {
        viewModelScope.launch {
            dataStoreManager.setCustomThemeColor(argbHex)
        }
    }

    fun setLyricsStyle(style: String) {
        viewModelScope.launch {
            dataStoreManager.setLyricsStyle(style)
        }
    }

    private val _reloadDestination: MutableStateFlow<KClass<*>?> = MutableStateFlow(null)
    val reloadDestination: StateFlow<KClass<*>?> = _reloadDestination.asStateFlow()

    fun reloadDestination(destination: KClass<*>) {
        _reloadDestination.value = destination
    }

    fun reloadDestinationDone() {
        _reloadDestination.value = null
    }

    suspend fun shouldCheckForUpdate(): Boolean = dataStoreManager.autoCheckForUpdates.first() == TRUE

    /** The launch check: reads the setting off the main thread, then asks GitHub if it is on. */
    fun checkForUpdateIfEnabled() {
        viewModelScope.launch {
            if (shouldCheckForUpdate()) checkForUpdate()
        }
    }

    private var _downloadFileProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.INIT)
    val downloadFileProgress: StateFlow<DownloadProgress> get() = _downloadFileProgress

    fun downloadFile(bitmap: ImageBitmap) {
        val fileName =
            "${nowPlayingScreenData.value.nowPlayingTitle} - ${nowPlayingScreenData.value.artistName}"
                .replace(Regex("""[|\\?*<":>]"""), "")
                .replace(" ", "_")
        val path =
            "${getDownloadFolderPath()}/$fileName"
        viewModelScope.launch {
            nowPlayingState.value?.track?.let { track ->
                val bytesArray = bitmap.toByteArray()
                try {
                    val fileOutputStream = FileOutputStream("$path.jpg")
                    fileOutputStream.write(bytesArray)
                    fileOutputStream.close()
                    Logger.d(tag, "Thumbnail saved to $path.jpg")
                } catch (e: Exception) {
                    throw RuntimeException(e)
                }
                songRepository
                    .downloadToFile(
                        track = track,
                        videoId = track.videoId,
                        path = path,
                        isVideo = nowPlayingScreenData.value.isVideo && getVideo.value,
                    ).collectLatest {
                        _downloadFileProgress.value = it
                    }
            }
        }
    }

    fun downloadFileDone() {
        _downloadFileProgress.value = DownloadProgress.INIT
    }

    fun setBitmap(bitmap: ImageBitmap?) {
        _nowPlayingScreenData.update {
            it.copy(bitmap = bitmap)
        }
    }

    /**
     * The Now Playing colour scheme's seed for a track, as (videoId, colour). Worked out from the first
     * artwork that loads for it — on Android usually the mini player's, long before the player opens —
     * and kept for the track, so opening the player starts in the cover's colours instead of fading in
     * from the app colour, and the bigger artwork that loads later does not shift them.
     */
    private val _artworkSeed = MutableStateFlow<Pair<String, Color>?>(null)
    val artworkSeed: StateFlow<Pair<String, Color>?> = _artworkSeed.asStateFlow()
    private var artworkSeedJob: Job? = null
    private var artworkSeedFor: String? = null

    fun offerArtworkForSeed(
        videoId: String?,
        bitmap: ImageBitmap,
    ) {
        if (videoId == null || videoId == artworkSeedFor) return
        artworkSeedFor = videoId
        artworkSeedJob?.cancel()
        artworkSeedJob =
            viewModelScope.launch(Dispatchers.Default) {
                val seed = bitmap.artworkSeedColor()
                if (seed == null) {
                    // A colourless cover: let the player's own artwork try.
                    artworkSeedFor = null
                    return@launch
                }
                _artworkSeed.value = videoId to seed
            }
    }

    fun shouldStopMusicService(): Boolean = runBlocking { dataStoreManager.killServiceOnExit.first() == TRUE }

    fun isUserLoggedIn(): Boolean = runBlocking { dataStoreManager.cookie.first().isNotEmpty() }

    // Flow-based variant of [isUserLoggedIn] so composables can collect login state once
    // instead of calling runBlocking inside composition (used by NowPlayingScreenContent).
    fun isUserLoggedInFlow(): Flow<Boolean> = dataStoreManager.cookie.map { it.isNotEmpty() }

    // Last in the class on purpose. Properties start in the order they are written, and this block runs
    // on Main.immediate: when a track is already playing (the activity was recreated, a rotation) the
    // first collector fires inside the constructor and used to reach fields declared further down,
    // still null (a NullPointerException, and the app crashed on every rotation).
    init {
        viewModelScope.launch {
            log("SharedViewModel init")
            if (dataStoreManager.appVersion.first() != VersionManager.getVersionName()) {
                dataStoreManager.setAppVersion(
                    VersionManager.getVersionName(),
                )
            }
            val timeLineJob =
                launch {
                    nowPlayingState
                        .filterNotNull()
                        .flatMapLatest { nowPlayingState ->
                            timeline.map { timeLine ->
                                Pair(timeLine, nowPlayingState)
                            }
                        }.distinctUntilChanged { old, new ->
                            // A live stream's "total" is the seek window mpv reports, and it grows with
                            // every new segment — not a new length, so it must not re-run what a new
                            // length triggers (this fired every few seconds for as long as one played).
                            val sameLiveStream = old.first.isLive && new.first.isLive
                            (old.first.total.toString() + old.second.songEntity?.videoId).hashCode() ==
                                (new.first.total.toString() + new.second.songEntity?.videoId).hashCode() ||
                                (sameLiveStream && old.second.songEntity?.videoId == new.second.songEntity?.videoId)
                        }.collectLatest {
                            val nowPlaying = it.second
                            val timeline = it.first
                            // Lyrics key off the track's length, which a live stream has none of.
                            if (timeline.isLive) return@collectLatest
                            log("Timeline job ${(it.first.total.toString() + it.second.songEntity?.videoId).hashCode()}")
                            if (timeline.total > 0 && nowPlaying.songEntity != null) {
                                nowPlaying.songEntity?.let { song ->
                                    if (nowPlayingScreenData.value.lyricsData == null) {
                                        getLyricsFromFormat(nowPlaying.mediaItem.isVideo(), song, (timeline.total / 1000).toInt())
                                    }
                                }
                            }
                        }
                }
            val checkGetVideoJob =
                launch {
                    dataStoreManager.watchVideoInsteadOfPlayingAudio.collectLatest {
                        _getVideo.value = it == TRUE
                    }
                }
            val lyricsProviderJob =
                launch {
                    LyricsSourceQueue.flow(dataStoreManager).drop(1).collectLatest {
                        // The first value is the queue as it is, not a change: the timeline job asks.
                        reaskLyricsQueue()
                    }
                }
            // Not joined below: these run for as long as the app does.
            launch {
                controllerState.map { it.isLiked }.distinctUntilChanged().collect { liked ->
                    // A like replaces a dislike on YouTube, and the ⋯ menu's Dislike reads this.
                    if (liked && _likeStatus.value == YouTubeLikeStatus.DISLIKE) {
                        _likeStatus.value = YouTubeLikeStatus.LIKE
                    }
                }
            }
            launch {
                songRepository.likeFailures.collect { failure ->
                    makeToast(
                        getString(
                            when (failure) {
                                LikeFailure.NOT_SIGNED_IN -> Res.string.log_in_to_like
                                LikeFailure.REQUEST_FAILED -> Res.string.error
                            },
                        ),
                    )
                }
            }
            launch {
                // Keyed on the session, not only on being signed in: switching to another account
                // (or a channel of the same login) leaves loggedIn true, and the likes and follows of
                // the previous account stayed until the next launch.
                combine(
                    dataStoreManager.loggedIn,
                    dataStoreManager.cookie,
                    dataStoreManager.pageId,
                    dataStoreManager.authUser,
                ) { loggedIn, cookie, pageId, authUser ->
                    if (loggedIn == TRUE) Triple(cookie, pageId, authUser) else null
                }.distinctUntilChanged().collectLatest { session ->
                    if (session != null) {
                        // CommonRepositoryImpl puts a changed session on the client from the same store.
                        delay(1000)
                        songRepository.syncLikedSongs()
                        artistRepository.syncFollowedArtists()
                    }
                }
            }
            timeLineJob.join()
            checkGetVideoJob.join()
            lyricsProviderJob.join()
//            controllerStateJob.join()
        }

        runBlocking {
            dataStoreManager.getString("miniplayer_guide").first().let {
                isFirstMiniplayer = it != STATUS_DONE
            }
            dataStoreManager.getString("suggest_guide").first().let {
                isFirstSuggestions = it != STATUS_DONE
            }
            dataStoreManager.getString("liked_guide").first().let {
                isFirstLiked = it != STATUS_DONE
            }
        }
        viewModelScope.launch {
            mediaPlayerHandler.nowPlayingState
                .distinctUntilChangedBy {
                    it.songEntity?.videoId
                }.collectLatest { state ->
                    // The previous track's lyrics go before the new track is published: publishing it
                    // (and the timeline below) runs the timeline job at once, which asks for lyrics
                    // only while none are shown. With the old ones still there it skipped the new
                    // track until the player reported a length of its own (0.2-0.5 s later).
                    lyricsRequestedAt = null
                    lyricsChainFor = null
                    lyricsChainJob?.cancel()
                    savedLyricsJob?.cancel()
                    _nowPlayingScreenData.update { it.copy(lyricsData = null, lyricsNotFound = false) }
                    _nowPlayingState.value = state

                    // Seed the timeline from METADATA as soon as the track is known, instead of
                    // waiting for the player. SimpleMediaState.Ready carries a duration and only
                    // fires once the container is parsed; after a queue restore nothing plays, the
                    // position poll never starts (it runs only while isPlaying), and so nothing
                    // ever reports a length — which is why a restored queue showed no times at all.
                    // SongEntity has known the length since the song was first seen.
                    //
                    // Written on EVERY track change, not just when missing: leaving the old value
                    // in place would show the previous track's length over the new one.
                    // Order matters: metadata first because it is available immediately, then the
                    // player's own duration, and only -1 ("not known yet") when neither has one.
                    //
                    // Writing -1 whenever metadata is missing — which this did at first — makes
                    // every radio track and every first-time track flash NA:NA on the clock until
                    // the container is parsed, because those rows have no durationSeconds stored.
                    // Asking the player before giving up covers exactly that case: on a normal
                    // track change it usually already knows.
                    val metadataDurationMs = (state.songEntity?.durationSeconds ?: 0).toLong() * 1000L
                    val seededTotal =
                        metadataDurationMs.takeIf { it > 0L }
                            ?: mediaPlayerHandler.getPlayerDuration().takeIf { it > 0L }
                            ?: -1L
                    // The position too: paused, the poll is stopped and nothing reports that a new
                    // track starts at its beginning, so the seek bar, clock and lyrics stayed where
                    // the previous one left them until play. Read from the player, not assumed to
                    // be 0: a restored queue starts halfway through.
                    _timeline.update {
                        it.copy(total = seededTotal, current = mediaPlayerHandler.getProgress().coerceAtLeast(0L))
                    }
                    state.songEntity?.let { track ->
                        _nowPlayingScreenData.value =
                            NowPlayingScreenData(
                                nowPlayingTitle = track.title,
                                artistName =
                                    track
                                        .artistName
                                        ?.joinToString(", ") ?: "",
                                isVideo = false,
                                thumbnailURL = null,
                                lyricsData = null,
                                songInfoData = null,
                                playlistName =
                                    mediaPlayerHandler.queueData.value
                                        ?.data
                                        ?.playlistName ?: "",
                                isRadioQueue =
                                    mediaPlayerHandler.queueData.value
                                        ?.data
                                        ?.playlistId
                                        ?.isEndlessQueueId() == true,
                            )
                    }
                    state.mediaItem.let { now ->
                        getLikeStatus(now.mediaId)
                        getSongInfo(now.mediaId)
                        getFormat(now.mediaId)
                        _nowPlayingScreenData.update {
                            it.copy(
                                thumbnailURL = now.metadata.artworkUri,
                                isVideo = now.isVideo(),
                            )
                        }
                    }
                    state.songEntity?.let { song ->
                        _liked.value = song.liked == true
                        _nowPlayingScreenData.update {
                            it.copy(
                                isExplicit = song.isExplicit,
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            val job1 =
                launch {
                    mediaPlayerHandler.simpleMediaState.collect { mediaState ->
                        when (mediaState) {
                            is SimpleMediaState.Buffering -> {
                                _timeline.update {
                                    it.copy(
                                        loading = true,
                                    )
                                }
                            }

                            SimpleMediaState.Initial -> {
                                _timeline.update { it.copy(loading = true) }
                            }

                            SimpleMediaState.Ended -> {
                                // Park at the end of the track rather than at -1. The only formatter
                                // for these fields renders any negative as "NA:NA", and nothing here
                                // is guaranteed to follow: at the end of the queue the player simply
                                // stays ended, so a -1 written here stays on screen. Worse, the
                                // Progress branch below ignores negative values and the Loading
                                // branch restores `total` without touching `current`, which is how
                                // the player ends up showing a correct duration next to "NA:NA".
                                _timeline.update {
                                    it.copy(
                                        current = it.total.coerceAtLeast(0L),
                                        bufferedPercent = 0,
                                        loading = false,
                                    )
                                }
                            }

                            is SimpleMediaState.Progress -> {
                                if (mediaState.progress >= 0L && mediaState.progress != _timeline.value.current) {
                                    // A live stream never reports a length (ExoPlayer: C.TIME_UNSET), so
                                    // a missing one means "loading" for everything except a live stream.
                                    if (_timeline.value.total > 0L || _timeline.value.isLive) {
                                        _timeline.update {
                                            it.copy(
                                                total = mediaPlayerHandler.getPlayerDuration().takeIf { d -> d > 0L } ?: it.total,
                                                current = mediaState.progress,
                                                loading = false,
                                            )
                                        }
                                    } else {
                                        _timeline.update {
                                            it.copy(
                                                current = mediaState.progress,
                                                loading = true,
                                                total = mediaPlayerHandler.getPlayerDuration().takeIf { d -> d > 0L } ?: it.total,
                                            )
                                        }
                                    }
                                }
                                // When progress hasn't changed (same value polled again) or is negative,
                                // don't modify loading state. The loading flag is already managed by
                                // Buffering/Ready/Loading state events. Setting loading=true here would
                                // cause rapid flickering whenever the same value arrives twice,
                                // which it can: the handler's ticker and the adapter's position
                                // poll both run at 50ms and are not in step, so a poll is sometimes
                                // read twice. A repeat is not evidence of a stall, and the loading
                                // flag belongs to the Buffering/Ready events rather than to a
                                // guess made here.
                            }

                            is SimpleMediaState.Loading -> {
                                _timeline.update {
                                    it.copy(
                                        bufferedPercent = mediaState.bufferedPercentage,
                                        total = mediaState.duration,
                                        loading = true,
                                    )
                                }
                            }

                            is SimpleMediaState.Ready -> {
                                _timeline.update {
                                    it.copy(
                                        current = mediaPlayerHandler.getProgress(),
                                        loading = false,
                                        // The player's own duration wins, but ONLY when it has one.
                                        // ExoPlayer answers C.TIME_UNSET (a large negative, not
                                        // null) until it has parsed the container, and Ready is
                                        // also published from onIsLoadingChanged — which fires
                                        // before STATE_READY. Writing that would throw away the
                                        // metadata duration seeded on the track change above.
                                        total = mediaState.duration.takeIf { d -> d > 0L } ?: it.total,
                                    )
                                }
                            }
                        }
                    }
                }
            val controllerJob =
                launch {
                    Logger.d(tag, "ControllerJob is running")
                    mediaPlayerHandler.controlState.collectLatest {
                        Logger.d(tag, "ControlState is $it")
                        _controllerState.value = it
                        // Propagate crossfade state to timeline so UI can react
                        _timeline.update { timeline ->
                            timeline.copy(isCrossfading = it.isCrossfading)
                        }
                    }
                }
            val sleepTimerJob =
                launch {
                    mediaPlayerHandler.sleepTimerState.collectLatest {
                        _sleepTimerState.value = it
                    }
                }
            val playlistNameJob =
                launch {
                    mediaPlayerHandler.queueData.collectLatest { queue ->
                        _nowPlayingScreenData.update {
                            it.copy(
                                playlistName = queue?.data?.playlistName ?: "",
                                isRadioQueue = queue?.data?.playlistId?.isEndlessQueueId() == true,
                            )
                        }
                    }
                }
            val liveStreamJob =
                launch {
                    // A track is only known to be live once its stream has been resolved, so this
                    // follows the registry as well as the track itself.
                    combine(mediaPlayerHandler.nowPlayingState, LiveStreamRegistry.liveVideoIds) { state, liveVideoIds ->
                        state.mediaItem.mediaId in liveVideoIds
                    }.distinctUntilChanged()
                        .collect { isLive -> _timeline.update { it.copy(isLive = isLive) } }
                }
            job1.join()
            controllerJob.join()
            sleepTimerJob.join()
            playlistNameJob.join()
            liveStreamJob.join()
        }
        // Reset downloading songs & playlists to not downloaded. Not the songs on desktop: its download queue
        // resumes them at launch and owns their rows, and this reset raced it (a resumed song showed "not
        // downloaded" while it downloaded, or was reset before the queue read it and never resumed).
        if (getPlatform() != Platform.Desktop) checkAllDownloadingSongs()
        checkAllDownloadingPlaylists()
        checkAllDownloadingLocalPlaylists()
    }
}

sealed class UIEvent {
    data object PlayPause : UIEvent()

    data object Backward : UIEvent()

    data object Forward : UIEvent()

    data object Next : UIEvent()

    data object Previous : UIEvent()

    /**
     * Always advances to the previous track — bypasses the 3-second
     * "seek to start of current track" rule used by [Previous]. Used by the
     * NowPlaying artwork pager swipe.
     */
    data object SkipToPrevious : UIEvent()

    data object Stop : UIEvent()

    data object Shuffle : UIEvent()

    data object Repeat : UIEvent()

    data class UpdateProgress(
        val newProgress: Float,
    ) : UIEvent()

    data class UpdateVolume(
        val newVolume: Float,
    ) : UIEvent()

    data object ToggleLike : UIEvent()
}

enum class LyricsProvider {
    SIMPMUSIC,
    YOUTUBE,
    SPOTIFY,
    LRCLIB,
    BETTER_LYRICS,
    OFFLINE,
}

data class NowPlayingScreenData(
    val playlistName: String,
    /** The queue plays from a radio or mix id, so the top bar says Radio/Mix instead of Now Playing. */
    val isRadioQueue: Boolean = false,
    val nowPlayingTitle: String,
    val artistName: String,
    val isVideo: Boolean,
    val isExplicit: Boolean = false,
    val thumbnailURL: String?,
    val lyricsData: LyricsData? = null,
    /** Every provider and the offline cache came back empty for this song; offers Add lyrics. */
    val lyricsNotFound: Boolean = false,
    val songInfoData: SongInfoEntity? = null,
    val bitmap: ImageBitmap? = null,
) {
    data class LyricsData(
        val lyrics: Lyrics,
        val translatedLyrics: Pair<Lyrics, LyricsProvider>? = null,
        val lyricsProvider: LyricsProvider,
    )

    companion object {
        fun initial(): NowPlayingScreenData =
            NowPlayingScreenData(
                nowPlayingTitle = "",
                artistName = "",
                isVideo = false,
                thumbnailURL = null,
                lyricsData = null,
                songInfoData = null,
                playlistName = "",
            )
    }
}

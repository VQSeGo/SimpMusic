package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.eygraber.uri.toKmpUri
import com.maxrave.common.LIMIT_CACHE_SIZE
import com.maxrave.common.QUALITY
import com.maxrave.common.SUPPORTED_LANGUAGE
import com.maxrave.common.SUPPORTED_LOCATION
import com.maxrave.common.VIDEO_QUALITY
import com.maxrave.domain.extension.now
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.manager.DataStoreManager.Values.TRUE
import com.maxrave.domain.utils.LocalResource
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.expect.ui.LoginSyncDialog
import com.maxrave.simpmusic.expect.ui.VerticalScrollBar
import com.maxrave.simpmusic.expect.ui.filePickerResult
import com.maxrave.simpmusic.expect.ui.fileSaverResult
import com.maxrave.simpmusic.expect.ui.isWallpaperDynamicColorSupported
import com.maxrave.simpmusic.expect.ui.openEqResult
import com.maxrave.simpmusic.extension.barBlurStyle
import com.maxrave.simpmusic.extension.bytesToMB
import com.maxrave.simpmusic.extension.isTwoLetterCode
import com.maxrave.simpmusic.extension.isValidProxyHost
import com.maxrave.simpmusic.getPlatform
import com.maxrave.simpmusic.ui.component.ActionButton
import com.maxrave.simpmusic.ui.component.AmbientThemeGlow
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.LoadingDialog
import com.maxrave.simpmusic.ui.component.LocalMarqueeIterations
import com.maxrave.simpmusic.ui.component.LyricsSourcesDialog
import com.maxrave.simpmusic.ui.component.lyricsSourcesSummary
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.SettingGroup
import com.maxrave.simpmusic.ui.component.SettingItem
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.Error
import com.maxrave.simpmusic.ui.icon.PeopleAlt
import com.maxrave.simpmusic.ui.icon.PlaylistAdd
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.home.AppLogDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.CreditDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.DiscordLoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.LoginDestination
import com.maxrave.simpmusic.ui.navigation.destination.login.SpotifyLoginDestination
import com.maxrave.simpmusic.ui.navigation.navigateOnce
import com.maxrave.simpmusic.ui.theme.md_theme_dark_primary
import com.maxrave.simpmusic.ui.theme.parseThemeColorHex
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.utils.VersionManager
import com.maxrave.simpmusic.viewModel.SettingAlertState
import com.maxrave.simpmusic.viewModel.SettingBasicAlertState
import com.maxrave.simpmusic.viewModel.SettingsViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.ChipColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cd_back
import simpmusic.composeapp.generated.resources.download_notifications
import simpmusic.composeapp.generated.resources.download_notifications_description
import simpmusic.composeapp.generated.resources.about_us
import simpmusic.composeapp.generated.resources.add_an_account
import simpmusic.composeapp.generated.resources.app_log
import simpmusic.composeapp.generated.resources.app_log_description
import simpmusic.composeapp.generated.resources.debug_logging
import simpmusic.composeapp.generated.resources.debug_logging_description
import simpmusic.composeapp.generated.resources.app_name
import simpmusic.composeapp.generated.resources.developer_option
import simpmusic.composeapp.generated.resources.audio
import simpmusic.composeapp.generated.resources.audio_delay
import simpmusic.composeapp.generated.resources.audio_delay_description
import simpmusic.composeapp.generated.resources.audio_reverb
import simpmusic.composeapp.generated.resources.audio_reverb_description
import simpmusic.composeapp.generated.resources.audio_eight_d
import simpmusic.composeapp.generated.resources.audio_eight_d_description
import simpmusic.composeapp.generated.resources.eight_d_unavailable
import simpmusic.composeapp.generated.resources.auto_check_for_update
import simpmusic.composeapp.generated.resources.auto_check_for_update_description
import simpmusic.composeapp.generated.resources.auto_download_liked_songs
import simpmusic.composeapp.generated.resources.auto_download_liked_songs_description
import simpmusic.composeapp.generated.resources.backup
import simpmusic.composeapp.generated.resources.based_on_simpmusic
import simpmusic.composeapp.generated.resources.backup_downloaded
import simpmusic.composeapp.generated.resources.backup_downloaded_description
import simpmusic.composeapp.generated.resources.balance_media_loudness
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.change
import simpmusic.composeapp.generated.resources.change_language_warning
import simpmusic.composeapp.generated.resources.check_for_update
import simpmusic.composeapp.generated.resources.checking
import simpmusic.composeapp.generated.resources.clear
import simpmusic.composeapp.generated.resources.clear_downloaded_cache
import simpmusic.composeapp.generated.resources.clear_listening_history
import simpmusic.composeapp.generated.resources.clear_listening_history_confirm
import simpmusic.composeapp.generated.resources.clear_listening_history_description
import simpmusic.composeapp.generated.resources.clear_player_cache
import simpmusic.composeapp.generated.resources.clear_thumbnail_cache
import simpmusic.composeapp.generated.resources.content
import simpmusic.composeapp.generated.resources.content_country
import simpmusic.composeapp.generated.resources.crossfade
import simpmusic.composeapp.generated.resources.crossfade_description
import simpmusic.composeapp.generated.resources.crossfade_duration
import simpmusic.composeapp.generated.resources.crossfade_skip_album
import simpmusic.composeapp.generated.resources.crossfade_skip_album_description
import simpmusic.composeapp.generated.resources.custom_color
import simpmusic.composeapp.generated.resources.database
import simpmusic.composeapp.generated.resources.description_and_licenses
import simpmusic.composeapp.generated.resources.discord_integration
import simpmusic.composeapp.generated.resources.download_quality
import simpmusic.composeapp.generated.resources.downloaded_cache
import simpmusic.composeapp.generated.resources.enable_rich_presence
import simpmusic.composeapp.generated.resources.equalizer
import simpmusic.composeapp.generated.resources.equalizer_description
import simpmusic.composeapp.generated.resources.equalizer_type
import simpmusic.composeapp.generated.resources.equalizer_type_built_in
import simpmusic.composeapp.generated.resources.equalizer_type_system
import simpmusic.composeapp.generated.resources.free_space
import simpmusic.composeapp.generated.resources.guest
import simpmusic.composeapp.generated.resources.http
import simpmusic.composeapp.generated.resources.intro_login_to_discord
import simpmusic.composeapp.generated.resources.intro_login_to_spotify
import simpmusic.composeapp.generated.resources.invalid_host
import simpmusic.composeapp.generated.resources.invalid_language_code
import simpmusic.composeapp.generated.resources.invalid_port
import simpmusic.composeapp.generated.resources.keep_your_youtube_playlist_offline
import simpmusic.composeapp.generated.resources.keep_your_youtube_playlist_offline_description
import simpmusic.composeapp.generated.resources.kill_service_on_exit
import simpmusic.composeapp.generated.resources.kill_service_on_exit_description
import simpmusic.composeapp.generated.resources.language
import simpmusic.composeapp.generated.resources.last_checked_at
import simpmusic.composeapp.generated.resources.limit_player_cache
import simpmusic.composeapp.generated.resources.listening_history
import simpmusic.composeapp.generated.resources.local_tracking_description
import simpmusic.composeapp.generated.resources.local_tracking_title
import simpmusic.composeapp.generated.resources.log_in_to_discord
import simpmusic.composeapp.generated.resources.log_in_to_spotify
import simpmusic.composeapp.generated.resources.log_out
import simpmusic.composeapp.generated.resources.log_out_from_discord
import simpmusic.composeapp.generated.resources.log_out_from_spotify
import simpmusic.composeapp.generated.resources.log_out_warning
import simpmusic.composeapp.generated.resources.logged_in
import simpmusic.composeapp.generated.resources.login_sync_android_description
import simpmusic.composeapp.generated.resources.login_sync_android_title
import simpmusic.composeapp.generated.resources.login_sync_desktop_description
import simpmusic.composeapp.generated.resources.login_sync_desktop_title
import simpmusic.composeapp.generated.resources.login_sync_section
import simpmusic.composeapp.generated.resources.lyrics
import simpmusic.composeapp.generated.resources.lyrics_over_video
import simpmusic.composeapp.generated.resources.lyrics_over_video_description
import simpmusic.composeapp.generated.resources.lyrics_sources
import simpmusic.composeapp.generated.resources.manage_your_youtube_accounts
import simpmusic.composeapp.generated.resources.no_account
import simpmusic.composeapp.generated.resources.normalize_volume
import simpmusic.composeapp.generated.resources.open_system_equalizer
import simpmusic.composeapp.generated.resources.original_audio
import simpmusic.composeapp.generated.resources.other_app
import simpmusic.composeapp.generated.resources.play_explicit_content
import simpmusic.composeapp.generated.resources.play_explicit_content_description
import simpmusic.composeapp.generated.resources.play_video_for_video_track_instead_of_audio_only
import simpmusic.composeapp.generated.resources.playback
import simpmusic.composeapp.generated.resources.player_cache
import simpmusic.composeapp.generated.resources.preferred_audio_language
import simpmusic.composeapp.generated.resources.preferred_audio_language_message
import simpmusic.composeapp.generated.resources.proxy
import simpmusic.composeapp.generated.resources.proxy_description
import simpmusic.composeapp.generated.resources.proxy_host
import simpmusic.composeapp.generated.resources.proxy_host_message
import simpmusic.composeapp.generated.resources.proxy_password
import simpmusic.composeapp.generated.resources.proxy_password_message
import simpmusic.composeapp.generated.resources.proxy_port
import simpmusic.composeapp.generated.resources.proxy_port_message
import simpmusic.composeapp.generated.resources.proxy_type
import simpmusic.composeapp.generated.resources.proxy_username
import simpmusic.composeapp.generated.resources.proxy_username_message
import simpmusic.composeapp.generated.resources.quality
import simpmusic.composeapp.generated.resources.radio_audio_only
import simpmusic.composeapp.generated.resources.radio_audio_only_description
import simpmusic.composeapp.generated.resources.restore_your_data
import simpmusic.composeapp.generated.resources.restore_your_saved_data
import simpmusic.composeapp.generated.resources.rich_presence_info
import simpmusic.composeapp.generated.resources.save
import simpmusic.composeapp.generated.resources.save_all_your_playlist_data
import simpmusic.composeapp.generated.resources.save_playback_state
import simpmusic.composeapp.generated.resources.save_shuffle_and_repeat_mode
import simpmusic.composeapp.generated.resources.send_back_listening_data_to_google
import simpmusic.composeapp.generated.resources.settings
import simpmusic.composeapp.generated.resources.show_analytics_tab
import simpmusic.composeapp.generated.resources.show_analytics_tab_description
import simpmusic.composeapp.generated.resources.signed_in
import simpmusic.composeapp.generated.resources.skip_no_music_part
import simpmusic.composeapp.generated.resources.skip_silent
import simpmusic.composeapp.generated.resources.smart_downloads
import simpmusic.composeapp.generated.resources.smart_downloads_description
import simpmusic.composeapp.generated.resources.smart_downloads_needs_local_tracking
import simpmusic.composeapp.generated.resources.smart_downloads_size
import simpmusic.composeapp.generated.resources.smart_downloads_size_value
import simpmusic.composeapp.generated.resources.socks
import simpmusic.composeapp.generated.resources.spotify
import simpmusic.composeapp.generated.resources.storage
import simpmusic.composeapp.generated.resources.such_as_music_video_lyrics_video_podcasts_and_more
import simpmusic.composeapp.generated.resources.theme
import simpmusic.composeapp.generated.resources.theme_color
import simpmusic.composeapp.generated.resources.theme_color_custom
import simpmusic.composeapp.generated.resources.theme_color_default
import simpmusic.composeapp.generated.resources.theme_color_wallpaper
import simpmusic.composeapp.generated.resources.theme_mode_dark
import simpmusic.composeapp.generated.resources.theme_mode_light
import simpmusic.composeapp.generated.resources.theme_mode_system
import simpmusic.composeapp.generated.resources.third_party_libraries
import simpmusic.composeapp.generated.resources.thumbnail_cache
import simpmusic.composeapp.generated.resources.translation_language
import simpmusic.composeapp.generated.resources.translation_language_message
import simpmusic.composeapp.generated.resources.upload_your_listening_history_to_youtube_music_server_it_will_make_yt_music_recommendation_system_better_working_only_if_logged_in
import simpmusic.composeapp.generated.resources.use_your_system_equalizer
import simpmusic.composeapp.generated.resources.user_interface
import simpmusic.composeapp.generated.resources.version
import simpmusic.composeapp.generated.resources.version_format
import simpmusic.composeapp.generated.resources.video_download_quality
import simpmusic.composeapp.generated.resources.video_quality
import simpmusic.composeapp.generated.resources.warning
import simpmusic.composeapp.generated.resources.youtube_account
import simpmusic.composeapp.generated.resources.youtube_subtitle_language
import simpmusic.composeapp.generated.resources.youtube_subtitle_language_message
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoilApi::class,
    FormatStringsInDatetimeFormats::class,
)
@Composable
fun SettingScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val platformContext = LocalPlatformContext.current
    val localDensity = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var width by rememberSaveable { mutableIntStateOf(0) }

    // Backup and restore
    val formatter =
        LocalDateTime.Format {
            byUnicodePattern("yyyyMMddHHmmss")
        }
    val appName = stringResource(Res.string.app_name)

    val backupLauncher =
        fileSaverResult(
            "${appName}_${
                now().format(
                    formatter,
                )
            }.backup",
            "application/octet-stream",
        ) { uri ->
            uri?.let {
                viewModel.backup(it.toKmpUri())
            }
        }

    val restoreLauncher =
        filePickerResult { uri ->
            uri?.let {
                viewModel.restore(it.toKmpUri())
            }
        }

    // Open equalizer
    val resultLauncher = openEqResult(viewModel.getAudioSessionId())

    val language by viewModel.language.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val autoDownloadLikedSongs by viewModel.autoDownloadLikedSongs.collectAsStateWithLifecycle()
    val downloadNotificationsEnabled by viewModel.downloadNotificationsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val smartDownloadsEnabled by viewModel.smartDownloadsEnabled.collectAsStateWithLifecycle()
    val smartDownloadsCount by viewModel.smartDownloadsCount.collectAsStateWithLifecycle()
    val videoDownloadQuality by viewModel.videoDownloadQuality.collectAsStateWithLifecycle()
    val keepYoutubePlaylistOffline by viewModel.keepYouTubePlaylistOffline.collectAsStateWithLifecycle()
    val localTrackingEnabled by viewModel.localTrackingEnabled.collectAsStateWithLifecycle(initialValue = false)
    val debugLogging by viewModel.debugLogging.collectAsStateWithLifecycle(initialValue = true)
    val analyticsTabEnabled by sharedViewModel.analyticsTabEnabled.collectAsStateWithLifecycle(initialValue = true)
    val playVideo by remember { viewModel.playVideoInsteadOfAudio.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val radioAudioOnly by remember { viewModel.radioAudioOnly.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle()
    val sendData by remember { viewModel.sendBackToGoogle.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val normalizeVolume by remember { viewModel.normalizeVolume.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val skipSilent by remember { viewModel.skipSilent.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val savePlaybackState by remember { viewModel.savedPlaybackState.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = false)
    val killServiceOnExit by remember { viewModel.killServiceOnExit.map { it == TRUE } }.collectAsStateWithLifecycle(initialValue = true)
    val lyricsSources by viewModel.lyricsSources.collectAsStateWithLifecycle()
    val videoLyricsOverlay by sharedViewModel.videoLyricsOverlay.collectAsStateWithLifecycle()
    val youtubeSubtitleLanguage by viewModel.youtubeSubtitleLanguage.collectAsStateWithLifecycle()
    val preferredAudioLanguage by viewModel.preferredAudioLanguage.collectAsStateWithLifecycle()
    val spotifyLoggedIn by viewModel.spotifyLogIn.collectAsStateWithLifecycle()
    val playerCache by viewModel.cacheSize.collectAsStateWithLifecycle()
    val downloadedCache by viewModel.downloadedCacheSize.collectAsStateWithLifecycle()
    val thumbnailCache by viewModel.thumbCacheSize.collectAsStateWithLifecycle()
    val limitPlayerCache by viewModel.playerCacheLimit.collectAsStateWithLifecycle()
    val fraction by viewModel.fraction.collectAsStateWithLifecycle()
    val lastCheckUpdate by viewModel.lastCheckForUpdate.collectAsStateWithLifecycle()
    val explicitContentEnabled by viewModel.explicitContentEnabled.collectAsStateWithLifecycle()
    val usingProxy by viewModel.usingProxy.collectAsStateWithLifecycle()
    val proxyType by viewModel.proxyType.collectAsStateWithLifecycle()
    val proxyHost by viewModel.proxyHost.collectAsStateWithLifecycle()
    val proxyPort by viewModel.proxyPort.collectAsStateWithLifecycle()
    val proxyUsername by viewModel.proxyUsername.collectAsStateWithLifecycle()
    val proxyPassword by viewModel.proxyPassword.collectAsStateWithLifecycle()
    val autoCheckUpdate by viewModel.autoCheckUpdate.collectAsStateWithLifecycle()
    val translationLanguage by viewModel.translationLanguage.collectAsStateWithLifecycle()
    val backupDownloaded by viewModel.backupDownloaded.collectAsStateWithLifecycle()
    val themeMode by sharedViewModel.getThemeMode().collectAsStateWithLifecycle(DataStoreManager.THEME_MODE_DARK)
    val themeColorSource by sharedViewModel.getThemeColorSource().collectAsStateWithLifecycle(DataStoreManager.THEME_COLOR_DEFAULT)
    val customThemeColorHex by sharedViewModel.getCustomThemeColor().collectAsStateWithLifecycle(DataStoreManager.DEFAULT_THEME_COLOR_HEX)
    var showColorPickerDialog by rememberSaveable { mutableStateOf(false) }
    val discordLoggedIn by viewModel.discordLoggedIn.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsStateWithLifecycle()
    val equalizerType by viewModel.equalizerType.collectAsStateWithLifecycle()
    val delayEnabled by viewModel.delayEnabled.collectAsStateWithLifecycle()
    val reverbEnabled by viewModel.reverbEnabled.collectAsStateWithLifecycle()
    val eightDEnabled by viewModel.eightDEnabled.collectAsStateWithLifecycle()
    val eightDAvailable by viewModel.eightDAvailable.collectAsStateWithLifecycle()
    val richPresenceEnabled by viewModel.richPresenceEnabled.collectAsStateWithLifecycle()

    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
    val crossfadeSkipAlbum by viewModel.crossfadeSkipAlbum.collectAsStateWithLifecycle()

    val isCheckingUpdate by sharedViewModel.isCheckingUpdate.collectAsStateWithLifecycle()

    val hazeState =
        rememberHazeState()

    val checkForUpdateSubtitle by remember {
        derivedStateOf {
            if (isCheckingUpdate) {
                return@derivedStateOf runBlocking { getString(Res.string.checking) }
            } else {
                val lastCheckLong = lastCheckUpdate?.toLong() ?: 0L
                return@derivedStateOf runBlocking {
                    getString(
                        Res.string.last_checked_at,
                        DateTimeFormatter
                            .ofPattern("yyyy-MM-dd HH:mm:ss")
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(lastCheckLong)),
                    )
                }
            }
        }
    }
    var showYouTubeAccountDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showLoginSyncDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showLyricsSourcesDialog by rememberSaveable { mutableStateOf(false) }
    var showThirdPartyLibraries by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(true) {
        viewModel.getAllGoogleAccount()
    }

    LaunchedEffect(true) {
        viewModel.getData()
        viewModel.getThumbCacheSize(platformContext)
    }

    val settingListState = rememberLazyListState()
    // The toolbar is part of the list and scrolls away with it; a pinned, frosted copy takes over
    // once the Devices group has gone under it — Playlist's and Artist's way. The group's height
    // (top padding, header, card) is the scroll that carries it fully under the bar.
    var devicesGroupHeightPx by remember { mutableIntStateOf(0) }
    val showPinnedBar by remember {
        derivedStateOf {
            settingListState.firstVisibleItemIndex > 0 ||
                settingListState.firstVisibleItemScrollOffset > devicesGroupHeightPx
        }
    }
    // The list's side margin, which the in-list toolbar stretches back across.
    val listSidePadding = 16.dp
    // Home-family ambient ground, and like Home's it SCROLLS AWAY with the content instead of
    // hanging off the ceiling. Still a sibling (so it sits behind the floating bar), but its draw
    // rides the list: exact tracking while item 0 is on screen, parked off-screen after. Item 0 is
    // taller than the glow, so the glow has fully left before the branch ever switches — no jump.
    // graphicsLayer reads the state in the DRAW phase, so scrolling redraws without recomposing.
    // Tinted with the app's own primary rather than the song playing: this page is about the app.
    AmbientThemeGlow(
        tint = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier.graphicsLayer {
                translationY =
                    if (settingListState.firstVisibleItemIndex == 0) {
                        -settingListState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        -size.height
                    }
            },
    )
    LazyColumn(
        state = settingListState,
        contentPadding = innerPadding,
        modifier =
            Modifier
                .padding(horizontal = listSidePadding)
                .hazeSource(hazeState),
    ) {
        item(key = "user_interface") {
            Column {
                // Was its own item. Folded in so item 0 is taller than the glow — the glow's
                // translation tracks item 0's offset exactly and parks once it scrolls past, and a
                // 64dp item 0 would have switched branches while the glow was still half-visible.
                // The toolbar's in-list copy, stretched across the list's side margin so it sits exactly
                // where the pinned copy appears; the list's top padding already clears the status bar.
                SettingTopBar(
                    onBack = { navController.navigateUp() },
                    windowInsets = WindowInsets(0),
                    modifier =
                        Modifier.layout { measurable, constraints ->
                            val bleed = listSidePadding.roundToPx()
                            val width = constraints.maxWidth + 2 * bleed
                            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                            layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
                        },
                )
                // Above every section, and inside item 0 rather than an item of its own, for the
                // glow reason above.
                SettingGroup(
                    title = stringResource(Res.string.login_sync_section),
                    modifier = Modifier.onSizeChanged { devicesGroupHeightPx = it.height },
                ) {
                    SettingItem(
                        title =
                            stringResource(
                                if (getPlatform() == Platform.Android) Res.string.login_sync_android_title else Res.string.login_sync_desktop_title,
                            ),
                        subtitle =
                            stringResource(
                                if (getPlatform() == Platform.Android) {
                                    Res.string.login_sync_android_description
                                } else {
                                    Res.string.login_sync_desktop_description
                                },
                            ),
                        onClick = { showLoginSyncDialog = true },
                    )
                }
                SettingGroup(title = stringResource(Res.string.user_interface)) {
                    val themeModeLabels =
                        listOf(
                            DataStoreManager.THEME_MODE_SYSTEM to stringResource(Res.string.theme_mode_system),
                            DataStoreManager.THEME_MODE_DARK to stringResource(Res.string.theme_mode_dark),
                            DataStoreManager.THEME_MODE_LIGHT to stringResource(Res.string.theme_mode_light),
                        )
                    SettingItem(
                        title = stringResource(Res.string.theme),
                        subtitle = themeModeLabels.firstOrNull { it.first == themeMode }?.second ?: "",
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = runBlocking { getString(Res.string.theme) },
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect = themeModeLabels.map { (it.first == themeMode) to it.second },
                                        ),
                                    confirm =
                                        runBlocking { getString(Res.string.change) } to { state ->
                                            val selected = state.selectOne?.getSelected()
                                            themeModeLabels.firstOrNull { it.second == selected }?.first?.let {
                                                sharedViewModel.setThemeMode(it)
                                            }
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                    val colorSourceLabels =
                        buildList {
                            add(DataStoreManager.THEME_COLOR_DEFAULT to stringResource(Res.string.theme_color_default))
                            if (isWallpaperDynamicColorSupported()) {
                                add(DataStoreManager.THEME_COLOR_WALLPAPER to stringResource(Res.string.theme_color_wallpaper))
                            }
                            add(DataStoreManager.THEME_COLOR_CUSTOM to stringResource(Res.string.theme_color_custom))
                        }
                    SettingItem(
                        title = stringResource(Res.string.theme_color),
                        subtitle = colorSourceLabels.firstOrNull { it.first == themeColorSource }?.second ?: "",
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = runBlocking { getString(Res.string.theme_color) },
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect = colorSourceLabels.map { (it.first == themeColorSource) to it.second },
                                        ),
                                    confirm =
                                        runBlocking { getString(Res.string.change) } to { state ->
                                            val selected = state.selectOne?.getSelected()
                                            colorSourceLabels.firstOrNull { it.second == selected }?.first?.let {
                                                sharedViewModel.setThemeColorSource(it)
                                                if (it == DataStoreManager.THEME_COLOR_CUSTOM) {
                                                    showColorPickerDialog = true
                                                }
                                            }
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                    if (themeColorSource == DataStoreManager.THEME_COLOR_CUSTOM) {
                        SettingItem(
                            title = stringResource(Res.string.custom_color),
                            subtitle = "#${customThemeColorHex.takeLast(6)}",
                            smallSubtitle = true,
                            onClick = { showColorPickerDialog = true },
                        )
                    }
                }
            }
        }
        item(key = "content") {
            SettingGroup(title = stringResource(Res.string.content)) {
                SettingItem(
                    title = stringResource(Res.string.youtube_account),
                    subtitle = stringResource(Res.string.manage_your_youtube_accounts),
                    onClick = {
                        viewModel.getAllGoogleAccount()
                        showYouTubeAccountDialog = true
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.language),
                    subtitle = SUPPORTED_LANGUAGE.getLanguageFromCode(language ?: "en-US"),
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.language) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            SUPPORTED_LANGUAGE.items.map {
                                                (it.toString() == SUPPORTED_LANGUAGE.getLanguageFromCode(language ?: "en-US")) to it.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        val code = SUPPORTED_LANGUAGE.getCodeFromLanguage(state.selectOne?.getSelected() ?: "English")
                                        viewModel.setBasicAlertData(
                                            SettingBasicAlertState(
                                                title = runBlocking { getString(Res.string.warning) },
                                                message = runBlocking { getString(Res.string.change_language_warning) },
                                                confirm =
                                                    runBlocking { getString(Res.string.change) } to {
                                                        sharedViewModel.activityRecreate()
                                                        viewModel.setBasicAlertData(null)
                                                        viewModel.changeLanguage(code)
                                                    },
                                                dismiss = runBlocking { getString(Res.string.cancel) },
                                            ),
                                        )
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.content_country),
                    subtitle = location ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.content_country) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            SUPPORTED_LOCATION.items.map { item ->
                                                (item.toString() == location) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.changeLocation(
                                            state.selectOne?.getSelected() ?: "US",
                                        )
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.preferred_audio_language),
                    subtitle = preferredAudioLanguage.ifEmpty { stringResource(Res.string.original_audio) },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.preferred_audio_language) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.preferred_audio_language) },
                                        value = preferredAudioLanguage,
                                        // Empty is valid here: it means "original audio".
                                        verifyCodeBlock = {
                                            (it.isEmpty() || it.isTwoLetterCode()) to
                                                runBlocking { getString(Res.string.invalid_language_code) }
                                        },
                                    ),
                                message = runBlocking { getString(Res.string.preferred_audio_language_message) },
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setPreferredAudioLanguage(state.textField?.value ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.quality),
                    subtitle = quality ?: "",
                    smallSubtitle = true,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.quality) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            QUALITY.items.map { item ->
                                                (item.toString() == quality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.changeQuality(state.selectOne?.getSelected())
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.download_quality),
                    subtitle = downloadQuality ?: "",
                    smallSubtitle = true,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.download_quality) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            QUALITY.items.map { item ->
                                                (item.toString() == downloadQuality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        state.selectOne?.getSelected()?.let { viewModel.setDownloadQuality(it) }
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.video_quality),
                    subtitle = videoQuality ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.video_quality) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            VIDEO_QUALITY.items.map { item ->
                                                (item.toString() == videoQuality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.changeVideoQuality(state.selectOne?.getSelected() ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.video_download_quality),
                    subtitle = videoDownloadQuality ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.video_download_quality) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            VIDEO_QUALITY.items.map { item ->
                                                (item.toString() == videoDownloadQuality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setVideoDownloadQuality(state.selectOne?.getSelected() ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.auto_download_liked_songs),
                    subtitle = stringResource(Res.string.auto_download_liked_songs_description),
                    smallSubtitle = true,
                    switch = (autoDownloadLikedSongs to { viewModel.setAutoDownloadLikedSongs(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.download_notifications),
                    subtitle = stringResource(Res.string.download_notifications_description),
                    smallSubtitle = true,
                    switch = (downloadNotificationsEnabled to { viewModel.setDownloadNotificationsEnabled(it) }),
                )
                // Smart downloads are Android only.
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.smart_downloads),
                        subtitle =
                            stringResource(
                                if (localTrackingEnabled) {
                                    Res.string.smart_downloads_description
                                } else {
                                    Res.string.smart_downloads_needs_local_tracking
                                },
                            ),
                        smallSubtitle = true,
                        switch = (smartDownloadsEnabled to { viewModel.setSmartDownloadsEnabled(it) }),
                        // Songs are picked from local tracking, so it gates switching on. Switching off
                        // stays possible without it, or the downloaded songs could not be cleared.
                        isEnable = localTrackingEnabled || smartDownloadsEnabled,
                    )
                    if (smartDownloadsEnabled) {
                        SettingItem(
                            title = stringResource(Res.string.smart_downloads_size),
                            subtitle = stringResource(Res.string.smart_downloads_size_value, smartDownloadsCount),
                            smallSubtitle = true,
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = runBlocking { getString(Res.string.smart_downloads_size) },
                                        selectOne =
                                            SettingAlertState.SelectData(
                                                listSelect =
                                                    listOf(25, 50, 100).map { count ->
                                                        (count == smartDownloadsCount) to count.toString()
                                                    },
                                            ),
                                        confirm =
                                            runBlocking { getString(Res.string.change) } to { state ->
                                                state.selectOne?.getSelected()?.toIntOrNull()?.let { viewModel.setSmartDownloadsCount(it) }
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            },
                        )
                    }
                }
                SettingItem(
                    title = stringResource(Res.string.play_video_for_video_track_instead_of_audio_only),
                    subtitle = stringResource(Res.string.such_as_music_video_lyrics_video_podcasts_and_more),
                    smallSubtitle = true,
                    switch = (playVideo to { viewModel.setPlayVideoInsteadOfAudio(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.radio_audio_only),
                    subtitle = stringResource(Res.string.radio_audio_only_description),
                    smallSubtitle = true,
                    switch = (radioAudioOnly to { viewModel.setRadioAudioOnly(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.send_back_listening_data_to_google),
                    subtitle =
                        stringResource(
                            Res.string
                                .upload_your_listening_history_to_youtube_music_server_it_will_make_yt_music_recommendation_system_better_working_only_if_logged_in,
                        ),
                    smallSubtitle = true,
                    switch = (sendData to { viewModel.setSendBackToGoogle(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.play_explicit_content),
                    subtitle = stringResource(Res.string.play_explicit_content_description),
                    switch = (explicitContentEnabled to { viewModel.setExplicitContentEnabled(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.keep_your_youtube_playlist_offline),
                    subtitle = stringResource(Res.string.keep_your_youtube_playlist_offline_description),
                    switch = (keepYoutubePlaylistOffline to { viewModel.setKeepYouTubePlaylistOffline(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.proxy),
                    subtitle = stringResource(Res.string.proxy_description),
                    switch = (usingProxy to { viewModel.setUsingProxy(it) }),
                )
                Crossfade(usingProxy) { it ->
                    if (it) {
                        Column {
                            SettingItem(
                                title = stringResource(Res.string.proxy_type),
                                subtitle =
                                    when (proxyType) {
                                        DataStoreManager.ProxyType.PROXY_TYPE_HTTP -> stringResource(Res.string.http)
                                        DataStoreManager.ProxyType.PROXY_TYPE_SOCKS -> stringResource(Res.string.socks)
                                    },
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = runBlocking { getString(Res.string.proxy_type) },
                                            selectOne =
                                                SettingAlertState.SelectData(
                                                    listSelect =
                                                        listOf(
                                                            (proxyType == DataStoreManager.ProxyType.PROXY_TYPE_HTTP) to
                                                                runBlocking {
                                                                    getString(
                                                                        Res.string.http,
                                                                    )
                                                                },
                                                            (proxyType == DataStoreManager.ProxyType.PROXY_TYPE_SOCKS) to
                                                                runBlocking { getString(Res.string.socks) },
                                                        ),
                                                ),
                                            confirm =
                                                runBlocking { getString(Res.string.change) } to { state ->
                                                    viewModel.setProxy(
                                                        if (state.selectOne?.getSelected() == runBlocking { getString(Res.string.socks) }) {
                                                            DataStoreManager.ProxyType.PROXY_TYPE_SOCKS
                                                        } else {
                                                            DataStoreManager.ProxyType.PROXY_TYPE_HTTP
                                                        },
                                                        proxyHost,
                                                        proxyPort,
                                                    )
                                                },
                                            dismiss = runBlocking { getString(Res.string.cancel) },
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.proxy_host),
                                subtitle = proxyHost,
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = runBlocking { getString(Res.string.proxy_host) },
                                            message = runBlocking { getString(Res.string.proxy_host_message) },
                                            textField =
                                                SettingAlertState.TextFieldData(
                                                    label = runBlocking { getString(Res.string.proxy_host) },
                                                    value = proxyHost,
                                                    verifyCodeBlock = {
                                                        isValidProxyHost(it) to runBlocking { getString(Res.string.invalid_host) }
                                                    },
                                                ),
                                            confirm =
                                                runBlocking { getString(Res.string.change) } to { state ->
                                                    viewModel.setProxy(
                                                        proxyType,
                                                        state.textField?.value ?: "",
                                                        proxyPort,
                                                    )
                                                },
                                            dismiss = runBlocking { getString(Res.string.cancel) },
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.proxy_port),
                                subtitle = proxyPort.toString(),
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = runBlocking { getString(Res.string.proxy_port) },
                                            message = runBlocking { getString(Res.string.proxy_port_message) },
                                            textField =
                                                SettingAlertState.TextFieldData(
                                                    label = runBlocking { getString(Res.string.proxy_port) },
                                                    value = proxyPort.toString(),
                                                    verifyCodeBlock = {
                                                        (it.toIntOrNull() != null) to runBlocking { getString(Res.string.invalid_port) }
                                                    },
                                                ),
                                            confirm =
                                                runBlocking { getString(Res.string.change) } to { state ->
                                                    viewModel.setProxy(
                                                        proxyType,
                                                        proxyHost,
                                                        state.textField?.value?.toIntOrNull() ?: 0,
                                                    )
                                                },
                                            dismiss = runBlocking { getString(Res.string.cancel) },
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.proxy_username),
                                subtitle = proxyUsername,
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = runBlocking { getString(Res.string.proxy_username) },
                                            message = runBlocking { getString(Res.string.proxy_username_message) },
                                            textField =
                                                SettingAlertState.TextFieldData(
                                                    label = runBlocking { getString(Res.string.proxy_username) },
                                                    value = proxyUsername,
                                                ),
                                            confirm =
                                                runBlocking { getString(Res.string.change) } to { state ->
                                                    viewModel.setProxyCredentials(
                                                        state.textField?.value ?: "",
                                                        proxyPassword,
                                                    )
                                                },
                                            dismiss = runBlocking { getString(Res.string.cancel) },
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.proxy_password),
                                subtitle =
                                    if (proxyPassword.isEmpty()) {
                                        ""
                                    } else {
                                        "\u2022".repeat(proxyPassword.length)
                                    },
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = runBlocking { getString(Res.string.proxy_password) },
                                            message = runBlocking { getString(Res.string.proxy_password_message) },
                                            textField =
                                                SettingAlertState.TextFieldData(
                                                    label = runBlocking { getString(Res.string.proxy_password) },
                                                    value = proxyPassword,
                                                ),
                                            confirm =
                                                runBlocking { getString(Res.string.change) } to { state ->
                                                    viewModel.setProxyCredentials(
                                                        proxyUsername,
                                                        state.textField?.value ?: "",
                                                    )
                                                },
                                            dismiss = runBlocking { getString(Res.string.cancel) },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        if (getPlatform() == Platform.Android) {
            item(key = "audio") {
                SettingGroup(title = stringResource(Res.string.audio)) {
                    val equalizerTypeLabels =
                        listOf(
                            DataStoreManager.EQUALIZER_TYPE_BUILT_IN to stringResource(Res.string.equalizer_type_built_in),
                            DataStoreManager.EQUALIZER_TYPE_SYSTEM to stringResource(Res.string.equalizer_type_system),
                        )
                    SettingItem(
                        title = stringResource(Res.string.equalizer_type),
                        subtitle = equalizerTypeLabels.firstOrNull { it.first == equalizerType }?.second ?: "",
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = runBlocking { getString(Res.string.equalizer_type) },
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect = equalizerTypeLabels.map { (it.first == equalizerType) to it.second },
                                        ),
                                    confirm =
                                        runBlocking { getString(Res.string.change) } to { state ->
                                            val selected = state.selectOne?.getSelected()
                                            equalizerTypeLabels.firstOrNull { it.second == selected }?.first?.let {
                                                viewModel.setEqualizerType(it)
                                            }
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                    AnimatedVisibility(visible = equalizerType == DataStoreManager.EQUALIZER_TYPE_SYSTEM) {
                        SettingItem(
                            title = stringResource(Res.string.open_system_equalizer),
                            subtitle = stringResource(Res.string.use_your_system_equalizer),
                            onClick = {
                                coroutineScope.launch {
                                    resultLauncher.launch()
                                }
                            },
                        )
                    }
                }
            }
        }
        item(key = "playback") {
            SettingGroup(title = stringResource(Res.string.playback)) {
                // On both platforms: Android drives the system loudness enhancer, Desktop mpv's volume-gain,
                // each from the loudness YouTube reports for the track. It sat in the Android-only audio group.
                SettingItem(
                    title = stringResource(Res.string.normalize_volume),
                    subtitle = stringResource(Res.string.balance_media_loudness),
                    switch = (normalizeVolume to { viewModel.setNormalizeVolume(it) }),
                )
                // Silence at the start, inside and at the end of a track: Media3's skipper and an AudioProcessor for
                // its tail on Android, mpv's silenceremove on Desktop. Also moved out of the Android-only group.
                SettingItem(
                    title = stringResource(Res.string.skip_silent),
                    subtitle = stringResource(Res.string.skip_no_music_part),
                    switch = (skipSilent to { viewModel.setSkipSilent(it) }),
                )
                // Under Playback rather than Audio because that whole group sits inside an
                // Android-only branch — "Open system equalizer" is an Android feature — and this
                // one is on both platforms: mpv's `af` chain on Desktop, an AudioProcessor in the
                // Media3 sink on Android, driven from the same stored curve.
                AnimatedVisibility(visible = getPlatform() != Platform.Android || equalizerType != DataStoreManager.EQUALIZER_TYPE_SYSTEM) {
                    Column {
                        SettingItem(
                            title = stringResource(Res.string.equalizer),
                            subtitle = stringResource(Res.string.equalizer_description),
                            smallSubtitle = true,
                            switch = (equalizerEnabled to { viewModel.setEqualizerEnabled(it) }),
                        )
                        // Only while on. A curve that visibly does nothing is worse than no curve —
                        // and the stored bands survive the switch, so turning it back on returns to
                        // the shape the user built rather than to flat.
                        AnimatedVisibility(visible = equalizerEnabled) {
                            EqualizerSection()
                        }
                    }
                }
                // Beside the equalizer rather than in its own group: all three are the same kind of
                // thing — one stored setting reshaping the audio on both backends — and a user
                // hunting for "reverb" looks wherever the sound settings are, not under a heading
                // they have to guess.
                SettingItem(
                    title = stringResource(Res.string.audio_delay),
                    subtitle = stringResource(Res.string.audio_delay_description),
                    smallSubtitle = true,
                    switch = (delayEnabled to { viewModel.setDelayEnabled(it) }),
                )
                // Only while on, like the curve — and the three values survive the switch, so
                // turning it back on returns to the echo the user dialled in.
                AnimatedVisibility(visible = delayEnabled) {
                    DelaySection()
                }
                SettingItem(
                    title = stringResource(Res.string.audio_reverb),
                    subtitle = stringResource(Res.string.audio_reverb_description),
                    smallSubtitle = true,
                    switch = (reverbEnabled to { viewModel.setReverbEnabled(it) }),
                )
                // Same again: the room and the wet level outlive the switch.
                AnimatedVisibility(visible = reverbEnabled) {
                    ReverbSection()
                }
                SettingItem(
                    title = stringResource(Res.string.audio_eight_d),
                    subtitle =
                        when {
                            !eightDAvailable -> stringResource(Res.string.eight_d_unavailable)
                            else -> stringResource(Res.string.audio_eight_d_description)
                        },
                    smallSubtitle = true,
                    switch = (eightDEnabled to viewModel::setEightDEnabled),
                    // A saved on-state may still be switched off when the native filter is absent.
                    isEnable = eightDAvailable || eightDEnabled,
                )
                AnimatedVisibility(visible = eightDEnabled && eightDAvailable) {
                    EightDSection()
                }
                SettingItem(
                    title = stringResource(Res.string.save_playback_state),
                    subtitle = stringResource(Res.string.save_shuffle_and_repeat_mode),
                    switch = (savePlaybackState to { viewModel.setSavedPlaybackState(it) }),
                )
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.kill_service_on_exit),
                        subtitle = stringResource(Res.string.kill_service_on_exit_description),
                        switch = (killServiceOnExit to { viewModel.setKillServiceOnExit(it) }),
                    )
                }
                // Crossfade (all platforms)
                SettingItem(
                    title = stringResource(Res.string.crossfade),
                    subtitle = stringResource(Res.string.crossfade_description),
                    smallSubtitle = true,
                    switch = (crossfadeEnabled to { viewModel.setCrossfadeEnabled(it) }),
                )
                AnimatedVisibility(visible = crossfadeEnabled) {
                    Column {
                        SettingItem(
                            title = stringResource(Res.string.crossfade_duration),
                            subtitle = "${crossfadeDuration / 1000}s",
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = runBlocking { getString(Res.string.crossfade_duration) },
                                        selectOne =
                                            SettingAlertState.SelectData(
                                                listSelect =
                                                    listOf(
                                                        (crossfadeDuration == 1000) to "1s",
                                                        (crossfadeDuration == 2000) to "2s",
                                                        (crossfadeDuration == 3000) to "3s",
                                                        (crossfadeDuration == 5000) to "5s",
                                                        (crossfadeDuration == 8000) to "8s",
                                                        (crossfadeDuration == 10000) to "10s",
                                                        (crossfadeDuration == 12000) to "12s",
                                                        (crossfadeDuration == 15000) to "15s",
                                                        (crossfadeDuration == 20000) to "20s",
                                                        (crossfadeDuration == 30000) to "30s",
                                                    ),
                                            ),
                                        confirm =
                                            runBlocking { getString(Res.string.change) } to { state ->
                                                val duration =
                                                    when (state.selectOne?.getSelected()) {
                                                        "1s" -> 1000
                                                        "2s" -> 2000
                                                        "3s" -> 3000
                                                        "5s" -> 5000
                                                        "8s" -> 8000
                                                        "10s" -> 10000
                                                        "12s" -> 12000
                                                        "15s" -> 15000
                                                        "20s" -> 20000
                                                        "30s" -> 30000
                                                        else -> 5000
                                                    }
                                                viewModel.setCrossfadeDuration(duration)
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            },
                        )
//                        if (getPlatform() == Platform.Android) {
                        SettingItem(
                            title = stringResource(Res.string.crossfade_skip_album),
                            subtitle = stringResource(Res.string.crossfade_skip_album_description),
                            smallSubtitle = true,
                            switch = ((crossfadeSkipAlbum) to { viewModel.setCrossfadeSkipAlbum(it) }),
                        )
//                        }
                    }
                }
            }
        }
        // Deliberately not part of "storage" further down, which is Android-only: tracking and the
        // rows it leaves behind exist on Desktop just the same. The switch that produces the history
        // and the button that erases it belong together.
        item(key = "listening_history") {
            SettingGroup(title = stringResource(Res.string.listening_history)) {
                SettingItem(
                    title = stringResource(Res.string.local_tracking_title),
                    subtitle = stringResource(Res.string.local_tracking_description),
                    switch = (localTrackingEnabled to { viewModel.setLocalTrackingEnabled(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.show_analytics_tab),
                    subtitle = stringResource(Res.string.show_analytics_tab_description),
                    switch = (analyticsTabEnabled to { sharedViewModel.setAnalyticsTabEnabled(it) }),
                    isEnable = localTrackingEnabled,
                )
                SettingItem(
                    title = stringResource(Res.string.clear_listening_history),
                    subtitle = stringResource(Res.string.clear_listening_history_description),
                    onClick = {
                        viewModel.setBasicAlertData(
                            SettingBasicAlertState(
                                title = runBlocking { getString(Res.string.clear_listening_history) },
                                message = runBlocking { getString(Res.string.clear_listening_history_confirm) },
                                confirm =
                                    runBlocking { getString(Res.string.clear) } to {
                                        viewModel.clearListeningHistory()
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
            }
        }
        item(key = "lyrics") {
            SettingGroup(title = stringResource(Res.string.lyrics)) {
                SettingItem(
                    title = stringResource(Res.string.lyrics_sources),
                    subtitle = lyricsSourcesSummary(lyricsSources),
                    onClick = { if (lyricsSources.isNotEmpty()) showLyricsSourcesDialog = true },
                )
                SettingItem(
                    title = stringResource(Res.string.lyrics_over_video),
                    subtitle = stringResource(Res.string.lyrics_over_video_description),
                    switch = (videoLyricsOverlay to { sharedViewModel.setVideoLyricsOverlay(it) }),
                )

                SettingItem(
                    title = stringResource(Res.string.translation_language),
                    subtitle = translationLanguage ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.translation_language) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.translation_language) },
                                        value = translationLanguage ?: "",
                                        verifyCodeBlock = {
                                            (it.length == 2 && it.isTwoLetterCode()) to
                                                runBlocking { getString(Res.string.invalid_language_code) }
                                        },
                                    ),
                                message = runBlocking { getString(Res.string.translation_language_message) },
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setTranslationLanguage(state.textField?.value ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                    isEnable = true,
                )
                SettingItem(
                    title = stringResource(Res.string.youtube_subtitle_language),
                    subtitle = youtubeSubtitleLanguage,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.youtube_subtitle_language) },
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = runBlocking { getString(Res.string.youtube_subtitle_language) },
                                        value = youtubeSubtitleLanguage,
                                        verifyCodeBlock = {
                                            (it.length == 2 && it.isTwoLetterCode()) to
                                                runBlocking { getString(Res.string.invalid_language_code) }
                                        },
                                    ),
                                message = runBlocking { getString(Res.string.youtube_subtitle_language_message) },
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setYoutubeSubtitleLanguage(state.textField?.value ?: "")
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
            }
        }
        item(key = "spotify") {
            SettingGroup(title = stringResource(Res.string.spotify)) {
                SettingItem(
                    // The title follows the state: a row that still reads "Log in" while logged in
                    // gives no clue that tapping it signs you out.
                    title =
                        if (spotifyLoggedIn) {
                            stringResource(Res.string.log_out_from_spotify)
                        } else {
                            stringResource(Res.string.log_in_to_spotify)
                        },
                    subtitle =
                        if (spotifyLoggedIn) {
                            stringResource(Res.string.logged_in)
                        } else {
                            stringResource(Res.string.intro_login_to_spotify)
                        },
                    onClick = {
                        if (spotifyLoggedIn) {
                            viewModel.confirmLogOut(
                                confirmLabel = runBlocking { getString(Res.string.log_out_from_spotify) },
                            ) { viewModel.setSpotifyLogIn(false) }
                        } else {
                            navController.navigateOnce(SpotifyLoginDestination)
                        }
                    },
                )
            }
        }
        item(key = "discord") {
            SettingGroup(title = stringResource(Res.string.discord_integration)) {
                SettingItem(
                    title =
                        if (discordLoggedIn) {
                            stringResource(Res.string.log_out_from_discord)
                        } else {
                            stringResource(Res.string.log_in_to_discord)
                        },
                    subtitle =
                        if (discordLoggedIn) {
                            stringResource(Res.string.logged_in)
                        } else {
                            stringResource(Res.string.intro_login_to_discord)
                        },
                    onClick = {
                        if (discordLoggedIn) {
                            viewModel.confirmLogOut(
                                confirmLabel = runBlocking { getString(Res.string.log_out_from_discord) },
                            ) { viewModel.logOutDiscord() }
                        } else {
                            navController.navigateOnce(DiscordLoginDestination)
                        }
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.enable_rich_presence),
                    subtitle = stringResource(Res.string.rich_presence_info),
                    switch = (richPresenceEnabled to { viewModel.setDiscordRichPresenceEnabled(it) }),
                    isEnable = discordLoggedIn,
                )
            }
        }
        item(key = "storage") {
            SettingGroup(title = stringResource(Res.string.storage)) {
                SettingItem(
                    title = stringResource(Res.string.player_cache),
                    subtitle = "${playerCache.bytesToMB()} MB",
                    onClick = {
                        viewModel.setBasicAlertData(
                            SettingBasicAlertState(
                                title = runBlocking { getString(Res.string.clear_player_cache) },
                                message = null,
                                confirm =
                                    runBlocking { getString(Res.string.clear) } to {
                                        viewModel.clearPlayerCache()
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                // Desktop has no download cache to clear here (downloads are managed on their own screen).
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.downloaded_cache),
                        subtitle = "${downloadedCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = runBlocking { getString(Res.string.clear_downloaded_cache) },
                                    message = null,
                                    confirm =
                                        runBlocking { getString(Res.string.clear) } to {
                                            viewModel.clearDownloadedCache()
                                        },
                                    dismiss = runBlocking { getString(Res.string.cancel) },
                                ),
                            )
                        },
                    )
                }
                SettingItem(
                    title = stringResource(Res.string.thumbnail_cache),
                    subtitle = "${thumbnailCache.bytesToMB()} MB",
                    onClick = {
                        viewModel.setBasicAlertData(
                            SettingBasicAlertState(
                                title = runBlocking { getString(Res.string.clear_thumbnail_cache) },
                                message = null,
                                confirm =
                                    runBlocking { getString(Res.string.clear) } to {
                                        viewModel.clearThumbnailCache(platformContext)
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.limit_player_cache),
                    subtitle = LIMIT_CACHE_SIZE.getItemFromData(limitPlayerCache).toString(),
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = runBlocking { getString(Res.string.limit_player_cache) },
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            LIMIT_CACHE_SIZE.items.map { item ->
                                                (item == LIMIT_CACHE_SIZE.getItemFromData(limitPlayerCache)) to item.toString()
                                            },
                                    ),
                                confirm =
                                    runBlocking { getString(Res.string.change) } to { state ->
                                        viewModel.setPlayerCacheLimit(
                                            LIMIT_CACHE_SIZE.getDataFromItem(state.selectOne?.getSelected()),
                                        )
                                    },
                                dismiss = runBlocking { getString(Res.string.cancel) },
                            ),
                        )
                    },
                )
                // The storage breakdown is measured on Android only (calculateDataFraction).
                if (getPlatform() == Platform.Android) {
                    Box(
                        Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 16.dp,
                        ),
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .onGloballyPositioned { layoutCoordinates ->
                                        with(localDensity) {
                                            width =
                                                layoutCoordinates.size.width
                                                    .toDp()
                                                    .value
                                                    .toInt()
                                        }
                                    },
                        ) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.otherApp * width).dp,
                                            ).background(
                                                md_theme_dark_primary,
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.downloadCache * width).dp,
                                            ).background(
                                                Color(0xD540FF17),
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.playerCache * width).dp,
                                            ).background(
                                                Color(0xD5FFFF00),
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.thumbCache * width).dp,
                                            ).background(
                                                Color.Magenta,
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.appDatabase * width).dp,
                                            ).background(
                                                Color.White,
                                            ),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.freeSpace * width).dp,
                                            ).background(
                                                Color.DarkGray,
                                            ).fillMaxHeight(),
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    md_theme_dark_primary,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.other_app), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Green,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.downloaded_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Yellow,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.player_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Magenta,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.thumbnail_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.White,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.database), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.LightGray,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.free_space), style = typo().bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        item(key = "backup") {
            SettingGroup(
                title = stringResource(Res.string.backup),
            ) {
                SettingItem(
                    title = stringResource(Res.string.backup_downloaded),
                    subtitle = stringResource(Res.string.backup_downloaded_description),
                    switch = (backupDownloaded to { viewModel.setBackupDownloaded(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.backup),
                    subtitle = stringResource(Res.string.save_all_your_playlist_data),
                    onClick = {
                        coroutineScope.launch {
                            backupLauncher.launch()
                        }
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.restore_your_data),
                    subtitle = stringResource(Res.string.restore_your_saved_data),
                    onClick = {
                        coroutineScope.launch {
                            restoreLauncher.launch()
                        }
                    },
                )
            }
        }
        item(key = "about_us") {
            SettingGroup(title = stringResource(Res.string.about_us)) {
                SettingItem(
                    title = stringResource(Res.string.version),
                    subtitle = stringResource(Res.string.version_format, VersionManager.getVersionName()),
                    onClick = {
                        navController.navigateOnce(CreditDestination)
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.based_on_simpmusic),
                    subtitle = "github.com/maxrave-dev/SimpMusic",
                    onClick = {
                        uriHandler.openUri("https://github.com/maxrave-dev/SimpMusic")
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.auto_check_for_update),
                    subtitle = stringResource(Res.string.auto_check_for_update_description),
                    switch = (autoCheckUpdate to { viewModel.setAutoCheckUpdate(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.check_for_update),
                    subtitle = checkForUpdateSubtitle,
                    onClick = {
                        sharedViewModel.checkForUpdate()
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.third_party_libraries),
                    subtitle = stringResource(Res.string.description_and_licenses),
                    onClick = {
                        showThirdPartyLibraries = true
                    },
                )
            }
        }
        item(key = "developer_option") {
            SettingGroup(title = stringResource(Res.string.developer_option)) {
                SettingItem(
                    title = stringResource(Res.string.app_log),
                    subtitle = stringResource(Res.string.app_log_description),
                    onClick = {
                        navController.navigateOnce(AppLogDestination)
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.debug_logging),
                    subtitle = stringResource(Res.string.debug_logging_description),
                    switch = (debugLogging to { viewModel.setDebugLogging(it) }),
                )
            }
        }
        item(key = "end") {
            EndOfPage()
        }
    }
    VerticalScrollBar(
        scrollState = settingListState,
        modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
    )
    val showLoadingDialog by viewModel.showLoadingDialog.collectAsStateWithLifecycle()
    if (showLoadingDialog.first) {
        LoadingDialog(
            true,
            showLoadingDialog.second,
        )
    }
    val basisAlertData by viewModel.basicAlertData.collectAsStateWithLifecycle()
    if (basisAlertData != null) {
        val alertBasicState = basisAlertData ?: return
        AlertDialog(
            onDismissRequest = { viewModel.setBasicAlertData(null) },
            title = {
                Text(
                    text = alertBasicState.title,
                    style = typo().titleSmall,
                )
            },
            text = {
                if (alertBasicState.message != null) {
                    Text(text = alertBasicState.message)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alertBasicState.confirm.second.invoke()
                        viewModel.setBasicAlertData(null)
                    },
                ) {
                    Text(text = alertBasicState.confirm.first)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setBasicAlertData(null)
                    },
                ) {
                    Text(text = alertBasicState.dismiss)
                }
            },
        )
    }
    if (showColorPickerDialog) {
        val presetColors =
            listOf(
                "FF8ECAE6",
                "FF4C82EF",
                "FF9B72CF",
                "FFEF6C9B",
                "FFEF5350",
                "FFF4A340",
                "FFFFCA28",
                "FF66BB6A",
                "FF26A69A",
                "FFBDBDBD",
            )
        var pendingHex by rememberSaveable { mutableStateOf(customThemeColorHex.takeLast(6)) }
        val parsedColor = parseThemeColorHex(pendingHex)
        AlertDialog(
            onDismissRequest = { showColorPickerDialog = false },
            title = { Text(text = stringResource(Res.string.custom_color), style = typo().titleSmall) },
            text = {
                Column {
                    presetColors.chunked(5).forEach { rowColors ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            rowColors.forEach { hex ->
                                val color = parseThemeColorHex(hex) ?: Color.Gray
                                val isSelected = pendingHex.equals(hex.takeLast(6), ignoreCase = true)
                                Box(
                                    modifier =
                                        Modifier
                                            .padding(4.dp)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape,
                                            ).clickable { pendingHex = hex.takeLast(6) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = pendingHex,
                        onValueChange = { pendingHex = it.removePrefix("#").take(8).uppercase() },
                        label = { Text("HEX") },
                        prefix = { Text("#") },
                        singleLine = true,
                        isError = parsedColor == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = parsedColor != null,
                    onClick = {
                        parsedColor?.let {
                            val argb = "FF${pendingHex.takeLast(6).uppercase()}"
                            sharedViewModel.setCustomThemeColor(argb)
                            sharedViewModel.setThemeColorSource(DataStoreManager.THEME_COLOR_CUSTOM)
                        }
                        showColorPickerDialog = false
                    },
                ) { Text(text = stringResource(Res.string.change)) }
            },
            dismissButton = {
                TextButton(onClick = { showColorPickerDialog = false }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }
    if (showLoginSyncDialog) {
        LoginSyncDialog(onDismiss = { showLoginSyncDialog = false })
    }
    if (showYouTubeAccountDialog) {
        BasicAlertDialog(
            onDismissRequest = { },
            modifier = Modifier.wrapContentSize(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                shadowElevation = 1.dp,
            ) {
                val googleAccounts by viewModel.googleAccounts.collectAsStateWithLifecycle(
                    minActiveState = Lifecycle.State.RESUMED,
                )
                LaunchedEffect(googleAccounts) {
                    Logger.w(
                        "SettingScreen",
                        "LaunchedEffect: ${
                            googleAccounts.data?.map {
                                it.name to it.isUsed
                            }
                        }",
                    )
                }
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                        ) {
                            IconButton(
                                onClick = { showYouTubeAccountDialog = false },
                                colors =
                                    IconButtonDefaults.iconButtonColors().copy(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .fillMaxHeight(),
                            ) {
                                Icon(SimpIcons.Close, null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                stringResource(Res.string.youtube_account),
                                style = typo().titleMedium,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .wrapContentHeight(align = Alignment.CenterVertically)
                                        .wrapContentWidth(),
                            )
                        }
                    }
                    if (googleAccounts is LocalResource.Success) {
                        val data = googleAccounts.data
                        if (data.isNullOrEmpty()) {
                            item {
                                Text(
                                    stringResource(Res.string.no_account),
                                    style = typo().bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier =
                                        Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                )
                            }
                        } else {
                            items(data) {
                                Row(
                                    modifier =
                                        Modifier
                                            .padding(vertical = 8.dp)
                                            .clickable {
                                                viewModel.setUsedAccount(it)
                                            },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Spacer(Modifier.width(24.dp))
                                    AsyncImage(
                                        model =
                                            ImageRequest
                                                .Builder(LocalPlatformContext.current)
                                                .data(it.thumbnailUrl)
                                                .crossfade(550)
                                                .build(),
                                        placeholder = rememberVectorPainter(SimpIcons.PeopleAlt),
                                        error = rememberVectorPainter(SimpIcons.PeopleAlt),
                                        contentDescription = it.name,
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(it.name, style = typo().labelMedium, color = MaterialTheme.colorScheme.onBackground)
                                        Text(it.email, style = typo().bodySmall)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    AnimatedVisibility(it.isUsed) {
                                        Text(
                                            stringResource(Res.string.signed_in),
                                            style = typo().bodySmall,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.widthIn(0.dp, 64.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(24.dp))
                                }
                            }
                        }
                    } else {
                        item {
                            CenterLoadingBox(
                                Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                            )
                        }
                    }
                    item {
                        Column {
                            ActionButton(
                                icon = SimpIcons.PeopleAlt,
                                text = Res.string.guest,
                            ) {
                                viewModel.setUsedAccount(null)
                                showYouTubeAccountDialog = false
                            }
                            ActionButton(
                                icon = SimpIcons.Close,
                                text = Res.string.log_out,
                            ) {
                                viewModel.setBasicAlertData(
                                    SettingBasicAlertState(
                                        title = runBlocking { getString(Res.string.warning) },
                                        message = runBlocking { getString(Res.string.log_out_warning) },
                                        confirm =
                                            runBlocking { getString(Res.string.log_out) } to {
                                                viewModel.logOutAllYouTube()
                                                showYouTubeAccountDialog = false
                                            },
                                        dismiss = runBlocking { getString(Res.string.cancel) },
                                    ),
                                )
                            }
                            ActionButton(
                                icon = SimpIcons.PlaylistAdd,
                                text = Res.string.add_an_account,
                            ) {
                                showYouTubeAccountDialog = false
                                navController.navigateOnce(LoginDestination)
                            }
                        }
                    }
                }
            }
        }
    }
    val alertData by viewModel.alertData.collectAsStateWithLifecycle()
    if (alertData != null) {
        val alertState = alertData ?: return
        // AlertDialog
        AlertDialog(
            onDismissRequest = { viewModel.setAlertData(null) },
            title = {
                Text(
                    text = alertState.title,
                    style = typo().titleSmall,
                )
            },
            text = {
                if (alertState.message != null) {
                    Column {
                        Text(text = alertState.message)
                        if (alertState.textField != null) {
                            val verify =
                                alertState.textField.verifyCodeBlock?.invoke(
                                    alertState.textField.value,
                                ) ?: (true to null)
                            TextField(
                                value = alertState.textField.value,
                                onValueChange = {
                                    viewModel.setAlertData(
                                        alertState.copy(
                                            textField =
                                                alertState.textField.copy(
                                                    value = it,
                                                ),
                                        ),
                                    )
                                },
                                isError = !verify.first,
                                label = { Text(text = alertState.textField.label) },
                                supportingText = {
                                    if (!verify.first) {
                                        Text(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = verify.second ?: "",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (!verify.first) {
                                        SimpIcons.Error
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = 6.dp,
                                        ),
                            )
                        }
                    }
                } else if (alertState.selectOne != null) {
                    LazyColumn(
                        Modifier
                            .padding(vertical = 6.dp)
                            .heightIn(0.dp, 500.dp),
                    ) {
                        items(alertState.selectOne.listSelect) { item ->
                            val onSelect = {
                                viewModel.setAlertData(
                                    alertState.copy(
                                        selectOne =
                                            alertState.selectOne.copy(
                                                listSelect =
                                                    alertState.selectOne.listSelect.toMutableList().map {
                                                        if (it == item) {
                                                            true to it.second
                                                        } else {
                                                            false to it.second
                                                        }
                                                    },
                                            ),
                                    ),
                                )
                            }
                            Row(
                                Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onSelect.invoke()
                                    }.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = item.first,
                                    onClick = {
                                        onSelect.invoke()
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item.second,
                                    style = typo().bodyMedium,
                                    maxLines = 1,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight(align = Alignment.CenterVertically)
                                            .basicMarquee(
                                                iterations = LocalMarqueeIterations.current,
                                                animationMode = MarqueeAnimationMode.Immediately,
                                            ).focusable(),
                                )
                            }
                        }
                    }
                } else if (alertState.multipleSelect != null) {
                    LazyColumn(
                        Modifier.padding(vertical = 6.dp),
                    ) {
                        items(alertState.multipleSelect.listSelect) { item ->
                            val onCheck = {
                                viewModel.setAlertData(
                                    alertState.copy(
                                        multipleSelect =
                                            alertState.multipleSelect.copy(
                                                listSelect =
                                                    alertState.multipleSelect.listSelect.toMutableList().map {
                                                        if (it == item) {
                                                            !it.first to it.second
                                                        } else {
                                                            it
                                                        }
                                                    },
                                            ),
                                    ),
                                )
                            }
                            Row(
                                Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onCheck.invoke()
                                    }.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = item.first,
                                    onCheckedChange = {
                                        onCheck.invoke()
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(text = item.second, style = typo().bodyMedium, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alertState.confirm.second.invoke(alertState)
                        viewModel.setAlertData(null)
                    },
                    enabled =
                        if (alertState.textField?.verifyCodeBlock != null) {
                            alertState.textField.verifyCodeBlock
                                .invoke(
                                    alertState.textField.value,
                                ).first
                        } else {
                            true
                        },
                ) {
                    Text(text = alertState.confirm.first)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setAlertData(null)
                    },
                ) {
                    Text(text = alertState.dismiss)
                }
            },
        )
    }

    if (showLyricsSourcesDialog && lyricsSources.isNotEmpty()) {
        LyricsSourcesDialog(
            sources = lyricsSources,
            spotifyLoggedIn = spotifyLoggedIn,
            onSave = viewModel::saveLyricsSources,
            onDismiss = { showLyricsSourcesDialog = false },
        )
    }

    if (showThirdPartyLibraries) {
        val libraries by produceLibraries {
            Res.readBytes("files/aboutlibraries.json").decodeToString()
        }
        val lazyListState = rememberLazyListState()
        val canScrollBackward by remember {
            derivedStateOf {
                lazyListState.canScrollBackward
            }
        }
        val sheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = {
                    !canScrollBackward
                },
            )
        val coroutineScope = rememberCoroutineScope()
        ModalBottomSheet(
            modifier =
                Modifier
                    .fillMaxHeight(),
            onDismissRequest = {
                showThirdPartyLibraries = false
            },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {},
            scrimColor = Color.Black.copy(alpha = .5f),
            sheetState = sheetState,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            shape = RectangleShape,
        ) {
            // Capture theme colors here: the ChipColors getters below run outside composition.
            val surfaceContainerHighestColor = MaterialTheme.colorScheme.surfaceContainerHighest
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            LibrariesContainer(
                libraries?.copy(
                    libraries =
                        libraries
                            ?.libraries
                            ?.distinctBy {
                                it.name
                            }?.toImmutableList() ?: emptyList<Library>().toImmutableList(),
                ),
                Modifier.fillMaxSize(),
                lazyListState = lazyListState,
                contentPadding = innerPadding,
                colors =
                    LibraryDefaults.libraryColors(
                        licenseChipColors =
                            object : ChipColors {
                                override val containerColor: Color
                                    get() = surfaceContainerHighestColor
                                override val contentColor: Color
                                    get() = onSurfaceColor
                            },
                    ),
                header = {
                    item {
                        TopAppBar(
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            title = {
                                Text(
                                    text =
                                        stringResource(
                                            Res.string.third_party_libraries,
                                        ),
                                    style = typo().titleMedium,
                                )
                            },
                            navigationIcon = {
                                Box(Modifier.padding(horizontal = 5.dp)) {
                                    RippleIconButton(
                                        SimpIcons.ArrowBackIosNew,
                                        Modifier
                                            .size(32.dp),
                                        true,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        contentDescription = stringResource(Res.string.cd_back),
                                    ) {
                                        coroutineScope.launch {
                                            sheetState.hide()
                                            showThirdPartyLibraries = false
                                        }
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    // The pinned copy of the toolbar, frosted: the frost waits for the Devices group to go under it,
    // because an always-on frost dimmed the glow behind the bar into a black band, which is exactly
    // where the glow carries its colour.
    // Captured outside the haze scope — HazeEffectScope is not composable (same move as
    // AlbumScreen's mutedPaletteBg capture).
    val settingBarTint = MaterialTheme.colorScheme.background
    AnimatedVisibility(
        visible = showPinnedBar,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
    ) {
        SettingTopBar(
            onBack = { navController.navigateUp() },
            // The house recipe from AlbumScreen's bars, thinned: ultraThin's built-in tint stacked
            // on this page's dark ground read as a solid lid. 0.3 keeps the blur doing the work and
            // the tint only settling legibility.
            modifier = Modifier.hazeBlur(HazeInput.Sources(hazeState), barBlurStyle(settingBarTint, 0.3f)),
        )
    }
}

/**
 * The page's toolbar. Drawn twice, the way Playlist and Artist draw theirs: once inside the list,
 * where it scrolls away with the content, and once pinned above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(Res.string.settings),
                style = typo().titleMedium,
            )
        },
        navigationIcon = {
            Box(Modifier.padding(horizontal = 5.dp)) {
                RippleIconButton(
                    SimpIcons.ArrowBackIosNew,
                    Modifier.size(32.dp),
                    true,
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = stringResource(Res.string.cd_back),
                ) {
                    onBack()
                }
            }
        },
        windowInsets = windowInsets,
        modifier = modifier,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
    )
}

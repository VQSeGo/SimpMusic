package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.data.type.LibraryType
import com.maxrave.domain.data.type.PlaylistType
import com.maxrave.domain.data.type.RecentlyType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.ui.component.selection.SongSelectionState
import com.maxrave.simpmusic.ui.navigation.destination.list.AlbumDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.ArtistDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.LocalPlaylistDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.PlaylistDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.no_favorite_playlists
import simpmusic.composeapp.generated.resources.no_playlists_downloaded
import simpmusic.composeapp.generated.resources.radio
import simpmusic.composeapp.generated.resources.recently_added
import com.maxrave.domain.mediaservice.handler.PlaylistType as DomainPlaylistType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItem(
    state: LibraryItemState,
    viewModel: LibraryViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
    navController: NavController,
    // Null means this row does not take part in multi-selection; the selection bar lives on the
    // hosting screen, so only a screen that shows one passes a state down.
    selectionState: SongSelectionState? = null,
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var songEntity by remember { mutableStateOf<SongEntity?>(null) }
    val title =
        when (state.type) {
            is LibraryItemType.RecentlyAdded -> stringResource(Res.string.recently_added)
            else -> return
        }
    val noPlaylistTitle =
        when (state.type) {
            LibraryItemType.DownloadedPlaylist -> stringResource(Res.string.no_playlists_downloaded)
            LibraryItemType.FavoritePlaylist -> stringResource(Res.string.no_favorite_playlists)
            is LibraryItemType.RecentlyAdded -> stringResource(Res.string.recently_added)
            else -> return
        }
    Box {
        if (showBottomSheet) {
            NowPlayingBottomSheet(
                onDismiss = {
                    showBottomSheet = false
                    songEntity = null
                },
                navController = navController,
                song = songEntity ?: return,
                onLibraryDelete = {
                    songEntity?.videoId?.let { viewModel.deleteSong(it) }
                },
            )
        }
        Column {
            Row(
                modifier = Modifier.padding(top = 15.dp, start = 10.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = typo().headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(35.dp)
                            .wrapContentHeight(align = Alignment.CenterVertically)
                            .weight(1f)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                animationMode = MarqueeAnimationMode.Immediately,
                            ).focusable(),
                )
            }
            Crossfade(targetState = state.isLoading, label = "Loading") { isLoading ->
                if (!isLoading) {
                    if (state.type is LibraryItemType.RecentlyAdded) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            state.data.filterIsInstance<RecentlyType>().forEach { item ->
                                when (item.objectType()) {
                                    RecentlyType.Type.SONG -> {
                                        SongFullWidthItems(
                                            songEntity = item as SongEntity,
                                            isPlaying = item.videoId == state.type.playingVideoId,
                                            modifier = Modifier,
                                            onMoreClickListener = {
                                                songEntity = item
                                                showBottomSheet = true
                                            },
                                            onClickListener = {
                                                viewModel.setQueueData(
                                                    QueueData.Data(
                                                        listTracks = arrayListOf(item.toTrack()),
                                                        firstPlayedTrack = item.toTrack(),
                                                        playlistId = "RDAMVM${item.videoId}",
                                                        playlistName = "\"${item.title}\" ${runBlocking { getString(Res.string.radio) }}",
                                                        playlistType = DomainPlaylistType.RADIO,
                                                        continuation = null,
                                                    ),
                                                )
                                                viewModel.loadMediaItem(
                                                    item,
                                                    type = Config.SONG_CLICK,
                                                    index = 0,
                                                )
                                            },
                                            selectionMode = selectionState?.isActive == true,
                                            isSelected = selectionState?.isSelected(item.videoId) == true,
                                            onLongClick =
                                                selectionState?.let { sel -> { id: String -> sel.start(id) } },
                                            onSelectToggle =
                                                selectionState?.let { sel -> { id: String -> sel.toggle(id) } },
                                            onAddToQueue = { videoId ->
                                                sharedViewModel.addListToQueue(
                                                    arrayListOf(item.toTrack()),
                                                )
                                            },
                                        )
                                    }

                                    RecentlyType.Type.ARTIST -> {
                                        ArtistFullWidthItems(
                                            data = item as? ArtistEntity ?: return@forEach,
                                            onClickListener = {
                                                navController.navigate(
                                                    ArtistDestination(
                                                        channelId = item.channelId,
                                                    ),
                                                )
                                            },
                                        )
                                    }

                                    else -> {
                                        if (item is PlaylistType) {
                                            PlaylistFullWidthItems(
                                                data = item,
                                                onClickListener = {
                                                    when (item) {
                                                        is AlbumEntity -> {
                                                            navController.navigate(
                                                                AlbumDestination(
                                                                    item.browseId,
                                                                ),
                                                            )
                                                        }

                                                        is PlaylistEntity -> {
                                                            navController.navigate(
                                                                PlaylistDestination(
                                                                    item.id,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (state.data.isNotEmpty()) {
                            LazyRow {
                                items(items = state.data) { item ->
                                    Box(modifier = Modifier.animateItem()) {
                                        HomeItemContentPlaylist(
                                            onClick = {
                                                when (item) {
                                                    is LocalPlaylistEntity -> {
                                                        navController.navigate(
                                                            LocalPlaylistDestination(
                                                                item.id,
                                                            ),
                                                        )
                                                    }

                                                    is PlaylistsResult -> {
                                                        navController.navigate(
                                                            PlaylistDestination(
                                                                item.browseId,
                                                                isYourYouTubePlaylist = true,
                                                            ),
                                                        )
                                                    }

                                                    is AlbumEntity -> {
                                                        navController.navigate(
                                                            AlbumDestination(
                                                                item.browseId,
                                                            ),
                                                        )
                                                    }

                                                    is PlaylistEntity -> {
                                                        navController.navigate(
                                                            PlaylistDestination(
                                                                item.id,
                                                            ),
                                                        )
                                                    }
                                                }
                                            },
                                            data = item as? PlaylistType ?: return@items,
                                            thumbSize = 125.dp,
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(130.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(noPlaylistTitle, style = typo().bodyMedium)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CenterLoadingBox(Modifier.wrapContentSize())
                    }
                }
            }
        }
    }
}

sealed class LibraryItemType {
    data class YouTubePlaylist(
        val isLoggedIn: Boolean,
        val onReload: () -> Unit = {},
    ) : LibraryItemType()

    data class LocalPlaylist(
        // Create new local playlist
        val onAddClick: (String) -> Unit,
    ) : LibraryItemType()

    data object FavoritePlaylist : LibraryItemType()

    data object DownloadedPlaylist : LibraryItemType()

    data class RecentlyAdded(
        val playingVideoId: String,
    ) : LibraryItemType()
}

data class LibraryItemState(
    val type: LibraryItemType,
    val data: List<LibraryType>,
    val isLoading: Boolean = true,
)
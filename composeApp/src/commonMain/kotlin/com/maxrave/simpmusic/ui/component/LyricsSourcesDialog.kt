package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.getPlatform
import com.maxrave.simpmusic.ui.icon.DragHandle
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.utils.LyricsSource
import com.maxrave.simpmusic.utils.LyricsSourceEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.better_lyrics
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.lrclib
import simpmusic.composeapp.generated.resources.lyrics_source_needs_spotify_login
import simpmusic.composeapp.generated.resources.lyrics_sources
import simpmusic.composeapp.generated.resources.lyrics_sources_all_off
import simpmusic.composeapp.generated.resources.lyrics_sources_description
import simpmusic.composeapp.generated.resources.save
import simpmusic.composeapp.generated.resources.simpmusic_lyrics
import simpmusic.composeapp.generated.resources.spotify
import simpmusic.composeapp.generated.resources.youtube_transcript

val LyricsSource.title: StringResource
    get() =
        when (this) {
            LyricsSource.SIMPMUSIC -> Res.string.simpmusic_lyrics
            LyricsSource.SPOTIFY -> Res.string.spotify
            LyricsSource.LRCLIB -> Res.string.lrclib
            LyricsSource.BETTER_LYRICS -> Res.string.better_lyrics
            LyricsSource.YOUTUBE -> Res.string.youtube_transcript
        }

/** The sources switched on, in the order they are asked: the subtitle of the Lyrics sources row. */
@Composable
fun lyricsSourcesSummary(sources: List<LyricsSourceEntry>): String {
    // Not read yet: an empty line for a moment rather than "all off".
    if (sources.isEmpty()) return ""
    val on = sources.filter { it.enabled }
    if (on.isEmpty()) return stringResource(Res.string.lyrics_sources_all_off)
    return on.map { stringResource(it.source.title) }.joinToString(" → ")
}

/**
 * Edits the lyrics source queue: rows dragged into the order the sources are asked in (with a mouse at
 * once, on a touch screen after a long press), each with its own switch. Nothing is written until
 * Save, so the queue is asked again once rather than after every drag.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LyricsSourcesDialog(
    sources: List<LyricsSourceEntry>,
    spotifyLoggedIn: Boolean,
    onSave: (List<LyricsSourceEntry>) -> Unit,
    onDismiss: () -> Unit,
    containerColor: Color = AlertDialogDefaults.containerColor,
) {
    // One list for the dialog's life: the drag state below keeps the first move lambda it is given.
    // Callers open the dialog only once the queue is read, so [sources] is never the empty placeholder.
    val draft = remember { mutableStateListOf(*sources.toTypedArray()) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val dragDropState =
        rememberDragDropState(lazyListState) { from, to ->
            if (from in draft.indices && to in draft.indices) draft.add(to, draft.removeAt(from))
        }
    var overscrollJob by remember { mutableStateOf<Job?>(null) }
    val isDesktop = getPlatform() == Platform.Desktop

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = {
            Text(text = stringResource(Res.string.lyrics_sources), style = typo().titleSmall)
        },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.lyrics_sources_description),
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 8.dp))
                LazyColumn(
                    state = lazyListState,
                    modifier =
                        Modifier
                            .heightIn(max = 420.dp)
                            .pointerInput(isDesktop) {
                                val onDrag: (PointerInputChange, Offset) -> Unit = { change, offset ->
                                    change.consume()
                                    dragDropState.onDrag(offset = offset)
                                    if (overscrollJob?.isActive != true) {
                                        dragDropState
                                            .checkForOverScroll()
                                            .takeIf { it != 0f }
                                            ?.let {
                                                overscrollJob =
                                                    coroutineScope.launch {
                                                        dragDropState.state.animateScrollBy(
                                                            it * 1.3f,
                                                            tween(easing = FastOutLinearInEasing),
                                                        )
                                                    }
                                            }
                                            ?: run { overscrollJob?.cancel() }
                                    }
                                }
                                val onDragStart: (Offset) -> Unit = { dragDropState.onDragStart(it) }
                                val onDragEnd: () -> Unit = {
                                    dragDropState.onDragInterrupted(true)
                                    overscrollJob?.cancel()
                                }
                                val onDragCancel: () -> Unit = {
                                    dragDropState.onDragInterrupted()
                                    overscrollJob?.cancel()
                                }
                                if (isDesktop) {
                                    // A mouse has no scroll gesture to steal: the row moves at once.
                                    detectDragGestures(
                                        onDragStart = onDragStart,
                                        onDragEnd = onDragEnd,
                                        onDragCancel = onDragCancel,
                                        onDrag = onDrag,
                                    )
                                } else {
                                    // On touch a plain drag is the list's scroll: a long press first.
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = onDragStart,
                                        onDragEnd = onDragEnd,
                                        onDragCancel = onDragCancel,
                                        onDrag = onDrag,
                                    )
                                }
                            },
                ) {
                    itemsIndexed(draft, key = { _, entry -> entry.source.id }) { index, entry ->
                        DraggableItem(dragDropState, index, Modifier) { dragging ->
                            LyricsSourceRow(
                                entry = entry,
                                needsLogin = entry.source == LyricsSource.SPOTIFY && !spotifyLoggedIn,
                                dragging = dragging,
                                onEnabledChange = { enabled ->
                                    val at = draft.indexOfFirst { it.source == entry.source }
                                    if (at >= 0) draft[at] = draft[at].copy(enabled = enabled)
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(draft.toList())
                    onDismiss()
                },
            ) {
                Text(text = stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun LyricsSourceRow(
    entry: LyricsSourceEntry,
    needsLogin: Boolean,
    dragging: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    RoundedCornerShape(12.dp),
                ).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SimpIcons.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.source.title),
                style = typo().bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (needsLogin) {
                Text(
                    text = stringResource(Res.string.lyrics_source_needs_spotify_login),
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = entry.enabled, onCheckedChange = onEnabledChange)
    }
}

package com.maxrave.simpmusic.expect.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** [flingBehavior]: the list's own snap fling, run once the thumb is let go so a drag ends on an item. */
@Composable
expect fun HorizontalScrollBar(
    modifier: Modifier,
    scrollState: LazyListState,
    flingBehavior: FlingBehavior? = null,
)

/** [flingBehavior]: the grid's own snap fling, run once the thumb is let go so a drag ends on a column. */
@Composable
expect fun HorizontalScrollBar(
    modifier: Modifier,
    scrollState: LazyGridState,
    flingBehavior: FlingBehavior? = null,
)

/**
 * A carousel moves a card at a time and exposes only which card is current, so the thumb moves a card
 * at a time too: [itemCount] cards long, one card wide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
expect fun HorizontalScrollBar(
    modifier: Modifier,
    scrollState: CarouselState,
    itemCount: Int,
)

/**
 * Space the app shell takes at the bottom of the page: the narrow-window desktop layout puts a bottom
 * bar (capsule player and tabs) over the screens, so a bar that ran to the window edge would end
 * underneath it. The shell (App.kt) provides it once; [VerticalScrollBar] adds it to its own bottom
 * padding, so screens do not repeat it. It is zero in the wide layout, where nothing sits below a page,
 * and a popup or sheet that has its own edges resets it to zero.
 */
val LocalScrollbarBottomInset = compositionLocalOf { 0.dp }

/**
 * Desktop only: a bar along the right edge of a vertical list, dragged by its thumb or held on its
 * track to page. Put it right after the list, in a parent that stacks its children (Box, Crossfade,
 * AnimatedContent): it fills that parent and takes input only on the bar itself, so the wheel and
 * clicks anywhere else reach the list. [modifier] carries the list's top and bottom padding, so the
 * track starts below the top bar; the bottom comes from [LocalScrollbarBottomInset].
 */
@Composable
expect fun VerticalScrollBar(
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
)

@Composable
expect fun VerticalScrollBar(
    scrollState: LazyGridState,
    modifier: Modifier = Modifier,
)

@Composable
expect fun VerticalScrollBar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
)

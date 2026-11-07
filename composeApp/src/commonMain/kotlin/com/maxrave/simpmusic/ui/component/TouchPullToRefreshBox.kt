package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.getPlatform

/**
 * Pull to refresh for touch screens. On Desktop a mouse wheel or touchpad scrolling past the top
 * would pull the indicator out and leave it hanging, so there it is a plain [Box].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    state: PullToRefreshState,
    indicator: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (getPlatform() == Platform.Desktop) {
        Box(modifier = modifier, content = content)
    } else {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            state = state,
            indicator = indicator,
            content = content,
        )
    }
}

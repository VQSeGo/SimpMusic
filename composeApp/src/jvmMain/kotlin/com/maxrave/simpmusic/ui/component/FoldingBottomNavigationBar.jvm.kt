package com.maxrave.simpmusic.ui.component

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlin.reflect.KClass

@Composable
actual fun FoldingBottomNavigationBar(
    startDestination: Any,
    navController: NavController,
    viewModel: SharedViewModel,
    isScrolledToTop: Boolean,
    showAnalyticsTab: Boolean,
    showMixForYouTab: Boolean,
    onOpenNowPlaying: () -> Unit,
    miniPlayerCovered: Boolean,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit
) {
}
package com.maxrave.simpmusic.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * The route each back stack entry was opened with, by entry id. The navigation library cannot give a
 * route object back from an entry for an arbitrary class, so the screens opened through
 * [navigateOnce] are remembered here, and entries that left the stack are dropped on the next call.
 * Touched from the main thread only, like the navigation calls themselves.
 */
private val openedRoutes = HashMap<String, Any>()

/**
 * Whether [route] is the screen already on top of the back stack: the same destination opened with
 * the same arguments. A different album, or the same album with something else opened over it, is not.
 */
fun NavController.isOnTop(route: Any): Boolean {
    val entry = currentBackStackEntry ?: return false
    if (!entry.destination.hasRoute(route::class)) return false
    return openedRoutes[entry.id] == route
}

/**
 * [NavController.navigate] that does nothing when [route] is already the screen on top.
 *
 * A second tap on a track's album name, on an artist in the player or on Settings before the first
 * screen has finished sliding in used to push another copy: another entry animation and another
 * Back press to get out. Only a repeat of the top screen is ignored; going from album A to an artist
 * and back to album A is a different screen on top, so it still opens.
 *
 * Use this for every plain "open this screen" navigation. Tab switches keep their own rules
 * (`popUpTo`, `restoreState`) in the navigation bars and the desktop sidebar.
 */
fun NavController.navigateOnce(
    route: Any,
    builder: NavOptionsBuilder.() -> Unit = {},
) {
    if (isOnTop(route)) return
    val before = currentBackStackEntry
    navigate(route, builder)
    val opened = currentBackStackEntry
    if (opened != null && opened !== before) openedRoutes[opened.id] = route
    val onStack = currentBackStack.value.mapTo(HashSet()) { it.id }
    openedRoutes.keys.retainAll(onStack)
}

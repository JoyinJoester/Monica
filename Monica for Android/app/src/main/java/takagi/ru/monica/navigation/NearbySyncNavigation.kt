package takagi.ru.monica.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import takagi.ru.monica.ui.LocalAnimatedVisibilityScope
import takagi.ru.monica.ui.navigation.easyNotesScreenEnter
import takagi.ru.monica.ui.navigation.easyNotesScreenExit
import takagi.ru.monica.ui.screens.LanSyncScannerScreen
import takagi.ru.monica.ui.screens.LanSyncScreen
import takagi.ru.monica.lansync.DeviceSyncCoordinator
import takagi.ru.monica.ui.password.NearbySyncMenuActions

@Composable
internal fun rememberNearbySyncMenu(navController: NavHostController): NearbySyncMenuActions {
    val context = LocalContext.current
    val coordinator = remember(context) { DeviceSyncCoordinator.get(context) }
    return remember(coordinator, navController) {
        NearbySyncMenuActions(coordinator.state, coordinator::syncNow) {
            navController.navigate(Screen.LanSync.route) { launchSingleTop = true }
        }
    }
}

/** A separate scanner destination keeps both toolbar and system Back on the pairing page. */
fun NavGraphBuilder.nearbySyncDestinations(navController: NavHostController) {
    composable(Screen.LanSync.route,
        enterTransition = { easyNotesScreenEnter() }, exitTransition = { easyNotesScreenExit() },
        popEnterTransition = { easyNotesScreenEnter() }, popExitTransition = { easyNotesScreenExit() }) {
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
            LanSyncScreen(onNavigateBack = { navController.popBackStack() },
                onScanPairingQr = { navController.navigate(Screen.LanSyncScanner.route) { launchSingleTop = true } })
        }
    }
    composable(Screen.LanSyncScanner.route,
        enterTransition = { easyNotesScreenEnter() }, exitTransition = { easyNotesScreenExit() },
        popEnterTransition = { easyNotesScreenEnter() }, popExitTransition = { easyNotesScreenExit() }) {
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
            LanSyncScannerScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

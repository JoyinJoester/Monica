package takagi.ru.monica.ui.password

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import takagi.ru.monica.R
import takagi.ru.monica.lansync.DeviceSyncState

internal val LocalNearbySyncMenu = staticCompositionLocalOf<NearbySyncMenuActions?> { null }

internal class NearbySyncMenuActions(
    val state: StateFlow<DeviceSyncState>,
    private val syncNow: () -> Unit,
    private val openStatus: () -> Unit,
) {
    fun open() {
        // Read the latest state: pairing/locking may have changed after the menu opened.
        val current = state.value
        if (current.peers.isEmpty() || current.pairing || !current.appVisible || current.phase == "locked") return
        openStatus()
        if (!current.hasSyncInProgress) syncNow()
    }
}

private val DeviceSyncState.hasSyncInProgress: Boolean
    get() = manualSync || activeConnections > 0 || passkeyReview != null ||
        (error == null && phase == "preparing")

@Composable
internal fun NearbySyncTopActionsMenuItem(onDismissMenu: () -> Unit) {
    val actions = LocalNearbySyncMenu.current ?: return
    val state by actions.state.collectAsStateWithLifecycle()
    if (state.peers.isEmpty()) return
    DropdownMenuItem(
        text = { Text(stringResource(if (state.hasSyncInProgress) R.string.nearby_menu_progress else R.string.nearby_menu_sync)) },
        leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
        enabled = state.appVisible && !state.pairing && state.phase != "locked",
        modifier = Modifier.testTag("nearby_sync_menu"),
        onClick = {
            onDismissMenu()
            actions.open()
        },
    )
}

package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.repository.MdbxStructureNode

@Composable
internal fun DatabaseManagerActionBar(current: DatabaseManagerPane, other: DatabaseManagerPane,
    active: Int, dual: Boolean, busy: Boolean, progress: Float,
    onBack: () -> Unit, onForward: () -> Unit, onCreate: () -> Unit, onSwitch: () -> Unit,
    onParent: () -> Unit, onCopy: () -> Unit, onMove: () -> Unit, onChooseDestination: () -> Unit,
    onMore: () -> Unit, onClear: () -> Unit) {
    val side = stringResource(if (active == 0) R.string.manager_left_pane else R.string.manager_right_pane)
    val copy = stringResource(if (active == 0) R.string.manager_copy_right else R.string.manager_copy_left)
    val move = stringResource(if (active == 0) R.string.manager_move_right else R.string.manager_move_left)
    val targetReady = dual && other.isReady
    Surface(tonalElevation = 2.dp, modifier = Modifier.testTag("manager-action-bar")) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Text(if (current.isSelecting) stringResource(R.string.manager_focused_selection, side, current.selected.size)
                else stringResource(R.string.manager_focused_location, side, current.snapshot?.title ?: stringResource(R.string.manager_databases)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).testTag("manager-focus-label"),
                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                @Composable fun action(tag: String, icon: ImageVector, label: String, enabled: Boolean, click: () -> Unit) {
                    TextButton(onClick = click, enabled = enabled && !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("manager-action-$tag"), contentPadding = PaddingValues(2.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, label, Modifier.size(20.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (current.isSelecting) {
                    if (targetReady) {
                        action("copy", Icons.Default.ContentCopy, copy, current.isReady && current.selected.isNotEmpty(), onCopy)
                        action("move", Icons.Default.DriveFileMove, move, current.isReady && current.selected.isNotEmpty(), onMove)
                    } else action("destination", Icons.Default.FolderOpen, stringResource(R.string.manager_choose_destination), current.isReady && current.selected.isNotEmpty(), onChooseDestination)
                    action("more", Icons.Default.MoreHoriz, stringResource(R.string.more_options), current.isReady, onMore)
                    action("clear", Icons.Default.Close, stringResource(R.string.cancel), true, onClear)
                } else {
                    action("back", Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.manager_history_back), current.canGoBack, onBack)
                    action("forward", Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.manager_history_forward), current.canGoForward, onForward)
                    action("new", Icons.Default.CreateNewFolder, stringResource(R.string.manager_new_folder_short), current.isReady, onCreate)
                    action("switch", Icons.Default.SwapHoriz, stringResource(R.string.manager_switch_pane), dual, onSwitch)
                    action("parent", Icons.Default.ArrowUpward, stringResource(R.string.manager_parent), current.databaseKey != null, onParent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatabaseManagerActionsSheet(title: String, paneIndex: Int, rows: List<MdbxStructureNode>, dual: Boolean,
    canTransfer: Boolean, onDismiss: () -> Unit, onCopy: () -> Unit, onMove: () -> Unit, onChooseDestination: () -> Unit,
    onOpen: () -> Unit, onRename: () -> Unit, onSelect: () -> Unit, onSelectAll: () -> Unit, onClear: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 16.dp)
            .testTag("manager-actions-sheet")) {
            val side = stringResource(if (paneIndex == 0) R.string.manager_left_pane else R.string.manager_right_pane)
            Text(stringResource(R.string.manager_focused_selection, side, rows.size),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
            Text(rows.singleOrNull()?.name ?: title, style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            @Composable fun action(tag: String, label: String, icon: ImageVector, enabled: Boolean = true, click: () -> Unit) {
                TextButton(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("manager-menu-$tag"), contentPadding = PaddingValues(12.dp)) {
                    Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(16.dp))
                    Text(label, modifier = Modifier.weight(1f))
                }
            }
            if (rows.isNotEmpty() && (!dual || !canTransfer)) action("destination", stringResource(R.string.manager_choose_destination), Icons.Default.FolderOpen, click = onChooseDestination)
            action("copy", stringResource(if (paneIndex == 0) R.string.manager_copy_right else R.string.manager_copy_left), Icons.Default.ContentCopy,
                canTransfer && rows.isNotEmpty(), onCopy)
            action("move", stringResource(if (paneIndex == 0) R.string.manager_move_right else R.string.manager_move_left), Icons.Default.DriveFileMove,
                canTransfer && rows.isNotEmpty(), onMove)
            if (rows.size == 1) {
                action("open", stringResource(R.string.manager_open_item), Icons.Default.OpenInNew, click = onOpen)
                action("rename", stringResource(R.string.rename_category), Icons.Default.Edit, click = onRename)
                action("select", stringResource(R.string.manager_select), Icons.Default.CheckCircle, click = onSelect)
            }
            action("all", stringResource(R.string.select_all), Icons.Default.SelectAll, click = onSelectAll)
            action("clear", stringResource(R.string.manager_clear_selection), Icons.Default.Deselect, click = onClear)
        }
    }
}

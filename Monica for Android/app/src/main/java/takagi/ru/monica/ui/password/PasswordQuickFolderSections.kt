package takagi.ru.monica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.MdbxSyncStatus
import takagi.ru.monica.viewmodel.CategoryFilter

internal fun LocalMdbxDatabase.mdbxPathShouldFlushPendingUpload(): Boolean =
    lastSyncStatus == MdbxSyncStatus.PENDING_UPLOAD.name

internal data class MdbxPathSyncState(
    val pendingCount: Int,
    val isSyncing: Boolean,
    val onSync: () -> Unit
)

internal fun LocalMdbxDatabase.mdbxPathPendingSyncCount(): Int {
    val pending = when (runCatching { MdbxSyncStatus.valueOf(lastSyncStatus) }.getOrNull()) {
        MdbxSyncStatus.PENDING_UPLOAD,
        MdbxSyncStatus.REMOTE_CHANGED,
        MdbxSyncStatus.CONFLICT,
        MdbxSyncStatus.FAILED -> true
        else -> false
    }
    return if (pending) 1 else 0
}

@Composable
internal fun PasswordQuickFolderBreadcrumbBanner(
    breadcrumbs: List<PasswordQuickFolderBreadcrumb>,
    currentFilter: CategoryFilter,
    onNavigate: (CategoryFilter) -> Unit,
    mdbxSyncState: MdbxPathSyncState? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 220)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mdbxSyncState != null) {
            MdbxPathSyncActions(state = mdbxSyncState)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                breadcrumbs.forEachIndexed { index, crumb ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (crumb.isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f)
                                }
                            )
                            .clickable(enabled = !crumb.isCurrent) {
                                if (currentFilter != crumb.targetFilter) {
                                    onNavigate(crumb.targetFilter)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = crumb.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (crumb.isCurrent) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                    if (index != breadcrumbs.lastIndex) {
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MdbxPathSyncActions(state: MdbxPathSyncState) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 220)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = state.pendingCount > 0,
            enter = expandHorizontally(
                expandFrom = Alignment.End,
                animationSpec = tween(durationMillis = 220)
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.End,
                animationSpec = tween(durationMillis = 180)
            ) + fadeOut(animationSpec = tween(durationMillis = 140))
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .width(104.dp)
                    .height(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "未同步${state.pendingCount}条",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val spin by rememberInfiniteTransition(label = "mdbx-path-sync-spin")
            .animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "mdbx-path-sync-rotation"
            )
        val rotation = if (state.isSyncing) spin else 0f

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.size(36.dp)
        ) {
            IconButton(
                onClick = state.onSync,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "同步 MDBX 数据库",
                    modifier = Modifier
                        .size(19.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        }
    }
}

@Composable
internal fun PasswordQuickFolderShortcutsSection(
    shortcuts: List<PasswordQuickFolderShortcut>,
    currentFilter: CategoryFilter,
    useM3CardStyle: Boolean,
    onNavigate: (CategoryFilter) -> Unit
) {
    if (useM3CardStyle) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shortcuts.forEach { shortcut ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (currentFilter != shortcut.targetFilter) {
                                onNavigate(shortcut.targetFilter)
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (shortcut.isBack) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (shortcut.isBack) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.Default.Folder
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (shortcut.isBack) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = shortcut.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (shortcut.subtitle.isNotBlank()) {
                                Text(
                                    text = shortcut.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!shortcut.isBack) {
                                Text(
                                    text = stringResource(
                                        R.string.password_list_quick_folder_count,
                                        shortcut.passwordCount ?: 0
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Icon(
                            imageVector = if (shortcut.isBack) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = if (shortcut.isBack) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shortcuts.forEach { shortcut ->
                Card(
                    modifier = Modifier
                        .size(width = 182.dp, height = 74.dp)
                        .clickable {
                            if (currentFilter != shortcut.targetFilter) {
                                onNavigate(shortcut.targetFilter)
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (shortcut.isBack) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (shortcut.isBack) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.Default.Folder
                            },
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (shortcut.isBack) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (!shortcut.isBack) {
                                Text(
                                    text = stringResource(
                                        R.string.password_list_quick_folder_count,
                                        shortcut.passwordCount ?: 0
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = shortcut.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (shortcut.subtitle.isNotBlank()) {
                                Text(
                                    text = shortcut.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

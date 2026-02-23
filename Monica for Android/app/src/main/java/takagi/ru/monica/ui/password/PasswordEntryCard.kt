package takagi.ru.monica.ui.password

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.sync.SyncStatus
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.components.SyncStatusIcon

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class
)
@Composable
fun PasswordEntryCard(
    entry: PasswordEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleFavorite: (() -> Unit)? = null,
    onToggleGroupCover: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    canSetGroupCover: Boolean = false,
    isInExpandedGroup: Boolean = false,
    isSingleCard: Boolean = false,
    iconCardsEnabled: Boolean = false,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL,
    enableSharedBounds: Boolean = true
) {
    val displayTitle = entry.title.ifBlank { stringResource(R.string.untitled) }
    val sharedTransitionScope = takagi.ru.monica.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = takagi.ru.monica.ui.LocalAnimatedVisibilityScope.current
    val reduceAnimations = takagi.ru.monica.ui.LocalReduceAnimations.current
    var sharedModifier: Modifier = Modifier
    if (enableSharedBounds && !reduceAnimations && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            sharedModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "password_card_${entry.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.ScaleToBounds()
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(sharedModifier),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        },
        elevation = if (isSingleCard) {
            CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
        } else if (isInExpandedGroup) {
            CardDefaults.cardElevation(defaultElevation = 2.dp)
        } else {
            CardDefaults.cardElevation()
        },
        shape = if (isSingleCard) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(if (isSingleCard) 20.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconCardsEnabled) {
                val simpleIcon = if (entry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE) {
                    takagi.ru.monica.ui.icons.rememberSimpleIconBitmap(
                        slug = entry.customIconValue,
                        tintColor = MaterialTheme.colorScheme.primary,
                        enabled = true
                    )
                } else null
                val uploadedIcon = if (entry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_UPLOADED) {
                    takagi.ru.monica.ui.icons.rememberUploadedPasswordIcon(entry.customIconValue)
                } else null
                val appIcon = if (!entry.appPackageName.isNullOrBlank()) {
                    takagi.ru.monica.autofill.ui.rememberAppIcon(entry.appPackageName)
                } else null
                val autoMatchedSimpleIcon = takagi.ru.monica.ui.icons.rememberAutoMatchedSimpleIcon(
                    website = entry.website,
                    title = entry.title,
                    appPackageName = entry.appPackageName,
                    tintColor = MaterialTheme.colorScheme.primary,
                    enabled = entry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_NONE
                )

                val favicon = if (entry.website.isNotBlank()) {
                    takagi.ru.monica.autofill.ui.rememberFavicon(
                        url = entry.website,
                        enabled = autoMatchedSimpleIcon.resolved && autoMatchedSimpleIcon.slug == null
                    )
                } else null

                if (simpleIcon != null) {
                    Image(
                        bitmap = simpleIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp).padding(2.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                } else if (uploadedIcon != null) {
                    Image(
                        bitmap = uploadedIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp).padding(2.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                } else if (autoMatchedSimpleIcon.bitmap != null) {
                    Image(
                        bitmap = autoMatchedSimpleIcon.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp).padding(2.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                } else if (favicon != null) {
                    Image(
                        bitmap = favicon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp).padding(2.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                } else if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp).padding(2.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                } else {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (isSingleCard) 8.dp else 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayTitle,
                        style = if (isSingleCard) {
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (entry.isBitwardenEntry()) {
                            val syncStatus = when {
                                entry.hasPendingBitwardenSync() -> SyncStatus.PENDING
                                else -> SyncStatus.SYNCED
                            }
                            SyncStatusIcon(status = syncStatus, size = 16.dp)
                        } else if (entry.isKeePassEntry()) {
                            Icon(
                                Icons.Default.VpnKey,
                                contentDescription = "KeePass",
                                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (!isSelectionMode && onToggleGroupCover != null) {
                            IconButton(
                                onClick = onToggleGroupCover,
                                modifier = Modifier.size(36.dp),
                                enabled = canSetGroupCover
                            ) {
                                Icon(
                                    if (entry.isGroupCover) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = if (entry.isGroupCover) "Remove cover" else "Set as cover",
                                    tint = if (entry.isGroupCover) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else if (canSetGroupCover) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (!isSelectionMode && onToggleFavorite != null) {
                            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    if (entry.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(R.string.favorite),
                                    tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isSelectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onClick() }
                            )
                        }
                    }
                }

                if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && entry.website.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isSingleCard) 8.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(if (isSingleCard) 18.dp else 16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = entry.website,
                            style = if (isSingleCard) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (passwordCardDisplayMode != takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY && entry.username.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isSingleCard) 8.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(if (isSingleCard) 18.dp else 16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = entry.username,
                            style = if (isSingleCard) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val additionalInfo = buildAdditionalInfoPreview(entry)
                if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && additionalInfo.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(if (isSingleCard) 10.dp else 8.dp),
                        color = if (isSingleCard) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (isSingleCard) 14.dp else 12.dp,
                                    vertical = if (isSingleCard) 10.dp else 8.dp
                                ),
                            horizontalArrangement = Arrangement.spacedBy(if (isSingleCard) 20.dp else 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            additionalInfo.take(2).forEach { info ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(if (isSingleCard) 6.dp else 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        info.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isSingleCard) 16.dp else 14.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = info.text,
                                        style = if (isSingleCard) {
                                            MaterialTheme.typography.labelMedium
                                        } else {
                                            MaterialTheme.typography.labelSmall
                                        },
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
}

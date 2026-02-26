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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.data.UnmatchedIconHandlingStrategy
import takagi.ru.monica.bitwarden.sync.SyncStatus
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.components.SyncStatusIcon
import takagi.ru.monica.ui.icons.UnmatchedIconFallback
import takagi.ru.monica.ui.icons.shouldShowFallbackSlot

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
    unmatchedIconHandlingStrategy: UnmatchedIconHandlingStrategy = UnmatchedIconHandlingStrategy.DEFAULT_ICON,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL,
    passwordCardDisplayFields: List<PasswordCardDisplayField> = PasswordCardDisplayField.DEFAULT_ORDER,
    showAuthenticator: Boolean = false,
    hideOtherContentWhenAuthenticator: Boolean = false,
    totpTimeOffsetSeconds: Int = 0,
    smoothAuthenticatorProgress: Boolean = true,
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
                } else if (shouldShowFallbackSlot(unmatchedIconHandlingStrategy)) {
                    UnmatchedIconFallback(
                        strategy = unmatchedIconHandlingStrategy,
                        primaryText = entry.website,
                        secondaryText = entry.title,
                        defaultIcon = Icons.Default.Key,
                        iconSize = 40.dp
                    )
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

                val authenticatorState = if (showAuthenticator) {
                    rememberPasswordAuthenticatorDisplayState(
                        authenticatorKey = entry.authenticatorKey,
                        timeOffsetSeconds = totpTimeOffsetSeconds,
                        smoothProgress = smoothAuthenticatorProgress
                    )
                } else {
                    null
                }
                val shouldHideDisplayLines = hideOtherContentWhenAuthenticator && authenticatorState != null
                val displayLines = if (
                    passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY || shouldHideDisplayLines
                ) {
                    emptyList()
                } else {
                    resolvePasswordCardDisplayLines(entry, passwordCardDisplayFields).take(3)
                }
                displayLines.forEach { line ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isSingleCard) 8.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            line.icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (isSingleCard) 18.dp else 16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = line.text,
                            style = if (isSingleCard) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (showAuthenticator) {
                    if (authenticatorState != null) {
                        PasswordAuthenticatorInlineRow(
                            state = authenticatorState,
                            isSingleCard = isSingleCard,
                            smoothProgress = smoothAuthenticatorProgress
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordAuthenticatorInlineRow(
    state: PasswordAuthenticatorDisplayState,
    isSingleCard: Boolean,
    smoothProgress: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isSingleCard) 8.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(if (isSingleCard) 18.dp else 16.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
            Text(
                text = state.code,
                style = if (isSingleCard) {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                },
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            state.remainingSeconds?.let { remaining ->
                Text(
                    text = stringResource(R.string.password_card_authenticator_seconds, remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        state.progress?.let { progress ->
            val animatedProgress = if (smoothProgress) {
                animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(durationMillis = 80, easing = LinearEasing),
                    label = "password_auth_progress"
                ).value
            } else {
                progress
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

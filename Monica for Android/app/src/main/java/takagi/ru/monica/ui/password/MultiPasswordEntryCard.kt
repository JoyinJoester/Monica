package takagi.ru.monica.ui.password

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.UnmatchedIconHandlingStrategy
import takagi.ru.monica.ui.icons.UnmatchedIconFallback
import takagi.ru.monica.ui.icons.shouldShowFallbackSlot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MultiPasswordEntryCard(
    passwords: List<PasswordEntry>,
    onClick: (PasswordEntry) -> Unit,
    onCardClick: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    onToggleFavorite: ((PasswordEntry) -> Unit)? = null,
    onToggleGroupCover: ((PasswordEntry) -> Unit)? = null,
    isSelectionMode: Boolean = false,
    selectedPasswords: Set<Long> = emptySet(),
    canSetGroupCover: Boolean = false,
    hasGroupCover: Boolean = false,
    isInExpandedGroup: Boolean = false,
    iconCardsEnabled: Boolean = false,
    unmatchedIconHandlingStrategy: UnmatchedIconHandlingStrategy = UnmatchedIconHandlingStrategy.DEFAULT_ICON,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL,
    passwordCardDisplayFields: List<PasswordCardDisplayField> = PasswordCardDisplayField.DEFAULT_ORDER,
    showAuthenticator: Boolean = false,
    hideOtherContentWhenAuthenticator: Boolean = false,
    totpTimeOffsetSeconds: Int = 0,
    smoothAuthenticatorProgress: Boolean = true
) {
    val firstEntry = passwords.first()
    val firstEntryTitle = firstEntry.title.ifBlank { stringResource(R.string.untitled) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onCardClick?.invoke() },
                onLongClick = onLongClick
            ),
        colors = if (passwords.any { selectedPasswords.contains(it.id) }) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        },
        elevation = if (isInExpandedGroup) {
            CardDefaults.cardElevation(defaultElevation = 2.dp)
        } else {
            CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
        },
        shape = RoundedCornerShape(if (isInExpandedGroup) 12.dp else 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isInExpandedGroup) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconCardsEnabled) {
                    val simpleIcon = if (firstEntry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE) {
                        takagi.ru.monica.ui.icons.rememberSimpleIconBitmap(
                            slug = firstEntry.customIconValue,
                            tintColor = MaterialTheme.colorScheme.primary,
                            enabled = true
                        )
                    } else {
                        null
                    }
                    val uploadedIcon = if (firstEntry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_UPLOADED) {
                        takagi.ru.monica.ui.icons.rememberUploadedPasswordIcon(firstEntry.customIconValue)
                    } else {
                        null
                    }
                    val appIcon = if (!firstEntry.appPackageName.isNullOrBlank()) {
                        takagi.ru.monica.autofill.ui.rememberAppIcon(firstEntry.appPackageName)
                    } else null
                    val autoMatchedSimpleIcon = takagi.ru.monica.ui.icons.rememberAutoMatchedSimpleIcon(
                        website = firstEntry.website,
                        title = firstEntry.title,
                        appPackageName = firstEntry.appPackageName,
                        tintColor = MaterialTheme.colorScheme.primary,
                        enabled = firstEntry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_NONE
                    )

                    val favicon = if (firstEntry.website.isNotBlank()) {
                        takagi.ru.monica.autofill.ui.rememberFavicon(
                            url = firstEntry.website,
                            enabled = autoMatchedSimpleIcon.resolved && autoMatchedSimpleIcon.slug == null
                        )
                    } else null

                    if (simpleIcon != null) {
                        Image(
                            bitmap = simpleIcon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(32.dp).padding(1.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else if (uploadedIcon != null) {
                        Image(
                            bitmap = uploadedIcon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(32.dp).padding(1.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else if (autoMatchedSimpleIcon.bitmap != null) {
                        Image(
                            bitmap = autoMatchedSimpleIcon.bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(32.dp).padding(1.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else if (favicon != null) {
                        Image(
                            bitmap = favicon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(32.dp).padding(1.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(32.dp).padding(1.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else if (shouldShowFallbackSlot(unmatchedIconHandlingStrategy)) {
                        UnmatchedIconFallback(
                            strategy = unmatchedIconHandlingStrategy,
                            primaryText = firstEntry.website,
                            secondaryText = firstEntry.title,
                            defaultIcon = Icons.Default.Key,
                            iconSize = 32.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                Text(
                    text = firstEntryTitle,
                    style = if (isInExpandedGroup) {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (firstEntry.isBitwardenEntry()) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = "Bitwarden",
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (firstEntry.isKeePassEntry()) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = "KeePass",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (!isSelectionMode && onToggleGroupCover != null) {
                        passwords.forEach { entry ->
                            if (entry.isGroupCover) {
                                IconButton(
                                    onClick = { onToggleGroupCover(entry) },
                                    modifier = Modifier.size(36.dp),
                                    enabled = canSetGroupCover
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "Remove cover",
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                return@forEach
                            }
                        }
                    } else if (isSelectionMode && passwords.any { it.isGroupCover }) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Cover",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (!isSelectionMode && onToggleFavorite != null) {
                        val allFavorited = passwords.all { it.isFavorite }
                        val anyFavorited = passwords.any { it.isFavorite }

                        IconButton(
                            onClick = {
                                passwords.forEach { entry ->
                                    onToggleFavorite(entry)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (anyFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (allFavorited) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites),
                                tint = if (anyFavorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (isSelectionMode && passwords.any { it.isFavorite }) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.favorite),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            val authenticatorState = if (showAuthenticator) {
                rememberPasswordAuthenticatorDisplayState(
                    authenticatorKey = firstEntry.authenticatorKey,
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
                resolvePasswordCardDisplayLines(firstEntry, passwordCardDisplayFields).take(3)
            }
            displayLines.forEach { line ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        line.icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (isInExpandedGroup) 16.dp else 18.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = line.text,
                        style = if (isInExpandedGroup) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (showAuthenticator) {
                if (authenticatorState != null) {
                    MultiPasswordAuthenticatorInlineRow(
                        state = authenticatorState,
                        isInExpandedGroup = isInExpandedGroup,
                        smoothProgress = smoothAuthenticatorProgress
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.password)}:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    passwords.forEachIndexed { index, password ->
                        val isSelected = selectedPasswords.contains(password.id)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            onClick = { onClick(password) },
                            modifier = Modifier.heightIn(min = 32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )

                                Text(
                                    text = stringResource(R.string.password_item_title, index + 1),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiPasswordAuthenticatorInlineRow(
    state: PasswordAuthenticatorDisplayState,
    isInExpandedGroup: Boolean,
    smoothProgress: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(if (isInExpandedGroup) 16.dp else 18.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
            Text(
                text = state.code,
                style = if (isInExpandedGroup) {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
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
                    label = "multi_password_auth_progress"
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

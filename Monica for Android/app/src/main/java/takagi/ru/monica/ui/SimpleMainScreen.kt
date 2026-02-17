package takagi.ru.monica.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import takagi.ru.monica.R
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.data.model.TimelineEvent
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.viewmodel.TotpViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.data.Category
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.GeneratorType
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.TimelineViewModel
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.ui.screens.GeneratorScreen  // 添加生成器页面导入
import takagi.ru.monica.ui.screens.NoteListScreen
import takagi.ru.monica.ui.screens.NoteListContent
import takagi.ru.monica.ui.screens.PasswordDetailScreen
import takagi.ru.monica.ui.screens.SendScreen
import takagi.ru.monica.ui.screens.CardWalletScreen
import takagi.ru.monica.ui.screens.CardWalletTab
import takagi.ru.monica.ui.screens.BankCardDetailScreen
import takagi.ru.monica.ui.screens.DocumentDetailScreen
import takagi.ru.monica.ui.screens.TimelineScreen
import takagi.ru.monica.ui.screens.PasskeyListScreen
import takagi.ru.monica.ui.gestures.SwipeActions
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import kotlin.math.absoluteValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import takagi.ru.monica.ui.components.QrCodeDialog
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.DraggableBottomNavScaffold
import takagi.ru.monica.ui.components.SwipeableAddFab
import takagi.ru.monica.ui.components.DraggableNavItem
import takagi.ru.monica.ui.components.QuickActionItem
import takagi.ru.monica.ui.components.QuickAddCallback
import takagi.ru.monica.ui.components.SyncStatusIcon
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.ui.components.UnifiedCategoryFilterBottomSheet
import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.data.bitwarden.BitwardenSend
import takagi.ru.monica.bitwarden.sync.SyncStatus
import takagi.ru.monica.security.SecurityManager
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.AddEditNoteScreen
import takagi.ru.monica.ui.screens.AddEditSendScreen
import takagi.ru.monica.ui.theme.MonicaTheme

@Composable
private fun SelectionActionBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMoveToCategory: (() -> Unit)? = null,
    onDelete: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 选中数量徽章
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            ActionIcon(
                icon = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(id = R.string.select_all),
                onClick = onSelectAll
            )

            onFavorite?.let {
                ActionIcon(
                    icon = Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(id = R.string.favorite),
                    onClick = it
                )
            }

            onMoveToCategory?.let {
                ActionIcon(
                    icon = Icons.Default.Folder,
                    contentDescription = stringResource(id = R.string.move_to_category),
                    onClick = it
                )
            }

            ActionIcon(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(id = R.string.delete),
                onClick = onDelete
            )

            Spacer(modifier = Modifier.width(4.dp))

            ActionIcon(
                icon = Icons.Default.Close,
                contentDescription = stringResource(id = R.string.close),
                onClick = onExit
            )
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class NewItemStorageDefaults(
    val categoryId: Long? = null,
    val keepassDatabaseId: Long? = null,
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null
)

private fun defaultsFromTotpFilter(filter: takagi.ru.monica.viewmodel.TotpCategoryFilter): NewItemStorageDefaults {
    return when (filter) {
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom -> {
            NewItemStorageDefaults(categoryId = filter.categoryId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault -> {
            NewItemStorageDefaults(bitwardenVaultId = filter.vaultId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter -> {
            NewItemStorageDefaults(
                bitwardenVaultId = filter.vaultId,
                bitwardenFolderId = filter.folderId
            )
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized -> {
            NewItemStorageDefaults(keepassDatabaseId = filter.databaseId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred -> {
            NewItemStorageDefaults(bitwardenVaultId = filter.vaultId)
        }
        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> {
            NewItemStorageDefaults(bitwardenVaultId = filter.vaultId)
        }
        else -> NewItemStorageDefaults()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ListPane(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, content = content)
}

@Composable
private fun DetailPane(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier = modifier, content = content)
}

@Composable
private fun InspectorRow(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun PasskeyOverviewPane(
    totalPasskeys: Int,
    boundPasskeys: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.passkey_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.passkey_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.passkey_count, totalPasskeys)) }
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("${stringResource(R.string.passkey_bound_label)} $boundPasskeys") }
                    )
                }
            }
        }
    }
}

@Composable
private fun PasskeyDetailPane(
    passkey: PasskeyEntry,
    boundPasswordTitle: String?,
    totalPasskeys: Int,
    boundPasskeys: Int,
    onOpenBoundPassword: (() -> Unit)?,
    onUnbindPassword: (() -> Unit)?,
    onDeletePasskey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val createdTime = remember(passkey.createdAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(passkey.createdAt))
    }
    val transports = remember(passkey.transports) {
        passkey.getTransportsList().joinToString(", ").ifBlank { "-" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = passkey.rpName.ifBlank { passkey.rpId },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = passkey.rpId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.passkey_count, totalPasskeys)) })
                AssistChip(onClick = {}, label = { Text("${stringResource(R.string.passkey_bound_label)} $boundPasskeys") })
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InspectorRow(stringResource(R.string.passkey_detail_user), passkey.userDisplayName.ifBlank { "-" })
                InspectorRow(stringResource(R.string.passkey_detail_username), passkey.userName.ifBlank { "-" })
                InspectorRow(stringResource(R.string.passkey_detail_created), createdTime)
                InspectorRow(stringResource(R.string.passkey_detail_last_used), passkey.getLastUsedFormatted())
                InspectorRow(stringResource(R.string.passkey_detail_use_count), passkey.useCount.toString())
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.security),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                InspectorRow("Algorithm", passkey.getAlgorithmName())
                InspectorRow("Transports", transports)
                InspectorRow("Discoverable", if (passkey.isDiscoverable) stringResource(R.string.yes) else stringResource(R.string.no))
                InspectorRow("User verification", if (passkey.isUserVerificationRequired) stringResource(R.string.yes) else stringResource(R.string.no))
                InspectorRow("Backed up", if (passkey.isBackedUp) stringResource(R.string.yes) else stringResource(R.string.no))
                InspectorRow("Sync status", passkey.syncStatus)
                SelectionContainer {
                    InspectorRow("Credential ID", passkey.credentialId)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.bind_password),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = boundPasswordTitle ?: stringResource(R.string.common_account_not_configured),
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { onOpenBoundPassword?.invoke() },
                        enabled = onOpenBoundPassword != null
                    ) {
                        Text(stringResource(R.string.passkey_view_details))
                    }
                    OutlinedButton(
                        onClick = { onUnbindPassword?.invoke() },
                        enabled = onUnbindPassword != null
                    ) {
                        Text(stringResource(R.string.unbind))
                    }
                }
            }
        }

        if (passkey.notes.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.notes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = passkey.notes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.passkey_delete_message, passkey.rpName.ifBlank { passkey.rpId }, passkey.userName.ifBlank { "-" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onDeletePasskey,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.passkey_delete_button))
                }
            }
        }
    }
}

@Composable
private fun SendDetailPane(
    send: BitwardenSend,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = send.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = send.shareUrl,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val previewText = when {
                    send.isTextType && !send.textContent.isNullOrBlank() -> send.textContent
                    send.isFileType -> send.fileName ?: "File Send"
                    else -> send.notes.ifBlank { "-" }
                }
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.send_tag_access_count, send.accessCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                send.maxAccessCount?.let { max ->
                    Text(
                        text = "${stringResource(R.string.send_max_access_count)}: $max",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                send.expirationDate?.let { expiration ->
                    Text(
                        text = stringResource(R.string.send_tag_expire, expiration),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratorDetailPane(
    selectedGenerator: GeneratorType,
    generatedValue: String,
    modifier: Modifier = Modifier
) {
    val generatorLabel = when (selectedGenerator) {
        GeneratorType.SYMBOL -> stringResource(R.string.generator_symbol)
        GeneratorType.PASSWORD -> stringResource(R.string.generator_word)
        GeneratorType.PASSPHRASE -> stringResource(R.string.generator_passphrase)
        GeneratorType.PIN -> stringResource(R.string.generator_pin)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.generator_result),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = generatorLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = generatedValue.ifBlank { stringResource(R.string.loading_default) },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun TimelineDetailPane(
    selectedLog: TimelineEvent.StandardLog,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.history),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = selectedLog.summary,
            style = MaterialTheme.typography.titleMedium
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("类型：${selectedLog.itemType.ifBlank { "-" }}")
                Text("操作：${selectedLog.operationType.ifBlank { "-" }}")
                Text(
                    text = "时间戳：${selectedLog.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 带有底部导航的主屏幕
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class
)
@Composable
fun SimpleMainScreen(
    passwordViewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    bankCardViewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    documentViewModel: takagi.ru.monica.viewmodel.DocumentViewModel,
    generatorViewModel: GeneratorViewModel = viewModel(), // 添加GeneratorViewModel
    noteViewModel: NoteViewModel = viewModel(),
    passkeyViewModel: PasskeyViewModel,  // Passkey ViewModel
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    securityManager: SecurityManager,
    onNavigateToAddPassword: (Long?) -> Unit,
    onNavigateToAddTotp: (Long?) -> Unit,
    onNavigateToQuickTotpScan: () -> Unit,
    onNavigateToAddBankCard: (Long?) -> Unit,
    onNavigateToAddDocument: (Long?) -> Unit,
    onNavigateToAddNote: (Long?) -> Unit,
    onNavigateToPasswordDetail: (Long) -> Unit = {},
    onNavigateToBankCardDetail: (Long) -> Unit, // Add this
    onNavigateToDocumentDetail: (Long) -> Unit, // Keep this
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToSecurityQuestion: () -> Unit = {},
    onNavigateToSyncBackup: () -> Unit = {},
    onNavigateToAutofill: () -> Unit = {},
    onNavigateToPasskeySettings: () -> Unit = {},
    onNavigateToBottomNavSettings: () -> Unit = {},
    onNavigateToColorScheme: () -> Unit = {},
    onSecurityAnalysis: () -> Unit = {},
    onNavigateToDeveloperSettings: () -> Unit = {},
    onNavigateToPermissionManagement: () -> Unit = {},
    onNavigateToMonicaPlus: () -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    onNavigateToBitwardenLogin: () -> Unit = {},
    onClearAllData: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    initialTab: Int = 0
) {

    // Bitwarden ViewModel
    val bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = viewModel()
    val timelineViewModel: TimelineViewModel = viewModel()
    
    // 双击返回退出相关状态
    var backPressedOnce by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 处理返回键 - 需要按两次才能退出
    // 只有在没有子页面（如添加页面）打开时才启用
    // FAB 展开状态由内部 SwipeableAddFab 管理，这里不需要干预，除非我们需要在 FAB 展开时拦截返回键
    // 目前 SwipeableAddFab 应该自己处理了返回键（如果有 BackHandler）
    // 为了安全起见，我们只在最外层处理
    BackHandler(enabled = true) {
        if (backPressedOnce) {
            // 第二次按返回键,退出应用
            (context as? android.app.Activity)?.finish()
        } else {
            // 第一次按返回键,显示提示
            backPressedOnce = true
            Toast.makeText(
                context,
                context.getString(R.string.press_back_again_to_exit),
                Toast.LENGTH_SHORT
            ).show()
            
            // 2秒后重置状态
            scope.launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }
    
    // 密码列表的选择模式状态
    var isPasswordSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswordCount by remember { mutableIntStateOf(0) }
    var onExitPasswordSelection by remember { mutableStateOf({}) }
    var onSelectAllPasswords by remember { mutableStateOf({}) }
    var onFavoriteSelectedPasswords by remember { mutableStateOf({}) }
    var onMoveToCategoryPasswords by remember { mutableStateOf({}) }
    var onDeleteSelectedPasswords by remember { mutableStateOf({}) }
    
    val appSettings by settingsViewModel.settings.collectAsState()
    
    // 密码分组模式: smart(备注>网站>应用>标题), note, website, app, title
    // 从设置中读取，如果设置中没有则默认为 "smart"
    val passwordGroupMode = appSettings.passwordGroupMode


    // 堆叠卡片显示模式: 自动/始终展开（始终展开指逐条显示，不堆叠）
    // 从设置中读取，如果设置中没有则默认为 AUTO
    val stackCardModeKey = appSettings.stackCardMode
    val stackCardMode = remember(stackCardModeKey) {
        runCatching { StackCardMode.valueOf(stackCardModeKey) }.getOrDefault(StackCardMode.AUTO)
    }
    var displayMenuExpanded by remember { mutableStateOf(false) }
    
    // TOTP的选择模式状态
    var isTotpSelectionMode by remember { mutableStateOf(false) }
    var selectedTotpCount by remember { mutableIntStateOf(0) }
    var onExitTotpSelection by remember { mutableStateOf({}) }
    var onSelectAllTotp by remember { mutableStateOf({}) }
    var onMoveToCategoryTotp by remember { mutableStateOf({}) }
    var onDeleteSelectedTotp by remember { mutableStateOf({}) }
    
    // 证件的选择模式状态
    var isDocumentSelectionMode by remember { mutableStateOf(false) }
    var selectedDocumentCount by remember { mutableIntStateOf(0) }
    var onExitDocumentSelection by remember { mutableStateOf({}) }
    var onSelectAllDocuments by remember { mutableStateOf({}) }
    var onDeleteSelectedDocuments by remember { mutableStateOf({}) }
    
    // 银行卡的选择模式状态
    var isBankCardSelectionMode by remember { mutableStateOf(false) }
    var selectedBankCardCount by remember { mutableIntStateOf(0) }
    var onExitBankCardSelection by remember { mutableStateOf({}) }
    var onSelectAllBankCards by remember { mutableStateOf({}) }
    var onDeleteSelectedBankCards by remember { mutableStateOf({}) }
    var onFavoriteBankCards by remember { mutableStateOf({}) }  // 添加收藏回调

    // CardWallet state
    var cardWalletSubTab by rememberSaveable { mutableStateOf(CardWalletTab.BANK_CARDS) }

    val bottomNavVisibility = appSettings.bottomNavVisibility

    val dataTabItems = appSettings.bottomNavOrder
        .map { it.toBottomNavItem() }
        .filter { item ->
            val tab = item.contentTab
            tab == null || bottomNavVisibility.isVisible(tab)
        }

    val tabs = buildList {
        addAll(dataTabItems)
        add(BottomNavItem.Settings)
    }

    val defaultTabKey = remember(initialTab, tabs) { 
        if (initialTab == 0 && tabs.isNotEmpty()) {
            tabs.first().key
        } else {
            indexToDefaultTabKey(initialTab) 
        }
    }
    var selectedTabKey by rememberSaveable { mutableStateOf(defaultTabKey) }

    LaunchedEffect(tabs) {
        if (tabs.none { it.key == selectedTabKey }) {
            selectedTabKey = tabs.first().key
        }
    }

    val currentTab = tabs.firstOrNull { it.key == selectedTabKey } ?: tabs.first()
    val currentTabLabel = stringResource(currentTab.fullLabelRes())
    var selectedPasswordId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inlinePasswordEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingPasswordInline by rememberSaveable { mutableStateOf(false) }
    var selectedTotpId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingTotpInline by rememberSaveable { mutableStateOf(false) }
    var selectedBankCardId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inlineBankCardEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingBankCardInline by rememberSaveable { mutableStateOf(false) }
    var selectedDocumentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var inlineDocumentEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingDocumentInline by rememberSaveable { mutableStateOf(false) }
    var inlineNoteEditorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isAddingNoteInline by rememberSaveable { mutableStateOf(false) }
    var isAddingSendInline by rememberSaveable { mutableStateOf(false) }
    var selectedPasskey by remember { mutableStateOf<PasskeyEntry?>(null) }
    var pendingPasskeyDelete by remember { mutableStateOf<PasskeyEntry?>(null) }
    var selectedSend by remember { mutableStateOf<BitwardenSend?>(null) }
    var selectedTimelineLog by remember { mutableStateOf<TimelineEvent.StandardLog?>(null) }
    val sendState by bitwardenViewModel.sendState.collectAsState()
    val totpFilter by totpViewModel.categoryFilter.collectAsState()
    val totpNewItemDefaults = remember(totpFilter) { defaultsFromTotpFilter(totpFilter) }

    val selectedGeneratorType by generatorViewModel.selectedGenerator.collectAsState()
    val symbolGeneratorResult by generatorViewModel.symbolResult.collectAsState()
    val passwordGeneratorResult by generatorViewModel.passwordResult.collectAsState()
    val passphraseGeneratorResult by generatorViewModel.passphraseResult.collectAsState()
    val pinGeneratorResult by generatorViewModel.pinResult.collectAsState()
    val currentGeneratorResult = when (selectedGeneratorType) {
        GeneratorType.SYMBOL -> symbolGeneratorResult
        GeneratorType.PASSWORD -> passwordGeneratorResult
        GeneratorType.PASSPHRASE -> passphraseGeneratorResult
        GeneratorType.PIN -> pinGeneratorResult
    }

    // 监听滚动以隐藏/显示 FAB
    var isFabVisible by remember { mutableStateOf(true) }
    
    // 如果设置中禁用了此功能，强制显示 FAB
    LaunchedEffect(appSettings.hideFabOnScroll) {
        if (!appSettings.hideFabOnScroll) {
            isFabVisible = true
        }
    }

    // 监听 FAB 展开状态，展开时禁用隐藏逻辑
    var isFabExpanded by remember { mutableStateOf(false) }
    // 使用 rememberUpdatedState 确保 currentTab 始终是最新的
    val currentTabState = rememberUpdatedState(currentTab)
    // 确保滚动监听器能获取到最新的设置值
    val hideFabOnScrollState = rememberUpdatedState(appSettings.hideFabOnScroll)

    // 检测是否有任何选择模式处于激活状态
    var isNoteSelectionMode by remember { mutableStateOf(false) }
    val isAnySelectionMode = isPasswordSelectionMode || isTotpSelectionMode || isDocumentSelectionMode || isBankCardSelectionMode || isNoteSelectionMode
    var generatorRefreshRequestKey by remember { mutableIntStateOf(0) }
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            // 使用 onPostScroll 代替 onPreScroll
            // 只有当子视图实际消费了滚动事件时（即真正滚动了内容），我们才根据方向判断显隐
            // 这样可以解决：
            // 1. 在页面顶部无法上滑时，FAB 不会错误隐藏
            // 2. 内容太少不足以滚动时，FAB 保持显示
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 如果功能未开启，直接返回
                if (!hideFabOnScrollState.value) return Offset.Zero

                // 如果 FAB 已展开，不要隐藏它（防止在添加页面滚动时误触导致页面关闭）
                if (isFabExpanded) return Offset.Zero

                val tab = currentTabState.value
                if (tab == BottomNavItem.Passwords || 
                    tab == BottomNavItem.Authenticator || 
                    tab == BottomNavItem.CardWallet ||
                    tab == BottomNavItem.Generator ||
                    tab == BottomNavItem.Send) {
                    
                    // consumed.y < 0 表示内容向上滚动（手指上滑，查看下方内容） -> 隐藏
                    if (consumed.y < -15f) {
                        isFabVisible = false
                    } 
                    // consumed.y > 0 表示内容向下滚动（手指下滑，回到顶部） -> 显示
                    // 注意：如果是 available.y > 0 但 consumed.y == 0，说明已经到顶滑不动了，
                    // 这种情况下我们也不隐藏（保持原状或强制显示），通常保持原状即可，
                    // 但为了体验，如果在顶部尝试下滑（即使没动），也可以强制显示
                    else if (consumed.y > 15f || (available.y > 0f && consumed.y == 0f)) {
                         isFabVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    val categories by passwordViewModel.categories.collectAsState()
    val currentFilter by passwordViewModel.categoryFilter.collectAsState()
    val allPasswords by passwordViewModel.allPasswords.collectAsState(initial = emptyList())
    val localPasskeys by passkeyViewModel.allPasskeys.collectAsState(initial = emptyList())
    val passkeyTotalCount = localPasskeys.size
    val passkeyBoundCount = localPasskeys.count { it.boundPasswordId != null }
    val passwordById = remember(allPasswords) { allPasswords.associateBy { it.id } }
    val keepassDatabases by localKeePassViewModel.allDatabases.collectAsState()
    val bitwardenVaults by bitwardenViewModel.vaults.collectAsState()
    
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf<Category?>(null) }
    var categoryNameInput by remember { mutableStateOf("") }
    // 可拖拽导航栏模式开关 (将来可从设置中读取)
    val useDraggableNav = appSettings.useDraggableBottomNav
    
    // 构建导航项列表 (用于可拖拽导航栏)
    val draggableNavItems = remember(tabs, currentTab) {
        tabs.map { item ->
            DraggableNavItem(
                key = item.key,
                icon = item.icon,
                labelRes = item.shortLabelRes(),
                selected = item.key == currentTab.key,
                onClick = { selectedTabKey = item.key }
            )
        }
    }
    
    // 获取颜色（在 Composable 上下文中）
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    // 构建快捷操作列表
    val quickActions = remember(
        onNavigateToAddPassword,
        onNavigateToAddTotp,
        onNavigateToQuickTotpScan,
        onNavigateToAddBankCard,
        onNavigateToAddDocument,
        onNavigateToAddNote,
        onSecurityAnalysis,
        onNavigateToSyncBackup,
        tertiaryColor,
        secondaryColor
    ) {
        listOf(
            QuickActionItem(
                icon = Icons.Default.Lock,
                labelRes = R.string.quick_action_add_password,
                onClick = { onNavigateToAddPassword(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.Security,
                labelRes = R.string.quick_action_add_totp,
                onClick = { onNavigateToAddTotp(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.QrCodeScanner,
                labelRes = R.string.quick_action_scan_qr,
                onClick = onNavigateToQuickTotpScan
            ),
            QuickActionItem(
                icon = Icons.Default.CreditCard,
                labelRes = R.string.quick_action_add_card,
                onClick = { onNavigateToAddBankCard(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.Badge,
                labelRes = R.string.quick_action_add_document,
                onClick = { onNavigateToAddDocument(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.Note,
                labelRes = R.string.quick_action_add_note,
                onClick = { onNavigateToAddNote(null) }
            ),
            QuickActionItem(
                icon = Icons.Default.AutoAwesome,
                labelRes = R.string.quick_action_generator,
                onClick = { selectedTabKey = BottomNavItem.Generator.key }
            ),
            QuickActionItem(
                icon = Icons.Default.Shield,
                labelRes = R.string.quick_action_security,
                onClick = onSecurityAnalysis,
                tint = tertiaryColor
            ),
            QuickActionItem(
                icon = Icons.Default.CloudUpload,
                labelRes = R.string.quick_action_backup,
                onClick = onNavigateToSyncBackup,
                tint = secondaryColor
            ),
            QuickActionItem(
                icon = Icons.Default.Download,
                labelRes = R.string.quick_action_import,
                onClick = onNavigateToSyncBackup
            ),
            QuickActionItem(
                icon = Icons.Default.Settings,
                labelRes = R.string.quick_action_settings,
                onClick = { selectedTabKey = BottomNavItem.Settings.key }
            )
        )
    }

    val activity = LocalContext.current.findActivity()
    val widthSizeClass = activity?.let { calculateWindowSizeClass(it).widthSizeClass }
    val isCompactWidth = widthSizeClass == null || widthSizeClass == WindowWidthSizeClass.Compact
    val wideListPaneWidth = 400.dp
    val wideNavigationRailWidth = 80.dp
    val wideFabHostWidth = wideNavigationRailWidth + wideListPaneWidth

    val handlePasswordAddOpen: () -> Unit = {
        if (isCompactWidth) {
            onNavigateToAddPassword(null)
        } else {
            isAddingPasswordInline = true
            inlinePasswordEditorId = null
            selectedPasswordId = null
        }
    }
    val handlePasswordEditOpen: (Long) -> Unit = { passwordId ->
        if (isCompactWidth) {
            onNavigateToAddPassword(passwordId)
        } else {
            isAddingPasswordInline = false
            inlinePasswordEditorId = passwordId
        }
    }
    val handleInlinePasswordEditorBack: () -> Unit = {
        isAddingPasswordInline = false
        inlinePasswordEditorId = null
    }
    val handleTotpAddOpen: () -> Unit = {
        if (isCompactWidth) {
            onNavigateToAddTotp(null)
        } else {
            isAddingTotpInline = true
            selectedTotpId = null
        }
    }
    val handleInlineTotpEditorBack: () -> Unit = {
        isAddingTotpInline = false
        selectedTotpId = null
    }
    val handleBankCardAddOpen: () -> Unit = {
        if (isCompactWidth) {
            onNavigateToAddBankCard(null)
        } else {
            isAddingBankCardInline = true
            inlineBankCardEditorId = null
            selectedBankCardId = null
        }
    }
    val handleBankCardEditOpen: (Long) -> Unit = { cardId ->
        if (isCompactWidth) {
            onNavigateToAddBankCard(cardId)
        } else {
            isAddingBankCardInline = false
            inlineBankCardEditorId = cardId
            selectedBankCardId = null
        }
    }
    val handleInlineBankCardEditorBack: () -> Unit = {
        isAddingBankCardInline = false
        inlineBankCardEditorId = null
    }
    val handleDocumentAddOpen: () -> Unit = {
        if (isCompactWidth) {
            onNavigateToAddDocument(null)
        } else {
            isAddingDocumentInline = true
            inlineDocumentEditorId = null
            selectedDocumentId = null
        }
    }
    val handleDocumentEditOpen: (Long) -> Unit = { documentId ->
        if (isCompactWidth) {
            onNavigateToAddDocument(documentId)
        } else {
            isAddingDocumentInline = false
            inlineDocumentEditorId = documentId
            selectedDocumentId = null
        }
    }
    val handleInlineDocumentEditorBack: () -> Unit = {
        isAddingDocumentInline = false
        inlineDocumentEditorId = null
    }
    val handleNoteOpen: (Long?) -> Unit = { noteId ->
        if (isCompactWidth) {
            onNavigateToAddNote(noteId)
        } else {
            if (noteId == null) {
                isAddingNoteInline = true
                inlineNoteEditorId = null
            } else {
                isAddingNoteInline = false
                inlineNoteEditorId = noteId
            }
        }
    }
    val handleInlineNoteEditorBack: () -> Unit = {
        isAddingNoteInline = false
        inlineNoteEditorId = null
    }

    val handlePasswordDetailOpen: (Long) -> Unit = { passwordId ->
        if (isCompactWidth) {
            onNavigateToPasswordDetail(passwordId)
        } else {
            isAddingPasswordInline = false
            inlinePasswordEditorId = null
            selectedPasswordId = passwordId
        }
    }
    val handleTotpOpen: (Long) -> Unit = { totpId ->
        if (isCompactWidth) {
            onNavigateToAddTotp(totpId)
        } else {
            isAddingTotpInline = false
            selectedTotpId = totpId
        }
    }
    val handleBankCardOpen: (Long) -> Unit = { cardId ->
        if (isCompactWidth) {
            onNavigateToBankCardDetail(cardId)
        } else {
            isAddingBankCardInline = false
            inlineBankCardEditorId = null
            selectedBankCardId = cardId
        }
    }
    val handleDocumentOpen: (Long) -> Unit = { documentId ->
        if (isCompactWidth) {
            onNavigateToDocumentDetail(documentId)
        } else {
            isAddingDocumentInline = false
            inlineDocumentEditorId = null
            selectedDocumentId = documentId
        }
    }
    val handlePasskeyOpen: (PasskeyEntry) -> Unit = { passkey ->
        if (!isCompactWidth) {
            selectedPasskey = passkey
        }
    }
    val handlePasskeyUnbind: (PasskeyEntry) -> Unit = { passkey ->
        val boundId = passkey.boundPasswordId
        if (boundId != null) {
            passwordById[boundId]?.let { entry ->
                val updatedBindings = PasskeyBindingCodec.removeBinding(
                    entry.passkeyBindings,
                    passkey.credentialId
                )
                passwordViewModel.updatePasskeyBindings(boundId, updatedBindings)
            }
        }
        if (passkey.syncStatus != "REFERENCE") {
            passkeyViewModel.updateBoundPassword(passkey.credentialId, null)
        }
        if (selectedPasskey?.credentialId == passkey.credentialId) {
            selectedPasskey = selectedPasskey?.copy(boundPasswordId = null)
        }
    }
    val confirmPasskeyDelete: () -> Unit = {
        val passkey = pendingPasskeyDelete
        if (passkey != null) {
            val boundId = passkey.boundPasswordId
            if (boundId != null) {
                passwordById[boundId]?.let { entry ->
                    val updatedBindings = PasskeyBindingCodec.removeBinding(
                        entry.passkeyBindings,
                        passkey.credentialId
                    )
                    passwordViewModel.updatePasskeyBindings(boundId, updatedBindings)
                }
            }
            val isReferenceOnly = passkey.syncStatus == "REFERENCE" &&
                passkey.privateKeyAlias.isBlank() &&
                passkey.publicKey.isBlank()
            if (!isReferenceOnly) {
                passkeyViewModel.deletePasskey(passkey)
            }
            if (selectedPasskey?.credentialId == passkey.credentialId) {
                selectedPasskey = null
            }
        }
        pendingPasskeyDelete = null
    }
    val handleSendOpen: (BitwardenSend) -> Unit = { send ->
        if (!isCompactWidth) {
            isAddingSendInline = false
            selectedSend = send
        }
    }
    val handleSendAddOpen: () -> Unit = {
        if (!isCompactWidth) {
            selectedSend = null
            isAddingSendInline = true
        }
    }
    val handleInlineSendEditorBack: () -> Unit = {
        isAddingSendInline = false
    }
    val handleTimelineLogOpen: (TimelineEvent.StandardLog) -> Unit = { log ->
        if (!isCompactWidth) {
            selectedTimelineLog = log
        }
    }

    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Passwords) {
            selectedPasswordId = null
            inlinePasswordEditorId = null
            isAddingPasswordInline = false
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Authenticator) {
            selectedTotpId = null
            isAddingTotpInline = false
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth, cardWalletSubTab) {
        if (isCompactWidth || currentTab != BottomNavItem.CardWallet) {
            selectedBankCardId = null
            selectedDocumentId = null
            inlineBankCardEditorId = null
            isAddingBankCardInline = false
            inlineDocumentEditorId = null
            isAddingDocumentInline = false
        } else if (cardWalletSubTab == CardWalletTab.BANK_CARDS) {
            selectedDocumentId = null
            inlineDocumentEditorId = null
            isAddingDocumentInline = false
        } else {
            selectedBankCardId = null
            inlineBankCardEditorId = null
            isAddingBankCardInline = false
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Notes) {
            inlineNoteEditorId = null
            isAddingNoteInline = false
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Passkey) {
            selectedPasskey = null
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Send) {
            selectedSend = null
            isAddingSendInline = false
        }
    }
    LaunchedEffect(currentTab.key, isCompactWidth) {
        if (isCompactWidth || currentTab != BottomNavItem.Timeline) {
            selectedTimelineLog = null
        }
    }
    
    // 根据设置选择导航模式
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        if (useDraggableNav && isCompactWidth) {
        // 使用可拖拽底部导航栏
        DraggableBottomNavScaffold(
            navItems = draggableNavItems,

            quickAddCallback = QuickAddCallback(
                onAddPassword = { title, username, password ->
                    passwordViewModel.quickAddPassword(title, username, password)
                },
                onAddTotp = { name, secret ->
                    totpViewModel.quickAddTotp(name, secret)
                },
                onAddBankCard = { name, number ->
                    bankCardViewModel.quickAddBankCard(name, number)
                },
                onAddNote = { title, content ->
                    noteViewModel.quickAddNote(title, content)
                }
            ),
            floatingActionButton = {}, // FAB 移至外层 Overlay
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (currentTab) {
                        BottomNavItem.Passwords -> {
                            PasswordListContent(
                                viewModel = passwordViewModel,
                                settingsViewModel = settingsViewModel,
                                securityManager = securityManager,
                                keepassDatabases = keepassDatabases,
                                bitwardenVaults = bitwardenVaults,
                                localKeePassViewModel = localKeePassViewModel,
                                groupMode = passwordGroupMode,
                                stackCardMode = stackCardMode,
                                onCreateCategory = {
                                    categoryNameInput = ""
                                    showAddCategoryDialog = true
                                },
                                onRenameCategory = { category ->
                                    categoryNameInput = category.name
                                    showEditCategoryDialog = category
                                },
                                onDeleteCategory = { category ->
                                    passwordViewModel.deleteCategory(category)
                                },
                                onPasswordClick = { password ->
                                    handlePasswordDetailOpen(password.id)
                                },
                                onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onFavorite, onMoveToCategory, onDelete ->
                                    isPasswordSelectionMode = isSelectionMode
                                    selectedPasswordCount = count
                                    onExitPasswordSelection = onExit
                                    onSelectAllPasswords = onSelectAll
                                    onFavoriteSelectedPasswords = onFavorite
                                    onMoveToCategoryPasswords = onMoveToCategory
                                    onDeleteSelectedPasswords = onDelete
                                }
                            )
                        }
                        BottomNavItem.Authenticator -> {
                            TotpListContent(
                                viewModel = totpViewModel,
                                passwordViewModel = passwordViewModel,
                                onTotpClick = { totpId ->
                                    handleTotpOpen(totpId)
                                },
                                onDeleteTotp = { totp ->
                                    totpViewModel.deleteTotpItem(totp)
                                },
                                onQuickScanTotp = onNavigateToQuickTotpScan,
                                onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onMoveToCategory, onDelete ->
                                    isTotpSelectionMode = isSelectionMode
                                    selectedTotpCount = count
                                    onExitTotpSelection = onExit
                                    onSelectAllTotp = onSelectAll
                                    onMoveToCategoryTotp = onMoveToCategory
                                    onDeleteSelectedTotp = onDelete
                                }
                            )
                        }
                        BottomNavItem.CardWallet -> {
                            CardWalletScreen(
                                bankCardViewModel = bankCardViewModel,
                                documentViewModel = documentViewModel,
                                currentTab = cardWalletSubTab,
                                onTabSelected = { cardWalletSubTab = it },
                                onCardClick = { cardId ->
                                    handleBankCardOpen(cardId)
                                },
                                onDocumentClick = { documentId ->
                                    handleDocumentOpen(documentId)
                                },
                                onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                                    isDocumentSelectionMode = isSelectionMode
                                    selectedDocumentCount = count
                                    onExitDocumentSelection = onExit
                                    onSelectAllDocuments = onSelectAll
                                    onDeleteSelectedDocuments = onDelete
                                },
                                onBankCardSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete, onFavorite ->
                                    isBankCardSelectionMode = isSelectionMode
                                    selectedBankCardCount = count
                                    onExitBankCardSelection = onExit
                                    onSelectAllBankCards = onSelectAll
                                    onDeleteSelectedBankCards = onDelete
                                    onFavoriteBankCards = onFavorite
                                }
                            )
                        }
                        BottomNavItem.Generator -> {
                            GeneratorScreen(
                                onNavigateBack = {},
                                viewModel = generatorViewModel,
                                passwordViewModel = passwordViewModel,
                                externalRefreshRequestKey = generatorRefreshRequestKey,
                                onRefreshRequestConsumed = { generatorRefreshRequestKey = 0 },
                                useExternalRefreshFab = true
                            )
                        }
                        BottomNavItem.Notes -> {
                            NoteListScreen(
                                viewModel = noteViewModel,
                                settingsViewModel = settingsViewModel,
                                onNavigateToAddNote = handleNoteOpen,
                                securityManager = securityManager,
                                onSelectionModeChange = { isSelectionMode ->
                                    isNoteSelectionMode = isSelectionMode
                                }
                            )
                        }
                        BottomNavItem.Timeline -> {
                            TimelineScreen(
                                viewModel = timelineViewModel,
                                splitPaneMode = false
                            )
                        }
                        BottomNavItem.Passkey -> {
                            PasskeyListScreen(
                                viewModel = passkeyViewModel,
                                passwordViewModel = passwordViewModel,
                                onNavigateToPasswordDetail = onNavigateToPasswordDetail,
                                onPasskeyClick = {}
                            )
                        }
                        BottomNavItem.Send -> {
                            SendScreen(
                                bitwardenViewModel = bitwardenViewModel
                            )
                        }
                        BottomNavItem.Settings -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = {},
                                onResetPassword = onNavigateToChangePassword,
                                onSecurityQuestions = onNavigateToSecurityQuestion,
                                onNavigateToSyncBackup = onNavigateToSyncBackup,
                                onNavigateToAutofill = onNavigateToAutofill,
                                onNavigateToPasskeySettings = onNavigateToPasskeySettings,
                                onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
                                onNavigateToColorScheme = onNavigateToColorScheme,
                                onSecurityAnalysis = onSecurityAnalysis,
                                onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                                onNavigateToPermissionManagement = onNavigateToPermissionManagement,
                                onNavigateToMonicaPlus = onNavigateToMonicaPlus,
                                onNavigateToExtensions = onNavigateToExtensions,
                                onClearAllData = onClearAllData,
                                showTopBar = false
                            )
                        }
                    }

                    // Selection Action Bars
                    when {
                        currentTab == BottomNavItem.Passwords && isPasswordSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedPasswordCount,
                                onExit = onExitPasswordSelection,
                                onSelectAll = onSelectAllPasswords,
                                onFavorite = onFavoriteSelectedPasswords,
                                onMoveToCategory = onMoveToCategoryPasswords,
                                onDelete = onDeleteSelectedPasswords
                            )
                        }
                        currentTab == BottomNavItem.Authenticator && isTotpSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedTotpCount,
                                onExit = onExitTotpSelection,
                                onSelectAll = onSelectAllTotp,
                                onMoveToCategory = onMoveToCategoryTotp,
                                onDelete = onDeleteSelectedTotp
                            )
                        }
                        currentTab == BottomNavItem.CardWallet && cardWalletSubTab == CardWalletTab.BANK_CARDS && isBankCardSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedBankCardCount,
                                onExit = onExitBankCardSelection,
                                onSelectAll = onSelectAllBankCards,
                                onFavorite = onFavoriteBankCards,
                                onDelete = onDeleteSelectedBankCards
                            )
                        }
                        currentTab == BottomNavItem.CardWallet && cardWalletSubTab == CardWalletTab.DOCUMENTS && isDocumentSelectionMode -> {
                            SelectionActionBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                selectedCount = selectedDocumentCount,
                                onExit = onExitDocumentSelection,
                                onSelectAll = onSelectAllDocuments,
                                onDelete = onDeleteSelectedDocuments
                            )
                        }
                    }
                }
            }
        )
    } else {
        // 使用传统底部导航栏
    Scaffold(
        topBar = {
            // 顶部栏由各自页面内部控制（如 ExpressiveTopBar），这里保持为空以避免叠加
        },
        contentWindowInsets = if (isCompactWidth) {
            ScaffoldDefaults.contentWindowInsets
        } else {
            WindowInsets(0, 0, 0, 0)
        },
        bottomBar = {
            if (isCompactWidth) {
                NavigationBar(
                    tonalElevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEach { item ->
                        val label = stringResource(item.shortLabelRes())
                        NavigationBarItem(
                            icon = {
                                Icon(item.icon, contentDescription = label)
                            },
                            label = {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = item.key == currentTab.key,
                            onClick = { selectedTabKey = item.key }
                        )
                    }
                }
            }
        },
        floatingActionButton = {} // FAB 移至外层 Overlay
    ) { paddingValues ->
        val scaffoldBody: @Composable BoxScope.() -> Unit = {
            when (currentTab) {
                BottomNavItem.Passwords -> {
                    val listPaneContent: @Composable ColumnScope.() -> Unit = {
                        PasswordListContent(
                            viewModel = passwordViewModel,
                            settingsViewModel = settingsViewModel, // Pass SettingsViewModel
                            securityManager = securityManager,
                            keepassDatabases = keepassDatabases,
                            bitwardenVaults = bitwardenVaults,
                            localKeePassViewModel = localKeePassViewModel,
                            groupMode = passwordGroupMode,
                            stackCardMode = stackCardMode,
                            onCreateCategory = {
                                categoryNameInput = ""
                                showAddCategoryDialog = true
                            },
                            onRenameCategory = { category ->
                                categoryNameInput = category.name
                                showEditCategoryDialog = category
                            },
                            onDeleteCategory = { category ->
                                passwordViewModel.deleteCategory(category)
                            },
                            onPasswordClick = { password ->
                                handlePasswordDetailOpen(password.id)
                            },
                            onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onFavorite, onMoveToCategory, onDelete ->
                                isPasswordSelectionMode = isSelectionMode
                                selectedPasswordCount = count
                                onExitPasswordSelection = onExit
                                onSelectAllPasswords = onSelectAll
                                onFavoriteSelectedPasswords = onFavorite
                                onMoveToCategoryPasswords = onMoveToCategory
                                onDeleteSelectedPasswords = onDelete
                            }
                        )
                    }

                    if (isCompactWidth) {
                        ListPane(
                            modifier = Modifier.fillMaxSize(),
                            content = listPaneContent
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ListPane(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(wideListPaneWidth),
                                content = listPaneContent
                            )
                            DetailPane(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                if (isAddingPasswordInline || inlinePasswordEditorId != null) {
                                    AddEditPasswordScreen(
                                        viewModel = passwordViewModel,
                                        totpViewModel = totpViewModel,
                                        bankCardViewModel = bankCardViewModel,
                                        localKeePassViewModel = localKeePassViewModel,
                                        passwordId = inlinePasswordEditorId,
                                        onNavigateBack = handleInlinePasswordEditorBack
                                    )
                                } else if (selectedPasswordId == null) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Select an item to view details",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    CompositionLocalProvider(
                                        LocalSharedTransitionScope provides null,
                                        LocalAnimatedVisibilityScope provides null
                                    ) {
                                        PasswordDetailScreen(
                                            viewModel = passwordViewModel,
                                            passkeyViewModel = passkeyViewModel,
                                            passwordId = selectedPasswordId!!,
                                            disablePasswordVerification = appSettings.disablePasswordVerification,
                                            biometricEnabled = appSettings.biometricEnabled,
                                            onNavigateBack = { selectedPasswordId = null },
                                            onEditPassword = handlePasswordEditOpen,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                BottomNavItem.Authenticator -> {
                    val listPaneContent: @Composable ColumnScope.() -> Unit = {
                        TotpListContent(
                            viewModel = totpViewModel,
                            passwordViewModel = passwordViewModel,
                            onTotpClick = { totpId ->
                                handleTotpOpen(totpId)
                            },
                            onDeleteTotp = { totp ->
                                totpViewModel.deleteTotpItem(totp)
                            },
                            onQuickScanTotp = onNavigateToQuickTotpScan,
                            onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onMoveToCategory, onDelete ->
                                isTotpSelectionMode = isSelectionMode
                                selectedTotpCount = count
                                onExitTotpSelection = onExit
                                onSelectAllTotp = onSelectAll
                                onMoveToCategoryTotp = onMoveToCategory
                                onDeleteSelectedTotp = onDelete
                            }
                        )
                    }

                    if (isCompactWidth) {
                        ListPane(
                            modifier = Modifier.fillMaxSize(),
                            content = listPaneContent
                        )
                    } else {
                        val totpItems by totpViewModel.totpItems.collectAsState()
                        val selectedTotpItem = remember(selectedTotpId, totpItems) {
                            selectedTotpId?.let { selectedId ->
                                totpItems.firstOrNull { it.id == selectedId }
                            }
                        }
                        val selectedTotpData = remember(selectedTotpItem?.itemData) {
                            selectedTotpItem?.itemData?.let { itemData ->
                                runCatching {
                                    kotlinx.serialization.json.Json.decodeFromString<takagi.ru.monica.data.model.TotpData>(itemData)
                                }.getOrNull()
                            }
                        }
                        val totpCategories by totpViewModel.categories.collectAsState()

                        Row(modifier = Modifier.fillMaxSize()) {
                            ListPane(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(wideListPaneWidth),
                                content = listPaneContent
                            )
                            DetailPane(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                if (isAddingTotpInline) {
                                    AddEditTotpScreen(
                                        totpId = null,
                                        initialData = null,
                                        initialTitle = "",
                                        initialNotes = "",
                                        initialCategoryId = totpNewItemDefaults.categoryId,
                                        initialKeePassDatabaseId = totpNewItemDefaults.keepassDatabaseId,
                                        initialBitwardenVaultId = totpNewItemDefaults.bitwardenVaultId,
                                        initialBitwardenFolderId = totpNewItemDefaults.bitwardenFolderId,
                                        categories = totpCategories,
                                        passwordViewModel = passwordViewModel,
                                        localKeePassViewModel = localKeePassViewModel,
                                        onSave = { title, notes, totpData, categoryId, keepassDatabaseId, bitwardenVaultId, bitwardenFolderId ->
                                            totpViewModel.saveTotpItem(
                                                id = null,
                                                title = title,
                                                notes = notes,
                                                totpData = totpData,
                                                categoryId = categoryId,
                                                keepassDatabaseId = keepassDatabaseId,
                                                bitwardenVaultId = bitwardenVaultId,
                                                bitwardenFolderId = bitwardenFolderId
                                            )
                                            handleInlineTotpEditorBack()
                                        },
                                        onNavigateBack = handleInlineTotpEditorBack,
                                        onScanQrCode = onNavigateToQuickTotpScan,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (selectedTotpId == null) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Select an item to view details",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else if (selectedTotpItem == null || selectedTotpItem.id <= 0L || selectedTotpData == null) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "This item is not available for inline editing",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    AddEditTotpScreen(
                                        totpId = selectedTotpItem.id,
                                        initialData = selectedTotpData,
                                        initialTitle = selectedTotpItem.title,
                                        initialNotes = selectedTotpItem.notes,
                                        initialCategoryId = selectedTotpData.categoryId,
                                        initialBitwardenVaultId = selectedTotpItem.bitwardenVaultId,
                                        initialBitwardenFolderId = selectedTotpItem.bitwardenFolderId,
                                        categories = totpCategories,
                                        passwordViewModel = passwordViewModel,
                                        localKeePassViewModel = localKeePassViewModel,
                                        onSave = { title, notes, totpData, categoryId, keepassDatabaseId, bitwardenVaultId, bitwardenFolderId ->
                                            totpViewModel.saveTotpItem(
                                                id = selectedTotpItem.id,
                                                title = title,
                                                notes = notes,
                                                totpData = totpData,
                                                categoryId = categoryId,
                                                keepassDatabaseId = keepassDatabaseId,
                                                bitwardenVaultId = bitwardenVaultId,
                                                bitwardenFolderId = bitwardenFolderId
                                            )
                                        },
                                        onNavigateBack = handleInlineTotpEditorBack,
                                        onScanQrCode = onNavigateToQuickTotpScan,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
                BottomNavItem.CardWallet -> {
                    val listPaneContent: @Composable ColumnScope.() -> Unit = {
                        CardWalletScreen(
                            bankCardViewModel = bankCardViewModel,
                            documentViewModel = documentViewModel,
                            currentTab = cardWalletSubTab,
                            onTabSelected = { cardWalletSubTab = it },
                            onCardClick = { cardId ->
                                handleBankCardOpen(cardId)
                            },
                            onDocumentClick = { documentId ->
                                handleDocumentOpen(documentId)
                            },
                            onSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete ->
                                isDocumentSelectionMode = isSelectionMode
                                selectedDocumentCount = count
                                onExitDocumentSelection = onExit
                                onSelectAllDocuments = onSelectAll
                                onDeleteSelectedDocuments = onDelete
                            },
                            onBankCardSelectionModeChange = { isSelectionMode, count, onExit, onSelectAll, onDelete, onFavorite ->
                                isBankCardSelectionMode = isSelectionMode
                                selectedBankCardCount = count
                                onExitBankCardSelection = onExit
                                onSelectAllBankCards = onSelectAll
                                onDeleteSelectedBankCards = onDelete
                                onFavoriteBankCards = onFavorite
                            }
                        )
                    }

                    if (isCompactWidth) {
                        ListPane(
                            modifier = Modifier.fillMaxSize(),
                            content = listPaneContent
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ListPane(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(wideListPaneWidth),
                                content = listPaneContent
                            )
                            DetailPane(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                if (cardWalletSubTab == CardWalletTab.BANK_CARDS) {
                                    if (isAddingBankCardInline || inlineBankCardEditorId != null) {
                                        AddEditBankCardScreen(
                                            viewModel = bankCardViewModel,
                                            cardId = inlineBankCardEditorId,
                                            onNavigateBack = handleInlineBankCardEditorBack,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (selectedBankCardId == null) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Select a card to view details",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        BankCardDetailScreen(
                                            viewModel = bankCardViewModel,
                                            cardId = selectedBankCardId!!,
                                            onNavigateBack = { selectedBankCardId = null },
                                            onEditCard = handleBankCardEditOpen,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    if (isAddingDocumentInline || inlineDocumentEditorId != null) {
                                        AddEditDocumentScreen(
                                            viewModel = documentViewModel,
                                            documentId = inlineDocumentEditorId,
                                            onNavigateBack = handleInlineDocumentEditorBack,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (selectedDocumentId == null) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Select a document to view details",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        DocumentDetailScreen(
                                            viewModel = documentViewModel,
                                            documentId = selectedDocumentId!!,
                                            onNavigateBack = { selectedDocumentId = null },
                                            onEditDocument = handleDocumentEditOpen,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                BottomNavItem.Generator -> {
                    if (isCompactWidth) {
                        GeneratorScreen(
                            onNavigateBack = {},
                            viewModel = generatorViewModel,
                            passwordViewModel = passwordViewModel,
                            externalRefreshRequestKey = generatorRefreshRequestKey,
                            onRefreshRequestConsumed = { generatorRefreshRequestKey = 0 },
                            useExternalRefreshFab = true
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ListPane(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(wideListPaneWidth)
                            ) {
                                GeneratorScreen(
                                    onNavigateBack = {},
                                    viewModel = generatorViewModel,
                                    passwordViewModel = passwordViewModel,
                                    externalRefreshRequestKey = generatorRefreshRequestKey,
                                    onRefreshRequestConsumed = { generatorRefreshRequestKey = 0 },
                                    useExternalRefreshFab = true
                                )
                            }
                            DetailPane(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                GeneratorDetailPane(
                                    selectedGenerator = selectedGeneratorType,
                                    generatedValue = currentGeneratorResult,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                BottomNavItem.Notes -> {
                    if (isCompactWidth) {
                        NoteListScreen(
                            viewModel = noteViewModel,
                            settingsViewModel = settingsViewModel,
                            onNavigateToAddNote = handleNoteOpen,
                            securityManager = securityManager,
                            onSelectionModeChange = { isSelectionMode ->
                                isNoteSelectionMode = isSelectionMode
                            }
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ListPane(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(wideListPaneWidth)
                            ) {
                                NoteListScreen(
                                    viewModel = noteViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onNavigateToAddNote = handleNoteOpen,
                                    securityManager = securityManager,
                                    onSelectionModeChange = { isSelectionMode ->
                                        isNoteSelectionMode = isSelectionMode
                                    }
                                )
                            }

                            DetailPane(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                if (isAddingNoteInline || inlineNoteEditorId != null) {
                                    AddEditNoteScreen(
                                        noteId = inlineNoteEditorId ?: -1L,
                                        onNavigateBack = handleInlineNoteEditorBack,
                                        viewModel = noteViewModel
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Select a note to view or edit",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                BottomNavItem.Timeline -> {
                    TimelineScreen(
                        viewModel = timelineViewModel,
                        onLogSelected = handleTimelineLogOpen,
                        splitPaneMode = !isCompactWidth
                    )
                }
                BottomNavItem.Passkey -> {
                    if (isCompactWidth) {
                        PasskeyListScreen(
                            viewModel = passkeyViewModel,
                            passwordViewModel = passwordViewModel,
                            onNavigateToPasswordDetail = onNavigateToPasswordDetail,
                            onPasskeyClick = {}
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ListPane(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(wideListPaneWidth)
                            ) {
                                PasskeyListScreen(
                                    viewModel = passkeyViewModel,
                                    passwordViewModel = passwordViewModel,
                                    onNavigateToPasswordDetail = onNavigateToPasswordDetail,
                                    onPasskeyClick = handlePasskeyOpen
                                )
                            }
                            DetailPane(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                val passkey = selectedPasskey
                                if (passkey == null) {
                                    PasskeyOverviewPane(
                                        totalPasskeys = passkeyTotalCount,
                                        boundPasskeys = passkeyBoundCount,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val boundPasswordTitle = passkey.boundPasswordId
                                        ?.let { passwordById[it]?.title }
                                    PasskeyDetailPane(
                                        passkey = passkey,
                                        boundPasswordTitle = boundPasswordTitle,
                                        totalPasskeys = passkeyTotalCount,
                                        boundPasskeys = passkeyBoundCount,
                                        onOpenBoundPassword = passkey.boundPasswordId?.let { boundId ->
                                            { handlePasswordDetailOpen(boundId) }
                                        },
                                        onUnbindPassword = if (passkey.boundPasswordId != null) {
                                            { handlePasskeyUnbind(passkey) }
                                        } else {
                                            null
                                        },
                                        onDeletePasskey = { pendingPasskeyDelete = passkey },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
                BottomNavItem.Send -> {
                    if (isCompactWidth) {
                        SendScreen(
                            bitwardenViewModel = bitwardenViewModel
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ListPane(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(wideListPaneWidth)
                            ) {
                                SendScreen(
                                    onSendClick = handleSendOpen,
                                    selectedSendId = selectedSend?.bitwardenSendId,
                                    bitwardenViewModel = bitwardenViewModel
                                )
                            }
                            DetailPane(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                if (isAddingSendInline) {
                                    AddEditSendScreen(
                                        sendState = sendState,
                                        onNavigateBack = handleInlineSendEditorBack,
                                        onCreate = { title, text, notes, password, maxAccessCount, hideEmail, hiddenText, expireInDays ->
                                            bitwardenViewModel.createTextSend(
                                                title = title,
                                                text = text,
                                                notes = notes,
                                                password = password,
                                                maxAccessCount = maxAccessCount,
                                                hideEmail = hideEmail,
                                                hiddenText = hiddenText,
                                                expireInDays = expireInDays
                                            )
                                            handleInlineSendEditorBack()
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (selectedSend == null) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Select an item to preview",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    SendDetailPane(
                                        send = selectedSend!!,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
                BottomNavItem.Settings -> {
                    // 设置页面 - 使用完整的SettingsScreen
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = {}, // 在主屏幕中不需要返回
                        onResetPassword = onNavigateToChangePassword,
                        onSecurityQuestions = onNavigateToSecurityQuestion,
                        onNavigateToSyncBackup = onNavigateToSyncBackup,
                        onNavigateToAutofill = onNavigateToAutofill,
                        onNavigateToPasskeySettings = onNavigateToPasskeySettings,
                        onNavigateToBottomNavSettings = onNavigateToBottomNavSettings,
                        onNavigateToColorScheme = onNavigateToColorScheme,
                        onSecurityAnalysis = onSecurityAnalysis,
                        onNavigateToDeveloperSettings = onNavigateToDeveloperSettings,
                        onNavigateToPermissionManagement = onNavigateToPermissionManagement,
                        onNavigateToMonicaPlus = onNavigateToMonicaPlus,
                        onNavigateToExtensions = onNavigateToExtensions,
                        onClearAllData = onClearAllData,
                        showTopBar = false  // 在标签页中不显示顶栏
                    )
                }
            }

            when {
                currentTab == BottomNavItem.Passwords && isPasswordSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedPasswordCount,
                        onExit = onExitPasswordSelection,
                        onSelectAll = onSelectAllPasswords,
                        onFavorite = onFavoriteSelectedPasswords,
                        onMoveToCategory = onMoveToCategoryPasswords,
                        onDelete = onDeleteSelectedPasswords
                    )
                }

                currentTab == BottomNavItem.Authenticator && isTotpSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedTotpCount,
                        onExit = onExitTotpSelection,
                        onSelectAll = onSelectAllTotp,
                        onMoveToCategory = onMoveToCategoryTotp,
                        onDelete = onDeleteSelectedTotp
                    )
                }

                currentTab == BottomNavItem.CardWallet && cardWalletSubTab == CardWalletTab.BANK_CARDS && isBankCardSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedBankCardCount,
                        onExit = onExitBankCardSelection,
                        onSelectAll = onSelectAllBankCards,
                        onFavorite = onFavoriteBankCards,
                        onDelete = onDeleteSelectedBankCards
                    )
                }

                currentTab == BottomNavItem.CardWallet && cardWalletSubTab == CardWalletTab.DOCUMENTS && isDocumentSelectionMode -> {
                    SelectionActionBar(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 20.dp),
                        selectedCount = selectedDocumentCount,
                        onExit = onExitDocumentSelection,
                        onSelectAll = onSelectAllDocuments,
                        onDelete = onDeleteSelectedDocuments
                    )
                }
            }
        }

        if (isCompactWidth) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                content = scaffoldBody
            )
        } else {
            val railTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val railBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(wideNavigationRailWidth),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(
                                top = railTopInset + 8.dp,
                                bottom = railBottomInset + 8.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tabs.forEach { item ->
                            val label = stringResource(item.shortLabelRes())
                            NavigationRailItem(
                                selected = item.key == currentTab.key,
                                onClick = { selectedTabKey = item.key },
                                icon = { Icon(item.icon, contentDescription = label) },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    content = scaffoldBody
                )
            }
        }
    }
    }

    // 全局 FAB Overlay
    // 放在最外层 Box 中，覆盖在 Scaffold 之上，确保能展开到全屏
    // 仅在特定 Tab 显示，并且不在多选模式下显示
    val hasWideDetailSelection = !isCompactWidth && when (currentTab) {
        BottomNavItem.Passwords -> isAddingPasswordInline || inlinePasswordEditorId != null
        BottomNavItem.Authenticator -> isAddingTotpInline || selectedTotpId != null
        BottomNavItem.CardWallet -> {
            (cardWalletSubTab == CardWalletTab.BANK_CARDS &&
                (isAddingBankCardInline || inlineBankCardEditorId != null || selectedBankCardId != null)) ||
                (cardWalletSubTab == CardWalletTab.DOCUMENTS &&
                    (isAddingDocumentInline || inlineDocumentEditorId != null || selectedDocumentId != null))
        }
        BottomNavItem.Notes -> isAddingNoteInline || inlineNoteEditorId != null
        BottomNavItem.Send -> isAddingSendInline
        else -> false
    }

    val showFab = (
        currentTab == BottomNavItem.Passwords ||
            currentTab == BottomNavItem.Authenticator ||
            currentTab == BottomNavItem.CardWallet ||
            currentTab == BottomNavItem.Generator ||
            currentTab == BottomNavItem.Notes ||
            currentTab == BottomNavItem.Send
        ) && !isAnySelectionMode && !hasWideDetailSelection

    val fabOverlayModifier = if (isCompactWidth) {
        Modifier.fillMaxSize().zIndex(5f)
    } else {
        Modifier
            .fillMaxHeight()
            .width(wideFabHostWidth)
            .align(Alignment.TopStart)
            .zIndex(5f)
    }
    
    AnimatedVisibility(
        visible = showFab && isFabVisible,
        enter = slideInHorizontally(initialOffsetX = { it * 2 }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it * 2 }) + fadeOut(),
        modifier = fabOverlayModifier
    ) {
        SwipeableAddFab(
            // 通过内部参数控制 FAB 位置，确保容器本身是全屏的
            // NavigationBar 高度约 80dp + 系统导航条高度 + 边距
            fabBottomOffset = if (isCompactWidth) 116.dp else 24.dp,
            modifier = Modifier,
            onFabClickOverride = when (currentTab) {
                BottomNavItem.Passwords -> if (isCompactWidth) null else ({ handlePasswordAddOpen() })
                BottomNavItem.Authenticator -> if (isCompactWidth) null else ({ handleTotpAddOpen() })
                BottomNavItem.CardWallet -> if (isCompactWidth) null else ({
                    if (cardWalletSubTab == CardWalletTab.BANK_CARDS) {
                        handleBankCardAddOpen()
                    } else {
                        handleDocumentAddOpen()
                    }
                })
                BottomNavItem.Notes -> if (isCompactWidth) null else ({ handleNoteOpen(null) })
                BottomNavItem.Send -> if (isCompactWidth) null else ({ handleSendAddOpen() })
                BottomNavItem.Generator -> ({ generatorRefreshRequestKey++ })
                else -> null
            },
            onExpandStateChanged = { expanded -> isFabExpanded = expanded },
            fabContent = { expand ->
            when (currentTab) {
                BottomNavItem.Passwords,
                BottomNavItem.Authenticator,
                BottomNavItem.CardWallet,
                BottomNavItem.Notes,
                BottomNavItem.Send -> {
                     Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                BottomNavItem.Generator -> {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.regenerate),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                else -> { /* 不显示 */ }
            }
        },
        expandedContent = { collapse ->
             Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
               when (currentTab) {
                    BottomNavItem.Passwords -> {
                        AddEditPasswordScreen(
                            viewModel = passwordViewModel,
                            totpViewModel = totpViewModel,
                            bankCardViewModel = bankCardViewModel,
                            localKeePassViewModel = localKeePassViewModel,
                            passwordId = null,
                            onNavigateBack = collapse
                        )
                    }
                    BottomNavItem.Authenticator -> {
                        val totpCategories by totpViewModel.categories.collectAsState()
                        AddEditTotpScreen(
                            totpId = null,
                            initialData = null,
                            initialTitle = "",
                            initialNotes = "",
                            initialCategoryId = totpNewItemDefaults.categoryId,
                            initialKeePassDatabaseId = totpNewItemDefaults.keepassDatabaseId,
                            initialBitwardenVaultId = totpNewItemDefaults.bitwardenVaultId,
                            initialBitwardenFolderId = totpNewItemDefaults.bitwardenFolderId,
                            categories = totpCategories,
                            passwordViewModel = passwordViewModel,
                            localKeePassViewModel = localKeePassViewModel,
                            onSave = { title, notes, totpData, categoryId, keepassDatabaseId, bitwardenVaultId, bitwardenFolderId ->
                                totpViewModel.saveTotpItem(
                                    id = null,
                                    title = title,
                                    notes = notes,
                                    totpData = totpData,
                                    categoryId = categoryId,
                                    keepassDatabaseId = keepassDatabaseId,
                                    bitwardenVaultId = bitwardenVaultId,
                                    bitwardenFolderId = bitwardenFolderId
                                )
                                collapse()
                            },
                            onNavigateBack = collapse,
                            onScanQrCode = {
                                collapse()
                                onNavigateToQuickTotpScan()
                            }
                        )
                    }
                    BottomNavItem.CardWallet -> {
                        if (cardWalletSubTab == CardWalletTab.BANK_CARDS) {
                            AddEditBankCardScreen(
                                viewModel = bankCardViewModel,
                                cardId = null,
                                onNavigateBack = collapse
                            )
                        } else {
                            AddEditDocumentScreen(
                                viewModel = documentViewModel,
                                documentId = null,
                                onNavigateBack = collapse
                            )
                        }
                    }
                    BottomNavItem.Notes -> {
                        AddEditNoteScreen(
                            noteId = -1L,
                            onNavigateBack = collapse,
                            viewModel = noteViewModel
                        )
                    }
                    BottomNavItem.Send -> {
                        AddEditSendScreen(
                            sendState = sendState,
                            onNavigateBack = collapse,
                            onCreate = { title, text, notes, password, maxAccessCount, hideEmail, hiddenText, expireInDays ->
                                bitwardenViewModel.createTextSend(
                                    title = title,
                                    text = text,
                                    notes = notes,
                                    password = password,
                                    maxAccessCount = maxAccessCount,
                                    hideEmail = hideEmail,
                                    hiddenText = hiddenText,
                                    expireInDays = expireInDays
                                )
                                collapse()
                            }
                        )
                    }
                    BottomNavItem.Generator -> {
                        // Generator 使用全局 FAB 点击回调触发刷新，不走展开页面。
                    }
                    else -> { /* Should not happen */ }
                }
            }
        }
    )
    } // End if (showFab)
    } // End Outer Box

    if (pendingPasskeyDelete != null) {
        val passkey = pendingPasskeyDelete!!
        AlertDialog(
            onDismissRequest = { pendingPasskeyDelete = null },
            title = { Text(stringResource(R.string.passkey_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.passkey_delete_message,
                        passkey.rpName.ifBlank { passkey.rpId },
                        passkey.userName.ifBlank { "-" }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = confirmPasskeyDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPasskeyDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(stringResource(R.string.new_category)) },
            text = {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryNameInput.isNotBlank()) {
                        passwordViewModel.addCategory(categoryNameInput)
                        categoryNameInput = ""
                        showAddCategoryDialog = false
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEditCategoryDialog != null) {
        AlertDialog(
            onDismissRequest = { showEditCategoryDialog = null },
            title = { Text(stringResource(R.string.edit_category)) },
            text = {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryNameInput.isNotBlank()) {
                        passwordViewModel.updateCategory(showEditCategoryDialog!!.copy(name = categoryNameInput))
                        categoryNameInput = ""
                        showEditCategoryDialog = null
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCategoryDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 密码列表内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordListContent(
    viewModel: PasswordViewModel,
    settingsViewModel: SettingsViewModel,
    securityManager: SecurityManager,
    keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    groupMode: String = "none",
    stackCardMode: StackCardMode,
    onCreateCategory: () -> Unit,
    onRenameCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onFavorite: () -> Unit,
        onMoveToCategory: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val passwordEntries by viewModel.passwordEntries.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.categoryFilter.collectAsState()
    // settings
    val appSettings by settingsViewModel.settings.collectAsState()

    // "仅本地" 的核心目标是给用户看待上传清单，不应该出现堆叠容器。
    // 因此这里强制扁平展示，仅在该筛选下生效，不影响其他页面。
    val isLocalOnlyView = currentFilter is CategoryFilter.LocalOnly
    val effectiveGroupMode = if (isLocalOnlyView) "none" else groupMode
    val effectiveStackCardMode = if (isLocalOnlyView) StackCardMode.ALWAYS_EXPANDED else stackCardMode
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPasswords by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMoveToCategoryDialog by remember { mutableStateOf(false) }
    
    // 详情对话框状态
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedPasswordForDetail by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { takagi.ru.monica.data.PasswordDatabase.getDatabase(context) }
    val bitwardenRepository = remember { takagi.ru.monica.bitwarden.repository.BitwardenRepository.getInstance(context) }

    // Display options menu state (moved here)
    var displayMenuExpanded by remember { mutableStateOf(false) }
    // Search state hoisted for morphing animation
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

    // 如果搜索框展开，按返回键关闭搜索框
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.updateSearchQuery("")
        focusManager.clearFocus()
    }

    // Handle back press for selection mode
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedPasswords = setOf()
    }
    // Category sheet state
    var isCategorySheetVisible by rememberSaveable { mutableStateOf(false) }
    var categoryPillBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    
    // 添加触觉反馈
    val haptic = rememberHapticFeedback()
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    // Pull-to-search state
    var currentOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val triggerDistance = remember(density) { with(density) { 40.dp.toPx() } }
    val maxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
    var hasVibrated by remember { mutableStateOf(false) }
    
    // Vibrator
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return androidx.compose.ui.geometry.Offset(0f, -consumed)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                 // Allow UserInput to trigger pull
                if (available.y > 0 && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    val delta = available.y * 0.5f // Damping
                    val newOffset = (currentOffset + delta).coerceAtMost(maxDragDistance)
                    val oldOffset = currentOffset
                    currentOffset = newOffset
                    
                    if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                        hasVibrated = true
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                             vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(20)
                        }
                    } else if (newOffset < triggerDistance) {
                        hasVibrated = false
                    }
                    return available
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (currentOffset >= triggerDistance) {
                     isSearchExpanded = true
                     hasVibrated = false
                }
                androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                    currentOffset = value
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.PasswordEntry?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 添加已删除项ID集合（用于在验证前隐藏项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 堆叠展开状态 - 记录哪些分组已展开
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    val outsideTapInteractionSource = remember { MutableInteractionSource() }
    val canCollapseExpandedGroups = effectiveStackCardMode == StackCardMode.AUTO && expandedGroups.isNotEmpty()
    
    // 当分组模式改变时,重置展开状态
    LaunchedEffect(effectiveGroupMode, effectiveStackCardMode) {
        expandedGroups = setOf()
    }
    
    // 根据分组模式对密码进行分组
    val groupedPasswords = remember(passwordEntries, deletedItemIds, effectiveGroupMode, effectiveStackCardMode) {
        val filteredEntries = passwordEntries.filter { it.id !in deletedItemIds }
        
        // 步骤1: 先按"除密码外的信息"合并；始终展开模式下跳过合并，逐条显示
        val mergedByInfo = if (effectiveStackCardMode == StackCardMode.ALWAYS_EXPANDED) {
            filteredEntries.sortedBy { it.sortOrder }.map { listOf(it) }
        } else {
            filteredEntries
                .groupBy { getPasswordInfoKey(it) }
                .map { (_, entries) -> 
                    // 如果有多个密码,保留所有但标记为合并组
                    entries.sortedBy { it.sortOrder }
                }
        }
        
        // 步骤2: 再按显示模式分组
        val groupedAndSorted = if (isLocalOnlyView) {
            // 本筛选是“待上传清单”，直接扁平显示，禁止堆叠/二次分组。
            filteredEntries
                .sortedBy { it.sortOrder }
                .associate { entry -> "entry_${entry.id}" to listOf(entry) }
        } else {
            when (effectiveGroupMode) {
                "title" -> {
                    // 按完整标题分组
                    mergedByInfo
                        .groupBy { entries -> entries.first().title.ifBlank { context.getString(R.string.untitled) } }
                        .mapValues { (_, groups) -> groups.flatten() }
                        .toList()
                        .sortedWith(compareByDescending<Pair<String, List<takagi.ru.monica.data.PasswordEntry>>> { (_, passwords) ->
                            // 计算卡片类型优先级
                            val infoKeyGroups = passwords.groupBy { getPasswordInfoKey(it) }
                            val cardType = when {
                                // 堆叠卡片: 多个不同信息的密码
                                infoKeyGroups.size > 1 -> 3
                                // 多密码卡片: 除密码外信息相同的多个密码
                                infoKeyGroups.size == 1 && passwords.size > 1 -> 2
                                // 单密码卡片: 只有一个密码
                                else -> 1
                            }
                            
                            // 计算收藏优先级 (收藏状态为主要优先级)
                            val anyFavorited = passwords.any { it.isFavorite }
                            val favoriteBonus = if (anyFavorited) 10 else 0
                            
                            // 组合分数: 收藏状态(主要) + 卡片类型(次要)
                            favoriteBonus.toDouble() + cardType.toDouble()
                        }.thenBy { (title, _) ->
                            // 同优先级内按标题排序
                            title
                        })
                        .toMap()
                }
                
                else -> {
                    // 按所选维度分组，并按优先级排序
                    mergedByInfo
                        .groupBy { entries -> getGroupKeyForMode(entries.first(), effectiveGroupMode) }
                        .mapValues { (_, groups) -> groups.flatten() }
                        .toList()
                        .sortedWith(compareByDescending<Pair<String, List<takagi.ru.monica.data.PasswordEntry>>> { (_, passwords) ->
                            // 计算卡片类型优先级
                            val infoKeyGroups = passwords.groupBy { getPasswordInfoKey(it) }
                            val cardType = when {
                                // 堆叠卡片: 多个不同信息的密码
                                infoKeyGroups.size > 1 -> 3
                                // 多密码卡片: 除密码外信息相同的多个密码
                                infoKeyGroups.size == 1 && passwords.size > 1 -> 2
                                // 单密码卡片: 只有一个密码
                                else -> 1
                            }
                            
                            // 计算收藏优先级
                            val favoriteCount = passwords.count { it.isFavorite }
                            val totalCount = passwords.size
                            
                            // 计算收藏优先级 (收藏状态为主要优先级)
                            val anyFavorited = passwords.any { it.isFavorite }
                            val favoriteBonus = if (anyFavorited) 10 else 0
                            
                            // 组合分数: 收藏状态(主要) + 卡片类型(次要)
                            favoriteBonus.toDouble() + cardType.toDouble()
                        }.thenBy { (_, passwords) ->
                            // 同优先级内按第一个卡片的 sortOrder 排序
                            passwords.firstOrNull()?.sortOrder ?: Int.MAX_VALUE
                        })
                        .toMap()
                }
            }
        }

        // 如果是始终展开模式，强制拆分为单项列表，但保持排序顺序
        if (effectiveStackCardMode == StackCardMode.ALWAYS_EXPANDED) {
            groupedAndSorted.values.flatten()
                .map { entry -> 
                    // 使用唯一ID作为键，确保LazyColumn正确渲染
                    "entry_${entry.id}" to listOf(entry)
                }
                .toMap()
        } else {
            groupedAndSorted
        }
    }
    
    // 定义回调函数
    val exitSelection = {
        isSelectionMode = false
        selectedPasswords = setOf()
    }
    
    val selectAll = {
        selectedPasswords = if (selectedPasswords.size == passwordEntries.size) {
            setOf()
        } else {
            passwordEntries.map { it.id }.toSet()
        }
    }
    
    val favoriteSelected = {
        // 智能批量收藏/取消收藏
        coroutineScope.launch {
            val selectedEntries = passwordEntries.filter { selectedPasswords.contains(it.id) }
            
            // 检查是否所有选中的密码都已收藏
            val allFavorited = selectedEntries.all { it.isFavorite }
            
            // 如果全部已收藏,则取消收藏;否则全部设为收藏
            val newFavoriteState = !allFavorited
            
            selectedEntries.forEach { entry ->
                viewModel.toggleFavorite(entry.id, newFavoriteState)
            }
            
            // 显示提示
            val message = if (newFavoriteState) {
                context.getString(R.string.batch_favorited, selectedEntries.size)
            } else {
                context.getString(R.string.batch_unfavorited, selectedEntries.size)
            }
            
            android.widget.Toast.makeText(
                context,
                message,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            // 退出选择模式
            isSelectionMode = false
            selectedPasswords = setOf()
        }
    }
    
    val moveToCategory = {
        showMoveToCategoryDialog = true
    }
    
    val deleteSelected = {
        showBatchDeleteDialog = true
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedPasswords.size) {
        onSelectionModeChange(
            isSelectionMode,
            selectedPasswords.size,
            exitSelection,
            selectAll,
            favoriteSelected,
            moveToCategory,
            deleteSelected
        )
    }

    UnifiedMoveToCategoryBottomSheet(
        visible = showMoveToCategoryDialog,
        onDismiss = { showMoveToCategoryDialog = false },
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = localKeePassViewModel::getGroups,
        onTargetSelected = { target ->
            val selectedIds = selectedPasswords.toList()
            val selectedEntries = passwordEntries.filter { it.id in selectedPasswords }
            when (target) {
                UnifiedMoveCategoryTarget.Uncategorized -> {
                    viewModel.movePasswordsToCategory(selectedIds, null)
                    Toast.makeText(context, context.getString(R.string.category_none), Toast.LENGTH_SHORT).show()
                }
                is UnifiedMoveCategoryTarget.MonicaCategory -> {
                    viewModel.movePasswordsToCategory(selectedIds, target.categoryId)
                    val name = categories.find { it.id == target.categoryId }?.name ?: ""
                    Toast.makeText(context, "${context.getString(R.string.move_to_category)} $name", Toast.LENGTH_SHORT).show()
                }
                is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> {
                    viewModel.movePasswordsToBitwardenFolder(selectedIds, target.vaultId, "")
                    Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                }
                is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> {
                    viewModel.movePasswordsToBitwardenFolder(selectedIds, target.vaultId, target.folderId)
                    Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                }
                is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
                    coroutineScope.launch {
                        try {
                            val result = localKeePassViewModel.addPasswordEntriesToKdbx(
                                databaseId = target.databaseId,
                                entries = selectedEntries,
                                decryptPassword = { encrypted -> securityManager.decryptData(encrypted) ?: "" }
                            )
                            if (result.isSuccess) {
                                viewModel.movePasswordsToKeePassDatabase(selectedIds, target.databaseId)
                                Toast.makeText(
                                    context,
                                    "${context.getString(R.string.move_to_category)} ${keepassDatabases.find { it.id == target.databaseId }?.name ?: "KeePass"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                    coroutineScope.launch {
                        try {
                            val result = localKeePassViewModel.addPasswordEntriesToKdbx(
                                databaseId = target.databaseId,
                                entries = selectedEntries,
                                decryptPassword = { encrypted -> securityManager.decryptData(encrypted) ?: "" }
                            )
                            if (result.isSuccess) {
                                viewModel.movePasswordsToKeePassGroup(selectedIds, target.databaseId, target.groupPath)
                                Toast.makeText(
                                    context,
                                    "${context.getString(R.string.move_to_category)} ${target.groupPath.substringAfterLast('/')}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.webdav_operation_failed, result.exceptionOrNull()?.message ?: ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            showMoveToCategoryDialog = false
            isSelectionMode = false
            selectedPasswords = emptySet()
        }
    )

    // Display options/search state moved to top
    
    Column {
        // M3E Top Bar with integrated search - 始终显示
        val title = when(val filter = currentFilter) {
            is CategoryFilter.All -> stringResource(R.string.filter_all)
            is CategoryFilter.Local -> stringResource(R.string.filter_monica)
            is CategoryFilter.LocalOnly -> stringResource(R.string.filter_local_only)
            is CategoryFilter.Starred -> stringResource(R.string.filter_starred)
            is CategoryFilter.Uncategorized -> stringResource(R.string.filter_uncategorized)
            is CategoryFilter.LocalStarred -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_starred)}"
            is CategoryFilter.LocalUncategorized -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_uncategorized)}"
            is CategoryFilter.Custom -> categories.find { it.id == filter.categoryId }?.name ?: stringResource(R.string.unknown_category)
            is CategoryFilter.KeePassDatabase -> keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"
            is CategoryFilter.KeePassGroupFilter -> filter.groupPath.substringAfterLast('/')
            is CategoryFilter.KeePassDatabaseStarred -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_starred)}"
            is CategoryFilter.KeePassDatabaseUncategorized -> "${keepassDatabases.find { it.id == filter.databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_uncategorized)}"
            is CategoryFilter.BitwardenVault -> "Bitwarden"
            is CategoryFilter.BitwardenFolderFilter -> "Bitwarden"
            is CategoryFilter.BitwardenVaultStarred -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_starred)}"
            is CategoryFilter.BitwardenVaultUncategorized -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_uncategorized)}"
        }

            ExpressiveTopBar(
                title = title,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                searchHint = stringResource(R.string.search_passwords_hint),
                onActionPillBoundsChanged = { bounds -> categoryPillBoundsInWindow = bounds },
                actions = {
                    // 1. Category Folder Trigger
                    IconButton(onClick = { isCategorySheetVisible = true }) {
                         Icon(
                            imageVector = Icons.Default.Folder, // Or CreateNewFolder
                            contentDescription = stringResource(R.string.category),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 2. Display Options Trigger
                    IconButton(onClick = { displayMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = stringResource(R.string.display_options_menu_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 3. Search Trigger (放在最右边)
                    IconButton(onClick = { isSearchExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            val unifiedSelectedFilter = when (val filter = currentFilter) {
                is CategoryFilter.All -> UnifiedCategoryFilterSelection.All
                is CategoryFilter.Local -> UnifiedCategoryFilterSelection.Local
                is CategoryFilter.LocalOnly -> UnifiedCategoryFilterSelection.Local
                is CategoryFilter.Starred -> UnifiedCategoryFilterSelection.Starred
                is CategoryFilter.Uncategorized -> UnifiedCategoryFilterSelection.Uncategorized
                is CategoryFilter.LocalStarred -> UnifiedCategoryFilterSelection.LocalStarred
                is CategoryFilter.LocalUncategorized -> UnifiedCategoryFilterSelection.LocalUncategorized
                is CategoryFilter.Custom -> UnifiedCategoryFilterSelection.Custom(filter.categoryId)
                is CategoryFilter.BitwardenVault -> UnifiedCategoryFilterSelection.BitwardenVaultFilter(filter.vaultId)
                is CategoryFilter.BitwardenFolderFilter -> UnifiedCategoryFilterSelection.BitwardenFolderFilter(filter.vaultId, filter.folderId)
                is CategoryFilter.BitwardenVaultStarred -> UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(filter.vaultId)
                is CategoryFilter.BitwardenVaultUncategorized -> UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(filter.vaultId)
                is CategoryFilter.KeePassDatabase -> UnifiedCategoryFilterSelection.KeePassDatabaseFilter(filter.databaseId)
                is CategoryFilter.KeePassGroupFilter -> UnifiedCategoryFilterSelection.KeePassGroupFilter(filter.databaseId, filter.groupPath)
                is CategoryFilter.KeePassDatabaseStarred -> UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(filter.databaseId)
                is CategoryFilter.KeePassDatabaseUncategorized -> UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(filter.databaseId)
            }
            UnifiedCategoryFilterBottomSheet(
                visible = isCategorySheetVisible,
                onDismiss = { isCategorySheetVisible = false },
                selected = unifiedSelectedFilter,
                showLocalOnlyQuickFilter = true,
                isLocalOnlyQuickFilterSelected = currentFilter is CategoryFilter.LocalOnly,
                onSelectLocalOnlyQuickFilter = { viewModel.setCategoryFilter(CategoryFilter.LocalOnly) },
                onSelect = { selection ->
                    when (selection) {
                        is UnifiedCategoryFilterSelection.All -> viewModel.setCategoryFilter(CategoryFilter.All)
                        is UnifiedCategoryFilterSelection.Local -> viewModel.setCategoryFilter(CategoryFilter.Local)
                        is UnifiedCategoryFilterSelection.Starred -> viewModel.setCategoryFilter(CategoryFilter.Starred)
                        is UnifiedCategoryFilterSelection.Uncategorized -> viewModel.setCategoryFilter(CategoryFilter.Uncategorized)
                        is UnifiedCategoryFilterSelection.LocalStarred -> viewModel.setCategoryFilter(CategoryFilter.LocalStarred)
                        is UnifiedCategoryFilterSelection.LocalUncategorized -> viewModel.setCategoryFilter(CategoryFilter.LocalUncategorized)
                        is UnifiedCategoryFilterSelection.Custom -> viewModel.setCategoryFilter(CategoryFilter.Custom(selection.categoryId))
                        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenVault(selection.vaultId))
                        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenFolderFilter(selection.folderId, selection.vaultId))
                        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenVaultStarred(selection.vaultId))
                        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> viewModel.setCategoryFilter(CategoryFilter.BitwardenVaultUncategorized(selection.vaultId))
                        is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassDatabase(selection.databaseId))
                        is UnifiedCategoryFilterSelection.KeePassGroupFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassGroupFilter(selection.databaseId, selection.groupPath))
                        is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassDatabaseStarred(selection.databaseId))
                        is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> viewModel.setCategoryFilter(CategoryFilter.KeePassDatabaseUncategorized(selection.databaseId))
                    }
                },
                launchAnchorBounds = categoryPillBoundsInWindow,
                categories = categories,
                keepassDatabases = keepassDatabases,
                bitwardenVaults = bitwardenVaults,
                getBitwardenFolders = viewModel::getBitwardenFolders,
                getKeePassGroups = localKeePassViewModel::getGroups,
                onCreateCategory = onCreateCategory,
                onVerifyMasterPassword = { input ->
                    SecurityManager(context).verifyMasterPassword(input)
                },
                onCreateCategoryWithName = { name -> viewModel.addCategory(name) },
                onCreateBitwardenFolder = { vaultId, name ->
                    coroutineScope.launch {
                        val result = bitwardenRepository.createFolder(vaultId, name)
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onRenameBitwardenFolder = { vaultId, folderId, newName ->
                    coroutineScope.launch {
                        val result = bitwardenRepository.renameFolder(vaultId, folderId, newName)
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onDeleteBitwardenFolder = { vaultId, folderId ->
                    coroutineScope.launch {
                        val result = bitwardenRepository.deleteFolder(vaultId, folderId)
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onRenameCategory = onRenameCategory,
                onDeleteCategory = onDeleteCategory,
                onCreateKeePassGroup = { databaseId, parentPath, name ->
                    localKeePassViewModel.createGroup(
                        databaseId = databaseId,
                        groupName = name,
                        parentPath = parentPath
                    ) { result ->
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onRenameKeePassGroup = { databaseId, groupPath, newName ->
                    localKeePassViewModel.renameGroup(
                        databaseId = databaseId,
                        groupPath = groupPath,
                        newName = newName
                    ) { result ->
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onDeleteKeePassGroup = { databaseId, groupPath ->
                    localKeePassViewModel.deleteGroup(
                        databaseId = databaseId,
                        groupPath = groupPath
                    ) { result ->
                        result.exceptionOrNull()?.let { error ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.webdav_operation_failed, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )

    // Display Options Bottom Sheet
    if (displayMenuExpanded) {
        ModalBottomSheet(
            onDismissRequest = { displayMenuExpanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                 modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
            ) {
                 Text(
                    text = stringResource(R.string.display_options_menu_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                // Stack Mode Section
                Text(
                    text = stringResource(R.string.stack_mode_menu_title),
                     style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                val stackModes = listOf(
                    StackCardMode.AUTO,
                    StackCardMode.ALWAYS_EXPANDED
                )

                stackModes.forEach { mode ->
                    val selected = mode == stackCardMode
                    val (modeTitle, desc, icon) = when (mode) {
                        StackCardMode.AUTO -> Triple(
                            stringResource(R.string.stack_mode_auto),
                            stringResource(R.string.stack_mode_auto_desc),
                            Icons.Default.AutoAwesome
                        )
                        StackCardMode.ALWAYS_EXPANDED -> Triple(
                            stringResource(R.string.stack_mode_expand),
                            stringResource(R.string.stack_mode_expand_desc),
                            Icons.Default.UnfoldMore
                        )
                    }

                    takagi.ru.monica.ui.components.SettingsOptionItem(
                        title = modeTitle,
                        description = desc,
                        icon = icon,
                        selected = selected,
                        onClick = {
                            settingsViewModel.updateStackCardMode(mode.name)
                            displayMenuExpanded = false
                        }
                    )
                }

                 HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Group Mode Section
                Text(
                    text = stringResource(R.string.group_mode_menu_title),
                     style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                val groupModes = listOf(
                    "smart" to Triple(
                        stringResource(R.string.group_mode_smart),
                        stringResource(R.string.group_mode_smart_desc),
                        Icons.Default.DashboardCustomize
                    ),
                    "note" to Triple(
                        stringResource(R.string.group_mode_note),
                        stringResource(R.string.group_mode_note_desc),
                        Icons.Default.Description
                    ),
                    "website" to Triple(
                        stringResource(R.string.group_mode_website),
                        stringResource(R.string.group_mode_website_desc),
                        Icons.Default.Language
                    ),
                    "app" to Triple(
                        stringResource(R.string.group_mode_app),
                        stringResource(R.string.group_mode_app_desc),
                        Icons.Default.Apps
                    ),
                    "title" to Triple(
                        stringResource(R.string.group_mode_title),
                        stringResource(R.string.group_mode_title_desc),
                        Icons.Default.Title
                    )
                )

                groupModes.forEach { (modeKey, meta) ->
                    val selected = groupMode == modeKey
                    val (modeTitle, desc, icon) = meta

                    takagi.ru.monica.ui.components.SettingsOptionItem(
                        title = modeTitle,
                        description = desc,
                        icon = icon,
                        selected = selected,
                        onClick = {
                            settingsViewModel.updatePasswordGroupMode(modeKey)
                            displayMenuExpanded = false
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Password Card Display Mode Section
                Text(
                    text = stringResource(R.string.password_card_display_mode_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                val displayModes = listOf(
                    takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL,
                    takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_USERNAME,
                    takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY
                )

                displayModes.forEach { mode ->
                    val selected = mode == appSettings.passwordCardDisplayMode
                    val (modeTitle, desc, icon) = when (mode) {
                        takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL -> Triple(
                            stringResource(R.string.display_mode_all),
                            stringResource(R.string.display_mode_all_desc),
                            Icons.Default.Visibility
                        )
                        takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_USERNAME -> Triple(
                            stringResource(R.string.display_mode_title_username),
                            stringResource(R.string.display_mode_title_username_desc),
                            Icons.Default.Person
                        )
                        takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY -> Triple(
                            stringResource(R.string.display_mode_title_only),
                            stringResource(R.string.display_mode_title_only_desc),
                            Icons.Default.Title
                        )
                    }

                    takagi.ru.monica.ui.components.SettingsOptionItem(
                        title = modeTitle,
                        description = desc,
                        icon = icon,
                        selected = selected,
                        onClick = {
                            settingsViewModel.updatePasswordCardDisplayMode(mode)
                            displayMenuExpanded = false
                        }
                    )
                }
            }
        }
    }

        // 密码列表 - 使用堆叠分组视图
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled = canCollapseExpandedGroups,
                    interactionSource = outsideTapInteractionSource,
                    indication = null
                ) {
                    expandedGroups = emptySet()
                }
        ) {
            if (passwordEntries.isEmpty() && searchQuery.isEmpty()) {
                // Empty state with pull-to-search
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { _, _ -> } // Consume long press to prevent issues
                            )
                        }
                        .pointerInput(Unit) {
                             detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount > 0) {
                                        val newOffset = (currentOffset + dragAmount * 0.5f).coerceAtMost(maxDragDistance)
                                        val oldOffset = currentOffset
                                        currentOffset = newOffset
                                        
                                        if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                                            hasVibrated = true
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                 vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
                                            } else {
                                                @Suppress("DEPRECATION")
                                                vibrator?.vibrate(20)
                                            }
                                        } else if (newOffset < triggerDistance) {
                                           hasVibrated = false
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (currentOffset >= triggerDistance) {
                                        isSearchExpanded = true
                                        hasVibrated = false
                                    }
                                    coroutineScope.launch {
                                        androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) { currentOffset = value }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) { currentOffset = value }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                         Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_passwords_saved),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = androidx.compose.foundation.lazy.rememberLazyListState(),
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    groupedPasswords.forEach { (groupKey, passwords) ->
                    val isExpanded = when (effectiveStackCardMode) {
                        StackCardMode.AUTO -> expandedGroups.contains(groupKey)
                        StackCardMode.ALWAYS_EXPANDED -> true
                    }

                    item(key = "group_$groupKey") {
                        StackedPasswordGroup(
                            website = groupKey,
                            passwords = passwords,
                            isExpanded = isExpanded,
                            stackCardMode = effectiveStackCardMode,
                            swipedItemId = itemToDelete?.id,
                            onToggleExpand = {
                                if (effectiveStackCardMode == StackCardMode.AUTO) {
                                    expandedGroups = if (expandedGroups.contains(groupKey)) {
                                        expandedGroups - groupKey
                                    } else {
                                        expandedGroups + groupKey
                                    }
                                }
                            },
                        onPasswordClick = { password ->
                            if (isSelectionMode) {
                                // 选择模式：切换选择状态
                                selectedPasswords = if (selectedPasswords.contains(password.id)) {
                                    selectedPasswords - password.id
                                } else {
                                    selectedPasswords + password.id
                                }
                            } else {
                                // 普通模式：显示详情页面
                                onPasswordClick(password)
                            }
                        },
                        onSwipeLeft = { password ->
                            // 防止连续滑动导致 itemToDelete 被覆盖
                            if (itemToDelete == null) {
                                // 左滑删除
                                haptic.performWarning()
                                itemToDelete = password
                                deletedItemIds = deletedItemIds + password.id
                            }
                        },
                        onSwipeRight = { password ->
                            // 右滑选择
                            haptic.performSuccess()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                            }
                            selectedPasswords = if (selectedPasswords.contains(password.id)) {
                                selectedPasswords - password.id
                            } else {
                                selectedPasswords + password.id
                            }
                        },
                        onGroupSwipeRight = { groupPasswords ->
                            // 右滑选择整组
                            haptic.performSuccess()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                            }
                            
                            // 检查是否整组都已选中
                            val allSelected = groupPasswords.all { selectedPasswords.contains(it.id) }
                            
                            selectedPasswords = if (allSelected) {
                                // 如果全选了，则取消全选
                                selectedPasswords - groupPasswords.map { it.id }.toSet()
                            } else {
                                // 否则全选（补齐未选中的）
                                selectedPasswords + groupPasswords.map { it.id }
                            }
                        },
                        onToggleFavorite = { password ->
                            viewModel.toggleFavorite(password.id, !password.isFavorite)
                        },
                        onToggleGroupFavorite = {
                            // 智能切换整组收藏状态
                            coroutineScope.launch {
                                val allFavorited = passwords.all { it.isFavorite }
                                val newState = !allFavorited
                                
                                passwords.forEach { password ->
                                    viewModel.toggleFavorite(password.id, newState)
                                }
                                
                                val message = if (newState) {
                                    context.getString(R.string.group_favorited, passwords.size)
                                } else {
                                    context.getString(R.string.group_unfavorited, passwords.size)
                                }
                                
                                android.widget.Toast.makeText(
                                    context,
                                    message,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onToggleGroupCover = { password ->
                            // 切换封面状态并移到顶部
                            coroutineScope.launch {
                                val websiteKey = password.website.ifBlank { context.getString(R.string.filter_uncategorized) }
                                val newCoverState = !password.isGroupCover
                                
                                if (newCoverState) {
                                    // 设置为封面时,同时移到组内第一位
                                    val groupPasswords = passwords
                                    val currentIndex = groupPasswords.indexOfFirst { it.id == password.id }
                                    
                                    if (currentIndex > 0) {
                                        // 需要移动到顶部
                                        val reordered = groupPasswords.toMutableList()
                                        val item = reordered.removeAt(currentIndex)
                                        reordered.add(0, item) // 移到第一位
                                        
                                        // 更新sortOrder
                                        val allPasswords = passwordEntries
                                        val firstItemInGroup = allPasswords.first { it.website.ifBlank { context.getString(R.string.filter_uncategorized) } == websiteKey }
                                        val startSortOrder = allPasswords.indexOf(firstItemInGroup)
                                        
                                        viewModel.updateSortOrders(
                                            reordered.mapIndexed { idx, entry -> 
                                                entry.id to (startSortOrder + idx)
                                            }
                                        )
                                    }
                                }
                                
                                // 设置/取消封面
                                viewModel.toggleGroupCover(password.id, websiteKey, newCoverState)
                            }
                        },
                        isSelectionMode = isSelectionMode,
                        selectedPasswords = selectedPasswords,
                        onToggleSelection = { id ->
                            selectedPasswords = if (selectedPasswords.contains(id)) {
                                selectedPasswords - id
                            } else {
                                selectedPasswords + id
                            }
                        },
                        onOpenMultiPasswordDialog = { passwords ->
                            // 导航到详情页面 (现在详情页面支持多密码)
                            onPasswordClick(passwords.first())
                        },
                        onLongClick = { password ->
                            // 长按进入多选模式
                            haptic.performLongPress()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedPasswords = setOf(password.id)
                            }
                        },
                        iconCardsEnabled = appSettings.iconCardsEnabled,
                        passwordCardDisplayMode = appSettings.passwordCardDisplayMode,
                        enableSharedBounds = !isLocalOnlyView
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
            } // Close else
        } // Close Box
    } // Close PasswordListContent
    
    val activity = context as? FragmentActivity
    val biometricHelper = remember { BiometricHelper(context) }
    val canUseBiometric = activity != null && appSettings.biometricEnabled && biometricHelper.isBiometricAvailable()

    // 批量删除验证对话框（统一 M3 身份验证弹窗）
    if (showBatchDeleteDialog) {
        val biometricAction = if (canUseBiometric) {
            {
                biometricHelper.authenticate(
                    activity = activity!!,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = {
                        coroutineScope.launch {
                            val toDelete = passwordEntries.filter { selectedPasswords.contains(it.id) }
                            toDelete.forEach { viewModel.deletePasswordEntry(it) }
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.deleted_items, toDelete.size),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            isSelectionMode = false
                            selectedPasswords = setOf()
                            passwordInput = ""
                            passwordError = false
                            showBatchDeleteDialog = false
                        }
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.batch_delete_passwords_message, selectedPasswords.size),
            passwordValue = passwordInput,
            onPasswordChange = {
                passwordInput = it
                passwordError = false
            },
            onDismiss = {
                showBatchDeleteDialog = false
                passwordInput = ""
                passwordError = false
            },
            onConfirm = {
                if (SecurityManager(context).verifyMasterPassword(passwordInput)) {
                    coroutineScope.launch {
                        val toDelete = passwordEntries.filter { selectedPasswords.contains(it.id) }
                        toDelete.forEach { viewModel.deletePasswordEntry(it) }
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.deleted_items, toDelete.size),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        isSelectionMode = false
                        selectedPasswords = setOf()
                        passwordInput = ""
                        passwordError = false
                        showBatchDeleteDialog = false
                    }
                } else {
                    passwordError = true
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = passwordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }
    

    
    

    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = stringResource(R.string.item_type_password),
            biometricEnabled = appSettings.biometricEnabled,
            onDismiss = {
                // 取消删除，恢复卡片显示
                deletedItemIds = deletedItemIds - item.id
                itemToDelete = null
            },
            onConfirmWithPassword = { password ->
                singleItemPasswordInput = password
                showSingleItemPasswordVerify = true
            },
            onConfirmWithBiometric = {
                // 指纹验证成功，直接删除
                coroutineScope.launch {
                    viewModel.deletePasswordEntry(item)
                    Toast.makeText(
                        context,
                        context.getString(R.string.deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                    itemToDelete = null
                }
            }
        )
    }
    
    // 单项删除密码验证
    if (showSingleItemPasswordVerify && itemToDelete != null) {
        LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                // 密码正确，执行真实删除
                viewModel.deletePasswordEntry(itemToDelete!!)
                
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 清理状态（保持在 deletedItemIds 中，因为已真实删除）
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            } else {
                // 密码错误，恢复卡片显示
                deletedItemIds = deletedItemIds - itemToDelete!!.id
                
                Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 重置状态
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            }
        }
    }
}
/**
 * TOTP列表内容
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TotpListContent(
    viewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    passwordViewModel: PasswordViewModel,
    onTotpClick: (Long) -> Unit,
    onDeleteTotp: (takagi.ru.monica.data.SecureItem) -> Unit,
    onQuickScanTotp: () -> Unit,
    onSelectionModeChange: (
        isSelectionMode: Boolean,
        selectedCount: Int,
        onExit: () -> Unit,
        onSelectAll: () -> Unit,
        onMoveToCategory: () -> Unit,
        onDelete: () -> Unit
    ) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitwardenRepository = remember { takagi.ru.monica.bitwarden.repository.BitwardenRepository.getInstance(context) }
    val database = remember { takagi.ru.monica.data.PasswordDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val keepassDatabases by database.localKeePassDatabaseDao().getAllDatabases().collectAsState(initial = emptyList())
    var bitwardenVaults by remember { mutableStateOf<List<takagi.ru.monica.data.bitwarden.BitwardenVault>>(emptyList()) }
    val keePassService = remember {
        takagi.ru.monica.utils.KeePassKdbxService(
            context,
            database.localKeePassDatabaseDao(),
            SecurityManager(context)
        )
    }
    val keepassGroupFlows = remember {
        mutableMapOf<Long, kotlinx.coroutines.flow.MutableStateFlow<List<takagi.ru.monica.utils.KeePassGroupInfo>>>()
    }
    val getKeePassGroups: (Long) -> kotlinx.coroutines.flow.Flow<List<takagi.ru.monica.utils.KeePassGroupInfo>> = remember {
        { databaseId ->
            val flow = keepassGroupFlows.getOrPut(databaseId) {
                kotlinx.coroutines.flow.MutableStateFlow(emptyList())
            }
            if (flow.value.isEmpty()) {
                scope.launch {
                    flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                }
            }
            flow
        }
    }
    val totpItems by viewModel.totpItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val passwords by passwordViewModel.allPasswords.collectAsState(initial = emptyList())
    val passwordMap = remember(passwords) { passwords.associateBy { it.id } }
    val haptic = rememberHapticFeedback()
    val focusManager = LocalFocusManager.current
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    
    // 分类选择状态
    var isCategorySheetVisible by rememberSaveable { mutableStateOf(false) }
    var categoryPillBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.categoryFilter.collectAsState()
    LaunchedEffect(Unit) {
        bitwardenVaults = bitwardenRepository.getAllVaults()
    }

    // 如果搜索框展开，按返回键关闭搜索框
    BackHandler(enabled = isSearchExpanded) {
        isSearchExpanded = false
        viewModel.updateSearchQuery("")
        focusManager.clearFocus()
    }

    // Pull-to-search state
    val density = androidx.compose.ui.platform.LocalDensity.current
    var currentOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val triggerDistance = remember(density) { with(density) { 40.dp.toPx() } }
    val maxDragDistance = remember(density) { with(density) { 100.dp.toPx() } }
    var hasVibrated by remember { mutableStateOf(false) }
    
    // Vibrator
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }

    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (currentOffset > 0 && available.y < 0) {
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    currentOffset = newOffset
                    return androidx.compose.ui.geometry.Offset(0f, -consumed)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y > 0 && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    val delta = available.y * 0.5f // Damping
                    val newOffset = (currentOffset + delta).coerceAtMost(maxDragDistance)
                    val oldOffset = currentOffset
                    currentOffset = newOffset
                    
                    if (oldOffset < triggerDistance && newOffset >= triggerDistance && !hasVibrated) {
                        hasVibrated = true
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                             vibrator?.vibrate(android.os.VibrationEffect.createWaveform(takagi.ru.monica.util.VibrationPatterns.TICK, -1))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(20)
                        }
                    } else if (newOffset < triggerDistance) {
                        hasVibrated = false
                    }
                    return available
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (currentOffset >= triggerDistance) {
                     isSearchExpanded = true
                     hasVibrated = false
                }
                androidx.compose.animation.core.Animatable(currentOffset).animateTo(0f) {
                    currentOffset = value
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    
    // 选择模式状态
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMoveToCategoryDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryNameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val settingsManager = remember { takagi.ru.monica.utils.SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(initial = takagi.ru.monica.data.AppSettings())
    val activity = context as? FragmentActivity
    val biometricHelper = remember { BiometricHelper(context) }
    val canUseBiometric = activity != null && appSettings.biometricEnabled && biometricHelper.isBiometricAvailable()
    val sharedTickSeconds by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            value = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 待删除项ID集合（用于隐藏即将删除的项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // QR码显示状态
    var itemToShowQr by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    
    // 过滤掉待删除的项
    val filteredTotpItems = remember(totpItems, deletedItemIds) {
        totpItems.filter { it.id !in deletedItemIds }
    }
    
    // 定义回调函数
    val exitSelection = {
        isSelectionMode = false
        selectedItems = setOf()
    }
    
    val selectAll = {
        selectedItems = if (selectedItems.size == filteredTotpItems.size) {
            setOf()
        } else {
            filteredTotpItems.map { it.id }.toSet()
        }
    }
    
    val deleteSelected = {
        showBatchDeleteDialog = true
    }

    val moveToCategory = {
        showMoveToCategoryDialog = true
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        onSelectionModeChange(
            isSelectionMode,
            selectedItems.size,
            exitSelection,
            selectAll,
            moveToCategory,
            deleteSelected
        )
    }

    UnifiedMoveToCategoryBottomSheet(
        visible = showMoveToCategoryDialog,
        onDismiss = { showMoveToCategoryDialog = false },
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = getKeePassGroups,
        onTargetSelected = { target ->
            val movableIds = selectedItems.filter { it > 0L }
            when (target) {
                UnifiedMoveCategoryTarget.Uncategorized -> {
                    viewModel.moveToCategory(movableIds, null)
                    Toast.makeText(context, context.getString(R.string.category_none), Toast.LENGTH_SHORT).show()
                }
                is UnifiedMoveCategoryTarget.MonicaCategory -> {
                    viewModel.moveToCategory(movableIds, target.categoryId)
                    val name = categories.find { it.id == target.categoryId }?.name ?: ""
                    Toast.makeText(context, "${context.getString(R.string.move_to_category)} $name", Toast.LENGTH_SHORT).show()
                }
                is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> {
                    viewModel.moveToBitwardenFolder(movableIds, target.vaultId, "")
                    Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                }
                is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> {
                    viewModel.moveToBitwardenFolder(movableIds, target.vaultId, target.folderId)
                    Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                }
                is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
                    viewModel.moveToKeePassDatabase(movableIds, target.databaseId)
                    val name = keepassDatabases.find { it.id == target.databaseId }?.name ?: "KeePass"
                    Toast.makeText(context, "${context.getString(R.string.move_to_category)} $name", Toast.LENGTH_SHORT).show()
                }
                is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                    viewModel.moveToKeePassGroup(movableIds, target.databaseId, target.groupPath)
                    val groupName = target.groupPath.substringAfterLast('/')
                    Toast.makeText(context, "${context.getString(R.string.move_to_category)} $groupName", Toast.LENGTH_SHORT).show()
                }
            }
            showMoveToCategoryDialog = false
            isSelectionMode = false
            selectedItems = emptySet()
        }
    )

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(stringResource(R.string.new_category)) },
            text = {
                OutlinedTextField(
                    value = categoryNameInput,
                    onValueChange = { categoryNameInput = it },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryNameInput.isNotBlank()) {
                        passwordViewModel.addCategory(categoryNameInput)
                        categoryNameInput = ""
                        showAddCategoryDialog = false
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column {
        // M3E Top Bar with integrated search - 始终显示
        // 根据当前分类过滤器动态显示标题
            val title = when(currentFilter) {
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.All -> stringResource(R.string.nav_authenticator)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Local -> stringResource(R.string.filter_monica)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Starred -> stringResource(R.string.filter_starred)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Uncategorized -> stringResource(R.string.filter_uncategorized)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalStarred -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_starred)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalUncategorized -> "${stringResource(R.string.filter_monica)} · ${stringResource(R.string.filter_uncategorized)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom -> categories.find { it.id == (currentFilter as takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom).categoryId }?.name ?: stringResource(R.string.unknown_category)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase -> keepassDatabases.find { it.id == (currentFilter as takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase).databaseId }?.name ?: "KeePass"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter -> (currentFilter as takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter).groupPath.substringAfterLast('/')
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred -> "${keepassDatabases.find { it.id == (currentFilter as takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred).databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_starred)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized -> "${keepassDatabases.find { it.id == (currentFilter as takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized).databaseId }?.name ?: "KeePass"} · ${stringResource(R.string.filter_uncategorized)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault -> "Bitwarden"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter -> "Bitwarden"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_starred)}"
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> "${stringResource(R.string.filter_bitwarden)} · ${stringResource(R.string.filter_uncategorized)}"
        }

        ExpressiveTopBar(
            title = title,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = { isSearchExpanded = it },
            searchHint = stringResource(R.string.search_authenticator),
            onActionPillBoundsChanged = { bounds -> categoryPillBoundsInWindow = bounds },
            actions = {
                // 分类选择按钮
                IconButton(onClick = { isCategorySheetVisible = true }) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = stringResource(R.string.category),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 快速扫码按钮
                IconButton(onClick = onQuickScanTotp) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.quick_action_scan_qr),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 搜索按钮
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
        
        val totpSelectedFilter = when (val filter = currentFilter) {
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.All -> UnifiedCategoryFilterSelection.All
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Local -> UnifiedCategoryFilterSelection.Local
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Starred -> UnifiedCategoryFilterSelection.Starred
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Uncategorized -> UnifiedCategoryFilterSelection.Uncategorized
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalStarred -> UnifiedCategoryFilterSelection.LocalStarred
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalUncategorized -> UnifiedCategoryFilterSelection.LocalUncategorized
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom -> UnifiedCategoryFilterSelection.Custom(filter.categoryId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase -> UnifiedCategoryFilterSelection.KeePassDatabaseFilter(filter.databaseId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter -> UnifiedCategoryFilterSelection.KeePassGroupFilter(filter.databaseId, filter.groupPath)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred -> UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter(filter.databaseId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized -> UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter(filter.databaseId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault -> UnifiedCategoryFilterSelection.BitwardenVaultFilter(filter.vaultId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter -> UnifiedCategoryFilterSelection.BitwardenFolderFilter(filter.vaultId, filter.folderId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred -> UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter(filter.vaultId)
            is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter(filter.vaultId)
        }
        UnifiedCategoryFilterBottomSheet(
            visible = isCategorySheetVisible,
            onDismiss = { isCategorySheetVisible = false },
            selected = totpSelectedFilter,
            onSelect = { selection ->
                when (selection) {
                    is UnifiedCategoryFilterSelection.All -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.All)
                    is UnifiedCategoryFilterSelection.Local -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.Local)
                    is UnifiedCategoryFilterSelection.Starred -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.Starred)
                    is UnifiedCategoryFilterSelection.Uncategorized -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.Uncategorized)
                    is UnifiedCategoryFilterSelection.LocalStarred -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalStarred)
                    is UnifiedCategoryFilterSelection.LocalUncategorized -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.LocalUncategorized)
                    is UnifiedCategoryFilterSelection.Custom -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom(selection.categoryId))
                    is UnifiedCategoryFilterSelection.KeePassDatabaseFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase(selection.databaseId))
                    is UnifiedCategoryFilterSelection.KeePassGroupFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter(selection.databaseId, selection.groupPath))
                    is UnifiedCategoryFilterSelection.KeePassDatabaseStarredFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred(selection.databaseId))
                    is UnifiedCategoryFilterSelection.KeePassDatabaseUncategorizedFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized(selection.databaseId))
                    is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault(selection.vaultId))
                    is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter(selection.folderId, selection.vaultId))
                    is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred(selection.vaultId))
                    is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> viewModel.setCategoryFilter(takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized(selection.vaultId))
                }
            },
            launchAnchorBounds = categoryPillBoundsInWindow,
            categories = categories,
            keepassDatabases = keepassDatabases,
            bitwardenVaults = bitwardenVaults,
            getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
            getKeePassGroups = getKeePassGroups,
            onCreateCategory = { showAddCategoryDialog = true },
            onVerifyMasterPassword = { input ->
                SecurityManager(context).verifyMasterPassword(input)
            },
            onCreateCategoryWithName = { name -> passwordViewModel.addCategory(name) },
            onCreateBitwardenFolder = { vaultId, name ->
                scope.launch {
                    val result = bitwardenRepository.createFolder(vaultId, name)
                    result.exceptionOrNull()?.let { error ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                error.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onRenameBitwardenFolder = { vaultId, folderId, newName ->
                scope.launch {
                    val result = bitwardenRepository.renameFolder(vaultId, folderId, newName)
                    result.exceptionOrNull()?.let { error ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                error.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDeleteBitwardenFolder = { vaultId, folderId ->
                scope.launch {
                    val result = bitwardenRepository.deleteFolder(vaultId, folderId)
                    result.exceptionOrNull()?.let { error ->
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                error.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onCreateKeePassGroup = { databaseId, parentPath, name ->
                scope.launch {
                    val result = keePassService.createGroup(
                        databaseId = databaseId,
                        groupName = name,
                        parentPath = parentPath
                    )
                    if (result.isSuccess) {
                        val flow = keepassGroupFlows.getOrPut(databaseId) {
                            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                        }
                        flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                result.exceptionOrNull()?.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onRenameKeePassGroup = { databaseId, groupPath, newName ->
                scope.launch {
                    val result = keePassService.renameGroup(
                        databaseId = databaseId,
                        groupPath = groupPath,
                        newName = newName
                    )
                    if (result.isSuccess) {
                        val flow = keepassGroupFlows.getOrPut(databaseId) {
                            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                        }
                        flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                result.exceptionOrNull()?.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDeleteKeePassGroup = { databaseId, groupPath ->
                scope.launch {
                    val result = keePassService.deleteGroup(
                        databaseId = databaseId,
                        groupPath = groupPath
                    )
                    if (result.isSuccess) {
                        val flow = keepassGroupFlows.getOrPut(databaseId) {
                            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                        }
                        flow.value = keePassService.listGroups(databaseId).getOrDefault(emptyList())
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.webdav_operation_failed,
                                result.exceptionOrNull()?.message ?: ""
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
        
        // 统一进度条 - 在顶栏下方显示
        if (appSettings.validatorUnifiedProgressBar == takagi.ru.monica.data.UnifiedProgressBarMode.ENABLED && 
            filteredTotpItems.isNotEmpty()) {
            takagi.ru.monica.ui.components.UnifiedProgressBar(
                style = appSettings.validatorProgressBarStyle,
                currentSeconds = sharedTickSeconds,
                period = 30,
                smoothProgress = appSettings.validatorSmoothProgress,
                timeOffset = (appSettings.totpTimeOffset * 1000).toLong() // 传递时间偏移(毫秒)
            )
        }

        // TOTP列表
        if (filteredTotpItems.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                    .nestedScroll(nestedScrollConnection),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.no_authenticators_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_authenticators_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // 可拖动排序的列表状态
            val lazyListState = rememberLazyListState()
            
            // 用于拖动排序的本地列表状态
            var localTotpItems by remember(filteredTotpItems) { 
                mutableStateOf(filteredTotpItems) 
            }
            
            // 当筛选后的列表变化时同步
            LaunchedEffect(filteredTotpItems) {
                localTotpItems = filteredTotpItems
            }
            
            val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                // 只在多选模式下允许排序
                if (isSelectionMode) {
                    localTotpItems = localTotpItems.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                }
            }
            
            // 当拖动结束时保存新顺序
            LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
                if (!reorderableLazyListState.isAnyItemDragging && isSelectionMode) {
                    // 拖动结束，保存新顺序到数据库
                    val newOrders = localTotpItems.mapIndexed { index, item ->
                        item.id to index
                    }
                    if (newOrders.isNotEmpty()) {
                        viewModel.updateSortOrders(newOrders)
                    }
                }
            }
            
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { androidx.compose.ui.unit.IntOffset(0, currentOffset.toInt()) }
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = localTotpItems,
                    key = { it.id }
                ) { item ->
                    val index = localTotpItems.indexOf(item)
                    
                    ReorderableItem(reorderableLazyListState, key = item.id) { isDragging ->
                        val elevation by animateDpAsState(
                            if (isDragging) 8.dp else 0.dp,
                            label = "drag_elevation"
                        )
                        
                        // 在多选模式下使用拖动手柄
                        val dragModifier = if (isSelectionMode) {
                            Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performLongPress()
                                },
                                onDragStopped = {
                                    haptic.performSuccess()
                                }
                            )
                        } else {
                            Modifier
                        }
                        
                        // 用 SwipeActions 包裹 TOTP 卡片（多选模式或拖动时禁用滑动）
                        takagi.ru.monica.ui.gestures.SwipeActions(
                            onSwipeLeft = {
                                // 左滑删除
                                haptic.performWarning()
                                itemToDelete = item
                                deletedItemIds = deletedItemIds + item.id
                            },
                            onSwipeRight = {
                                // 右滑选择
                                haptic.performSuccess()
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                }
                                selectedItems = if (selectedItems.contains(item.id)) {
                                    selectedItems - item.id
                                } else {
                                    selectedItems + item.id
                                }
                            },
                            isSwiped = itemToDelete?.id == item.id,
                            enabled = !isDragging && !isSelectionMode // 多选模式下禁用滑动，让拖动手势生效
                        ) {
                            // 包装卡片以支持拖动
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        shadowElevation = elevation.toPx()
                                    }
                                    .then(dragModifier)
                            ) {
                                TotpItemCard(
                                    item = item,
                                    onEdit = { onTotpClick(item.id) },
                                    onToggleSelect = {
                                        selectedItems = if (selectedItems.contains(item.id)) {
                                            selectedItems - item.id
                                        } else {
                                            selectedItems + item.id
                                        }
                                    },
                                    onDelete = {
                                        haptic.performWarning()
                                        itemToDelete = item
                                        deletedItemIds = deletedItemIds + item.id
                                    },
                                    onToggleFavorite = { id, isFavorite ->
                                        viewModel.toggleFavorite(id, isFavorite)
                                    },
                                    onGenerateNext = { id ->
                                        viewModel.incrementHotpCounter(id)
                                    },
                                    onMoveUp = null, // 使用拖动排序替代
                                    onMoveDown = null, // 使用拖动排序替代
                                    onShowQrCode = {
                                        itemToShowQr = item
                                    },
                                    onLongClick = {
                                        // 长按进入多选模式
                                        haptic.performLongPress()
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedItems = setOf(item.id)
                                        }
                                    },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedItems.contains(item.id),
                                    sharedTickSeconds = sharedTickSeconds,
                                    appSettings = appSettings
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
    
    // QR码对话框
    itemToShowQr?.let { item ->
        QrCodeDialog(
            item = item,
            onDismiss = { itemToShowQr = null }
        )
    }
    
    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = stringResource(R.string.item_type_authenticator),
            biometricEnabled = appSettings.biometricEnabled,
            onDismiss = {
                // 取消删除，恢复卡片显示
                deletedItemIds = deletedItemIds - item.id
                itemToDelete = null
            },
            onConfirmWithPassword = { password ->
                singleItemPasswordInput = password
                showSingleItemPasswordVerify = true
            },
            onConfirmWithBiometric = {
                // 指纹验证成功，直接删除
                onDeleteTotp(item)
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                itemToDelete = null
            }
        )
    }
    
    // 单项删除密码验证
    if (showSingleItemPasswordVerify && itemToDelete != null) {
        LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                // 密码正确，删除 TOTP
                onDeleteTotp(itemToDelete!!)
                
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 清理状态（保持在 deletedItemIds 中，因为已真实删除）
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            } else {
                // 密码错误，恢复卡片显示
                deletedItemIds = deletedItemIds - itemToDelete!!.id
                
                Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 重置状态
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            }
        }
    }
    
    // 批量删除验证对话框（统一 M3 身份验证弹窗）
    if (showBatchDeleteDialog) {
        val biometricAction = if (canUseBiometric) {
            {
                biometricHelper.authenticate(
                    activity = activity!!,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = {
                        coroutineScope.launch {
                            val toDelete = totpItems.filter { selectedItems.contains(it.id) }
                            toDelete.forEach { onDeleteTotp(it) }
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.deleted_items, toDelete.size),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            isSelectionMode = false
                            selectedItems = setOf()
                            passwordInput = ""
                            passwordError = false
                            showBatchDeleteDialog = false
                        }
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.batch_delete_totp_message, selectedItems.size),
            passwordValue = passwordInput,
            onPasswordChange = {
                passwordInput = it
                passwordError = false
            },
            onDismiss = {
                showBatchDeleteDialog = false
                passwordInput = ""
                passwordError = false
            },
            onConfirm = {
                if (SecurityManager(context).verifyMasterPassword(passwordInput)) {
                    coroutineScope.launch {
                        val toDelete = totpItems.filter { selectedItems.contains(it.id) }
                        toDelete.forEach { onDeleteTotp(it) }
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.deleted_items, toDelete.size),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        isSelectionMode = false
                        selectedItems = setOf()
                        passwordInput = ""
                        passwordError = false
                        showBatchDeleteDialog = false
                    }
                } else {
                    passwordError = true
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = passwordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }
}

/**
 * TOTP项卡片
 */
@Composable
private fun TotpItemCard(
    item: takagi.ru.monica.data.SecureItem,
    boundPasswordSummary: String? = null,
    onEdit: () -> Unit,
    onToggleSelect: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onGenerateNext: ((Long) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onShowQrCode: ((takagi.ru.monica.data.SecureItem) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    sharedTickSeconds: Long? = null,
    appSettings: takagi.ru.monica.data.AppSettings? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // 直接使用修改后的 TotpCodeCard 组件
    takagi.ru.monica.ui.components.TotpCodeCard(
        item = item,
        boundPasswordSummary = boundPasswordSummary,
        onCopyCode = { code ->
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("TOTP Code", code)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, context.getString(R.string.verification_code_copied), android.widget.Toast.LENGTH_SHORT).show()
        },
        onToggleSelect = onToggleSelect,
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onGenerateNext = onGenerateNext,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onShowQrCode = onShowQrCode,
        onEdit = onEdit,
        onLongClick = onLongClick,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        allowVibration = true,
        sharedTickSeconds = sharedTickSeconds,
        appSettings = appSettings
    )
}

/**
 * 笔记列表
 */
/**
 * 银行卡列表内容
 */
@Composable
private fun BankCardListContent(
    viewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    onCardClick: (Long) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit, () -> Unit) -> Unit  // 添加第6个参数：收藏
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? FragmentActivity
    val settingsManager = remember { takagi.ru.monica.utils.SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(
        initial = takagi.ru.monica.data.AppSettings(biometricEnabled = false)
    )
    val biometricHelper = remember { BiometricHelper(context) }
    
    val cards by viewModel.allCards.collectAsState(initial = emptyList())
    
    // 添加触觉反馈
    val haptic = rememberHapticFeedback()
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 添加已删除项ID集合（用于在验证前隐藏项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 过滤掉已删除的项
    val visibleCards = remember(cards, deletedItemIds) {
        cards.filter { it.id !in deletedItemIds }
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        if (isSelectionMode) {
            onSelectionModeChange(
                true,
                selectedItems.size,
                {
                    // 退出选择模式
                    isSelectionMode = false
                    selectedItems = emptySet()
                },
                {
                    // 全选/取消全选
                    selectedItems = if (selectedItems.size == cards.size) {
                        emptySet()
                    } else {
                        cards.map { it.id }.toSet()
                    }
                },
                {
                    // 批量删除
                    showPasswordDialog = true
                },
                {
                    // 批量收藏
                    scope.launch {
                        selectedItems.forEach { id ->
                            viewModel.toggleFavorite(id)
                        }
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.batch_favorited, selectedItems.size),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        isSelectionMode = false
                        selectedItems = emptySet()
                    }
                }
            )
        } else {
            onSelectionModeChange(false, 0, {}, {}, {}, {})
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_bankcards_title)) },
            text = { Text(stringResource(R.string.batch_delete_bankcards_message, selectedItems.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        showPasswordDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    // 主密码验证对话框
    if (showPasswordDialog) {
        val biometricAction = if (
            activity != null &&
            appSettings.biometricEnabled &&
            biometricHelper.isBiometricAvailable()
        ) {
            {
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = {
                        selectedItems.forEach { id ->
                            viewModel.deleteCard(id)
                        }
                        showPasswordDialog = false
                        masterPassword = ""
                        passwordError = false
                        isSelectionMode = false
                        selectedItems = emptySet()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.batch_delete_bankcards_message, selectedItems.size),
            passwordValue = masterPassword,
            onPasswordChange = {
                masterPassword = it
                passwordError = false
            },
            onDismiss = {
                showPasswordDialog = false
                masterPassword = ""
                passwordError = false
            },
            onConfirm = {
                scope.launch {
                    val securityManager = takagi.ru.monica.security.SecurityManager(context)
                    if (securityManager.verifyMasterPassword(masterPassword)) {
                        selectedItems.forEach { id ->
                            viewModel.deleteCard(id)
                        }
                        showPasswordDialog = false
                        masterPassword = ""
                        passwordError = false
                        isSelectionMode = false
                        selectedItems = emptySet()
                    } else {
                        passwordError = true
                    }
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = passwordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }
    
    if (cards.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_bank_cards_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = visibleCards,  // 使用过滤后的列表
                key = { it.id }
            ) { card ->
                val index = visibleCards.indexOf(card)
                
                takagi.ru.monica.ui.gestures.SwipeActions(
                    onSwipeLeft = {
                        // 左滑删除
                        haptic.performWarning()
                        itemToDelete = card
                        deletedItemIds = deletedItemIds + card.id
                    },
                    onSwipeRight = {
                        // 右滑选择
                        haptic.performSuccess()
                        if (!isSelectionMode) {
                            isSelectionMode = true
                        }
                        selectedItems = if (selectedItems.contains(card.id)) {
                            selectedItems - card.id
                        } else {
                            selectedItems + card.id
                        }
                    },
                    isSwiped = itemToDelete?.id == card.id,
                    enabled = true
                ) {
                    BankCardItemCard(
                        item = card,
                        onClick = { 
                            if (isSelectionMode) {
                                // 选择模式下点击切换选择状态
                                selectedItems = if (selectedItems.contains(card.id)) {
                                    selectedItems - card.id
                                } else {
                                    selectedItems + card.id
                                }
                            } else {
                                // 普通模式下打开详情
                                onCardClick(card.id)
                            }
                        },
                        onDelete = {
                            itemToDelete = card
                        },
                        onToggleFavorite = { id, _ ->
                            viewModel.toggleFavorite(id)
                        },
                        onMoveUp = if (index > 0 && !isSelectionMode) {
                            {
                                val currentItem = visibleCards[index]
                                val previousItem = visibleCards[index - 1]
                                viewModel.updateSortOrders(listOf(
                                    currentItem.id to (index - 1),
                                    previousItem.id to index
                                ))
                            }
                        } else null,
                        onMoveDown = if (index < visibleCards.size - 1 && !isSelectionMode) {
                            {
                                val currentItem = visibleCards[index]
                                val nextItem = visibleCards[index + 1]
                                viewModel.updateSortOrders(listOf(
                                    currentItem.id to (index + 1),
                                    nextItem.id to index
                                ))
                            }
                        } else null,
                        onLongClick = {
                            haptic.performLongPress()
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedItems = setOf(card.id)
                            }
                        },
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedItems.contains(card.id)
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = stringResource(R.string.item_type_bank_card),
            biometricEnabled = appSettings.biometricEnabled,
            onDismiss = {
                // 取消删除，恢复卡片显示
                deletedItemIds = deletedItemIds - item.id
                itemToDelete = null
            },
            onConfirmWithPassword = { password ->
                singleItemPasswordInput = password
                showSingleItemPasswordVerify = true
            },
            onConfirmWithBiometric = {
                // 指纹验证成功，直接删除
                viewModel.deleteCard(item.id)
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                itemToDelete = null
            }
        )
    }
    
    // 单项删除密码验证
    if (showSingleItemPasswordVerify && itemToDelete != null) {
        LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                // 密码正确，执行真实删除
                viewModel.deleteCard(itemToDelete!!.id)
                
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 清理状态（保持在 deletedItemIds 中，因为已真实删除）
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            } else {
                // 密码错误，恢复卡片显示
                deletedItemIds = deletedItemIds - itemToDelete!!.id
                
                Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 重置状态
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            }
        }
    }
}

@Composable
private fun BankCardItemCard(
    item: takagi.ru.monica.data.SecureItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    takagi.ru.monica.ui.components.BankCardCard(
        item = item,
        onClick = onClick,
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onLongClick = onLongClick,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected
    )
}

/**
 * 证件列表内容
 */
@Composable
private fun DocumentListContent(
    viewModel: takagi.ru.monica.viewmodel.DocumentViewModel,
    onDocumentClick: (Long) -> Unit,
    onSelectionModeChange: (Boolean, Int, () -> Unit, () -> Unit, () -> Unit) -> Unit
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? FragmentActivity
    val settingsManager = remember { takagi.ru.monica.utils.SettingsManager(context) }
    val appSettings by settingsManager.settingsFlow.collectAsState(
        initial = takagi.ru.monica.data.AppSettings(biometricEnabled = false)
    )
    val biometricHelper = remember { BiometricHelper(context) }
    val haptic = rememberHapticFeedback()
    
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    
    // 添加单项删除对话框状态
    var itemToDelete by remember { mutableStateOf<takagi.ru.monica.data.SecureItem?>(null) }
    var singleItemPasswordInput by remember { mutableStateOf("") }
    var showSingleItemPasswordVerify by remember { mutableStateOf(false) }
    
    // 待删除项ID集合（用于隐藏即将删除的项）
    var deletedItemIds by remember { mutableStateOf(setOf<Long>()) }
    
    // 过滤掉待删除的项
    val filteredDocuments = remember(documents, deletedItemIds) {
        documents.filter { it.id !in deletedItemIds }
    }
    
    // 通知父组件选择模式状态变化
    LaunchedEffect(isSelectionMode, selectedItems.size) {
        if (isSelectionMode) {
            onSelectionModeChange(
                true,
                selectedItems.size,
                {
                    isSelectionMode = false
                    selectedItems = emptySet()
                },
                {
                    selectedItems = if (selectedItems.size == filteredDocuments.size) {
                        emptySet()
                    } else {
                        filteredDocuments.map { it.id }.toSet()
                    }
                },
                {
                    showPasswordDialog = true
                }
            )
        } else {
            onSelectionModeChange(false, 0, {}, {}, {})
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.batch_delete_documents_title)) },
            text = { Text(stringResource(R.string.batch_delete_documents_message, selectedItems.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        showPasswordDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
    
    // 主密码验证对话框
    if (showPasswordDialog) {
        val biometricAction = if (
            activity != null &&
            appSettings.biometricEnabled &&
            biometricHelper.isBiometricAvailable()
        ) {
            {
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity),
                    subtitle = context.getString(R.string.verify_to_delete),
                    onSuccess = {
                        selectedItems.forEach { id ->
                            viewModel.deleteDocument(id)
                        }
                        showPasswordDialog = false
                        masterPassword = ""
                        passwordError = false
                        isSelectionMode = false
                        selectedItems = emptySet()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {}
                )
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.batch_delete_documents_message, selectedItems.size),
            passwordValue = masterPassword,
            onPasswordChange = {
                masterPassword = it
                passwordError = false
            },
            onDismiss = {
                showPasswordDialog = false
                masterPassword = ""
                passwordError = false
            },
            onConfirm = {
                scope.launch {
                    val securityManager = takagi.ru.monica.security.SecurityManager(context)
                    if (securityManager.verifyMasterPassword(masterPassword)) {
                        selectedItems.forEach { id ->
                            viewModel.deleteDocument(id)
                        }
                        showPasswordDialog = false
                        masterPassword = ""
                        passwordError = false
                        isSelectionMode = false
                        selectedItems = emptySet()
                    } else {
                        passwordError = true
                    }
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = passwordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }
    
    if (filteredDocuments.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_documents_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = filteredDocuments,
                key = { it.id }
            ) { document ->
                val index = filteredDocuments.indexOf(document)
                
                // 用 SwipeActions 包裹文档卡片
                takagi.ru.monica.ui.gestures.SwipeActions(
                    onSwipeLeft = {
                        // 左滑删除
                        haptic.performWarning()
                        itemToDelete = document
                        deletedItemIds = deletedItemIds + document.id
                    },
                    onSwipeRight = {
                        // 右滑选择
                        haptic.performSuccess()
                        if (!isSelectionMode) {
                            isSelectionMode = true
                        }
                        selectedItems = if (selectedItems.contains(document.id)) {
                            selectedItems - document.id
                        } else {
                            selectedItems + document.id
                        }
                    },
                    isSwiped = itemToDelete?.id == document.id,
                    enabled = true // 多选模式下也可以滑动
                ) {
                    DocumentItemCard(
                        item = document,
                        onClick = {
                            if (isSelectionMode) {
                                selectedItems = if (selectedItems.contains(document.id)) {
                                    selectedItems - document.id
                                } else {
                                    selectedItems + document.id
                                }
                            } else {
                                onDocumentClick(document.id)
                            }
                        },
                        onDelete = {
                            haptic.performWarning()
                            itemToDelete = document
                            deletedItemIds = deletedItemIds + document.id
                        },
                        onToggleFavorite = { id, _ -> // isFavorite 参数未使用，因为直接调用 toggleFavorite
                            viewModel.toggleFavorite(id)
                        },
                        onMoveUp = if (index > 0) {
                            {
                                // 交换当前项和上一项的sortOrder
                                val currentItem = filteredDocuments[index]
                                val previousItem = filteredDocuments[index - 1]
                                viewModel.updateSortOrders(listOf(
                                    currentItem.id to (index - 1),
                                    previousItem.id to index
                                ))
                            }
                        } else null,
                        onMoveDown = if (index < filteredDocuments.size - 1) {
                            {
                                // 交换当前项和下一项的sortOrder
                                val currentItem = filteredDocuments[index]
                                val nextItem = filteredDocuments[index + 1]
                                viewModel.updateSortOrders(listOf(
                                    currentItem.id to (index + 1),
                                    nextItem.id to index
                                ))
                            }
                        } else null,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedItems.contains(document.id)
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    // 单项删除确认对话框(支持指纹和密码验证)
    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = stringResource(R.string.item_type_document),
            biometricEnabled = appSettings.biometricEnabled,
            onDismiss = {
                // 取消删除，恢复卡片显示
                deletedItemIds = deletedItemIds - item.id
                itemToDelete = null
            },
            onConfirmWithPassword = { password ->
                singleItemPasswordInput = password
                showSingleItemPasswordVerify = true
            },
            onConfirmWithBiometric = {
                // 指纹验证成功，直接删除
                viewModel.deleteDocument(item.id)
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                itemToDelete = null
            }
        )
    }
    
    // 单项删除密码验证
    if (showSingleItemPasswordVerify && itemToDelete != null) {
        LaunchedEffect(Unit) {
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                // 密码正确，删除文档
                viewModel.deleteDocument(itemToDelete!!.id)
                
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 清理状态（保持在 deletedItemIds 中，因为已真实删除）
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            } else {
                // 密码错误，恢复卡片显示
                deletedItemIds = deletedItemIds - itemToDelete!!.id
                
                Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    Toast.LENGTH_SHORT
                ).show()
                
                // 重置状态
                itemToDelete = null
                singleItemPasswordInput = ""
                showSingleItemPasswordVerify = false
            }
        }
    }
}

/**
 * 堆叠密码卡片组
 * 
 * @param website 网站分组键（用于分组，内部从 passwords 获取实际值）
 * @param passwords 该组的密码条目列表
 * @param isExpanded 是否展开显示所有卡片
 * @param onToggleExpand 展开/收起切换回调
 * @param onPasswordClick 密码卡片点击回调
 * @param onLongClick 长按回调
 * @param onToggleFavorite 收藏切换回调
 * @param onToggleGroupFavorite 整组收藏切换回调
 * @param onToggleGroupCover 封面切换回调
 * @param isSelectionMode 是否处于选择模式
 * @param selectedPasswords 已选中的密码ID集合
 * @param onToggleSelection 选择切换回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StackedPasswordGroup(
    @Suppress("UNUSED_PARAMETER") website: String,
    passwords: List<takagi.ru.monica.data.PasswordEntry>,
    isExpanded: Boolean,
    stackCardMode: StackCardMode,
    onToggleExpand: () -> Unit,
    onPasswordClick: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSwipeLeft: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onSwipeRight: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onGroupSwipeRight: (List<takagi.ru.monica.data.PasswordEntry>) -> Unit,
    onToggleFavorite: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    onToggleGroupFavorite: () -> Unit,
    onToggleGroupCover: (takagi.ru.monica.data.PasswordEntry) -> Unit,
    isSelectionMode: Boolean,
    selectedPasswords: Set<Long>,
    swipedItemId: Long? = null,
    onToggleSelection: (Long) -> Unit,
    onOpenMultiPasswordDialog: (List<takagi.ru.monica.data.PasswordEntry>) -> Unit,
    onLongClick: (takagi.ru.monica.data.PasswordEntry) -> Unit, // 新增：长按进入多选模式
    iconCardsEnabled: Boolean = false,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL,
    enableSharedBounds: Boolean = true
) {
    // 检查是否为多密码合并卡片(除密码外信息完全相同)
    val isMergedPasswordCard = passwords.size > 1 && 
        passwords.map { getPasswordInfoKey(it) }.distinct().size == 1
    
    // 如果选择“始终展开”，则直接平铺展示，不使用堆叠容器
    if (stackCardMode == StackCardMode.ALWAYS_EXPANDED) {
        passwords.forEach { password ->
            key(password.id) {
                takagi.ru.monica.ui.gestures.SwipeActions(
                    onSwipeLeft = { onSwipeLeft(password) },
                    onSwipeRight = { onSwipeRight(password) },
                    isSwiped = password.id == swipedItemId,
                    enabled = true
                ) {
                    PasswordEntryCard(
                        entry = password,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelection(password.id)
                            } else {
                                onPasswordClick(password)
                            }
                        },
                        onLongClick = { onLongClick(password) },
                        onToggleFavorite = { onToggleFavorite(password) },
                        onToggleGroupCover = null,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedPasswords.contains(password.id),
                        canSetGroupCover = false,
                        isInExpandedGroup = false,
                        isSingleCard = true,
                        iconCardsEnabled = iconCardsEnabled,
                        passwordCardDisplayMode = passwordCardDisplayMode,
                        enableSharedBounds = enableSharedBounds
                    )
                }
            }
        }
        return
    }

    // 单个密码直接显示，不堆叠 (且不是合并卡片)
    if (passwords.size == 1 && !isMergedPasswordCard) {
        val password = passwords.first()
        takagi.ru.monica.ui.gestures.SwipeActions(
            onSwipeLeft = { onSwipeLeft(password) },
            onSwipeRight = { onSwipeRight(password) },
            isSwiped = password.id == swipedItemId,
            enabled = true
        ) {
            PasswordEntryCard(
                entry = password,
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection(password.id)
                    } else {
                        onPasswordClick(password)
                    }
                },
                onLongClick = { onLongClick(password) },
                onToggleFavorite = { onToggleFavorite(password) },
                onToggleGroupCover = null,
                isSelectionMode = isSelectionMode,
                isSelected = selectedPasswords.contains(password.id),
                canSetGroupCover = false,
                isInExpandedGroup = false,
                isSingleCard = true,
                iconCardsEnabled = iconCardsEnabled,
                passwordCardDisplayMode = passwordCardDisplayMode,
                enableSharedBounds = enableSharedBounds
            )
        }
        return
    }

    // 如果是多密码合并卡片,直接显示为单卡片,不堆叠
    if (isMergedPasswordCard) {
        takagi.ru.monica.ui.gestures.SwipeActions(
            onSwipeLeft = { onSwipeLeft(passwords.first()) },
            onSwipeRight = { onGroupSwipeRight(passwords) },
            isSwiped = passwords.first().id == swipedItemId,
            enabled = true
        ) {
            MultiPasswordEntryCard(
                passwords = passwords,
                onClick = { password ->
                    if (isSelectionMode) {
                        onToggleSelection(password.id)
                    } else {
                        // 点击密码按钮 → 进入编辑页面
                        onPasswordClick(password)
                    }
                },
                onCardClick = if (!isSelectionMode) {
                    // 点击卡片本身 → 打开多密码详情对话框
                    { onOpenMultiPasswordDialog(passwords) }
                } else null,
                onLongClick = { onLongClick(passwords.first()) },
                onToggleFavorite = { password -> onToggleFavorite(password) },
                onToggleGroupCover = null,
                isSelectionMode = isSelectionMode,
                selectedPasswords = selectedPasswords,
                canSetGroupCover = false,
                hasGroupCover = false,
                isInExpandedGroup = false,
                iconCardsEnabled = iconCardsEnabled,
                passwordCardDisplayMode = passwordCardDisplayMode
            )
        }
        return
    }
    
    // 否则使用原有的堆叠逻辑
    val isGroupFavorited = passwords.all { it.isFavorite }
    val hasGroupCover = passwords.any { it.isGroupCover }
    
    // 🎨 动画状态
    val effectiveExpanded = when (stackCardMode) {
        StackCardMode.AUTO -> isExpanded
        StackCardMode.ALWAYS_EXPANDED -> true
    }

    val expandProgress by animateFloatAsState(
        targetValue = if (effectiveExpanded && passwords.size > 1) 1f else 0f,
        animationSpec = tween(200),
        label = "expand_animation"
    )
    
    val containerAlpha by animateFloatAsState(
        targetValue = if (effectiveExpanded && passwords.size > 1) 1f else 0f,
        animationSpec = tween(200),
        label = "container_alpha"
    )
    
    // 🎯 下滑手势状态
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val haptic = rememberHapticFeedback()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (effectiveExpanded && passwords.size > 1) {
                    Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { 
                                haptic.performLongPress()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // 只允许向下滑动
                                if (dragAmount.y > 0) {
                                    swipeOffset = (swipeOffset + dragAmount.y).coerceAtMost(150f)
                                }
                            },
                            onDragEnd = {
                                // 如果下滑超过阈值，收起卡片组
                                if (swipeOffset > 80f) {
                                    haptic.performSuccess()
                                    onToggleExpand()
                                }
                                swipeOffset = 0f
                            },
                            onDragCancel = {
                                swipeOffset = 0f
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        // 📚 堆叠背后的层级卡片 (仅在堆叠状态下可见，或动画过程中可见)
        val stackAlpha by animateFloatAsState(
            targetValue = if (effectiveExpanded) 0f else 1f,
            animationSpec = tween(200),
            label = "stack_alpha"
        )
        
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 背景堆叠层 (当 stackAlpha > 0 时显示)
            if (passwords.size > 1) {
                val stackCount = passwords.size.coerceAtMost(3)
                for (i in (stackCount - 1) downTo 1) {
                    val offsetDp = (i * 4).dp
                    val scaleFactor = 1f - (i * 0.02f)
                    val layerAlpha = (0.7f - (i * 0.2f)) * stackAlpha
                    
                    if (layerAlpha > 0.01f) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = offsetDp) // Padding top creates the vertical offset effect
                                .graphicsLayer {
                                    scaleX = scaleFactor
                                    scaleY = scaleFactor
                                    alpha = layerAlpha
                                    translationY = (i * 4).dp.toPx() * (1f - stackAlpha) // Optional: slide up when disappearing?
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = (i * 1.5).dp),
                            colors = CardDefaults.cardColors(), // Use default colors to match single cards
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Box(modifier = Modifier.height(76.dp))
                        }
                    }
                }
            }

            // 🎯 主卡片 (持续存在，内容和属性变化)
            val cardElevation by animateDpAsState(
                targetValue = if (effectiveExpanded) 4.dp else 6.dp,
                animationSpec = tween(200),
                label = "elevation"
            )
            val cardShape by animateDpAsState(
                targetValue = if (effectiveExpanded) 16.dp else 14.dp,
                animationSpec = tween(200),
                label = "shape"
            )
            
            val isSelected = selectedPasswords.contains(passwords.first().id)
            val cardColors = if (isSelected) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                CardDefaults.cardColors()
            }
            
            takagi.ru.monica.ui.gestures.SwipeActions(
                onSwipeLeft = { 
                    if (!effectiveExpanded) onSwipeLeft(passwords.first()) 
                    // Expanded state swipe logic handled inside? Or disable swipe on container when expanded?
                },
                onSwipeRight = { 
                    if (!effectiveExpanded) onGroupSwipeRight(passwords)
                },
                isSwiped = passwords.first().id == swipedItemId,
                enabled = !effectiveExpanded // Disable swipe actions on the container when expanded
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = tween(200)
                        )
                        .then(
                            // 展开时的下滑手势
                            if (effectiveExpanded && passwords.size > 1) {
                                Modifier.pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { haptic.performLongPress() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (dragAmount.y > 0) {
                                                swipeOffset = (swipeOffset + dragAmount.y).coerceAtMost(150f)
                                            }
                                        },
                                        onDragEnd = {
                                            if (swipeOffset > 80f) {
                                                haptic.performSuccess()
                                                onToggleExpand()
                                            }
                                            swipeOffset = 0f
                                        },
                                        onDragCancel = { swipeOffset = 0f }
                                    )
                                }
                            } else Modifier
                        )
                        .graphicsLayer {
                            // 下滑时的位移效果
                            if (effectiveExpanded) {
                                translationY = swipeOffset * 0.5f
                            }
                        },
                    shape = RoundedCornerShape(cardShape),
                    elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                    colors = cardColors
                ) {
                    // 内容切换：收起态(Header) vs 展开态(Column)
                    AnimatedContent(
                        targetState = effectiveExpanded,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith 
                            fadeOut(animationSpec = tween(200))
                        },
                        label = "content_switch"
                    ) { expanded ->
                        if (!expanded) {
                            // --- 收起状态的内容 ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleExpand() }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // 🏷️ 数量徽章
                                            Surface(
                                                shape = RoundedCornerShape(18.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shadowElevation = 2.dp
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Layers,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        text = "${passwords.size}",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                            
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = passwords.first().title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (passwords.first().website.isNotBlank()) {
                                                    Text(
                                                        text = passwords.first().website,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isSelectionMode) {
                                                androidx.compose.material3.Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { onGroupSwipeRight(passwords) }
                                                )
                                            } else {
                                                if (isGroupFavorited) {
                                                    Icon(
                                                        Icons.Default.Favorite,
                                                        contentDescription = stringResource(R.string.favorite),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.ExpandMore,
                                                    contentDescription = stringResource(R.string.expand),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // --- 展开状态的内容 ---
                            val edgeInteractionSource = remember { MutableInteractionSource() }
                            val edgeContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                            val edgeBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            val edgeHitWidth = 14.dp
                            val edgeHitHeight = 12.dp
                            val edgeTapModifier = Modifier.clickable(
                                interactionSource = edgeInteractionSource,
                                indication = null,
                                onClick = onToggleExpand
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // 📌 1. 顶部标题栏
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 左侧：密码数量标签
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.passwords_count, passwords.size),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    // 右侧：收起按钮
                                    FilledTonalIconButton(
                                        onClick = onToggleExpand,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ExpandLess,
                                            contentDescription = stringResource(R.string.collapse),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                
                                // 分隔线
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                
                                // 📦 2. 密码列表内容
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(edgeContainerColor)
                                        .border(
                                            width = 1.dp,
                                            color = edgeBorderColor,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = edgeHitWidth, vertical = edgeHitHeight),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val groupedByInfo = passwords.groupBy { getPasswordInfoKey(it) }
                                        
                                        groupedByInfo.values.forEachIndexed { groupIndex, passwordGroup ->
                                            // 列表项动画
                                            val itemEnterDelay = groupIndex * 30
                                            var isVisible by remember { mutableStateOf(false) }
                                            LaunchedEffect(Unit) {
                                                isVisible = true
                                            }
                                            
                                            AnimatedVisibility(
                                                visible = isVisible,
                                                enter = fadeIn(tween(300, delayMillis = itemEnterDelay)) + 
                                                        androidx.compose.animation.slideInVertically(
                                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                            initialOffsetY = { 50 } 
                                                        ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                 takagi.ru.monica.ui.gestures.SwipeActions(
                                                    onSwipeLeft = { onSwipeLeft(passwordGroup.first()) },
                                                    onSwipeRight = { onSwipeRight(passwordGroup.first()) },
                                                    enabled = true
                                                ) {
                                                    if (passwordGroup.size == 1) {
                                                        val password = passwordGroup.first()
                                                        PasswordEntryCard(
                                                            entry = password,
                                                            onClick = {
                                                                if (isSelectionMode) {
                                                                    onToggleSelection(password.id)
                                                                } else {
                                                                    onPasswordClick(password)
                                                                }
                                                            },
                                                            onLongClick = { onLongClick(password) },
                                                            onToggleFavorite = { onToggleFavorite(password) },
                                                            onToggleGroupCover = if (passwords.size > 1) {
                                                                { onToggleGroupCover(password) }
                                                            } else null,
                                                            isSelectionMode = isSelectionMode,
                                                            isSelected = selectedPasswords.contains(password.id),
                                                            canSetGroupCover = passwords.size > 1,
                                                            isInExpandedGroup = true, // We are inside the expanded container
                                                            isSingleCard = false,
                                                            iconCardsEnabled = iconCardsEnabled,
                                                            passwordCardDisplayMode = passwordCardDisplayMode,
                                                            enableSharedBounds = enableSharedBounds
                                                        )
                                                    } else {
                                                        MultiPasswordEntryCard(
                                                            passwords = passwordGroup,
                                                            onClick = { password ->
                                                                if (isSelectionMode) {
                                                                    onToggleSelection(password.id)
                                                                } else {
                                                                    onPasswordClick(password)
                                                                }
                                                            },
                                                            onCardClick = if (!isSelectionMode) {
                                                                { onOpenMultiPasswordDialog(passwordGroup) }
                                                            } else null,
                                                            onLongClick = { onLongClick(passwordGroup.first()) },
                                                            onToggleFavorite = { password -> onToggleFavorite(password) },
                                                            onToggleGroupCover = if (passwords.size > 1) {
                                                                { password -> onToggleGroupCover(password) }
                                                            } else null,
                                                            isSelectionMode = isSelectionMode,
                                                            selectedPasswords = selectedPasswords,
                                                            canSetGroupCover = passwords.size > 1,
                                                            hasGroupCover = hasGroupCover,
                                                            isInExpandedGroup = true, // We are inside the expanded container
                                                            iconCardsEnabled = iconCardsEnabled,
                                                            passwordCardDisplayMode = passwordCardDisplayMode
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Expanded state edge zones: only these non-card areas collapse the stack.
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .fillMaxWidth()
                                            .height(edgeHitHeight)
                                            .then(edgeTapModifier)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(edgeHitHeight)
                                            .then(edgeTapModifier)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .width(edgeHitWidth)
                                            .fillMaxHeight()
                                            .then(edgeTapModifier)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(edgeHitWidth)
                                            .fillMaxHeight()
                                            .then(edgeTapModifier)
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

@Composable
private fun DocumentItemCard(
    item: takagi.ru.monica.data.SecureItem,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false
) {
    takagi.ru.monica.ui.components.DocumentCard(
        item = item,
        onClick = onClick,
        onDelete = onDelete,
        onToggleFavorite = onToggleFavorite,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected
    )
}

/**
 * 多密码合并卡片
 * 显示除密码外其它信息相同的多个密码条目
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MultiPasswordEntryCard(
    passwords: List<takagi.ru.monica.data.PasswordEntry>,
    onClick: (takagi.ru.monica.data.PasswordEntry) -> Unit, // 点击密码按钮 → 进入编辑页面
    onCardClick: (() -> Unit)? = null, // 点击卡片本身 → 打开详情对话框
    onLongClick: () -> Unit = {},
    onToggleFavorite: ((takagi.ru.monica.data.PasswordEntry) -> Unit)? = null,
    onToggleGroupCover: ((takagi.ru.monica.data.PasswordEntry) -> Unit)? = null,
    isSelectionMode: Boolean = false,
    selectedPasswords: Set<Long> = emptySet(),
    canSetGroupCover: Boolean = false,
    hasGroupCover: Boolean = false,
    isInExpandedGroup: Boolean = false,
    iconCardsEnabled: Boolean = false,
    passwordCardDisplayMode: takagi.ru.monica.data.PasswordCardDisplayMode = takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL
) {
    // 使用第一个条目的共同信息
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
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
        elevation = if (isInExpandedGroup) {
            androidx.compose.material3.CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        } else {
            androidx.compose.material3.CardDefaults.cardElevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp
            )
        },
        shape = RoundedCornerShape(if (isInExpandedGroup) 12.dp else 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isInExpandedGroup) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 头部：标题和图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon (NEW) - only show if enabled
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
                    
                    val favicon = if (firstEntry.website.isNotBlank()) {
                        takagi.ru.monica.autofill.ui.rememberFavicon(url = firstEntry.website, enabled = true)
                    } else null
                    
                    if (simpleIcon != null) {
                         Image(
                            bitmap = simpleIcon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                         )
                         Spacer(modifier = Modifier.width(12.dp))
                    } else if (uploadedIcon != null) {
                         Image(
                            bitmap = uploadedIcon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                         )
                         Spacer(modifier = Modifier.width(12.dp))
                    } else if (favicon != null) {
                         Image(
                            bitmap = favicon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                         )
                         Spacer(modifier = Modifier.width(12.dp))
                    } else if (appIcon != null) {
                         Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                         )
                         Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                // 标题
                Text(
                    text = firstEntryTitle,
                    style = if (isInExpandedGroup) {
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // 图标区域
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 数据来源标识 - Bitwarden / KeePass
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

                    // 封面星星图标
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
                                return@forEach // 只显示一个
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
                    
                    // 收藏心形图标
                    if (!isSelectionMode && onToggleFavorite != null) {
                        // 检查是否所有密码都已收藏
                        val allFavorited = passwords.all { it.isFavorite }
                        val anyFavorited = passwords.any { it.isFavorite }
                        
                        IconButton(
                            onClick = { 
                                // 批量切换所有密码的收藏状态
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
            
            // 网站信息
            if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && firstEntry.website.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(if (isInExpandedGroup) 16.dp else 18.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = firstEntry.website,
                        style = if (isInExpandedGroup) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 用户名信息
            if (passwordCardDisplayMode != takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY && firstEntry.username.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(if (isInExpandedGroup) 16.dp else 18.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Text(
                        text = firstEntry.username,
                        style = if (isInExpandedGroup) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 附加信息预览
            val additionalInfo = buildAdditionalInfoPreview(firstEntry)
            if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && additionalInfo.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(if (isInExpandedGroup) 8.dp else 10.dp),
                    color = if (isInExpandedGroup) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (isInExpandedGroup) 12.dp else 14.dp,
                                vertical = if (isInExpandedGroup) 8.dp else 10.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 16.dp else 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        additionalInfo.take(2).forEach { info ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (isInExpandedGroup) 4.dp else 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    info.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isInExpandedGroup) 14.dp else 16.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = info.text,
                                    style = if (isInExpandedGroup) {
                                        MaterialTheme.typography.labelSmall
                                    } else {
                                        MaterialTheme.typography.labelMedium
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
            
            // 密码按钮区域
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
                            modifier = Modifier
                                .heightIn(min = 32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    androidx.compose.material3.Checkbox(
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

/**
 * 密码条目卡片
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun PasswordEntryCard(
    entry: takagi.ru.monica.data.PasswordEntry,
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
    // 当减少动画模式开启时，不使用 sharedBounds 以解决部分设备上的动画卡顿问题
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
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
        elevation = if (isSingleCard) {
            // 单卡片：更突出的阴影
            androidx.compose.material3.CardDefaults.cardElevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp
            )
        } else if (isInExpandedGroup) {
            androidx.compose.material3.CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        } else {
            androidx.compose.material3.CardDefaults.cardElevation()
        },
        shape = if (isSingleCard) {
            RoundedCornerShape(16.dp) // 单卡片：更圆润
        } else {
            RoundedCornerShape(12.dp)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(
                    if (isSingleCard) 20.dp else 16.dp // 单卡片：更大的padding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon - only show if enabled
            if (iconCardsEnabled) {
                val simpleIcon = if (entry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_SIMPLE) {
                    takagi.ru.monica.ui.icons.rememberSimpleIconBitmap(
                        slug = entry.customIconValue,
                        tintColor = MaterialTheme.colorScheme.primary,
                        enabled = true
                    )
                } else {
                    null
                }
                val uploadedIcon = if (entry.customIconType == takagi.ru.monica.ui.icons.PASSWORD_ICON_TYPE_UPLOADED) {
                    takagi.ru.monica.ui.icons.rememberUploadedPasswordIcon(entry.customIconValue)
                } else {
                    null
                }
                val appIcon = if (!entry.appPackageName.isNullOrBlank()) {
                     takagi.ru.monica.autofill.ui.rememberAppIcon(entry.appPackageName)
                } else null
                
                val favicon = if (entry.website.isNotBlank()) {
                    takagi.ru.monica.autofill.ui.rememberFavicon(url = entry.website, enabled = true)
                } else null
                
                if (simpleIcon != null) {
                     Image(
                        bitmap = simpleIcon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                     )
                     Spacer(modifier = Modifier.width(16.dp))
                } else if (uploadedIcon != null) {
                     Image(
                        bitmap = uploadedIcon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                     )
                     Spacer(modifier = Modifier.width(16.dp))
                } else if (favicon != null) {
                     Image(
                        bitmap = favicon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                     )
                     Spacer(modifier = Modifier.width(16.dp))
                } else if (appIcon != null) {
                     Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                     )
                     Spacer(modifier = Modifier.width(16.dp))
                } else {
                     // Key Icon
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
                verticalArrangement = Arrangement.spacedBy(
                    if (isSingleCard) 8.dp else 6.dp // 单卡片：更大的间距
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 标题 - 优化样式
                    Text(
                        text = displayTitle,
                        style = if (isSingleCard) {
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // 图标区域 - 优化布局
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 数据来源标识 - Bitwarden / KeePass
                        if (entry.isBitwardenEntry()) {
                            // Bitwarden 同步状态标识
                            val syncStatus = when {
                                entry.hasPendingBitwardenSync() -> SyncStatus.PENDING
                                else -> SyncStatus.SYNCED
                            }
                            SyncStatusIcon(
                                status = syncStatus,
                                size = 16.dp
                            )
                        } else if (entry.isKeePassEntry()) {
                            // KeePass 密钥标识
                            Icon(
                                Icons.Default.VpnKey,
                                contentDescription = "KeePass",
                                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // 封面星星图标 - 仅在组内且非选择模式下显示
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
                        
                        // 收藏心形图标 - 非选择模式下可点击
                        if (!isSelectionMode && onToggleFavorite != null) {
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (entry.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(R.string.favorite),
                                    tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isSelectionMode) {
                            androidx.compose.material3.Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onClick() }
                            )
                        }
                    }
                }
                
                // 网站信息 - 优化显示
                if (passwordCardDisplayMode == takagi.ru.monica.data.PasswordCardDisplayMode.SHOW_ALL && entry.website.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            if (isSingleCard) 8.dp else 6.dp
                        ),
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
                            style = if (isSingleCard) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 用户名信息 - 优化显示
                if (passwordCardDisplayMode != takagi.ru.monica.data.PasswordCardDisplayMode.TITLE_ONLY && entry.username.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            if (isSingleCard) 8.dp else 10.dp
                        ),
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
                            style = if (isSingleCard) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 新字段预览 - 优化显示样式
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
                            horizontalArrangement = Arrangement.spacedBy(
                                if (isSingleCard) 20.dp else 16.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            additionalInfo.take(2).forEach { info ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(
                                        if (isSingleCard) 6.dp else 4.dp
                                    ),
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

/**
 * 附加信息预览数据类
 * 
 * 用于在密码卡片上显示额外的预览信息。
 * 
 * @param icon Material Icon 图标
 * @param text 显示文本（已格式化或掩码处理）
 */
private data class AdditionalInfoItem(
    val icon: ImageVector,
    val text: String
)

/**
 * 构建附加信息预览列表
 * 
 * 根据优先级从密码条目中提取最重要的附加信息用于卡片预览。
 * 
 * ## 优先级顺序
 * 1. � **关联应用** (appName) - 显示应用图标和名称
 * 2. �📧 **邮箱** (email) - 最常用的登录凭证
 * 3. 📱 **手机号** (phone) - 账户绑定信息，自动格式化显示
 * 4. 💳 **信用卡号** (creditCard) - 支付信息，掩码显示仅后4位
 * 5. 📍 **城市** (city) - 地址信息代表
 * 
 * ## 显示规则
 * - 最多返回 **2项** 预览信息，避免卡片拥挤
 * - 使用 FieldValidation 工具类进行格式化和掩码处理
 * - 空字段自动跳过
 * 
 * ## 示例输出
 * ```
 * � 微信  �📧 user@example.com
 * 📱 +86 138 0013 8000  💳 •••• •••• •••• 1234
 * ```
 * 
 * @param entry 密码条目
 * @return 附加信息列表，最多2项
 */
private fun buildAdditionalInfoPreview(entry: takagi.ru.monica.data.PasswordEntry): List<AdditionalInfoItem> {
    val items = mutableListOf<AdditionalInfoItem>()
    
    // 1. 关联应用（最高优先级）
    if (entry.appName.isNotBlank()) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.Apps,
            text = entry.appName
        ))
    }
    
    // 2. 邮箱
    if (entry.email.isNotBlank() && items.size < 2) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.Email,
            text = entry.email
        ))
    }
    
    // 3. 手机号
    if (entry.phone.isNotBlank() && items.size < 2) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.Phone,
            text = takagi.ru.monica.utils.FieldValidation.formatPhone(entry.phone)
        ))
    }
    
    // 4. 信用卡号（掩码显示）
    if (entry.creditCardNumber.isNotBlank() && items.size < 2) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.CreditCard,
            text = takagi.ru.monica.utils.FieldValidation.maskCreditCard(entry.creditCardNumber)
        ))
    }
    
    // 5. 城市信息（如果还有空间）
    if (entry.city.isNotBlank() && items.size < 2) {
        items.add(AdditionalInfoItem(
            icon = Icons.Default.LocationOn,
            text = entry.city
        ))
    }
    
    return items
}

/**
 * 选择模式顶栏组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionModeTopBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMoveToCategory: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { 
            // 使用 Row 确保文本不会被截断
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.selected_items, selectedCount),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(
                    Icons.Default.Close, 
                    contentDescription = stringResource(R.string.exit_selection_mode)
                )
            }
        },
        actions = {
            // 使用 Row 确保图标按钮不会被挤压
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 全选/取消全选
                IconButton(onClick = onSelectAll) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = stringResource(R.string.select_all)
                    )
                }
                
                // 批量移动到分类
                if (onMoveToCategory != null) {
                    IconButton(
                        onClick = onMoveToCategory,
                        enabled = selectedCount > 0
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = stringResource(R.string.move_to_category),
                            tint = if (selectedCount > 0) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                // 批量收藏按钮 (仅部分列表显示)
                if (onFavorite != null) {
                    IconButton(
                        onClick = onFavorite,
                        enabled = selectedCount > 0
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.batch_favorite),
                            tint = if (selectedCount > 0) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
                
                // 批量删除按钮
                IconButton(
                    onClick = onDelete,
                    enabled = selectedCount > 0
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.batch_delete),
                        tint = if (selectedCount > 0) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

private const val SETTINGS_TAB_KEY = "SETTINGS"

@Composable
private fun CategoryListItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    menu: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = containerColor,
            headlineColor = contentColor,
            leadingIconColor = contentColor,
            trailingIconColor = contentColor
        ),
        leadingContent = {
            Icon(icon, contentDescription = null)
        },
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = badge,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = contentColor)
                }
                menu?.invoke()
            }
        }
    )
}

sealed class BottomNavItem(
    val contentTab: BottomNavContentTab?,
    val icon: ImageVector
) {
    val key: String = contentTab?.name ?: SETTINGS_TAB_KEY

    object Passwords : BottomNavItem(BottomNavContentTab.PASSWORDS, Icons.Default.Lock)
    object Authenticator : BottomNavItem(BottomNavContentTab.AUTHENTICATOR, Icons.Default.Security)
    object CardWallet : BottomNavItem(BottomNavContentTab.CARD_WALLET, Icons.Default.Wallet)
    object Generator : BottomNavItem(BottomNavContentTab.GENERATOR, Icons.Default.AutoAwesome)
    object Notes : BottomNavItem(BottomNavContentTab.NOTES, Icons.Default.Note)
    object Send : BottomNavItem(BottomNavContentTab.SEND, Icons.Default.Send)
    object Timeline : BottomNavItem(BottomNavContentTab.TIMELINE, Icons.Default.AccountTree)
    object Passkey : BottomNavItem(BottomNavContentTab.PASSKEY, Icons.Default.Key)
    object Settings : BottomNavItem(null, Icons.Default.Settings)
}

private fun BottomNavContentTab.toBottomNavItem(): BottomNavItem = when (this) {
    BottomNavContentTab.PASSWORDS -> BottomNavItem.Passwords
    BottomNavContentTab.AUTHENTICATOR -> BottomNavItem.Authenticator
    BottomNavContentTab.CARD_WALLET -> BottomNavItem.CardWallet
    BottomNavContentTab.GENERATOR -> BottomNavItem.Generator
    BottomNavContentTab.NOTES -> BottomNavItem.Notes
    BottomNavContentTab.SEND -> BottomNavItem.Send
    BottomNavContentTab.TIMELINE -> BottomNavItem.Timeline
    BottomNavContentTab.PASSKEY -> BottomNavItem.Passkey
}

private fun BottomNavItem.fullLabelRes(): Int = when (this) {
    BottomNavItem.Passwords -> R.string.nav_passwords
    BottomNavItem.Authenticator -> R.string.nav_authenticator
    BottomNavItem.CardWallet -> R.string.nav_card_wallet
    BottomNavItem.Generator -> R.string.nav_generator
    BottomNavItem.Notes -> R.string.nav_notes
    BottomNavItem.Send -> R.string.nav_v2_send
    BottomNavItem.Timeline -> R.string.nav_timeline
    BottomNavItem.Passkey -> R.string.nav_passkey
    BottomNavItem.Settings -> R.string.nav_settings
}

private fun BottomNavItem.shortLabelRes(): Int = when (this) {
    BottomNavItem.Passwords -> R.string.nav_passwords_short
    BottomNavItem.Authenticator -> R.string.nav_authenticator_short
    BottomNavItem.CardWallet -> R.string.nav_card_wallet_short
    BottomNavItem.Generator -> R.string.nav_generator_short
    BottomNavItem.Notes -> R.string.nav_notes_short
    BottomNavItem.Send -> R.string.nav_v2_send_short
    BottomNavItem.Timeline -> R.string.nav_timeline_short
    BottomNavItem.Passkey -> R.string.nav_passkey_short
    BottomNavItem.Settings -> R.string.nav_settings_short
}

private fun indexToDefaultTabKey(index: Int): String = when (index) {
    0 -> BottomNavContentTab.PASSWORDS.name
    1 -> BottomNavContentTab.AUTHENTICATOR.name
    2 -> BottomNavContentTab.CARD_WALLET.name
    3 -> BottomNavContentTab.GENERATOR.name
    4 -> BottomNavContentTab.NOTES.name
    5 -> SETTINGS_TAB_KEY
    else -> BottomNavContentTab.PASSWORDS.name
}

/**
 * 支持指纹验证的删除确认对话框
 * 
 * @param itemTitle 要删除的项目标题
 * @param itemType 项目类型描述（如"密码"、"验证器"、"证件"）
 * @param onDismiss 取消删除的回调
 * @param onConfirmWithPassword 使用密码确认删除的回调
 * @param onConfirmWithBiometric 使用指纹确认删除的回调
 */
@Composable
private fun DeleteConfirmDialog(
    itemTitle: String,
    itemType: String = "Item",
    biometricEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirmWithPassword: (String) -> Unit,
    onConfirmWithBiometric: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var passwordInput by remember { mutableStateOf("") }
    val biometricHelper = remember { BiometricHelper(context) }
    val isBiometricAvailable = remember(biometricEnabled) {
        biometricEnabled && biometricHelper.isBiometricAvailable()
    }

    val biometricAction = if (isBiometricAvailable && activity != null) {
        {
            biometricHelper.authenticate(
                activity = activity,
                title = context.getString(R.string.verify_identity),
                subtitle = context.getString(R.string.verify_to_delete),
                description = context.getString(R.string.biometric_login_description),
                onSuccess = {
                    onConfirmWithBiometric()
                },
                onError = { error ->
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                },
                onFailed = {}
            )
        }
    } else {
        null
    }
    M3IdentityVerifyDialog(
        title = stringResource(R.string.delete_item_title, itemType),
        message = stringResource(R.string.delete_item_message, itemType, itemTitle),
        passwordValue = passwordInput,
        onPasswordChange = { passwordInput = it },
        onDismiss = onDismiss,
        onConfirm = {
            if (passwordInput.isNotEmpty()) {
                onConfirmWithPassword(passwordInput)
            }
        },
        confirmText = stringResource(R.string.delete),
        destructiveConfirm = true,
        onBiometricClick = biometricAction,
        biometricHintText = if (biometricAction == null) {
            context.getString(R.string.biometric_not_available)
        } else {
            null
        }
    )
}

/**
 * 为密码条目生成“同一信息组”键（不含密码值）。
 *
 * 这里必须与详情页/编辑页的 sibling 分组规则保持一致，
 * 否则会出现“列表显示多密码，点进去只有一条”的不一致。
 */
private fun getPasswordInfoKey(entry: takagi.ru.monica.data.PasswordEntry): String {
    val sourceKey = buildPasswordSourceKey(entry)
    val title = entry.title.trim().lowercase(Locale.ROOT)
    val username = entry.username.trim().lowercase(Locale.ROOT)
    val website = normalizeWebsiteForGroupKey(entry.website)
    return "$sourceKey|$title|$website|$username"
}

private fun buildPasswordSourceKey(entry: takagi.ru.monica.data.PasswordEntry): String {
    return when {
        !entry.bitwardenCipherId.isNullOrBlank() ->
            "bw:${entry.bitwardenVaultId}:${entry.bitwardenCipherId}"
        entry.bitwardenVaultId != null ->
            "bw-local:${entry.bitwardenVaultId}:${entry.bitwardenFolderId.orEmpty()}"
        entry.keepassDatabaseId != null ->
            "kp:${entry.keepassDatabaseId}:${entry.keepassGroupPath.orEmpty()}"
        else -> "local"
    }
}

private fun normalizeWebsiteForGroupKey(value: String): String {
    val raw = value.trim()
    if (raw.isEmpty()) return ""
    return raw
        .lowercase(Locale.ROOT)
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .trimEnd('/')
}

/**
 * 生成密码分组标题,按备注>网站>应用>标题的优先顺序选择第一个非空字段
 */
private fun getGroupKeyForMode(entry: takagi.ru.monica.data.PasswordEntry, mode: String): String {
    val noteLabel = entry.notes
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
    val website = entry.website.trim()
    val appName = entry.appName.trim()
    val packageName = entry.appPackageName.trim()
    val title = entry.title.trim()
    val idKey = "id-${entry.id}"

    return when (mode) {
        // 只按备注；若备注为空则不分组（使用唯一键避免堆叠）
        "note" -> noteLabel.takeUnless { it.isNullOrEmpty() } ?: idKey

        // 只按网站；若网站为空则不分组
        "website" -> website.takeUnless { it.isEmpty() } ?: idKey

        // 只按应用；若应用名/包名都空则不分组
        "app" -> appName.takeUnless { it.isEmpty() }
            ?: packageName.takeUnless { it.isEmpty() }
            ?: idKey

        // 只按标题；若标题为空则不分组
        "title" -> title.takeUnless { it.isEmpty() } ?: idKey

        else -> {
            // smart: 备注 > 网站 > 应用 > 标题，若都空则不分组
            noteLabel.takeUnless { it.isNullOrEmpty() }
                ?: website.takeUnless { it.isEmpty() }
                ?: appName.takeUnless { it.isEmpty() }
                ?: packageName.takeUnless { it.isEmpty() }
                ?: title.takeUnless { it.isEmpty() }
                ?: idKey
        }
    }
}

private fun getPasswordGroupTitle(entry: takagi.ru.monica.data.PasswordEntry): String =
    getGroupKeyForMode(entry, "smart")

private enum class StackCardMode {
    AUTO,
    ALWAYS_EXPANDED
}

@Composable
private fun AdaptiveMainScaffold(
    isCompactWidth: Boolean,
    tabs: List<BottomNavItem>,
    currentTab: BottomNavItem,
    onTabSelected: (BottomNavItem) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        bottomBar = {
            if (isCompactWidth) {
                NavigationBar(
                    tonalElevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEach { item ->
                        val label = stringResource(item.shortLabelRes())
                        NavigationBarItem(
                            selected = item.key == currentTab.key,
                            onClick = { onTabSelected(item) },
                            icon = { Icon(item.icon, contentDescription = label) },
                            label = {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {}
    ) { paddingValues ->
        if (isCompactWidth) {
            content(paddingValues)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tabs.forEach { item ->
                            val label = stringResource(item.shortLabelRes())
                            NavigationRailItem(
                                selected = item.key == currentTab.key,
                                onClick = { onTabSelected(item) },
                                icon = { Icon(item.icon, contentDescription = label) },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    content(PaddingValues())
                }
            }
        }
    }
}

private val adaptivePreviewTabs = listOf(
    BottomNavItem.Passwords,
    BottomNavItem.Authenticator,
    BottomNavItem.CardWallet,
    BottomNavItem.Generator,
    BottomNavItem.Notes,
    BottomNavItem.Send,
    BottomNavItem.Timeline,
    BottomNavItem.Passkey,
    BottomNavItem.Settings
)

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true, name = "Adaptive Phone")
@Composable
private fun AdaptiveMainScaffoldPhonePreview() {
    MonicaTheme {
        AdaptiveMainScaffold(
            isCompactWidth = true,
            tabs = adaptivePreviewTabs,
            currentTab = BottomNavItem.Passwords,
            onTabSelected = {}
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Phone Content")
            }
        }
    }
}

@Preview(
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true,
    name = "Adaptive Tablet"
)
@Composable
private fun AdaptiveMainScaffoldTabletPreview() {
    MonicaTheme {
        AdaptiveMainScaffold(
            isCompactWidth = false,
            tabs = adaptivePreviewTabs,
            currentTab = BottomNavItem.Passwords,
            onTabSelected = {}
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Tablet Content")
            }
        }
    }
}

@Composable
private fun ListPanePreviewContent(modifier: Modifier = Modifier) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    ListPane(modifier = modifier) {
        ExpressiveTopBar(
            title = "Passwords",
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            isSearchExpanded = isSearchExpanded,
            onSearchExpandedChange = { isSearchExpanded = it },
            searchHint = "Search..."
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(12) { index ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = "Sample Item ${index + 1}",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Preview(device = "spec:width=411dp,height=891dp", showBackground = true, name = "Stage2 ListPane Phone")
@Composable
private fun Stage2ListPanePhonePreview() {
    MonicaTheme {
        ListPanePreviewContent(modifier = Modifier.fillMaxSize())
    }
}

@Preview(
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true,
    name = "Stage2 TwoPane Tablet"
)
@Composable
private fun Stage2TwoPaneTabletPreview() {
    MonicaTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            ListPanePreviewContent(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(400.dp)
            )

            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }
}

@Composable
private fun Stage3TwoPanePreviewContent(selectedPasswordId: Long?) {
    Row(modifier = Modifier.fillMaxSize()) {
        ListPanePreviewContent(
            modifier = Modifier
                .fillMaxHeight()
                .width(400.dp)
        )

        DetailPane(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedPasswordId?.let { "Selected ID: $it" } ?: "Select an item to view details",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true,
    name = "Stage3 TwoPane Empty Detail"
)
@Composable
private fun Stage3TwoPaneEmptyDetailPreview() {
    MonicaTheme {
        Stage3TwoPanePreviewContent(selectedPasswordId = null)
    }
}

@Preview(
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true,
    name = "Stage3 TwoPane Selected Detail"
)
@Composable
private fun Stage3TwoPaneSelectedDetailPreview() {
    MonicaTheme {
        Stage3TwoPanePreviewContent(selectedPasswordId = 42L)
    }
}

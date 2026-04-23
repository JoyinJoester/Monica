package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.AddButtonBehaviorMode
import takagi.ru.monica.data.AddButtonMenuAction
import takagi.ru.monica.data.AppLauncherIcon
import takagi.ru.monica.data.AuthenticatorCardDisplayField
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.ProgressBarStyle
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.UnifiedProgressBarMode
import takagi.ru.monica.data.UnmatchedIconHandlingStrategy
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.ui.components.TotpCodeCard
import takagi.ru.monica.ui.password.appendAggregateContentQuickFilterItems
import takagi.ru.monica.ui.password.PasswordEntryCard
import takagi.ru.monica.ui.password.StackCardMode
import takagi.ru.monica.viewmodel.SettingsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageAdjustmentCustomizationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPasswordListCustomization: () -> Unit,
    onNavigateToPasswordCardAdjustment: () -> Unit,
    onNavigateToAuthenticatorCardAdjustment: () -> Unit,
    onNavigateToPasswordFieldCustomization: () -> Unit,
    onNavigateToIconSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.page_adjust_custom_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.page_adjust_custom_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PageAdjustmentEntryCard(
                title = stringResource(R.string.password_list_customization_title),
                subtitle = stringResource(R.string.password_list_customization_subtitle),
                icon = Icons.Default.FilterList,
                onClick = onNavigateToPasswordListCustomization
            )

            PageAdjustmentEntryCard(
                title = stringResource(R.string.password_card_adjust_title),
                subtitle = stringResource(R.string.password_card_adjust_subtitle),
                icon = Icons.Default.Apps,
                onClick = onNavigateToPasswordCardAdjustment
            )

            PageAdjustmentEntryCard(
                title = stringResource(R.string.authenticator_card_adjust_title),
                subtitle = stringResource(R.string.authenticator_card_adjust_subtitle),
                icon = Icons.Default.Security,
                onClick = onNavigateToAuthenticatorCardAdjustment
            )

            PageAdjustmentEntryCard(
                title = stringResource(R.string.password_field_customization_title),
                subtitle = stringResource(R.string.extensions_password_field_customization_desc),
                icon = Icons.Default.Tune,
                onClick = onNavigateToPasswordFieldCustomization
            )

            PageAdjustmentEntryCard(
                title = stringResource(R.string.icon_settings_title),
                subtitle = stringResource(R.string.icon_settings_subtitle),
                icon = Icons.Default.Key,
                onClick = onNavigateToIconSettings
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddButtonCustomizationScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val supportedActions = remember {
        listOf(
            AddButtonMenuAction.PASSWORD,
            AddButtonMenuAction.NOTE,
            AddButtonMenuAction.AUTHENTICATOR,
            AddButtonMenuAction.BANK_CARD
        )
    }
    val enabledActions = remember(settings.addButtonMenuEnabledActions, settings.addButtonMenuOrder) {
        mutableStateListOf<AddButtonMenuAction>().apply {
            addAll(
                AddButtonMenuAction.normalizeEnabledActions(
                    settings.addButtonMenuEnabledActions,
                    settings.addButtonMenuOrder
                ).filter { supportedActions.contains(it) }
            )
        }
    }
    var actionOrder by remember(settings.addButtonMenuOrder) {
        mutableStateOf(
            buildList {
                settings.addButtonMenuOrder
                    .filter { supportedActions.contains(it) }
                    .forEach { add(it) }
                supportedActions
                    .filter { !contains(it) }
                    .forEach { add(it) }
            }
        )
    }
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        actionOrder = actionOrder.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        viewModel.updateAddButtonMenuOrder(actionOrder)
    }
    val previewActions = remember(actionOrder, enabledActions) {
        actionOrder.filter { enabledActions.contains(it) }
    }

    LaunchedEffect(settings.addButtonMenuEnabledActions, settings.addButtonMenuOrder) {
        val normalized = AddButtonMenuAction.normalizeEnabledActions(
            settings.addButtonMenuEnabledActions,
            settings.addButtonMenuOrder
        )
        if (normalized != settings.addButtonMenuEnabledActions) {
            viewModel.updateAddButtonMenuEnabledActions(normalized)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.add_button_customization_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = !reorderableState.isAnyItemDragging
        ) {
            item {
                Text(
                    text = stringResource(R.string.add_button_customization_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.add_button_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (settings.addButtonBehaviorMode == AddButtonBehaviorMode.DIRECT_PASSWORD) {
                                stringResource(R.string.add_button_mode_direct_desc)
                            } else {
                                stringResource(R.string.add_button_mode_expand_desc)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f)
                        )

                        if (settings.addButtonBehaviorMode == AddButtonBehaviorMode.DIRECT_PASSWORD) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = AddButtonMenuAction.PASSWORD.toIcon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(AddButtonMenuAction.PASSWORD.toLabelRes()),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        } else if (previewActions.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                previewActions.forEach { action ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {},
                                        label = { Text(stringResource(action.toLabelRes())) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = action.toIcon(),
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.add_button_mode_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.add_button_mode_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                AddButtonBehaviorMode.DIRECT_PASSWORD to stringResource(R.string.add_button_mode_direct),
                                AddButtonBehaviorMode.EXPANDABLE_MENU to stringResource(R.string.add_button_mode_expand)
                            ).forEachIndexed { index, (mode, label) ->
                                SegmentedButton(
                                    selected = settings.addButtonBehaviorMode == mode,
                                    onClick = { viewModel.updateAddButtonBehaviorMode(mode) },
                                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = 2
                                    ),
                                    label = { Text(text = label) }
                                )
                            }
                        }
                    }
                }
            }

            if (settings.addButtonBehaviorMode == AddButtonBehaviorMode.EXPANDABLE_MENU) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.add_button_actions_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.add_button_actions_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(
                    items = actionOrder,
                    key = { it.name }
                ) { action ->
                    val enabled = enabledActions.contains(action)
                    val switchEnabled = action != AddButtonMenuAction.PASSWORD
                    ReorderableItem(
                        reorderableState,
                        key = action.name,
                        enabled = true
                    ) { isDragging ->
                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 0.dp,
                            label = "add_button_action_drag_elevation"
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation, RoundedCornerShape(18.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(action.toIcon(), contentDescription = null)
                                Spacer(modifier = Modifier.size(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(action.toLabelRes()),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = if (action == AddButtonMenuAction.PASSWORD) {
                                            stringResource(R.string.add_button_password_required)
                                        } else {
                                            stringResource(R.string.add_button_action_toggle_hint)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (enabled) {
                                        "${previewActions.indexOf(action) + 1}"
                                    } else {
                                        stringResource(R.string.hide)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .longPressDraggableHandle(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.DragIndicator, contentDescription = null)
                                }
                                Switch(
                                    checked = enabled,
                                    enabled = switchEnabled,
                                    onCheckedChange = { checked ->
                                        if (switchEnabled) {
                                            val newEnabled = actionOrder.filter { current ->
                                                if (current == action) checked else enabledActions.contains(current)
                                            }
                                            enabledActions.clear()
                                            enabledActions.addAll(newEnabled)
                                            viewModel.updateAddButtonMenuEnabledActions(newEnabled)
                                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListCustomizationScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val supportedContentTypes = remember {
        listOf(
            PasswordPageContentType.PASSWORD,
            PasswordPageContentType.CARD_WALLET,
            PasswordPageContentType.NOTE,
            PasswordPageContentType.AUTHENTICATOR,
            PasswordPageContentType.PASSKEY
        )
    }
    val selectedContentTypes = remember(settings.passwordPageVisibleContentTypes) {
        mutableStateListOf<PasswordPageContentType>().apply {
            addAll(
                PasswordPageContentType.normalizeEnabledTypes(
                    settings.passwordPageVisibleContentTypes
                ).filter { supportedContentTypes.contains(it) }
            )
        }
    }
    LaunchedEffect(settings.passwordPageVisibleContentTypes) {
        val normalized = PasswordPageContentType.normalizeEnabledTypes(
            settings.passwordPageVisibleContentTypes
        ).filter { supportedContentTypes.contains(it) }
        if (normalized != settings.passwordPageVisibleContentTypes) {
            viewModel.updatePasswordPageVisibleContentTypes(normalized)
        }
        selectedContentTypes.clear()
        selectedContentTypes.addAll(normalized)
    }
    var contentTypeOrder by remember(settings.passwordPageVisibleContentTypes) {
        mutableStateOf(
            buildList {
                PasswordPageContentType.normalizeEnabledTypes(settings.passwordPageVisibleContentTypes)
                    .filter { supportedContentTypes.contains(it) }
                    .forEach { add(it) }
                supportedContentTypes
                    .filter { !contains(it) }
                    .forEach { add(it) }
            }
        )
    }
    val contentTypeOptions = supportedContentTypes.map { type ->
        PasswordPageContentTypeOption(
            type = type,
            title = stringResource(type.toLabelRes()),
            icon = type.toIcon()
        )
    }
    val allAddButtonActions = remember {
        AddButtonMenuAction.values().toList()
    }

    fun syncAddButtonMenuFromContent(
        order: List<PasswordPageContentType>,
        enabledTypes: List<PasswordPageContentType>
    ) {
        val mappedOrder = order
            .mapNotNull { it.toAddButtonMenuActionOrNull() }
            .distinct()
        val targetOrder = buildList {
            addAll(mappedOrder)
            allAddButtonActions.forEach { action ->
                if (action !in this) {
                    add(action)
                }
            }
        }

        val mappedEnabled = enabledTypes
            .mapNotNull { it.toAddButtonMenuActionOrNull() }
            .distinct()

        val normalizedCurrentOrder = AddButtonMenuAction.sanitizeOrder(settings.addButtonMenuOrder)
        if (targetOrder != normalizedCurrentOrder) {
            viewModel.updateAddButtonMenuOrder(targetOrder)
        }

        val normalizedCurrentEnabled = AddButtonMenuAction.normalizeEnabledActions(
            settings.addButtonMenuEnabledActions,
            settings.addButtonMenuOrder
        )
        if (mappedEnabled != normalizedCurrentEnabled) {
            viewModel.updateAddButtonMenuEnabledActions(mappedEnabled)
        }
    }

    LaunchedEffect(
        settings.passwordPageVisibleContentTypes,
        settings.addButtonMenuOrder,
        settings.addButtonMenuEnabledActions
    ) {
        val normalizedTypes = PasswordPageContentType.normalizeEnabledTypes(
            settings.passwordPageVisibleContentTypes
        ).filter { supportedContentTypes.contains(it) }
        syncAddButtonMenuFromContent(
            order = normalizedTypes,
            enabledTypes = normalizedTypes
        )
    }

    LaunchedEffect(settings.passwordPageAggregateEnabled, settings.addButtonBehaviorMode) {
        val expectedMode = if (settings.passwordPageAggregateEnabled) {
            AddButtonBehaviorMode.EXPANDABLE_MENU
        } else {
            AddButtonBehaviorMode.DIRECT_PASSWORD
        }
        if (settings.addButtonBehaviorMode != expectedMode) {
            viewModel.updateAddButtonBehaviorMode(expectedMode)
        }
    }

    val supportedQuickFilterItems = remember {
        listOf(
            PasswordListQuickFilterItem.FAVORITE,
            PasswordListQuickFilterItem.TWO_FA,
            PasswordListQuickFilterItem.NOTES,
            PasswordListQuickFilterItem.UNCATEGORIZED,
            PasswordListQuickFilterItem.LOCAL_ONLY,
            PasswordListQuickFilterItem.MANUAL_STACK_ONLY,
            PasswordListQuickFilterItem.NEVER_STACK,
            PasswordListQuickFilterItem.UNSTACKED,
            PasswordListQuickFilterItem.CARD_WALLET,
            PasswordListQuickFilterItem.PASSKEY,
            PasswordListQuickFilterItem.NOTE
        )
    }
    val effectiveQuickFilterItems = remember(
        settings.passwordListQuickFilterItems,
        settings.passwordPageVisibleContentTypes,
        settings.passwordPageAggregateEnabled
    ) {
        appendAggregateContentQuickFilterItems(
            configuredItems = settings.passwordListQuickFilterItems,
            visibleTypes = settings.passwordPageVisibleContentTypes,
            aggregateEnabled = settings.passwordPageAggregateEnabled
        )
    }
    val selectedQuickFilterItems = remember(effectiveQuickFilterItems) {
        mutableStateListOf<PasswordListQuickFilterItem>().apply {
            addAll(
                effectiveQuickFilterItems
                    .filter { supportedQuickFilterItems.contains(it) }
                    .distinct()
            )
        }
    }
    LaunchedEffect(effectiveQuickFilterItems) {
        val normalized = effectiveQuickFilterItems
            .filter { supportedQuickFilterItems.contains(it) }
            .distinct()
        if (normalized != settings.passwordListQuickFilterItems) {
            viewModel.updatePasswordListQuickFilterItems(normalized)
        }
    }
    var quickFilterOrder by remember(effectiveQuickFilterItems) {
        mutableStateOf(
            buildList {
                effectiveQuickFilterItems
                    .filter { supportedQuickFilterItems.contains(it) }
                    .forEach { add(it) }
                supportedQuickFilterItems
                    .filter { !contains(it) }
                    .forEach { add(it) }
            }
        )
    }
    val quickFilterOptions = listOf(
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.FAVORITE,
            title = stringResource(R.string.password_list_quick_filter_favorite),
            icon = if (selectedQuickFilterItems.contains(PasswordListQuickFilterItem.FAVORITE)) {
                Icons.Default.Favorite
            } else {
                Icons.Default.FilterList
            }
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.TWO_FA,
            title = stringResource(R.string.password_list_quick_filter_2fa),
            icon = Icons.Default.Security
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.NOTES,
            title = stringResource(R.string.password_list_quick_filter_notes),
            icon = Icons.Default.Description
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.UNCATEGORIZED,
            title = stringResource(R.string.password_list_quick_filter_uncategorized),
            icon = Icons.Default.Folder
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.LOCAL_ONLY,
            title = stringResource(R.string.password_list_quick_filter_local_only),
            icon = Icons.Default.Key
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.MANUAL_STACK_ONLY,
            title = stringResource(R.string.password_list_quick_filter_manual_stack_only),
            icon = Icons.Default.Apps
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.NEVER_STACK,
            title = stringResource(R.string.password_list_quick_filter_never_stack),
            icon = Icons.Default.LinearScale
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.UNSTACKED,
            title = stringResource(R.string.password_list_quick_filter_unstacked),
            icon = Icons.Default.Straighten
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.CARD_WALLET,
            title = stringResource(R.string.nav_card_wallet),
            icon = Icons.Default.CreditCard
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.PASSKEY,
            title = stringResource(R.string.nav_passkey),
            icon = Icons.Default.VpnKey
        ),
        PasswordListQuickFilterOption(
            item = PasswordListQuickFilterItem.NOTE,
            title = stringResource(R.string.nav_notes),
            icon = Icons.Default.Description
        )
    )

    var previewAggregateEnabled by remember(settings.passwordPageAggregateEnabled) {
        mutableStateOf(settings.passwordPageAggregateEnabled)
    }
    var previewQuickFiltersEnabled by remember(settings.passwordListQuickFiltersEnabled) {
        mutableStateOf(settings.passwordListQuickFiltersEnabled)
    }
    var previewCategoryQuickFiltersEnabled by remember(settings.passwordListCategoryQuickFiltersEnabled) {
        mutableStateOf(settings.passwordListCategoryQuickFiltersEnabled)
    }
    var previewQuickFolderPathBannerEnabled by remember(settings.passwordListQuickFolderPathBannerEnabled) {
        mutableStateOf(settings.passwordListQuickFolderPathBannerEnabled)
    }
    var previewSystemBackToParentFolderEnabled by remember(settings.passwordListSystemBackToParentFolderEnabled) {
        mutableStateOf(settings.passwordListSystemBackToParentFolderEnabled)
    }
    var previewQuickAccessEnabled by remember(settings.passwordListQuickAccessEnabled) {
        mutableStateOf(settings.passwordListQuickAccessEnabled)
    }

    var aggregateSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var quickFiltersSectionExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        previewAggregateEnabled,
        previewQuickFiltersEnabled,
        previewQuickAccessEnabled
    ) {
        if (!previewAggregateEnabled) {
            aggregateSectionExpanded = false
        }
        if (!previewQuickFiltersEnabled) {
            quickFiltersSectionExpanded = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.password_list_customization_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.password_list_customization_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
                )
            ) {
                Column {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.password_list_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.password_list_preview_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (previewQuickFiltersEnabled && selectedQuickFilterItems.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedQuickFilterItems.forEach { item ->
                                    when (item) {
                                        PasswordListQuickFilterItem.FAVORITE -> {
                                            FilterChip(
                                                selected = true,
                                                onClick = {},
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_favorite)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Favorite,
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                        }

                                        PasswordListQuickFilterItem.TWO_FA -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = {},
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_2fa)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Security,
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                        }

                                        PasswordListQuickFilterItem.NOTES -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = {},
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_notes)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Description,
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                        }

                                        PasswordListQuickFilterItem.UNCATEGORIZED -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = {},
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_uncategorized)) }
                                            )
                                        }

                                        PasswordListQuickFilterItem.LOCAL_ONLY -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = {},
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_local_only)) }
                                            )
                                        }

                                        PasswordListQuickFilterItem.MANUAL_STACK_ONLY -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = {},
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_manual_stack_only)) }
                                            )
                                        }

                                        PasswordListQuickFilterItem.NEVER_STACK -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = {},
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_never_stack)) }
                                            )
                                        }

                                        PasswordListQuickFilterItem.UNSTACKED -> {
                                            FilterChip(
                                                selected = false,
                                                onClick = {},
                                                label = { Text(text = stringResource(R.string.password_list_quick_filter_unstacked)) }
                                            )
                                        }

                                        PasswordListQuickFilterItem.CARD_WALLET,
                                        PasswordListQuickFilterItem.PASSKEY,
                                        PasswordListQuickFilterItem.NOTE -> {
                                            val type = when (item) {
                                                PasswordListQuickFilterItem.CARD_WALLET -> PasswordPageContentType.CARD_WALLET
                                                PasswordListQuickFilterItem.PASSKEY -> PasswordPageContentType.PASSKEY
                                                PasswordListQuickFilterItem.NOTE -> PasswordPageContentType.NOTE
                                                else -> PasswordPageContentType.PASSWORD
                                            }
                                            if (previewAggregateEnabled &&
                                                selectedContentTypes.contains(type)
                                            ) {
                                                FilterChip(
                                                    selected = false,
                                                    onClick = {},
                                                    label = { Text(text = stringResource(type.toLabelRes())) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = type.toIcon(),
                                                            contentDescription = null
                                                        )
                                                    }
                                                )
                                            }
                                        }

                                        PasswordListQuickFilterItem.AUTHENTICATOR -> Unit
                                    }
                                }
                            }
                        }

                        if (previewCategoryQuickFiltersEnabled) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(text = stringResource(R.string.password_list_quick_folder_back)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = null
                                        )
                                    }
                                )
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(text = "QQ") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null
                                        )
                                    }
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(text = "游戏") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }

                        if (previewQuickFolderPathBannerEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Monica",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = ">",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "目录1",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Text(
                                        text = ">",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "子目录",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        if (previewQuickAccessEnabled) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.password_list_quick_access_switch_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }

                        if ((!previewQuickFiltersEnabled || selectedQuickFilterItems.isEmpty()) &&
                            !previewCategoryQuickFiltersEnabled &&
                            !previewQuickAccessEnabled
                        ) {
                            Text(
                                text = stringResource(R.string.password_list_preview_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            ExpandableSettingsCard(
                title = stringResource(R.string.password_page_aggregate_switch_title),
                subtitle = stringResource(R.string.password_page_aggregate_switch_desc),
                expanded = aggregateSectionExpanded,
                onExpandedChange = { aggregateSectionExpanded = it },
                expansionEnabled = previewAggregateEnabled,
                headerTrailing = {
                    Switch(
                        checked = previewAggregateEnabled,
                        onCheckedChange = { checked ->
                            previewAggregateEnabled = checked
                            if (!checked) {
                                aggregateSectionExpanded = false
                            }
                            viewModel.updatePasswordPageAggregateEnabled(checked)
                            viewModel.updateAddButtonBehaviorMode(
                                if (checked) AddButtonBehaviorMode.EXPANDABLE_MENU
                                else AddButtonBehaviorMode.DIRECT_PASSWORD
                            )
                        }
                    )
                }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.password_page_aggregate_content_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.password_page_aggregate_content_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val contentTypeListState = rememberLazyListState()
                    val contentTypeReorderableState =
                        rememberReorderableLazyListState(contentTypeListState) { from, to ->
                            contentTypeOrder = contentTypeOrder.toMutableList().apply {
                                add(to.index, removeAt(from.index))
                            }
                            val newSelected = contentTypeOrder.filter { selectedContentTypes.contains(it) }
                            selectedContentTypes.clear()
                            selectedContentTypes.addAll(newSelected)
                            viewModel.updatePasswordPageVisibleContentTypes(newSelected)
                            syncAddButtonMenuFromContent(
                                order = contentTypeOrder,
                                enabledTypes = newSelected
                            )
                        }

                    LazyColumn(
                        state = contentTypeListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((contentTypeOrder.size * 92).dp),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(contentTypeOrder, key = { it.name }) { type ->
                            val option = contentTypeOptions.first { it.type == type }
                            val enabled = selectedContentTypes.contains(type)
                            val selectedIndex = selectedContentTypes.indexOf(type)
                            val isRequired = type == PasswordPageContentType.PASSWORD

                            ReorderableItem(
                                contentTypeReorderableState,
                                key = type.name,
                                enabled = true
                            ) { isDragging ->
                                val elevation by animateDpAsState(
                                    if (isDragging) 6.dp else 0.dp,
                                    label = "password_page_content_type_drag_elevation"
                                )

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { shadowElevation = elevation.toPx() },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (enabled) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(option.icon, contentDescription = null)
                                        Spacer(modifier = Modifier.size(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.title,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Text(
                                            text = if (enabled) {
                                                "${selectedIndex + 1}"
                                            } else {
                                                stringResource(R.string.hide)
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .longPressDraggableHandle(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.DragIndicator, contentDescription = null)
                                        }
                                        Switch(
                                            checked = enabled,
                                            enabled = !isRequired,
                                            onCheckedChange = { checked ->
                                                val newSelected = contentTypeOrder.filter { current ->
                                                    when {
                                                        current == PasswordPageContentType.PASSWORD -> true
                                                        current == type -> checked
                                                        else -> selectedContentTypes.contains(current)
                                                    }
                                                }
                                                selectedContentTypes.clear()
                                                selectedContentTypes.addAll(newSelected)
                                                viewModel.updatePasswordPageVisibleContentTypes(newSelected)
                                                syncAddButtonMenuFromContent(
                                                    order = contentTypeOrder,
                                                    enabledTypes = newSelected
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                }
            }

            ExpandableSettingsCard(
                title = stringResource(R.string.password_list_quick_filters_switch_title),
                subtitle = stringResource(R.string.password_list_quick_filters_switch_desc),
                expanded = quickFiltersSectionExpanded,
                onExpandedChange = { quickFiltersSectionExpanded = it },
                expansionEnabled = previewQuickFiltersEnabled,
                headerTrailing = {
                    Switch(
                        checked = previewQuickFiltersEnabled,
                        onCheckedChange = { checked ->
                            previewQuickFiltersEnabled = checked
                            if (!checked) {
                                quickFiltersSectionExpanded = false
                            }
                            viewModel.updatePasswordListQuickFiltersEnabled(checked)
                        }
                    )
                }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.password_list_quick_filters_content_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.password_list_quick_filters_content_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val lazyListState = rememberLazyListState()
                    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        quickFilterOrder = quickFilterOrder.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                        val newSelected = quickFilterOrder.filter { selectedQuickFilterItems.contains(it) }
                        selectedQuickFilterItems.clear()
                        selectedQuickFilterItems.addAll(newSelected)
                        viewModel.updatePasswordListQuickFilterItems(newSelected)
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((quickFilterOrder.size * 92).dp),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(quickFilterOrder, key = { it.name }) { item ->
                            val option = quickFilterOptions.first { it.item == item }
                            val enabled = selectedQuickFilterItems.contains(item)
                            val selectedIndex = selectedQuickFilterItems.indexOf(item)

                            ReorderableItem(reorderableState, key = item.name, enabled = true) { isDragging ->
                                val elevation by animateDpAsState(
                                    if (isDragging) 6.dp else 0.dp,
                                    label = "password_list_quick_filter_drag_elevation"
                                )

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { shadowElevation = elevation.toPx() },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (enabled) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(option.icon, contentDescription = null)
                                        Spacer(modifier = Modifier.size(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.title,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Text(
                                            text = if (enabled) "${selectedIndex + 1}" else stringResource(R.string.hide),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .longPressDraggableHandle(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.DragIndicator, contentDescription = null)
                                        }
                                        Switch(
                                            checked = enabled,
                                            onCheckedChange = { checked ->
                                                val newSelected = quickFilterOrder.filter { current ->
                                                    if (current == item) checked else selectedQuickFilterItems.contains(current)
                                                }
                                                selectedQuickFilterItems.clear()
                                                selectedQuickFilterItems.addAll(newSelected)
                                                viewModel.updatePasswordListQuickFilterItems(newSelected)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SwitchSettingsCard(
                title = stringResource(R.string.password_list_category_quick_filters_switch_title),
                subtitle = stringResource(R.string.password_list_category_quick_filters_switch_desc),
                checked = previewCategoryQuickFiltersEnabled,
                onCheckedChange = { checked ->
                    previewCategoryQuickFiltersEnabled = checked
                    viewModel.updatePasswordListCategoryQuickFiltersEnabled(checked)
                }
            )

            SwitchSettingsCard(
                title = stringResource(R.string.password_list_quick_folder_path_banner_switch_title),
                subtitle = stringResource(R.string.password_list_quick_folder_path_banner_switch_desc),
                checked = previewQuickFolderPathBannerEnabled,
                onCheckedChange = { checked ->
                    previewQuickFolderPathBannerEnabled = checked
                    viewModel.updatePasswordListQuickFolderPathBannerEnabled(checked)
                }
            )

            SwitchSettingsCard(
                title = stringResource(R.string.password_list_system_back_to_parent_folder_switch_title),
                subtitle = stringResource(R.string.password_list_system_back_to_parent_folder_switch_desc),
                checked = previewSystemBackToParentFolderEnabled,
                onCheckedChange = { checked ->
                    previewSystemBackToParentFolderEnabled = checked
                    viewModel.updatePasswordListSystemBackToParentFolderEnabled(checked)
                }
            )

            SwitchSettingsCard(
                title = stringResource(R.string.password_list_quick_access_switch_title),
                subtitle = stringResource(R.string.password_list_quick_access_switch_desc),
                checked = previewQuickAccessEnabled,
                onCheckedChange = { checked ->
                    previewQuickAccessEnabled = checked
                    viewModel.updatePasswordListQuickAccessEnabled(checked)
                }
            )
        }
    }
}

@Composable
private fun PageAdjustmentEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class GroupModeOption(
    val mode: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

private data class PasswordListQuickFilterOption(
    val item: PasswordListQuickFilterItem,
    val title: String,
    val icon: ImageVector
)

private data class PasswordPageContentTypeOption(
    val type: PasswordPageContentType,
    val title: String,
    val icon: ImageVector
)

private fun PasswordPageContentType.toLabelRes(): Int = when (this) {
    PasswordPageContentType.PASSWORD -> R.string.nav_passwords
    PasswordPageContentType.CARD_WALLET -> R.string.nav_card_wallet
    PasswordPageContentType.NOTE -> R.string.nav_notes
    PasswordPageContentType.AUTHENTICATOR -> R.string.nav_authenticator
    PasswordPageContentType.PASSKEY -> R.string.nav_passkey
}

private fun PasswordPageContentType.toIcon(): ImageVector = when (this) {
    PasswordPageContentType.PASSWORD -> Icons.Default.Lock
    PasswordPageContentType.CARD_WALLET -> Icons.Default.CreditCard
    PasswordPageContentType.NOTE -> Icons.Default.Description
    PasswordPageContentType.AUTHENTICATOR -> Icons.Default.Security
    PasswordPageContentType.PASSKEY -> Icons.Default.VpnKey
}

private fun PasswordPageContentType.toAddButtonMenuActionOrNull(): AddButtonMenuAction? = when (this) {
    PasswordPageContentType.PASSWORD -> AddButtonMenuAction.PASSWORD
    PasswordPageContentType.CARD_WALLET -> AddButtonMenuAction.BANK_CARD
    PasswordPageContentType.NOTE -> AddButtonMenuAction.NOTE
    PasswordPageContentType.AUTHENTICATOR -> AddButtonMenuAction.AUTHENTICATOR
    PasswordPageContentType.PASSKEY -> null
}

private fun AddButtonMenuAction.toLabelRes(): Int = when (this) {
    AddButtonMenuAction.PASSWORD -> R.string.item_type_password
    AddButtonMenuAction.NOTE -> R.string.v2_create_note
    AddButtonMenuAction.AUTHENTICATOR -> R.string.item_type_authenticator
    AddButtonMenuAction.BANK_CARD -> R.string.add_button_action_card
}

private fun AddButtonMenuAction.toIcon(): ImageVector = when (this) {
    AddButtonMenuAction.PASSWORD -> Icons.Default.Lock
    AddButtonMenuAction.NOTE -> Icons.Default.Description
    AddButtonMenuAction.AUTHENTICATOR -> Icons.Default.Security
    AddButtonMenuAction.BANK_CARD -> Icons.Default.CreditCard
}

private data class DisplayFieldOption(
    val field: PasswordCardDisplayField,
    val title: String,
    val icon: ImageVector
)

private data class AuthenticatorDisplayFieldOption(
    val field: AuthenticatorCardDisplayField,
    val title: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordCardAdjustmentScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var groupModeExpanded by rememberSaveable { mutableStateOf(false) }
    val supportedDisplayFields = remember {
        setOf(
            PasswordCardDisplayField.USERNAME,
            PasswordCardDisplayField.WEBSITE
        )
    }
    val selectedFields = remember(settings.passwordCardDisplayFields) {
        mutableStateListOf<PasswordCardDisplayField>().apply {
            addAll(
                settings.passwordCardDisplayFields
                    .filter { supportedDisplayFields.contains(it) }
                    .distinct()
            )
        }
    }
    LaunchedEffect(settings.passwordCardDisplayFields) {
        val normalized = settings.passwordCardDisplayFields
            .filter { supportedDisplayFields.contains(it) }
            .distinct()
        if (normalized != settings.passwordCardDisplayFields) {
            viewModel.updatePasswordCardDisplayFields(normalized)
        }
    }

    val availableFields = remember {
        listOf(
            DisplayFieldOption(PasswordCardDisplayField.USERNAME, "用户名", Icons.Default.Person),
            DisplayFieldOption(PasswordCardDisplayField.WEBSITE, "网站", Icons.Default.Language)
        )
    }
    var fieldOrder by remember(settings.passwordCardDisplayFields) {
        mutableStateOf(
            buildList {
                settings.passwordCardDisplayFields
                    .filter { supportedDisplayFields.contains(it) }
                    .forEach { add(it) }
                supportedDisplayFields
                    .filter { !contains(it) }
                    .forEach { add(it) }
            }
        )
    }

    val previewEntry = remember {
        PasswordEntry(
            title = "GitHub - Monica-all",
            website = "github.com",
            username = "joyins",
            password = "******",
            appName = "GitHub",
            authenticatorKey = "JBSWY3DPEHPK3PXP"
        )
    }

    val groupOptions = remember {
        listOf(
            GroupModeOption("smart", "智能堆叠（备注>网站>应用>标题）", "优先备注，其次网站/应用，最后标题", Icons.Default.Apps),
            GroupModeOption("note", "按备注堆叠", "取备注首个非空行", Icons.Default.Description),
            GroupModeOption("website", "按网站堆叠", "网站优先", Icons.Default.Language),
            GroupModeOption("app", "按应用堆叠", "应用名/包名优先", Icons.Default.Apps),
            GroupModeOption("title", "按标题堆叠", "严格按完整标题分组", Icons.Default.Person)
        )
    }
    val selectedGroupOption = remember(settings.passwordGroupMode, groupOptions) {
        groupOptions.firstOrNull { it.mode == settings.passwordGroupMode } ?: groupOptions.first()
    }
    val websiteStackMatchMode = remember(settings.passwordWebsiteStackMatchMode) {
        when (settings.passwordWebsiteStackMatchMode.lowercase()) {
            "relaxed" -> "relaxed"
            else -> "strict"
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.password_card_adjust_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.password_card_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    PasswordEntryCard(
                        entry = previewEntry,
                        onClick = {},
                        isSingleCard = true,
                        iconCardsEnabled = settings.iconCardsEnabled && settings.passwordPageIconEnabled,
                        unmatchedIconHandlingStrategy = settings.unmatchedIconHandlingStrategy,
                        passwordCardDisplayMode = settings.passwordCardDisplayMode,
                        passwordCardDisplayFields = selectedFields.toList(),
                        showAuthenticator = settings.passwordCardShowAuthenticator,
                        hideOtherContentWhenAuthenticator = settings.passwordCardHideOtherContentWhenAuthenticator,
                        totpTimeOffsetSeconds = settings.totpTimeOffset,
                        smoothAuthenticatorProgress = settings.validatorSmoothProgress,
                        enableSharedBounds = false
                    )
                    Text(
                        text = stringResource(R.string.password_card_field_limit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.password_card_show_authenticator_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.password_card_show_authenticator_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = stringResource(R.string.password_card_show_authenticator_switch_label),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.passwordCardShowAuthenticator,
                            onCheckedChange = viewModel::updatePasswordCardShowAuthenticator
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = if (settings.passwordCardShowAuthenticator) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.password_card_hide_other_content_when_authenticator_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (settings.passwordCardShowAuthenticator) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = stringResource(R.string.password_card_hide_other_content_when_authenticator_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.passwordCardHideOtherContentWhenAuthenticator,
                            onCheckedChange = viewModel::updatePasswordCardHideOtherContentWhenAuthenticator,
                            enabled = settings.passwordCardShowAuthenticator
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.stack_mode_menu_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val selectedMode = runCatching {
                            StackCardMode.valueOf(settings.stackCardMode)
                        }.getOrDefault(StackCardMode.AUTO)
                        listOf(StackCardMode.AUTO, StackCardMode.ALWAYS_EXPANDED).forEachIndexed { index, mode ->
                            val text = if (mode == StackCardMode.AUTO) {
                                stringResource(R.string.stack_mode_auto)
                            } else {
                                stringResource(R.string.stack_mode_expand)
                            }
                            SegmentedButton(
                                selected = selectedMode == mode,
                                onClick = { viewModel.updateStackCardMode(mode.name) },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = 2
                                ),
                                label = { Text(text = text) }
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                val groupHeaderInteraction = remember { MutableInteractionSource() }
                val groupItemInteraction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = groupHeaderInteraction,
                                indication = null
                            ) { groupModeExpanded = !groupModeExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.group_mode_menu_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (groupModeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    // 收起时仅显示当前选项，展开时带动画显示全部选项
                    if (!groupModeExpanded) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = groupItemInteraction,
                                    indication = null
                                ) {
                                    groupModeExpanded = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(selectedGroupOption.icon, contentDescription = null)
                                Spacer(modifier = Modifier.size(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedGroupOption.title,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = selectedGroupOption.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = groupModeExpanded,
                        enter = expandVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(220)),
                        exit = shrinkVertically(
                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(180))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            groupOptions.forEach { option ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = groupItemInteraction,
                                            indication = null
                                        ) {
                                            viewModel.updatePasswordGroupMode(option.mode)
                                            // Keep expanded after choosing an option; collapse only by manual header tap.
                                            groupModeExpanded = true
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (settings.passwordGroupMode == option.mode) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(option.icon, contentDescription = null)
                                        Spacer(modifier = Modifier.size(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = option.title,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = option.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.website_stack_match_mode_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.website_stack_match_mode_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("strict", "relaxed").forEachIndexed { index, mode ->
                            val text = if (mode == "strict") {
                                stringResource(R.string.website_stack_match_mode_strict)
                            } else {
                                stringResource(R.string.website_stack_match_mode_relaxed)
                            }
                            SegmentedButton(
                                selected = websiteStackMatchMode == mode,
                                onClick = { viewModel.updatePasswordWebsiteStackMatchMode(mode) },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = 2
                                ),
                                label = { Text(text = text) }
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.password_card_display_mode_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.password_card_display_field_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val lazyListState = rememberLazyListState()
                    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        fieldOrder = fieldOrder.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                        val newSelected = fieldOrder.filter { selectedFields.contains(it) }
                        selectedFields.clear()
                        selectedFields.addAll(newSelected)
                        viewModel.updatePasswordCardDisplayFields(newSelected)
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((fieldOrder.size * 82).dp),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(fieldOrder, key = { it.name }) { field ->
                            val option = availableFields.first { it.field == field }
                            val enabled = selectedFields.contains(field)
                            val selectedIndex = selectedFields.indexOf(field)

                            ReorderableItem(reorderableState, key = field.name, enabled = true) { isDragging ->
                                val elevation by animateDpAsState(
                                    if (isDragging) 6.dp else 0.dp,
                                    label = "field_drag_elevation"
                                )

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { shadowElevation = elevation.toPx() },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (enabled) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(option.icon, contentDescription = null)
                                        Spacer(modifier = Modifier.size(10.dp))
                                        Text(
                                            text = option.title,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = if (enabled) "${selectedIndex + 1}" else "隐藏",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .longPressDraggableHandle(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.DragIndicator, contentDescription = null)
                                        }
                                        Switch(
                                            checked = enabled,
                                            onCheckedChange = { checked ->
                                                val newSelected = fieldOrder.filter { current ->
                                                    if (current == field) checked else selectedFields.contains(current)
                                                }
                                                selectedFields.clear()
                                                selectedFields.addAll(newSelected)
                                                viewModel.updatePasswordCardDisplayFields(newSelected)
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatorCardAdjustmentScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val supportedDisplayFields = remember {
        setOf(
            AuthenticatorCardDisplayField.ISSUER,
            AuthenticatorCardDisplayField.ACCOUNT_NAME
        )
    }
    val selectedFields = remember(settings.authenticatorCardDisplayFields) {
        mutableStateListOf<AuthenticatorCardDisplayField>().apply {
            addAll(
                settings.authenticatorCardDisplayFields
                    .filter { supportedDisplayFields.contains(it) }
                    .distinct()
            )
        }
    }
    LaunchedEffect(settings.authenticatorCardDisplayFields) {
        val normalized = settings.authenticatorCardDisplayFields
            .filter { supportedDisplayFields.contains(it) }
            .distinct()
        if (normalized != settings.authenticatorCardDisplayFields) {
            viewModel.updateAuthenticatorCardDisplayFields(normalized)
        }
    }

    val availableFields = listOf(
        AuthenticatorDisplayFieldOption(
            AuthenticatorCardDisplayField.ISSUER,
            stringResource(R.string.issuer),
            Icons.Default.Security
        ),
        AuthenticatorDisplayFieldOption(
            AuthenticatorCardDisplayField.ACCOUNT_NAME,
            stringResource(R.string.account_name),
            Icons.Default.Person
        )
    )
    var fieldOrder by remember(settings.authenticatorCardDisplayFields) {
        mutableStateOf(
            buildList {
                settings.authenticatorCardDisplayFields
                    .filter { supportedDisplayFields.contains(it) }
                    .forEach { add(it) }
                supportedDisplayFields
                    .filter { !contains(it) }
                    .forEach { add(it) }
            }
        )
    }
    var showProgressStyleDialog by rememberSaveable { mutableStateOf(false) }

    val previewItem = remember {
        SecureItem(
            itemType = ItemType.TOTP,
            title = "GitHub",
            itemData = Json.encodeToString(
                TotpData(
                    secret = "JBSWY3DPEHPK3PXP",
                    issuer = "GitHub",
                    accountName = "joyins@example.com",
                    link = "github.com"
                )
            )
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.authenticator_card_adjust_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.authenticator_card_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    TotpCodeCard(
                        item = previewItem,
                        onCopyCode = {},
                        appSettings = settings.copy(
                            authenticatorCardDisplayFields = selectedFields.toList(),
                            iconCardsEnabled = settings.iconCardsEnabled && settings.authenticatorPageIconEnabled
                        )
                    )
                    Text(
                        text = stringResource(R.string.authenticator_card_field_limit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.validator_settings_section),
                        style = MaterialTheme.typography.titleMedium
                    )

                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(R.string.unified_progress_bar_title))
                        },
                        supportingContent = {
                            Text(text = stringResource(R.string.unified_progress_bar_description))
                        },
                        leadingContent = {
                            Icon(imageVector = Icons.Default.LinearScale, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.validatorUnifiedProgressBar == UnifiedProgressBarMode.ENABLED,
                                onCheckedChange = { enabled ->
                                    viewModel.updateValidatorUnifiedProgressBar(
                                        if (enabled) UnifiedProgressBarMode.ENABLED else UnifiedProgressBarMode.DISABLED
                                    )
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            val newMode = if (settings.validatorUnifiedProgressBar == UnifiedProgressBarMode.ENABLED) {
                                UnifiedProgressBarMode.DISABLED
                            } else {
                                UnifiedProgressBarMode.ENABLED
                            }
                            viewModel.updateValidatorUnifiedProgressBar(newMode)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(R.string.validator_progress_bar_style))
                        },
                        supportingContent = {
                            Text(text = validatorProgressBarStyleDisplayName(settings.validatorProgressBarStyle))
                        },
                        leadingContent = {
                            Icon(
                                imageVector = if (settings.validatorProgressBarStyle == ProgressBarStyle.WAVE) {
                                    Icons.Default.Waves
                                } else {
                                    Icons.Default.Straighten
                                },
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable { showProgressStyleDialog = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(R.string.smooth_progress_bar_title))
                        },
                        supportingContent = {
                            Text(text = stringResource(R.string.smooth_progress_bar_description))
                        },
                        leadingContent = {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.validatorSmoothProgress,
                                onCheckedChange = viewModel::updateValidatorSmoothProgress
                            )
                        },
                        modifier = Modifier.clickable {
                            viewModel.updateValidatorSmoothProgress(!settings.validatorSmoothProgress)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    if (settings.isPlusActivated) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )

                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.validator_vibration))
                            },
                            supportingContent = {
                                Text(text = stringResource(R.string.validator_vibration_description))
                            },
                            leadingContent = {
                                Icon(imageVector = Icons.Default.Vibration, contentDescription = null)
                            },
                            trailingContent = {
                                Switch(
                                    checked = settings.validatorVibrationEnabled,
                                    onCheckedChange = viewModel::updateValidatorVibrationEnabled
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateValidatorVibrationEnabled(!settings.validatorVibrationEnabled)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )

                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(R.string.copy_next_code_when_expiring))
                            },
                            supportingContent = {
                                Text(text = stringResource(R.string.copy_next_code_when_expiring_description))
                            },
                            leadingContent = {
                                Icon(imageVector = Icons.Default.Update, contentDescription = null)
                            },
                            trailingContent = {
                                Switch(
                                    checked = settings.copyNextCodeWhenExpiring,
                                    onCheckedChange = viewModel::updateCopyNextCodeWhenExpiring
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.updateCopyNextCodeWhenExpiring(!settings.copyNextCodeWhenExpiring)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.authenticator_card_display_content_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.authenticator_card_display_field_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val lazyListState = rememberLazyListState()
                    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                        fieldOrder = fieldOrder.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                        val newSelected = fieldOrder.filter { selectedFields.contains(it) }
                        selectedFields.clear()
                        selectedFields.addAll(newSelected)
                        viewModel.updateAuthenticatorCardDisplayFields(newSelected)
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((fieldOrder.size * 82).dp),
                        userScrollEnabled = false,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(fieldOrder, key = { it.name }) { field ->
                            val option = availableFields.first { it.field == field }
                            val enabled = selectedFields.contains(field)
                            val selectedIndex = selectedFields.indexOf(field)

                            ReorderableItem(reorderableState, key = field.name, enabled = true) { isDragging ->
                                val elevation by animateDpAsState(
                                    if (isDragging) 6.dp else 0.dp,
                                    label = "auth_field_drag_elevation"
                                )

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { shadowElevation = elevation.toPx() },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (enabled) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(option.icon, contentDescription = null)
                                        Spacer(modifier = Modifier.size(10.dp))
                                        Text(
                                            text = option.title,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = if (enabled) "${selectedIndex + 1}" else stringResource(R.string.hide),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .longPressDraggableHandle(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.DragIndicator, contentDescription = null)
                                        }
                                        Switch(
                                            checked = enabled,
                                            onCheckedChange = { checked ->
                                                val newSelected = fieldOrder.filter { current ->
                                                    if (current == field) checked else selectedFields.contains(current)
                                                }
                                                selectedFields.clear()
                                                selectedFields.addAll(newSelected)
                                                viewModel.updateAuthenticatorCardDisplayFields(newSelected)
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
    }

    if (showProgressStyleDialog) {
        ValidatorProgressBarStyleDialog(
            currentStyle = settings.validatorProgressBarStyle,
            onStyleSelected = { style ->
                viewModel.updateValidatorProgressBarStyle(style)
                showProgressStyleDialog = false
            },
            onDismiss = { showProgressStyleDialog = false }
        )
    }
}

@Composable
private fun validatorProgressBarStyleDisplayName(style: ProgressBarStyle): String {
    return when (style) {
        ProgressBarStyle.LINEAR -> stringResource(R.string.progress_bar_style_linear)
        ProgressBarStyle.WAVE -> stringResource(R.string.progress_bar_style_wave)
    }
}

@Composable
private fun ValidatorProgressBarStyleDialog(
    currentStyle: ProgressBarStyle,
    onStyleSelected: (ProgressBarStyle) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.validator_progress_bar_style)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProgressBarStyle.values().forEach { style ->
                    ListItem(
                        headlineContent = {
                            Text(text = validatorProgressBarStyleDisplayName(style))
                        },
                        leadingContent = {
                            RadioButton(
                                selected = style == currentStyle,
                                onClick = null
                            )
                        },
                        modifier = Modifier.clickable { onStyleSelected(style) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

private data class IconSettingOption(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

private data class AppLauncherIconOption(
    val value: AppLauncherIcon,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var appLauncherExpanded by rememberSaveable { mutableStateOf(true) }
    var pageToggleExpanded by rememberSaveable { mutableStateOf(true) }
    var unmatchedStrategyExpanded by rememberSaveable { mutableStateOf(true) }

    val appLauncherOptions = listOf(
        AppLauncherIconOption(
            value = AppLauncherIcon.MODERN,
            title = stringResource(R.string.icon_settings_app_icon_modern_title),
            subtitle = stringResource(R.string.icon_settings_app_icon_modern_subtitle),
            icon = Icons.Default.Apps
        ),
        AppLauncherIconOption(
            value = AppLauncherIcon.LOCK_CLASSIC,
            title = stringResource(R.string.icon_settings_app_icon_classic_title),
            subtitle = stringResource(R.string.icon_settings_app_icon_classic_subtitle),
            icon = Icons.Default.Lock
        )
    )
    val selectedAppLauncherLabel = appLauncherOptions
        .firstOrNull { it.value == settings.appLauncherIcon }
        ?.title
        ?: appLauncherOptions.first().title

    val unmatchedStrategyOptions = listOf(
        UnmatchedIconHandlingStrategy.DEFAULT_ICON to stringResource(R.string.icon_settings_unmatched_strategy_default),
        UnmatchedIconHandlingStrategy.WEBSITE_OR_TITLE_INITIAL to stringResource(R.string.icon_settings_unmatched_strategy_initial),
        UnmatchedIconHandlingStrategy.HIDE to stringResource(R.string.icon_settings_unmatched_strategy_hide)
    )
    val selectedStrategyLabel = unmatchedStrategyOptions
        .firstOrNull { it.first == settings.unmatchedIconHandlingStrategy }
        ?.second
        ?: unmatchedStrategyOptions.first().second

    val options = listOf(
        IconSettingOption(
            title = stringResource(R.string.icon_settings_password_page_title),
            subtitle = stringResource(R.string.icon_settings_password_page_subtitle),
            icon = Icons.Default.Key,
            checked = settings.passwordPageIconEnabled,
            onCheckedChange = viewModel::updatePasswordPageIconEnabled
        ),
        IconSettingOption(
            title = stringResource(R.string.icon_settings_authenticator_page_title),
            subtitle = stringResource(R.string.icon_settings_authenticator_page_subtitle),
            icon = Icons.Default.Security,
            checked = settings.authenticatorPageIconEnabled,
            onCheckedChange = viewModel::updateAuthenticatorPageIconEnabled
        ),
        IconSettingOption(
            title = stringResource(R.string.icon_settings_passkey_page_title),
            subtitle = stringResource(R.string.icon_settings_passkey_page_subtitle),
            icon = Icons.Default.VpnKey,
            checked = settings.passkeyPageIconEnabled,
            onCheckedChange = viewModel::updatePasskeyPageIconEnabled
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.icon_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.icon_settings_master_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.icon_settings_master_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.icon_settings_master_switch),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Switch(
                            checked = settings.iconCardsEnabled,
                            onCheckedChange = viewModel::updateIconCardsEnabled
                        )
                    }
                }
            }

            ExpandableSettingsCard(
                title = stringResource(R.string.icon_settings_app_icon_title),
                subtitle = selectedAppLauncherLabel,
                expanded = appLauncherExpanded,
                onExpandedChange = { appLauncherExpanded = it }
            ) {
                appLauncherOptions.forEachIndexed { index, option ->
                    ListItem(
                        headlineContent = { Text(option.title) },
                        supportingContent = {
                            Text(
                                text = option.subtitle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        trailingContent = {
                            RadioButton(
                                selected = settings.appLauncherIcon == option.value,
                                onClick = null
                            )
                        },
                        modifier = Modifier.clickable {
                            viewModel.updateAppLauncherIcon(option.value)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (index != appLauncherOptions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }
                }
            }

            ExpandableSettingsCard(
                title = stringResource(R.string.icon_settings_page_switches_title),
                subtitle = stringResource(R.string.icon_settings_page_switches_desc),
                expanded = pageToggleExpanded,
                onExpandedChange = { pageToggleExpanded = it }
            ) {
                options.forEachIndexed { index, option ->
                    ListItem(
                        headlineContent = {
                            Text(option.title)
                        },
                        supportingContent = {
                            Text(
                                text = option.subtitle,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = option.checked,
                                enabled = settings.iconCardsEnabled,
                                onCheckedChange = option.onCheckedChange
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (index != options.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            ExpandableSettingsCard(
                title = stringResource(R.string.icon_settings_unmatched_strategy_title),
                subtitle = selectedStrategyLabel,
                expanded = unmatchedStrategyExpanded,
                onExpandedChange = { unmatchedStrategyExpanded = it }
            ) {
                unmatchedStrategyOptions.forEachIndexed { index, (strategy, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(
                                selected = settings.unmatchedIconHandlingStrategy == strategy,
                                onClick = null
                            )
                        },
                        modifier = Modifier.clickable(
                            onClick = { viewModel.updateUnmatchedIconHandlingStrategy(strategy) }
                        ),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (index != unmatchedStrategyOptions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }
                }
            }

            StaticInfoCard(
                title = stringResource(R.string.icon_settings_priority_title),
                subtitle = stringResource(R.string.icon_settings_priority_desc)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.icon_settings_priority_unified)) },
                    leadingContent = { Icon(Icons.Default.Key, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            StaticInfoCard(
                title = stringResource(R.string.icon_settings_source_title),
                subtitle = stringResource(R.string.icon_settings_source_desc)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.icon_settings_source_line_1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.icon_settings_source_line_2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.icon_settings_source_line_3),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingsCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun ExpandableSettingsCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expansionEnabled: Boolean = true,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = expansionEnabled) { onExpandedChange(!expanded) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (headerTrailing != null) {
                    headerTrailing()
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 160))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun StaticInfoCard(
    title: String,
    subtitle: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                content = content
            )
        }
    }
}

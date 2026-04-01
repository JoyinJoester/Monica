package takagi.ru.monica.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.ui.password.PasswordAggregateCardStyle
import takagi.ru.monica.ui.password.PasswordAggregateListItemUi
import takagi.ru.monica.ui.password.PasswordListAggregateConfig
import takagi.ru.monica.ui.password.StackCardMode
import takagi.ru.monica.ui.password.appendAggregateContentQuickFilterItems
import takagi.ru.monica.ui.password.buildPasswordAggregateItems
import takagi.ru.monica.ui.password.getGroupKeyForMode
import takagi.ru.monica.ui.password.getPasswordInfoKey
import takagi.ru.monica.ui.password.passwordSelectionKey
import takagi.ru.monica.ui.password.resolvePasswordPageDisplayedTypes
import takagi.ru.monica.ui.password.resolvePasswordPageQuickFilterTypes
import takagi.ru.monica.ui.password.toPasswordPageContentTypeOrNull
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.CategoryFilter
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.TotpViewModel

private const val FAST_SCROLL_LOG_TAG = "PasswordFastScroll"
private const val PASSWORD_SCROLL_LOG_TAG = "PasswordScrollDebug"
private const val MANUAL_STACK_GROUP_KEY_PREFIX = "manual_stack:"
private const val NO_STACK_GROUP_KEY_PREFIX = "no_stack:"

private data class PasswordListScrollSnapshot(
    val allowPersistence: Boolean,
    val pendingRestore: Boolean,
    val totalItems: Int,
    val index: Int,
    val offset: Int,
    val anchorKey: String?
)

internal data class PasswordListAggregateUiState(
    val visibleContentTypes: List<PasswordPageContentType>,
    val selectedContentTypes: Set<PasswordPageContentType>,
    val quickFilterTypes: List<PasswordPageContentType>,
    val displayedContentTypes: Set<PasswordPageContentType>,
    val hasActiveContentTypeFilter: Boolean,
    val cardStyle: PasswordAggregateCardStyle,
    val visibleItems: List<PasswordAggregateListItemUi>,
    val bankCards: List<SecureItem>,
    val documents: List<SecureItem>,
    val totpItems: List<SecureItem>,
    val notes: List<SecureItem>,
    val passkeys: List<PasskeyEntry>,
    val totpViewModel: TotpViewModel?,
    val bankCardViewModel: BankCardViewModel?,
    val documentViewModel: DocumentViewModel?,
    val noteViewModel: NoteViewModel?,
    val passkeyViewModel: PasskeyViewModel?
)

internal data class PasswordGroupingConfig(
    val isLocalOnlyView: Boolean,
    val effectiveStackCardMode: StackCardMode,
    val effectiveGroupMode: String,
    val websiteStackMatchMode: String,
    val effectiveNoStackEntryIds: Set<Long>,
    val effectiveManualStackGroupByEntryId: Map<Long, String>,
    val untitledLabel: String
)

internal data class FavoriteSelectionToggleRequest(
    val context: Context,
    val viewModel: PasswordViewModel,
    val selectedPasswords: Set<Long>,
    val passwordEntries: List<PasswordEntry>,
    val selectedSupplementaryItems: List<PasswordAggregateListItemUi>,
    val aggregateUiState: PasswordListAggregateUiState
)

internal data class PasswordListInitialRenderState(
    val isPasswordListDataLoaded: Boolean,
    val isHeaderDataLoaded: Boolean,
    val isPasswordPageListModelReady: Boolean,
    val shouldGateInitialContent: Boolean
)

// Keeps aggregate-card state assembly outside the main password list composable.
@Composable
internal fun rememberPasswordAggregateUiState(
    aggregateConfig: PasswordListAggregateConfig?,
    searchQuery: String,
    currentFilter: CategoryFilter,
    appSettings: AppSettings
): PasswordListAggregateUiState {
    val emptySecureItems = remember { emptyList<SecureItem>() }
    val emptyPasskeys = remember { emptyList<PasskeyEntry>() }
    val aggregateTotpItemsState =
        aggregateConfig?.totpViewModel?.totpItems?.collectAsState()
            ?: remember { mutableStateOf(emptySecureItems) }
    val aggregateBankCardsState =
        aggregateConfig?.bankCardViewModel?.allCards?.collectAsState(initial = emptySecureItems)
            ?: remember { mutableStateOf(emptySecureItems) }
    val aggregateDocumentsState =
        aggregateConfig?.documentViewModel?.allDocuments?.collectAsState(initial = emptySecureItems)
            ?: remember { mutableStateOf(emptySecureItems) }
    val aggregateNotesState =
        aggregateConfig?.noteViewModel?.allNotes?.collectAsState(initial = emptySecureItems)
            ?: remember { mutableStateOf(emptySecureItems) }
    val aggregatePasskeysState =
        aggregateConfig?.passkeyViewModel?.allPasskeys?.collectAsState()
            ?: remember { mutableStateOf(emptyPasskeys) }
    val aggregateTotpItems by aggregateTotpItemsState
    val aggregateBankCards by aggregateBankCardsState
    val aggregateDocuments by aggregateDocumentsState
    val aggregateNotes by aggregateNotesState
    val aggregatePasskeys by aggregatePasskeysState
    val aggregateVisibleContentTypes = aggregateConfig?.visibleContentTypes ?: emptyList()
    val aggregateSelectedContentTypes = aggregateConfig?.selectedContentTypes ?: emptySet()
    val effectiveQuickFilterItems = remember(
        appSettings.passwordListQuickFilterItems,
        appSettings.passwordPageAggregateEnabled,
        aggregateVisibleContentTypes
    ) {
        appendAggregateContentQuickFilterItems(
            configuredItems = appSettings.passwordListQuickFilterItems,
            visibleTypes = aggregateVisibleContentTypes,
            aggregateEnabled = appSettings.passwordPageAggregateEnabled
        )
    }
    val aggregateQuickFilterTypes = remember(
        aggregateVisibleContentTypes,
        effectiveQuickFilterItems
    ) {
        val enabledQuickFilterTypes = effectiveQuickFilterItems
            .mapNotNull { item -> item.toPasswordPageContentTypeOrNull() }
            .toSet()
        resolvePasswordPageQuickFilterTypes(aggregateVisibleContentTypes)
            .filter { type -> type in enabledQuickFilterTypes }
    }
    val aggregateDisplayedContentTypes = remember(
        aggregateQuickFilterTypes,
        aggregateSelectedContentTypes
    ) {
        resolvePasswordPageDisplayedTypes(
            visibleTypes = buildList {
                add(PasswordPageContentType.PASSWORD)
                addAll(aggregateQuickFilterTypes)
            },
            selectedTypes = aggregateSelectedContentTypes.filterTo(linkedSetOf()) { type ->
                type in aggregateQuickFilterTypes
            }
        )
    }
    val aggregateCardStyle = remember(
        appSettings.iconCardsEnabled,
        appSettings.passwordPageIconEnabled,
        appSettings.unmatchedIconHandlingStrategy,
        appSettings.passwordCardDisplayMode,
        appSettings.passwordCardDisplayFields,
        appSettings.passwordCardShowAuthenticator,
        appSettings.passwordCardHideOtherContentWhenAuthenticator,
        appSettings.totpTimeOffset,
        appSettings.validatorSmoothProgress
    ) {
        PasswordAggregateCardStyle(
            iconCardsEnabled = appSettings.iconCardsEnabled && appSettings.passwordPageIconEnabled,
            unmatchedIconHandlingStrategy = appSettings.unmatchedIconHandlingStrategy,
            passwordCardDisplayMode = appSettings.passwordCardDisplayMode,
            passwordCardDisplayFields = appSettings.passwordCardDisplayFields,
            showAuthenticator = appSettings.passwordCardShowAuthenticator,
            hideOtherContentWhenAuthenticator = appSettings.passwordCardHideOtherContentWhenAuthenticator,
            totpTimeOffsetSeconds = appSettings.totpTimeOffset,
            smoothAuthenticatorProgress = appSettings.validatorSmoothProgress
        )
    }
    val aggregateVisibleItems = remember(
        aggregateDisplayedContentTypes,
        aggregateBankCards,
        aggregateDocuments,
        aggregateNotes,
        aggregateTotpItems,
        aggregatePasskeys,
        searchQuery,
        currentFilter
    ) {
        buildPasswordAggregateItems(
            selectedContentTypes = aggregateDisplayedContentTypes,
            bankCards = aggregateBankCards,
            documents = aggregateDocuments,
            notes = aggregateNotes,
            totpItems = aggregateTotpItems,
            passkeys = aggregatePasskeys,
            searchQuery = searchQuery,
            categoryFilter = currentFilter
        )
    }

    return PasswordListAggregateUiState(
        visibleContentTypes = aggregateVisibleContentTypes,
        selectedContentTypes = aggregateSelectedContentTypes,
        quickFilterTypes = aggregateQuickFilterTypes,
        displayedContentTypes = aggregateDisplayedContentTypes,
        hasActiveContentTypeFilter = aggregateSelectedContentTypes.any { type ->
            type in aggregateQuickFilterTypes
        },
        cardStyle = aggregateCardStyle,
        visibleItems = aggregateVisibleItems,
        bankCards = aggregateBankCards,
        documents = aggregateDocuments,
        totpItems = aggregateTotpItems,
        notes = aggregateNotes,
        passkeys = aggregatePasskeys,
        totpViewModel = aggregateConfig?.totpViewModel,
        bankCardViewModel = aggregateConfig?.bankCardViewModel,
        documentViewModel = aggregateConfig?.documentViewModel,
        noteViewModel = aggregateConfig?.noteViewModel,
        passkeyViewModel = aggregateConfig?.passkeyViewModel
    )
}

// Centralizes list scroll bookkeeping so the main screen body stays readable.
@Composable
internal fun rememberPasswordListLazyListState(
    viewModel: PasswordViewModel,
    currentListItemKeys: List<String>,
    scrollToTopRequestKey: Int,
    fastScrollRequestKey: Int,
    fastScrollProgress: Float,
    allowScrollPositionPersistence: Boolean,
    onBackToTopVisibilityChange: (Boolean) -> Unit
): LazyListState {
    val savedScrollIndex by viewModel.passwordListScrollIndex.collectAsState()
    val savedScrollOffset by viewModel.passwordListScrollOffset.collectAsState()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )
    val currentListItemKeySet = remember(currentListItemKeys) {
        currentListItemKeys.toSet()
    }
    val backToTopVisibilityCallback by rememberUpdatedState(onBackToTopVisibilityChange)
    var shouldShowBackToTop by remember { mutableStateOf(false) }
    var lastHandledScrollToTopRequestKey by rememberSaveable {
        mutableStateOf(scrollToTopRequestKey)
    }
    var lastHandledFastScrollRequestKey by remember {
        mutableIntStateOf(fastScrollRequestKey)
    }
    var hasAppliedDeferredScrollRestore by remember {
        mutableStateOf(false)
    }
    val backToTopEstimatedScrollPx by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                .coerceAtLeast(1)
            val firstVisibleItemSize =
                layoutInfo.visibleItemsInfo.firstOrNull()?.size?.coerceAtLeast(1)
                    ?: viewportHeight
            (listState.firstVisibleItemIndex * firstVisibleItemSize) +
                listState.firstVisibleItemScrollOffset
        }
    }
    val backToTopViewportHeight by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
        }
    }

    LaunchedEffect(backToTopEstimatedScrollPx, backToTopViewportHeight) {
        val viewportHeight = backToTopViewportHeight
        val showThreshold = viewportHeight * 2
        val hideThreshold = (viewportHeight * 1.6f).toInt()
        shouldShowBackToTop = if (shouldShowBackToTop) {
            listState.firstVisibleItemIndex > 0 || backToTopEstimatedScrollPx >= hideThreshold
        } else {
            backToTopEstimatedScrollPx >= showThreshold
        }
    }

    LaunchedEffect(shouldShowBackToTop) {
        backToTopVisibilityCallback(shouldShowBackToTop)
    }

    DisposableEffect(Unit) {
        onDispose {
            backToTopVisibilityCallback(false)
        }
    }

    LaunchedEffect(scrollToTopRequestKey) {
        if (scrollToTopRequestKey > lastHandledScrollToTopRequestKey) {
            try {
                listState.animateScrollToItem(index = 0)
            } finally {
                lastHandledScrollToTopRequestKey = scrollToTopRequestKey
            }
        }
    }

    LaunchedEffect(fastScrollRequestKey) {
        if (fastScrollRequestKey <= lastHandledFastScrollRequestKey) {
            return@LaunchedEffect
        }

        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) {
            Log.d(
                PASSWORD_SCROLL_LOG_TAG,
                "source=v1_fast_scroll_skip_empty requestKey=$fastScrollRequestKey progress=$fastScrollProgress"
            )
            lastHandledFastScrollRequestKey = fastScrollRequestKey
            return@LaunchedEffect
        }

        val targetIndex = (fastScrollProgress.coerceIn(0f, 1f) * (totalItems - 1))
            .roundToInt()
            .coerceIn(0, totalItems - 1)
        if (listState.firstVisibleItemIndex == targetIndex) {
            Log.d(
                PASSWORD_SCROLL_LOG_TAG,
                "source=v1_fast_scroll_skip_same_target requestKey=$fastScrollRequestKey target=$targetIndex current=${listState.firstVisibleItemIndex}"
            )
            lastHandledFastScrollRequestKey = fastScrollRequestKey
            return@LaunchedEffect
        }

        Log.d(
            PASSWORD_SCROLL_LOG_TAG,
            "source=v1_fast_scroll_apply requestKey=$fastScrollRequestKey progress=$fastScrollProgress target=$targetIndex current=${listState.firstVisibleItemIndex} total=$totalItems"
        )

        runCatching {
            listState.scrollToItem(index = targetIndex)
        }.onFailure { throwable ->
            if (throwable is CancellationException) return@onFailure
            Log.e(
                FAST_SCROLL_LOG_TAG,
                "scrollToItem failed: targetIndex=$targetIndex totalItems=${listState.layoutInfo.totalItemsCount}",
                throwable
            )
        }.also {
            lastHandledFastScrollRequestKey = fastScrollRequestKey
        }
    }

    LaunchedEffect(
        allowScrollPositionPersistence,
        currentListItemKeys,
        savedScrollIndex,
        savedScrollOffset,
        listState.layoutInfo.totalItemsCount
    ) {
        if (hasAppliedDeferredScrollRestore) return@LaunchedEffect
        if (!allowScrollPositionPersistence) return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems <= 0) return@LaunchedEffect
        Log.d(
            PASSWORD_SCROLL_LOG_TAG,
            "source=v1_restore_check saved=$savedScrollIndex/$savedScrollOffset current=${listState.firstVisibleItemIndex}/${listState.firstVisibleItemScrollOffset} total=$totalItems"
        )
        val hasSavedPosition = savedScrollIndex != 0 || savedScrollOffset != 0
        if (!hasSavedPosition) {
            if (listState.firstVisibleItemIndex != 0 ||
                listState.firstVisibleItemScrollOffset != 0
            ) {
                Log.d(
                    PASSWORD_SCROLL_LOG_TAG,
                    "source=v1_restore_no_saved_force_top current=${listState.firstVisibleItemIndex}/${listState.firstVisibleItemScrollOffset} total=$totalItems"
                )
                runCatching {
                    listState.scrollToItem(0, 0)
                }.onSuccess {
                    viewModel.updatePasswordListScrollPosition(
                        0,
                        0,
                        null,
                        source = "v1_restore_no_saved_force_top"
                    )
                }
            }
            hasAppliedDeferredScrollRestore = true
            return@LaunchedEffect
        }

        val isSavedIndexOutOfBounds = savedScrollIndex !in 0 until totalItems

        val targetIndex = if (isSavedIndexOutOfBounds) 0 else savedScrollIndex
        val targetOffset = if (isSavedIndexOutOfBounds) 0 else savedScrollOffset

        if (isSavedIndexOutOfBounds) {
            Log.w(
                PASSWORD_SCROLL_LOG_TAG,
                "source=v1_restore_saved_out_of_bounds saved=$savedScrollIndex/$savedScrollOffset total=$totalItems -> 0/0"
            )
            runCatching {
                listState.scrollToItem(targetIndex, targetOffset)
            }.onSuccess {
                viewModel.updatePasswordListScrollPosition(
                    0,
                    0,
                    null,
                    source = "v1_restore_saved_out_of_bounds"
                )
                hasAppliedDeferredScrollRestore = true
            }
            return@LaunchedEffect
        }

        if (listState.firstVisibleItemIndex == targetIndex &&
            listState.firstVisibleItemScrollOffset == targetOffset
        ) {
            Log.d(
                PASSWORD_SCROLL_LOG_TAG,
                "source=v1_restore_skip_already_at_target target=$targetIndex/$targetOffset"
            )
            hasAppliedDeferredScrollRestore = true
            return@LaunchedEffect
        }
        Log.d(
            PASSWORD_SCROLL_LOG_TAG,
            "source=v1_restore_apply target=$targetIndex/$targetOffset current=${listState.firstVisibleItemIndex}/${listState.firstVisibleItemScrollOffset} total=$totalItems"
        )
        runCatching {
            listState.scrollToItem(targetIndex, targetOffset)
        }.onSuccess {
            Log.d(
                PASSWORD_SCROLL_LOG_TAG,
                "source=v1_restore_applied target=$targetIndex/$targetOffset"
            )
            hasAppliedDeferredScrollRestore = true
        }
    }

    LaunchedEffect(
        listState,
        allowScrollPositionPersistence,
        hasAppliedDeferredScrollRestore,
        savedScrollIndex,
        savedScrollOffset
    ) {
        snapshotFlow {
            val pendingRestore =
                !hasAppliedDeferredScrollRestore &&
                    (
                        savedScrollIndex != 0 ||
                            savedScrollOffset != 0
                        )
            val topVisibleKey = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { item -> item.key.toString() in currentListItemKeySet }
                ?.key
                ?.toString()
            PasswordListScrollSnapshot(
                allowPersistence = allowScrollPositionPersistence,
                pendingRestore = pendingRestore,
                totalItems = listState.layoutInfo.totalItemsCount,
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
                anchorKey = topVisibleKey
            )
        }
            .distinctUntilChanged()
            .collect { snapshot ->
                if (!snapshot.allowPersistence || snapshot.pendingRestore || snapshot.totalItems <= 0) {
                    return@collect
                }
                viewModel.updatePasswordListScrollPosition(
                    snapshot.index,
                    snapshot.offset,
                    snapshot.anchorKey,
                    source = "v1_snapshot_persist"
                )
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) {
                0f
            } else {
                (listState.firstVisibleItemIndex.toFloat() / (totalItems - 1).toFloat())
                    .coerceIn(0f, 1f)
            }
        }
            .distinctUntilChanged()
            .collect { progress ->
                viewModel.updateFastScrollProgress(progress)
            }
    }

    return listState
}

internal fun buildGroupedPasswordsForEntries(
    sourceEntries: List<PasswordEntry>,
    config: PasswordGroupingConfig
): Map<String, List<PasswordEntry>> {
    val mergedByInfo = if (config.effectiveStackCardMode == StackCardMode.ALWAYS_EXPANDED) {
        sourceEntries.sortedBy { it.sortOrder }.map { listOf(it) }
    } else {
        sourceEntries
            .groupBy { entry ->
                if (entry.id in config.effectiveNoStackEntryIds) {
                    "$NO_STACK_GROUP_KEY_PREFIX${entry.id}"
                } else {
                    config.effectiveManualStackGroupByEntryId[entry.id]
                        ?.let { groupId -> "$MANUAL_STACK_GROUP_KEY_PREFIX$groupId" }
                        ?: getPasswordInfoKey(entry)
                }
            }
            .map { (_, entries) -> entries.sortedBy { it.sortOrder } }
    }

    val groupedAndSorted = if (config.isLocalOnlyView) {
        sourceEntries
            .sortedBy { it.sortOrder }
            .associate { entry -> "entry_${entry.id}" to listOf(entry) }
    } else {
        when (config.effectiveGroupMode) {
            "title" -> mergedByInfo
                .groupBy { entries ->
                    val first = entries.first()
                    if (first.id in config.effectiveNoStackEntryIds) {
                        "$NO_STACK_GROUP_KEY_PREFIX${first.id}"
                    } else {
                        config.effectiveManualStackGroupByEntryId[first.id]
                            ?.let { groupId -> "$MANUAL_STACK_GROUP_KEY_PREFIX$groupId" }
                            ?: first.title.ifBlank { config.untitledLabel }
                    }
                }
                .mapValues { (_, groups) -> groups.flatten() }
                .toList()
                .sortedWith(
                    compareByDescending<Pair<String, List<PasswordEntry>>> { (_, passwords) ->
                        val infoKeyGroups = passwords.groupBy { getPasswordInfoKey(it) }
                        val cardType = when {
                            infoKeyGroups.size > 1 -> 3
                            infoKeyGroups.size == 1 && passwords.size > 1 -> 2
                            else -> 1
                        }
                        val favoriteBonus = if (passwords.any { it.isFavorite }) 10 else 0
                        favoriteBonus.toDouble() + cardType.toDouble()
                    }.thenBy { (title, _) -> title }
                )
                .toMap()

            else -> mergedByInfo
                .groupBy { entries ->
                    val first = entries.first()
                    if (first.id in config.effectiveNoStackEntryIds) {
                        "$NO_STACK_GROUP_KEY_PREFIX${first.id}"
                    } else {
                        config.effectiveManualStackGroupByEntryId[first.id]
                            ?.let { groupId -> "$MANUAL_STACK_GROUP_KEY_PREFIX$groupId" }
                            ?: getGroupKeyForMode(
                                first,
                                config.effectiveGroupMode,
                                config.websiteStackMatchMode
                            )
                    }
                }
                .mapValues { (_, groups) -> groups.flatten() }
                .toList()
                .sortedWith(
                    compareByDescending<Pair<String, List<PasswordEntry>>> { (_, passwords) ->
                        val infoKeyGroups = passwords.groupBy { getPasswordInfoKey(it) }
                        val cardType = when {
                            infoKeyGroups.size > 1 -> 3
                            infoKeyGroups.size == 1 && passwords.size > 1 -> 2
                            else -> 1
                        }
                        val favoriteBonus = if (passwords.any { it.isFavorite }) 10 else 0
                        favoriteBonus.toDouble() + cardType.toDouble()
                    }.thenBy { (_, passwords) ->
                        passwords.firstOrNull()?.sortOrder ?: Int.MAX_VALUE
                    }
                )
                .toMap()
        }
    }

    return if (config.effectiveStackCardMode == StackCardMode.ALWAYS_EXPANDED) {
        groupedAndSorted.values.flatten()
            .map { entry -> "entry_${entry.id}" to listOf(entry) }
            .toMap()
    } else {
        groupedAndSorted
    }
}

internal fun filterPasswordEntriesByStackQuickFilters(
    items: List<PasswordEntry>,
    configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
    quickFilterManualStackOnly: Boolean,
    quickFilterUnstacked: Boolean,
    effectiveStackCardMode: StackCardMode,
    effectiveManualStackGroupByEntryId: Map<Long, String>,
    aggregateManualStackedItemKeys: Set<String>,
    aggregateManualStackedPasswordIds: Set<Long>,
    groupingConfig: PasswordGroupingConfig
): List<PasswordEntry> {
    var filtered = items

    if (
        quickFilterManualStackOnly &&
        PasswordListQuickFilterItem.MANUAL_STACK_ONLY in configuredQuickFilterItems
    ) {
        filtered = filtered.filter { entry ->
            effectiveManualStackGroupByEntryId.containsKey(entry.id) ||
                passwordSelectionKey(entry.id) in aggregateManualStackedItemKeys
        }
    }

    if (
        quickFilterUnstacked &&
        PasswordListQuickFilterItem.UNSTACKED in configuredQuickFilterItems &&
        effectiveStackCardMode != StackCardMode.ALWAYS_EXPANDED
    ) {
        val autoGroupingCandidates = filtered.filter { entry ->
            entry.id !in aggregateManualStackedPasswordIds
        }
        val singleCardEntryIds = buildGroupedPasswordsForEntries(
            sourceEntries = autoGroupingCandidates,
            config = groupingConfig
        )
            .values
            .asSequence()
            .filter { group -> group.size == 1 }
            .flatten()
            .map(PasswordEntry::id)
            .toSet()
        filtered = filtered.filter { entry ->
            entry.id !in aggregateManualStackedPasswordIds &&
                entry.id in singleCardEntryIds
        }
    }

    return filtered
}

internal fun resolvePasswordListInitialRenderState(
    hasCompletedInitialPasswordListStabilization: Boolean,
    passwordEntriesReady: Boolean,
    allPasswordsForUiReady: Boolean,
    categoriesReady: Boolean,
    shouldRenderPasswordGroups: Boolean,
    visiblePasswordIds: List<Long>,
    groupedPasswordIds: List<Long>,
    displayedContentTypes: Set<PasswordPageContentType>,
    searchQuery: String
): PasswordListInitialRenderState {
    val isPasswordListDataLoaded = passwordEntriesReady && allPasswordsForUiReady
    val isHeaderDataLoaded = isPasswordListDataLoaded && categoriesReady
    val isPasswordPageListModelReady = if (!isPasswordListDataLoaded) {
        false
    } else {
        !shouldRenderPasswordGroups ||
            visiblePasswordIds.isEmpty() ||
            (
                groupedPasswordIds.size == visiblePasswordIds.size &&
                    groupedPasswordIds.toSet() == visiblePasswordIds.toSet()
                )
    }
    val shouldGateInitialContent =
        !hasCompletedInitialPasswordListStabilization &&
            (
                !isHeaderDataLoaded ||
                    !isPasswordPageListModelReady
                ) &&
            PasswordPageContentType.PASSWORD in displayedContentTypes &&
            searchQuery.isEmpty()

    return PasswordListInitialRenderState(
        isPasswordListDataLoaded = isPasswordListDataLoaded,
        isHeaderDataLoaded = isHeaderDataLoaded,
        isPasswordPageListModelReady = isPasswordPageListModelReady,
        shouldGateInitialContent = shouldGateInitialContent
    )
}

internal suspend fun applyFavoriteSelectionToggle(
    request: FavoriteSelectionToggleRequest
): Int {
    val selectedEntries = request.passwordEntries.filter { it.id in request.selectedPasswords }
    val favoriteTargets = selectedEntries.size + request.selectedSupplementaryItems.count {
        it.type != PasswordPageContentType.PASSKEY
    }
    if (favoriteTargets <= 0) {
        return 0
    }

    val allFavorited = selectedEntries.all { it.isFavorite } &&
        request.selectedSupplementaryItems.all { item ->
            when (item.type) {
                PasswordPageContentType.AUTHENTICATOR,
                PasswordPageContentType.CARD_WALLET,
                PasswordPageContentType.NOTE -> item.entry.isFavorite
                PasswordPageContentType.PASSKEY,
                PasswordPageContentType.PASSWORD -> true
            }
        }
    val newFavoriteState = !allFavorited

    selectedEntries.forEach { entry ->
        request.viewModel.toggleFavorite(entry.id, newFavoriteState)
    }

    request.selectedSupplementaryItems.forEach { item ->
        when (item.type) {
            PasswordPageContentType.AUTHENTICATOR -> {
                item.secureItemId?.let { id ->
                    request.aggregateUiState.totpViewModel?.toggleFavorite(id, newFavoriteState)
                }
            }

            PasswordPageContentType.CARD_WALLET -> {
                item.secureItemId?.let { id ->
                    if (item.isDocument) {
                        request.aggregateUiState.documentViewModel?.toggleFavorite(id)
                    } else {
                        request.aggregateUiState.bankCardViewModel?.toggleFavorite(id)
                    }
                }
            }

            PasswordPageContentType.NOTE -> {
                item.secureItemId?.let { noteId ->
                    request.aggregateUiState.notes
                        .firstOrNull { it.id == noteId }
                        ?.let { note ->
                            val decoded = NoteContentCodec.decodeFromItem(note)
                            request.aggregateUiState.noteViewModel?.updateNote(
                                id = note.id,
                                content = decoded.content,
                                title = note.title,
                                tags = decoded.tags,
                                isMarkdown = decoded.isMarkdown,
                                isFavorite = newFavoriteState,
                                createdAt = note.createdAt,
                                categoryId = note.categoryId,
                                imagePaths = note.imagePaths,
                                keepassDatabaseId = note.keepassDatabaseId,
                                keepassGroupPath = note.keepassGroupPath,
                                bitwardenVaultId = note.bitwardenVaultId,
                                bitwardenFolderId = note.bitwardenFolderId
                            )
                        }
                }
            }

            PasswordPageContentType.PASSKEY,
            PasswordPageContentType.PASSWORD -> Unit
        }
    }

    val messageRes = if (newFavoriteState) {
        R.string.batch_favorited
    } else {
        R.string.batch_unfavorited
    }
    Toast.makeText(
        request.context,
        request.context.getString(messageRes, favoriteTargets),
        Toast.LENGTH_SHORT
    ).show()
    return favoriteTargets
}

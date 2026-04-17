package takagi.ru.monica.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.PasswordListQuickFolderStyle
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.ui.common.pull.PullActionStateHandle
import takagi.ru.monica.ui.components.PullActionVisualState
import takagi.ru.monica.viewmodel.CategoryFilter

@Composable
internal fun PasswordListMainPane(
    canCollapseExpandedGroups: Boolean,
    outsideTapInteractionSource: MutableInteractionSource,
    onCollapseExpandedGroups: () -> Unit,
    isBitwardenDatabaseView: Boolean,
    pullAction: PullActionStateHandle,
    triggerDistance: Float,
    syncTriggerDistance: Float,
    density: Density,
    showPinnedQuickFolderPathBanner: Boolean,
    quickFolderBreadcrumbs: List<PasswordQuickFolderBreadcrumb>,
    currentFilter: CategoryFilter,
    onNavigateFilter: (CategoryFilter) -> Unit,
    shouldGateInitialPasswordFirstFrame: Boolean,
    searchQuery: String,
    isPasswordPageListModelReady: Boolean,
    hasVisibleListItems: Boolean,
    showEmptyState: Boolean,
    hasScrollableHeaderContent: Boolean,
    hasVisibleQuickFilters: Boolean,
    aggregateUiState: PasswordListAggregateUiState,
    emptyStateMessage: PasswordListEmptyStateMessage,
    listState: LazyListState,
    appSettings: AppSettings,
    configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
    quickFilterFavorite: Boolean,
    onQuickFilterFavoriteChange: (Boolean) -> Unit,
    quickFilter2fa: Boolean,
    onQuickFilter2faChange: (Boolean) -> Unit,
    quickFilterNotes: Boolean,
    onQuickFilterNotesChange: (Boolean) -> Unit,
    quickFilterUncategorized: Boolean,
    onQuickFilterUncategorizedChange: (Boolean) -> Unit,
    quickFilterLocalOnly: Boolean,
    onQuickFilterLocalOnlyChange: (Boolean) -> Unit,
    quickFilterManualStackOnly: Boolean,
    onQuickFilterManualStackOnlyChange: (Boolean) -> Unit,
    quickFilterNeverStack: Boolean,
    onQuickFilterNeverStackChange: (Boolean) -> Unit,
    quickFilterUnstacked: Boolean,
    onQuickFilterUnstackedChange: (Boolean) -> Unit,
    onToggleAggregateType: ((PasswordPageContentType) -> Unit)?,
    quickFolderShortcuts: List<PasswordQuickFolderShortcut>,
    quickFolderStyle: PasswordListQuickFolderStyle,
    renderPasswordRows: LazyListScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                enabled = canCollapseExpandedGroups,
                interactionSource = outsideTapInteractionSource,
                indication = null
            ) {
                onCollapseExpandedGroups()
            }
    ) {
        val contentPullOffset = if (isBitwardenDatabaseView) {
            (pullAction.currentOffset * 0.28f).toInt()
        } else {
            pullAction.currentOffset.toInt()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (showPinnedQuickFolderPathBanner) {
                PasswordQuickFolderBreadcrumbBanner(
                    breadcrumbs = quickFolderBreadcrumbs,
                    currentFilter = currentFilter,
                    onNavigate = onNavigateFilter
                )
            }

            if (shouldGateInitialPasswordFirstFrame && searchQuery.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .offset { IntOffset(0, contentPullOffset) },
                    contentAlignment = Alignment.Center
                ) {
                    PasswordListInitialLoadingIndicator()
                }
            } else if (showEmptyState && !hasScrollableHeaderContent) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .offset { IntOffset(0, contentPullOffset) }
                        .pointerInput(isBitwardenDatabaseView) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { _, _ -> }
                            )
                        }
                        .pointerInput(isBitwardenDatabaseView) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    pullAction.onVerticalDrag(dragAmount)
                                },
                                onDragEnd = { pullAction.onDragEnd() },
                                onDragCancel = { pullAction.onDragCancel() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PasswordListEmptyState(
                        message = if (aggregateUiState.hasActiveContentTypeFilter) {
                            PasswordListEmptyStateMessage(titleRes = R.string.no_results)
                        } else {
                            emptyStateMessage
                        }
                    )
                }
            } else {
                PasswordListScrollableContent(
                    listState = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .offset { IntOffset(0, contentPullOffset) }
                        .nestedScroll(pullAction.nestedScrollConnection),
                    isPasswordPageListModelReady = isPasswordPageListModelReady,
                    hasVisibleQuickFilters = hasVisibleQuickFilters,
                    appSettings = appSettings,
                    configuredQuickFilterItems = configuredQuickFilterItems,
                    aggregateUiState = aggregateUiState,
                    quickFilterFavorite = quickFilterFavorite,
                    onQuickFilterFavoriteChange = onQuickFilterFavoriteChange,
                    quickFilter2fa = quickFilter2fa,
                    onQuickFilter2faChange = onQuickFilter2faChange,
                    quickFilterNotes = quickFilterNotes,
                    onQuickFilterNotesChange = onQuickFilterNotesChange,
                    quickFilterUncategorized = quickFilterUncategorized,
                    onQuickFilterUncategorizedChange = onQuickFilterUncategorizedChange,
                    quickFilterLocalOnly = quickFilterLocalOnly,
                    onQuickFilterLocalOnlyChange = onQuickFilterLocalOnlyChange,
                    quickFilterManualStackOnly = quickFilterManualStackOnly,
                    onQuickFilterManualStackOnlyChange = onQuickFilterManualStackOnlyChange,
                    quickFilterNeverStack = quickFilterNeverStack,
                    onQuickFilterNeverStackChange = onQuickFilterNeverStackChange,
                    quickFilterUnstacked = quickFilterUnstacked,
                    onQuickFilterUnstackedChange = onQuickFilterUnstackedChange,
                    onToggleAggregateType = onToggleAggregateType,
                    quickFolderShortcuts = quickFolderShortcuts,
                    quickFolderStyle = quickFolderStyle,
                    currentFilter = currentFilter,
                    onNavigateFilter = onNavigateFilter,
                    hasVisibleListItems = hasVisibleListItems,
                    showEmptyState = showEmptyState,
                    searchQuery = searchQuery,
                    emptyStateMessage = emptyStateMessage,
                    renderPasswordRows = renderPasswordRows
                )
            }
        }
    }
}

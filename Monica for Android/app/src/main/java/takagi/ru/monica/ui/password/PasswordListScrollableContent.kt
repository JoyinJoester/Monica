package takagi.ru.monica.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.PasswordListQuickFolderStyle
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.viewmodel.CategoryFilter

private const val PASSWORD_LIST_QUICK_FILTERS_KEY = "quick_filters"
private const val PASSWORD_LIST_QUICK_FOLDER_SHORTCUTS_KEY = "quick_folder_shortcuts"
private const val PASSWORD_LIST_EMPTY_STATE_WITH_HEADERS_KEY = "empty_state_with_quick_headers"
private const val PASSWORD_LIST_BOTTOM_SPACER_KEY = "password_list_bottom_spacer"

@Composable
internal fun PasswordListScrollableContent(
    listState: LazyListState,
    modifier: Modifier,
    isPasswordPageListModelReady: Boolean,
    hasVisibleQuickFilters: Boolean,
    appSettings: AppSettings,
    configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
    aggregateUiState: PasswordListAggregateUiState,
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
    currentFilter: CategoryFilter,
    onNavigateFilter: (CategoryFilter) -> Unit,
    hasVisibleListItems: Boolean,
    searchQuery: String,
    emptyStateMessage: PasswordListEmptyStateMessage,
    renderPasswordRows: LazyListScope.() -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (hasVisibleQuickFilters) {
            item(key = PASSWORD_LIST_QUICK_FILTERS_KEY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 2.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (appSettings.passwordListQuickFiltersEnabled) {
                        configuredQuickFilterItems.forEach { item ->
                            if (shouldShowQuickFilterItem(item, aggregateUiState.visibleContentTypes)) {
                                PasswordQuickFilterChipItem(
                                    item = item,
                                    categoryEditMode = false,
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
                                    aggregateSelectedTypes = aggregateUiState.selectedContentTypes,
                                    aggregateVisibleTypes = aggregateUiState.visibleContentTypes,
                                    onToggleAggregateType = onToggleAggregateType
                                )
                            }
                        }
                    }
                }
            }
        }

        if (quickFolderShortcuts.isNotEmpty()) {
            val quickFolderUseM3CardStyle =
                quickFolderStyle == PasswordListQuickFolderStyle.M3_CARD
            item(key = PASSWORD_LIST_QUICK_FOLDER_SHORTCUTS_KEY) {
                PasswordQuickFolderShortcutsSection(
                    shortcuts = quickFolderShortcuts,
                    currentFilter = currentFilter,
                    useM3CardStyle = quickFolderUseM3CardStyle,
                    onNavigate = onNavigateFilter
                )
            }
        }

        if (isPasswordPageListModelReady && !hasVisibleListItems && searchQuery.isEmpty()) {
            item(key = PASSWORD_LIST_EMPTY_STATE_WITH_HEADERS_KEY) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 84.dp, bottom = 24.dp),
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
            }
        } else {
            renderPasswordRows()
        }

        item(key = PASSWORD_LIST_BOTTOM_SPACER_KEY) {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
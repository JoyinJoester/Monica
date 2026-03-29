package takagi.ru.monica.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.unit.IntSize
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.data.PasswordPageContentType

internal data class PasswordCategoryMenuQuickFilterState(
    val order: List<PasswordListQuickFilterItem>,
    val onOrderChange: (List<PasswordListQuickFilterItem>) -> Unit,
    val measuredSizes: SnapshotStateMap<PasswordListQuickFilterItem, IntSize>
)

@Composable
internal fun rememberCategoryMenuQuickFilterState(
    configuredQuickFilterItems: List<PasswordListQuickFilterItem>
): PasswordCategoryMenuQuickFilterState {
    val quickFilterItems = remember(configuredQuickFilterItems) {
        configuredQuickFilterItems.ifEmpty { PasswordListQuickFilterItem.DEFAULT_ORDER }
    }
    var quickFilterOrder by remember(quickFilterItems) { mutableStateOf(quickFilterItems) }
    val quickFilterMeasuredSizes = remember {
        mutableStateMapOf<PasswordListQuickFilterItem, IntSize>()
    }

    LaunchedEffect(quickFilterItems) {
        quickFilterOrder = quickFilterItems
    }

    return PasswordCategoryMenuQuickFilterState(
        order = quickFilterOrder,
        onOrderChange = { quickFilterOrder = it },
        measuredSizes = quickFilterMeasuredSizes
    )
}

internal data class PasswordCategoryMenuQuickFilterBindings(
    val state: PasswordQuickFilterChipState,
    val callbacks: PasswordQuickFilterChipCallbacks
)

internal fun buildCategoryMenuQuickFilterBindings(
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
    aggregateSelectedTypes: Set<PasswordPageContentType>,
    aggregateVisibleTypes: List<PasswordPageContentType>,
    onToggleAggregateType: (PasswordPageContentType) -> Unit
): PasswordCategoryMenuQuickFilterBindings {
    return PasswordCategoryMenuQuickFilterBindings(
        state = PasswordQuickFilterChipState(
            favorite = quickFilterFavorite,
            twoFa = quickFilter2fa,
            notes = quickFilterNotes,
            uncategorized = quickFilterUncategorized,
            localOnly = quickFilterLocalOnly,
            manualStackOnly = quickFilterManualStackOnly,
            neverStack = quickFilterNeverStack,
            unstacked = quickFilterUnstacked,
            aggregateSelectedTypes = aggregateSelectedTypes,
            aggregateVisibleTypes = aggregateVisibleTypes
        ),
        callbacks = PasswordQuickFilterChipCallbacks(
            onFavoriteChange = onQuickFilterFavoriteChange,
            onTwoFaChange = onQuickFilter2faChange,
            onNotesChange = onQuickFilterNotesChange,
            onUncategorizedChange = onQuickFilterUncategorizedChange,
            onLocalOnlyChange = onQuickFilterLocalOnlyChange,
            onManualStackOnlyChange = onQuickFilterManualStackOnlyChange,
            onNeverStackChange = onQuickFilterNeverStackChange,
            onUnstackedChange = onQuickFilterUnstackedChange,
            onToggleAggregateType = onToggleAggregateType
        )
    )
}

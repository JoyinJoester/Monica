package takagi.ru.monica.ui.vaultv2

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.PasswordListQuickFilterItem
import takagi.ru.monica.ui.PasswordQuickFilterChipCallbacks
import takagi.ru.monica.ui.PasswordQuickFilterChipItem
import takagi.ru.monica.ui.PasswordQuickFilterChipState
import takagi.ru.monica.ui.shouldShowQuickFilterItem

@Composable
internal fun VaultV2QuickFilterRow(
    configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
    chipState: PasswordQuickFilterChipState,
    chipCallbacks: PasswordQuickFilterChipCallbacks,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 2.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VaultV2QuickFilterChips(
            configuredQuickFilterItems = configuredQuickFilterItems,
            chipState = chipState,
            chipCallbacks = chipCallbacks,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VaultV2QuickFilterFlow(
    configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
    chipState: PasswordQuickFilterChipState,
    chipCallbacks: PasswordQuickFilterChipCallbacks,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VaultV2QuickFilterChips(
            configuredQuickFilterItems = configuredQuickFilterItems,
            chipState = chipState,
            chipCallbacks = chipCallbacks,
        )
    }
}

@Composable
private fun VaultV2QuickFilterChips(
    configuredQuickFilterItems: List<PasswordListQuickFilterItem>,
    chipState: PasswordQuickFilterChipState,
    chipCallbacks: PasswordQuickFilterChipCallbacks,
) {
    configuredQuickFilterItems.forEach { item ->
        if (!shouldShowQuickFilterItem(item, chipState.aggregateVisibleTypes)) return@forEach
        PasswordQuickFilterChipItem(
            item = item,
            categoryEditMode = false,
            quickFilterFavorite = chipState.favorite,
            onQuickFilterFavoriteChange = chipCallbacks.onFavoriteChange,
            quickFilter2fa = chipState.twoFa,
            onQuickFilter2faChange = chipCallbacks.onTwoFaChange,
            quickFilterNotes = chipState.notes,
            onQuickFilterNotesChange = chipCallbacks.onNotesChange,
            quickFilterUncategorized = chipState.uncategorized,
            onQuickFilterUncategorizedChange = chipCallbacks.onUncategorizedChange,
            quickFilterLocalOnly = chipState.localOnly,
            onQuickFilterLocalOnlyChange = chipCallbacks.onLocalOnlyChange,
            quickFilterManualStackOnly = chipState.manualStackOnly,
            onQuickFilterManualStackOnlyChange = chipCallbacks.onManualStackOnlyChange,
            quickFilterNeverStack = chipState.neverStack,
            onQuickFilterNeverStackChange = chipCallbacks.onNeverStackChange,
            quickFilterUnstacked = chipState.unstacked,
            onQuickFilterUnstackedChange = chipCallbacks.onUnstackedChange,
            aggregateSelectedTypes = chipState.aggregateSelectedTypes,
            aggregateVisibleTypes = chipState.aggregateVisibleTypes,
            onToggleAggregateType = chipCallbacks.onToggleAggregateType,
        )
    }
}

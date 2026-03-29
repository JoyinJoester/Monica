package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.PasswordListQuickFilterItem

internal data class PasswordQuickFilterFlowParams(
    val items: List<PasswordListQuickFilterItem>,
    val measuredSizes: MutableMap<PasswordListQuickFilterItem, IntSize>,
    val chipState: PasswordQuickFilterChipState,
    val chipCallbacks: PasswordQuickFilterChipCallbacks
)

@Composable
internal fun PasswordQuickFilterFlow(
    params: PasswordQuickFilterFlowParams,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        params.items.forEach { item ->
            if (!shouldShowQuickFilterItem(item, params.chipState.aggregateVisibleTypes)) return@forEach
            androidx.compose.runtime.key(item) {
                Box(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val size = coordinates.size
                        if (params.measuredSizes[item] != size) {
                            params.measuredSizes[item] = size
                        }
                    }
                ) {
                    PasswordQuickFilterChipItem(
                        item = item,
                        categoryEditMode = false,
                        quickFilterFavorite = params.chipState.favorite,
                        onQuickFilterFavoriteChange = params.chipCallbacks.onFavoriteChange,
                        quickFilter2fa = params.chipState.twoFa,
                        onQuickFilter2faChange = params.chipCallbacks.onTwoFaChange,
                        quickFilterNotes = params.chipState.notes,
                        onQuickFilterNotesChange = params.chipCallbacks.onNotesChange,
                        quickFilterUncategorized = params.chipState.uncategorized,
                        onQuickFilterUncategorizedChange = params.chipCallbacks.onUncategorizedChange,
                        quickFilterLocalOnly = params.chipState.localOnly,
                        onQuickFilterLocalOnlyChange = params.chipCallbacks.onLocalOnlyChange,
                        quickFilterManualStackOnly = params.chipState.manualStackOnly,
                        onQuickFilterManualStackOnlyChange = params.chipCallbacks.onManualStackOnlyChange,
                        quickFilterNeverStack = params.chipState.neverStack,
                        onQuickFilterNeverStackChange = params.chipCallbacks.onNeverStackChange,
                        quickFilterUnstacked = params.chipState.unstacked,
                        onQuickFilterUnstackedChange = params.chipCallbacks.onUnstackedChange,
                        aggregateSelectedTypes = params.chipState.aggregateSelectedTypes,
                        aggregateVisibleTypes = params.chipState.aggregateVisibleTypes,
                        onToggleAggregateType = params.chipCallbacks.onToggleAggregateType
                    )
                }
            }
        }
    }
}

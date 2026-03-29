package takagi.ru.monica.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip
import takagi.ru.monica.viewmodel.CategoryFilter

internal data class PasswordDatabaseFiltersSectionParams(
    val currentFilter: CategoryFilter,
    val keepassDatabases: List<takagi.ru.monica.data.LocalKeePassDatabase>,
    val bitwardenVaults: List<BitwardenVault>,
    val onSelectFilter: (CategoryFilter) -> Unit
)

@Composable
internal fun PasswordDatabaseFiltersSection(
    params: PasswordDatabaseFiltersSectionParams,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Text(
        text = stringResource(R.string.category_selection_menu_databases),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MonicaExpressiveFilterChip(
            selected = params.currentFilter is CategoryFilter.All,
            onClick = { params.onSelectFilter(CategoryFilter.All) },
            label = stringResource(R.string.category_all),
            leadingIcon = Icons.Default.List
        )
        MonicaExpressiveFilterChip(
            selected = params.currentFilter.isMonicaDatabaseFilter(),
            onClick = { params.onSelectFilter(CategoryFilter.Local) },
            label = stringResource(R.string.category_selection_menu_local_database),
            leadingIcon = Icons.Default.Smartphone
        )
        params.keepassDatabases.forEach { database ->
            MonicaExpressiveFilterChip(
                selected = params.currentFilter.isKeePassDatabaseFilter(database.id),
                onClick = { params.onSelectFilter(CategoryFilter.KeePassDatabase(database.id)) },
                label = database.name,
                leadingIcon = Icons.Default.Key
            )
        }
        params.bitwardenVaults.forEach { vault ->
            MonicaExpressiveFilterChip(
                selected = params.currentFilter.isBitwardenVaultFilter(vault.id),
                onClick = { params.onSelectFilter(CategoryFilter.BitwardenVault(vault.id)) },
                label = vault.email.ifBlank { "Bitwarden" },
                leadingIcon = Icons.Default.CloudSync
            )
        }
    }
}

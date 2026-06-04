package takagi.ru.monica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.writeOperationAvailability
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip
import takagi.ru.monica.viewmodel.CategoryFilter

internal data class PasswordDatabaseFiltersSectionParams(
    val currentFilter: CategoryFilter,
    val keepassDatabases: List<LocalKeePassDatabase>,
    val mdbxDatabases: List<LocalMdbxDatabase>,
    val bitwardenVaults: List<BitwardenVault>,
    val onSelectFilter: (CategoryFilter) -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PasswordDatabaseFiltersSection(
    params: PasswordDatabaseFiltersSectionParams,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(durationMillis = 160),
        label = "database_section_arrow"
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.category_selection_menu_databases),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
            exit = shrinkVertically(animationSpec = tween(140)) + fadeOut(animationSpec = tween(100))
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        leadingIcon = Icons.Default.Key,
                        statusDotColor = if (database.writeOperationAvailability().canOperate) {
                            StorageHealthyGreen
                        } else {
                            null
                        }
                    )
                }
                params.mdbxDatabases.forEach { database ->
                    MonicaExpressiveFilterChip(
                        selected = params.currentFilter.isMdbxDatabaseFilter(database.id),
                        onClick = { params.onSelectFilter(CategoryFilter.MdbxDatabase(database.id)) },
                        label = database.name,
                        leadingIcon = Icons.Default.Storage
                    )
                }
                params.bitwardenVaults.forEach { vault ->
                    MonicaExpressiveFilterChip(
                        selected = params.currentFilter.isBitwardenVaultFilter(vault.id),
                        onClick = { params.onSelectFilter(CategoryFilter.BitwardenVault(vault.id)) },
                        label = vault.email.ifBlank { "Bitwarden" },
                        leadingIcon = Icons.Default.CloudSync,
                        statusDotColor = if (vault.hasHealthyConnection()) StorageHealthyGreen else null
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = !expanded,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
            exit = shrinkVertically(animationSpec = tween(140)) + fadeOut(animationSpec = tween(100))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
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
                        leadingIcon = Icons.Default.Key,
                        statusDotColor = if (database.writeOperationAvailability().canOperate) {
                            StorageHealthyGreen
                        } else {
                            null
                        }
                    )
                }
                params.mdbxDatabases.forEach { database ->
                    MonicaExpressiveFilterChip(
                        selected = params.currentFilter.isMdbxDatabaseFilter(database.id),
                        onClick = { params.onSelectFilter(CategoryFilter.MdbxDatabase(database.id)) },
                        label = database.name,
                        leadingIcon = Icons.Default.Storage
                    )
                }
                params.bitwardenVaults.forEach { vault ->
                    MonicaExpressiveFilterChip(
                        selected = params.currentFilter.isBitwardenVaultFilter(vault.id),
                        onClick = { params.onSelectFilter(CategoryFilter.BitwardenVault(vault.id)) },
                        label = vault.email.ifBlank { "Bitwarden" },
                        leadingIcon = Icons.Default.CloudSync,
                        statusDotColor = if (vault.hasHealthyConnection()) StorageHealthyGreen else null
                    )
                }
            }
        }
    }
}

private val StorageHealthyGreen = Color(0xFF22C55E)

private fun BitwardenVault.hasHealthyConnection(): Boolean {
    return isConnected && !encryptedRefreshToken.isNullOrBlank()
}

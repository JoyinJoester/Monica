package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.CompromisedPassword
import takagi.ru.monica.data.DuplicatePasswordGroup
import takagi.ru.monica.data.DuplicateUrlGroup
import takagi.ru.monica.data.InactivePasskeyAccount
import takagi.ru.monica.data.No2FAAccount
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordStrengthDistribution
import takagi.ru.monica.data.SecurityAnalysisData
import takagi.ru.monica.data.SecurityAnalysisScopeOption
import takagi.ru.monica.data.SecurityAnalysisScopeType

private enum class SecurityIssueType {
    DUPLICATE_PASSWORDS,
    DUPLICATE_URLS,
    COMPROMISED,
    NO_2FA,
    INACTIVE_PASSKEY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAnalysisScreen(
    analysisData: SecurityAnalysisData,
    autoAnalysisEnabled: Boolean,
    onStartAnalysis: () -> Unit,
    onAutoAnalysisEnabledChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToPassword: (Long) -> Unit,
    onSelectScope: (String) -> Unit
) {
    var selectedIssue by rememberSaveable { mutableStateOf<SecurityIssueType?>(null) }

    if (selectedIssue != null) {
        SecurityIssueDetailScreen(
            issueType = selectedIssue ?: SecurityIssueType.DUPLICATE_PASSWORDS,
            analysisData = analysisData,
            onNavigateBack = { selectedIssue = null },
            onNavigateToPassword = onNavigateToPassword
        )
        return
    }

    val duplicatePasswordGroupCount = analysisData.duplicatePasswords.size
    val duplicatePasswordItemCount = analysisData.duplicatePasswords.sumOf { it.count }
    val duplicateUrlGroupCount = analysisData.duplicateUrls.size
    val duplicateUrlItemCount = analysisData.duplicateUrls.sumOf { it.count }
    val compromisedCount = analysisData.compromisedPasswords.size
    val no2faCount = analysisData.no2FAAccounts.count { it.supports2FA }
    val inactivePasskeyCount = analysisData.inactivePasskeyAccounts.size

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(text = stringResource(R.string.security_analysis)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        if (analysisData.isAnalyzing) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(stringResource(R.string.security_analysis_in_progress_short)) }
                            )
                        }
                        IconButton(onClick = onStartAnalysis) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh)
                            )
                        }
                    }
                )
                if (analysisData.isAnalyzing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CompactOverviewCard(
                    duplicatePasswordsCount = duplicatePasswordItemCount,
                    duplicateUrlsCount = duplicateUrlItemCount,
                    compromisedCount = compromisedCount,
                    no2faCount = no2faCount,
                    inactivePasskeyCount = inactivePasskeyCount,
                    isAnalyzing = analysisData.isAnalyzing
                )
            }

            item {
                AutoAnalysisToggleCard(
                    autoAnalysisEnabled = autoAnalysisEnabled,
                    onAutoAnalysisEnabledChange = onAutoAnalysisEnabledChange
                )
            }

            item {
                ScopeSelectorCard(
                    scopes = analysisData.availableScopes,
                    selectedScopeKey = analysisData.selectedScopeKey,
                    onSelectScope = onSelectScope
                )
            }

            item {
                SecurityStrengthDistributionCard(
                    distribution = analysisData.passwordStrengthDistribution
                )
            }

            item {
                Text(
                    text = stringResource(R.string.security_risk_cards_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                SecurityIssueSummaryCard(
                    icon = Icons.Default.ContentCopy,
                    title = stringResource(R.string.duplicate_passwords),
                    subtitle = stringResource(
                        R.string.security_issue_duplicate_password_subtitle,
                        duplicatePasswordGroupCount,
                        duplicatePasswordItemCount
                    ),
                    onClick = { selectedIssue = SecurityIssueType.DUPLICATE_PASSWORDS }
                )
            }

            item {
                SecurityIssueSummaryCard(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.duplicate_urls),
                    subtitle = stringResource(
                        R.string.security_issue_duplicate_url_subtitle,
                        duplicateUrlGroupCount,
                        duplicateUrlItemCount
                    ),
                    onClick = { selectedIssue = SecurityIssueType.DUPLICATE_URLS }
                )
            }

            item {
                SecurityIssueSummaryCard(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.compromised_passwords),
                    subtitle = stringResource(
                        R.string.security_issue_simple_count_subtitle,
                        compromisedCount
                    ),
                    onClick = { selectedIssue = SecurityIssueType.COMPROMISED }
                )
            }

            item {
                SecurityIssueSummaryCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.no_twofa),
                    subtitle = stringResource(
                        R.string.security_issue_simple_count_subtitle,
                        no2faCount
                    ),
                    onClick = { selectedIssue = SecurityIssueType.NO_2FA }
                )
            }

            item {
                SecurityIssueSummaryCard(
                    icon = Icons.Default.VpnKey,
                    title = stringResource(R.string.inactive_passkeys),
                    subtitle = stringResource(
                        R.string.security_issue_simple_count_subtitle,
                        inactivePasskeyCount
                    ),
                    onClick = { selectedIssue = SecurityIssueType.INACTIVE_PASSKEY }
                )
            }
        }

        analysisData.error?.let { error ->
            Snackbar(modifier = Modifier.padding(16.dp)) {
                Text(error)
            }
        }
    }
}

@Composable
private fun AutoAnalysisToggleCard(
    autoAnalysisEnabled: Boolean,
    onAutoAnalysisEnabledChange: (Boolean) -> Unit
) {
    OutlinedCard(shape = RoundedCornerShape(16.dp)) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.security_analysis_auto_toggle_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(R.string.security_analysis_auto_toggle_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = autoAnalysisEnabled,
                    onCheckedChange = onAutoAnalysisEnabledChange
                )
            }
        )
    }
}

@Composable
private fun CompactOverviewCard(
    duplicatePasswordsCount: Int,
    duplicateUrlsCount: Int,
    compromisedCount: Int,
    no2faCount: Int,
    inactivePasskeyCount: Int,
    isAnalyzing: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primaryContainer.copy(alpha = 0.36f),
                            colorScheme.secondaryContainer.copy(alpha = 0.20f)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.security_overview),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isAnalyzing) {
                            stringResource(R.string.security_analysis_in_progress_short)
                        } else {
                            stringResource(R.string.security_analysis_realtime)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.security_risk_cards_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${stringResource(R.string.duplicate_short)} $duplicatePasswordsCount") }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${stringResource(R.string.duplicate_url_short)} $duplicateUrlsCount") }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${stringResource(R.string.compromised_short)} $compromisedCount") }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${stringResource(R.string.no_2fa_short)} $no2faCount") }
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${stringResource(R.string.inactive_passkeys_short)} $inactivePasskeyCount") }
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeSelectorCard(
    scopes: List<SecurityAnalysisScopeOption>,
    selectedScopeKey: String,
    onSelectScope: (String) -> Unit
) {
    val context = LocalContext.current
    val selectedScope = scopes.firstOrNull { it.key == selectedScopeKey } ?: scopes.firstOrNull() ?: SecurityAnalysisScopeOption.all()
    var expanded by remember { mutableStateOf(false) }

    OutlinedCard(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.security_analysis_scope_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = scopeDisplayName(selectedScope, context),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.security_analysis_scope_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    scopes.forEach { scope ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${scopeDisplayName(scope, context)} (${scope.itemCount})"
                                )
                            },
                            onClick = {
                                expanded = false
                                onSelectScope(scope.key)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityStrengthDistributionCard(
    distribution: PasswordStrengthDistribution
) {
    val colorScheme = MaterialTheme.colorScheme
    val total = distribution.total
    OutlinedCard(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.security_strength_distribution),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
            ) {
                val segments = listOf(
                    Pair(distribution.weak, colorScheme.error),
                    Pair(distribution.medium, colorScheme.tertiary),
                    Pair(distribution.strong, colorScheme.secondary),
                    Pair(distribution.veryStrong, colorScheme.primary)
                )
                if (total > 0) {
                    segments.forEach { (count, color) ->
                        if (count > 0) {
                            val rawWeight = count / total.toFloat()
                            val weight = if (rawWeight < 0.08f) 0.08f else rawWeight
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxSize()
                                    .background(color = color)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StrengthLegendChip(
                    label = stringResource(R.string.strength_weak),
                    count = distribution.weak,
                    color = colorScheme.error
                )
                StrengthLegendChip(
                    label = stringResource(R.string.security_strength_medium),
                    count = distribution.medium,
                    color = colorScheme.tertiary
                )
                StrengthLegendChip(
                    label = stringResource(R.string.strength_strong),
                    count = distribution.strong,
                    color = colorScheme.secondary
                )
                StrengthLegendChip(
                    label = stringResource(R.string.security_strength_very_strong),
                    count = distribution.veryStrong,
                    color = colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StrengthLegendChip(
    label: String,
    count: Int,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.20f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SecurityIssueSummaryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityIssueDetailScreen(
    issueType: SecurityIssueType,
    analysisData: SecurityAnalysisData,
    onNavigateBack: () -> Unit,
    onNavigateToPassword: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (issueType) {
                            SecurityIssueType.DUPLICATE_PASSWORDS -> stringResource(R.string.duplicate_passwords)
                            SecurityIssueType.DUPLICATE_URLS -> stringResource(R.string.duplicate_urls)
                            SecurityIssueType.COMPROMISED -> stringResource(R.string.compromised_passwords)
                            SecurityIssueType.NO_2FA -> stringResource(R.string.no_twofa)
                            SecurityIssueType.INACTIVE_PASSKEY -> stringResource(R.string.inactive_passkeys)
                        }
                    )
                },
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
        when (issueType) {
            SecurityIssueType.DUPLICATE_PASSWORDS -> DuplicatePasswordsFlatList(
                groups = analysisData.duplicatePasswords,
                onNavigateToPassword = onNavigateToPassword,
                modifier = Modifier.padding(paddingValues)
            )
            SecurityIssueType.DUPLICATE_URLS -> DuplicateUrlsFlatList(
                groups = analysisData.duplicateUrls,
                onNavigateToPassword = onNavigateToPassword,
                modifier = Modifier.padding(paddingValues)
            )
            SecurityIssueType.COMPROMISED -> CompromisedFlatList(
                items = analysisData.compromisedPasswords,
                onNavigateToPassword = onNavigateToPassword,
                modifier = Modifier.padding(paddingValues)
            )
            SecurityIssueType.NO_2FA -> No2faFlatList(
                items = analysisData.no2FAAccounts,
                onNavigateToPassword = onNavigateToPassword,
                modifier = Modifier.padding(paddingValues)
            )
            SecurityIssueType.INACTIVE_PASSKEY -> InactivePasskeyFlatList(
                items = analysisData.inactivePasskeyAccounts,
                onNavigateToPassword = onNavigateToPassword,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun DuplicatePasswordsFlatList(
    groups: List<DuplicatePasswordGroup>,
    onNavigateToPassword: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (groups.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            message = stringResource(R.string.no_duplicate_passwords),
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(groups) { group ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.used_in_accounts, group.count),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${group.entries.size}") }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.entries.forEach { entry ->
                            SecurityDetailEntryCard(
                                title = entry.title,
                                subtitle = entry.username,
                                detail = entry.website,
                                icon = Icons.Default.VpnKey,
                                onClick = { onNavigateToPassword(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateUrlsFlatList(
    groups: List<DuplicateUrlGroup>,
    onNavigateToPassword: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (groups.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            message = stringResource(R.string.no_duplicate_urls),
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(groups) { group ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = group.url,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${group.count}") }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.entries.forEach { entry ->
                            SecurityDetailEntryCard(
                                title = entry.title,
                                subtitle = entry.username,
                                detail = entry.website,
                                icon = Icons.Default.Link,
                                onClick = { onNavigateToPassword(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompromisedFlatList(
    items: List<CompromisedPassword>,
    onNavigateToPassword: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            message = stringResource(R.string.no_compromised_passwords),
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            SecurityDetailEntryCard(
                title = item.entry.title,
                subtitle = item.entry.username,
                detail = stringResource(R.string.breached_times, item.breachCount),
                icon = Icons.Default.Warning,
                onClick = { onNavigateToPassword(item.entry.id) }
            )
        }
    }
}

@Composable
private fun No2faFlatList(
    items: List<No2FAAccount>,
    onNavigateToPassword: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            message = stringResource(R.string.all_accounts_have_twofa),
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            SecurityDetailEntryCard(
                title = item.entry.title,
                subtitle = item.entry.username,
                detail = item.domain,
                icon = Icons.Default.Lock,
                onClick = { onNavigateToPassword(item.entry.id) }
            )
        }
    }
}

@Composable
private fun InactivePasskeyFlatList(
    items: List<InactivePasskeyAccount>,
    onNavigateToPassword: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.CheckCircle,
            message = stringResource(R.string.all_accounts_have_passkeys),
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            SecurityDetailEntryCard(
                title = item.entry.title,
                subtitle = item.entry.username,
                detail = item.domain,
                icon = Icons.Default.VpnKey,
                onClick = { onNavigateToPassword(item.entry.id) }
            )
        }
    }
}

@Composable
private fun SecurityDetailEntryCard(
    title: String,
    subtitle: String?,
    detail: String?,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        )
    ) {
        ListItem(
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            headlineContent = {
                Text(
                    text = title.ifBlank { "—" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!detail.isNullOrBlank()) {
                        Text(
                            text = detail,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
private fun EmptyStateView(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun scopeDisplayName(
    scope: SecurityAnalysisScopeOption,
    context: android.content.Context
): String {
    return when (scope.type) {
        SecurityAnalysisScopeType.ALL -> context.getString(R.string.security_analysis_scope_all)
        SecurityAnalysisScopeType.LOCAL -> context.getString(R.string.security_analysis_scope_local)
        SecurityAnalysisScopeType.KEEPASS -> {
            val name = scope.displayName
            if (!name.isNullOrBlank()) {
                "KeePass · $name"
            } else {
                context.getString(R.string.security_analysis_scope_keepass, scope.sourceId ?: 0L)
            }
        }
        SecurityAnalysisScopeType.BITWARDEN -> {
            val name = scope.displayName
            if (!name.isNullOrBlank()) {
                "Bitwarden · $name"
            } else {
                context.getString(R.string.security_analysis_scope_bitwarden, scope.sourceId ?: 0L)
            }
        }
    }
}

package takagi.ru.monica.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay

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
    var lastSelectedIssue by rememberSaveable { mutableStateOf(SecurityIssueType.DUPLICATE_PASSWORDS) }

    val duplicatePasswordGroupCount = analysisData.duplicatePasswords.size
    val duplicatePasswordItemCount = analysisData.duplicatePasswords.sumOf { it.count }
    val duplicateUrlGroupCount = analysisData.duplicateUrls.size
    val duplicateUrlItemCount = analysisData.duplicateUrls.sumOf { it.count }
    val compromisedCount = analysisData.compromisedPasswords.size
    val no2faCount = analysisData.no2FAAccounts.count { it.supports2FA }
    val inactivePasskeyCount = analysisData.inactivePasskeyAccounts.size
    val totalRiskCount = duplicatePasswordItemCount + duplicateUrlItemCount + compromisedCount + no2faCount + inactivePasskeyCount
    var showAnalyzingUi by remember { mutableStateOf(analysisData.isAnalyzing) }
    LaunchedEffect(analysisData.isAnalyzing) {
        if (analysisData.isAnalyzing) {
            delay(300)
            if (analysisData.isAnalyzing) {
                showAnalyzingUi = true
            }
        } else {
            showAnalyzingUi = false
        }
    }
    val analyzingTransition = rememberInfiniteTransition(label = "security_analysis_progress")
    val loopingProgress by analyzingTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "security_analysis_looping_progress"
    )
    val effectiveProgress = if (analysisData.analysisProgress in 1..99) {
        analysisData.analysisProgress / 100f
    } else {
        loopingProgress
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        },
                        actions = {
                            if (showAnalyzingUi) {
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
                    if (showAnalyzingUi) {
                        LinearProgressIndicator(
                            progress = { effectiveProgress },
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                SecurityOverviewHeader(
                    score = analysisData.securityScore,
                    riskCount = totalRiskCount,
                    isAnalyzing = showAnalyzingUi,
                    progress = effectiveProgress
                )
            }

            item {
                SecurityStrengthDistributionCard(
                    distribution = analysisData.passwordStrengthDistribution
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
                AutoAnalysisToggleCard(
                    autoAnalysisEnabled = autoAnalysisEnabled,
                    onAutoAnalysisEnabledChange = onAutoAnalysisEnabledChange
                )
            }

            item {
                SecurityIssueGrid(
                    items = listOf(
                        SecurityIssueGridItem(
                            icon = Icons.Default.ContentCopy,
                            title = stringResource(R.string.duplicate_passwords),
                            count = duplicatePasswordItemCount,
                            subtitle = stringResource(
                                R.string.security_issue_duplicate_password_subtitle,
                                duplicatePasswordGroupCount,
                                duplicatePasswordItemCount
                            ),
                            issueType = SecurityIssueType.DUPLICATE_PASSWORDS
                        ),
                        SecurityIssueGridItem(
                            icon = Icons.Default.Link,
                            title = stringResource(R.string.duplicate_urls),
                            count = duplicateUrlItemCount,
                            subtitle = stringResource(
                                R.string.security_issue_duplicate_url_subtitle,
                                duplicateUrlGroupCount,
                                duplicateUrlItemCount
                            ),
                            issueType = SecurityIssueType.DUPLICATE_URLS
                        ),
                        SecurityIssueGridItem(
                            icon = Icons.Default.Warning,
                            title = stringResource(R.string.compromised_passwords),
                            count = compromisedCount,
                            subtitle = stringResource(R.string.security_issue_simple_count_subtitle, compromisedCount),
                            issueType = SecurityIssueType.COMPROMISED
                        ),
                        SecurityIssueGridItem(
                            icon = Icons.Default.Security,
                            title = stringResource(R.string.no_twofa),
                            count = no2faCount,
                            subtitle = stringResource(R.string.security_issue_simple_count_subtitle, no2faCount),
                            issueType = SecurityIssueType.NO_2FA
                        ),
                        SecurityIssueGridItem(
                            icon = Icons.Default.VpnKey,
                            title = stringResource(R.string.inactive_passkeys),
                            count = inactivePasskeyCount,
                            subtitle = stringResource(R.string.security_issue_simple_count_subtitle, inactivePasskeyCount),
                            issueType = SecurityIssueType.INACTIVE_PASSKEY
                        )
                    ),
                    onSelectIssue = {
                        lastSelectedIssue = it
                        selectedIssue = it
                    }
                )
            }
        }

            analysisData.error?.let { error ->
                Snackbar(modifier = Modifier.padding(16.dp)) {
                    Text(error)
                }
            }
        }

        AnimatedVisibility(
            visible = selectedIssue != null,
            enter = slideInHorizontally(
                animationSpec = tween(durationMillis = 300),
                initialOffsetX = { fullWidth -> fullWidth / 8 }
            ) + fadeIn(animationSpec = tween(durationMillis = 280)),
            exit = slideOutHorizontally(
                animationSpec = tween(durationMillis = 280),
                targetOffsetX = { fullWidth -> fullWidth / 8 }
            ) + fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            SecurityIssueDetailScreen(
                issueType = selectedIssue ?: lastSelectedIssue,
                analysisData = analysisData,
                onNavigateBack = { selectedIssue = null },
                onNavigateToPassword = onNavigateToPassword
            )
        }
    }
}

@Composable
private fun AutoAnalysisToggleCard(
    autoAnalysisEnabled: Boolean,
    onAutoAnalysisEnabledChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier
                    .size(42.dp)
                    .background(colorScheme.primaryContainer.copy(alpha = 0.72f), CircleShape)
                    .padding(9.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.security_analysis_auto_toggle_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.security_analysis_auto_toggle_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoAnalysisEnabled,
                onCheckedChange = onAutoAnalysisEnabledChange
            )
        }
    }
}

@Composable
private fun SecurityOverviewHeader(
    score: Int,
    riskCount: Int,
    isAnalyzing: Boolean,
    progress: Float
) {
    val colorScheme = MaterialTheme.colorScheme
    val animatedScore by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 700),
        label = "security_score_progress"
    )
    val scoreColor = when {
        score >= 80 -> Color(0xFF22C55E)
        score >= 55 -> Color(0xFFF59E0B)
        else -> colorScheme.error
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 4 }
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colorScheme.primaryContainer.copy(alpha = 0.42f),
                                colorScheme.secondaryContainer.copy(alpha = 0.20f),
                                colorScheme.surface.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.security_analysis),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isAnalyzing) {
                                    stringResource(R.string.security_analysis_in_progress_short)
                                } else {
                                    stringResource(R.string.security_analysis_realtime)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape)
                                .background(scoreColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            0f to scoreColor,
                                            animatedScore to scoreColor,
                                            animatedScore to colorScheme.outline.copy(alpha = 0.20f),
                                            1f to colorScheme.outline.copy(alpha = 0.20f)
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = score.coerceIn(0, 100).toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = scoreColor
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OverviewPill(
                            label = stringResource(R.string.security_risk_cards_title),
                            value = riskCount.toString(),
                            color = if (riskCount == 0) Color(0xFF22C55E) else colorScheme.error
                        )
                        OverviewPill(
                            label = stringResource(R.string.security_overview),
                            value = if (score >= 80) "OK" else "!",
                            color = scoreColor
                        )
                    }

                    if (isAnalyzing) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.security_analysis_in_progress_short),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                                color = scoreColor,
                                trackColor = colorScheme.outline.copy(alpha = 0.18f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeSelectorCard(
    scopes: List<SecurityAnalysisScopeOption>,
    selectedScopeKey: String,
    onSelectScope: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.security_analysis_scope_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                scopes.forEach { scope ->
                    ScopeChip(
                        text = "${scopeDisplayName(scope, context)} ${scope.itemCount}",
                        selected = scope.key == selectedScopeKey,
                        onClick = { onSelectScope(scope.key) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    colorScheme.surface.copy(alpha = 0.72f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun SecurityStrengthDistributionCard(
    distribution: PasswordStrengthDistribution
) {
    val colorScheme = MaterialTheme.colorScheme
    val total = distribution.total
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.security_strength_distribution),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                                    .background(
                                        color = color.copy(alpha = 0.72f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
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
                color = color.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
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
private fun OverviewPill(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private data class SecurityIssueGridItem(
    val icon: ImageVector,
    val title: String,
    val count: Int,
    val subtitle: String,
    val issueType: SecurityIssueType
)

@Composable
private fun SecurityIssueGrid(
    items: List<SecurityIssueGridItem>,
    onSelectIssue: (SecurityIssueType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.security_risk_cards_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(180 + rowIndex * 80)) + slideInVertically(tween(220 + rowIndex * 80)) { it / 5 }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { item ->
                        SecurityIssueTile(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectIssue(item.issueType) }
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityIssueTile(
    item: SecurityIssueGridItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = if (item.count == 0) Color(0xFF22C55E) else colorScheme.error
    Card(
        modifier = modifier
            .heightIn(min = 168.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant.copy(alpha = 0.20f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = accent.copy(alpha = 0.10f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(72.dp)
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = item.count.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    BackHandler(onBack = onNavigateBack)

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

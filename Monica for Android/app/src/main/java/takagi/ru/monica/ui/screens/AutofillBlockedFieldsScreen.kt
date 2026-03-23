package takagi.ru.monica.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.AutofillPreferences

private const val BLOCKED_FIELDS_SHARE_DIR = "temp_share"
private const val BLOCKED_FIELDS_SHARE_PREFIX = "monica_blocked_fields_"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutofillBlockedFieldsScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AutofillPreferences(context) }
    val records by preferences.blockedFieldSignatureRecords.collectAsState(initial = emptyList())
    val sortedRecords = remember(records) { records.sortedByDescending { it.blockedAt } }
    val formatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    val appLabels = remember(context, sortedRecords) {
        sortedRecords
            .mapNotNull { record ->
                val packageName = record.packageName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                packageName to resolveBlockedFieldAppLabel(context, packageName)
            }
            .toMap()
    }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.autofill_blocked_fields_dialog_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.autofill_settings_back),
                        )
                    }
                },
                actions = {
                    if (sortedRecords.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val shareIntent = createBlockedFieldsShareIntent(
                                            context = context,
                                            records = sortedRecords,
                                            appLabels = appLabels,
                                        )
                                        context.startActivity(
                                            Intent.createChooser(
                                                shareIntent,
                                                context.getString(R.string.autofill_blocked_fields_share_title),
                                            ),
                                        )
                                    }.onFailure { error ->
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.autofill_blocked_fields_share_failed,
                                                error.message ?: error.javaClass.simpleName,
                                            ),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.autofill_blocked_fields_share),
                            )
                        }
                        TextButton(onClick = { showClearAllDialog = true }) {
                            Text(text = stringResource(R.string.autofill_blocked_fields_clear_all))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DoNotDisturb,
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.autofill_blocked_fields_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.autofill_blocked_fields_manage_desc,
                                        sortedRecords.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.autofill_blocked_fields_dialog_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                        )
                    }
                }
            }

            if (sortedRecords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.autofill_blocked_fields_empty),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = sortedRecords,
                    key = { it.signatureKey },
                ) { record ->
                    val appLabel = record.packageName?.let(appLabels::get)
                    BlockedFieldSignatureCard(
                        record = record,
                        appLabel = appLabel,
                        formatter = formatter,
                        onRemove = {
                            scope.launch {
                                preferences.removeBlockedFieldSignature(record.signatureKey)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(text = stringResource(R.string.autofill_blocked_fields_clear_all)) },
            text = { Text(text = stringResource(R.string.autofill_blocked_fields_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllDialog = false
                        scope.launch { preferences.clearBlockedFieldSignatures() }
                    },
                ) {
                    Text(text = stringResource(R.string.autofill_blocked_fields_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(text = stringResource(R.string.autofill_blacklist_dialog_done))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockedFieldSignatureCard(
    record: AutofillPreferences.BlockedFieldSignatureRecord,
    appLabel: String?,
    formatter: DateFormat,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        imageVector = if (!record.webDomain.isNullOrBlank()) {
                            Icons.Outlined.Language
                        } else {
                            Icons.Outlined.Apps
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = record.primaryBlockedFieldTitle(appLabel),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.autofill_blocked_fields_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                TextButton(onClick = onRemove) {
                    Text(text = stringResource(R.string.autofill_blocked_fields_remove))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                appLabel?.let { label ->
                    BlockedFieldSourceCard(
                        icon = Icons.Outlined.Apps,
                        title = label,
                        subtitle = record.packageName,
                    )
                } ?: record.packageName?.let { packageName ->
                    BlockedFieldSourceCard(
                        icon = Icons.Outlined.Apps,
                        title = packageName,
                        subtitle = stringResource(R.string.autofill_blocked_fields_package_only),
                    )
                }

                record.webDomain?.let { domain ->
                    BlockedFieldSourceCard(
                        icon = Icons.Outlined.Language,
                        title = domain,
                        subtitle = stringResource(R.string.autofill_blocked_fields_domain_only),
                    )
                }
            }

            if (record.hints.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    record.hints.forEach { hint ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = hint,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(
                    R.string.autofill_blocked_fields_time,
                    formatter.format(Date(record.blockedAt)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlockedFieldSourceCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun resolveBlockedFieldAppLabel(
    context: android.content.Context,
    packageName: String?,
): String? {
    val normalized = packageName?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val appInfo = context.packageManager.getApplicationInfo(normalized, 0)
        context.packageManager.getApplicationLabel(appInfo).toString().trim().ifBlank { null }
    }.getOrNull()
}

private fun AutofillPreferences.BlockedFieldSignatureRecord.primaryBlockedFieldTitle(
    appLabel: String?,
): String {
    return webDomain
        ?.takeIf { it.isNotBlank() }
        ?: appLabel
        ?: packageName
        ?: signatureKey.take(12)
}

private suspend fun createBlockedFieldsShareIntent(
    context: Context,
    records: List<AutofillPreferences.BlockedFieldSignatureRecord>,
    appLabels: Map<String, String?>,
): Intent = withContext(Dispatchers.IO) {
    val shareDir = File(context.cacheDir, BLOCKED_FIELDS_SHARE_DIR).apply {
        if (!exists()) {
            mkdirs()
        }
    }
    cleanupOldBlockedFieldExports(shareDir)

    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    val fileName = "$BLOCKED_FIELDS_SHARE_PREFIX${formatter.format(Date())}.txt"
    val file = File(shareDir, fileName)
    file.writeText(buildBlockedFieldsShareText(records, appLabels))

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.autofill_blocked_fields_share_subject))
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.autofill_blocked_fields_share_file_hint))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun cleanupOldBlockedFieldExports(dir: File) {
    val exported = dir.listFiles { file ->
        file.isFile && file.name.startsWith(BLOCKED_FIELDS_SHARE_PREFIX) && file.name.endsWith(".txt")
    } ?: return
    if (exported.size <= 10) return
    exported.sortedByDescending { it.lastModified() }
        .drop(10)
        .forEach { stale ->
            runCatching { stale.delete() }
        }
}

private fun buildBlockedFieldsShareText(
    records: List<AutofillPreferences.BlockedFieldSignatureRecord>,
    appLabels: Map<String, String?>,
): String {
    val exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
    val lineBreak = System.lineSeparator()
    val builder = StringBuilder()

    builder.appendLine("Monica Autofill Blocked Fields Export")
    builder.appendLine("exported_at=$exportedAt")
    builder.appendLine("record_count=${records.size}")
    builder.appendLine()

    records.forEachIndexed { index, record ->
        builder.appendLine("[record_${index + 1}]")
        builder.appendLine("signature_key=${record.signatureKey}")
        builder.appendLine("blocked_at=${record.blockedAt}")
        record.packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
            builder.appendLine("package_name=$packageName")
            appLabels[packageName]
                ?.takeIf { it.isNotBlank() }
                ?.let { label ->
                    builder.appendLine("app_label=$label")
                }
        }
        record.webDomain?.takeIf { it.isNotBlank() }?.let { domain ->
            builder.appendLine("web_domain=$domain")
        }
        builder.appendLine("hints=${record.hints.joinToString(",").ifBlank { "-" }}")
        builder.append(lineBreak)
    }

    return builder.toString().trimEnd()
}

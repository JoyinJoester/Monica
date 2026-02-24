package takagi.ru.monica.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.autofill.AutofillPickerActivityV2
import takagi.ru.monica.autofill.core.AutofillLogger
import takagi.ru.monica.bitwarden.service.BitwardenDiagLogger
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.viewmodel.SettingsViewModel

/**
 * 开发者设置页面
 * 包含日志查看、清除以及开发者专用功能
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DeveloperSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var showDebugLogsDialog by remember { mutableStateOf(false) }
    var disablePasswordVerification by remember { mutableStateOf(settings.disablePasswordVerification) }

    // 准备共享元素 Modifier
    val sharedTransitionScope = takagi.ru.monica.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = takagi.ru.monica.ui.LocalAnimatedVisibilityScope.current

    var sharedModifier: Modifier = Modifier
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            sharedModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "developer_settings_card"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
            )
        }
    }

    Scaffold(
        modifier = sharedModifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.developer_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.developer_settings_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // 日志调试区域
            SettingsSection(
                title = stringResource(R.string.developer_log_debugging)
            ) {
                SettingsItem(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.developer_view_logs),
                    subtitle = stringResource(R.string.developer_view_logs_desc),
                    onClick = { showDebugLogsDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = stringResource(R.string.developer_clear_log_buffer),
                    subtitle = stringResource(R.string.developer_clear_log_buffer_desc),
                    onClick = {
                        scope.launch {
                            val clearResult = DeveloperLogDebugHelper.clearLogs()
                            val message = if (clearResult.logcatCleared) {
                                context.getString(R.string.developer_log_buffer_cleared)
                            } else {
                                context.getString(
                                    R.string.developer_clear_failed,
                                    clearResult.reason ?: "unknown"
                                )
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                SettingsItem(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.developer_share_logs),
                    subtitle = stringResource(R.string.developer_share_logs_desc),
                    onClick = {
                        scope.launch {
                            try {
                                val snapshot = DeveloperLogDebugHelper.collectLogs(context)
                                val shareIntent =
                                    DeveloperLogDebugHelper.createShareIntent(context, snapshot.report)
                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        context.getString(R.string.developer_share_title)
                                    )
                                )
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.developer_share_failed,
                                        e.message ?: "unknown"
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }

            // 开发者功能
            SettingsSection(
                title = stringResource(R.string.developer_functions)
            ) {
                SettingsItemWithSwitch(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.developer_disable_password_verification),
                    subtitle = stringResource(R.string.developer_disable_password_verification_desc),
                    checked = disablePasswordVerification,
                    onCheckedChange = { enabled ->
                        android.util.Log.d("DeveloperSettings", "Toggling password verification: $enabled")
                        disablePasswordVerification = enabled
                        scope.launch {
                            viewModel.updateDisablePasswordVerification(enabled)
                            android.util.Log.d(
                                "DeveloperSettings",
                                "Password verification setting updated to: $enabled"
                            )
                        }
                    }
                )
            }

            // 自动填充调试区域
            SettingsSection(
                title = stringResource(R.string.developer_autofill_debug)
            ) {
                SettingsItem(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.developer_launch_autofill_v2_test),
                    subtitle = stringResource(R.string.developer_launch_autofill_v2_desc),
                    onClick = {
                        try {
                            val testIntent = AutofillPickerActivityV2.getTestIntent(context)
                            context.startActivity(testIntent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.developer_launch_failed, e.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                // 显示会话状态
                val sessionUnlocked by SessionManager.isUnlocked.collectAsState()
                val remainingMinutes = SessionManager.getRemainingMinutes()

                SettingsItem(
                    icon = if (sessionUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    title = stringResource(R.string.developer_session_status),
                    subtitle = if (sessionUnlocked) {
                        stringResource(R.string.developer_session_unlocked_remaining, remainingMinutes)
                    } else {
                        stringResource(R.string.developer_session_locked)
                    },
                    onClick = {
                        // 手动锁定/解锁会话（用于测试）
                        if (sessionUnlocked) {
                            SessionManager.markLocked()
                            Toast.makeText(
                                context,
                                context.getString(R.string.developer_session_locked_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            SessionManager.markUnlocked()
                            Toast.makeText(
                                context,
                                context.getString(R.string.developer_session_unlocked_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 警告提示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.developer_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 显示日志对话框
    if (showDebugLogsDialog) {
        DebugLogsDialog(
            onDismiss = { showDebugLogsDialog = false }
        )
    }
}

/**
 * 调试日志对话框 - 分级显示关键日志
 */
@Composable
fun DebugLogsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember {
        mutableStateOf(
            DeveloperLogSnapshot(
                report = "",
                lines = emptyList()
            )
        )
    }
    var isLoading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(DeveloperLogFilter.ALL) }

    suspend fun refreshLogs() {
        isLoading = true
        snapshot = try {
            DeveloperLogDebugHelper.collectLogs(context)
        } catch (e: Exception) {
            DeveloperLogSnapshot(
                report = context.getString(R.string.developer_load_failed, e.message ?: "unknown"),
                lines = emptyList()
            )
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    val allLines = snapshot.lines
    val errorCount = allLines.count { it.level == DeveloperLogLevel.ERROR }
    val warningCount = allLines.count { it.level == DeveloperLogLevel.WARN }
    val filteredLines = remember(allLines, filter) {
        when (filter) {
            DeveloperLogFilter.ALL -> allLines
            DeveloperLogFilter.ERROR -> allLines.filter { it.level == DeveloperLogLevel.ERROR }
            DeveloperLogFilter.WARNING -> allLines.filter { it.level == DeveloperLogLevel.WARN }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.developer_system_logs),
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { scope.launch { refreshLogs() } },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.developer_refresh),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column {
                if (!isLoading && allLines.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filter == DeveloperLogFilter.ALL,
                            onClick = { filter = DeveloperLogFilter.ALL },
                            label = { Text("${stringResource(R.string.developer_filter_all)} (${allLines.size})") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        FilterChip(
                            selected = filter == DeveloperLogFilter.ERROR,
                            onClick = { filter = DeveloperLogFilter.ERROR },
                            label = { Text("${stringResource(R.string.developer_filter_errors)} ($errorCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                        FilterChip(
                            selected = filter == DeveloperLogFilter.WARNING,
                            onClick = { filter = DeveloperLogFilter.WARNING },
                            label = { Text("${stringResource(R.string.developer_filter_warnings)} ($warningCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        filteredLines.isEmpty() -> {
                            Text(
                                text = if (snapshot.report.isNotBlank()) {
                                    snapshot.report
                                } else {
                                    stringResource(R.string.developer_no_logs)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(filteredLines) { _, line ->
                                    DeveloperLogLineItem(line)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.developer_close))
            }
        }
    )
}

@Composable
private fun DeveloperLogLineItem(line: DeveloperLogLine) {
    val textColor = when (line.level) {
        DeveloperLogLevel.ERROR -> MaterialTheme.colorScheme.error
        DeveloperLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        DeveloperLogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        DeveloperLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        DeveloperLogLevel.VERBOSE -> MaterialTheme.colorScheme.onSurfaceVariant
        DeveloperLogLevel.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = line.text,
        color = textColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
}

private enum class DeveloperLogLevel {
    ERROR,
    WARN,
    INFO,
    DEBUG,
    VERBOSE,
    OTHER
}

private enum class DeveloperLogFilter {
    ALL,
    ERROR,
    WARNING
}

private data class DeveloperLogLine(
    val text: String,
    val level: DeveloperLogLevel
)

private data class DeveloperLogSnapshot(
    val report: String,
    val lines: List<DeveloperLogLine>
)

private data class ClearLogsResult(
    val logcatCleared: Boolean,
    val reason: String?
)

private object DeveloperLogDebugHelper {
    private const val LOG_LINE_LIMIT = 1200
    private const val SHARE_DIR = "temp_photos"
    private const val SHARE_PREFIX = "monica_logs_"
    private val AUTOFILL_LOG_TAGS = arrayOf(
        "MonicaAutofill:V",
        "AutofillPicker:V",
        "AutofillPickerV2:V",
        "EnhancedParser:V",
        "EnhancedFieldParser:V",
        "SmartFieldDetector:V",
        "*:S"
    )
    private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    suspend fun collectLogs(context: Context): DeveloperLogSnapshot = withContext(Dispatchers.IO) {
        runCatching { AutofillLogger.initialize(context.applicationContext) }
        runCatching { BitwardenDiagLogger.initialize(context.applicationContext) }
        val autofillTagLogs = readAutofillTagLogs()
        val appProcessLogs = readLogcat(
            arrayOf(
                "logcat",
                "-d",
                "-v",
                "threadtime",
                "--pid",
                android.os.Process.myPid().toString(),
                "-t",
                "400",
                "*:V"
            )
        )
        val crashLogs = readLogcat(
            arrayOf(
                "logcat",
                "-d",
                "-v",
                "threadtime",
                "-t",
                "300",
                "AndroidRuntime:E",
                "System.err:W",
                "libc:E",
                "*:S"
            )
        )
        val selectedLogs = buildString {
            if (autofillTagLogs.isNotBlank()) {
                appendLine("---- autofill-tags ----")
                appendLine(autofillTagLogs.trim())
            }
            if (appProcessLogs.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine("---- app-process ----")
                appendLine(appProcessLogs.trim())
            }
            if (crashLogs.isNotBlank()) {
                if (isNotBlank()) appendLine()
                appendLine("---- crash/system ----")
                appendLine(crashLogs.trim())
            }
        }.trim()

        val autofillLogs = runCatching {
            AutofillLogger.exportLogs(300)
        }.getOrElse {
            "AutofillLogger unavailable: ${it.message}"
        }
        val persistedAutofillLogs = runCatching {
            AutofillLogger.exportPersistedLogs(1200)
        }.getOrElse {
            "Autofill persisted logs unavailable: ${it.message}"
        }
        val persistedBitwardenLogs = runCatching {
            BitwardenDiagLogger.exportPersistedLogs(2000)
        }.getOrElse {
            "Bitwarden persisted logs unavailable: ${it.message}"
        }

        val report = buildString {
            appendLine("=== Monica Developer Log Report ===")
            appendLine("exportedAt=${timeFormatter.format(Date())}")
            appendLine("package=${context.packageName}")
            appendLine("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("=== System Logcat ===")
            if (selectedLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(selectedLogs.trim())
            }
            appendLine()
            appendLine("=== Autofill Structured Logs ===")
            appendLine(autofillLogs.trim())
            appendLine()
            appendLine("=== Autofill Persisted Logs ===")
            if (persistedAutofillLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(persistedAutofillLogs.trim())
            }
            appendLine()
            appendLine("=== Bitwarden Persisted Logs ===")
            if (persistedBitwardenLogs.isBlank()) {
                appendLine(context.getString(R.string.developer_no_logs))
            } else {
                appendLine(persistedBitwardenLogs.trim())
            }
        }

        val parsedSystem = parseLines(selectedLogs)
        val parsedPersisted = parseLines(persistedAutofillLogs)
        val parsedBitwarden = parseLines(persistedBitwardenLogs)
        val parsed = when {
            parsedSystem.isNotEmpty() -> parsedSystem
            parsedBitwarden.isNotEmpty() -> parsedBitwarden
            parsedPersisted.isNotEmpty() -> parsedPersisted
            else -> parseLines(autofillLogs)
        }
        DeveloperLogSnapshot(report = report, lines = parsed)
    }

    suspend fun clearLogs(): ClearLogsResult = withContext(Dispatchers.IO) {
        runCatching {
            AutofillLogger.clear()
        }
        runCatching {
            BitwardenDiagLogger.clear()
        }

        val process = runCatching {
            ProcessBuilder("logcat", "-c")
                .redirectErrorStream(true)
                .start()
        }.getOrElse { error ->
            return@withContext ClearLogsResult(
                logcatCleared = false,
                reason = error.message
            )
        }

        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
        if (exitCode == 0) {
            ClearLogsResult(logcatCleared = true, reason = null)
        } else {
            ClearLogsResult(
                logcatCleared = false,
                reason = if (output.isNotBlank()) output else "exit=$exitCode"
            )
        }
    }

    suspend fun createShareIntent(context: Context, report: String): Intent = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, SHARE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        cleanupOldFiles(shareDir)

        val fileName = "${SHARE_PREFIX}${fileFormatter.format(Date())}.txt"
        val file = File(shareDir, fileName)
        file.writeText(report)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.developer_share_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.developer_share_file_hint))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun cleanupOldFiles(dir: File) {
        val exported = dir.listFiles { file ->
            file.isFile && file.name.startsWith(SHARE_PREFIX) && file.name.endsWith(".txt")
        } ?: return
        if (exported.size <= 10) return
        exported.sortedByDescending { it.lastModified() }
            .drop(10)
            .forEach { stale ->
                runCatching { stale.delete() }
            }
    }

    private fun readLogcat(command: Array<String>): String {
        val process = runCatching {
            ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return ""

        val output = runCatching {
            process.inputStream.bufferedReader().use { it.readText() }
        }.getOrDefault("")

        runCatching { process.waitFor() }
        return output.trim()
    }

    private fun readAutofillTagLogs(): String {
        val command = mutableListOf(
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "-t",
            LOG_LINE_LIMIT.toString()
        ).apply {
            addAll(AUTOFILL_LOG_TAGS)
        }.toTypedArray()
        return readLogcat(command)
    }

    private fun parseLines(raw: String): List<DeveloperLogLine> {
        if (raw.isBlank()) return emptyList()
        return raw
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .map { line ->
                DeveloperLogLine(
                    text = line,
                    level = detectLevel(line)
                )
            }
            .toList()
    }

    private fun detectLevel(line: String): DeveloperLogLevel {
        if (line.contains("FATAL EXCEPTION", ignoreCase = true)) return DeveloperLogLevel.ERROR
        if (line.contains("[ERROR]")) return DeveloperLogLevel.ERROR
        if (line.contains("[WARN]")) return DeveloperLogLevel.WARN
        if (line.contains("[INFO]")) return DeveloperLogLevel.INFO
        if (line.contains("[DEBUG]")) return DeveloperLogLevel.DEBUG

        val match = Regex("""\s([VDIWEAF])\s[^:]+:\s""").find(line)
        val levelChar = match?.groupValues?.getOrNull(1) ?: return DeveloperLogLevel.OTHER
        return when (levelChar) {
            "E", "F", "A" -> DeveloperLogLevel.ERROR
            "W" -> DeveloperLogLevel.WARN
            "I" -> DeveloperLogLevel.INFO
            "D" -> DeveloperLogLevel.DEBUG
            "V" -> DeveloperLogLevel.VERBOSE
            else -> DeveloperLogLevel.OTHER
        }
    }
}


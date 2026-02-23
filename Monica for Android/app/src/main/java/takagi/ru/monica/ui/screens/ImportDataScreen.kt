package takagi.ru.monica.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.util.DataExportImportManager
import takagi.ru.monica.util.FileOperationHelper
import takagi.ru.monica.viewmodel.DataExportImportViewModel

/**
 * 导入类型数据类
 */
private data class ImportTypeInfo(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val fileHint: String
)

private fun isPasswordDecryptError(errorMessage: String): Boolean {
    val normalized = errorMessage.lowercase()
    return normalized.contains("wrong password") ||
        normalized.contains("password incorrect") ||
        normalized.contains("decrypt") ||
        normalized.contains("invalid credentials") ||
        normalized.contains("密码错误") ||
        normalized.contains("解密失败")
}

private fun isPasswordRequiredError(errorMessage: String): Boolean {
    val normalized = errorMessage.lowercase()
    return normalized.contains("password required") ||
        normalized.contains("password needed") ||
        normalized.contains("need password")
}

/**
 * 导入类型选项卡片
 */
@Composable
private fun ImportTypeCard(
    info: ImportTypeInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "containerColor"
    )
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        label = "borderColor"
    )
    val elevation by animateDpAsState(
        if (selected) 4.dp else 0.dp,
        label = "elevation"
    )
    
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    info.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                if (selected) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                info.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 数据导入界面 - M3 Expressive 设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDataScreen(
    onNavigateBack: () -> Unit,
    onImport: suspend (Uri) -> Result<Int>,  // 普通数据导入
    onImportAegis: suspend (Uri) -> Result<Int>,  // Aegis JSON导入
    onImportEncryptedAegis: suspend (Uri, String) -> Result<Int>,  // 加密的Aegis JSON导入
    onImportStratum: suspend (Uri, String?) -> Result<Int> = { _, _ -> Result.failure(Exception("Not implemented")) }, // Stratum 导入
    onImportSteamMaFile: suspend (Uri) -> Result<Int>,  // Steam maFile导入
    onBeginSteamLoginImport: suspend (String, String, String?) -> DataExportImportViewModel.SteamLoginImportState = { _, _, _ ->
        DataExportImportViewModel.SteamLoginImportState.Failure("Not implemented")
    }, // Steam 登录导入（开始）
    onSubmitSteamLoginImportCode: suspend (String, String, Int, String?) -> DataExportImportViewModel.SteamLoginImportState = { _, _, _, _ ->
        DataExportImportViewModel.SteamLoginImportState.Failure("Not implemented")
    }, // Steam 登录导入（提交验证码）
    onClearSteamLoginImportSession: (String) -> Unit = {}, // 清理 Steam 登录会话
    onImportZip: suspend (Uri, String?) -> Result<Int>,  // Monica ZIP导入
    onImportKdbx: suspend (Uri, String) -> Result<Int> = { _, _ -> Result.failure(Exception("Not implemented")) },  // KDBX导入
    onImportKeePassCsv: suspend (Uri) -> Result<Int> = { _ -> Result.failure(Exception("Not implemented")) },  // KeePass CSV导入
    onImportBitwardenCsv: suspend (Uri) -> Result<Int> = { _ -> Result.failure(Exception("Not implemented")) }  // Bitwarden CSV导入
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importType by remember { mutableStateOf("monica_zip") } // 默认选择 ZIP 备份
    var csvImportType by remember { mutableStateOf("normal") } // CSV子类型
    var showPasswordDialog by remember { mutableStateOf(false) }
    var aegisPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    
    // KDBX 导入密码
    var showKdbxPasswordDialog by remember { mutableStateOf(false) }
    var kdbxPassword by remember { mutableStateOf("") }
    var kdbxPasswordVisible by remember { mutableStateOf(false) }

    var steamImportMode by remember { mutableStateOf("mafile") } // mafile / login
    var steamDeviceIdInput by remember { mutableStateOf("") }
    var steamGuardJsonInput by remember { mutableStateOf("") }
    var steamCustomNameInput by remember { mutableStateOf("") }
    var steamLoginUserNameInput by remember { mutableStateOf("") }
    var steamLoginPasswordInput by remember { mutableStateOf("") }
    var steamLoginPasswordVisible by remember { mutableStateOf(false) }
    var steamLoginChallengeCodeInput by remember { mutableStateOf("") }
    var steamLoginPendingSessionId by remember { mutableStateOf<String?>(null) }
    var steamLoginChallengeType by remember { mutableStateOf(0) }
    var steamLoginChallengeHint by remember { mutableStateOf("") }
    
    // 导入类型信息列表
    val importTypes = listOf(
        ImportTypeInfo(
            key = "monica_zip",
            icon = Icons.Default.Archive,
            title = stringResource(R.string.import_type_monica_backup_title),
            description = stringResource(R.string.import_type_monica_backup_desc),
            fileHint = stringResource(R.string.import_type_monica_backup_file_hint)
        ),
        ImportTypeInfo(
            key = "kdbx",
            icon = Icons.Default.Key,
            title = stringResource(R.string.import_type_keepass_format_title),
            description = stringResource(R.string.import_type_keepass_format_desc),
            fileHint = stringResource(R.string.import_type_keepass_format_file_hint)
        ),
        ImportTypeInfo(
            key = "csv_group",
            icon = Icons.Default.TableChart,
            title = stringResource(R.string.import_type_csv_data_title),
            description = stringResource(R.string.import_type_csv_data_desc),
            fileHint = stringResource(R.string.import_type_csv_data_file_hint)
        ),
        ImportTypeInfo(
            key = "aegis",
            icon = Icons.Default.Security,
            title = stringResource(R.string.import_type_aegis_title),
            description = stringResource(R.string.import_type_aegis_desc),
            fileHint = stringResource(R.string.import_type_aegis_file_hint)
        ),
        ImportTypeInfo(
            key = "stratum",
            icon = Icons.Default.VerifiedUser,
            title = stringResource(R.string.import_type_stratum_title),
            description = stringResource(R.string.import_type_stratum_desc),
            fileHint = stringResource(R.string.import_type_stratum_file_hint)
        ),
        ImportTypeInfo(
            key = "steam",
            icon = Icons.Default.SportsEsports,
            title = stringResource(R.string.import_type_steam_title),
            description = stringResource(R.string.import_type_steam_desc),
            fileHint = stringResource(R.string.import_type_steam_file_hint)
        )
    )

    val csvImportTypes = listOf(
        ImportTypeInfo(
            key = "normal",
            icon = Icons.Default.TableChart,
            title = stringResource(R.string.import_type_csv_monica_title),
            description = stringResource(R.string.import_type_csv_monica_desc),
            fileHint = stringResource(R.string.import_type_csv_monica_file_hint)
        ),
        ImportTypeInfo(
            key = "keepass_csv",
            icon = Icons.Default.Description,
            title = stringResource(R.string.import_type_csv_keepass_title),
            description = stringResource(R.string.import_type_csv_keepass_desc),
            fileHint = stringResource(R.string.import_type_csv_keepass_file_hint)
        ),
        ImportTypeInfo(
            key = "bitwarden_csv",
            icon = Icons.Default.Lock,
            title = stringResource(R.string.import_type_csv_bitwarden_title),
            description = stringResource(R.string.import_type_csv_bitwarden_desc),
            fileHint = stringResource(R.string.import_type_csv_bitwarden_file_hint)
        )
    )

    val effectiveImportType = if (importType == "csv_group") csvImportType else importType
    val isSteamLoginMode = effectiveImportType == "steam" && steamImportMode == "login"
    
    val currentTypeInfo = if (importType == "csv_group") {
        csvImportTypes.find { it.key == csvImportType } ?: csvImportTypes[0]
    } else {
        importTypes.find { it.key == importType } ?: importTypes[0]
    }
    
    // 设置文件操作回调
    LaunchedEffect(Unit) {
        FileOperationHelper.setCallback(object : FileOperationHelper.FileOperationCallback {
            override fun onExportFileSelected(uri: Uri?) {
                // 导入界面不需要处理导出文件选择
            }
            
            override fun onImportFileSelected(uri: Uri?) {
                uri?.let { safeUri ->
                    scope.launch {
                        try {
                            // 获取持久化权限
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    safeUri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (e: SecurityException) {
                                // 某些URI可能不支持持久化权限,忽略这个错误
                                android.util.Log.w("ImportDataScreen", "无法获取持久化权限", e)
                            }
                            
                            selectedFileUri = safeUri
                            // 尝试获取文件名
                            selectedFileName = try {
                                safeUri.lastPathSegment?.substringAfterLast('/')
                                    ?: context.getString(R.string.import_data_file_selected)
                            } catch (e: Exception) {
                                context.getString(R.string.import_data_file_selected)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ImportDataScreen", "文件选择异常", e)
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    R.string.import_data_file_select_failed,
                                    e.message ?: context.getString(R.string.import_data_unknown_error)
                                )
                            )
                        }
                    }
                }
            }
        })
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_data_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 导入按钮
                    Button(
                        onClick = {
                            if (isSteamLoginMode) {
                                scope.launch {
                                    isImporting = true
                                    try {
                                        val customName = steamCustomNameInput.trim().takeIf { it.isNotBlank() }
                                        val loginState = if (steamLoginPendingSessionId.isNullOrBlank()) {
                                            onBeginSteamLoginImport(
                                                steamLoginUserNameInput.trim(),
                                                steamLoginPasswordInput,
                                                customName
                                            )
                                        } else {
                                            onSubmitSteamLoginImportCode(
                                                steamLoginPendingSessionId.orEmpty(),
                                                steamLoginChallengeCodeInput.trim(),
                                                steamLoginChallengeType,
                                                customName
                                            )
                                        }

                                        when (loginState) {
                                            is DataExportImportViewModel.SteamLoginImportState.ChallengeRequired -> {
                                                steamLoginPendingSessionId = loginState.pendingSessionId
                                                steamLoginChallengeType = loginState.challenges.firstOrNull()?.confirmationType ?: 0
                                                steamLoginChallengeHint = loginState.challenges.firstOrNull()?.associatedMessage.orEmpty()
                                                // 每次进入挑战阶段都清空输入框，避免二次提交用到旧验证码
                                                steamLoginChallengeCodeInput = ""
                                                snackbarHostState.showSnackbar(
                                                    loginState.message
                                                        ?: context.getString(R.string.import_type_steam_login_challenge_required)
                                                )
                                            }

                                            is DataExportImportViewModel.SteamLoginImportState.Imported -> {
                                                steamLoginPendingSessionId = null
                                                steamLoginChallengeType = 0
                                                steamLoginChallengeCodeInput = ""
                                                steamLoginChallengeHint = ""
                                                handleImportResult(
                                                    Result.success(loginState.count),
                                                    context,
                                                    snackbarHostState,
                                                    effectiveImportType,
                                                    onNavigateBack
                                                )
                                            }

                                            is DataExportImportViewModel.SteamLoginImportState.Failure -> {
                                                snackbarHostState.showSnackbar(loginState.message)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ImportDataScreen", "导入异常", e)
                                        snackbarHostState.showSnackbar(
                                            context.getString(
                                                R.string.import_data_error_exception,
                                                e.message ?: context.getString(R.string.import_data_unknown_error)
                                            )
                                        )
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            } else {
                                selectedFileUri?.let { uri ->
                                    scope.launch {
                                        isImporting = true
                                        try {
                                            when (effectiveImportType) {
                                                "monica_zip" -> {
                                                    val result = onImportZip(uri, null)
                                                    result.onSuccess { count ->
                                                        handleImportResult(Result.success(count), context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                    }.onFailure { error ->
                                                        if (error is takagi.ru.monica.utils.WebDavHelper.PasswordRequiredException) {
                                                            isImporting = false
                                                            showPasswordDialog = true
                                                            passwordError = null
                                                            aegisPassword = ""
                                                        } else {
                                                            handleImportResult(Result.failure(error), context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                        }
                                                    }
                                                }
                                                "aegis" -> {
                                                    // Aegis导入类型，先检查是否为加密文件
                                                    val isEncryptedResult = DataExportImportManager(context).isEncryptedAegisFile(uri)
                                                    val isEncrypted = isEncryptedResult.getOrDefault(false)
                                                    if (isEncrypted) {
                                                        // 是加密文件，显示密码输入对话框
                                                        isImporting = false
                                                        showPasswordDialog = true
                                                        passwordError = null
                                                        aegisPassword = ""
                                                        return@launch
                                                    } else {
                                                        // 不是加密文件，直接导入
                                                        val result = onImportAegis(uri)
                                                        handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                    }
                                                }
                                                "stratum" -> {
                                                    val result = onImportStratum(uri, null)
                                                    result.onSuccess { count ->
                                                        handleImportResult(Result.success(count), context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                    }.onFailure { error ->
                                                        val errorMsg = error.message ?: ""
                                                        if (isPasswordRequiredError(errorMsg)) {
                                                            isImporting = false
                                                            showPasswordDialog = true
                                                            passwordError = null
                                                            aegisPassword = ""
                                                        } else {
                                                            handleImportResult(Result.failure(error), context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                        }
                                                    }
                                                }
                                                "steam" -> {
                                                    // Steam maFile导入
                                                    val result = onImportSteamMaFile(uri)
                                                    handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                }
                                                "kdbx" -> {
                                                    // KDBX 导入需要密码
                                                    isImporting = false
                                                    showKdbxPasswordDialog = true
                                                    kdbxPassword = ""
                                                }
                                                "keepass_csv" -> {
                                                    val result = onImportKeePassCsv(uri)
                                                    handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                }
                                                "bitwarden_csv" -> {
                                                    val result = onImportBitwardenCsv(uri)
                                                    handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                }
                                                else -> {
                                                    // 普通CSV导入
                                                    val result = onImport(uri)
                                                    handleImportResult(result, context, snackbarHostState, effectiveImportType, onNavigateBack)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("ImportDataScreen", "导入异常", e)
                                            snackbarHostState.showSnackbar(
                                                context.getString(
                                                    R.string.import_data_error_exception,
                                                    e.message ?: context.getString(R.string.import_data_unknown_error)
                                                )
                                            )
                                        } finally {
                                            isImporting = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = if (isSteamLoginMode) {
                            if (steamLoginPendingSessionId.isNullOrBlank()) {
                                steamLoginUserNameInput.isNotBlank() &&
                                    steamLoginPasswordInput.isNotBlank() &&
                                    !isImporting
                            } else {
                                steamLoginChallengeCodeInput.isNotBlank() && !isImporting
                            }
                        } else {
                            selectedFileUri != null && !isImporting
                        },
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.importing), style = MaterialTheme.typography.titleMedium)
                        } else {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isSteamLoginMode && !steamLoginPendingSessionId.isNullOrBlank())
                                    stringResource(R.string.import_type_steam_login_submit_code)
                                else
                                    stringResource(R.string.start_import),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    
                    // 说明文字
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.import_data_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 选择导入类型标题
            Text(
                stringResource(R.string.import_data_select_type),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // 导入类型卡片列表 - 垂直排列，适配各种屏幕尺寸
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                importTypes.forEach { typeInfo ->
                    ImportTypeCard(
                        info = typeInfo,
                        selected = importType == typeInfo.key,
                        onClick = { 
                            if (steamLoginPendingSessionId != null) {
                                onClearSteamLoginImportSession(steamLoginPendingSessionId.orEmpty())
                            }
                            importType = typeInfo.key
                            // 切换类型时清除已选文件
                            selectedFileUri = null
                            selectedFileName = null
                            steamLoginPendingSessionId = null
                            steamLoginChallengeType = 0
                            steamLoginChallengeCodeInput = ""
                            steamLoginChallengeHint = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (importType == "csv_group") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.import_type_csv_source_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        csvImportTypes.forEach { csvTypeInfo ->
                            ImportTypeCard(
                                info = csvTypeInfo,
                                selected = csvImportType == csvTypeInfo.key,
                                onClick = {
                                    csvImportType = csvTypeInfo.key
                                    selectedFileUri = null
                                    selectedFileName = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (effectiveImportType == "steam") {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.import_type_steam_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = steamImportMode == "mafile",
                                onClick = {
                                    if (steamLoginPendingSessionId != null) {
                                        onClearSteamLoginImportSession(steamLoginPendingSessionId.orEmpty())
                                    }
                                    steamImportMode = "mafile"
                                    steamDeviceIdInput = ""
                                    steamGuardJsonInput = ""
                                    steamCustomNameInput = ""
                                    steamLoginPendingSessionId = null
                                    steamLoginChallengeType = 0
                                    steamLoginChallengeCodeInput = ""
                                    steamLoginChallengeHint = ""
                                },
                                label = { Text(stringResource(R.string.import_type_steam_mode_mafile)) }
                            )
                            FilterChip(
                                selected = steamImportMode == "login",
                                onClick = {
                                    steamImportMode = "login"
                                    selectedFileUri = null
                                    selectedFileName = null
                                    steamDeviceIdInput = ""
                                    steamGuardJsonInput = ""
                                    steamLoginChallengeCodeInput = ""
                                    steamLoginChallengeHint = ""
                                },
                                label = { Text(stringResource(R.string.import_type_steam_mode_login)) }
                            )
                        }

                        if (steamImportMode == "login") {
                            Text(
                                stringResource(R.string.import_type_steam_login_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = steamLoginUserNameInput,
                                onValueChange = { steamLoginUserNameInput = it },
                                label = { Text(stringResource(R.string.import_type_steam_login_username_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = steamLoginPendingSessionId.isNullOrBlank()
                            )
                            OutlinedTextField(
                                value = steamLoginPasswordInput,
                                onValueChange = { steamLoginPasswordInput = it },
                                label = { Text(stringResource(R.string.import_type_steam_login_password_label)) },
                                visualTransformation = if (steamLoginPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(onClick = { steamLoginPasswordVisible = !steamLoginPasswordVisible }) {
                                        Icon(
                                            imageVector = if (steamLoginPasswordVisible) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                            contentDescription = if (steamLoginPasswordVisible) {
                                                stringResource(R.string.hide_password)
                                            } else {
                                                stringResource(R.string.show_password)
                                            }
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = steamLoginPendingSessionId.isNullOrBlank()
                            )
                            if (!steamLoginPendingSessionId.isNullOrBlank()) {
                                if (steamLoginChallengeHint.isNotBlank()) {
                                    Text(
                                        steamLoginChallengeHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                OutlinedTextField(
                                    value = steamLoginChallengeCodeInput,
                                    onValueChange = { steamLoginChallengeCodeInput = it },
                                    label = { Text(stringResource(R.string.import_type_steam_login_code_label)) },
                                    placeholder = { Text(stringResource(R.string.import_type_steam_login_code_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OutlinedTextField(
                                value = steamCustomNameInput,
                                onValueChange = { steamCustomNameInput = it },
                                label = { Text(stringResource(R.string.import_type_steam_custom_name_label)) },
                                placeholder = { Text(stringResource(R.string.import_type_steam_custom_name_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!isSteamLoginMode) {
                // 文件选择区域
                Text(
                    stringResource(R.string.import_data_select_file),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // 选择文件卡片
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        activity?.let { act ->
                            // 根据导入类型选择不同的文件过滤器
                            when (effectiveImportType) {
                                "monica_zip" -> FileOperationHelper.importFromZip(act)
                                "kdbx" -> FileOperationHelper.importFromKdbx(act)
                                "keepass_csv" -> FileOperationHelper.importFromCsv(act)
                                "bitwarden_csv" -> FileOperationHelper.importFromCsv(act)
                                "aegis" -> FileOperationHelper.importFromJson(act)
                                "stratum" -> FileOperationHelper.importFromStratum(act)
                                "steam" -> FileOperationHelper.importFromMaFile(act)
                                else -> FileOperationHelper.importFromCsv(act)
                            }
                        } ?: run {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        R.string.error_launch_export,
                                        context.getString(R.string.import_data_operation_unavailable)
                                    )
                                )
                            }
                        }
                    },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (selectedFileUri != null)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 文件图标
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (selectedFileUri != null)
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (selectedFileUri != null) Icons.Default.InsertDriveFile else Icons.Default.FileOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selectedFileUri != null)
                                        MaterialTheme.colorScheme.onSecondary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (selectedFileUri != null) {
                                    stringResource(R.string.import_data_file_selected)
                                } else {
                                    stringResource(R.string.import_data_tap_to_select_file)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (selectedFileUri != null)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                selectedFileName ?: currentTypeInfo.fileHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedFileUri != null)
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (selectedFileUri != null)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 底部留白，避免被底部栏遮挡
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    
    // 密码输入对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false
                passwordError = null
            },
            title = {
                val title = when (importType) {
                    "stratum" -> stringResource(R.string.stratum_decrypt_password_title)
                    else -> stringResource(R.string.aegis_decrypt_password_title)
                }
                Text(title)
            },
            text = {
                Column {
                    Text(
                        when (importType) {
                            "stratum" -> stringResource(R.string.stratum_decrypt_password_hint)
                            else -> stringResource(R.string.aegis_decrypt_password_hint)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = aegisPassword,
                        onValueChange = { 
                            aegisPassword = it
                            passwordError = null
                        },
                        label = { Text(stringResource(R.string.password)) },
                        singleLine = true,
                        isError = passwordError != null,
                        supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aegisPassword.isBlank()) {
                            passwordError = context.getString(R.string.import_data_password_cannot_be_empty)
                            return@TextButton
                        }
                        
                        scope.launch {
                            isImporting = true
                            showPasswordDialog = false
                            try {
                                selectedFileUri?.let { uri ->
                                    // 使用加密导入回调
                                    val result = when (importType) {
                                        "monica_zip" -> onImportZip(uri, aegisPassword)
                                        "stratum" -> onImportStratum(uri, aegisPassword)
                                        else -> onImportEncryptedAegis(uri, aegisPassword)
                                    }
                                    
                                    result.onSuccess { count ->
                                        val message = if (importType == "monica_zip") {
                                            context.getString(R.string.import_data_zip_restore_success_count, count)
                                        } else if (importType == "stratum") {
                                            context.getString(R.string.import_data_stratum_import_success_count, count)
                                        } else {
                                            context.getString(R.string.import_data_aegis_import_success_count, count)
                                        }
                                        snackbarHostState.showSnackbar(message)
                                        onNavigateBack()
                                    }.onFailure { error ->
                                        val errorMsg = error.message ?: context.getString(R.string.import_data_unknown_error)
                                        if (isPasswordDecryptError(errorMsg)) {
                                            passwordError = context.getString(R.string.import_data_password_incorrect_retry)
                                            showPasswordDialog = true
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.import_data_failed_with_reason, errorMsg)
                                            )
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ImportDataScreen", "加密导入异常", e)
                                snackbarHostState.showSnackbar(
                                    context.getString(
                                        R.string.import_data_failed_with_reason,
                                        e.message ?: context.getString(R.string.import_data_unknown_error)
                                    )
                                )
                            } finally {
                                isImporting = false
                            }
                        }
                    },
                    enabled = !isImporting
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.confirm))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showPasswordDialog = false
                        passwordError = null
                    },
                    enabled = !isImporting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // KDBX 密码输入对话框
    if (showKdbxPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showKdbxPasswordDialog = false
                kdbxPassword = ""
            },
            title = { Text(stringResource(R.string.kdbx_import_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.kdbx_import_password_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = kdbxPassword,
                        onValueChange = { kdbxPassword = it },
                        label = { Text(stringResource(R.string.password)) },
                        visualTransformation = if (kdbxPasswordVisible) 
                            androidx.compose.ui.text.input.VisualTransformation.None 
                        else 
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { kdbxPasswordVisible = !kdbxPasswordVisible }) {
                                Icon(
                                    if (kdbxPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showKdbxPasswordDialog = false
                        selectedFileUri?.let { uri ->
                            scope.launch {
                                isImporting = true
                                try {
                                    val result = onImportKdbx(uri, kdbxPassword)
                                    result.onSuccess { count ->
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.import_data_kdbx_import_success_count, count)
                                        )
                                        onNavigateBack()
                                    }.onFailure { error ->
                                        snackbarHostState.showSnackbar(
                                            error.message ?: context.getString(R.string.import_data_error)
                                        )
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.import_data_error_exception,
                                            e.message ?: context.getString(R.string.import_data_unknown_error)
                                        )
                                    )
                                } finally {
                                    isImporting = false
                                    kdbxPassword = ""
                                }
                            }
                        }
                    },
                    enabled = kdbxPassword.isNotEmpty() && !isImporting
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.import_data_btn))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showKdbxPasswordDialog = false
                        kdbxPassword = ""
                    },
                    enabled = !isImporting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// 处理导入结果的辅助函数
private suspend fun handleImportResult(
    result: Result<Int>,
    context: android.content.Context,
    snackbarHostState: SnackbarHostState,
    importType: String,
    onNavigateBack: () -> Unit
) {
    result.onSuccess { count ->
        val message = when (importType) {
            "aegis" -> context.getString(R.string.import_data_aegis_import_success_count, count)
            "stratum" -> context.getString(R.string.import_data_stratum_import_success_count, count)
            "steam" -> context.getString(R.string.import_data_steam_import_success)
            else -> context.getString(R.string.import_data_success_normal, count)
        }
        snackbarHostState.showSnackbar(message)
        onNavigateBack()
    }.onFailure { error ->
        snackbarHostState.showSnackbar(
            error.message ?: context.getString(R.string.import_data_error)
        )
    }
}

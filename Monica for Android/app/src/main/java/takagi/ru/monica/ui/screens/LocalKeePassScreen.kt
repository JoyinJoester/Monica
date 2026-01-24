package takagi.ru.monica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.viewmodel.LocalKeePassViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 本地 KeePass 数据库管理页面
 * M3 Expressive Design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalKeePassScreen(
    viewModel: LocalKeePassViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allDatabases by viewModel.allDatabases.collectAsState()
    val internalDatabases by viewModel.internalDatabases.collectAsState()
    val externalDatabases by viewModel.externalDatabases.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    
    // 对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedDatabase by remember { mutableStateOf<LocalKeePassDatabase?>(null) }
    var showDatabaseDetailSheet by remember { mutableStateOf(false) }
    var databaseToTransferExternal by remember { mutableStateOf<LocalKeePassDatabase?>(null) }
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            showImportDialog = true
            // 传递 URI 给导入对话框
        }
    }
    
    // 外部转移文件创建选择器
    val transferToExternalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-keepass")
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            databaseToTransferExternal?.let { db ->
                viewModel.transferDatabase(db.id, KeePassStorageLocation.EXTERNAL, targetUri)
            }
        }
        databaseToTransferExternal = null
    }
    
    // 处理操作状态
    LaunchedEffect(operationState) {
        when (operationState) {
            is LocalKeePassViewModel.OperationState.Success -> {
                // 可以显示 snackbar
                kotlinx.coroutines.delay(2000)
                viewModel.clearOperationState()
            }
            is LocalKeePassViewModel.OperationState.Error -> {
                kotlinx.coroutines.delay(3000)
                viewModel.clearOperationState()
            }
            else -> {}
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.local_keepass_database),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.create_database)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (allDatabases.isEmpty()) {
                // 空状态
                EmptyKeePassState(
                    onCreateClick = { showCreateDialog = true },
                    onImportClick = { filePickerLauncher.launch(arrayOf("*/*")) }
                )
            } else {
                // 数据库列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 内部存储数据库
                    if (internalDatabases.isNotEmpty()) {
                        item {
                            SectionHeader(
                                icon = Icons.Outlined.PhoneAndroid,
                                title = stringResource(R.string.internal_storage),
                                subtitle = stringResource(R.string.internal_storage_description),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        items(
                            items = internalDatabases,
                            key = { it.id }
                        ) { database ->
                            KeePassDatabaseCard(
                                database = database,
                                onClick = {
                                    selectedDatabase = database
                                    showDatabaseDetailSheet = true
                                }
                            )
                        }
                    }
                    
                    // 外部存储数据库
                    if (externalDatabases.isNotEmpty()) {
                        item {
                            if (internalDatabases.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            SectionHeader(
                                icon = Icons.Outlined.SdStorage,
                                title = stringResource(R.string.external_storage),
                                subtitle = stringResource(R.string.external_storage_description),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        items(
                            items = externalDatabases,
                            key = { it.id }
                        ) { database ->
                            KeePassDatabaseCard(
                                database = database,
                                onClick = {
                                    selectedDatabase = database
                                    showDatabaseDetailSheet = true
                                }
                            )
                        }
                    }
                    
                    // 快捷操作
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        QuickActionsCard(
                            onImportClick = { filePickerLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }
            
            // 操作状态提示
            AnimatedVisibility(
                visible = operationState != LocalKeePassViewModel.OperationState.Idle,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                OperationStatusBar(operationState)
            }
        }
    }
    
    // 创建数据库对话框
    if (showCreateDialog) {
        CreateKeePassDatabaseDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, password, location, externalUri, description ->
                viewModel.createDatabase(name, password, location, externalUri, description)
                showCreateDialog = false
            }
        )
    }
    
    // 导入数据库对话框
    if (showImportDialog) {
        // 这里需要处理文件导入
    }
    
    // 数据库详情底部弹窗
    if (showDatabaseDetailSheet && selectedDatabase != null) {
        DatabaseDetailBottomSheet(
            database = selectedDatabase!!,
            onDismiss = { 
                showDatabaseDetailSheet = false
                selectedDatabase = null
            },
            onSetDefault = { viewModel.setAsDefault(it.id) },
            onDelete = { viewModel.deleteDatabase(it.id, deleteFile = false) },
            onTransferToInternal = { viewModel.transferDatabase(it.id, KeePassStorageLocation.INTERNAL) },
            onTransferToExternal = { db ->
                // 保存要转移的数据库，关闭弹窗，打开文件选择器
                databaseToTransferExternal = db
                showDatabaseDetailSheet = false
                selectedDatabase = null
                transferToExternalLauncher.launch("${db.name}.kdbx")
            },
            onExport = { /* 需要文件选择器 */ }
        )
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyKeePassState(
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标动画
        val infiniteTransition = rememberInfiniteTransition(label = "icon")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size((80 * scale).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            stringResource(R.string.no_keepass_database),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            stringResource(R.string.no_keepass_database_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.open_existing))
            }
            
            Button(
                onClick = onCreateClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_new))
            }
        }
    }
}

/**
 * 区块标题
 */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = color
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * KeePass 数据库卡片
 */
@Composable
private fun KeePassDatabaseCard(
    database: LocalKeePassDatabase,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (database.isDefault)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (database.isDefault) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (database.storageLocation == KeePassStorageLocation.INTERNAL)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (database.storageLocation == KeePassStorageLocation.INTERNAL)
                            Icons.Filled.Lock
                        else
                            Icons.Filled.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (database.storageLocation == KeePassStorageLocation.INTERNAL)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        database.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (database.isDefault) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                stringResource(R.string.default_label),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 位置信息
                Text(
                    if (database.storageLocation == KeePassStorageLocation.INTERNAL)
                        stringResource(R.string.internal_storage)
                    else
                        stringResource(R.string.external_storage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 最后更新时间
                Text(
                    stringResource(R.string.last_updated_format, dateFormat.format(Date(database.lastAccessedAt))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 快捷操作卡片
 */
@Composable
private fun QuickActionsCard(
    onImportClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onImportClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FileOpen,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.open_external_database),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.open_external_database_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 操作状态栏
 */
@Composable
private fun OperationStatusBar(state: LocalKeePassViewModel.OperationState) {
    val backgroundColor = when (state) {
        is LocalKeePassViewModel.OperationState.Loading -> MaterialTheme.colorScheme.primaryContainer
        is LocalKeePassViewModel.OperationState.Success -> MaterialTheme.colorScheme.tertiaryContainer
        is LocalKeePassViewModel.OperationState.Error -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    
    val contentColor = when (state) {
        is LocalKeePassViewModel.OperationState.Loading -> MaterialTheme.colorScheme.onPrimaryContainer
        is LocalKeePassViewModel.OperationState.Success -> MaterialTheme.colorScheme.onTertiaryContainer
        is LocalKeePassViewModel.OperationState.Error -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val icon = when (state) {
        is LocalKeePassViewModel.OperationState.Loading -> Icons.Default.Sync
        is LocalKeePassViewModel.OperationState.Success -> Icons.Default.CheckCircle
        is LocalKeePassViewModel.OperationState.Error -> Icons.Default.Error
        else -> Icons.Default.Info
    }
    
    val message = when (state) {
        is LocalKeePassViewModel.OperationState.Loading -> state.message
        is LocalKeePassViewModel.OperationState.Success -> state.message
        is LocalKeePassViewModel.OperationState.Error -> state.message
        else -> ""
    }
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state is LocalKeePassViewModel.OperationState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

/**
 * 创建数据库对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateKeePassDatabaseDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, password: String, location: KeePassStorageLocation, externalUri: Uri?, description: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var storageLocation by remember { mutableStateOf(KeePassStorageLocation.INTERNAL) }
    var showPassword by remember { mutableStateOf(false) }
    var externalUri by remember { mutableStateOf<Uri?>(null) }
    
    // 外部存储选择器
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        externalUri = uri
    }
    
    val isValid = name.isNotBlank() && 
                  password.isNotBlank() && 
                  password == confirmPassword &&
                  (storageLocation == KeePassStorageLocation.INTERNAL || externalUri != null)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.create_keepass_database),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 数据库名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.database_name)) },
                    placeholder = { Text(stringResource(R.string.database_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 密码
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.database_password)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 确认密码
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.confirm_password)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirmPassword.isNotBlank() && password != confirmPassword,
                    supportingText = if (confirmPassword.isNotBlank() && password != confirmPassword) {
                        { Text(stringResource(R.string.password_mismatch)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 描述（可选）
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_optional)) },
                    placeholder = { Text(stringResource(R.string.description_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider()
                
                // 存储位置选择
                Text(
                    stringResource(R.string.storage_location),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                // 内部存储选项
                StorageLocationOption(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = stringResource(R.string.internal_storage),
                    description = stringResource(R.string.internal_storage_option_description),
                    selected = storageLocation == KeePassStorageLocation.INTERNAL,
                    onClick = { storageLocation = KeePassStorageLocation.INTERNAL }
                )
                
                // 外部存储选项
                StorageLocationOption(
                    icon = Icons.Outlined.SdStorage,
                    title = stringResource(R.string.external_storage),
                    description = if (externalUri != null)
                        stringResource(R.string.location_selected)
                    else
                        stringResource(R.string.external_storage_option_description),
                    selected = storageLocation == KeePassStorageLocation.EXTERNAL,
                    onClick = { 
                        storageLocation = KeePassStorageLocation.EXTERNAL
                        if (externalUri == null) {
                            directoryPickerLauncher.launch(null)
                        }
                    },
                    trailing = if (storageLocation == KeePassStorageLocation.EXTERNAL) {
                        {
                            TextButton(
                                onClick = { directoryPickerLauncher.launch(null) }
                            ) {
                                Text(stringResource(R.string.select_location))
                            }
                        }
                    } else null
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        name,
                        password,
                        storageLocation,
                        externalUri,
                        description.ifBlank { null }
                    )
                },
                enabled = isValid
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 存储位置选项
 */
@Composable
private fun StorageLocationOption(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (selected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            trailing?.invoke()
        }
    }
}

/**
 * 数据库详情底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatabaseDetailBottomSheet(
    database: LocalKeePassDatabase,
    onDismiss: () -> Unit,
    onSetDefault: (LocalKeePassDatabase) -> Unit,
    onDelete: (LocalKeePassDatabase) -> Unit,
    onTransferToInternal: (LocalKeePassDatabase) -> Unit,
    onTransferToExternal: (LocalKeePassDatabase) -> Unit,
    onExport: (LocalKeePassDatabase) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // 标题区
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        database.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (database.storageLocation == KeePassStorageLocation.INTERNAL)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                if (database.storageLocation == KeePassStorageLocation.INTERNAL)
                                    stringResource(R.string.internal_storage)
                                else
                                    stringResource(R.string.external_storage),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (database.storageLocation == KeePassStorageLocation.INTERNAL)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        if (database.isDefault) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    stringResource(R.string.default_label),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 信息区
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow(
                        label = stringResource(R.string.created_at),
                        value = dateFormat.format(Date(database.createdAt))
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    InfoRow(
                        label = stringResource(R.string.last_accessed),
                        value = dateFormat.format(Date(database.lastAccessedAt))
                    )
                    
                    if (database.description != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        InfoRow(
                            label = stringResource(R.string.description),
                            value = database.description
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 操作按钮
            Text(
                stringResource(R.string.actions),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 设为默认
            if (!database.isDefault) {
                ActionButton(
                    icon = Icons.Default.Star,
                    text = stringResource(R.string.set_as_default),
                    onClick = {
                        onSetDefault(database)
                        onDismiss()
                    }
                )
            }
            
            // 导出（仅内部存储）
            if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
                ActionButton(
                    icon = Icons.Default.Upload,
                    text = stringResource(R.string.export_to_external),
                    onClick = { onExport(database) }
                )
                
                // 转移到外部存储
                ActionButton(
                    icon = Icons.Default.DriveFileMove,
                    text = stringResource(R.string.transfer_to_external),
                    onClick = {
                        onTransferToExternal(database)
                        onDismiss()
                    }
                )
            }
            
            // 转移到内部（仅外部存储）
            if (database.storageLocation == KeePassStorageLocation.EXTERNAL) {
                ActionButton(
                    icon = Icons.Default.MoveToInbox,
                    text = stringResource(R.string.transfer_to_internal),
                    onClick = {
                        onTransferToInternal(database)
                        onDismiss()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 删除
            ActionButton(
                icon = Icons.Default.Delete,
                text = stringResource(R.string.remove_database),
                color = MaterialTheme.colorScheme.error,
                onClick = { showDeleteConfirm = true }
            )
        }
    }
    
    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.confirm_remove)) },
            text = { 
                Text(
                    if (database.storageLocation == KeePassStorageLocation.INTERNAL)
                        stringResource(R.string.confirm_remove_internal_description)
                    else
                        stringResource(R.string.confirm_remove_external_description)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(database)
                        showDeleteConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 信息行
 */
@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 操作按钮
 */
@Composable
private fun ActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = color
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = color
            )
        }
    }
}

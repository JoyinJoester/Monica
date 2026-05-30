package takagi.ru.monica.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.MdbxUnlockMethod
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.utils.WebDavHelper
import takagi.ru.monica.viewmodel.MdbxKeyFileSelection
import takagi.ru.monica.viewmodel.MdbxViewModel
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MdbxWebDavCreateScreen(
    viewModel: MdbxViewModel,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val operationState by viewModel.operationState.collectAsState()

    val webDavHelper = remember { WebDavHelper(context) }
    val savedConfig = remember { webDavHelper.getCurrentConfig() }

    var serverUrl by remember { mutableStateOf(savedConfig?.serverUrl ?: "") }
    var username by remember { mutableStateOf(savedConfig?.username ?: "") }
    var webDavPassword by remember { mutableStateOf(webDavHelper.getCurrentPasswordForEdit()) }
    var showWebDavPassword by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.NotTested) }
    var remoteDirectory by remember { mutableStateOf("") }

    var vaultName by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var unlockMethod by remember { mutableStateOf(MdbxUnlockMethod.MASTER_PASSWORD) }
    var keyFile by remember { mutableStateOf<MdbxKeyFileSelection?>(null) }
    var keyFileError by remember { mutableStateOf<String?>(null) }
    var selectedTigaMode by remember { mutableStateOf(MdbxTigaMode.MULTI) }

    val passwordRequired = unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD ||
        unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE
    val keyFileRequired = unlockMethod == MdbxUnlockMethod.KEY_FILE ||
        unlockMethod == MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE

    val normalizedMasterPassword = remember(masterPassword) {
        Normalizer.normalize(masterPassword, Normalizer.Form.NFC)
    }
    val normalizedConfirmPassword = remember(confirmPassword) {
        Normalizer.normalize(confirmPassword, Normalizer.Form.NFC)
    }

    val keyFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                keyFileError = null
                viewModel.readSelectedKeyFile(uri)
                    .onSuccess { keyFile = it }
                    .onFailure { keyFileError = it.message ?: "无法读取 MDBX 密钥文件" }
            }
        }
    }

    val keyFileCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                keyFileError = null
                viewModel.writeGeneratedKeyFile(uri)
                    .onSuccess { keyFile = it }
                    .onFailure { keyFileError = it.message ?: "无法生成 MDBX 密钥文件" }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.clearOperationState()
    }
    LaunchedEffect(operationState) {
        if (operationState is MdbxViewModel.OperationState.Success) {
            delay(1200)
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mdbx_create_vault_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === Card: WebDAV Connection ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "WebDAV 连接",
                        style = MaterialTheme.typography.titleMedium
                    )
                    MdbxWebDavConnectionSection(
                        serverUrl = serverUrl,
                        onServerUrlChange = {
                            serverUrl = it
                            connectionState = ConnectionState.NotTested
                        },
                        username = username,
                        onUsernameChange = {
                            username = it
                            connectionState = ConnectionState.NotTested
                        },
                        password = webDavPassword,
                        onPasswordChange = {
                            webDavPassword = it
                            connectionState = ConnectionState.NotTested
                        },
                        showPassword = showWebDavPassword,
                        onTogglePasswordVisibility = { showWebDavPassword = !showWebDavPassword },
                        connectionState = connectionState,
                        onTestConnection = {
                            connectionState = ConnectionState.Testing
                            scope.launch {
                                val result = viewModel.testWebDavConnection(
                                    serverUrl = serverUrl,
                                    username = username,
                                    password = webDavPassword
                                )
                                connectionState = if (result.isSuccess) {
                                    ConnectionState.Connected
                                } else {
                                    ConnectionState.Failed(
                                        result.exceptionOrNull()?.message ?: "Unknown error"
                                    )
                                }
                                if (connectionState is ConnectionState.Connected) {
                                    webDavHelper.configure(serverUrl, username, webDavPassword)
                                }
                            }
                        }
                    )
                    AnimatedVisibility(
                        visible = connectionState is ConnectionState.Connected,
                        enter = expandVertically() + fadeIn()
                    ) {
                        OutlinedTextField(
                            value = remoteDirectory,
                            onValueChange = { remoteDirectory = it },
                            label = { Text(stringResource(R.string.mdbx_webdav_directory)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // === Vault creation sections (only after connection) ===
            AnimatedVisibility(
                visible = connectionState is ConnectionState.Connected,
                enter = expandVertically() + fadeIn()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MdbxTigaModeSection(
                        selectedTigaMode = selectedTigaMode,
                        onTigaModeChange = { selectedTigaMode = it }
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.mdbx_vault_settings),
                                style = MaterialTheme.typography.titleMedium
                            )
                            MdbxVaultNameField(
                                vaultName = vaultName,
                                onVaultNameChange = { vaultName = it }
                            )
                            MdbxPasswordFieldSection(
                                masterPassword = masterPassword,
                                onMasterPasswordChange = { masterPassword = it },
                                confirmPassword = confirmPassword,
                                onConfirmPasswordChange = { confirmPassword = it },
                                passwordRequired = passwordRequired
                            )
                        }
                    }

                    MdbxUnlockMethodSection(
                        unlockMethod = unlockMethod,
                        onUnlockMethodChange = { unlockMethod = it }
                    )

                    MdbxKeyFileSection(
                        keyFile = keyFile,
                        keyFileError = keyFileError,
                        keyFileRequired = keyFileRequired,
                        onPickKeyFile = { keyFilePickerLauncher.launch(arrayOf("*/*")) },
                        onGenerateKeyFile = { keyFileCreateLauncher.launch("monica-mdbx.key") }
                    )
                }
            }

            // === Submit Button ===
            val isFormValid = connectionState is ConnectionState.Connected &&
                serverUrl.isNotBlank() &&
                username.isNotBlank() &&
                webDavPassword.isNotBlank() &&
                vaultName.isNotBlank() &&
                (!passwordRequired || (
                    normalizedMasterPassword.isNotBlank() &&
                        normalizedMasterPassword == normalizedConfirmPassword
                    )) &&
                (!keyFileRequired || keyFile != null) &&
                operationState !is MdbxViewModel.OperationState.Loading

            Button(
                onClick = {
                    viewModel.createWebDavVault(
                        name = vaultName,
                        masterPassword = masterPassword,
                        unlockMethod = unlockMethod,
                        keyFile = keyFile,
                        tigaMode = selectedTigaMode,
                        serverUrl = serverUrl,
                        username = username,
                        webDavPassword = webDavPassword,
                        remoteDirectoryPath = remoteDirectory.ifBlank { null },
                        description = null
                    )
                },
                enabled = isFormValid,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (operationState is MdbxViewModel.OperationState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.mdbx_creating_vault))
                } else {
                    Text("创建远程保险库")
                }
            }

            MdbxOperationFeedback(operationState)
        }
    }
}

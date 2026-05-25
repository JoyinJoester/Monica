package takagi.ru.monica.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.MdbxUnlockMethod
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.mdbx.MdbxDiagLogger
import takagi.ru.monica.viewmodel.MdbxKeyFileSelection
import takagi.ru.monica.viewmodel.MdbxViewModel
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MdbxLocalCreateScreen(
    viewModel: MdbxViewModel,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val operationState by viewModel.operationState.collectAsState()

    var vaultName by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showMasterPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var unlockMethod by remember { mutableStateOf(MdbxUnlockMethod.MASTER_PASSWORD) }
    var keyFile by remember { mutableStateOf<MdbxKeyFileSelection?>(null) }
    var keyFileError by remember { mutableStateOf<String?>(null) }
    var selectedTigaMode by remember { mutableStateOf(MdbxTigaMode.MULTI) }
    var useCustomDirectory by remember { mutableStateOf(false) }
    var customDirectoryUri by remember { mutableStateOf<Uri?>(null) }

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

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) customDirectoryUri = uri
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
            // === Card: Storage Location ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ListItem(
                        headlineContent = { Text("存储位置", style = MaterialTheme.typography.titleMedium) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text("保存到指定本地文件夹") },
                        supportingContent = {
                            Text(
                                customDirectoryUri?.lastPathSegment
                                    ?: stringResource(R.string.mdbx_local_create_hint)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = useCustomDirectory,
                                onCheckedChange = { useCustomDirectory = it }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                    AnimatedVisibility(
                        visible = useCustomDirectory,
                        enter = expandVertically() + fadeIn()
                    ) {
                        OutlinedButton(
                            onClick = { directoryPickerLauncher.launch(null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.mdbx_select_directory))
                        }
                    }
                }
            }

            // === Card: Tiga Mode ===
            MdbxTigaModeSection(
                selectedTigaMode = selectedTigaMode,
                onTigaModeChange = { selectedTigaMode = it }
            )

            // === Card: Vault Settings ===
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

            // === Submit Button ===
            val isFormValid = vaultName.isNotBlank() &&
                (!passwordRequired || (
                    normalizedMasterPassword.isNotBlank() &&
                        normalizedMasterPassword == normalizedConfirmPassword
                    )) &&
                (!keyFileRequired || keyFile != null) &&
                (!useCustomDirectory || customDirectoryUri != null) &&
                operationState !is MdbxViewModel.OperationState.Loading

            Button(
                onClick = {
                    MdbxDiagLogger.append(
                        "[MDBX][MdbxLocalCreateScreen] submitClicked name=${vaultName.trim().ifBlank { "<blank>" }} useCustomDirectory=$useCustomDirectory hasCustomUri=${customDirectoryUri != null} unlock=${unlockMethod.name} passwordRequired=$passwordRequired keyFileRequired=$keyFileRequired hasKeyFile=${keyFile != null} formValid=$isFormValid"
                    )
                    viewModel.createLocalVault(
                        name = vaultName,
                        masterPassword = masterPassword,
                        unlockMethod = unlockMethod,
                        keyFile = keyFile,
                        tigaMode = selectedTigaMode,
                        description = null,
                        customDirectoryUri = if (useCustomDirectory) customDirectoryUri else null
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
                    Text(stringResource(R.string.mdbx_create_vault_button))
                }
            }

            MdbxOperationFeedback(operationState)
        }
    }
}

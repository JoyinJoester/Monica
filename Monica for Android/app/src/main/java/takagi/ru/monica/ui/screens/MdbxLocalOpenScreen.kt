package takagi.ru.monica.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.MdbxUnlockMethod
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.viewmodel.MdbxKeyFileSelection
import takagi.ru.monica.viewmodel.MdbxViewModel
import java.text.Normalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MdbxLocalOpenScreen(
    viewModel: MdbxViewModel,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val operationState by viewModel.operationState.collectAsState()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var vaultName by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showMasterPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var unlockMethod by remember { mutableStateOf(MdbxUnlockMethod.MASTER_PASSWORD) }
    var keyFile by remember { mutableStateOf<MdbxKeyFileSelection?>(null) }
    var keyFileError by remember { mutableStateOf<String?>(null) }

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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedUri = uri
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mdbx_open_vault_button)) },
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
            // === Card: Select File ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "选择文件",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.mdbx_open_local_vault_button))
                    }
                    selectedUri?.let { uri ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    uri.lastPathSegment.orEmpty(),
                                    maxLines = 1
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    }
                }
            }

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
            val isFormValid = selectedUri != null &&
                vaultName.isNotBlank() &&
                (!passwordRequired || (
                    normalizedMasterPassword.isNotBlank() &&
                        normalizedMasterPassword == normalizedConfirmPassword
                    )) &&
                (!keyFileRequired || keyFile != null) &&
                operationState !is MdbxViewModel.OperationState.Loading

            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        viewModel.importLocalVault(
                            sourceUri = uri,
                            name = vaultName,
                            masterPassword = masterPassword,
                            unlockMethod = unlockMethod,
                            keyFile = keyFile,
                            tigaMode = MdbxTigaMode.MULTI,
                            description = null
                        )
                    }
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
                    Text(stringResource(R.string.mdbx_open_vault_button))
                }
            }

            MdbxOperationFeedback(operationState)
        }
    }
}

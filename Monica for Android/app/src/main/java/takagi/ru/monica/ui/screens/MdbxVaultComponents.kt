package takagi.ru.monica.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.MdbxUnlockMethod
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.viewmodel.MdbxKeyFileSelection
import takagi.ru.monica.viewmodel.MdbxViewModel
import java.text.Normalizer

internal sealed class ConnectionState {
    data object NotTested : ConnectionState()
    data object Testing : ConnectionState()
    data object Connected : ConnectionState()
    data class Failed(val error: String) : ConnectionState()
}

@Composable
internal fun MdbxVaultNameField(
    vaultName: String,
    onVaultNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = vaultName,
        onValueChange = onVaultNameChange,
        label = { Text(stringResource(R.string.mdbx_vault_name)) },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun MdbxPasswordFieldSection(
    masterPassword: String,
    onMasterPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    passwordRequired: Boolean
) {
    var showMasterPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    val normalizedMasterPassword = remember(masterPassword) {
        Normalizer.normalize(masterPassword, Normalizer.Form.NFC)
    }
    val normalizedConfirmPassword = remember(confirmPassword) {
        Normalizer.normalize(confirmPassword, Normalizer.Form.NFC)
    }

    OutlinedTextField(
        value = masterPassword,
        onValueChange = onMasterPasswordChange,
        label = { Text(stringResource(R.string.mdbx_master_password)) },
        visualTransformation = if (showMasterPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showMasterPassword = !showMasterPassword }) {
                Icon(
                    if (showMasterPassword) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = null
                )
            }
        },
        singleLine = true,
        enabled = passwordRequired,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChange,
        label = { Text(stringResource(R.string.mdbx_confirm_password)) },
        visualTransformation = if (showConfirmPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                Icon(
                    if (showConfirmPassword) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = null
                )
            }
        },
        isError = confirmPassword.isNotEmpty() && normalizedMasterPassword != normalizedConfirmPassword,
        supportingText = if (confirmPassword.isNotEmpty() && normalizedMasterPassword != normalizedConfirmPassword) {
            { Text(stringResource(R.string.mdbx_password_mismatch)) }
        } else {
            { Text("支持中文主密码；MDBX 会按 Unicode NFC 处理。") }
        },
        singleLine = true,
        enabled = passwordRequired,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun MdbxUnlockMethodSection(
    unlockMethod: MdbxUnlockMethod,
    onUnlockMethodChange: (MdbxUnlockMethod) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                "解锁方式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val methods = listOf(
                Triple(MdbxUnlockMethod.MASTER_PASSWORD, Icons.Default.Key, "只用主密码解锁"),
                Triple(MdbxUnlockMethod.KEY_FILE, Icons.Default.VpnKey, "只用 MDBX key file 解锁"),
                Triple(MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE, Icons.Default.Shield, "两者同时正确才可解锁")
            )
            val titles = mapOf(
                MdbxUnlockMethod.MASTER_PASSWORD to "主密码",
                MdbxUnlockMethod.KEY_FILE to "密钥文件",
                MdbxUnlockMethod.MASTER_PASSWORD_AND_KEY_FILE to "主密码 + 密钥文件"
            )

            methods.forEach { (method, icon, description) ->
                ListItem(
                    headlineContent = {
                        Text(
                            titles[method] ?: "",
                            fontWeight = if (unlockMethod == method) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    supportingContent = { Text(description) },
                    leadingContent = {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (unlockMethod == method) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingContent = {
                        RadioButton(
                            selected = unlockMethod == method,
                            onClick = { onUnlockMethodChange(method) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickable { onUnlockMethodChange(method) }
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun MdbxTigaModeSection(
    selectedTigaMode: MdbxTigaMode,
    onTigaModeChange: (MdbxTigaMode) -> Unit
) {
    val descResId = when (selectedTigaMode) {
        MdbxTigaMode.POWER -> R.string.mdbx_tiga_power_desc
        MdbxTigaMode.MULTI -> R.string.mdbx_tiga_multi_desc
        MdbxTigaMode.SKY -> R.string.mdbx_tiga_sky_desc
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.mdbx_tiga_section),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(descResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MdbxTigaMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedTigaMode == mode,
                        onClick = { onTigaModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = MdbxTigaMode.entries.size
                        ),
                        label = { Text(mode.label) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MdbxKeyFileSection(
    keyFile: MdbxKeyFileSelection?,
    keyFileError: String?,
    keyFileRequired: Boolean,
    onPickKeyFile: () -> Unit,
    onGenerateKeyFile: () -> Unit
) {
    if (!keyFileRequired) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        keyFile?.name ?: "MDBX 密钥文件",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    if (keyFile != null) {
                        AssistChip(
                            onClick = {},
                            label = { Text("SHA-256 ${keyFile.shortFingerprint}...") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    null,
                                    Modifier.size(16.dp)
                                )
                            }
                        )
                    } else {
                        Text("选择已有密钥文件，或生成新的 .key 文件")
                    }
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickKeyFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("选择")
                }
                Button(
                    onClick = onGenerateKeyFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("生成")
                }
            }
            keyFileError?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
internal fun MdbxOperationFeedback(
    operationState: MdbxViewModel.OperationState
) {
    when (operationState) {
        is MdbxViewModel.OperationState.Success -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                operationState.message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        is MdbxViewModel.OperationState.Error -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                operationState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        else -> {}
    }
}

@Composable
internal fun MdbxWebDavConnectionSection(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    connectionState: ConnectionState,
    onTestConnection: () -> Unit
) {
    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChange,
        label = { Text(stringResource(R.string.mdbx_webdav_url)) },
        leadingIcon = { Icon(Icons.Default.Cloud, null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.mdbx_webdav_username)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.mdbx_webdav_password)) },
        visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    if (showPassword) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = null
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = onTestConnection,
        enabled = serverUrl.isNotBlank() && username.isNotBlank() &&
            password.isNotBlank() && connectionState !is ConnectionState.Testing,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (connectionState) {
            is ConnectionState.Testing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.mdbx_test_connection))
            }
            is ConnectionState.Connected -> {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.mdbx_connection_success))
            }
            else -> Text(stringResource(R.string.mdbx_test_connection))
        }
    }

    if (connectionState is ConnectionState.Connected) {
        Text(
            stringResource(R.string.mdbx_connection_success),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    } else if (connectionState is ConnectionState.Failed) {
        Text(
            "${stringResource(R.string.mdbx_connection_failed)}: ${connectionState.error}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

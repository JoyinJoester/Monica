package takagi.ru.monica.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.data.model.PasskeyBinding
import takagi.ru.monica.data.model.PasskeyBindingCodec
import takagi.ru.monica.passkey.PasskeyPrivateKeySupport
import takagi.ru.monica.passkey.managementRecordIdOrNull
import takagi.ru.monica.ui.PasskeyDetailPane
import takagi.ru.monica.ui.components.ActionStrip
import takagi.ru.monica.ui.components.ActionStripItem
import takagi.ru.monica.ui.components.PasswordEntryPickerBottomSheet
import takagi.ru.monica.viewmodel.PasskeyViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasskeyDetailScreen(
    recordId: Long,
    passkeyViewModel: PasskeyViewModel,
    passwordViewModel: PasswordViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPasswordDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val passkeys by passkeyViewModel.allPasskeys.collectAsState()
    val passwords by passwordViewModel.allPasswordsForUi.collectAsState()
    val scope = rememberCoroutineScope()
    val bitwardenRepository = remember { BitwardenRepository.getInstance(context) }
    val passwordMap = remember(passwords) { passwords.associateBy { it.id } }
    var passkeyToBind by remember { mutableStateOf<PasskeyEntry?>(null) }
    val passkey = passkeys.firstOrNull { it.id == recordId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        passkey?.let { currentPasskey ->
                            currentPasskey.rpName.ifBlank { currentPasskey.rpId }
                        } ?: stringResource(R.string.passkey_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            passkey?.let { currentPasskey ->
                ActionStrip(
                    actions = buildList {
                        if (currentPasskey.boundPasswordId == null) {
                            add(
                                ActionStripItem(
                                    icon = Icons.Default.Link,
                                    contentDescription = stringResource(R.string.bind_password),
                                    onClick = { passkeyToBind = currentPasskey }
                                )
                            )
                        } else {
                            add(
                                ActionStripItem(
                                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.passkey_view_details),
                                    onClick = { onNavigateToPasswordDetail(currentPasskey.boundPasswordId) }
                                )
                            )
                            add(
                                ActionStripItem(
                                    icon = Icons.Default.Link,
                                    contentDescription = stringResource(R.string.bound_password_change),
                                    onClick = { passkeyToBind = currentPasskey }
                                )
                            )
                            add(
                                ActionStripItem(
                                    icon = Icons.Default.LinkOff,
                                    contentDescription = stringResource(R.string.unbind),
                                    onClick = {
                                        unbindPasskey(
                                            recordId = currentPasskey.id,
                                            credentialId = currentPasskey.credentialId,
                                            boundPasswordId = currentPasskey.boundPasswordId,
                                            passwords = passwords,
                                            passkeyViewModel = passkeyViewModel,
                                            passwordViewModel = passwordViewModel
                                        )
                                    }
                                )
                            )
                        }
                        add(
                            ActionStripItem(
                                icon = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete),
                                onClick = {
                                    scope.launch {
                                        passkeyViewModel.deletePasskey(currentPasskey)
                                        onNavigateBack()
                                    }
                                },
                                tint = MaterialTheme.colorScheme.error
                            )
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    ) { paddingValues ->
        if (passkey == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val boundPasswordTitle = passkey.boundPasswordId?.let { boundId ->
            passwordMap[boundId]?.title
        }

        PasskeyDetailPane(
            passkey = passkey,
            boundPasswordTitle = boundPasswordTitle,
            onBindPassword = { passkeyToBind = passkey },
            onChangeBinding = { passkeyToBind = passkey },
            onOpenBoundPassword = passkey.boundPasswordId?.let { boundId ->
                { onNavigateToPasswordDetail(boundId) }
            },
            onUnbindPassword = {
                unbindPasskey(
                    recordId = passkey.id,
                    credentialId = passkey.credentialId,
                    boundPasswordId = passkey.boundPasswordId,
                    passwords = passwords,
                    passkeyViewModel = passkeyViewModel,
                    passwordViewModel = passwordViewModel
                )
            },
            onDeletePasskey = {
                scope.launch {
                    passkeyViewModel.deletePasskey(passkey)
                    onNavigateBack()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }

    PasswordEntryPickerBottomSheet(
        visible = passkeyToBind != null,
        title = stringResource(R.string.select_password_to_bind),
        passwords = passwords.filter { !it.isDeleted && !it.isArchived },
        selectedEntryId = passkeyToBind?.boundPasswordId,
        onDismiss = { passkeyToBind = null },
        onSelect = { password ->
            val currentPasskey = passkeyToBind ?: return@PasswordEntryPickerBottomSheet
            scope.launch {
                when (
                    val result = bindPasskey(
                        passkey = currentPasskey,
                        password = password,
                        passwordMap = passwordMap,
                        passkeyViewModel = passkeyViewModel,
                        passwordViewModel = passwordViewModel,
                        bitwardenRepository = bitwardenRepository
                    )
                ) {
                    PasskeyBindResult.DeleteQueueFailed -> {
                        Toast.makeText(context, "操作失败", Toast.LENGTH_SHORT).show()
                    }

                    PasskeyBindResult.UpdateFailed -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.passkey_update_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is PasskeyBindResult.Success -> {
                        if (result.showLegacyHint) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.passkey_bitwarden_legacy_hint),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        passkeyToBind = null
                    }
                }
            }
        }
    )
}

private sealed interface PasskeyBindResult {
    data class Success(val showLegacyHint: Boolean) : PasskeyBindResult
    data object UpdateFailed : PasskeyBindResult
    data object DeleteQueueFailed : PasskeyBindResult
}

private suspend fun bindPasskey(
    passkey: PasskeyEntry,
    password: PasswordEntry,
    passwordMap: Map<Long, PasswordEntry>,
    passkeyViewModel: PasskeyViewModel,
    passwordViewModel: PasswordViewModel,
    bitwardenRepository: BitwardenRepository
): PasskeyBindResult {
    val previousPasswordId = passkey.boundPasswordId
    val nonMigratableForBitwarden = password.bitwardenVaultId != null &&
        !withContext(Dispatchers.IO) { isPasskeyMigratableToBitwarden(passkey) }

    val newBinding = PasskeyBinding(
        credentialId = passkey.credentialId,
        rpId = passkey.rpId,
        rpName = passkey.rpName,
        userName = passkey.userName,
        userDisplayName = passkey.userDisplayName
    )

    passwordMap[password.id]?.let { targetEntry ->
        val updatedBindings = PasskeyBindingCodec.addBinding(targetEntry.passkeyBindings, newBinding)
        passwordViewModel.updatePasskeyBindings(password.id, updatedBindings)
    }

    if (previousPasswordId != null && previousPasswordId != password.id) {
        passwordMap[previousPasswordId]?.let { previousEntry ->
            val updatedBindings = PasskeyBindingCodec.removeBinding(
                previousEntry.passkeyBindings,
                passkey.credentialId
            )
            passwordViewModel.updatePasskeyBindings(previousPasswordId, updatedBindings)
        }
    }

    if (passkey.syncStatus == "REFERENCE") {
        passkey.managementRecordIdOrNull()?.let { recordId ->
            passkeyViewModel.updateBoundPassword(recordId, password.id)
        }
        return PasskeyBindResult.Success(showLegacyHint = false)
    }

    val targetVaultId = when {
        password.bitwardenVaultId != null && !nonMigratableForBitwarden -> password.bitwardenVaultId
        password.bitwardenVaultId != null && nonMigratableForBitwarden -> passkey.bitwardenVaultId
        else -> null
    }
    val targetFolderId = when {
        password.bitwardenVaultId != null && !nonMigratableForBitwarden -> password.bitwardenFolderId
        password.bitwardenVaultId != null && nonMigratableForBitwarden -> passkey.bitwardenFolderId
        else -> null
    }
    val currentVaultId = passkey.bitwardenVaultId
    val currentCipherId = passkey.bitwardenCipherId
    val isLeavingCurrentCipher =
        currentVaultId != null &&
            !currentCipherId.isNullOrBlank() &&
            currentVaultId != targetVaultId

    if (isLeavingCurrentCipher) {
        val queueResult = bitwardenRepository.queueCipherDelete(
            vaultId = currentVaultId,
            cipherId = currentCipherId.orEmpty(),
            itemType = BitwardenPendingOperation.ITEM_TYPE_PASSKEY
        )
        if (queueResult.isFailure) {
            return PasskeyBindResult.DeleteQueueFailed
        }
    }

    val keepExistingCipher =
        currentVaultId != null &&
            !currentCipherId.isNullOrBlank() &&
            currentVaultId == targetVaultId

    val resolvedSyncStatus = when {
        targetVaultId == null -> "NONE"
        keepExistingCipher -> if (passkey.syncStatus == "SYNCED") "SYNCED" else "PENDING"
        else -> "PENDING"
    }
    val resolvedMode = when {
        targetVaultId != null -> PasskeyEntry.MODE_BW_COMPAT
        password.keepassDatabaseId != null -> PasskeyEntry.MODE_KEEPASS_COMPAT
        passkey.isKeePassCompatible() -> PasskeyEntry.MODE_KEEPASS_COMPAT
        else -> passkey.passkeyMode
    }

    val updateResult = passkeyViewModel.updatePasskey(
        passkey.copy(
            boundPasswordId = password.id,
            categoryId = password.categoryId,
            keepassDatabaseId = password.keepassDatabaseId,
            keepassGroupPath = password.keepassGroupPath,
            bitwardenVaultId = targetVaultId,
            bitwardenFolderId = targetFolderId,
            bitwardenCipherId = if (keepExistingCipher) currentCipherId else null,
            syncStatus = resolvedSyncStatus,
            passkeyMode = resolvedMode
        )
    )
    if (updateResult.isFailure) {
        return PasskeyBindResult.UpdateFailed
    }

    return PasskeyBindResult.Success(showLegacyHint = nonMigratableForBitwarden)
}

private fun unbindPasskey(
    recordId: Long,
    credentialId: String,
    boundPasswordId: Long?,
    passwords: List<PasswordEntry>,
    passkeyViewModel: PasskeyViewModel,
    passwordViewModel: PasswordViewModel
) {
    if (boundPasswordId != null) {
        passwords.firstOrNull { it.id == boundPasswordId }?.let { entry ->
            val bindings = PasskeyBindingCodec.removeBinding(entry.passkeyBindings, credentialId)
            passwordViewModel.updatePasskeyBindings(boundPasswordId, bindings)
        }
    }
    if (recordId > 0L) {
        passkeyViewModel.updateBoundPassword(recordId, null)
    }
}

private fun isPasskeyMigratableToBitwarden(passkey: PasskeyEntry): Boolean {
    if (passkey.passkeyMode != PasskeyEntry.MODE_BW_COMPAT) return false
    if (passkey.syncStatus == "REFERENCE") return false
    return PasskeyPrivateKeySupport.hasBitwardenCompatiblePrivateKey(passkey.privateKeyAlias)
}

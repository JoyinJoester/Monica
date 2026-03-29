package takagi.ru.monica.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.common.dialog.DeleteConfirmDialog
import takagi.ru.monica.ui.components.M3IdentityVerifyDialog
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.viewmodel.PasswordViewModel

internal enum class ManualStackDialogMode(
    val titleRes: Int,
    val descRes: Int
) {
    STACK(
        titleRes = R.string.batch_stack_mode_stack,
        descRes = R.string.batch_stack_mode_stack_desc
    ),
    AUTO_STACK(
        titleRes = R.string.batch_stack_mode_auto,
        descRes = R.string.batch_stack_mode_auto_desc
    ),
    NEVER_STACK(
        titleRes = R.string.batch_stack_mode_never,
        descRes = R.string.batch_stack_mode_never_desc
    )
}

// Isolates destructive-action dialogs so the main list composable stays focused on layout.
@Composable
internal fun PasswordListDialogs(
    showManualStackConfirmDialog: Boolean,
    onShowManualStackConfirmDialogChange: (Boolean) -> Unit,
    selectedPasswords: Set<Long>,
    selectedCount: Int,
    selectedManualStackMode: ManualStackDialogMode,
    onSelectedManualStackModeChange: (ManualStackDialogMode) -> Unit,
    viewModel: PasswordViewModel,
    context: Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onDeleteSelection: () -> Int,
    onSelectionCleared: () -> Unit,
    showBatchDeleteDialog: Boolean,
    onShowBatchDeleteDialogChange: (Boolean) -> Unit,
    passwordInput: String,
    onPasswordInputChange: (String) -> Unit,
    passwordError: Boolean,
    onPasswordErrorChange: (Boolean) -> Unit,
    canUseBiometric: Boolean,
    activity: FragmentActivity?,
    biometricHelper: BiometricHelper,
    itemToDelete: PasswordEntry?,
    onItemToDeleteChange: (PasswordEntry?) -> Unit,
    appSettings: AppSettings,
    singleItemPasswordInput: String,
    onSingleItemPasswordInputChange: (String) -> Unit,
    showSingleItemPasswordVerify: Boolean,
    onShowSingleItemPasswordVerifyChange: (Boolean) -> Unit
) {
    if (showManualStackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { onShowManualStackConfirmDialogChange(false) },
            title = { Text(text = stringResource(R.string.batch_stack_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.batch_stack_confirm_message,
                            selectedPasswords.size
                        )
                    )
                    ManualStackDialogMode.values().forEach { mode ->
                        Row(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .clickable { onSelectedManualStackModeChange(mode) },
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = selectedManualStackMode == mode,
                                onClick = { onSelectedManualStackModeChange(mode) }
                            )
                            Column(modifier = androidx.compose.ui.Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = stringResource(mode.titleRes),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(mode.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val selectedIds = selectedPasswords.toList()
                            val mode = when (selectedManualStackMode) {
                                ManualStackDialogMode.STACK -> PasswordViewModel.ManualStackMode.STACK
                                ManualStackDialogMode.AUTO_STACK -> PasswordViewModel.ManualStackMode.AUTO_STACK
                                ManualStackDialogMode.NEVER_STACK -> PasswordViewModel.ManualStackMode.NEVER_STACK
                            }
                            val handledCount = viewModel.applyManualStackMode(selectedIds, mode)
                            if (handledCount > 0) {
                                val toastRes = when (selectedManualStackMode) {
                                    ManualStackDialogMode.STACK -> R.string.batch_stack_success
                                    ManualStackDialogMode.AUTO_STACK -> R.string.batch_stack_auto_success
                                    ManualStackDialogMode.NEVER_STACK -> R.string.batch_stack_never_success
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(toastRes, handledCount),
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSelectionCleared()
                            }
                            onShowManualStackConfirmDialogChange(false)
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowManualStackConfirmDialogChange(false) }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBatchDeleteDialog) {
        val biometricAction = if (canUseBiometric) {
            {
                val hostActivity = activity
                if (hostActivity == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.biometric_not_available),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    biometricHelper.authenticate(
                        activity = hostActivity,
                        title = context.getString(R.string.verify_identity),
                        subtitle = context.getString(R.string.verify_to_delete),
                        onSuccess = {
                            coroutineScope.launch {
                                val deletedCount = onDeleteSelection()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.deleted_items, deletedCount),
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSelectionCleared()
                                onPasswordInputChange("")
                                onPasswordErrorChange(false)
                                onShowBatchDeleteDialogChange(false)
                            }
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        },
                        onFailed = {}
                    )
                }
            }
        } else {
            null
        }
        M3IdentityVerifyDialog(
            title = stringResource(R.string.verify_identity),
            message = stringResource(R.string.batch_delete_passwords_message, selectedCount),
            passwordValue = passwordInput,
            onPasswordChange = {
                onPasswordInputChange(it)
                onPasswordErrorChange(false)
            },
            onDismiss = {
                onShowBatchDeleteDialogChange(false)
                onPasswordInputChange("")
                onPasswordErrorChange(false)
            },
            onConfirm = {
                if (SecurityManager(context).verifyMasterPassword(passwordInput)) {
                    coroutineScope.launch {
                        val deletedCount = onDeleteSelection()
                        Toast.makeText(
                            context,
                            context.getString(R.string.deleted_items, deletedCount),
                            Toast.LENGTH_SHORT
                        ).show()
                        onSelectionCleared()
                        onPasswordInputChange("")
                        onPasswordErrorChange(false)
                        onShowBatchDeleteDialogChange(false)
                    }
                } else {
                    onPasswordErrorChange(true)
                }
            },
            confirmText = stringResource(R.string.delete),
            destructiveConfirm = true,
            isPasswordError = passwordError,
            passwordErrorText = stringResource(R.string.current_password_incorrect),
            onBiometricClick = biometricAction,
            biometricHintText = if (biometricAction == null) {
                context.getString(R.string.biometric_not_available)
            } else {
                null
            }
        )
    }

    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemTitle = item.title,
            itemType = stringResource(R.string.item_type_password),
            biometricEnabled = appSettings.biometricEnabled,
            onDismiss = {
                onItemToDeleteChange(null)
            },
            onConfirmWithPassword = { password ->
                onSingleItemPasswordInputChange(password)
                onShowSingleItemPasswordVerifyChange(true)
            },
            onConfirmWithBiometric = {
                coroutineScope.launch {
                    viewModel.deletePasswordEntry(item)
                    Toast.makeText(
                        context,
                        context.getString(R.string.deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                    onItemToDeleteChange(null)
                }
            }
        )
    }

    if (showSingleItemPasswordVerify && itemToDelete != null) {
        val pendingDeleteItem = itemToDelete
        LaunchedEffect(pendingDeleteItem.id, showSingleItemPasswordVerify) {
            val securityManager = SecurityManager(context)
            if (securityManager.verifyMasterPassword(singleItemPasswordInput)) {
                viewModel.deletePasswordEntry(pendingDeleteItem)
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.current_password_incorrect),
                    Toast.LENGTH_SHORT
                ).show()
            }
            onItemToDeleteChange(null)
            onSingleItemPasswordInputChange("")
            onShowSingleItemPasswordVerifyChange(false)
        }
    }
}

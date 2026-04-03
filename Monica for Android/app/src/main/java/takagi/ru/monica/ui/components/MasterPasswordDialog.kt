package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

// ============================================
// 🔐 主密码验证对话框
// ============================================
@Composable
fun MasterPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isError: Boolean
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.verify_identity)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.verify_master_password_desc))
                
                MasterPasswordTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        // Clear error when user types
                        // Note: To implement this properly, we'd need to pass a callback to clear error state
                    },
                    label = { Text(stringResource(R.string.master_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    visible = passwordVisible,
                    onVisibilityChange = { passwordVisible = it },
                    imeAction = ImeAction.Done,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.wrong_password)) }
                    } else null,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

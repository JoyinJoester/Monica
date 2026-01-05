package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.ui.icons.MonicaIcons

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
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        // Clear error when user types
                        // Note: To implement this properly, we'd need to pass a callback to clear error state
                    },
                    label = { Text(stringResource(R.string.master_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.wrong_password)) }
                    } else null,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) 
                                    MonicaIcons.Security.visibilityOff 
                                else 
                                    MonicaIcons.Security.visibility,
                                contentDescription = if (passwordVisible) 
                                    stringResource(R.string.hide) 
                                else 
                                    stringResource(R.string.show)
                            )
                        }
                    },
                    singleLine = true
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

package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    securityManager: SecurityManager,
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit,
    skipCurrentPassword: Boolean = false
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.reset_password_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Description Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (skipCurrentPassword) 
                            context.getString(R.string.reset_password_verified_description)
                        else 
                            context.getString(R.string.reset_password_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            // Current Password (only show if not skipping)
            if (!skipCurrentPassword) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { 
                        currentPassword = it
                        errorMessage = ""
                    },
                    label = { 
                        Text(
                            text = context.getString(R.string.current_password),
                            maxLines = 2,
                            style = MaterialTheme.typography.bodySmall
                        ) 
                    },
                    placeholder = { Text(context.getString(R.string.enter_current_password)) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                            Icon(
                                imageVector = if (currentPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (currentPasswordVisible) 
                                    context.getString(R.string.hide_password) 
                                else 
                                    context.getString(R.string.show_password)
                            )
                        }
                    },
                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true
                )
                
                HorizontalDivider()
            }
            
            // New Password
            OutlinedTextField(
                value = newPassword,
                onValueChange = { input ->
                    // 只允许数字输入
                    if (input.all { it.isDigit() }) {
                        newPassword = input
                        errorMessage = ""
                    } else if (input.isNotEmpty()) {
                        // 检测到非数字,显示错误
                        errorMessage = context.getString(R.string.error_password_must_be_numeric)
                    }
                },
                label = { 
                    Text(
                        text = context.getString(R.string.new_password),
                        maxLines = 2,
                        style = MaterialTheme.typography.bodySmall
                    ) 
                },
                placeholder = { Text(context.getString(R.string.enter_new_password)) },
                leadingIcon = {
                    Icon(Icons.Default.VpnKey, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(
                            imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (newPasswordVisible) 
                                context.getString(R.string.hide_password) 
                            else 
                                context.getString(R.string.show_password)
                        )
                    }
                },
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                isError = newPassword.isNotEmpty() && newPassword.length < 4
            )
            
            if (newPassword.isNotEmpty() && newPassword.length < 4) {
                Text(
                    text = context.getString(R.string.password_too_short),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            
            // Confirm New Password
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { input ->
                    // 只允许数字输入
                    if (input.all { it.isDigit() }) {
                        confirmPassword = input
                        errorMessage = ""
                    } else if (input.isNotEmpty()) {
                        // 检测到非数字,显示错误
                        errorMessage = context.getString(R.string.error_password_must_be_numeric)
                    }
                },
                label = { 
                    Text(
                        text = context.getString(R.string.confirm_new_password),
                        maxLines = 2,
                        style = MaterialTheme.typography.bodySmall
                    ) 
                },
                placeholder = { Text(context.getString(R.string.enter_confirm_password)) },
                leadingIcon = {
                    Icon(Icons.Default.VpnKey, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (confirmPasswordVisible) 
                                context.getString(R.string.hide_password) 
                            else 
                                context.getString(R.string.show_password)
                        )
                    }
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
            )
            
            if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
                Text(
                    text = context.getString(R.string.passwords_do_not_match),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            
            // Error Message
            if (errorMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reset Password Button
            Button(
                onClick = {
                    when {
                        !skipCurrentPassword && currentPassword.isEmpty() -> {
                            errorMessage = context.getString(R.string.current_password_required)
                        }
                        newPassword.isEmpty() -> {
                            errorMessage = context.getString(R.string.new_password_required)
                        }
                        newPassword.length < 4 -> {
                            errorMessage = context.getString(R.string.password_too_short)
                        }
                        newPassword != confirmPassword -> {
                            errorMessage = context.getString(R.string.passwords_do_not_match)
                        }
                        else -> {
                            isLoading = true
                            errorMessage = ""
                            
                            val resetSuccess = if (skipCurrentPassword) {
                                // If skipping current password, directly set new password
                                securityManager.setMasterPassword(newPassword)
                                true
                            } else {
                                // Normal reset with current password verification
                                securityManager.resetMasterPassword(currentPassword, newPassword)
                            }
                            
                            if (resetSuccess) {
                                showSuccessDialog = true
                            } else {
                                errorMessage = context.getString(R.string.current_password_incorrect)
                            }
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && 
                         (skipCurrentPassword || currentPassword.isNotEmpty()) && 
                         newPassword.isNotEmpty() && 
                         confirmPassword.isNotEmpty()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(context.getString(R.string.reset_password))
            }
        }
    }
    
    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSuccessDialog = false
                onResetSuccess()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(context.getString(R.string.password_reset_success))
            },
            text = {
                Text(context.getString(R.string.password_reset_success_message))
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showSuccessDialog = false
                        onResetSuccess()
                    }
                ) {
                    Text(context.getString(R.string.ok))
                }
            }
        )
    }
}
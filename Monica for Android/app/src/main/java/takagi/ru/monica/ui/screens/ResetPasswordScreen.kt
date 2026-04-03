package takagi.ru.monica.ui.screens

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.security.MasterPasswordPolicy
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.MasterPasswordTextField
import takagi.ru.monica.ui.components.rememberBringIntoViewOnFocusModifier

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ResetPasswordScreen(
    securityManager: SecurityManager,
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit,
    skipCurrentPassword: Boolean = false
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val currentPasswordImeModifier = rememberBringIntoViewOnFocusModifier()
    val newPasswordImeModifier = rememberBringIntoViewOnFocusModifier()
    val confirmPasswordImeModifier = rememberBringIntoViewOnFocusModifier()
    
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    


    // 准备共享元素 Modifier
    val sharedTransitionScope = takagi.ru.monica.ui.LocalSharedTransitionScope.current
    val animatedVisibilityScope = takagi.ru.monica.ui.LocalAnimatedVisibilityScope.current
    
    var sharedModifier: Modifier = Modifier
    if (false && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope!!) {
            sharedModifier = Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "reset_password_card"),
                animatedVisibilityScope = animatedVisibilityScope!!,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
            )
        }
    }

    Scaffold(
        modifier = sharedModifier,
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
                .imePadding()
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
                MasterPasswordTextField(
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
                    visible = currentPasswordVisible,
                    onVisibilityChange = { currentPasswordVisible = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(currentPasswordImeModifier),
                    enabled = !isLoading,
                )
                
                HorizontalDivider()
            }
            
            // New Password
            MasterPasswordTextField(
                value = newPassword,
                onValueChange = { input ->
                    newPassword = input
                    errorMessage = ""
                },
                onUnsupportedCharacterAttempt = {
                    errorMessage = context.getString(R.string.error_password_contains_unsupported_characters)
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
                visible = newPasswordVisible,
                onVisibilityChange = { newPasswordVisible = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(newPasswordImeModifier),
                enabled = !isLoading,
                isError = newPassword.isNotEmpty() && !MasterPasswordPolicy.meetsMinLength(newPassword)
            )
            
            if (newPassword.isNotEmpty() && !MasterPasswordPolicy.meetsMinLength(newPassword)) {
                Text(
                    text = context.getString(R.string.password_too_short),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            
            // Confirm New Password
            MasterPasswordTextField(
                value = confirmPassword,
                onValueChange = { input ->
                    confirmPassword = input
                    errorMessage = ""
                },
                onUnsupportedCharacterAttempt = {
                    errorMessage = context.getString(R.string.error_password_contains_unsupported_characters)
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
                visible = confirmPasswordVisible,
                onVisibilityChange = { confirmPasswordVisible = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(confirmPasswordImeModifier),
                enabled = !isLoading,
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
                        !MasterPasswordPolicy.meetsMinLength(newPassword) -> {
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


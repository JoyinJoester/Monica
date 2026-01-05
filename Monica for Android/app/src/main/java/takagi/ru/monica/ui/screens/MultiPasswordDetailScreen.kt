package takagi.ru.monica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.ui.components.ActionStrip
import takagi.ru.monica.ui.components.ActionStripItem
import takagi.ru.monica.ui.components.MasterPasswordDialog
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.utils.BiometricHelper
import takagi.ru.monica.viewmodel.PasswordViewModel

/**
 * 多密码详情页 (Multi-Password Detail Screen)
 * Refactored from Dialog to Full-Screen Page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPasswordDetailScreen(
    viewModel: PasswordViewModel,
    passwordId: Long,
    onNavigateBack: () -> Unit,
    onEditPassword: (Long) -> Unit,
    onAddPassword: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allPasswords by viewModel.passwordEntries.collectAsState(initial = emptyList())
    var primaryEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    
    // Load primary entry
    LaunchedEffect(passwordId) {
        primaryEntry = viewModel.getPasswordEntryById(passwordId)
    }
    
    // Filter grouped passwords
    val groupPasswords = remember(allPasswords, primaryEntry) {
        if (primaryEntry == null) emptyList()
        else {
            val key = getPasswordInfoKey(primaryEntry!!)
            allPasswords.filter { getPasswordInfoKey(it) == key }
        }
    }
    
    if (primaryEntry == null && allPasswords.isNotEmpty()) {
        // Handle case where item might have been deleted while on screen
        // or initial load haven't finished? 
        // If it's empty and we have data, maybe it's gone.
    }

    // Deletion State
    var itemToDelete by remember { mutableStateOf<PasswordEntry?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMasterPasswordDialog by remember { mutableStateOf(false) }
    var passwordVerificationError by remember { mutableStateOf(false) }
    
    val biometricHelper = remember { BiometricHelper(context) }
    
    // Logic to perform actual deletion after verification
    fun performDeletion() {
        itemToDelete?.let { entry ->
            viewModel.deletePasswordEntry(entry)
            itemToDelete = null
            showDeleteConfirmDialog = false
            showMasterPasswordDialog = false
            
            // If it was the last one, go back
            if (groupPasswords.size <= 1) {
                onNavigateBack()
            }
        }
    }
    
    // Verification Flow
    fun startVerification() {
        showDeleteConfirmDialog = false 
        
        if (biometricHelper.isBiometricAvailable()) {
            (context as? FragmentActivity)?.let { activity ->
                biometricHelper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.verify_identity_to_delete),
                    onSuccess = { performDeletion() },
                    onError = { showMasterPasswordDialog = true },
                    onFailed = { showMasterPasswordDialog = true }
                )
            } ?: run {
                showMasterPasswordDialog = true
            }
        } else {
            showMasterPasswordDialog = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(primaryEntry?.title ?: stringResource(R.string.password_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = MonicaIcons.Navigation.back,
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
            ActionStrip(
                actions = listOf(
                    ActionStripItem(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_password),
                        onClick = { onAddPassword(passwordId) }, // Pass ID to know common info
                        tint = MaterialTheme.colorScheme.primary
                    )
                )
            )
        }
    ) { paddingValues ->
        if (groupPasswords.isNotEmpty()) {
            val firstEntry = groupPasswords.first()
            
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // ==========================================
                // 🎯 Header Section (Centered)
                // ==========================================
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MonicaIcons.Security.lock,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Text(
                            text = firstEntry.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        
                        if (firstEntry.website.isNotEmpty()) {
                            Text(
                                text = firstEntry.website,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // ==========================================
                // 📝 Common Info Card
                // ==========================================
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "📝 " + stringResource(R.string.common_info),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (firstEntry.website.isNotEmpty()) {
                                InfoItem(label = stringResource(R.string.label_website), value = firstEntry.website)
                            }
                            if (firstEntry.username.isNotEmpty()) {
                                InfoItem(label = stringResource(R.string.label_username), value = firstEntry.username)
                            }
                            if (firstEntry.notes.isNotEmpty()) {
                                InfoItem(label = stringResource(R.string.label_notes), value = firstEntry.notes)
                            }
                            if (firstEntry.appName.isNotEmpty()) {
                                InfoItem(label = stringResource(R.string.linked_app), value = firstEntry.appName)
                            }
                        }
                    }
                }
                
                // ==========================================
                // 🔑 Password List Header
                // ==========================================
                item {
                    Text(
                        text = stringResource(R.string.password_list, groupPasswords.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                // ==========================================
                // 🔑 Passwords
                // ==========================================
                itemsIndexed(groupPasswords) { index, password ->
                    PasswordItemCardRefactored(
                        password = password,
                        index = index + 1,
                        onEdit = { onEditPassword(password.id) },
                        onDelete = { 
                            itemToDelete = password
                            showDeleteConfirmDialog = true
                        },
                        onToggleFavorite = { 
                            viewModel.toggleFavorite(password.id, !password.isFavorite)
                        },
                        onClick = { /* Could auto-copy or just stay here */ }
                    )
                }
                
                // Bottom Spacer
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        } else {
            // Loading or Empty State
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.delete_password_title)) },
            text = { Text(stringResource(R.string.delete_password_message, itemToDelete?.title ?: "")) },
            confirmButton = {
                TextButton(
                    onClick = { startVerification() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Master Password Verification Dialog
    if (showMasterPasswordDialog) {
        MasterPasswordDialog(
            onDismiss = { 
                showMasterPasswordDialog = false
                passwordVerificationError = false
            },
            onConfirm = { inputPassword ->
                if (viewModel.verifyMasterPassword(inputPassword)) {
                     performDeletion()
                } else {
                    passwordVerificationError = true
                }
            },
            isError = passwordVerificationError
        )
    }
}

private fun getPasswordInfoKey(entry: PasswordEntry): String {
    return "${entry.title}|${entry.website}|${entry.username}|${entry.notes}|${entry.appPackageName}|${entry.appName}"
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PasswordItemCardRefactored(
    password: PasswordEntry,
    index: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (password.isFavorite) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.password_item_title, index),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Actions
                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (password.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.favorite),
                            tint = if (password.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            // Password & Copy Row
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (passwordVisible) password.password else "••••••••",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) stringResource(R.string.hide) else stringResource(R.string.show)
                            )
                        }
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("password", password.password)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, context.getString(R.string.password_copied), android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                        }
                    }
                }
            }
        }
    }
}

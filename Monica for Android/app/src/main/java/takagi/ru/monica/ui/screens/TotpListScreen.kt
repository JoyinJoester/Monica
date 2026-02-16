package takagi.ru.monica.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.ui.components.TotpCodeCard
import takagi.ru.monica.ui.components.QrCodeDialog
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.utils.SettingsManager
import kotlinx.coroutines.delay

/**
 * TOTP验证器列表页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpListScreen(
    totpItems: List<SecureItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onItemClick: (Long) -> Unit,
    onDeleteItem: (SecureItem) -> Unit,
    onGenerateNext: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val settings by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
    val sharedTickSeconds by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            value = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    var itemToDelete by remember { mutableStateOf<SecureItem?>(null) }
    var itemToShowQr by remember { mutableStateOf<SecureItem?>(null) }
    
    Column(modifier = modifier.fillMaxSize()) {
        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text(stringResource(R.string.search_authenticator)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        )
        
        // TOTP列表
        if (totpItems.isEmpty()) {
            // 空状态
            EmptyTotpState(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = totpItems,
                    key = { it.id }
                ) { item ->
                    TotpCodeCard(
                        item = item,
                        onCopyCode = { code ->
                            copyToClipboard(context, code)
                            Toast.makeText(context, context.getString(R.string.verification_code_copied), Toast.LENGTH_SHORT).show()
                        },
                        onEdit = { onItemClick(item.id) },
                        onDelete = {
                            itemToDelete = item
                        },
                        onGenerateNext = onGenerateNext,
                        onShowQrCode = {
                            itemToShowQr = item
                        },
                        allowVibration = true,
                        sharedTickSeconds = sharedTickSeconds,
                        appSettings = settings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
    
    // QR码对话框
    itemToShowQr?.let { item ->
        QrCodeDialog(
            item = item,
            onDismiss = { itemToShowQr = null }
        )
    }

    // 删除确认对话框
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.delete_authenticator_title)) },
            text = { Text(stringResource(R.string.delete_authenticator_message, item.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItem(item)
                        itemToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 空状态提示
 */
@Composable
private fun EmptyTotpState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.no_authenticators_title),
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.no_authenticators_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 复制到剪贴板
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("TOTP Code", text)
    clipboard.setPrimaryClip(clip)
}

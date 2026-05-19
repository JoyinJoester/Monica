package takagi.ru.monica.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.model.WifiData
import takagi.ru.monica.data.model.WifiSecurity
import takagi.ru.monica.data.model.toStorageTarget
import takagi.ru.monica.ui.components.CustomFieldDetailCard
import takagi.ru.monica.ui.components.PasswordFieldActionMenuHost
import takagi.ru.monica.ui.components.TextQrCodeDialog
import takagi.ru.monica.ui.components.rememberPasswordFieldActionMenuState
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.utils.ClipboardUtils
import takagi.ru.monica.utils.WifiConnectLauncher
import takagi.ru.monica.utils.WifiQrPayload
import takagi.ru.monica.viewmodel.PasswordViewModel

/**
 * WIFI 详情页（精简版）：SSID、安全性、密码（默认隐藏）、隐藏网络、存储位置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDetailScreen(
    viewModel: PasswordViewModel,
    passwordId: Long,
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCreateSend: ((title: String, text: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var entry by remember { mutableStateOf<PasswordEntry?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }
    var customFields by remember { mutableStateOf<List<takagi.ru.monica.data.CustomField>>(emptyList()) }

    LaunchedEffect(passwordId) {
        entry = viewModel.getPasswordEntryById(passwordId)
        customFields = viewModel.getCustomFieldsByEntryIdSync(passwordId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        entry?.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.wifi_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MonicaIcons.Navigation.back, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    entry?.let { current ->
                        val wifiForQr = remember(current.wifiMetadata) {
                            WifiData.fromJsonOrEmpty(current.wifiMetadata)
                        }
                        val qrSupported = WifiQrPayload.build(wifiForQr, current.password) != null
                        IconButton(
                            enabled = qrSupported,
                            onClick = { showQrDialog = true }
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = stringResource(R.string.wifi_qr_button))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            entry?.let { current ->
                val wifiSnapshot = remember(current.wifiMetadata) {
                    WifiData.fromJsonOrEmpty(current.wifiMetadata)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(onClick = { onEdit(current.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            val result = WifiConnectLauncher.launch(context, wifiSnapshot, current.password)
                            if (result is WifiConnectLauncher.Result.Failed) {
                                Toast.makeText(context, context.getString(R.string.wifi_connect_failed), Toast.LENGTH_LONG).show()
                            }
                        },
                        icon = { Icon(Icons.Default.NetworkWifi, contentDescription = null) },
                        text = { Text(stringResource(R.string.wifi_connect_button)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val current = entry
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.loading)) }
            return@Scaffold
        }

        val wifi = WifiData.fromJsonOrEmpty(current.wifiMetadata)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Column {
                        Text(
                            wifi.ssid.ifBlank { current.title },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                        Text(
                            securityLabel(wifi.security),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 详情卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SSID
                    DetailRow(
                        label = stringResource(R.string.wifi_ssid_label),
                        value = wifi.ssid.ifBlank { current.title },
                        context = context,
                        onCreateSend = onCreateSend
                    )

                    // 安全性
                    DetailRow(
                        label = stringResource(R.string.wifi_security_label),
                        value = securityLabel(wifi.security),
                        copyLabel = null,
                        context = context,
                        onCreateSend = onCreateSend
                    )

                    // 密码
                    if (wifi.security != WifiSecurity.NONE && current.password.isNotEmpty()) {
                        SecretRow(
                            label = stringResource(R.string.wifi_password_label),
                            value = current.password,
                            copyLabel = stringResource(R.string.wifi_detail_copy_password),
                            context = context,
                            onCreateSend = onCreateSend
                        )
                    }

                    // 隐藏网络
                    if (wifi.hiddenNetwork) {
                        DetailRow(
                            label = stringResource(R.string.wifi_hidden_network),
                            value = "✓",
                            copyLabel = null,
                            context = context,
                            onCreateSend = onCreateSend
                        )
                    }

                    // 存储位置
                    val storageLabel = remember(current) {
                        when {
                            current.bitwardenVaultId != null -> "Bitwarden"
                            current.keepassDatabaseId != null -> "KeePass"
                            else -> "Monica"
                        }
                    }
                    DetailRow(
                        label = stringResource(R.string.wifi_detail_storage),
                        value = storageLabel,
                        copyLabel = null,
                        context = context,
                        onCreateSend = onCreateSend
                    )
                }
            }

            // 自定义字段
            if (customFields.isNotEmpty()) {
                customFields.forEach { field ->
                    CustomFieldDetailCard(
                        field = field,
                        onCopy = { title ->
                            Toast.makeText(context, title, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // QR 对话框
    if (showQrDialog) {
        val snapshotEntry = entry
        val qrContent = remember(snapshotEntry?.wifiMetadata, snapshotEntry?.password) {
            if (snapshotEntry == null) null
            else WifiQrPayload.build(WifiData.fromJsonOrEmpty(snapshotEntry.wifiMetadata), snapshotEntry.password)
        }
        if (qrContent != null && snapshotEntry != null) {
            val ssidForTitle = WifiData.fromJsonOrEmpty(snapshotEntry.wifiMetadata).ssid.ifBlank { snapshotEntry.title }
            TextQrCodeDialog(
                title = stringResource(R.string.wifi_qr_dialog_title, ssidForTitle),
                content = qrContent,
                saveFileBaseName = ssidForTitle.ifBlank { "wifi" },
                onDismiss = { showQrDialog = false }
            )
        } else {
            LaunchedEffect(Unit) {
                showQrDialog = false
                Toast.makeText(context, context.getString(R.string.wifi_qr_unsupported), Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun securityLabel(security: WifiSecurity): String = when (security) {
    WifiSecurity.NONE -> stringResource(R.string.wifi_security_none)
    WifiSecurity.WEP -> stringResource(R.string.wifi_security_wep)
    WifiSecurity.WPA_WPA2 -> stringResource(R.string.wifi_security_wpa_wpa2)
    WifiSecurity.WPA2_WPA3 -> stringResource(R.string.wifi_security_wpa2_wpa3)
    WifiSecurity.WPA3 -> stringResource(R.string.wifi_security_wpa3)
    WifiSecurity.WPA2_ENTERPRISE -> stringResource(R.string.wifi_security_wpa2_enterprise)
    WifiSecurity.WPA3_ENTERPRISE -> stringResource(R.string.wifi_security_wpa3_enterprise)
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    context: Context,
    copyLabel: String? = label,
    onCreateSend: ((title: String, text: String) -> Unit)? = null
) {
    val actionMenuState = rememberPasswordFieldActionMenuState()
    val menuEnabled = copyLabel != null && value.isNotBlank()

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = menuEnabled) { actionMenuState.open() }
                    .padding(vertical = 6.dp)
            ) {
                if (menuEnabled) {
                    PasswordFieldActionMenuHost(
                        state = actionMenuState,
                        label = label,
                        value = value,
                        displayValue = value,
                        context = context,
                        onCreateSend = onCreateSend
                    )
                }
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (copyLabel != null && value.isNotBlank()) {
            IconButton(onClick = { copyToClipboard(context, label, value) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = copyLabel)
            }
        }
    }
}

@Composable
private fun SecretRow(
    label: String,
    value: String,
    context: Context,
    copyLabel: String,
    onCreateSend: ((title: String, text: String) -> Unit)? = null
) {
    var revealed by remember { mutableStateOf(false) }
    val actionMenuState = rememberPasswordFieldActionMenuState()
    val displayValue = if (revealed) value else "••••••••"

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { actionMenuState.open() }
                    .padding(vertical = 6.dp)
            ) {
                PasswordFieldActionMenuHost(
                    state = actionMenuState,
                    label = label,
                    value = value,
                    displayValue = displayValue,
                    context = context,
                    includeVisibilityToggle = true,
                    isVisible = revealed,
                    onToggleVisibility = { revealed = !revealed },
                    onCreateSend = onCreateSend
                )
                Text(displayValue, style = MaterialTheme.typography.bodyLarge)
            }
        }
        IconButton(onClick = { revealed = !revealed }) {
            Icon(if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
        }
        IconButton(onClick = { copyToClipboard(context, label, value) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = copyLabel)
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    ClipboardUtils.copyToClipboard(context, value, label)
    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
}

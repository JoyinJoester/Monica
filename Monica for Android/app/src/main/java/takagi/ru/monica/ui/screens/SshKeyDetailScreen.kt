package takagi.ru.monica.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.model.SshKeyData
import takagi.ru.monica.data.model.SshKeyDataCodec
import takagi.ru.monica.ui.components.CustomFieldDetailCard
import takagi.ru.monica.ui.components.PasswordFieldActionMenuHost
import takagi.ru.monica.ui.components.rememberPasswordFieldActionMenuState
import takagi.ru.monica.ui.icons.MonicaIcons
import takagi.ru.monica.utils.ClipboardUtils
import takagi.ru.monica.viewmodel.PasswordViewModel

/**
 * SSH 密钥详情页。
 *
 * 展示算法、位数、指纹、公钥、私钥（默认遮罩）与自定义字段。编辑入口直接跳
 * [AddEditSshKeyScreen]。私钥的「显示 / 复制」按钮由用户直接触发，不再额外
 * 弹出主密码对话框；主应用入口已有生物/密码锁。如需二次确认，可在后续迭代
 * 中接入 `PasswordDetailDialog` 的授权策略。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshKeyDetailScreen(
    viewModel: PasswordViewModel,
    passwordId: Long,
    onNavigateBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCreateSend: ((title: String, text: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var entry by remember { mutableStateOf<PasswordEntry?>(null) }
    var customFields by remember { mutableStateOf<List<CustomField>>(emptyList()) }
    var privateKeyRevealed by remember { mutableStateOf(false) }

    LaunchedEffect(passwordId) {
        entry = viewModel.getPasswordEntryById(passwordId)
        customFields = viewModel.getCustomFieldsByEntryIdSync(passwordId)
    }

    val sshData = remember(entry?.sshKeyData) {
        entry?.sshKeyData?.let(SshKeyDataCodec::decode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = entry?.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.edit_ssh_key_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            MonicaIcons.Navigation.back,
                            contentDescription = stringResource(R.string.back)
                        )
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
            ExtendedFloatingActionButton(
                onClick = { onEdit(passwordId) },
                icon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                },
                text = {
                    Text(stringResource(R.string.edit_ssh_key_title))
                }
            )
        }
    ) { innerPadding ->
        if (sshData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.ssh_key_empty_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        SshKeyDetailBody(
            innerPadding = innerPadding,
            data = sshData,
            privateKeyRevealed = privateKeyRevealed,
            onTogglePrivateKeyVisibility = { privateKeyRevealed = !privateKeyRevealed },
            onCopy = { label, text -> copyTextToClipboardLocal(context, label, text) },
            customFields = customFields,
            onCreateSend = onCreateSend
        )
    }
}

@Composable
private fun SshKeyDetailBody(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    data: SshKeyData,
    privateKeyRevealed: Boolean,
    onTogglePrivateKeyVisibility: () -> Unit,
    onCopy: (label: String, text: String) -> Unit,
    customFields: List<CustomField>,
    onCreateSend: ((title: String, text: String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeaderCard(data = data)

        val copyFingerprintLabel = stringResource(R.string.ssh_key_copy_fingerprint)
        val copyPublicLabel = stringResource(R.string.ssh_key_copy_public)
        val copyPrivateLabel = stringResource(R.string.ssh_key_copy_private)

        SshFieldCard(
            label = stringResource(R.string.ssh_key_fingerprint),
            value = data.fingerprintSha256,
            monospace = true,
            onCopy = { onCopy(copyFingerprintLabel, data.fingerprintSha256) },
            onCreateSend = onCreateSend
        )

        SshFieldCard(
            label = stringResource(R.string.ssh_key_public_key),
            value = data.publicKeyOpenSsh,
            monospace = true,
            onCopy = { onCopy(copyPublicLabel, data.publicKeyOpenSsh) },
            onCreateSend = onCreateSend
        )

        SshPrivateKeyCard(
            value = data.privateKeyOpenSsh,
            revealed = privateKeyRevealed,
            onToggleVisibility = onTogglePrivateKeyVisibility,
            onCopy = { onCopy(copyPrivateLabel, data.privateKeyOpenSsh) },
            onCreateSend = onCreateSend
        )

        if (data.comment.isNotBlank()) {
            SshFieldCard(
                label = stringResource(R.string.ssh_key_comment),
                value = data.comment,
                monospace = false,
                onCopy = null,
                onCreateSend = onCreateSend
            )
        }

        if (customFields.isNotEmpty()) {
            customFields.forEach { field ->
                CustomFieldDetailCard(
                    field = field,
                    onCopy = { /* no-op: detail card manages its own copy toast */ }
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun HeaderCard(data: SshKeyData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.algorithm.ifBlank { stringResource(R.string.ssh_key_algorithm_rsa) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (data.keySize > 0) {
                    Text(
                        text = "${data.keySize} bits",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SshFieldCard(
    label: String,
    value: String,
    monospace: Boolean,
    onCopy: (() -> Unit)?,
    onCreateSend: ((title: String, text: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val actionMenuState = rememberPasswordFieldActionMenuState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (onCopy != null) {
                    IconButton(onClick = onCopy) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.copy)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = value.isNotBlank()) { actionMenuState.open() }
                    .padding(vertical = 6.dp)
            ) {
                if (value.isNotBlank()) {
                    PasswordFieldActionMenuHost(
                        state = actionMenuState,
                        label = label,
                        value = value,
                        displayValue = value,
                        context = context,
                        onCreateSend = onCreateSend
                    )
                }
                SelectionContainer {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}

@Composable
private fun SshPrivateKeyCard(
    value: String,
    revealed: Boolean,
    onToggleVisibility: () -> Unit,
    onCopy: () -> Unit,
    onCreateSend: ((title: String, text: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val actionMenuState = rememberPasswordFieldActionMenuState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ssh_key_private_key),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(
                            if (revealed) R.string.ssh_key_hide_private
                            else R.string.ssh_key_reveal_private
                        )
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.ssh_key_copy_private)
                    )
                }
            }
            val displayValue = if (revealed) {
                value
            } else {
                "•".repeat(24)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = value.isNotBlank()) { actionMenuState.open() }
                    .padding(vertical = 6.dp)
            ) {
                if (value.isNotBlank()) {
                    PasswordFieldActionMenuHost(
                        state = actionMenuState,
                        label = stringResource(R.string.ssh_key_private_key),
                        value = value,
                        displayValue = displayValue,
                        context = context,
                        includeVisibilityToggle = true,
                        isVisible = revealed,
                        onToggleVisibility = onToggleVisibility,
                        onCreateSend = onCreateSend
                    )
                }
                SelectionContainer {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun copyTextToClipboardLocal(context: Context, label: String, text: String) {
    ClipboardUtils.copyToClipboard(context, text, label)
    Toast.makeText(
        context,
        context.getString(R.string.copied_to_clipboard),
        Toast.LENGTH_SHORT
    ).show()
}

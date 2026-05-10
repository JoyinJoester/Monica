package takagi.ru.monica.attachments.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.attachments.AttachmentContainer
import takagi.ru.monica.attachments.model.Attachment
import takagi.ru.monica.attachments.model.AttachmentDownloadState
import takagi.ru.monica.attachments.model.AttachmentError
import takagi.ru.monica.attachments.model.AttachmentSource

/**
 * 密码详情页的附件区块（只读 + 下载/打开）。
 *
 * 当前实现限定为 `LOCAL` 附件：
 * - Bitwarden/KeePass 附件需要调用方提供 context（token/kdbx 数据库 id 等），
 *   未来在密码列表/详情屏支持时可扩展 `BitwardenContext` / `KeePassContext` 参数。
 * - 无附件时不渲染，避免给没有附件的密码造成视觉噪音。
 *
 * 对应 requirements.md Requirement 7.1 / 7.2（基础形态）。
 */
@Composable
fun AttachmentsDetailSection(
    passwordId: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val facade = remember(context) { AttachmentContainer.facade(context) }
    val attachments by facade.observeByPassword(passwordId).collectAsState(initial = emptyList())

    if (attachments.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.attachments_section_title, attachments.size),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            attachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    onOpen = {
                        scope.launch {
                            runCatching {
                                val uri = facade.openForPreview(attachment.id)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, attachment.mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }.onFailure { e ->
                                Toast.makeText(
                                    context,
                                    resolveErrorMessage(context, e),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onRetry = {
                        scope.launch {
                            runCatching { facade.retryFailed(attachment.id) }
                                .onFailure { e ->
                                    Toast.makeText(
                                        context,
                                        resolveErrorMessage(context, e),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: Attachment,
    onOpen: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = attachment.downloadStateEnum == AttachmentDownloadState.DOWNLOADED) {
                onOpen()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatSecondary(attachment),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when (attachment.downloadStateEnum) {
            AttachmentDownloadState.DOWNLOADING ->
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            AttachmentDownloadState.PENDING ->
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            AttachmentDownloadState.FAILED ->
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            AttachmentDownloadState.DOWNLOADED -> Unit
        }
    }
}

private fun formatSecondary(attachment: Attachment): String {
    val sizeKb = (attachment.sizeBytes + 1023) / 1024
    val sizeText = when {
        attachment.sizeBytes <= 0 -> ""
        sizeKb >= 1024 -> "${sizeKb / 1024} MB"
        else -> "$sizeKb KB"
    }
    val sourceLabel = when (attachment.sourceEnum) {
        AttachmentSource.LOCAL -> "Local"
        AttachmentSource.BITWARDEN -> "Bitwarden"
        AttachmentSource.KEEPASS -> "KeePass"
    }
    return if (sizeText.isBlank()) sourceLabel else "$sourceLabel · $sizeText"
}

private fun resolveErrorMessage(context: android.content.Context, e: Throwable): String {
    val resId = when (e) {
        is AttachmentError.TooLarge -> R.string.attachment_error_too_large
        AttachmentError.QuotaExceeded -> R.string.attachment_error_quota_exceeded
        AttachmentError.PremiumRequired -> R.string.attachment_error_premium_required
        AttachmentError.Offline -> R.string.attachment_error_offline
        is AttachmentError.NetworkError -> R.string.attachment_error_network
        AttachmentError.CryptoError -> R.string.attachment_error_crypto
        AttachmentError.IoError -> R.string.attachment_error_io
        AttachmentError.KdbxLocked -> R.string.attachment_error_kdbx_locked
        AttachmentError.KdbxCapacityExceeded -> R.string.attachment_error_kdbx_capacity
        else -> R.string.attachment_error_io
    }
    return when (e) {
        is AttachmentError.TooLarge -> context.getString(resId, formatSizeHuman(e.limitBytes))
        is AttachmentError.NetworkError -> context.getString(resId, e.httpStatus?.toString() ?: "-")
        else -> context.getString(resId)
    }
}

private fun formatSizeHuman(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / (1024 * 1024)
    return if (mb >= 1) "${mb} MB" else "${bytes / 1024} KB"
}

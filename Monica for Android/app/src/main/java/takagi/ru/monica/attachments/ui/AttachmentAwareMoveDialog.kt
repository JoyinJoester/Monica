package takagi.ru.monica.attachments.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.attachments.facade.AttachmentBatchMoveAdvisor

/**
 * Bitwarden 免费账户 + 带附件条目的批量移动确认对话框。
 *
 * 对应 requirements.md Requirement 8.4：
 * - 标题：`attachment_move_dialog_title`
 * - 正文：`attachment_move_dialog_message` 组合 (total, withAttachments, withoutAttachments)
 *   阐明"带附件条目仅复制、不带附件条目正常移动"
 * - 可展开"查看详情"（`attachment_move_dialog_details`）显示带附件条目的标题
 * - 按钮：确定 / 取消
 */
@Composable
fun AttachmentAwareMoveDialog(
    classification: AttachmentBatchMoveAdvisor.Classification,
    attachmentItemTitles: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val total = classification.totalSelected
    val withAttachments = classification.copyInsteadOfMove.size
    val withoutAttachments = classification.plainMove.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.attachment_move_dialog_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.attachment_move_dialog_message,
                        total,
                        withAttachments,
                        withoutAttachments
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (attachmentItemTitles.isNotEmpty()) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.attachment_move_dialog_details))
                    }
                    if (expanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(attachmentItemTitles, key = { it }) { title ->
                                Text(
                                    text = "• $title",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.attachment_move_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.attachment_move_dialog_cancel))
            }
        }
    )
}

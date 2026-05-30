package takagi.ru.monica.ui.screens

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 导入类型元数据。
 *
 * 这里仍保留现有 string key，避免本次拆分改变导入分发逻辑。
 */
data class ImportTypeInfo(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val fileHint: String
)

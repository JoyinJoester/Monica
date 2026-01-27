package takagi.ru.monica.utils

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * Saver for SnapshotStateList<String> to support rememberSaveable
 * 用于在配置变化（如屏幕旋转）时保存和恢复字符串列表状态
 */
val StringListSaver: Saver<SnapshotStateList<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it.toList()) },
    restore = { it.toMutableStateList() }
)

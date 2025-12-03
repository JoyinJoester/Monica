package takagi.ru.monica.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class PlusFeature(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val isAvailable: Boolean = true
)

object PlusFeatures {
    fun getPlaceholderFeatures(): List<PlusFeature> = listOf(
        PlusFeature(
            id = "webdav_backup",
            icon = Icons.Default.Cloud,
            title = "WebDAV 云备份",
            description = "跨设备同步您的数据，支持自动备份和手动恢复",
            isAvailable = true
        ),
        PlusFeature(
            id = "premium_themes",
            icon = Icons.Default.Palette,
            title = "会员专属主题",
            description = "莫奈配色",
            isAvailable = true
        ),
        PlusFeature(
            id = "notification_validator",
            icon = Icons.Default.Notifications,
            title = "通知栏验证器",
            description = "在通知栏固定显示 TOTP 验证器，随时查看验证码",
            isAvailable = true
        ),
        PlusFeature(
            id = "validator_vibration",
            icon = Icons.Default.Vibration,
            title = "验证器震动",
            description = "验证码即将刷新时自动震动提醒，不再错过窗口",
            isAvailable = true
        )
    )
}

package takagi.ru.monica.data.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 权限信息数据类
 * Permission information data class
 */
data class PermissionInfo(
    val id: String,                          // 权限ID (如 "CAMERA")
    val androidPermission: String,           // Android权限名称 (如 "android.permission.CAMERA")
    val icon: ImageVector,                   // Material图标
    @StringRes val nameResId: Int,           // 权限名称资源ID
    @StringRes val descriptionResId: Int,    // 功能说明资源ID
    val category: PermissionCategory,        // 权限分类
    val importance: PermissionImportance,    // 重要性级别
    val status: PermissionStatus = PermissionStatus.UNKNOWN  // 当前状态
)

/**
 * 权限分类
 * Permission category
 */
enum class PermissionCategory(
    @StringRes val titleResId: Int
) {
    SECURITY(takagi.ru.monica.R.string.permission_category_security),      // 安全权限
    STORAGE(takagi.ru.monica.R.string.permission_category_storage),        // 存储权限
    NETWORK(takagi.ru.monica.R.string.permission_category_network),        // 网络权限
    DEVICE(takagi.ru.monica.R.string.permission_category_device),          // 设备权限
    LOCATION(takagi.ru.monica.R.string.permission_category_location)       // 位置权限
}

/**
 * 权限重要性级别
 * Permission importance level
 */
enum class PermissionImportance(
    @StringRes val labelResId: Int
) {
    REQUIRED(takagi.ru.monica.R.string.permission_importance_required),    // 必需
    RECOMMENDED(takagi.ru.monica.R.string.permission_importance_recommended), // 推荐
    OPTIONAL(takagi.ru.monica.R.string.permission_importance_optional)     // 可选
}

/**
 * 权限状态
 * Permission status
 */
enum class PermissionStatus {
    GRANTED,        // 已授予
    DENIED,         // 未授予
    UNAVAILABLE,    // 不可用（设备不支持）
    UNKNOWN         // 未知
}

/**
 * 权限统计数据
 * Permission statistics data
 */
data class PermissionStats(
    val totalRequired: Int,
    val grantedRequired: Int,
    val totalPermissions: Int,
    val grantedPermissions: Int
)

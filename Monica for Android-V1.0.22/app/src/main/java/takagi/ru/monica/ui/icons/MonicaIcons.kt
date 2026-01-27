package takagi.ru.monica.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Phase 9: Monica 自定义图标集
 * 
 * 统一管理应用中使用的所有图标，确保视觉一致性
 * 
 * ## 设计原则
 * - 使用 Material Icons AutoMirrored 版本（支持RTL）
 * - 统一图标大小（24dp）
 * - 语义化命名
 * - 避免直接使用 Icons.Filled（已废弃）
 * 
 * ## 图标分类
 * - **导航图标**: 返回、前进、展开等
 * - **操作图标**: 编辑、删除、复制等
 * - **状态图标**: 成功、警告、错误等
 * - **功能图标**: 密码、信用卡、文档等
 * 
 * ## 使用示例
 * ```kotlin
 * Icon(
 *     imageVector = MonicaIcons.Navigation.back,
 *     contentDescription = "返回"
 * )
 * ```
 */
object MonicaIcons {
    
    /**
     * 导航图标集
     */
    object Navigation {
        /**
         * 返回图标（支持RTL）
         */
        val back: ImageVector
            get() = Icons.AutoMirrored.Filled.ArrowBack
        
        /**
         * 向右箭头（支持RTL）
         */
        val arrowRight: ImageVector
            get() = Icons.AutoMirrored.Filled.KeyboardArrowRight
        
        /**
         * 展开图标
         */
        val expandMore: ImageVector
            get() = Icons.Filled.ExpandMore
        
        /**
         * 收起图标
         */
        val expandLess: ImageVector
            get() = Icons.Filled.ExpandLess
    }
    
    /**
     * 操作图标集
     */
    object Action {
        /**
         * 添加图标
         */
        val add: ImageVector
            get() = Icons.Filled.Add
        
        /**
         * 编辑图标
         */
        val edit: ImageVector
            get() = Icons.Filled.Edit
        
        /**
         * 删除图标
         */
        val delete: ImageVector
            get() = Icons.Filled.Delete
        
        /**
         * 搜索图标
         */
        val search: ImageVector
            get() = Icons.Filled.Search
        
        /**
         * 复制图标
         */
        val copy: ImageVector
            get() = Icons.Filled.ContentCopy
        
        /**
         * 分享图标
         */
        val share: ImageVector
            get() = Icons.Filled.Share
        
        /**
         * 更多图标
         */
        val more: ImageVector
            get() = Icons.Filled.MoreVert
        
        /**
         * 刷新图标
         */
        val refresh: ImageVector
            get() = Icons.Filled.Refresh
        
        /**
         * 关闭图标
         */
        val close: ImageVector
            get() = Icons.Filled.Close
        
        /**
         * 检查/完成图标
         */
        val check: ImageVector
            get() = Icons.Filled.Check
    }
    
    /**
     * 安全/密码图标集
     */
    object Security {
        /**
         * 密码锁图标
         */
        val lock: ImageVector
            get() = Icons.Filled.Lock
        
        /**
         * 解锁图标
         */
        val lockOpen: ImageVector
            get() = Icons.Filled.LockOpen
        
        /**
         * 可见性（显示密码）
         */
        val visibility: ImageVector
            get() = Icons.Filled.Visibility
        
        /**
         * 不可见（隐藏密码）
         */
        val visibilityOff: ImageVector
            get() = Icons.Filled.VisibilityOff
        
        /**
         * 指纹图标
         */
        val fingerprint: ImageVector
            get() = Icons.Filled.Fingerprint
        
        /**
         * 安全图标
         */
        val security: ImageVector
            get() = Icons.Filled.Security
        
        /**
         * 盾牌图标
         */
        val shield: ImageVector
            get() = Icons.Filled.Shield
        
        /**
         * 密钥图标
         */
        val key: ImageVector
            get() = Icons.Filled.Key
    }
    
    /**
     * 数据/文档图标集
     */
    object Data {
        /**
         * 信用卡图标
         */
        val creditCard: ImageVector
            get() = Icons.Filled.CreditCard
        
        /**
         * 文档图标
         */
        val document: ImageVector
            get() = Icons.Filled.Description
        
        /**
         * 文件夹图标
         */
        val folder: ImageVector
            get() = Icons.Filled.Folder
        
        /**
         * 上传图标
         */
        val upload: ImageVector
            get() = Icons.Filled.Upload
        
        /**
         * 下载图标
         */
        val download: ImageVector
            get() = Icons.Filled.Download
        
        /**
         * 导入图标
         */
        val import: ImageVector
            get() = Icons.Filled.FileDownload
        
        /**
         * 导出图标
         */
        val export: ImageVector
            get() = Icons.Filled.FileUpload
    }
    
    /**
     * 状态图标集
     */
    object Status {
        /**
         * 成功图标
         */
        val success: ImageVector
            get() = Icons.Filled.CheckCircle
        
        /**
         * 警告图标
         */
        val warning: ImageVector
            get() = Icons.Filled.Warning
        
        /**
         * 错误图标
         */
        val error: ImageVector
            get() = Icons.Filled.Error
        
        /**
         * 信息图标
         */
        val info: ImageVector
            get() = Icons.Filled.Info
        
        /**
         * 收藏图标（实心）
         */
        val favorite: ImageVector
            get() = Icons.Filled.Favorite
        
        /**
         * 收藏图标（空心）
         */
        val favoriteBorder: ImageVector
            get() = Icons.Filled.FavoriteBorder
        
        /**
         * 星标图标（实心）
         */
        val star: ImageVector
            get() = Icons.Filled.Star
        
        /**
         * 星标图标（空心）
         */
        val starBorder: ImageVector
            get() = Icons.Filled.StarBorder
    }
    
    /**
     * 设置/配置图标集
     */
    object Settings {
        /**
         * 设置图标
         */
        val settings: ImageVector
            get() = Icons.Filled.Settings
        
        /**
         * 调色板图标
         */
        val palette: ImageVector
            get() = Icons.Filled.Palette
        
        /**
         * 语言图标
         */
        val language: ImageVector
            get() = Icons.Filled.Language
        
        /**
         * 通知图标
         */
        val notifications: ImageVector
            get() = Icons.Filled.Notifications
        
        /**
         * 暗黑模式图标
         */
        val darkMode: ImageVector
            get() = Icons.Filled.DarkMode
        
        /**
         * 明亮模式图标
         */
        val lightMode: ImageVector
            get() = Icons.Filled.LightMode
    }
    
    /**
     * 其他通用图标
     */
    object General {
        /**
         * 人员图标
         */
        val person: ImageVector
            get() = Icons.Filled.Person
        
        /**
         * 邮件图标
         */
        val email: ImageVector
            get() = Icons.Filled.Email
        
        /**
         * 电话图标
         */
        val phone: ImageVector
            get() = Icons.Filled.Phone
        
        /**
         * 网站图标
         */
        val web: ImageVector
            get() = Icons.Filled.Language
        
        /**
         * 日历图标
         */
        val calendar: ImageVector
            get() = Icons.Filled.CalendarToday
        
        /**
         * 时间图标
         */
        val time: ImageVector
            get() = Icons.Filled.AccessTime
        
        /**
         * 位置图标
         */
        val location: ImageVector
            get() = Icons.Filled.LocationOn
        
        /**
         * 照相机图标
         */
        val camera: ImageVector
            get() = Icons.Filled.CameraAlt
        
        /**
         * 图片图标
         */
        val image: ImageVector
            get() = Icons.Filled.Image
        
        /**
         * 二维码图标
         */
        val qrCode: ImageVector
            get() = Icons.Filled.QrCode2
    }
    
    /**
     * 财务相关图标
     */
    object Finance {
        /**
         * 账本图标
         */
        val ledger: ImageVector
            get() = Icons.Filled.Book
        
        /**
         * 账户余额图标
         */
        val accountBalance: ImageVector
            get() = Icons.Filled.AccountBalance
        
        /**
         * 钱包图标
         */
        val wallet: ImageVector
            get() = Icons.Filled.AccountBalanceWallet
        
        /**
         * 货币图标
         */
        val attachMoney: ImageVector
            get() = Icons.Filled.AttachMoney
    }
}

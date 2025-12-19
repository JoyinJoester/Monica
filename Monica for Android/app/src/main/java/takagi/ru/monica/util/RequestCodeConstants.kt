package takagi.ru.monica.util

/**
 * RequestCode常量管理器
 * 通过抽象类集中管理所有requestCode常量，避免requestCode超限问题
 */
abstract class RequestCodeConstants {
    companion object {
        // 图片选择相关
        const val REQUEST_CODE_CAMERA = 1001
        const val REQUEST_CODE_GALLERY = 1002
        const val REQUEST_CODE_PERMISSION_CAMERA = 1003
        
        // 文件操作相关
        const val REQUEST_CODE_EXPORT_DATA = 2001
        const val REQUEST_CODE_IMPORT_DATA = 2002
        const val REQUEST_CODE_OPEN_DOCUMENT = 2003
        
        // 权限相关
        const val REQUEST_CODE_PERMISSION_STORAGE = 3001
        const val REQUEST_CODE_PERMISSION_LOCATION = 3002
        const val REQUEST_CODE_PERMISSION_CAMERA_MAIN = 3003
        
        // 系统功能相关
        const val REQUEST_CODE_QR_SCANNER = 4001
        const val REQUEST_CODE_BIOMETRIC = 4002
        const val REQUEST_CODE_WEBDAV_AUTH = 4003
        
        // 自定义功能相关
        const val REQUEST_CODE_SUPPORT_AUTHOR = 5001
        const val REQUEST_CODE_BACKUP_RESTORE = 5002
    }
}
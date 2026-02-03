package takagi.ru.monica.bitwarden.sync

/**
 * 同步数据项类型
 * 对应 Monica 中的不同数据模型
 */
enum class SyncItemType {
    /** 密码条目 (PasswordEntry) -> Bitwarden Login (Type 1) */
    PASSWORD,
    
    /** 独立验证器 (SecureItem TOTP) -> Bitwarden Login with TOTP (Type 1) */
    TOTP,
    
    /** 银行卡 (SecureItem BANK_CARD) -> Bitwarden Card (Type 3) */
    CARD,
    
    /** 安全笔记 (SecureItem NOTE) -> Bitwarden SecureNote (Type 2) */
    NOTE,
    
    /** 证件 (SecureItem DOCUMENT) -> Bitwarden Identity (Type 4) */
    IDENTITY,
    
    /** 通行密钥 (PasskeyEntry) -> Bitwarden Login with metadata (Type 1) */
    PASSKEY,
    
    /** 分类/文件夹 (Category) -> Bitwarden Folder */
    FOLDER;
    
    /**
     * 转换为 Bitwarden Cipher 类型
     */
    fun toBitwardenCipherType(): Int = when (this) {
        PASSWORD -> 1  // Login
        TOTP -> 1      // Login (with totp field only)
        CARD -> 3      // Card
        NOTE -> 2      // SecureNote
        IDENTITY -> 4  // Identity
        PASSKEY -> 1   // Login (metadata only)
        FOLDER -> 0    // Folder (not a cipher)
    }
    
    companion object {
        /**
         * 从 Bitwarden Cipher 类型转换
         * 注意：Type 1 (Login) 可能对应多种 Monica 类型，需要根据内容判断
         */
        fun fromBitwardenCipherType(type: Int): SyncItemType = when (type) {
            1 -> PASSWORD  // 默认为 PASSWORD，具体需要根据内容判断
            2 -> NOTE
            3 -> CARD
            4 -> IDENTITY
            else -> PASSWORD
        }
    }
}

/**
 * 同步操作类型
 */
enum class SyncOperation {
    /** 创建新条目 */
    CREATE,
    
    /** 更新现有条目 */
    UPDATE,
    
    /** 删除条目 */
    DELETE,
    
    /** 移动到其他文件夹 */
    MOVE_FOLDER;
    
    fun toDbValue(): String = name
    
    companion object {
        fun fromDbValue(value: String): SyncOperation = 
            values().find { it.name == value } ?: CREATE
    }
}

/**
 * 同步状态
 */
enum class SyncStatus {
    /** 未同步 - 仅本地存储 */
    NONE,
    
    /** 等待同步 */
    PENDING,
    
    /** 正在同步 */
    SYNCING,
    
    /** 同步成功 */
    SYNCED,
    
    /** 同步失败 */
    FAILED,
    
    /** 存在冲突 */
    CONFLICT;
    
    fun toDbValue(): String = name
    
    companion object {
        fun fromDbValue(value: String): SyncStatus = 
            values().find { it.name == value } ?: NONE
    }
}

/**
 * 同步错误类型
 */
enum class SyncErrorType {
    /** 网络错误 - 可自动重试 */
    NETWORK_ERROR,
    
    /** 认证错误 - 需要重新登录 */
    AUTH_ERROR,
    
    /** 权限错误 - 无法写入 */
    PERMISSION_ERROR,
    
    /** 冲突错误 - 需要用户干预 */
    CONFLICT_ERROR,
    
    /** 数据错误 - 格式不正确 */
    DATA_ERROR,
    
    /** 服务器错误 - 稍后重试 */
    SERVER_ERROR,
    
    /** 未知错误 */
    UNKNOWN_ERROR;
    
    /**
     * 是否可以自动重试
     */
    fun isRetryable(): Boolean = when (this) {
        NETWORK_ERROR, SERVER_ERROR -> true
        else -> false
    }
    
    /**
     * 是否需要用户干预
     */
    fun requiresUserAction(): Boolean = when (this) {
        AUTH_ERROR, PERMISSION_ERROR, CONFLICT_ERROR -> true
        else -> false
    }
}

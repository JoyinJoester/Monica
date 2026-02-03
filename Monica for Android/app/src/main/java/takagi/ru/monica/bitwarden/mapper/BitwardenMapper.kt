package takagi.ru.monica.bitwarden.mapper

import takagi.ru.monica.bitwarden.api.CipherApiResponse
import takagi.ru.monica.bitwarden.api.CipherCreateRequest

/**
 * Bitwarden Cipher Mapper 基础接口
 * 
 * 定义 Monica 数据模型与 Bitwarden Cipher 之间的双向映射
 */
interface BitwardenMapper<T> {
    
    /**
     * 将 Monica 数据模型转换为 Bitwarden 创建请求
     * 
     * @param item Monica 数据项
     * @param folderId 目标文件夹 ID（可选）
     * @return Cipher 创建请求
     */
    fun toCreateRequest(item: T, folderId: String? = null): CipherCreateRequest
    
    /**
     * 将 Bitwarden Cipher 响应转换为 Monica 数据模型
     * 
     * @param cipher Bitwarden Cipher 响应
     * @param vaultId Monica 中的 Vault ID
     * @return Monica 数据项
     */
    fun fromCipherResponse(cipher: CipherApiResponse, vaultId: Long): T
    
    /**
     * 检查 Monica 数据项与 Bitwarden Cipher 是否有差异
     * 
     * @param item Monica 数据项
     * @param cipher Bitwarden Cipher 响应
     * @return 是否有差异需要同步
     */
    fun hasDifference(item: T, cipher: CipherApiResponse): Boolean
    
    /**
     * 合并 Monica 数据项和 Bitwarden Cipher
     * 用于冲突解决
     * 
     * @param local 本地 Monica 数据
     * @param remote Bitwarden 远程数据
     * @param preference 优先级：LOCAL 或 REMOTE
     * @return 合并后的数据
     */
    fun merge(local: T, remote: CipherApiResponse, preference: MergePreference): T
}

/**
 * 合并偏好
 */
enum class MergePreference {
    /** 本地优先 */
    LOCAL,
    /** 远程优先 */
    REMOTE,
    /** 按时间戳最新优先 */
    LATEST
}

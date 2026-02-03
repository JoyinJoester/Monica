package takagi.ru.monica.bitwarden.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 空 Vault 保护机制
 * 
 * 当服务器返回空的 Vault 数据时，阻止同步并发出警告，
 * 防止因服务器错误或网络问题导致本地数据被意外清除。
 * 
 * 保护规则：
 * 1. 首次同步允许空 Vault（新账户）
 * 2. 后续同步如果服务器返回 0 条数据但本地有数据，触发保护
 * 3. 用户可以强制覆盖（确认清空）
 * 
 * 参考 Keyguard 的安全同步策略
 */
object EmptyVaultProtection {
    
    private const val TAG = "EmptyVaultProtection"
    
    /**
     * 空 Vault 警告事件
     */
    sealed class ProtectionEvent {
        /**
         * 检测到可疑的空 Vault
         * @param vaultId Vault ID
         * @param localCount 本地条目数量
         * @param serverCount 服务器返回数量
         */
        data class EmptyVaultDetected(
            val vaultId: Long,
            val localCount: Int,
            val serverCount: Int
        ) : ProtectionEvent()
        
        /**
         * 用户确认清空
         */
        data class UserConfirmedClear(val vaultId: Long) : ProtectionEvent()
        
        /**
         * 用户取消同步
         */
        data class UserCancelledSync(val vaultId: Long) : ProtectionEvent()
    }
    
    /**
     * 检查结果
     */
    sealed class CheckResult {
        /** 允许同步 */
        object Allowed : CheckResult()
        
        /** 首次同步，允许空 Vault */
        object FirstSyncAllowed : CheckResult()
        
        /** 阻止同步，需要用户确认 */
        data class Blocked(
            val vaultId: Long,
            val localCount: Int,
            val serverCount: Int,
            val reason: String
        ) : CheckResult()
    }
    
    // 事件流
    private val _events = MutableSharedFlow<ProtectionEvent>()
    val events: SharedFlow<ProtectionEvent> = _events.asSharedFlow()
    
    // 用户确认状态（vaultId -> 是否确认清空）
    private val userConfirmations = mutableMapOf<Long, Boolean>()
    
    /**
     * 检查是否允许同步
     * 
     * @param vaultId Vault ID
     * @param localCipherCount 本地 Cipher 数量
     * @param serverCipherCount 服务器返回的 Cipher 数量
     * @param isFirstSync 是否为首次同步
     * @return 检查结果
     */
    fun checkSyncAllowed(
        vaultId: Long,
        localCipherCount: Int,
        serverCipherCount: Int,
        isFirstSync: Boolean
    ): CheckResult {
        Log.d(TAG, "检查同步: vaultId=$vaultId, local=$localCipherCount, server=$serverCipherCount, firstSync=$isFirstSync")
        
        // 首次同步允许空 Vault
        if (isFirstSync) {
            Log.i(TAG, "首次同步，允许空 Vault")
            return CheckResult.FirstSyncAllowed
        }
        
        // 服务器有数据，正常同步
        if (serverCipherCount > 0) {
            Log.d(TAG, "服务器有数据，允许同步")
            return CheckResult.Allowed
        }
        
        // 服务器返回 0 条数据
        if (localCipherCount == 0) {
            // 本地也是空的，正常
            Log.d(TAG, "本地和服务器都为空，允许同步")
            return CheckResult.Allowed
        }
        
        // 危险情况：本地有数据，但服务器返回空
        // 检查用户是否已确认
        if (userConfirmations[vaultId] == true) {
            Log.w(TAG, "用户已确认清空本地数据，允许同步")
            userConfirmations.remove(vaultId)
            return CheckResult.Allowed
        }
        
        // 阻止同步
        Log.w(TAG, "⚠️ 空 Vault 保护触发! 本地 $localCipherCount 条，服务器 0 条")
        return CheckResult.Blocked(
            vaultId = vaultId,
            localCount = localCipherCount,
            serverCount = serverCipherCount,
            reason = "服务器返回空数据，但本地有 $localCipherCount 条记录。" +
                    "这可能是服务器错误或账号问题，为保护您的数据，同步已暂停。"
        )
    }
    
    /**
     * 用户确认清空本地数据
     */
    suspend fun confirmClearLocalData(vaultId: Long) {
        Log.w(TAG, "用户确认清空 Vault $vaultId 的本地数据")
        userConfirmations[vaultId] = true
        _events.emit(ProtectionEvent.UserConfirmedClear(vaultId))
    }
    
    /**
     * 用户取消同步
     */
    suspend fun cancelSync(vaultId: Long) {
        Log.i(TAG, "用户取消 Vault $vaultId 的同步")
        userConfirmations.remove(vaultId)
        _events.emit(ProtectionEvent.UserCancelledSync(vaultId))
    }
    
    /**
     * 发送空 Vault 检测事件
     */
    suspend fun emitEmptyVaultDetected(vaultId: Long, localCount: Int, serverCount: Int) {
        _events.emit(ProtectionEvent.EmptyVaultDetected(vaultId, localCount, serverCount))
    }
    
    /**
     * 清除确认状态（用于测试或重置）
     */
    fun clearConfirmations() {
        userConfirmations.clear()
    }
    
    /**
     * 计算安全阈值
     * 
     * 如果服务器返回的数据量比本地少太多，也发出警告
     * 
     * @return 是否触发警告
     */
    fun checkSignificantDataLoss(
        localCount: Int,
        serverCount: Int,
        threshold: Float = 0.5f  // 默认：如果减少超过 50% 则警告
    ): Boolean {
        if (localCount == 0) return false
        if (serverCount >= localCount) return false
        
        val lossRatio = (localCount - serverCount).toFloat() / localCount
        return lossRatio > threshold
    }
}

package takagi.ru.monica.bitwarden.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperationDao

/**
 * 同步队列管理器
 * 
 * 采用 Telegram 风格的离线优先同步策略：
 * 1. 用户操作立即保存到本地，同时加入同步队列
 * 2. 有网络时后台自动处理队列
 * 3. 网络恢复时自动重试失败的操作
 * 4. 失败操作使用指数退避重试策略
 */
class SyncQueueManager(
    private val context: Context,
    private val pendingOperationDao: BitwardenPendingOperationDao,
    private val networkMonitor: NetworkMonitor
) {
    companion object {
        private const val TAG = "SyncQueueManager"
        
        // 重试策略配置
        private const val INITIAL_RETRY_DELAY_MS = 1000L      // 1秒
        private const val MAX_RETRY_DELAY_MS = 300000L        // 5分钟
        private const val RETRY_MULTIPLIER = 2.0
        private const val MAX_RETRIES = 5
        
        // 批量处理配置
        private const val BATCH_SIZE = 10
        private const val BATCH_DELAY_MS = 500L
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processingJob: Job? = null
    
    // 同步处理器，需要在初始化时设置
    private var syncProcessor: SyncProcessor? = null
    
    // 同步设置
    private var syncOnWifiOnly: Boolean = false
    private var autoSyncEnabled: Boolean = true
    
    /**
     * 同步处理器接口
     * 负责实际的同步操作，由 BitwardenRepository 实现
     */
    interface SyncProcessor {
        /**
         * 处理单个同步操作
         * @return 成功返回 true，失败返回 false
         */
        suspend fun processOperation(operation: BitwardenPendingOperation): SyncResult
    }
    
    /**
     * 同步结果
     */
    sealed class SyncResult {
        /** 同步成功 */
        data class Success(val bitwardenCipherId: String? = null) : SyncResult()
        
        /** 同步失败，可以重试 */
        data class RetryableError(
            val errorType: SyncErrorType,
            val message: String
        ) : SyncResult()
        
        /** 同步失败，需要用户干预 */
        data class FatalError(
            val errorType: SyncErrorType,
            val message: String
        ) : SyncResult()
        
        /** 操作已取消（如条目已删除） */
        object Cancelled : SyncResult()
    }
    
    /**
     * 队列状态
     */
    data class QueueState(
        val pendingCount: Int,
        val failedCount: Int,
        val isProcessing: Boolean
    )
    
    // 队列状态 Flow
    private val _isProcessing = MutableStateFlow(false)
    
    val queueStateFlow: Flow<QueueState> = combine(
        pendingOperationDao.getPendingCountFlow(),
        pendingOperationDao.getFailedCountFlow(),
        _isProcessing
    ) { pending, failed, processing ->
        QueueState(pending, failed, processing)
    }
    
    /**
     * 待处理操作列表 Flow
     */
    val pendingOperationsFlow: Flow<List<BitwardenPendingOperation>> = 
        pendingOperationDao.getAllPendingAndFailedFlow()
    
    /**
     * 初始化并开始监听网络变化
     */
    fun initialize(processor: SyncProcessor) {
        this.syncProcessor = processor
        
        // 监听网络变化，网络恢复时自动处理队列
        scope.launch {
            networkMonitor.networkStateFlow.collect { state ->
                if (state.isOnline && autoSyncEnabled) {
                    if (!syncOnWifiOnly || state.isWifi) {
                        Log.d(TAG, "Network available, starting queue processing")
                        processQueue()
                    }
                }
            }
        }
    }
    
    /**
     * 设置同步配置
     */
    fun updateSettings(autoSync: Boolean, wifiOnly: Boolean) {
        this.autoSyncEnabled = autoSync
        this.syncOnWifiOnly = wifiOnly
    }
    
    /**
     * 添加操作到队列
     */
    suspend fun enqueue(
        vaultId: Long,
        entryId: Long,
        itemType: SyncItemType,
        operation: SyncOperation,
        bitwardenCipherId: String? = null,
        payloadJson: String = "{}"
    ): Long {
        // 检查是否已有相同的待处理操作
        val existing = pendingOperationDao.findPendingByEntryAndType(
            entryId = entryId,
            itemType = itemType.name
        )
        
        if (existing != null) {
            // 如果已有待处理操作，根据新操作类型决定如何处理
            when (operation) {
                SyncOperation.DELETE -> {
                    // 删除操作：取消之前的创建/更新，如果之前是创建则直接删除本地记录
                    if (existing.operationType == BitwardenPendingOperation.OP_CREATE) {
                        // 还没创建到服务器，直接取消
                        pendingOperationDao.cancel(existing.id)
                        return -1 // 不需要新的操作
                    } else {
                        // 之前是更新，现在变成删除
                        pendingOperationDao.cancel(existing.id)
                    }
                }
                SyncOperation.UPDATE -> {
                    // 更新操作：如果之前是创建，保持创建；如果是更新，替换
                    if (existing.operationType == BitwardenPendingOperation.OP_UPDATE) {
                        // 取消旧的更新，添加新的
                        pendingOperationDao.cancel(existing.id)
                    } else if (existing.operationType == BitwardenPendingOperation.OP_CREATE) {
                        // 创建还没完成，保持创建操作，但更新 payload
                        val updated = existing.copy(payloadJson = payloadJson)
                        pendingOperationDao.update(updated)
                        return existing.id
                    }
                }
                SyncOperation.CREATE -> {
                    // 不应该有重复的创建操作
                    Log.w(TAG, "Duplicate CREATE operation for entry $entryId")
                    return existing.id
                }
                else -> {}
            }
        }
        
        val pendingOperation = BitwardenPendingOperation(
            vaultId = vaultId,
            entryId = entryId,
            bitwardenCipherId = bitwardenCipherId,
            itemType = itemType.name,
            operationType = operation.toDbValue(),
            targetType = BitwardenPendingOperation.TARGET_CIPHER,
            payloadJson = payloadJson,
            status = BitwardenPendingOperation.STATUS_PENDING,
            maxRetries = MAX_RETRIES
        )
        
        val id = pendingOperationDao.insert(pendingOperation)
        Log.d(TAG, "Enqueued operation: $operation for $itemType entry $entryId, id=$id")
        
        // 如果有网络，立即触发处理
        if (networkMonitor.canSync(syncOnWifiOnly) && autoSyncEnabled) {
            processQueue()
        }
        
        return id
    }
    
    /**
     * 处理同步队列
     */
    fun processQueue() {
        if (processingJob?.isActive == true) {
            Log.d(TAG, "Queue processing already in progress")
            return
        }
        
        processingJob = scope.launch {
            _isProcessing.value = true
            try {
                processQueueInternal()
            } finally {
                _isProcessing.value = false
            }
        }
    }
    
    private suspend fun processQueueInternal() {
        val processor = syncProcessor ?: run {
            Log.w(TAG, "SyncProcessor not set, skipping queue processing")
            return
        }
        
        while (true) {
            // 检查网络
            if (!networkMonitor.canSync(syncOnWifiOnly)) {
                Log.d(TAG, "Network not available, pausing queue processing")
                break
            }
            
            // 获取待处理操作
            val operations = pendingOperationDao.getPendingOperations()
            if (operations.isEmpty()) {
                Log.d(TAG, "No pending operations")
                break
            }
            
            // 按批次处理
            val batch = operations.take(BATCH_SIZE)
            for (operation in batch) {
                if (!networkMonitor.canSync(syncOnWifiOnly)) {
                    Log.d(TAG, "Network lost during processing, pausing")
                    return
                }
                
                processOperation(processor, operation)
                delay(BATCH_DELAY_MS) // 避免过快请求
            }
        }
    }
    
    private suspend fun processOperation(
        processor: SyncProcessor,
        operation: BitwardenPendingOperation
    ) {
        Log.d(TAG, "Processing operation ${operation.id}: ${operation.operationType} for ${operation.itemType}")
        
        // 更新状态为处理中
        pendingOperationDao.updateStatus(
            id = operation.id,
            status = BitwardenPendingOperation.STATUS_IN_PROGRESS
        )
        
        try {
            when (val result = processor.processOperation(operation)) {
                is SyncResult.Success -> {
                    Log.d(TAG, "Operation ${operation.id} completed successfully")
                    
                    // 如果是创建操作，更新 Cipher ID
                    result.bitwardenCipherId?.let { cipherId ->
                        pendingOperationDao.updateCipherId(operation.id, cipherId)
                    }
                    
                    pendingOperationDao.markCompleted(operation.id)
                }
                
                is SyncResult.RetryableError -> {
                    val nextRetryDelay = calculateRetryDelay(operation.retryCount)
                    Log.w(TAG, "Operation ${operation.id} failed (retryable): ${result.message}, " +
                            "retry ${operation.retryCount + 1}/$MAX_RETRIES, next retry in ${nextRetryDelay}ms")
                    
                    if (operation.retryCount + 1 >= MAX_RETRIES) {
                        // 达到最大重试次数
                        pendingOperationDao.updateStatus(
                            id = operation.id,
                            status = BitwardenPendingOperation.STATUS_FAILED,
                            lastError = "${result.errorType}: ${result.message}"
                        )
                    } else {
                        // 标记为待重试
                        pendingOperationDao.updateStatus(
                            id = operation.id,
                            status = BitwardenPendingOperation.STATUS_PENDING,
                            lastError = result.message
                        )
                    }
                }
                
                is SyncResult.FatalError -> {
                    Log.e(TAG, "Operation ${operation.id} failed (fatal): ${result.message}")
                    pendingOperationDao.updateStatus(
                        id = operation.id,
                        status = BitwardenPendingOperation.STATUS_FAILED,
                        lastError = "${result.errorType}: ${result.message}"
                    )
                }
                
                is SyncResult.Cancelled -> {
                    Log.d(TAG, "Operation ${operation.id} cancelled")
                    pendingOperationDao.cancel(operation.id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception processing operation ${operation.id}", e)
            pendingOperationDao.updateStatus(
                id = operation.id,
                status = if (operation.retryCount + 1 >= MAX_RETRIES) 
                    BitwardenPendingOperation.STATUS_FAILED 
                else 
                    BitwardenPendingOperation.STATUS_PENDING,
                lastError = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * 计算指数退避延迟
     */
    private fun calculateRetryDelay(retryCount: Int): Long {
        val delay = INITIAL_RETRY_DELAY_MS * Math.pow(RETRY_MULTIPLIER, retryCount.toDouble())
        return minOf(delay.toLong(), MAX_RETRY_DELAY_MS)
    }
    
    /**
     * 手动重试失败的操作
     */
    suspend fun retryFailed(operationId: Long) {
        pendingOperationDao.resetForRetry(operationId)
        if (networkMonitor.canSync(syncOnWifiOnly)) {
            processQueue()
        }
    }
    
    /**
     * 重试所有失败的操作
     */
    suspend fun retryAllFailed() {
        pendingOperationDao.resetAllFailedForRetry()
        if (networkMonitor.canSync(syncOnWifiOnly)) {
            processQueue()
        }
    }
    
    /**
     * 取消指定操作
     */
    suspend fun cancelOperation(operationId: Long) {
        pendingOperationDao.cancel(operationId)
    }
    
    /**
     * 取消指定条目的所有待处理操作
     */
    suspend fun cancelOperationsForEntry(entryId: Long, itemType: SyncItemType) {
        pendingOperationDao.deletePendingForEntryAndType(entryId, itemType.name)
    }
    
    /**
     * 清理已完成的操作（保留最近7天）
     */
    suspend fun cleanupCompleted() {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        pendingOperationDao.deleteCompletedBefore(sevenDaysAgo)
    }
    
    /**
     * 停止处理并清理资源
     */
    fun shutdown() {
        processingJob?.cancel()
        scope.cancel()
    }
}

package takagi.ru.monica.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.OperationLog
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.model.DiffChange
import takagi.ru.monica.data.model.TIMELINE_FIELD_BATCH_COPY_PAYLOAD
import takagi.ru.monica.data.model.TIMELINE_FIELD_BATCH_MOVE_PAYLOAD
import takagi.ru.monica.data.model.TimelineBatchCopyPayload
import takagi.ru.monica.data.model.TimelineBatchMovePayload
import takagi.ru.monica.data.model.TimelineBranch
import takagi.ru.monica.data.model.TimelineEvent
import takagi.ru.monica.repository.OperationLogRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.FieldChange
import java.util.Date

/**
 * Timeline ViewModel - 管理时间线事件的状态
 * 从 OperationLogRepository 加载真实数据
 */
class TimelineViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = PasswordDatabase.getDatabase(application)
    private val repository = OperationLogRepository(database.operationLogDao())
    private val passwordRepository = PasswordRepository(database.passwordEntryDao())
    private val secureItemRepository = SecureItemRepository(database.secureItemDao())
    private val securityManager = SecurityManager(application)
    
    private val _timelineEvents = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val timelineEvents: StateFlow<List<TimelineEvent>> = _timelineEvents.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        loadTimelineData()
    }
    
    /**
     * 从数据库加载时间线数据
     */
    private fun loadTimelineData() {
        viewModelScope.launch {
            repository.getRecentLogs(100).collectLatest { logs ->
                _isLoading.value = false
                _timelineEvents.value = convertLogsToEvents(logs)
            }
        }
    }
    
    /**
     * 将 OperationLog 列表转换为 TimelineEvent 列表
     */
    private fun convertLogsToEvents(logs: List<OperationLog>): List<TimelineEvent> {
        return logs.map { log ->
            val changes = parseChanges(log.changesJson)
            val summary = generateSummary(log)
            
            TimelineEvent.StandardLog(
                id = log.id.toString(),
                timestamp = log.timestamp,
                deviceId = log.deviceId,
                summary = summary,
                itemId = log.itemId,
                itemType = log.itemType,
                operationType = log.operationType,
                isReverted = log.isReverted,
                changes = changes
            )
        }
    }
    
    /**
     * 解析 JSON 格式的变更记录
     */
    private fun parseChanges(changesJson: String): List<DiffChange> {
        if (changesJson.isEmpty()) return emptyList()
        
        return try {
            val fieldChanges = parseRawChanges(changesJson)
            fieldChanges.map { fc ->
                DiffChange(
                    fieldName = fc.fieldName,
                    oldValue = fc.oldValue,
                    newValue = fc.newValue
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseRawChanges(changesJson: String): List<FieldChange> {
        if (changesJson.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<FieldChange>>(changesJson)
                .filterNot { it.fieldName.startsWith("__") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRawChangesIncludingTechnical(changesJson: String): List<FieldChange> {
        if (changesJson.isBlank()) return emptyList()
        return try {
            json.decodeFromString(changesJson)
        } catch (_: Exception) {
            emptyList()
        }
    }
    
    /**
     * 根据操作类型生成摘要 - 只显示项目标题
     */
    private fun generateSummary(log: OperationLog): String {
        return log.itemTitle
    }
    
    /**
     * 刷新时间线数据
     */
    fun refresh() {
        _isLoading.value = true
        loadTimelineData()
    }
    
    /**
     * 恢复到指定分支版本
     */
    fun restoreVersion(branch: TimelineBranch) {
        // TODO: 实现实际的恢复逻辑
    }
    
    /**
     * 保存为新条目
     */
    fun saveAsNewEntry(branch: TimelineBranch) {
        // TODO: 实现实际的保存逻辑
    }
    
    /**
     * 恢复编辑操作 - 将条目恢复到编辑前的状态
     * @param log 要恢复的日志记录
     * @param onResult 回调函数，返回是否成功
     */
    fun revertEdit(log: TimelineEvent.StandardLog, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val logId = log.id.toLongOrNull() ?: return@launch onResult(false)
                val itemId = log.itemId
                val rawLog = database.operationLogDao().getLogById(logId) ?: return@launch onResult(false)
                val isCurrentlyReverted = rawLog.isReverted
                val rawChanges = parseRawChangesIncludingTechnical(rawLog.changesJson)
                val batchMovePayload = rawChanges.firstOrNull {
                    it.fieldName == TIMELINE_FIELD_BATCH_MOVE_PAYLOAD
                }?.newValue
                val batchCopyPayload = rawChanges.firstOrNull {
                    it.fieldName == TIMELINE_FIELD_BATCH_COPY_PAYLOAD
                }?.newValue

                if (!batchMovePayload.isNullOrBlank()) {
                    if (isCurrentlyReverted) return@launch onResult(false)
                    val restored = restoreBatchMove(payloadJson = batchMovePayload)
                    if (!restored) return@launch onResult(false)
                    database.operationLogDao().updateRevertedStatus(logId, !isCurrentlyReverted)
                    return@launch onResult(true)
                }

                if (!batchCopyPayload.isNullOrBlank()) {
                    if (isCurrentlyReverted) return@launch onResult(false)
                    val restored = restoreBatchCopy(payloadJson = batchCopyPayload)
                    if (!restored) return@launch onResult(false)
                    database.operationLogDao().updateRevertedStatus(logId, !isCurrentlyReverted)
                    return@launch onResult(true)
                }
                
                when (log.itemType) {
                    "PASSWORD" -> {
                        val entry = passwordRepository.getPasswordEntryById(itemId) ?: return@launch onResult(false)
                        
                        // 应用变更（恢复或重做）
                        var updatedEntry = entry
                        rawChanges.forEach { change ->
                            // 确定要使用的目标值
                            val targetValue = if (isCurrentlyReverted) {
                                // 当前是已恢复状态，要重做（恢复到编辑后）-> 使用 newValue
                                change.newValue
                            } else {
                                // 当前是编辑后状态，要恢复（恢复到编辑前）-> 使用 oldValue
                                change.oldValue
                            }
                            
                            updatedEntry = when (change.fieldName) {
                                "用户名" -> updatedEntry.copy(username = targetValue)
                                "网站" -> updatedEntry.copy(website = targetValue)
                                "密码" -> if (targetValue.isNotBlank()) {
                                    updatedEntry.copy(password = securityManager.encryptData(targetValue))
                                } else updatedEntry
                                "备注" -> updatedEntry.copy(notes = targetValue)
                                "标题" -> updatedEntry.copy(title = targetValue)
                                else -> updatedEntry
                            }
                        }
                        
                        passwordRepository.updatePasswordEntry(updatedEntry)
                    }
                    "TOTP", "BANK_CARD", "NOTE", "DOCUMENT" -> {
                        val item = secureItemRepository.getItemById(itemId) ?: return@launch onResult(false)
                        
                        var updatedItem = item
                        rawChanges.forEach { change ->
                            val targetVal = if (isCurrentlyReverted) change.newValue else change.oldValue
                            
                            updatedItem = when (change.fieldName) {
                                "标题" -> updatedItem.copy(title = targetVal)
                                "备注" -> updatedItem.copy(notes = targetVal)
                                "内容" -> updatedItem.copy(notes = targetVal) // 笔记内容
                                else -> updatedItem
                            }
                        }
                        
                        secureItemRepository.updateItem(updatedItem)
                    }
                    else -> return@launch onResult(false)
                }
                
                // 切换恢复状态
                database.operationLogDao().updateRevertedStatus(logId, !isCurrentlyReverted)
                onResult(true)
                
            } catch (e: Exception) {
                android.util.Log.e("TimelineViewModel", "Failed to revert edit", e)
                onResult(false)
            }
        }
    }

    private suspend fun restoreBatchMove(payloadJson: String): Boolean {
        val payload = try {
            json.decodeFromString<TimelineBatchMovePayload>(payloadJson)
        } catch (_: Exception) {
            return false
        }

        val targetStates = payload.oldStates
        if (targetStates.isEmpty()) return false

        var changed = false
        targetStates.forEach { state ->
            val entry = passwordRepository.getPasswordEntryById(state.id) ?: return@forEach
            val updatedEntry = entry.copy(
                categoryId = state.categoryId,
                keepassDatabaseId = state.keepassDatabaseId,
                keepassGroupPath = state.keepassGroupPath,
                bitwardenVaultId = state.bitwardenVaultId,
                bitwardenFolderId = state.bitwardenFolderId,
                bitwardenLocalModified = state.bitwardenLocalModified,
                isArchived = state.isArchived,
                archivedAt = state.archivedAtMillis?.let { Date(it) },
                updatedAt = Date()
            )
            passwordRepository.updatePasswordEntry(updatedEntry)
            changed = true
        }
        return changed
    }

    private suspend fun restoreBatchCopy(payloadJson: String): Boolean {
        val payload = try {
            json.decodeFromString<TimelineBatchCopyPayload>(payloadJson)
        } catch (_: Exception) {
            return false
        }
        if (payload.copiedEntryIds.isEmpty()) return false

        var changed = false
        payload.copiedEntryIds.forEach { id ->
            val entry = passwordRepository.getPasswordEntryById(id) ?: return@forEach
            val updatedEntry = entry.copy(
                isDeleted = true,
                deletedAt = Date(),
                updatedAt = Date()
            )
            passwordRepository.updatePasswordEntry(updatedEntry)
            changed = true
        }
        return changed
    }
    
    /**
     * 将旧数据保存为新条目
     * @param log 日志记录（从中提取旧数据）
     * @param onResult 回调函数，返回是否成功
     */
    fun saveOldDataAsNew(log: TimelineEvent.StandardLog, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val itemId = log.itemId
                
                when (log.itemType) {
                    "PASSWORD" -> {
                        val entry = passwordRepository.getPasswordEntryById(itemId) ?: return@launch onResult(false)
                        
                        // 创建新条目，使用旧值
                        var newEntry = entry.copy(
                            id = 0,  // 新ID
                            title = "${entry.title} (旧版本)",
                            createdAt = java.util.Date(),
                            updatedAt = java.util.Date()
                        )
                        
                        // 应用旧值
                        log.changes.forEach { change ->
                            newEntry = when (change.fieldName) {
                                "用户名" -> newEntry.copy(username = change.oldValue)
                                "网站" -> newEntry.copy(website = change.oldValue)
                                "密码" -> if (change.oldValue.isNotBlank()) {
                                    newEntry.copy(password = securityManager.encryptData(change.oldValue))
                                } else newEntry
                                "备注" -> newEntry.copy(notes = change.oldValue)
                                "标题" -> newEntry.copy(title = "${change.oldValue} (旧版本)")
                                else -> newEntry
                            }
                        }
                        
                        passwordRepository.insertPasswordEntry(newEntry)
                    }
                    "NOTE" -> {
                        val item = secureItemRepository.getItemById(itemId) ?: return@launch onResult(false)
                        
                        var newItem = item.copy(
                            id = 0,
                            title = "${item.title} (旧版本)",
                            createdAt = java.util.Date(),
                            updatedAt = java.util.Date()
                        )
                        
                        log.changes.forEach { change ->
                            newItem = when (change.fieldName) {
                                "内容" -> newItem.copy(notes = change.oldValue)
                                "标题" -> newItem.copy(title = "${change.oldValue} (旧版本)")
                                else -> newItem
                            }
                        }
                        
                        secureItemRepository.insertItem(newItem)
                    }
                    "TOTP", "BANK_CARD", "DOCUMENT" -> {
                        val item = secureItemRepository.getItemById(itemId) ?: return@launch onResult(false)
                        
                        var newItem = item.copy(
                            id = 0,
                            title = "${item.title} (旧版本)",
                            createdAt = java.util.Date(),
                            updatedAt = java.util.Date()
                        )
                        
                        log.changes.forEach { change ->
                            newItem = when (change.fieldName) {
                                "标题" -> newItem.copy(title = "${change.oldValue} (旧版本)")
                                "备注" -> newItem.copy(notes = change.oldValue)
                                else -> newItem
                            }
                        }
                        
                        secureItemRepository.insertItem(newItem)
                    }
                    else -> return@launch onResult(false)
                }
                
                onResult(true)
                
            } catch (e: Exception) {
                android.util.Log.e("TimelineViewModel", "Failed to save old data as new", e)
                onResult(false)
            }
        }
    }
}

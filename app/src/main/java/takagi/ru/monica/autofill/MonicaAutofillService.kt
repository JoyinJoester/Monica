package takagi.ru.monica.autofill

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.data.PasswordDatabase

/**
 * Monica 自动填充服务
 * 
 * 提供密码和表单的自动填充功能
 */
class MonicaAutofillService : AutofillService() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var passwordRepository: PasswordRepository
    private lateinit var autofillPreferences: AutofillPreferences
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 Repository
        val database = PasswordDatabase.getDatabase(applicationContext)
        passwordRepository = PasswordRepository(database.passwordEntryDao())
        
        // 初始化配置
        autofillPreferences = AutofillPreferences(applicationContext)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    /**
     * 处理填充请求
     * 当用户聚焦到可以自动填充的字段时调用
     */
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        android.util.Log.d("MonicaAutofill", "onFillRequest called")
        
        serviceScope.launch {
            try {
                // 检查是否启用自动填充
                val isEnabled = autofillPreferences.isAutofillEnabled.first()
                if (!isEnabled) {
                    android.util.Log.d("MonicaAutofill", "Autofill disabled")
                    callback.onSuccess(null)
                    return@launch
                }
                
                // 解析填充上下文
                val context = request.fillContexts.lastOrNull()
                if (context == null) {
                    android.util.Log.d("MonicaAutofill", "No fill context")
                    callback.onSuccess(null)
                    return@launch
                }
                
                val structure = context.structure
                val parser = AutofillFieldParser(structure)
                val fieldCollection = parser.parse()
                
                if (!fieldCollection.hasCredentialFields()) {
                    android.util.Log.d("MonicaAutofill", "No credential fields found")
                    callback.onSuccess(null)
                    return@launch
                }
                
                // 获取包名或域名
                val packageName = structure.activityComponent.packageName
                val webDomain = parser.extractWebDomain()
                val identifier = webDomain ?: packageName
                
                android.util.Log.d("MonicaAutofill", "Identifier: $identifier (web: $webDomain, package: $packageName)")
                
                // 查找匹配的密码
                val matchStrategy = autofillPreferences.domainMatchStrategy.first()
                val allPasswords = passwordRepository.getAllPasswordEntries().first()
                val matchedPasswords = allPasswords.filter { password ->
                    if (password.website.isBlank()) {
                        false
                    } else {
                        DomainMatcher.matches(password.website, identifier, matchStrategy)
                    }
                }
                
                android.util.Log.d("MonicaAutofill", "Found ${matchedPasswords.size} matched passwords")
                
                if (matchedPasswords.isEmpty()) {
                    callback.onSuccess(null)
                    return@launch
                }
                
                // 构建填充响应
                val responseBuilder = FillResponse.Builder()
                
                // 为每个匹配的密码创建数据集
                matchedPasswords.forEach { password ->
                    val datasetBuilder = Dataset.Builder()
                    
                    // 创建显示视图
                    val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                        setTextViewText(R.id.text_title, password.title)
                        setTextViewText(R.id.text_username, password.username)
                    }
                    
                    // 填充用户名
                    fieldCollection.usernameField?.let { usernameId ->
                        datasetBuilder.setValue(
                            usernameId,
                            AutofillValue.forText(password.username),
                            presentation
                        )
                    }
                    
                    // 填充密码
                    fieldCollection.passwordField?.let { passwordId ->
                        datasetBuilder.setValue(
                            passwordId,
                            AutofillValue.forText(password.password),
                            presentation
                        )
                    }
                    
                    responseBuilder.addDataset(datasetBuilder.build())
                }
                
                // 添加保存信息（如果启用）
                val requestSaveData = autofillPreferences.isRequestSaveDataEnabled.first()
                if (requestSaveData) {
                    val saveInfoBuilder = SaveInfo.Builder(
                        SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                        arrayOf(
                            fieldCollection.usernameField,
                            fieldCollection.passwordField
                        ).filterNotNull().toTypedArray()
                    )
                    responseBuilder.setSaveInfo(saveInfoBuilder.build())
                }
                
                callback.onSuccess(responseBuilder.build())
                
            } catch (e: Exception) {
                android.util.Log.e("MonicaAutofill", "Error in onFillRequest", e)
                callback.onFailure(e.message)
            }
        }
    }
    
    /**
     * 处理保存请求
     * 当用户提交表单时调用，可以保存新的密码或更新现有密码
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        android.util.Log.d("MonicaAutofill", "onSaveRequest called")
        
        serviceScope.launch {
            try {
                val context = request.fillContexts.lastOrNull()
                if (context == null) {
                    callback.onSuccess()
                    return@launch
                }
                
                val structure = context.structure
                val parser = AutofillFieldParser(structure)
                val fieldCollection = parser.parse()
                
                // 提取用户名和密码
                val username = fieldCollection.usernameValue ?: ""
                val password = fieldCollection.passwordValue ?: ""
                
                if (username.isBlank() && password.isBlank()) {
                    callback.onSuccess()
                    return@launch
                }
                
                // 获取包名或域名
                val packageName = structure.activityComponent.packageName
                val webDomain = parser.extractWebDomain()
                val website = webDomain ?: packageName
                
                android.util.Log.d("MonicaAutofill", "Save request - username: $username, website: $website")
                
                // 检查是否启用保存功能
                val requestSaveEnabled = autofillPreferences.isRequestSaveDataEnabled.first()
                if (!requestSaveEnabled) {
                    callback.onSuccess()
                    return@launch
                }
                
                // 启动保存Activity
                val saveIntent = android.content.Intent(applicationContext, AutofillSaveActivity::class.java).apply {
                    putExtra(AutofillSaveActivity.EXTRA_USERNAME, username)
                    putExtra(AutofillSaveActivity.EXTRA_PASSWORD, password)
                    putExtra(AutofillSaveActivity.EXTRA_WEBSITE, website)
                    putExtra(AutofillSaveActivity.EXTRA_PACKAGE_NAME, packageName)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                
                startActivity(saveIntent)
                callback.onSuccess()
                
            } catch (e: Exception) {
                android.util.Log.e("MonicaAutofill", "Error in onSaveRequest", e)
                callback.onFailure(e.message)
            }
        }
    }
    
    override fun onConnected() {
        super.onConnected()
        android.util.Log.d("MonicaAutofill", "Service connected")
    }
    
    override fun onDisconnected() {
        super.onDisconnected()
        android.util.Log.d("MonicaAutofill", "Service disconnected")
    }
}

/**
 * 自动填充字段解析器
 */
private class AutofillFieldParser(private val structure: AssistStructure) {
    
    fun parse(): AutofillFieldCollection {
        val collection = AutofillFieldCollection()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            parseNode(windowNode.rootViewNode, collection)
        }
        
        return collection
    }
    
    private fun parseNode(node: AssistStructure.ViewNode, collection: AutofillFieldCollection) {
        // 检查autofill hints
        node.autofillHints?.forEach { hint ->
            when (hint) {
                android.view.View.AUTOFILL_HINT_USERNAME,
                android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS -> {
                    collection.usernameField = node.autofillId
                    collection.usernameValue = node.autofillValue?.textValue?.toString()
                }
                android.view.View.AUTOFILL_HINT_PASSWORD -> {
                    collection.passwordField = node.autofillId
                    collection.passwordValue = node.autofillValue?.textValue?.toString()
                }
            }
        }
        
        // 如果没有hint，尝试通过ID name推断
        if (node.autofillHints.isNullOrEmpty()) {
            val idEntry = node.idEntry?.lowercase() ?: ""
            val hint = node.hint?.lowercase() ?: ""
            
            when {
                idEntry.contains("user") || idEntry.contains("email") ||
                hint.contains("user") || hint.contains("email") -> {
                    if (collection.usernameField == null) {
                        collection.usernameField = node.autofillId
                        collection.usernameValue = node.autofillValue?.textValue?.toString()
                    }
                }
                idEntry.contains("pass") || hint.contains("pass") -> {
                    if (collection.passwordField == null) {
                        collection.passwordField = node.autofillId
                        collection.passwordValue = node.autofillValue?.textValue?.toString()
                    }
                }
            }
        }
        
        // 递归处理子节点
        for (i in 0 until node.childCount) {
            parseNode(node.getChildAt(i), collection)
        }
    }
    
    fun extractWebDomain(): String? {
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val domain = extractWebDomainFromNode(windowNode.rootViewNode)
            if (domain != null) {
                return domain
            }
        }
        return null
    }
    
    private fun extractWebDomainFromNode(node: AssistStructure.ViewNode): String? {
        // 检查webDomain
        node.webDomain?.let { return it }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val domain = extractWebDomainFromNode(node.getChildAt(i))
            if (domain != null) {
                return domain
            }
        }
        
        return null
    }
}

/**
 * 自动填充字段集合
 */
private data class AutofillFieldCollection(
    var usernameField: AutofillId? = null,
    var passwordField: AutofillId? = null,
    var usernameValue: String? = null,
    var passwordValue: String? = null
) {
    fun hasCredentialFields(): Boolean {
        return usernameField != null || passwordField != null
    }
}

package takagi.ru.monica.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.data.*
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.utils.PwnedPasswordsChecker
import java.security.MessageDigest

/**
 * 安全分析 ViewModel
 */
class SecurityAnalysisViewModel(
    private val passwordRepository: PasswordRepository
) : ViewModel() {
    
    private val TAG = "SecurityAnalysisVM"
    
    private val _analysisData = MutableStateFlow(SecurityAnalysisData())
    val analysisData: StateFlow<SecurityAnalysisData> = _analysisData.asStateFlow()
    
    /**
     * 执行完整的安全分析
     */
    fun performSecurityAnalysis() {
        viewModelScope.launch {
            try {
                _analysisData.value = _analysisData.value.copy(
                    isAnalyzing = true,
                    analysisProgress = 0,
                    error = null
                )
                
                // 获取所有密码
                val passwords = passwordRepository.getAllPasswordEntries().first()
                
                // 1. 分析重复密码 (25%)
                _analysisData.value = _analysisData.value.copy(analysisProgress = 10)
                val duplicatePasswords = analyzeDuplicatePasswords(passwords)
                _analysisData.value = _analysisData.value.copy(
                    duplicatePasswords = duplicatePasswords,
                    analysisProgress = 25
                )
                
                // 2. 分析重复URL (50%)
                val duplicateUrls = analyzeDuplicateUrls(passwords)
                _analysisData.value = _analysisData.value.copy(
                    duplicateUrls = duplicateUrls,
                    analysisProgress = 50
                )
                
                // 3. 检查泄露密码 (75%)
                val compromisedPasswords = checkCompromisedPasswords(passwords) { current, total ->
                    val progress = 50 + ((current.toFloat() / total) * 25).toInt()
                    _analysisData.value = _analysisData.value.copy(analysisProgress = progress)
                }
                _analysisData.value = _analysisData.value.copy(
                    compromisedPasswords = compromisedPasswords,
                    analysisProgress = 75
                )
                
                // 4. 分析未启用2FA的账户 (100%)
                val no2FAAccounts = analyzeNo2FAAccounts(passwords)
                _analysisData.value = _analysisData.value.copy(
                    no2FAAccounts = no2FAAccounts,
                    analysisProgress = 100,
                    isAnalyzing = false
                )
                
                Log.d(TAG, "Security analysis completed successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during security analysis", e)
                _analysisData.value = _analysisData.value.copy(
                    isAnalyzing = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    /**
     * 分析重复使用的密码
     */
    private fun analyzeDuplicatePasswords(passwords: List<PasswordEntry>): List<DuplicatePasswordGroup> {
        val passwordGroups = passwords
            .filter { it.password.isNotBlank() }
            .groupBy { hashPassword(it.password) }
            .filter { it.value.size > 1 }  // 只保留重复的
            .map { (hash, entries) ->
                DuplicatePasswordGroup(
                    passwordHash = hash,
                    count = entries.size,
                    entries = entries
                )
            }
            .sortedByDescending { it.count }
        
        Log.d(TAG, "Found ${passwordGroups.size} duplicate password groups")
        return passwordGroups
    }
    
    /**
     * 分析重复的URL
     */
    private fun analyzeDuplicateUrls(passwords: List<PasswordEntry>): List<DuplicateUrlGroup> {
        val urlGroups = passwords
            .filter { it.website.isNotBlank() }
            .groupBy { normalizeUrl(it.website) }
            .filter { it.value.size > 1 }  // 只保留重复的
            .map { (url, entries) ->
                DuplicateUrlGroup(
                    url = url,
                    count = entries.size,
                    entries = entries
                )
            }
            .sortedByDescending { it.count }
        
        Log.d(TAG, "Found ${urlGroups.size} duplicate URL groups")
        return urlGroups
    }
    
    /**
     * 检查泄露的密码
     */
    private suspend fun checkCompromisedPasswords(
        passwords: List<PasswordEntry>,
        onProgress: (Int, Int) -> Unit
    ): List<CompromisedPassword> {
        val uniquePasswords = passwords
            .filter { it.password.isNotBlank() }
            .distinctBy { it.password }
        
        val compromisedList = mutableListOf<CompromisedPassword>()
        
        uniquePasswords.forEachIndexed { index, entry ->
            try {
                val breachCount = PwnedPasswordsChecker.checkPassword(entry.password)
                if (breachCount > 0) {
                    compromisedList.add(
                        CompromisedPassword(
                            entry = entry,
                            breachCount = breachCount
                        )
                    )
                    Log.d(TAG, "Found compromised password: ${entry.title}, count: $breachCount")
                }
                onProgress(index + 1, uniquePasswords.size)
                
                // API限流延迟
                if (index < uniquePasswords.size - 1) {
                    kotlinx.coroutines.delay(1600)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking password for ${entry.title}", e)
            }
        }
        
        return compromisedList.sortedByDescending { it.breachCount }
    }
    
    /**
     * 分析未启用2FA的账户
     * 基于常见支持2FA的网站列表
     */
    private fun analyzeNo2FAAccounts(passwords: List<PasswordEntry>): List<No2FAAccount> {
        // 常见支持2FA的网站域名列表
        val supports2FA = setOf(
            "google.com", "gmail.com", "facebook.com", "twitter.com", "x.com",
            "github.com", "microsoft.com", "apple.com", "amazon.com",
            "dropbox.com", "linkedin.com", "instagram.com", "reddit.com",
            "slack.com", "discord.com", "paypal.com", "netflix.com",
            "yahoo.com", "outlook.com", "icloud.com", "twitch.tv",
            "steam.com", "epic.com", "battle.net", "riot.com"
        )
        
        val no2FAList = passwords
            .filter { it.website.isNotBlank() }
            .mapNotNull { entry ->
                val domain = extractDomain(entry.website)
                if (domain.isNotBlank()) {
                    val supports = supports2FA.any { domain.contains(it, ignoreCase = true) }
                    No2FAAccount(
                        entry = entry,
                        domain = domain,
                        supports2FA = supports
                    )
                } else {
                    null
                }
            }
            // 优先显示已知支持2FA但未启用的
            .sortedByDescending { it.supports2FA }
        
        Log.d(TAG, "Found ${no2FAList.size} accounts without 2FA")
        return no2FAList
    }
    
    /**
     * 哈希密码用于分组（不用于安全目的）
     */
    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 标准化URL
     */
    private fun normalizeUrl(url: String): String {
        return url.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .trimEnd('/')
    }
    
    /**
     * 提取域名
     */
    private fun extractDomain(url: String): String {
        val normalized = normalizeUrl(url)
        return normalized.split("/").firstOrNull() ?: ""
    }
}

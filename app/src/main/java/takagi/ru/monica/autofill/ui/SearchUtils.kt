package takagi.ru.monica.autofill.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import takagi.ru.monica.autofill.data.PaymentInfo
import takagi.ru.monica.data.PasswordEntry

/**
 * 搜索防抖Hook
 * 
 * 延迟执行搜索,避免频繁触发过滤操作
 * 
 * @param value 输入值
 * @param delayMillis 延迟时间(毫秒)
 * @return 防抖后的值
 */
@Composable
fun rememberDebouncedValue(
    value: String,
    delayMillis: Long = 300L
): State<String> {
    var debouncedValue by remember { mutableStateOf(value) }
    
    LaunchedEffect(value) {
        delay(delayMillis)
        debouncedValue = value
    }
    
    return remember { derivedStateOf { debouncedValue } }
}

/**
 * 过滤密码列表
 * 
 * 根据搜索查询过滤密码,支持标题、用户名、网站和应用名称匹配
 * 
 * @param passwords 原始密码列表
 * @param query 搜索查询
 * @return 过滤后的密码列表
 */
fun filterPasswords(
    passwords: List<PasswordEntry>,
    query: String
): List<PasswordEntry> {
    if (query.isBlank()) {
        return passwords
    }
    
    val searchQuery = query.trim().lowercase()
    
    return passwords.filter { password ->
        password.title.lowercase().contains(searchQuery) ||
        password.username.lowercase().contains(searchQuery) ||
        password.website.lowercase().contains(searchQuery) ||
        password.appName.lowercase().contains(searchQuery)
    }
}

/**
 * 过滤账单信息列表
 * 
 * 根据搜索查询过滤账单信息,支持持卡人姓名和卡号后四位匹配
 * 
 * @param paymentInfo 原始账单信息列表
 * @param query 搜索查询
 * @return 过滤后的账单信息列表
 */
fun filterPaymentInfo(
    paymentInfo: List<PaymentInfo>,
    query: String
): List<PaymentInfo> {
    if (query.isBlank()) {
        return paymentInfo
    }
    
    val searchQuery = query.trim().lowercase()
    
    return paymentInfo.filter { info ->
        info.cardHolderName.lowercase().contains(searchQuery) ||
        info.cardNumber.takeLast(4).contains(searchQuery)
    }
}

/**
 * 使用derivedStateOf优化的密码过滤
 * 
 * 避免不必要的重组
 * 
 * @param passwords 密码列表
 * @param query 搜索查询
 * @return 过滤后的密码列表State
 */
@Composable
fun rememberFilteredPasswords(
    passwords: List<PasswordEntry>,
    query: String
): State<List<PasswordEntry>> {
    return remember(passwords, query) {
        derivedStateOf {
            filterPasswords(passwords, query)
        }
    }
}

/**
 * 使用derivedStateOf优化的账单信息过滤
 * 
 * 避免不必要的重组
 * 
 * @param paymentInfo 账单信息列表
 * @param query 搜索查询
 * @return 过滤后的账单信息列表State
 */
@Composable
fun rememberFilteredPaymentInfo(
    paymentInfo: List<PaymentInfo>,
    query: String
): State<List<PaymentInfo>> {
    return remember(paymentInfo, query) {
        derivedStateOf {
            filterPaymentInfo(paymentInfo, query)
        }
    }
}

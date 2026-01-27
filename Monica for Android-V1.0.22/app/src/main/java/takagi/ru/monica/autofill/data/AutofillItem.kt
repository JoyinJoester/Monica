package takagi.ru.monica.autofill.data

import takagi.ru.monica.data.PasswordEntry

/**
 * 自动填充项密封类
 * 
 * 统一处理密码和账单信息的数据结构
 */
sealed class AutofillItem {
    /**
     * 密码条目
     */
    data class Password(val entry: PasswordEntry) : AutofillItem()
    
    /**
     * 账单信息
     */
    data class Payment(val info: PaymentInfo) : AutofillItem()
}

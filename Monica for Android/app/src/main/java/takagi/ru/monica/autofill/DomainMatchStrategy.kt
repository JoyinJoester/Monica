package takagi.ru.monica.autofill

import android.content.Context
import takagi.ru.monica.R

/**
 * 域名匹配策略
 */
enum class DomainMatchStrategy {
    /**
     * 主域名匹配 - 例如: example.com 匹配 www.example.com, login.example.com
     */
    BASE_DOMAIN,
    
    /**
     * 域匹配 - 例如: example.com 匹配 example.com 和所有子域名
     */
    DOMAIN,
    
    /**
     * 前缀匹配 - 检查URL是否以指定字符串开头
     */
    STARTS_WITH,
    
    /**
     * 完全匹配 - URL必须完全相同
     */
    EXACT_MATCH,
    
    /**
     * 正则表达式匹配 - 使用正则表达式匹配
     */
    REGEX,
    
    /**
     * 从不匹配 - 禁用此条目的自动填充
     */
    NEVER;
    
    companion object {
        fun getDisplayName(context: Context, strategy: DomainMatchStrategy): String {
            return when (strategy) {
                BASE_DOMAIN -> context.getString(R.string.autofill_domain_strategy_base_domain)
                DOMAIN -> context.getString(R.string.autofill_domain_strategy_domain)
                STARTS_WITH -> context.getString(R.string.autofill_domain_strategy_starts_with)
                EXACT_MATCH -> context.getString(R.string.autofill_domain_strategy_exact_match)
                REGEX -> context.getString(R.string.autofill_domain_strategy_regex)
                NEVER -> context.getString(R.string.autofill_domain_strategy_never)
            }
        }
        
        fun getDescription(context: Context, strategy: DomainMatchStrategy): String {
            return when (strategy) {
                BASE_DOMAIN -> context.getString(R.string.autofill_domain_strategy_base_domain_desc)
                DOMAIN -> context.getString(R.string.autofill_domain_strategy_domain_desc)
                STARTS_WITH -> context.getString(R.string.autofill_domain_strategy_starts_with_desc)
                EXACT_MATCH -> context.getString(R.string.autofill_domain_strategy_exact_match_desc)
                REGEX -> context.getString(R.string.autofill_domain_strategy_regex_desc)
                NEVER -> context.getString(R.string.autofill_domain_strategy_never_desc)
            }
        }
    }
}


package takagi.ru.monica.autofill

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
        fun getDisplayName(strategy: DomainMatchStrategy): String {
            return when (strategy) {
                BASE_DOMAIN -> "主域名"
                DOMAIN -> "域"
                STARTS_WITH -> "匹配开头"
                EXACT_MATCH -> "完全匹配"
                REGEX -> "正则表达式"
                NEVER -> "从不"
            }
        }
        
        fun getDescription(strategy: DomainMatchStrategy): String {
            return when (strategy) {
                BASE_DOMAIN -> "匹配主域名及所有子域名 (推荐)"
                DOMAIN -> "仅匹配指定域名和子域名"
                STARTS_WITH -> "URL以指定字符串开头即匹配"
                EXACT_MATCH -> "URL必须完全相同才匹配"
                REGEX -> "使用正则表达式进行高级匹配"
                NEVER -> "禁用自动填充"
            }
        }
    }
}

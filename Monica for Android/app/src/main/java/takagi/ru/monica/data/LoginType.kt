package takagi.ru.monica.data

/**
 * 登录类型枚举
 * 用于标识密码条目的登录方式
 */
enum class LoginType {
    /** 使用账号密码登录 */
    PASSWORD,
    /** 使用第三方SSO登录 */
    SSO
}

/**
 * SSO提供商枚举
 * 支持的第三方登录提供商
 */
enum class SsoProvider(val displayName: String, val icon: String) {
    GOOGLE("Google", "google"),
    APPLE("Apple", "apple"),
    FACEBOOK("Facebook", "facebook"),
    MICROSOFT("Microsoft", "microsoft"),
    GITHUB("GitHub", "github"),
    TWITTER("Twitter/X", "twitter"),
    WECHAT("微信", "wechat"),
    QQ("QQ", "qq"),
    WEIBO("微博", "weibo"),
    OTHER("其他", "other");
    
    companion object {
        fun fromName(name: String): SsoProvider {
            return entries.find { it.name == name } ?: OTHER
        }
    }
}

package takagi.ru.monica.webdav

/**
 * WebDAV 客户端凭据的内存表示。
 *
 * 仅用于在拦截器与 [WebDavGateway] 之间传递用户名/密码；实例不得直接写入日志。
 */
data class WebDavCredentials(
    val username: String,
    val password: String,
) {
    /**
     * 是否需要在请求中携带 Authorization 头。
     *
     * 当用户名或密码任一不为空（去除首尾空白后）时返回 true。
     */
    val hasValue: Boolean
        get() = username.trim().isNotEmpty() || password.isNotEmpty()

    override fun toString(): String = "WebDavCredentials(user=$username, password=***)"

    companion object {
        /** 未配置凭据时的占位值。 */
        val EMPTY: WebDavCredentials = WebDavCredentials("", "")
    }
}

package takagi.ru.monica.webdav

/**
 * WebDAV 调用的错误分类。
 *
 * 由 [WebDavErrorClassifier.classify] 产出，供 UI 错误提示与
 * [takagi.ru.monica.workers.AutoBackupWorker] 调度决策使用。
 */
enum class WebDavErrorKind {
    /** 调用成功。 */
    Ok,

    /**
     * 服务器返回 429 或带 Retry-After 的 503，或本地 backoff 阻止了请求。
     *
     * 携带的建议等待时长见 [WebDavErrorClassifier.suggestedWaitMillis]。
     */
    RateLimited,

    /** 用户凭据错误 / 无权限：HTTP 401/403。 */
    AuthFailed,

    /** 目标资源不存在：HTTP 404。 */
    NotFound,

    /**
     * 服务器不支持该 WebDAV 方法或需要不同路径：HTTP 405/501。
     */
    MethodNotAllowed,

    /** DNS 无法解析 / 无路由 / 连接被拒。 */
    NetworkUnreachable,

    /** 建立连接或读取响应超时。 */
    Timeout,

    /** 服务器返回的 WebDAV XML 无法解析。 */
    MalformedResponse,

    /** 未能分类的其他错误。 */
    Unknown,
}

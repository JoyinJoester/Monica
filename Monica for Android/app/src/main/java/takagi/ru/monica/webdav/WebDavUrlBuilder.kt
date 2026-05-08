package takagi.ru.monica.webdav

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 构造/规范化 WebDAV 请求 URL。
 *
 * 基于 OkHttp 的 [HttpUrl] 做解析与 percent-encode，保证每个 path 段都按
 * RFC 3986 编码，保留 `/` 作为分隔符。
 */
object WebDavUrlBuilder {

    private const val DAV_SEGMENT = "dav"

    /**
     * 规范化用户输入的服务器 URL。
     *
     * 行为：
     * - 去除首尾空白。
     * - 补齐 scheme（默认 `http://`）。
     * - 去除 path 末尾多余的 `/`（保证幂等）。
     * - 保留原始 path 段（含中文、特殊字符）的可读性：以解码形式出现在返回值里。
     *
     * 返回的字符串满足 `normalizeServer(normalizeServer(x)) == normalizeServer(x)`。
     */
    fun normalizeServer(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val parsed = withScheme.toHttpUrlOrNull() ?: return withScheme.trimEnd('/')

        val schemeAndHost = buildString {
            append(parsed.scheme).append("://")
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
                append(parsed.encodedUsername)
                if (parsed.password.isNotEmpty()) {
                    append(':').append(parsed.encodedPassword)
                }
                append('@')
            }
            append(parsed.host)
            val defaultPort = HttpUrl.defaultPort(parsed.scheme)
            if (parsed.port != defaultPort) {
                append(':').append(parsed.port)
            }
        }

        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        val pathPart = if (segments.isEmpty()) {
            ""
        } else {
            segments.joinToString(prefix = "/", separator = "/")
        }
        return schemeAndHost + pathPart
    }

    /**
     * 将 [child] 追加到 [base] 后面，按段 percent-encode 并保留 `/` 作为分隔符。
     *
     * - [child] 允许以 `/` 开头或结尾，空段会被忽略（避免出现 `//`）。
     * - 结果通过 [HttpUrl.Builder.addPathSegment] 构造，因此中文、空格等字符
     *   会按 RFC 3986 正确编码。
     * - 若 [base] 本身无法被解析为 URL，退回为简单字符串拼接。
     */
    fun join(base: String, child: String): String {
        val normalizedBase = normalizeServer(base)
        val cleanedChild = child.replace('\\', '/').trim().trim('/')
        if (cleanedChild.isEmpty()) return normalizedBase

        val baseUrl = normalizedBase.toHttpUrlOrNull()
            ?: return "$normalizedBase/${cleanedChild.trim('/')}"

        val builder = baseUrl.newBuilder()
        cleanedChild.split('/')
            .filter { it.isNotEmpty() }
            .forEach { segment -> builder.addPathSegment(segment) }
        return builder.build().toString().trimEnd('/')
    }

    /**
     * 根据规范化后的 URL 生成连接候选列表。
     *
     * 规则：
     * - 若 [base] 已经包含路径（非空也非仅 `/`），返回单一候选 `[base]`。
     * - 否则返回 `[base, base + "/dav"]`，用于覆盖 OpenList/AList 常见挂载根。
     * - 若 [base] 的 path 段内已经包含 `dav`，始终只返回单一候选。
     */
    fun candidates(base: String): List<String> {
        val normalized = normalizeServer(base)
        if (normalized.isEmpty()) return emptyList()

        val parsed = normalized.toHttpUrlOrNull() ?: return listOf(normalized)
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        val hasDav = segments.any { it.equals(DAV_SEGMENT, ignoreCase = true) }
        if (hasDav) return listOf(normalized)
        if (segments.isNotEmpty()) return listOf(normalized)

        val davCandidate = join(normalized, DAV_SEGMENT)
        return if (davCandidate == normalized) listOf(normalized)
        else listOf(normalized, davCandidate)
    }

    /**
     * 从任意 URL 字符串中提取 host 部分。若无法解析返回空串。
     */
    fun hostOf(url: String): String {
        val normalized = normalizeServer(url).ifEmpty { return "" }
        return normalized.toHttpUrlOrNull()?.host.orEmpty()
    }
}

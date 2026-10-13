package takagi.ru.monica.util

import android.net.Uri
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * OTP URI 解析工具
 *
 * 支持:
 * - otpauth://totp
 * - otpauth://hotp
 * - otpauth://yaotp
 * - motp://
 * - otpauth-migration://offline?data=...
 * - phonefactor:// (显式标记为不支持)
 *
 * 例如: otpauth://totp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example
 * 例如: otpauth://hotp/Example:user@example.com?secret=JBSWY3DPEHPK3PXP&counter=0
 */
object TotpUriParser {

    private val motpRegex = Regex("^motp://(.*?):(.*?)\\?(.*)$", RegexOption.IGNORE_CASE)
    private const val migrationScheme = "otpauth-migration"
    private const val migrationAuthority = "offline"
    private const val maxMigrationUriLength = 64 * 1024
    private const val maxMigrationPayloadBytes = 48 * 1024
    private const val maxMigrationItems = 256
    private const val maxMigrationSecretBytes = 1024
    private const val maxMigrationTextBytes = 4096
    private const val migrationPayloadField = 1
    private const val migrationVersionField = 2
    private const val migrationBatchSizeField = 3
    private const val migrationBatchIndexField = 4
    private const val migrationBatchIdField = 5
    private const val otpSecretField = 1
    private const val otpUsernameField = 2
    private const val otpIssuerField = 3
    private const val otpAlgorithmField = 4
    private const val otpDigitsField = 5
    private const val otpTypeField = 6
    private const val otpCounterField = 7

    /**
     * 解析标准 URI（otpauth/motp）
     *
     * @param uri OTP URI字符串
     * @return 解析结果，包含TotpData、账户名和标签
     */
    fun parseUri(uri: String): TotpParseResult? {
        val normalized = uri.trim()
        val lower = normalized.lowercase()
        return when {
            lower.startsWith("otpauth://") -> parseOtpAuthUri(normalized)
            lower.startsWith("motp://") -> parseMotpUri(normalized)
            else -> null
        }
    }

    /**
     * 解析扫码得到的内容，支持单条与批量（migration）
     */
    fun parseScannedContent(content: String): TotpScanParseResult {
        val normalized = content.trim()
        val lower = normalized.lowercase()
        return when {
            lower.startsWith("$migrationScheme://") -> {
                when (val migration = parseOtpAuthMigrationUri(normalized)) {
                    is MigrationParseOutcome.Success -> when {
                        migration.items.size == 1 -> TotpScanParseResult.Single(migration.items.first())
                        else -> TotpScanParseResult.Multiple(migration.items)
                    }
                    is MigrationParseOutcome.Failure -> {
                        TotpScanParseResult.MigrationFailure(migration.reason)
                    }
                }
            }
            lower.startsWith("otpauth://") || lower.startsWith("motp://") -> {
                parseUri(normalized)
                    ?.let { TotpScanParseResult.Single(it) }
                    ?: TotpScanParseResult.InvalidFormat
            }
            lower.startsWith("phonefactor://") -> TotpScanParseResult.UnsupportedPhoneFactor
            else -> TotpScanParseResult.InvalidFormat
        }
    }

    private fun parseOtpAuthUri(uri: String): TotpParseResult? {
        try {
            val parsedUri = Uri.parse(uri)
            if (!parsedUri.scheme.equals("otpauth", ignoreCase = true)) {
                return null
            }

            val authority = parsedUri.authority?.lowercase() ?: return null
            if (authority != "totp" && authority != "hotp" && authority != "yaotp") {
                return null
            }

            // 获取密钥（必需）
            val secret = parsedUri.getQueryParameter("secret")?.trim()?.takeIf { it.isNotEmpty() } ?: return null

            // 获取可选参数
            val issuer = parsedUri.getQueryParameter("issuer")?.trim() ?: ""
            val algorithm = (parsedUri.getQueryParameter("algorithm") ?: "SHA1").uppercase()
            val digits = parsedUri.getQueryParameter("digits")?.toIntOrNull() ?: 6
            val period = parsedUri.getQueryParameter("period")?.toIntOrNull() ?: 30

            // HOTP特有参数
            val counter = parsedUri.getQueryParameter("counter")?.toLongOrNull() ?: 0L

            // 解析路径以获取标签和账户名
            // 格式: otpauth://totp/Label?...
            // 或: otpauth://totp/Issuer:AccountName?...
            val path = parsedUri.path?.removePrefix("/") ?: ""
            val (label, labelAccountName) = parseLabel(path)
            val accountName = if (issuer.isNotBlank() && label.startsWith("$issuer:"))
                label.removePrefix("$issuer:") else labelAccountName

            // 如果URI中没有issuer参数，尝试从label中提取
            val finalIssuer = if (issuer.isBlank() && label.contains(":")) {
                label.substringBefore(":").trim()
            } else {
                issuer
            }

            // 检测特殊服务提供商并设置相应的OTP类型
            val otpType = detectOtpType(authority, finalIssuer, parsedUri)

            // 根据检测到的类型调整参数
            val finalDigits = when (otpType) {
                OtpType.STEAM -> 5  // Steam固定5位
                else -> digits
            }

            val totpData = TotpData(
                secret = secret,
                issuer = finalIssuer,
                accountName = accountName,
                period = period,
                digits = finalDigits,
                algorithm = algorithm,
                otpType = otpType,
                counter = counter,
                pin = parsedUri.getQueryParameter("pin").orEmpty()
            )

            return TotpParseResult(
                totpData = totpData,
                label = label,
                accountName = accountName
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * 检测OTP类型
     * 根据URI类型、issuer名称和特殊参数判断
     */
    private fun detectOtpType(authority: String, issuer: String, uri: Uri): OtpType {
        if (authority == "hotp") {
            return OtpType.HOTP
        }
        if (authority == "yaotp") {
            return OtpType.YANDEX
        }

        // 检查issuer中是否包含特殊服务提供商名称
        val issuerLower = issuer.lowercase()
        when {
            issuerLower.contains("steam") -> return OtpType.STEAM
            issuerLower.contains("yandex") -> return OtpType.YANDEX
        }

        // 检查是否有Steam特殊参数
        val encoder = uri.getQueryParameter("encoder")?.lowercase()
        if (encoder == "steam") {
            return OtpType.STEAM
        }

        // 默认为TOTP
        return OtpType.TOTP
    }

    private fun parseMotpUri(uri: String): TotpParseResult? {
        // Split URI components before decoding so encoded ':' and '&' stay inside their values.
        val match = motpRegex.matchEntire(uri) ?: return null

        val issuerRaw = Uri.decode(match.groupValues[1]).trim()
        val accountName = Uri.decode(match.groupValues[2]).trim()
        val queryParams = parseQueryParams(match.groupValues[3])
        val secret = queryParams["secret"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val issuer = issuerRaw.ifBlank { accountName }.ifBlank { "mOTP" }
        val label = if (accountName.isNotBlank()) "$issuer:$accountName" else issuer

        return TotpParseResult(
            totpData = TotpData(
                secret = secret,
                issuer = issuer,
                accountName = accountName,
                period = 10,
                digits = 6,
                algorithm = "SHA1",
                otpType = OtpType.MOTP,
                pin = queryParams["pin"].orEmpty()
            ),
            label = label,
            accountName = accountName
        )
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        query.split("&").forEach { segment ->
            if (segment.isBlank()) return@forEach
            val key = segment.substringBefore("=")
            val value = segment.substringAfter("=", "")
            if (key.isNotBlank()) {
                result[Uri.decode(key)] = Uri.decode(value)
            }
        }
        return result
    }

    private fun parseOtpAuthMigrationUri(uri: String): MigrationParseOutcome {
        return try {
            val encodedData = extractMigrationData(uri)
            val payload = decodeMigrationPayload(encodedData)
            val parsedPayload = parseMigrationPayload(payload)
            validateMigrationBatch(parsedPayload)
            val items = parsedPayload.items.map(::toParseResult)
            if (items.isEmpty()) {
                migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
            }
            MigrationParseOutcome.Success(items)
        } catch (error: MigrationParseException) {
            MigrationParseOutcome.Failure(error.reason)
        } catch (_: Exception) {
            MigrationParseOutcome.Failure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }
    }

    private fun extractMigrationData(uri: String): String {
        if (uri.length > maxMigrationUriLength) {
            migrationFailure(MigrationFailureReason.PAYLOAD_TOO_LARGE)
        }

        val parsed = runCatching { URI(uri) }.getOrElse {
            migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }
        if (
            !parsed.scheme.equals(migrationScheme, ignoreCase = true) ||
            !parsed.rawAuthority.equals(migrationAuthority, ignoreCase = true) ||
            parsed.rawFragment != null
        ) {
            migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }

        val rawQuery = parsed.rawQuery ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        var encodedData: String? = null
        rawQuery.split('&').forEach { segment ->
            val rawKey = segment.substringBefore('=', "")
            val rawValue = segment.substringAfter('=', "")
            val key = decodeQueryComponent(rawKey)
            if (key == "data") {
                if (encodedData != null || rawValue.isBlank()) {
                    migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                encodedData = decodeQueryComponent(rawValue)
            }
        }
        return encodedData ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
    }

    private fun decodeQueryComponent(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrElse {
            migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }
    }

    private fun decodeMigrationPayload(encodedData: String): ByteArray {
        if (encodedData.length > maxMigrationUriLength) {
            migrationFailure(MigrationFailureReason.PAYLOAD_TOO_LARGE)
        }

        var base64 = encodedData
            .replace(' ', '+')
            .replace('-', '+')
            .replace('_', '/')
        val padding = (4 - (base64.length % 4)) % 4
        if (padding > 0) {
            base64 += "=".repeat(padding)
        }

        val payload = runCatching { Base64.getDecoder().decode(base64) }.getOrElse {
            migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }
        if (payload.isEmpty()) {
            migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }
        if (payload.size > maxMigrationPayloadBytes) {
            migrationFailure(MigrationFailureReason.PAYLOAD_TOO_LARGE)
        }
        return payload
    }

    private fun parseMigrationPayload(payload: ByteArray): MigrationPayloadRaw {
        val reader = ProtoReader(payload)
        val result = mutableListOf<MigrationOtpRaw>()
        var version: Int? = null
        var batchSize: Int? = null
        var batchIndex: Int? = null
        var batchId: Int? = null

        while (!reader.isAtEnd()) {
            val tag = reader.readTag() ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (fieldNumber) {
                migrationPayloadField -> {
                    requireWireType(wireType, WireType.LENGTH_DELIMITED)
                    if (result.size >= maxMigrationItems) {
                        migrationFailure(MigrationFailureReason.PAYLOAD_TOO_LARGE)
                    }
                    val messageBytes = reader.readBytes()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                    result += parseMigrationAuthenticator(messageBytes)
                }
                migrationVersionField -> {
                    requireWireType(wireType, WireType.VARINT)
                    if (version != null) migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                    version = reader.readVarInt32()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                migrationBatchSizeField -> {
                    requireWireType(wireType, WireType.VARINT)
                    if (batchSize != null) migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                    batchSize = reader.readVarInt32()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                migrationBatchIndexField -> {
                    requireWireType(wireType, WireType.VARINT)
                    if (batchIndex != null) migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                    batchIndex = reader.readVarInt32()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                migrationBatchIdField -> {
                    requireWireType(wireType, WireType.VARINT)
                    if (batchId != null) migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                    batchId = reader.readVarInt32()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                else -> if (!reader.skipField(wireType)) {
                    migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
            }
        }

        return MigrationPayloadRaw(
            items = result,
            version = version,
            batchSize = batchSize,
            batchIndex = batchIndex,
            batchId = batchId
        )
    }

    private fun validateMigrationBatch(payload: MigrationPayloadRaw) {
        if (payload.items.isEmpty()) {
            migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }
        if (payload.version != null && payload.version !in 1..2) {
            migrationFailure(MigrationFailureReason.UNSUPPORTED_VERSION)
        }

        val batchSize = payload.batchSize ?: 1
        val batchIndex = payload.batchIndex ?: 0
        if (batchSize <= 0 || batchIndex < 0 || batchIndex >= batchSize) {
            migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }
        if (batchSize > 1) {
            migrationFailure(MigrationFailureReason.MULTI_QR_BATCH)
        }
    }

    private fun parseMigrationAuthenticator(bytes: ByteArray): MigrationOtpRaw {
        val reader = ProtoReader(bytes)
        var secret = ByteArray(0)
        var username = ""
        var issuer = ""
        var algorithm: Int? = null
        var digits: Int? = null
        var type: Int? = null
        var counter = 0L

        while (!reader.isAtEnd()) {
            val tag = reader.readTag() ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (fieldNumber) {
                otpSecretField -> {
                    requireWireType(wireType, WireType.LENGTH_DELIMITED)
                    secret = reader.readBytes()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                    if (secret.size > maxMigrationSecretBytes) {
                        migrationFailure(MigrationFailureReason.PAYLOAD_TOO_LARGE)
                    }
                }
                otpUsernameField -> {
                    requireWireType(wireType, WireType.LENGTH_DELIMITED)
                    username = reader.readString(maxMigrationTextBytes)
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                otpIssuerField -> {
                    requireWireType(wireType, WireType.LENGTH_DELIMITED)
                    issuer = reader.readString(maxMigrationTextBytes)
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                otpAlgorithmField -> {
                    requireWireType(wireType, WireType.VARINT)
                    algorithm = reader.readVarInt32()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                otpDigitsField -> {
                    requireWireType(wireType, WireType.VARINT)
                    digits = reader.readVarInt32()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                otpTypeField -> {
                    requireWireType(wireType, WireType.VARINT)
                    type = reader.readVarInt32()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                otpCounterField -> {
                    requireWireType(wireType, WireType.VARINT)
                    counter = reader.readVarInt64()
                        ?: migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
                else -> if (!reader.skipField(wireType)) {
                    migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
                }
            }
        }

        if (secret.isEmpty()) {
            migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }

        return MigrationOtpRaw(
            secret = secret,
            username = username,
            issuer = issuer,
            algorithm = algorithm,
            digits = digits,
            type = type,
            counter = counter
        )
    }

    private fun toParseResult(raw: MigrationOtpRaw): TotpParseResult {
        val otpType = when (raw.type) {
            1 -> OtpType.HOTP
            2 -> OtpType.TOTP
            else -> migrationFailure(MigrationFailureReason.UNSUPPORTED_OTP_TYPE)
        }

        val algorithm = when (raw.algorithm) {
            1 -> "SHA1"
            2 -> "SHA256"
            3 -> "SHA512"
            else -> migrationFailure(MigrationFailureReason.UNSUPPORTED_ALGORITHM)
        }

        val digits = when (raw.digits) {
            1 -> 6
            2 -> 8
            else -> migrationFailure(MigrationFailureReason.UNSUPPORTED_DIGITS)
        }

        val issuer = raw.issuer.trim()
        var accountName = raw.username.trim()
        if (issuer.isNotBlank() && accountName.startsWith("$issuer:", ignoreCase = true)) {
            accountName = accountName.substring(issuer.length + 1).trim()
        }

        if (issuer.isBlank() && accountName.isBlank()) {
            migrationFailure(MigrationFailureReason.MISSING_ACCOUNT_NAME)
        }

        val label = when {
            issuer.isNotBlank() && accountName.isNotBlank() -> "$issuer:$accountName"
            issuer.isNotBlank() -> issuer
            else -> accountName
        }
        val secretBase32 = base32Encode(raw.secret)

        return TotpParseResult(
            totpData = TotpData(
                secret = secretBase32,
                issuer = issuer,
                accountName = accountName,
                period = 30,
                digits = digits,
                algorithm = algorithm,
                otpType = otpType,
                counter = if (otpType == OtpType.HOTP) raw.counter else 0L,
                pin = ""
            ),
            label = label,
            accountName = accountName
        )
    }

    private fun requireWireType(actual: Int, expected: Int) {
        if (actual != expected) {
            migrationFailure(MigrationFailureReason.MALFORMED_PAYLOAD)
        }
    }

    private fun migrationFailure(reason: MigrationFailureReason): Nothing {
        throw MigrationParseException(reason)
    }

    private fun base32Encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val output = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0

        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                output.append(alphabet[index])
                bitsLeft -= 5
            }
        }

        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            output.append(alphabet[index])
        }

        return output.toString()
    }

    /**
     * 解析标签
     *
     * 格式1: "Example:user@example.com" -> ("Example:user@example.com", "user@example.com")
     * 格式2: "user@example.com" -> ("user@example.com", "user@example.com")
     */
    private fun parseLabel(label: String): Pair<String, String> {
        val decodedLabel = label

        return if (decodedLabel.contains(":")) {
            val parts = decodedLabel.split(":", limit = 2)
            decodedLabel to parts[1]
        } else {
            decodedLabel to decodedLabel
        }
    }
    
    /**
     * 生成OTP URI
     *
     * @param label 标签（通常是 "Issuer:AccountName"）
     * @param totpData OTP数据
     * @return otpauth:// URI字符串
     */
    fun generateUri(label: String, totpData: TotpData, includePin: Boolean = false): String {
        if (totpData.otpType == OtpType.MOTP) {
            val issuer = totpData.issuer.ifBlank { label.substringBefore(":", label) }
            val accountName = totpData.accountName.ifBlank {
                if (label.contains(":")) label.substringAfter(":") else ""
            }
            val encodedIssuer = Uri.encode(issuer)
            val encodedAccountName = Uri.encode(accountName)
            val encodedSecret = Uri.encode(totpData.secret)
            val pinParameter = if (includePin && totpData.pin.isNotBlank()) "&pin=${Uri.encode(totpData.pin)}" else ""
            return "motp://$encodedIssuer:$encodedAccountName?secret=$encodedSecret$pinParameter"
        }

        // 根据OTP类型选择authority
        val authority = when (totpData.otpType) {
            OtpType.HOTP -> "hotp"
            OtpType.YANDEX -> "yaotp"
            else -> "totp"
        }

        val builder = Uri.Builder()
            .scheme("otpauth")
            .authority(authority)
            .appendPath(label)
            .appendQueryParameter("secret", totpData.secret)

        if (totpData.issuer.isNotBlank()) {
            builder.appendQueryParameter("issuer", totpData.issuer)
        }

        // HOTP需要counter参数
        if (totpData.otpType == OtpType.HOTP) {
            builder.appendQueryParameter("counter", totpData.counter.toString())
        }

        if (totpData.period != 30 && totpData.otpType != OtpType.HOTP) {
            builder.appendQueryParameter("period", totpData.period.toString())
        }

        if (totpData.digits != 6) {
            builder.appendQueryParameter("digits", totpData.digits.toString())
        }

        if (totpData.algorithm != "SHA1") {
            builder.appendQueryParameter("algorithm", totpData.algorithm)
        }

        if (includePin && totpData.pin.isNotBlank()) {
            builder.appendQueryParameter("pin", totpData.pin)
        }

        // Steam特殊标记
        if (totpData.otpType == OtpType.STEAM) {
            builder.appendQueryParameter("encoder", "steam")
        }

        return builder.build().toString()
    }

    /**
     * 生成OTP URI (兼容旧版本API)
     *
     * @param label 标签（通常是 "Issuer:AccountName"）
     * @param secret Base32编码的密钥
     * @param issuer 发行者
     * @param accountName 账户名
     * @param period 时间周期
     * @param digits 验证码位数
     * @param algorithm 算法
     * @return otpauth:// URI字符串
     */
    @Deprecated("使用 generateUri(label, totpData) 代替",
        ReplaceWith("generateUri(label, TotpData(secret, issuer, accountName, period, digits, algorithm))"))
    fun generateUri(
        label: String,
        secret: String,
        issuer: String = "",
        accountName: String = "",
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1"
    ): String {
        val resolvedLabel = if (label.isNotBlank()) {
            label
        } else {
            listOf(issuer, accountName).filter { it.isNotBlank() }.joinToString(":")
        }
        val builder = Uri.Builder()
            .scheme("otpauth")
            .authority("totp")
            .appendPath(resolvedLabel)
            .appendQueryParameter("secret", secret)

        if (issuer.isNotBlank()) {
            builder.appendQueryParameter("issuer", issuer)
        }

        if (period != 30) {
            builder.appendQueryParameter("period", period.toString())
        }

        if (digits != 6) {
            builder.appendQueryParameter("digits", digits.toString())
        }

        if (algorithm != "SHA1") {
            builder.appendQueryParameter("algorithm", algorithm)
        }

        return builder.build().toString()
    }

    private object WireType {
        const val VARINT = 0
        const val FIXED64 = 1
        const val LENGTH_DELIMITED = 2
        const val FIXED32 = 5
    }

    private data class MigrationOtpRaw(
        val secret: ByteArray,
        val username: String,
        val issuer: String,
        val algorithm: Int?,
        val digits: Int?,
        val type: Int?,
        val counter: Long
    )

    private data class MigrationPayloadRaw(
        val items: List<MigrationOtpRaw>,
        val version: Int?,
        val batchSize: Int?,
        val batchIndex: Int?,
        val batchId: Int?
    )

    private sealed interface MigrationParseOutcome {
        data class Success(val items: List<TotpParseResult>) : MigrationParseOutcome
        data class Failure(val reason: MigrationFailureReason) : MigrationParseOutcome
    }

    private class MigrationParseException(
        val reason: MigrationFailureReason
    ) : RuntimeException()

    private class ProtoReader(private val data: ByteArray) {
        private var position: Int = 0

        fun isAtEnd(): Boolean = position >= data.size

        fun readTag(): Int? {
            if (isAtEnd()) return null
            return readVarInt32()?.takeIf { it != 0 }
        }

        fun readVarInt32(): Int? {
            return readVarInt64()?.toInt()
        }

        fun readVarInt64(): Long? {
            var shift = 0
            var result = 0L
            while (shift < 64) {
                if (position >= data.size) return null
                val b = data[position++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) {
                    return result
                }
                shift += 7
            }
            return null
        }

        fun readBytes(): ByteArray? {
            val length = readVarInt32() ?: return null
            if (length < 0 || position + length > data.size) return null
            val result = data.copyOfRange(position, position + length)
            position += length
            return result
        }

        fun readString(maxBytes: Int): String? {
            val bytes = readBytes() ?: return null
            if (bytes.size > maxBytes) return null
            return runCatching {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            }.getOrNull()
        }

        fun skipField(wireType: Int): Boolean {
            return when (wireType) {
                WireType.VARINT -> readVarInt64() != null
                WireType.FIXED64 -> skipBytes(8)
                WireType.LENGTH_DELIMITED -> {
                    val length = readVarInt32() ?: return false
                    skipBytes(length)
                }
                WireType.FIXED32 -> skipBytes(4)
                else -> false
            }
        }

        private fun skipBytes(count: Int): Boolean {
            if (count < 0 || position + count > data.size) {
                return false
            }
            position += count
            return true
        }
    }
}

/**
 * TOTP URI 解析结果
 */
data class TotpParseResult(
    val totpData: TotpData,
    val label: String,
    val accountName: String
) {
    /** Suggested local name; the original URI label remains available for round trips. */
    val suggestedTitle: String get() = suggestedTotpTitle(totpData, label)
}

sealed class TotpScanParseResult {
    data class Single(val item: TotpParseResult) : TotpScanParseResult()
    data class Multiple(val items: List<TotpParseResult>) : TotpScanParseResult()
    data class MigrationFailure(val reason: MigrationFailureReason) : TotpScanParseResult()
    data object UnsupportedPhoneFactor : TotpScanParseResult()
    data object InvalidFormat : TotpScanParseResult()
}

enum class MigrationFailureReason {
    MALFORMED_PAYLOAD,
    PAYLOAD_TOO_LARGE,
    UNSUPPORTED_VERSION,
    MULTI_QR_BATCH,
    UNSUPPORTED_ALGORITHM,
    UNSUPPORTED_DIGITS,
    UNSUPPORTED_OTP_TYPE,
    MISSING_ACCOUNT_NAME
}

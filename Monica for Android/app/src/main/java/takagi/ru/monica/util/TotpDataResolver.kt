package takagi.ru.monica.util

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData

/**
 * 统一处理 Base32 secret 与 otpauth URI 两种验证器密钥格式。
 */
object TotpDataResolver {
    private const val DEFAULT_PERIOD = 30
    private const val DEFAULT_DIGITS = 6
    private const val DEFAULT_ALGORITHM = "SHA1"
    private val json = Json { ignoreUnknownKeys = true }

    fun fromAuthenticatorKey(
        rawKey: String,
        fallbackIssuer: String = "",
        fallbackAccountName: String = ""
    ): TotpData? {
        val normalizedKey = rawKey.trim()
        if (normalizedKey.isBlank()) return null

        val parsedFromUri = parseUriTotpData(normalizedKey)
        val initialData = if (parsedFromUri != null) {
            parsedFromUri.copy(
                issuer = parsedFromUri.issuer.ifBlank { fallbackIssuer.trim() },
                accountName = parsedFromUri.accountName.ifBlank { fallbackAccountName.trim() }
            )
        } else {
            TotpData(
                secret = normalizedKey,
                issuer = fallbackIssuer.trim(),
                accountName = fallbackAccountName.trim()
            )
        }

        return normalizeTotpData(initialData)
    }

    fun normalizeTotpData(data: TotpData): TotpData {
        val mergedData = parseUriTotpData(data.secret)?.let { parsed ->
            mergeParsedData(data, parsed)
        } ?: data

        val contextAwareData = applyContextualOtpType(mergedData)

        val safePeriod = when (contextAwareData.otpType) {
            OtpType.STEAM -> DEFAULT_PERIOD
            else -> contextAwareData.period.takeIf { it > 0 } ?: DEFAULT_PERIOD
        }
        val safeDigits = when (contextAwareData.otpType) {
            OtpType.STEAM -> 5
            else -> contextAwareData.digits.coerceIn(4, 10)
        }
        val normalizedAlgorithm = when (contextAwareData.otpType) {
            OtpType.STEAM -> DEFAULT_ALGORITHM
            else -> normalizeAlgorithm(contextAwareData.algorithm)
        }
        val normalizedSecret = when (contextAwareData.otpType) {
            OtpType.MOTP -> contextAwareData.secret.trim()
            else -> normalizeBase32Secret(contextAwareData.secret)
        }

        return contextAwareData.copy(
            secret = normalizedSecret,
            issuer = contextAwareData.issuer.trim(),
            accountName = contextAwareData.accountName.trim(),
            period = safePeriod,
            digits = safeDigits,
            algorithm = normalizedAlgorithm,
            counter = contextAwareData.counter.coerceAtLeast(0L)
        )
    }

    fun normalizeBase32Secret(secret: String): String {
        return secret
            .trim()
            .replace(" ", "")
            .replace("-", "")
            .uppercase()
    }

    fun parseStoredItemData(
        itemData: String,
        fallbackIssuer: String = "",
        fallbackAccountName: String = ""
    ): TotpData? {
        runCatching {
            json.decodeFromString<TotpData>(itemData)
        }.getOrNull()?.let { decoded ->
            return normalizeTotpData(
                decoded.copy(
                    issuer = decoded.issuer.ifBlank { fallbackIssuer.trim() },
                    accountName = decoded.accountName.ifBlank { fallbackAccountName.trim() }
                )
            )
        }

        runCatching {
            json.parseToJsonElement(itemData) as? JsonObject
        }.getOrNull()?.let { obj ->
            val secret = obj["secret"]?.jsonPrimitive?.content
                ?: obj["key"]?.jsonPrimitive?.content
                ?: ""
            val issuer = obj["issuer"]?.jsonPrimitive?.content
                ?: obj["serviceName"]?.jsonPrimitive?.content
                ?: fallbackIssuer
            val accountName = obj["account"]?.jsonPrimitive?.content
                ?: obj["accountName"]?.jsonPrimitive?.content
                ?: fallbackAccountName
            val period = obj["period"]?.jsonPrimitive?.content?.toIntOrNull() ?: DEFAULT_PERIOD
            val digits = obj["digits"]?.jsonPrimitive?.content?.toIntOrNull() ?: DEFAULT_DIGITS
            val algorithm = obj["algorithm"]?.jsonPrimitive?.content ?: DEFAULT_ALGORITHM

            if (secret.isNotBlank() || issuer.isNotBlank() || accountName.isNotBlank()) {
                return normalizeTotpData(
                    TotpData(
                        secret = secret,
                        issuer = issuer,
                        accountName = accountName,
                        period = period,
                        digits = digits,
                        algorithm = algorithm
                    )
                )
            }
        }

        return fromAuthenticatorKey(
            rawKey = itemData,
            fallbackIssuer = fallbackIssuer,
            fallbackAccountName = fallbackAccountName
        )
    }

    fun toBitwardenPayload(title: String, data: TotpData): String {
        val normalized = normalizeTotpData(data)
        val shouldUseOtpAuthPayload =
            normalized.otpType != OtpType.TOTP ||
                !normalized.algorithm.equals(DEFAULT_ALGORITHM, ignoreCase = true) ||
                normalized.digits != DEFAULT_DIGITS ||
                normalized.period != DEFAULT_PERIOD

        if (!shouldUseOtpAuthPayload) {
            return normalized.secret
        }

        val label = buildTotpLabel(
            title = title,
            issuer = normalized.issuer,
            accountName = normalized.accountName
        )
        return TotpUriParser.generateUri(label, normalized)
    }

    fun hasEquivalentOtpParameters(left: TotpData, right: TotpData): Boolean {
        val normalizedLeft = normalizeTotpData(left)
        val normalizedRight = normalizeTotpData(right)
        return normalizedLeft.secret == normalizedRight.secret &&
            normalizedLeft.issuer == normalizedRight.issuer &&
            normalizedLeft.accountName == normalizedRight.accountName &&
            normalizedLeft.algorithm == normalizedRight.algorithm &&
            normalizedLeft.digits == normalizedRight.digits &&
            normalizedLeft.period == normalizedRight.period &&
            normalizedLeft.otpType == normalizedRight.otpType &&
            normalizedLeft.counter == normalizedRight.counter &&
            normalizedLeft.pin == normalizedRight.pin
    }

    fun hasNonDefaultOtpSettings(data: TotpData): Boolean {
        val normalized = normalizeTotpData(data)
        return normalized.otpType != OtpType.TOTP ||
            !normalized.algorithm.equals(DEFAULT_ALGORITHM, ignoreCase = true) ||
            normalized.digits != DEFAULT_DIGITS ||
            normalized.period != DEFAULT_PERIOD ||
            normalized.counter > 0L ||
            normalized.pin.isNotBlank()
    }

    private fun parseUriTotpData(raw: String): TotpData? {
        if (!raw.contains("://")) return null
        return TotpUriParser.parseUri(raw.trim())?.totpData
    }

    private fun applyContextualOtpType(source: TotpData): TotpData {
        if (source.otpType != OtpType.TOTP) return source

        val context = buildString {
            append(source.issuer)
            append(' ')
            append(source.accountName)
            append(' ')
            append(source.associatedApp)
            append(' ')
            append(source.link)
        }.lowercase()

        val looksLikeSteam =
            context.contains("steamcommunity") ||
                context.contains("steampowered") ||
                context.contains("steam")

        if (!looksLikeSteam) return source

        return source.copy(
            otpType = OtpType.STEAM,
            digits = 5,
            period = DEFAULT_PERIOD,
            algorithm = DEFAULT_ALGORITHM
        )
    }

    private fun mergeParsedData(source: TotpData, parsed: TotpData): TotpData {
        val sourceAlgorithm = normalizeAlgorithm(source.algorithm)
        val parsedAlgorithm = normalizeAlgorithm(parsed.algorithm)

        val shouldUseParsedPeriod = source.period <= 0 || source.period == DEFAULT_PERIOD
        val shouldUseParsedDigits = source.digits <= 0 || source.digits == DEFAULT_DIGITS
        val shouldUseParsedAlgorithm = sourceAlgorithm == DEFAULT_ALGORITHM
        val shouldUseParsedType = source.otpType == OtpType.TOTP
        val shouldUseParsedCounter = source.counter <= 0L

        return source.copy(
            secret = parsed.secret,
            issuer = source.issuer.ifBlank { parsed.issuer },
            accountName = source.accountName.ifBlank { parsed.accountName },
            period = if (shouldUseParsedPeriod) parsed.period else source.period,
            digits = if (shouldUseParsedDigits) parsed.digits else source.digits,
            algorithm = if (shouldUseParsedAlgorithm) parsedAlgorithm else sourceAlgorithm,
            otpType = if (shouldUseParsedType) parsed.otpType else source.otpType,
            counter = if (shouldUseParsedCounter) parsed.counter else source.counter,
            pin = source.pin.ifBlank { parsed.pin }
        )
    }

    private fun normalizeAlgorithm(algorithm: String): String {
        return algorithm.trim().uppercase().ifBlank { DEFAULT_ALGORITHM }
    }

    private fun buildTotpLabel(title: String, issuer: String, accountName: String): String {
        val normalizedIssuer = issuer.trim()
        val normalizedAccount = accountName.trim()
        return when {
            normalizedIssuer.isNotBlank() && normalizedAccount.isNotBlank() -> {
                "$normalizedIssuer:$normalizedAccount"
            }
            title.isNotBlank() -> title.trim()
            normalizedIssuer.isNotBlank() -> normalizedIssuer
            normalizedAccount.isNotBlank() -> normalizedAccount
            else -> "Authenticator"
        }
    }
}

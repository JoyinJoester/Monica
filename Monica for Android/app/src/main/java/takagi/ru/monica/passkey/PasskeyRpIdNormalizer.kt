package takagi.ru.monica.passkey

import java.net.IDN
import java.util.Locale

/**
 * WebAuthn RP ID normalization and loose-equivalence matcher.
 *
 * We keep matching conservative:
 * - normalize case and trailing dot
 * - normalize unicode domain to ASCII (IDN)
 * - no cross-domain aliasing
 */
object PasskeyRpIdNormalizer {

    fun normalize(rpId: String?): String? {
        val trimmed = rpId?.trim().orEmpty().trimEnd('.')
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase(Locale.ROOT)
        return runCatching { IDN.toASCII(lower, IDN.USE_STD3_ASCII_RULES) }
            .getOrDefault(lower)
    }

    fun isEquivalent(left: String?, right: String?): Boolean {
        val l = normalize(left)
        val r = normalize(right)
        if (l == null || r == null) return false
        return l == r
    }
}


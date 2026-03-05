package takagi.ru.monica.passkey

import android.content.Context
import android.util.Log
import androidx.credentials.provider.CallingAppInfo
import org.json.JSONObject
import java.net.URI
import java.util.Locale

data class PasskeyValidationVerdict(
    val resolvedOrigin: String,
    val resolvedSource: PasskeyOriginResolver.Source,
    val reasons: List<String>,
    val strictBlock: Boolean
)

object PasskeyRequestValidator {

    private const val TAG = "PasskeyRequestValidator"

    fun validate(
        context: Context,
        requestJson: String,
        rpId: String?,
        callingAppInfo: CallingAppInfo?
    ): PasskeyValidationVerdict {
        val originMeta = PasskeyOriginResolver.resolveOriginWithMeta(
            context = context,
            requestJson = requestJson,
            callingAppInfo = callingAppInfo,
            rpIdFallback = rpId
        )

        val reasons = mutableListOf<String>()
        val normalizedRpId = PasskeyRpIdNormalizer.normalize(rpId)
        val requestOrigin = extractRequestOrigin(requestJson)
        val requestHost = extractHttpsHost(requestOrigin)
        val callingOrigin = callingAppInfo?.origin?.takeIf { it.isNotBlank() }
        val callingHost = extractHttpsHost(callingOrigin)

        if (!requestOrigin.isNullOrBlank() && requestHost == null && !requestOrigin.startsWith("android:")) {
            reasons += "request_origin_unparsable_or_non_https"
        }

        if (!normalizedRpId.isNullOrBlank() && !requestHost.isNullOrBlank() &&
            !isHostAllowedForRpId(requestHost, normalizedRpId)
        ) {
            reasons += "request_origin_host_not_allowed_for_rp"
        }

        if (!normalizedRpId.isNullOrBlank() && !callingHost.isNullOrBlank() &&
            !isHostAllowedForRpId(callingHost, normalizedRpId)
        ) {
            reasons += "calling_origin_host_not_allowed_for_rp"
        }

        if (!requestHost.isNullOrBlank() && !callingHost.isNullOrBlank() &&
            requestHost != callingHost
        ) {
            reasons += "request_and_calling_origin_host_mismatch"
        }

        if (originMeta.source == PasskeyOriginResolver.Source.SELF_SIGNING_FALLBACK ||
            originMeta.source == PasskeyOriginResolver.Source.INVALID_FALLBACK
        ) {
            reasons += "origin_fallback_used"
        }

        val strictBlock = reasons.contains("request_origin_host_not_allowed_for_rp")

        return PasskeyValidationVerdict(
            resolvedOrigin = originMeta.origin,
            resolvedSource = originMeta.source,
            reasons = reasons,
            strictBlock = strictBlock
        )
    }

    fun logShadow(
        flowTag: String,
        rpId: String?,
        callingPackage: String?,
        verdict: PasskeyValidationVerdict
    ) {
        if (verdict.reasons.isEmpty()) {
            Log.d(
                TAG,
                "[$flowTag] validation clean; rpId=${rpId ?: "null"}, pkg=${callingPackage ?: "null"}, source=${verdict.resolvedSource}"
            )
            return
        }

        Log.w(
            TAG,
            "[$flowTag] validation suspicious; rpId=${rpId ?: "null"}, pkg=${callingPackage ?: "null"}, source=${verdict.resolvedSource}, reasons=${verdict.reasons.joinToString(",")}"
        )
    }

    private fun extractRequestOrigin(requestJson: String): String? {
        return runCatching {
            JSONObject(requestJson).optString("origin").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun extractHttpsHost(origin: String?): String? {
        if (origin.isNullOrBlank()) return null
        return runCatching {
            val uri = URI(origin)
            if (uri.scheme?.lowercase(Locale.ROOT) != "https") return null
            val host = uri.host ?: return null
            PasskeyRpIdNormalizer.normalize(host)
        }.getOrNull()
    }

    private fun isHostAllowedForRpId(host: String, rpId: String): Boolean {
        return host == rpId || host.endsWith(".$rpId")
    }
}


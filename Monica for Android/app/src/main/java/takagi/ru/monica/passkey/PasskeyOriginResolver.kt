package takagi.ru.monica.passkey

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.provider.CallingAppInfo
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Shared origin resolution for passkey create/get.
 *
 * Resolution order (compat + safety):
 * 1) requestJson.origin
 * 2) CallingAppInfo.origin
 * 3) calling app signing hash -> android:apk-key-hash:...
 * 4) rpId fallback -> https://{rpId}
 * 5) legacy fallback (this app signing hash) to avoid breaking existing users
 */
object PasskeyOriginResolver {

    private const val TAG = "PasskeyOriginResolver"

    enum class Source {
        REQUEST_JSON_ORIGIN,
        CALLING_APP_ORIGIN,
        CALLING_APP_SIGNATURE,
        RP_ID_HTTPS_FALLBACK,
        SELF_SIGNING_FALLBACK,
        INVALID_FALLBACK
    }

    data class ResolvedOrigin(
        val origin: String,
        val source: Source
    )

    fun resolveOrigin(
        context: Context,
        requestJson: String,
        callingAppInfo: CallingAppInfo?,
        rpIdFallback: String?,
    ): String {
        return resolveOriginWithMeta(
            context = context,
            requestJson = requestJson,
            callingAppInfo = callingAppInfo,
            rpIdFallback = rpIdFallback
        ).origin
    }

    fun resolveOriginWithMeta(
        context: Context,
        requestJson: String,
        callingAppInfo: CallingAppInfo?,
        rpIdFallback: String?,
    ): ResolvedOrigin {
        val requestOrigin = extractRequestOrigin(requestJson)
        if (!requestOrigin.isNullOrBlank()) {
            return ResolvedOrigin(requestOrigin, Source.REQUEST_JSON_ORIGIN)
        }

        val callerOrigin = getOriginFromCallingAppInfo(callingAppInfo)
        if (!callerOrigin.isNullOrBlank()) {
            val source = if (callingAppInfo?.origin.isNullOrBlank()) {
                Source.CALLING_APP_SIGNATURE
            } else {
                Source.CALLING_APP_ORIGIN
            }
            return ResolvedOrigin(callerOrigin, source)
        }

        val normalizedRpId = PasskeyRpIdNormalizer.normalize(rpIdFallback)
        if (!normalizedRpId.isNullOrBlank()) {
            return ResolvedOrigin("https://$normalizedRpId", Source.RP_ID_HTTPS_FALLBACK)
        }

        // Legacy fallback for maximum compatibility with existing edge cases.
        val appHash = getAppSigningHash(context)
        if (!appHash.isNullOrBlank()) {
            Log.w(TAG, "Falling back to self signing hash origin")
            return ResolvedOrigin("android:apk-key-hash:$appHash", Source.SELF_SIGNING_FALLBACK)
        }

        return ResolvedOrigin("https://invalid.local", Source.INVALID_FALLBACK)
    }

    private fun extractRequestOrigin(requestJson: String): String? {
        return runCatching {
            JSONObject(requestJson).optString("origin").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun getOriginFromCallingAppInfo(callingAppInfo: CallingAppInfo?): String? {
        if (callingAppInfo == null) return null
        return try {
            val origin = callingAppInfo.origin
            if (!origin.isNullOrBlank()) {
                origin
            } else {
                val hash = getCallingAppSigningHash(callingAppInfo)
                if (hash.isNullOrBlank()) null else "android:apk-key-hash:$hash"
            }
        } catch (e: Exception) {
            val hash = getCallingAppSigningHash(callingAppInfo)
            if (hash.isNullOrBlank()) null else "android:apk-key-hash:$hash"
        }
    }

    private fun getCallingAppSigningHash(callingAppInfo: CallingAppInfo): String? {
        return try {
            val signatures = callingAppInfo.signingInfo.apkContentsSigners
            if (signatures.isNotEmpty()) {
                val certFactory = CertificateFactory.getInstance("X509")
                val cert = certFactory.generateCertificate(
                    signatures[0].toByteArray().inputStream()
                ) as X509Certificate
                val md = MessageDigest.getInstance("SHA-256")
                val hash = md.digest(cert.encoded)
                Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get calling app signing hash", e)
            null
        }
    }

    private fun getAppSigningHash(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signatures = packageInfo.signingInfo?.apkContentsSigners ?: return null
            if (signatures.isNotEmpty()) {
                val md = MessageDigest.getInstance("SHA-256")
                val hash = md.digest(signatures[0].toByteArray())
                Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app signing hash", e)
            null
        }
    }
}

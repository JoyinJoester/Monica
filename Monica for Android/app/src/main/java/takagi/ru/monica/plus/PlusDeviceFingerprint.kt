package takagi.ru.monica.plus

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object PlusDeviceFingerprint {

    fun create(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "unknown_android_id"

        val payload = listOf(
            androidId,
            Build.BRAND.orEmpty(),
            Build.MODEL.orEmpty(),
            context.packageName
        ).joinToString(separator = "|")

        return sha256Hex(payload)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}

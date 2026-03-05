package takagi.ru.monica.passkey

import android.content.Context

/**
 * Runtime flags for passkey request validation.
 *
 * Defaults are intentionally safe for existing users:
 * - shadow validation: enabled (observe only)
 * - strict validation: disabled (no blocking)
 */
object PasskeyValidationFlags {

    private const val PREF_NAME = "passkey_validation_flags"
    private const val KEY_SHADOW_VALIDATION_ENABLED = "shadow_validation_enabled"
    private const val KEY_STRICT_VALIDATION_ENABLED = "strict_validation_enabled"

    fun isShadowValidationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SHADOW_VALIDATION_ENABLED, true)
    }

    fun isStrictValidationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_STRICT_VALIDATION_ENABLED, false)
    }

    fun setShadowValidationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHADOW_VALIDATION_ENABLED, enabled).apply()
    }

    fun setStrictValidationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_STRICT_VALIDATION_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}

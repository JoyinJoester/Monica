package takagi.ru.monica.autofill_ng

import java.util.Locale

/** Shared by direct, authenticated and manual system datasets. */
internal object AutofillPasswordRolePolicy {
    fun isNewPasswordHint(hint: String): Boolean = hint.trim().lowercase(Locale.ROOT)
        .replace("_", "-").replace(" ", "-") in setOf("new-password", "newpassword")

    fun isStoredPasswordHint(hint: String): Boolean = !isNewPasswordHint(hint) &&
        hint.lowercase(Locale.ROOT).contains("pass")
}

package takagi.ru.monica.autofill_ng

import android.view.autofill.AutofillId
import java.util.Locale
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.ParsedItem

enum class AutofillFormPurpose { UNKNOWN, LOGIN, USERNAME_STEP, PASSWORD_STEP, SIGN_UP, CHANGE_PASSWORD, RESET_PASSWORD, OTP }

/** Bounded static labels/actions only. Never pass editable text or credential values as cues. */
internal object AutofillFormPurposePolicy {
    data class Result(val items: List<ParsedItem>, val purpose: AutofillFormPurpose)
    private val signup = Regex("\\b(sign up|signup|register|registration|create account)\\b|注册|註冊|创建账户|建立帳戶")
    private val login = Regex("\\b(log in|login|sign in|signin)\\b|登录|登入|登錄")
    private val reset = Regex("\\b(reset password|change password|update password|set password)\\b|重置密码|修改密码|更改密码|重設密碼|變更密碼")
    private val newPassword = Regex("\\b(new|confirm|repeat|retype)( your)? password\\b|\\bpassword (confirmation|confirm|repeat)\\b|新密码|确认密码|重复密码|再次输入密码|新密碼|確認密碼|重複密碼")
    private val currentPassword = Regex("\\b(current|old|existing) password\\b|旧密码|原密码|当前密码|舊密碼|目前密碼")

    private fun normalize(value: String) = value.take(256)
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .lowercase(Locale.ROOT).replace(Regex("[-_\\s]+"), " ").trim()

    fun isNewPasswordLabel(value: String) = newPassword.containsMatchIn(normalize(value))
    fun isCurrentPasswordLabel(value: String) = currentPassword.containsMatchIn(normalize(value))

    fun analyze(items: List<ParsedItem>, staticCues: List<String> = emptyList(),
                explicitCurrentIds: Set<AutofillId> = emptySet()): Result {
        val cues = staticCues.take(64).map(::normalize)
        val hasSignup = cues.any { signup.containsMatchIn(it) }
        val hasLogin = cues.any { login.containsMatchIn(it) }
        val hasReset = cues.any { reset.containsMatchIn(it) }
        val canPromote = !hasLogin && (hasSignup || hasReset) && explicitCurrentIds.isEmpty()
        val resolved = if (canPromote) items.map {
            if (it.hint == FieldHint.PASSWORD) it.copy(hint = FieldHint.NEW_PASSWORD) else it
        } else items
        val hints = resolved.map { it.hint }
        val account = hints.any { it in setOf(FieldHint.USERNAME, FieldHint.EMAIL_ADDRESS, FieldHint.PHONE_NUMBER) }
        val current = FieldHint.PASSWORD in hints
        val new = FieldHint.NEW_PASSWORD in hints
        val purpose = when {
            current && new -> AutofillFormPurpose.CHANGE_PASSWORD
            new && (hasSignup || account) && !hasReset -> AutofillFormPurpose.SIGN_UP
            new -> AutofillFormPurpose.RESET_PASSWORD
            current && account -> AutofillFormPurpose.LOGIN
            current -> AutofillFormPurpose.PASSWORD_STEP
            FieldHint.OTP_CODE in hints -> AutofillFormPurpose.OTP
            account -> AutofillFormPurpose.USERNAME_STEP
            else -> AutofillFormPurpose.UNKNOWN
        }
        return Result(resolved, purpose)
    }
}

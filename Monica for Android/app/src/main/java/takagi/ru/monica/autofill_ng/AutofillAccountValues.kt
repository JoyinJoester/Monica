package takagi.ru.monica.autofill_ng

/** A login identifier must not spill into a profile name or an unrelated phone field. */
internal object AutofillAccountValues {
    fun resolve(rawHint: String?, account: String, allowEmail: Boolean): String? {
        val hint = AutofillProfileValues.canonical(rawHint)
        return when {
            hint == "username" -> account
            hint == "email" -> account.takeIf { allowEmail }
            hint == "tel" || hint.startsWith("tel-") -> {
                val isPhone = account.any(Char::isDigit) && account.all { it.isDigit() || it in "+()- ." }
                if (isPhone) AutofillProfileValues.phonePart(hint, account, "") else null
            }
            else -> null
        }?.takeIf(String::isNotBlank)
    }
}

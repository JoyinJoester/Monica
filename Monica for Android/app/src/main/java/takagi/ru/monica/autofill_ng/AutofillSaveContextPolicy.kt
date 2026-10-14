package takagi.ru.monica.autofill_ng

/** Only related Android-provided contexts are considered; no global account memory. */
internal object AutofillSaveContextPolicy {
    data class Target(val packageName: String, val scheme: String?, val host: String?)
    data class Snapshot(val target: Target, val purpose: AutofillFormPurpose, val hasAccount: Boolean, val username: String)

    fun username(contexts: List<Snapshot>): String {
        val last = contexts.lastOrNull() ?: return ""
        if (last.hasAccount || last.purpose != AutofillFormPurpose.PASSWORD_STEP) return last.username
        if (last.target.packageName.isBlank()) return ""
        for (previous in contexts.dropLast(1).asReversed().take(7)) {
            // Do not jump across another app, origin, reset or unrelated form.
            if (previous.target != last.target) return ""
            if (previous.purpose == AutofillFormPurpose.USERNAME_STEP) return previous.username
            if (previous.purpose != AutofillFormPurpose.PASSWORD_STEP) return ""
        }
        return ""
    }
}

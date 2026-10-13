package takagi.ru.monica.util

import takagi.ru.monica.data.model.TotpData

/** Default name for a newly imported authenticator. Account data stays in its own field. */
internal fun suggestedTotpTitle(data: TotpData, fallback: String = ""): String =
    data.issuer.trim().ifBlank { data.accountName.trim() }.ifBlank { fallback.trim() }

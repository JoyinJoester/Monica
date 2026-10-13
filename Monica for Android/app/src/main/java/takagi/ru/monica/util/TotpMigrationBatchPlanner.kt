package takagi.ru.monica.util

import java.util.Locale
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData

internal sealed interface TotpMigrationBatchPlan {
    data class Ready(
        val items: List<TotpParseResult>,
        val duplicateCount: Int
    ) : TotpMigrationBatchPlan

    data class Rejected(
        val invalidItemCount: Int
    ) : TotpMigrationBatchPlan
}

internal fun planTotpMigrationBatch(
    items: List<TotpParseResult>
): TotpMigrationBatchPlan {
    val invalidItemCount = items.count { !isValidMigrationImportItem(it) }
    if (items.isEmpty() || invalidItemCount > 0) {
        return TotpMigrationBatchPlan.Rejected(
            invalidItemCount = invalidItemCount
        )
    }

    val uniqueItems = LinkedHashMap<String, TotpParseResult>()
    var duplicateCount = 0
    items.forEach { item ->
        val identityKey = migrationImportIdentityKey(item.totpData)
        if (uniqueItems.putIfAbsent(identityKey, item) != null) {
            duplicateCount++
        }
    }
    return TotpMigrationBatchPlan.Ready(
        items = uniqueItems.values.toList(),
        duplicateCount = duplicateCount
    )
}

internal fun migrationImportIdentityKey(data: TotpData): String {
    return listOf(
        data.otpType.name,
        data.secret.filterNot(Char::isWhitespace).uppercase(Locale.ROOT),
        data.algorithm.uppercase(Locale.ROOT),
        data.digits.toString(),
        data.period.toString(),
        data.counter.toString()
    ).joinToString("|")
}

private fun isValidMigrationImportItem(item: TotpParseResult): Boolean {
    val data = item.totpData
    val title = item.suggestedTitle
    return title.isNotBlank() &&
        data.secret.isNotBlank() &&
        data.algorithm.uppercase(Locale.ROOT) in setOf("SHA1", "SHA256", "SHA512") &&
        data.digits in setOf(6, 8) &&
        data.otpType in setOf(OtpType.TOTP, OtpType.HOTP) &&
        data.counter >= 0L &&
        (data.otpType == OtpType.HOTP || data.period > 0)
}

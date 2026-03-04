package takagi.ru.monica.autofill.bwcompat.builder

import android.view.autofill.AutofillValue
import android.widget.inline.InlinePresentationSpec
import takagi.ru.monica.autofill.bwcompat.model.AutofillCipher
import takagi.ru.monica.autofill.bwcompat.model.AutofillRequest
import takagi.ru.monica.autofill.bwcompat.model.AutofillView
import takagi.ru.monica.autofill.bwcompat.model.FilledData
import takagi.ru.monica.autofill.bwcompat.model.FilledItem
import takagi.ru.monica.autofill.bwcompat.model.FilledPartition
import takagi.ru.monica.autofill.bwcompat.model.toAutofillCipherLogin
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager

private const val MAX_FILLED_PARTITIONS = 20
private const val MAX_INLINE_SUGGESTIONS = 5
private const val RESERVED_INLINE_FOR_MANUAL_ENTRY = 1

class FilledDataBuilder(
    private val securityManager: SecurityManager,
) {

    fun build(
        request: AutofillRequest.Fillable,
        passwords: List<PasswordEntry>,
    ): FilledData {
        var inlineSuggestionsAdded = 0
        // 永远为“打开自动填充页面”入口预留 1 个 inline 槽位。
        val maxCredentialInlineSuggestionsCount = (request.maxInlineSuggestionsCount - RESERVED_INLINE_FOR_MANUAL_ENTRY)
            .coerceAtLeast(0)
            .coerceAtMost(MAX_INLINE_SUGGESTIONS)

        fun getInlineSpecOrNull(): InlinePresentationSpec? =
            if (inlineSuggestionsAdded < maxCredentialInlineSuggestionsCount) {
                request.inlinePresentationSpecs.getOrLastOrNull(inlineSuggestionsAdded)
            } else {
                null
            }?.also { inlineSuggestionsAdded += 1 }

        val ciphers = passwords.map { entry ->
            entry.toAutofillCipherLogin(
                fallbackWebsite = request.uri.orEmpty(),
                usernameValue = decryptIfPossible(entry.username),
                passwordValue = decryptIfPossible(entry.password)
            )
        }

        val filledPartitions = ciphers
            .map { cipher ->
                FilledPartition(
                    autofillCipher = cipher,
                    filledItems = fillLoginItems(cipher, request.partition.views),
                    inlinePresentationSpec = getInlineSpecOrNull()
                )
            }
            .filter { it.filledItems.isNotEmpty() }
            .take(MAX_FILLED_PARTITIONS)

        return FilledData(
            filledPartitions = filledPartitions,
            ignoreAutofillIds = request.ignoreAutofillIds,
            originalPartition = request.partition,
            uri = request.uri
        )
    }

    private fun fillLoginItems(
        cipher: AutofillCipher.Login,
        views: List<AutofillView.Login>,
    ): List<FilledItem> {
        return views.mapNotNull { view ->
            val value = when (view) {
                is AutofillView.Login.Username -> cipher.username
                is AutofillView.Login.Password -> cipher.password
            }
            if (value.isBlank()) {
                null
            } else {
                FilledItem(
                    autofillId = view.data.autofillId,
                    value = AutofillValue.forText(value)
                )
            }
        }
    }

    private fun decryptIfPossible(value: String): String {
        if (value.isBlank()) return value
        return runCatching { securityManager.decryptData(value) }
            .getOrElse { value }
    }
}

private fun <T> List<T>?.getOrLastOrNull(index: Int): T? =
    this?.getOrNull(index) ?: this?.lastOrNull()

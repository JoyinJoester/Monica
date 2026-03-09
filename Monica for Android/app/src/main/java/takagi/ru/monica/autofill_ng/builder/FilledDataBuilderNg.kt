package takagi.ru.monica.autofill_ng.builder

import android.content.Context
import android.view.autofill.AutofillValue
import android.widget.inline.InlinePresentationSpec
import takagi.ru.monica.autofill_ng.model.AutofillCipher
import takagi.ru.monica.autofill_ng.model.AutofillRequest
import takagi.ru.monica.autofill_ng.model.AutofillView
import takagi.ru.monica.autofill_ng.model.FilledData
import takagi.ru.monica.autofill_ng.model.FilledItem
import takagi.ru.monica.autofill_ng.model.FilledPartition
import takagi.ru.monica.autofill_ng.model.toAutofillCipherLogin
import takagi.ru.monica.autofill_ng.AutofillSecretResolver
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager

private const val MAX_FILLED_PARTITIONS_COUNT = 20
private const val MAX_INLINE_SUGGESTION_COUNT = 5

class FilledDataBuilderNg(
    private val context: Context,
    private val securityManager: SecurityManager,
) {

    fun build(
        request: AutofillRequest.Fillable,
        passwords: List<PasswordEntry>,
    ): FilledData {
        val isVaultLocked = !SessionManager.canSkipVerification(context)
        val maxCipherInlineSuggestionsCount = (request.maxInlineSuggestionsCount - 1)
            .coerceAtMost(MAX_INLINE_SUGGESTION_COUNT)

        var inlineSuggestionsAdded = 0

        fun getCipherInlinePresentationOrNull(): InlinePresentationSpec? =
            if (inlineSuggestionsAdded < maxCipherInlineSuggestionsCount) {
                request.inlinePresentationSpecs?.getOrLastOrNull(inlineSuggestionsAdded)
            } else {
                null
            }?.also { inlineSuggestionsAdded += 1 }

        val ciphers = passwords.mapNotNull { entry ->
            val usernameValue = decryptForAutofill(entry.username)
            val passwordValue = decryptForAutofill(entry.password)
            if (usernameValue.isNullOrBlank() && passwordValue.isNullOrBlank()) {
                null
            } else {
                entry.toAutofillCipherLogin(
                    fallbackWebsite = request.uri.orEmpty(),
                    usernameValue = usernameValue.orEmpty(),
                    passwordValue = passwordValue.orEmpty()
                )
            }
        }

        val filledPartitions = ciphers
            .map { autofillCipher ->
                fillLoginPartition(
                    autofillCipher = autofillCipher,
                    autofillViews = request.partition.views,
                    inlinePresentationSpec = getCipherInlinePresentationOrNull()
                )
            }
            .filter { it.filledItems.isNotEmpty() }
            .take(MAX_FILLED_PARTITIONS_COUNT)

        val vaultItemInlinePresentationSpec = request
            .inlinePresentationSpecs
            ?.getOrLastOrNull(inlineSuggestionsAdded)

        return FilledData(
            filledPartitions = filledPartitions,
            ignoreAutofillIds = request.ignoreAutofillIds,
            originalPartition = request.partition,
            uri = request.uri,
            vaultItemInlinePresentationSpec = vaultItemInlinePresentationSpec,
            isVaultLocked = isVaultLocked
        )
    }

    private fun fillLoginPartition(
        autofillCipher: AutofillCipher.Login,
        autofillViews: List<AutofillView.Login>,
        inlinePresentationSpec: InlinePresentationSpec?,
    ): FilledPartition {
        val filledItems = autofillViews
            .mapNotNull { autofillView ->
                val value = when (autofillView) {
                    is AutofillView.Login.Username -> autofillCipher.username
                    is AutofillView.Login.Password -> autofillCipher.password
                }
                autofillView.buildFilledItemOrNull(value = value)
            }

        return FilledPartition(
            autofillCipher = autofillCipher,
            filledItems = filledItems,
            inlinePresentationSpec = inlinePresentationSpec
        )
    }

    private fun AutofillView.Login.buildFilledItemOrNull(value: String?): FilledItem? {
        if (value.isNullOrBlank()) return null
        return FilledItem(
            autofillId = data.autofillId,
            value = AutofillValue.forText(value)
        )
    }

    private fun decryptForAutofill(value: String): String? {
        if (value.isBlank()) return ""
        return AutofillSecretResolver.decryptPasswordOrNull(
            securityManager = securityManager,
            encryptedOrPlain = value,
            logTag = "FilledDataBuilderNg",
        )
    }
}

private fun <T> List<T>.getOrLastOrNull(index: Int): T? =
    getOrNull(index) ?: lastOrNull()

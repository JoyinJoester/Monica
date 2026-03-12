package takagi.ru.monica.autofill_ng.parser

import android.os.Build
import android.view.View
import android.view.inputmethod.InlineSuggestionsRequest
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill_ng.model.AutofillPartition
import takagi.ru.monica.autofill_ng.model.AutofillRequest
import takagi.ru.monica.autofill_ng.model.AutofillView

class AutofillParserNg {

    fun parse(
        packageName: String,
        uri: String?,
        credentialTargets: List<ParsedItem>,
        inlineRequest: InlineSuggestionsRequest?,
        fieldSignatureKey: String? = null,
        isCompatMode: Boolean = false,
    ): AutofillRequest {
        val normalizedUri = uri?.trim().takeUnless { it.isNullOrBlank() } ?: "androidapp://$packageName"
        val loginViews = buildLoginViews(
            credentialTargets = credentialTargets,
            website = normalizedUri
        )
        if (loginViews.isEmpty()) return AutofillRequest.Unfillable

        val inlineSpecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineRequest?.inlinePresentationSpecs
        } else {
            null
        }

        return AutofillRequest.Fillable(
            ignoreAutofillIds = emptyList(),
            inlinePresentationSpecs = inlineSpecs,
            maxInlineSuggestionsCount = inlineRequest?.maxSuggestionCount ?: 0,
            isCompatMode = isCompatMode,
            packageName = packageName,
            partition = AutofillPartition.Login(loginViews),
            uri = normalizedUri,
            fieldSignatureKey = fieldSignatureKey,
        )
    }

    private fun buildLoginViews(
        credentialTargets: List<ParsedItem>,
        website: String,
    ): List<AutofillView.Login> {
        if (credentialTargets.isEmpty()) return emptyList()

        val prioritized = credentialTargets.sortedWith(
            compareByDescending<ParsedItem> { it.isFocused }
                .thenByDescending { it.accuracy.score }
                .thenBy { it.traversalIndex }
        )

        val deduped = linkedMapOf<String, AutofillView.Login>()
        prioritized.forEach { item ->
            val view = item.toLoginView(website = website) ?: return@forEach
            val key = view.data.autofillId.toString()
            val existing = deduped[key]
            if (existing == null || (existing is AutofillView.Login.Username && view is AutofillView.Login.Password)) {
                deduped[key] = view
            }
        }

        return deduped.values.toList()
    }

    private fun ParsedItem.toLoginView(website: String): AutofillView.Login? {
        val data = AutofillView.Data(
            autofillId = id,
            autofillType = View.AUTOFILL_TYPE_TEXT,
            isFocused = isFocused,
            textValue = value,
            website = website
        )
        return when (hint) {
            FieldHint.PASSWORD, FieldHint.NEW_PASSWORD -> AutofillView.Login.Password(data)
            FieldHint.USERNAME, FieldHint.EMAIL_ADDRESS, FieldHint.PHONE_NUMBER -> AutofillView.Login.Username(data)
            else -> null
        }
    }
}


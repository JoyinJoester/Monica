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
        fillableTargets: List<ParsedItem>,
        inlineRequest: InlineSuggestionsRequest?,
        fieldSignatureKey: String? = null,
        isCompatMode: Boolean = false,
    ): AutofillRequest {
        val normalizedUri = uri?.trim().takeUnless { it.isNullOrBlank() } ?: "androidapp://$packageName"
        val views = buildViews(
            fillableTargets = fillableTargets,
            website = normalizedUri
        )
        if (views.isEmpty()) return AutofillRequest.Unfillable

        val inlineSpecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineRequest?.inlinePresentationSpecs
        } else {
            null
        }

        val partition = if (views.any { it !is AutofillView.Login }) {
            AutofillPartition.Generic(views)
        } else {
            AutofillPartition.Login(views.filterIsInstance<AutofillView.Login>())
        }

        return AutofillRequest.Fillable(
            ignoreAutofillIds = emptyList(),
            inlinePresentationSpecs = inlineSpecs,
            maxInlineSuggestionsCount = inlineRequest?.maxSuggestionCount ?: 0,
            isCompatMode = isCompatMode,
            packageName = packageName,
            partition = partition,
            uri = normalizedUri,
            fieldSignatureKey = fieldSignatureKey,
        )
    }

    private fun buildViews(
        fillableTargets: List<ParsedItem>,
        website: String,
    ): List<AutofillView> {
        if (fillableTargets.isEmpty()) return emptyList()

        val promoteCandidateId = resolveUsernamePromotionCandidate(fillableTargets)

        val prioritized = fillableTargets.sortedWith(
            compareByDescending<ParsedItem> { it.isFocused }
                .thenByDescending { it.accuracy.score }
                .thenBy { it.traversalIndex }
        )

        val deduped = linkedMapOf<String, AutofillView>()
        prioritized.forEach { item ->
            val shouldPromoteToUsername =
                promoteCandidateId != null && item.id.toString() == promoteCandidateId
            val view = item.toAutofillView(
                website = website,
                promoteToUsername = shouldPromoteToUsername,
            ) ?: return@forEach
            val key = view.data.autofillId.toString()
            val existing = deduped[key]
            if (existing == null || isHigherPriorityView(candidate = view, existing = existing)) {
                deduped[key] = view
            }
        }

        return deduped.values.toList()
    }

    private fun resolveUsernamePromotionCandidate(fillableTargets: List<ParsedItem>): String? {
        val hasPassword = fillableTargets.any {
            it.hint == FieldHint.PASSWORD || it.hint == FieldHint.NEW_PASSWORD
        }
        if (!hasPassword) return null

        val hasExplicitAccountTarget = fillableTargets.any {
            it.hint == FieldHint.USERNAME ||
                it.hint == FieldHint.EMAIL_ADDRESS ||
                it.hint == FieldHint.PHONE_NUMBER
        }
        if (hasExplicitAccountTarget) return null

        val candidate = fillableTargets
            .asSequence()
            .filter {
                it.hint == FieldHint.PERSON_NAME ||
                    it.hint == FieldHint.PERSON_FIRST_NAME ||
                    it.hint == FieldHint.PERSON_LAST_NAME
            }
            .sortedWith(
                compareByDescending<ParsedItem> { it.isFocused }
                    .thenByDescending { it.accuracy.score }
                    .thenBy { it.traversalIndex }
            )
            .firstOrNull()

        return candidate?.id?.toString()
    }

    private fun ParsedItem.toAutofillView(
        website: String,
        promoteToUsername: Boolean,
    ): AutofillView? {
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
            FieldHint.PERSON_NAME,
            FieldHint.PERSON_FIRST_NAME,
            FieldHint.PERSON_LAST_NAME,
            -> if (promoteToUsername) {
                AutofillView.Login.Username(data)
            } else {
                AutofillView.Field(hint = hint, data = data)
            }
            FieldHint.CREDIT_CARD_NUMBER,
            FieldHint.CREDIT_CARD_EXPIRATION_DATE,
            FieldHint.CREDIT_CARD_EXPIRATION_MONTH,
            FieldHint.CREDIT_CARD_EXPIRATION_YEAR,
            FieldHint.CREDIT_CARD_SECURITY_CODE,
            FieldHint.CREDIT_CARD_HOLDER_NAME,
            FieldHint.POSTAL_ADDRESS,
            FieldHint.POSTAL_CODE,
            FieldHint.ADDRESS_CITY,
            FieldHint.ADDRESS_REGION,
            FieldHint.ADDRESS_COUNTRY,
            FieldHint.COMPANY_NAME,
            FieldHint.IDENTITY_NUMBER,
            -> AutofillView.Field(hint = hint, data = data)
            else -> null
        }
    }

    private fun isHigherPriorityView(candidate: AutofillView, existing: AutofillView): Boolean {
        return viewPriority(candidate) > viewPriority(existing)
    }

    private fun viewPriority(view: AutofillView): Int {
        return when (view) {
            is AutofillView.Login.Password -> 4
            is AutofillView.Login.Username -> 3
            is AutofillView.Field -> 2
        }
    }
}


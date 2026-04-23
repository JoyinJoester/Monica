package takagi.ru.monica.autofill_ng.processor

import android.content.Context
import android.service.autofill.FillResponse
import android.view.inputmethod.InlineSuggestionsRequest
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill_ng.builder.FillResponseBuilderNg
import takagi.ru.monica.autofill_ng.builder.FilledDataBuilderNg
import takagi.ru.monica.autofill_ng.model.AutofillRequest
import takagi.ru.monica.autofill_ng.parser.AutofillParserNg
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager

class AutofillProcessorNg(
    private val context: Context,
    private val parser: AutofillParserNg = AutofillParserNg(),
    private val filledDataBuilder: FilledDataBuilderNg =
        FilledDataBuilderNg(
            context = context.applicationContext,
            securityManager = SecurityManager(context.applicationContext)
        ),
    private val fillResponseBuilder: FillResponseBuilderNg = FillResponseBuilderNg(context),
) {

    fun process(
        packageName: String,
        uri: String?,
        fillableTargets: List<ParsedItem>,
        inlineRequest: InlineSuggestionsRequest?,
        isCompatMode: Boolean,
        passwords: List<PasswordEntry>,
        fieldSignatureKey: String? = null,
        preferDirectAutoFill: Boolean = false,
    ): FillResponse? {
        val request = parser.parse(
            packageName = packageName,
            uri = uri,
            fillableTargets = fillableTargets,
            inlineRequest = inlineRequest,
            fieldSignatureKey = fieldSignatureKey,
            isCompatMode = isCompatMode,
            preferDirectAutoFill = preferDirectAutoFill,
        )

        if (request !is AutofillRequest.Fillable) return null

        val filledData = filledDataBuilder.build(
            request = request,
            passwords = passwords
        )
        return fillResponseBuilder.build(
            request = request,
            filledData = filledData
        )
    }
}


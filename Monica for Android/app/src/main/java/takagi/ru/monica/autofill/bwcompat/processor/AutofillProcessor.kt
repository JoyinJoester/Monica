package takagi.ru.monica.autofill.bwcompat.processor

import android.content.Context
import android.service.autofill.FillResponse
import android.view.inputmethod.InlineSuggestionsRequest
import takagi.ru.monica.autofill.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill.bwcompat.builder.FillResponseBuilder
import takagi.ru.monica.autofill.bwcompat.builder.FilledDataBuilder
import takagi.ru.monica.autofill.bwcompat.model.AutofillRequest
import takagi.ru.monica.autofill.bwcompat.parser.AutofillParser
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager

class AutofillProcessor(
    private val context: Context,
    private val parser: AutofillParser = AutofillParser(),
    private val filledDataBuilder: FilledDataBuilder =
        FilledDataBuilder(SecurityManager(context.applicationContext)),
    private val fillResponseBuilder: FillResponseBuilder = FillResponseBuilder(context),
) {

    fun process(
        packageName: String,
        uri: String?,
        credentialTargets: List<ParsedItem>,
        inlineRequest: InlineSuggestionsRequest?,
        passwords: List<PasswordEntry>,
    ): FillResponse? {
        val request = parser.parse(
            packageName = packageName,
            uri = uri,
            credentialTargets = credentialTargets,
            inlineRequest = inlineRequest
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

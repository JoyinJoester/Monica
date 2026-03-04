package takagi.ru.monica.autofill.bwcompat.model

import android.view.autofill.AutofillId
import android.widget.inline.InlinePresentationSpec

/**
 * Bitwarden-compatible parsed request model.
 */
sealed class AutofillRequest {
    data class Fillable(
        val ignoreAutofillIds: List<AutofillId>,
        val inlinePresentationSpecs: List<InlinePresentationSpec>?,
        val maxInlineSuggestionsCount: Int,
        val packageName: String,
        val partition: AutofillPartition.Login,
        val uri: String?,
    ) : AutofillRequest()

    data object Unfillable : AutofillRequest()
}

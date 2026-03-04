package takagi.ru.monica.autofill.bwcompat.model

import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.inline.InlinePresentationSpec

data class FilledData(
    val filledPartitions: List<FilledPartition>,
    val ignoreAutofillIds: List<AutofillId>,
    val originalPartition: AutofillPartition.Login,
    val uri: String?,
)

data class FilledPartition(
    val autofillCipher: AutofillCipher.Login,
    val filledItems: List<FilledItem>,
    val inlinePresentationSpec: InlinePresentationSpec?,
)

data class FilledItem(
    val autofillId: AutofillId,
    val value: AutofillValue,
)

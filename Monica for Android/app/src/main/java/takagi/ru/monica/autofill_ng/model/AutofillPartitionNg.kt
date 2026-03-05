package takagi.ru.monica.autofill_ng.model

import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId

/**
 * Bitwarden-compatible partition model.
 */
sealed class AutofillPartition {
    abstract val optionalSaveIds: List<AutofillId>
    abstract val requiredSaveIds: List<AutofillId>
    abstract val saveType: Int
    abstract val views: List<AutofillView>

    val canPerformSaveRequest: Boolean
        get() = requiredSaveIds.isNotEmpty()

    data class Login(
        override val views: List<AutofillView.Login>,
    ) : AutofillPartition() {
        override val optionalSaveIds: List<AutofillId>
            get() = views
                .filter { it !is AutofillView.Login.Password }
                .map { it.data.autofillId }

        override val requiredSaveIds: List<AutofillId>
            get() = views
                .filterIsInstance<AutofillView.Login.Password>()
                .map { it.data.autofillId }

        override val saveType: Int
            get() = SaveInfo.SAVE_DATA_TYPE_PASSWORD
    }
}

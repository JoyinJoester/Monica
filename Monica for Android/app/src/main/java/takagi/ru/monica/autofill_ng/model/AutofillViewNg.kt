package takagi.ru.monica.autofill_ng.model

import android.view.autofill.AutofillId

/**
 * Bitwarden-compatible autofill view model.
 */
sealed class AutofillView {

    data class Data(
        val autofillId: AutofillId,
        val autofillType: Int,
        val isFocused: Boolean,
        val textValue: String?,
        val website: String?,
    )

    abstract val data: Data

    sealed class Login : AutofillView() {
        data class Username(override val data: Data) : Login()
        data class Password(override val data: Data) : Login()
    }
}

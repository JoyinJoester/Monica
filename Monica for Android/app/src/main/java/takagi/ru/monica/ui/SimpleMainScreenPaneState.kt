package takagi.ru.monica.ui

import androidx.compose.runtime.saveable.Saver
import takagi.ru.monica.data.bitwarden.BitwardenSend

internal data class PasswordPaneUiState(
    val selectedPasswordId: Long? = null,
    val inlinePasswordEditorId: Long? = null,
    val isAddingPasswordInline: Boolean = false
)

internal val PasswordPaneUiStateSaver = Saver<PasswordPaneUiState, List<Any?>>(
    save = { state ->
        listOf(state.selectedPasswordId, state.inlinePasswordEditorId, state.isAddingPasswordInline)
    },
    restore = { saved ->
        PasswordPaneUiState(
            selectedPasswordId = saved.getOrNull(0) as? Long,
            inlinePasswordEditorId = saved.getOrNull(1) as? Long,
            isAddingPasswordInline = saved.getOrNull(2) as? Boolean ?: false
        )
    }
)

internal object PasswordPaneUiStateTransitions {
    fun reset(): PasswordPaneUiState = PasswordPaneUiState()

    fun openInlineAdd(): PasswordPaneUiState = PasswordPaneUiState(isAddingPasswordInline = true)

    fun openInlineEditor(passwordId: Long): PasswordPaneUiState =
        PasswordPaneUiState(inlinePasswordEditorId = passwordId)

    fun openDetail(passwordId: Long): PasswordPaneUiState =
        PasswordPaneUiState(selectedPasswordId = passwordId)

    fun closeInlineEditor(current: PasswordPaneUiState): PasswordPaneUiState =
        current.copy(
            isAddingPasswordInline = false,
            inlinePasswordEditorId = null
        )

    fun clearSelected(current: PasswordPaneUiState): PasswordPaneUiState =
        current.copy(selectedPasswordId = null)
}

internal data class TotpPaneUiState(
    val selectedTotpId: Long? = null,
    val isAddingInline: Boolean = false
)

internal val TotpPaneUiStateSaver = Saver<TotpPaneUiState, List<Any?>>(
    save = { state ->
        listOf(state.selectedTotpId, state.isAddingInline)
    },
    restore = { saved ->
        TotpPaneUiState(
            selectedTotpId = saved.getOrNull(0) as? Long,
            isAddingInline = saved.getOrNull(1) as? Boolean ?: false
        )
    }
)

internal object TotpPaneUiStateTransitions {
    fun reset(): TotpPaneUiState = TotpPaneUiState()

    fun openInlineAdd(): TotpPaneUiState = TotpPaneUiState(isAddingInline = true)

    fun openDetail(totpId: Long): TotpPaneUiState = TotpPaneUiState(selectedTotpId = totpId)
}

internal data class CardWalletPaneUiState(
    val selectedBankCardId: Long? = null,
    val inlineBankCardEditorId: Long? = null,
    val isAddingBankCardInline: Boolean = false,
    val selectedDocumentId: Long? = null,
    val inlineDocumentEditorId: Long? = null,
    val isAddingDocumentInline: Boolean = false
)

internal val CardWalletPaneUiStateSaver = Saver<CardWalletPaneUiState, List<Any?>>(
    save = { state ->
        listOf(
            state.selectedBankCardId,
            state.inlineBankCardEditorId,
            state.isAddingBankCardInline,
            state.selectedDocumentId,
            state.inlineDocumentEditorId,
            state.isAddingDocumentInline
        )
    },
    restore = { saved ->
        CardWalletPaneUiState(
            selectedBankCardId = saved.getOrNull(0) as? Long,
            inlineBankCardEditorId = saved.getOrNull(1) as? Long,
            isAddingBankCardInline = saved.getOrNull(2) as? Boolean ?: false,
            selectedDocumentId = saved.getOrNull(3) as? Long,
            inlineDocumentEditorId = saved.getOrNull(4) as? Long,
            isAddingDocumentInline = saved.getOrNull(5) as? Boolean ?: false
        )
    }
)

internal object CardWalletPaneUiStateTransitions {
    fun resetAll(): CardWalletPaneUiState = CardWalletPaneUiState()

    fun openBankCardAddInline(): CardWalletPaneUiState =
        CardWalletPaneUiState(isAddingBankCardInline = true)

    fun openBankCardEditInline(cardId: Long): CardWalletPaneUiState =
        CardWalletPaneUiState(inlineBankCardEditorId = cardId)

    fun openBankCardDetail(cardId: Long): CardWalletPaneUiState =
        CardWalletPaneUiState(selectedBankCardId = cardId)

    fun closeBankCardEditor(current: CardWalletPaneUiState): CardWalletPaneUiState =
        current.copy(
            inlineBankCardEditorId = null,
            isAddingBankCardInline = false
        )

    fun clearSelectedBankCard(current: CardWalletPaneUiState): CardWalletPaneUiState =
        current.copy(selectedBankCardId = null)

    fun openDocumentAddInline(): CardWalletPaneUiState =
        CardWalletPaneUiState(isAddingDocumentInline = true)

    fun openDocumentEditInline(documentId: Long): CardWalletPaneUiState =
        CardWalletPaneUiState(inlineDocumentEditorId = documentId)

    fun openDocumentDetail(documentId: Long): CardWalletPaneUiState =
        CardWalletPaneUiState(selectedDocumentId = documentId)

    fun closeDocumentEditor(current: CardWalletPaneUiState): CardWalletPaneUiState =
        current.copy(
            inlineDocumentEditorId = null,
            isAddingDocumentInline = false
        )

    fun clearSelectedDocument(current: CardWalletPaneUiState): CardWalletPaneUiState =
        current.copy(selectedDocumentId = null)

    fun resetDocumentPane(current: CardWalletPaneUiState): CardWalletPaneUiState =
        current.copy(
            selectedDocumentId = null,
            inlineDocumentEditorId = null,
            isAddingDocumentInline = false
        )

    fun resetBankCardPane(current: CardWalletPaneUiState): CardWalletPaneUiState =
        current.copy(
            selectedBankCardId = null,
            inlineBankCardEditorId = null,
            isAddingBankCardInline = false
        )
}

internal data class NotePaneUiState(
    val inlineNoteEditorId: Long? = null,
    val isAddingInline: Boolean = false
)

internal val NotePaneUiStateSaver = Saver<NotePaneUiState, List<Any?>>(
    save = { state ->
        listOf(state.inlineNoteEditorId, state.isAddingInline)
    },
    restore = { saved ->
        NotePaneUiState(
            inlineNoteEditorId = saved.getOrNull(0) as? Long,
            isAddingInline = saved.getOrNull(1) as? Boolean ?: false
        )
    }
)

internal object NotePaneUiStateTransitions {
    fun reset(): NotePaneUiState = NotePaneUiState()

    fun openInlineEditor(noteId: Long?): NotePaneUiState =
        if (noteId == null) {
            NotePaneUiState(isAddingInline = true)
        } else {
            NotePaneUiState(inlineNoteEditorId = noteId)
        }
}

internal data class SendPaneUiState(
    val selectedSend: BitwardenSend? = null,
    val isAddingInline: Boolean = false
)

internal object SendPaneUiStateTransitions {
    fun reset(): SendPaneUiState = SendPaneUiState()

    fun openDetail(send: BitwardenSend): SendPaneUiState = SendPaneUiState(selectedSend = send)

    fun openInlineAdd(): SendPaneUiState = SendPaneUiState(isAddingInline = true)

    fun closeInlineEditor(current: SendPaneUiState): SendPaneUiState =
        current.copy(isAddingInline = false)
}

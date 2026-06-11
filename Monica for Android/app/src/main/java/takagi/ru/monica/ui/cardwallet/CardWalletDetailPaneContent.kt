package takagi.ru.monica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.BankCardDetailScreen
import takagi.ru.monica.ui.screens.DocumentDetailScreen
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel

@Composable
internal fun CardWalletDetailPaneContent(
    bankCardViewModel: BankCardViewModel,
    documentViewModel: DocumentViewModel,
    isAddingBankCardInline: Boolean,
    inlineBankCardEditorId: Long?,
    onInlineBankCardEditorBack: () -> Unit,
    isAddingDocumentInline: Boolean,
    inlineDocumentEditorId: Long?,
    onInlineDocumentEditorBack: () -> Unit,
    selectedBankCardId: Long?,
    onClearSelectedBankCard: () -> Unit,
    onEditBankCard: (Long) -> Unit,
    selectedDocumentId: Long?,
    onClearSelectedDocument: () -> Unit,
    onEditDocument: (Long) -> Unit,
    initialCategoryId: Long? = null,
    initialKeePassDatabaseId: Long? = null,
    initialKeePassGroupPath: String? = null,
    initialMdbxDatabaseId: Long? = null,
    initialMdbxFolderId: String? = null,
    initialBitwardenVaultId: Long? = null,
    initialBitwardenFolderId: String? = null
) {
    val detailContent = remember(
        isAddingBankCardInline,
        inlineBankCardEditorId,
        isAddingDocumentInline,
        inlineDocumentEditorId,
        selectedBankCardId,
        selectedDocumentId
    ) {
        when {
            isAddingBankCardInline -> CardWalletDetailContent.BankCardAdd
            inlineBankCardEditorId != null -> CardWalletDetailContent.BankCardEdit(inlineBankCardEditorId)
            isAddingDocumentInline -> CardWalletDetailContent.DocumentAdd
            inlineDocumentEditorId != null -> CardWalletDetailContent.DocumentEdit(inlineDocumentEditorId)
            selectedBankCardId != null -> CardWalletDetailContent.BankCardDetail(selectedBankCardId)
            selectedDocumentId != null -> CardWalletDetailContent.DocumentDetail(selectedDocumentId)
            else -> CardWalletDetailContent.Empty
        }
    }

    AnimatedContent(
        targetState = detailContent,
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(240)) +
                    scaleIn(initialScale = 0.94f, animationSpec = tween(320))
                ) togetherWith (
                fadeOut(animationSpec = tween(120)) +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(120))
                ) using SizeTransform(clip = false)
        },
        label = "CardWalletDetailPaneContent"
    ) { content ->
        when (content) {
            CardWalletDetailContent.BankCardAdd,
            is CardWalletDetailContent.BankCardEdit -> {
                val editorId = (content as? CardWalletDetailContent.BankCardEdit)?.cardId
                AddEditBankCardScreen(
                    viewModel = bankCardViewModel,
                    cardId = editorId,
                    initialCategoryId = initialCategoryId,
                    initialKeePassDatabaseId = initialKeePassDatabaseId,
                    initialKeePassGroupPath = initialKeePassGroupPath,
                    initialMdbxDatabaseId = initialMdbxDatabaseId,
                    initialMdbxFolderId = initialMdbxFolderId,
                    initialBitwardenVaultId = initialBitwardenVaultId,
                    initialBitwardenFolderId = initialBitwardenFolderId,
                    onNavigateBack = onInlineBankCardEditorBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
            CardWalletDetailContent.DocumentAdd,
            is CardWalletDetailContent.DocumentEdit -> {
                val editorId = (content as? CardWalletDetailContent.DocumentEdit)?.documentId
                AddEditDocumentScreen(
                    viewModel = documentViewModel,
                    documentId = editorId,
                    initialCategoryId = initialCategoryId,
                    initialKeePassDatabaseId = initialKeePassDatabaseId,
                    initialKeePassGroupPath = initialKeePassGroupPath,
                    initialMdbxDatabaseId = initialMdbxDatabaseId,
                    initialMdbxFolderId = initialMdbxFolderId,
                    initialBitwardenVaultId = initialBitwardenVaultId,
                    initialBitwardenFolderId = initialBitwardenFolderId,
                    onNavigateBack = onInlineDocumentEditorBack,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is CardWalletDetailContent.BankCardDetail -> {
                BankCardDetailScreen(
                    viewModel = bankCardViewModel,
                    cardId = content.cardId,
                    onNavigateBack = onClearSelectedBankCard,
                    onEditCard = onEditBankCard,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is CardWalletDetailContent.DocumentDetail -> {
                DocumentDetailScreen(
                    viewModel = documentViewModel,
                    documentId = content.documentId,
                    onNavigateBack = onClearSelectedDocument,
                    onEditDocument = onEditDocument,
                    modifier = Modifier.fillMaxSize()
                )
            }
            CardWalletDetailContent.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select an item to view details",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private sealed interface CardWalletDetailContent {
    data object Empty : CardWalletDetailContent
    data object BankCardAdd : CardWalletDetailContent
    data class BankCardEdit(val cardId: Long) : CardWalletDetailContent
    data class BankCardDetail(val cardId: Long) : CardWalletDetailContent
    data object DocumentAdd : CardWalletDetailContent
    data class DocumentEdit(val documentId: Long) : CardWalletDetailContent
    data class DocumentDetail(val documentId: Long) : CardWalletDetailContent
}

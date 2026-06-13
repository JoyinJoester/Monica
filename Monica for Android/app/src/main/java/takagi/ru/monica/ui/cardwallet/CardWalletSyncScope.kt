package takagi.ru.monica.ui.cardwallet

import takagi.ru.monica.ui.components.UnifiedCategoryFilterSelection

fun UnifiedCategoryFilterSelection.isBitwardenWalletScope(): Boolean =
    bitwardenVaultIdForWalletSync() != null

fun UnifiedCategoryFilterSelection.bitwardenVaultIdForWalletSync(): Long? =
    when (this) {
        is UnifiedCategoryFilterSelection.BitwardenVaultFilter -> vaultId
        is UnifiedCategoryFilterSelection.BitwardenFolderFilter -> vaultId
        is UnifiedCategoryFilterSelection.BitwardenVaultStarredFilter -> vaultId
        is UnifiedCategoryFilterSelection.BitwardenVaultUncategorizedFilter -> vaultId
        else -> null
    }

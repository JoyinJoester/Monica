package takagi.ru.monica.ui.vaultv2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
class VaultV2PaneState internal constructor(
    scrollIndex: Int,
    scrollOffset: Int,
    fastScrollRequestKey: Int,
    fastScrollProgress: Float,
    scrollToTopRequestKey: Int,
    storageFilterType: String,
    storageFilterPrimaryId: Long?,
    storageFilterSecondaryKey: String?,
) {
    var scrollIndex by mutableIntStateOf(scrollIndex)
        private set

    var scrollOffset by mutableIntStateOf(scrollOffset)
        private set

    var fastScrollRequestKey by mutableIntStateOf(fastScrollRequestKey)
        private set

    var fastScrollProgress by mutableFloatStateOf(fastScrollProgress.coerceIn(0f, 1f))
        private set

    var scrollToTopRequestKey by mutableIntStateOf(scrollToTopRequestKey)
        private set

    var showBackToTop by mutableStateOf(false)

    var fastScrollIndicatorLabel by mutableStateOf<String?>(null)

    var storageFilterType by mutableStateOf(storageFilterType)
        private set

    var storageFilterPrimaryId by mutableStateOf(storageFilterPrimaryId)
        private set

    var storageFilterSecondaryKey by mutableStateOf(storageFilterSecondaryKey)
        private set

    fun updateScrollPosition(index: Int, offset: Int) {
        val safeIndex = index.coerceAtLeast(0)
        val safeOffset = offset.coerceAtLeast(0)
        if (scrollIndex != safeIndex) {
            scrollIndex = safeIndex
        }
        if (scrollOffset != safeOffset) {
            scrollOffset = safeOffset
        }
    }

    fun requestFastScroll(progress: Float) {
        fastScrollProgress = progress.coerceIn(0f, 1f)
        fastScrollRequestKey += 1
    }

    fun updateFastScrollProgress(progress: Float) {
        fastScrollProgress = progress.coerceIn(0f, 1f)
    }

    fun requestScrollToTop() {
        scrollToTopRequestKey += 1
    }

    fun updateStorageFilter(
        type: String,
        primaryId: Long? = null,
        secondaryKey: String? = null,
    ) {
        storageFilterType = type
        storageFilterPrimaryId = primaryId
        storageFilterSecondaryKey = secondaryKey
    }

    fun clearTransientUi() {
        showBackToTop = false
        fastScrollIndicatorLabel = null
    }

    companion object {
        val Saver: Saver<VaultV2PaneState, Any> = listSaver(
            save = {
                listOf(
                    it.scrollIndex,
                    it.scrollOffset,
                    it.fastScrollRequestKey,
                    it.fastScrollProgress,
                    it.scrollToTopRequestKey,
                    it.storageFilterType,
                    it.storageFilterPrimaryId,
                    it.storageFilterSecondaryKey,
                )
            },
            restore = { restored ->
                VaultV2PaneState(
                    scrollIndex = restored[0] as Int,
                    scrollOffset = restored[1] as Int,
                    fastScrollRequestKey = restored[2] as Int,
                    fastScrollProgress = restored[3] as Float,
                    scrollToTopRequestKey = restored[4] as Int,
                    storageFilterType = restored[5] as String,
                    storageFilterPrimaryId = restored[6] as Long?,
                    storageFilterSecondaryKey = restored[7] as String?,
                )
            }
        )
    }
}

@Composable
fun rememberVaultV2PaneState(): VaultV2PaneState {
    return rememberSaveable(saver = VaultV2PaneState.Saver) {
        VaultV2PaneState(
            scrollIndex = 0,
            scrollOffset = 0,
            fastScrollRequestKey = 0,
            fastScrollProgress = 0f,
            scrollToTopRequestKey = 0,
            storageFilterType = VAULT_V2_STORAGE_FILTER_LOCAL,
            storageFilterPrimaryId = null,
            storageFilterSecondaryKey = null,
        )
    }
}

const val VAULT_V2_STORAGE_FILTER_ALL = "all"
const val VAULT_V2_STORAGE_FILTER_LOCAL = "local"
const val VAULT_V2_STORAGE_FILTER_STARRED = "starred"
const val VAULT_V2_STORAGE_FILTER_UNCATEGORIZED = "uncategorized"
const val VAULT_V2_STORAGE_FILTER_LOCAL_STARRED = "local_starred"
const val VAULT_V2_STORAGE_FILTER_LOCAL_UNCATEGORIZED = "local_uncategorized"
const val VAULT_V2_STORAGE_FILTER_CUSTOM = "custom"
const val VAULT_V2_STORAGE_FILTER_KEEPASS_DATABASE = "keepass_database"
const val VAULT_V2_STORAGE_FILTER_KEEPASS_GROUP = "keepass_group"
const val VAULT_V2_STORAGE_FILTER_KEEPASS_DATABASE_STARRED = "keepass_database_starred"
const val VAULT_V2_STORAGE_FILTER_KEEPASS_DATABASE_UNCATEGORIZED = "keepass_database_uncategorized"
const val VAULT_V2_STORAGE_FILTER_BITWARDEN_VAULT = "bitwarden_vault"
const val VAULT_V2_STORAGE_FILTER_BITWARDEN_FOLDER = "bitwarden_folder"
const val VAULT_V2_STORAGE_FILTER_BITWARDEN_VAULT_STARRED = "bitwarden_vault_starred"
const val VAULT_V2_STORAGE_FILTER_BITWARDEN_VAULT_UNCATEGORIZED = "bitwarden_vault_uncategorized"

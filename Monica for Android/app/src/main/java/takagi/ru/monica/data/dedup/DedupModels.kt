package takagi.ru.monica.data.dedup

import takagi.ru.monica.data.ItemType

enum class DedupScope {
    ALL,
    MONICA_LOCAL,
    KEEPASS,
    BITWARDEN
}

enum class DedupClusterType {
    EXACT_PASSWORD_DUPLICATE,
    CROSS_SOURCE_PASSWORD_MIRROR,
    DUPLICATE_TOTP,
    DUPLICATE_BANK_CARD,
    DUPLICATE_DOCUMENT,
    PASSKEY_ACCOUNT_CONFLICT
}

enum class DedupAction {
    APPLY_PASSWORD_PREFERENCE,
    MOVE_LOCAL_SECURE_ITEM_COPIES_TO_TRASH,
    IGNORE_CLUSTER
}

enum class DedupSourceKind {
    MONICA_LOCAL,
    KEEPASS,
    BITWARDEN
}

enum class DedupPreferredSource {
    MONICA_LOCAL,
    KEEPASS,
    BITWARDEN
}

data class DedupSourceDescriptor(
    val instanceKey: String,
    val kind: DedupSourceKind,
    val label: String
)

sealed interface DedupEntityRef {
    val stableId: String
    val title: String
    val subtitle: String
    val sourceKind: DedupSourceKind
    val sourceLabel: String
    val sourceInstanceKey: String
    val isLocal: Boolean

    data class PasswordRef(
        val entryId: Long,
        override val title: String,
        override val subtitle: String,
        override val sourceKind: DedupSourceKind,
        override val sourceLabel: String,
        override val sourceInstanceKey: String,
        override val isLocal: Boolean
    ) : DedupEntityRef {
        override val stableId: String = "password:$entryId"
    }

    data class SecureItemRef(
        val itemId: Long,
        val itemType: ItemType,
        override val title: String,
        override val subtitle: String,
        override val sourceKind: DedupSourceKind,
        override val sourceLabel: String,
        override val sourceInstanceKey: String,
        override val isLocal: Boolean
    ) : DedupEntityRef {
        override val stableId: String = "secure:${itemType.name}:$itemId"
    }

    data class PasskeyRef(
        val credentialId: String,
        override val title: String,
        override val subtitle: String,
        override val sourceKind: DedupSourceKind,
        override val sourceLabel: String,
        override val sourceInstanceKey: String,
        override val isLocal: Boolean
    ) : DedupEntityRef {
        override val stableId: String = "passkey:$credentialId"
    }
}

data class DedupCluster(
    val id: String,
    val type: DedupClusterType,
    val keyLabel: String,
    val itemCount: Int,
    val items: List<DedupEntityRef>,
    val sources: Set<DedupSourceKind>,
    val sourceDescriptors: List<DedupSourceDescriptor>,
    val supportedActions: List<DedupAction>
)

data class DedupActionResult(
    val message: String,
    val changedCount: Int = 0
)

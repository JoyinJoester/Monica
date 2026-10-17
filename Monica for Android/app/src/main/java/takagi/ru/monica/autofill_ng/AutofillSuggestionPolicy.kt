package takagi.ru.monica.autofill_ng

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill_ng.core.AutofillLogger

/** Shared settings policy for framework and manually opened credential suggestions. */
internal object AutofillSuggestionPolicy {
    data class UriStrategyConfig(
        val allowSubdomainMatch: Boolean,
        val allowBaseDomainMatch: Boolean,
        val exactDomainOnly: Boolean,
        val disableMatch: Boolean = false,
    )

    fun resolveUriStrategyConfig(
        strategy: DomainMatchStrategy,
        allowSubdomainToggle: Boolean,
    ): UriStrategyConfig {
        return when (strategy) {
            DomainMatchStrategy.BASE_DOMAIN -> UriStrategyConfig(
                allowSubdomainMatch = allowSubdomainToggle,
                allowBaseDomainMatch = true,
                exactDomainOnly = false,
            )

            DomainMatchStrategy.DOMAIN -> UriStrategyConfig(
                allowSubdomainMatch = allowSubdomainToggle,
                allowBaseDomainMatch = false,
                exactDomainOnly = false,
            )

            DomainMatchStrategy.EXACT_MATCH -> UriStrategyConfig(
                allowSubdomainMatch = false,
                allowBaseDomainMatch = false,
                exactDomainOnly = true,
            )

            DomainMatchStrategy.NEVER -> UriStrategyConfig(
                allowSubdomainMatch = false,
                allowBaseDomainMatch = false,
                exactDomainOnly = false,
                disableMatch = true,
            )

            DomainMatchStrategy.STARTS_WITH,
            DomainMatchStrategy.REGEX -> {
                AutofillLogger.w(
                    "AF",
                    "URI strategy $strategy is not natively supported by NG matcher; fallback to BASE_DOMAIN",
                )
                UriStrategyConfig(
                    allowSubdomainMatch = allowSubdomainToggle,
                    allowBaseDomainMatch = true,
                    exactDomainOnly = false,
                )
            }
        }
    }

    fun applyDefaultSourceFilter(
        entries: List<PasswordEntry>,
        sourceFilter: AutofillPreferences.AutofillDefaultSourceFilter,
        keepassDatabaseId: Long?,
        bitwardenVaultId: Long?,
    ): List<PasswordEntry> {
        return when (sourceFilter) {
            AutofillPreferences.AutofillDefaultSourceFilter.ALL -> entries
            AutofillPreferences.AutofillDefaultSourceFilter.LOCAL -> entries.filter { entry ->
                entry.isLocalOnlyEntry()
            }
            AutofillPreferences.AutofillDefaultSourceFilter.KEEPASS -> entries.filter { entry ->
                entry.keepassDatabaseId != null &&
                    (keepassDatabaseId == null || entry.keepassDatabaseId == keepassDatabaseId)
            }
            AutofillPreferences.AutofillDefaultSourceFilter.BITWARDEN -> entries.filter { entry ->
                entry.bitwardenVaultId != null &&
                    (bitwardenVaultId == null || entry.bitwardenVaultId == bitwardenVaultId)
            }
        }
    }

}

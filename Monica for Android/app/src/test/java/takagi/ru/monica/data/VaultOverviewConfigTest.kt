package takagi.ru.monica.data

import java.util.Date
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VaultOverviewConfigTest {
    @Test fun missingScopeIncludesSyncedVaultsAfterUpgrade() {
        for (saved in listOf(null, "{}", """{"hidden":["RECENT"],"recommendItems":false}""")) {
            assertEquals("all", VaultOverviewConfig.decode(saved).scope)
        }
        assertEquals(setOf("RECENT"), VaultOverviewConfig.decode("""{"hidden":["RECENT"]}""").hidden)
    }

    @Test fun explicitScopesSurviveSavingAndReloadingIncludingLocal() {
        for (scope in listOf("local", "all", "bitwarden:136", "keepass:3", "mdbx:5")) {
            val restored = VaultOverviewConfig.decode("""{"scope":"$scope"}""")
            assertEquals(scope, restored.scope)
            assertEquals(scope, VaultOverviewConfig.decode(restored.encode()).scope)
        }
    }

    @Test fun defaultsEnableOverviewWithoutChangingListLayout() {
        val settings = AppSettings()
        assertTrue(settings.vaultOverviewEnabled)
        assertEquals("all", settings.vaultOverviewConfig.scope)
        assertEquals(9, settings.vaultOverviewConfig.order.size)
        assertEquals(listOf("CARDS", "RECENT", "ITEMS"), settings.vaultOverviewConfig.order.take(3))
        val oldOrder = VaultOverviewModule.defaultOrder.filterNot { it == "RECENT" }
        assertEquals(listOf("CARDS", "RECENT", "ITEMS"), VaultOverviewConfig(order = oldOrder).normalized().order.take(3))
        assertEquals(settings.vaultV2LayoutMode, settings.copy(vaultOverviewEnabled = false).vaultV2LayoutMode)
    }

    @Test fun layoutAndIndependentPinsSurviveRoundTrip() {
        val config = VaultOverviewConfig(
            order = VaultOverviewModule.defaultOrder.reversed(), hidden = setOf("FOLDERS"),
            collapsed = setOf("FAVORITES"), pinnedCards = listOf("local/BANK_CARD/7:10"),
            pinnedItems = listOf("keepass:2/password/uuid"), recommendCards = false,
            recommendItems = true, scope = "keepass:2",
        )
        assertEquals(config, VaultOverviewConfig.decode(config.encode()))
        assertEquals(config.pinnedItems, config.copy(pinnedCards = emptyList()).pinnedItems)
    }

    @Test fun malformedAndFuturePreferencesCannotRemoveRequiredModulesOrSelectInvalidScopes() {
        assertEquals(VaultOverviewConfig(), VaultOverviewConfig.decode("broken JSON"))
        val normalized = VaultOverviewConfig.decode("""{"order":["ITEMS","ITEMS","FUTURE"],"hidden":["FUTURE"],"scope":"mdbx:-2","future":true}""")
        assertEquals("ITEMS", normalized.order.first())
        assertEquals(VaultOverviewModule.defaultOrder.toSet(), normalized.order.toSet())
        assertTrue(normalized.hidden.isEmpty())
        assertEquals("local", normalized.scope)
        assertEquals("local", VaultOverviewConfig(scope = "keepass:9999999999999999999999").normalized().scope)
    }

    @Test fun removingPinnedAndRecommendedItemsPersistsWithoutChangingOtherModules() {
        val config = VaultOverviewConfig(pinnedItems = listOf("local/password/one", "local/password/two"),
            pinnedCards = listOf("local/BANK_CARD/card"), scope = "all")
        val removed = config.removeFrequentItems(listOf("local/password/one", "bitwarden:2/password/recommended"))
        val restored = VaultOverviewConfig.decode(removed.encode())
        assertEquals(listOf("local/password/two"), restored.pinnedItems)
        assertEquals(setOf("local/password/one", "bitwarden:2/password/recommended"), restored.excludedFrequentItems)
        assertEquals(config.pinnedCards, restored.pinnedCards)
        assertEquals(config.scope, restored.scope)
        assertTrue(restored.recommendItems)
        assertEquals(restored, restored.removeFrequentItems(listOf("local/password/one")))
        assertTrue(VaultOverviewConfig.decode("""{"pinnedItems":["local/password/one"]}""").excludedFrequentItems.isEmpty())
    }

    @Test fun addingAnItemAgainClearsOnlyItsExclusionAndHonorsThePinLimit() {
        val removed = VaultOverviewConfig().removeFrequentItems(listOf("one", "two"))
        val restored = VaultOverviewConfig.decode(removed.togglePinnedItem("one").encode())
        assertEquals(listOf("one"), restored.pinnedItems)
        assertEquals(setOf("two"), restored.excludedFrequentItems)
        val full = removed.copy(pinnedItems = List(8) { "pin$it" })
        assertEquals(full, full.togglePinnedItem("two"))
        val replaced = full.togglePinnedItem("pin0").togglePinnedItem("two")
        assertEquals(8, replaced.pinnedItems.size)
        assertTrue("two" in replaced.pinnedItems)
        assertFalse("pin0" in replaced.pinnedItems)
        assertEquals(setOf("one"), replaced.excludedFrequentItems)
    }

    @Test fun legacyItemPinsKeepTheFirstEightWhileCardsRetainTheirIndependentLimit() {
        val legacy = VaultOverviewConfig(
            pinnedItems = listOf("", "pin1") + (1..12).map { "pin$it" },
            pinnedCards = List(205) { "card$it" },
            excludedFrequentItems = setOf("pin1", "pin9", "excluded"),
        )
        // Serialize without encode(), as an older app could save more than eight.
        val restored = VaultOverviewConfig.decode(Json.encodeToString(legacy))
        assertEquals((1..8).map { "pin$it" }, restored.pinnedItems)
        assertEquals(legacy.pinnedCards.take(200), restored.pinnedCards)
        assertEquals(setOf("pin9", "excluded"), restored.excludedFrequentItems)
        assertEquals(restored, VaultOverviewConfig.decode(restored.encode()))
        assertEquals(restored, restored.togglePinnedItem("pin9"))
    }

    @Test fun identitiesSeparateDatabasesTypesAndReusedLocalIdsButSurviveExternalRowRecreation() {
        val password = PasswordEntry(id = 1, title = "One", website = "", username = "", password = "secret", createdAt = Date(10))
        val card = SecureItem(id = 1, title = "One", itemType = ItemType.BANK_CARD, itemData = "secret", createdAt = Date(10))
        assertNotEquals(password.vaultOverviewKey(), card.vaultOverviewKey())
        assertNotEquals(password.vaultOverviewKey(), password.copy(createdAt = Date(20)).vaultOverviewKey())
        val synced = password.copy(bitwardenVaultId = 2, bitwardenCipherId = "cipher")
        assertEquals(synced.vaultOverviewKey(), synced.copy(id = 200, createdAt = Date(99)).vaultOverviewKey())
        assertNotEquals(synced.vaultOverviewKey(), synced.copy(bitwardenVaultId = 3).vaultOverviewKey())
        val keepass = password.copy(keepassDatabaseId = 2, keepassEntryUuid = "uuid")
        assertEquals(keepass.vaultOverviewKey(), keepass.copy(id = 500, createdAt = Date(99)).vaultOverviewKey())
        assertFalse(synced.vaultOverviewKey().contains("secret"))
    }
}

package takagi.ru.monica.util

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.data.model.TotpData

class TotpImportTitleTest {
    @Test fun issuerBecomesTitleWithoutChangingAccountOrOriginalLabel() {
        val data = TotpData(secret = "JBSWY3DPEHPK3PXP", issuer = "Example", accountName = "alice@example.com")
        val item = TotpParseResult(data, "Example:alice@example.com", data.accountName)
        assertEquals("Example", item.suggestedTitle)
        assertEquals("Example:alice@example.com", item.label)
        assertEquals(data, item.totpData)
    }

    @Test fun issuerMayContainColonsAndDoesNotHaveToMatchUriLabel() {
        val item = TotpParseResult(TotpData(secret = "TEST", issuer = " Example: Team ", accountName = "alice"),
            "Old label:alice", "alice")
        assertEquals("Example: Team", item.suggestedTitle)
    }

    @Test fun missingIssuerFallsBackToCompleteAccountThenOriginalLabel() {
        assertEquals("alice:work@example.com", suggestedTotpTitle(TotpData(secret = "TEST", issuer = " ",
            accountName = " alice:work@example.com "), "Other label"))
        assertEquals("Original", suggestedTotpTitle(TotpData(secret = "TEST"), " Original "))
        assertEquals("", suggestedTotpTitle(TotpData(secret = "TEST")))
    }

    @Test fun multipleAccountsAtOneIssuerRemainSeparateDuringMigration() {
        val one = TotpParseResult(TotpData(secret = "JBSWY3DPEHPK3PXP", issuer = "Example", accountName = "alice"), "Example:alice", "alice")
        val two = TotpParseResult(one.totpData.copy(secret = "GEZDGNBVGY3TQOJQ", accountName = "bob"), "Example:bob", "bob")
        val plan = planTotpMigrationBatch(listOf(one, two)) as TotpMigrationBatchPlan.Ready
        assertEquals(0, plan.duplicateCount)
        assertEquals(listOf("Example", "Example"), plan.items.map { it.suggestedTitle })
        assertEquals(listOf("alice", "bob"), plan.items.map { it.totpData.accountName })
    }
}

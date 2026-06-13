package takagi.ru.monica.utils

import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavBillingAddressBackupGuardTest {

    @Test
    fun webDavBackupAndRestoreKeepBillingAddressesInCardWalletBackup() {
        val source = projectFile("app/src/main/java/takagi/ru/monica/utils/WebDavHelper.kt")

        assertTrue(source.contains("item.itemType != ItemType.BILLING_ADDRESS"))
        assertTrue(source.contains("ItemType.BILLING_ADDRESS -> preferences.includeBankCards || preferences.includeDocuments"))
        assertTrue(source.contains("ItemType.BILLING_ADDRESS -> File(foldersRootDir, \"${'$'}folderKey/billing_addresses\")"))
        assertTrue(source.contains("ItemType.BILLING_ADDRESS -> \"billing_address\""))
        assertTrue(source.contains("restoreCardWalletItemFromJson(tempFile, ItemType.BILLING_ADDRESS)"))
        assertTrue(source.contains("deleteAllLocalItemsByType(takagi.ru.monica.data.ItemType.BILLING_ADDRESS)"))
        assertTrue(source.contains("billingAddresses = cardWalletItems.count { it.itemType == ItemType.BILLING_ADDRESS }"))
        assertTrue(source.contains("billingAddresses = if (restoredBillingAddressCount > 0) restoredBillingAddressCount else billingAddressItems"))
    }

    @Test
    fun webDavBackupAndRestoreKeepPaymentAccountsInCardWalletBackup() {
        val source = projectFile("app/src/main/java/takagi/ru/monica/utils/WebDavHelper.kt")

        assertTrue(source.contains("item.itemType != ItemType.PAYMENT_ACCOUNT"))
        assertTrue(source.contains("ItemType.PAYMENT_ACCOUNT -> preferences.includeBankCards || preferences.includeDocuments"))
        assertTrue(source.contains("ItemType.PAYMENT_ACCOUNT -> File(foldersRootDir, \"${'$'}folderKey/payment_accounts\")"))
        assertTrue(source.contains("ItemType.PAYMENT_ACCOUNT -> \"payment_account\""))
        assertTrue(source.contains("restoreCardWalletItemFromJson(tempFile, ItemType.PAYMENT_ACCOUNT)"))
        assertTrue(source.contains("deleteAllLocalItemsByType(takagi.ru.monica.data.ItemType.PAYMENT_ACCOUNT)"))
        assertTrue(source.contains("paymentAccounts = cardWalletItems.count { it.itemType == ItemType.PAYMENT_ACCOUNT }"))
        assertTrue(source.contains("paymentAccounts = if (restoredPaymentAccountCount > 0) restoredPaymentAccountCount else paymentAccountItems"))
    }

    @Test
    fun backupReportsExposeBillingAddressCounts() {
        val source = projectFile("app/src/main/java/takagi/ru/monica/data/BackupReport.kt")

        assertTrue(source.contains("val billingAddresses: Int = 0"))
        assertTrue(source.contains("val paymentAccounts: Int = 0"))
        assertTrue(source.contains("账单地址: ${'$'}{successItems.billingAddresses}/${'$'}{totalItems.billingAddresses}"))
        assertTrue(source.contains("账单地址: ${'$'}{restoredSuccessfully.billingAddresses}/${'$'}{backupContains.billingAddresses}"))
        assertTrue(source.contains("支付方式: ${'$'}{successItems.paymentAccounts}/${'$'}{totalItems.paymentAccounts}"))
        assertTrue(source.contains("支付方式: ${'$'}{restoredSuccessfully.paymentAccounts}/${'$'}{backupContains.paymentAccounts}"))
        assertTrue(source.contains("passwords + notes + totp + bankCards + documents + billingAddresses + paymentAccounts"))
    }

    private fun projectFile(relativePath: String): String {
        val start = Paths.get("").toAbsolutePath()
        var cursor = start
        while (cursor.parent != null) {
            val candidate = cursor.resolve(relativePath).toFile()
            if (candidate.exists()) {
                return candidate.readText()
            }
            cursor = cursor.parent
        }
        error("Project file not found from $start: $relativePath")
    }
}

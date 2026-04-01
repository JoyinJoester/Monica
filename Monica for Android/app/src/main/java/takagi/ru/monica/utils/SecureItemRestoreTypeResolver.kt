package takagi.ru.monica.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import takagi.ru.monica.data.ItemType
import java.util.Locale

internal object SecureItemRestoreTypeResolver {
    private val json = Json { ignoreUnknownKeys = true }

    fun resolve(
        rawType: String?,
        itemData: String,
        sourceFileName: String? = null
    ): ItemType? {
        val parsedType = parseTypeAlias(rawType)
        val inferredType = inferTypeFromItemData(itemData)
        val looksLikeLegacyCardDocFile =
            sourceFileName?.contains("cards_docs", ignoreCase = true) == true

        if (looksLikeLegacyCardDocFile && inferredType in setOf(ItemType.BANK_CARD, ItemType.DOCUMENT)) {
            return inferredType
        }

        return parsedType ?: inferredType
    }

    private fun parseTypeAlias(rawType: String?): ItemType? {
        val normalized = rawType?.trim()?.uppercase(Locale.ROOT)?.replace('-', '_') ?: return null
        return when (normalized) {
            ItemType.TOTP.name,
            "AUTHENTICATOR",
            "AUTHENTICATORS",
            "验证器" -> ItemType.TOTP

            ItemType.BANK_CARD.name,
            "BANKCARD",
            "BANK_CARDS",
            "BANKCARDS",
            "CARD",
            "CARDS",
            "CREDIT_CARD",
            "DEBIT_CARD",
            "卡片",
            "银行卡" -> ItemType.BANK_CARD

            ItemType.DOCUMENT.name,
            "DOCUMENTS",
            "DOC",
            "DOCS",
            "IDENTITY",
            "ID_CARD",
            "IDCARD",
            "PASSPORT",
            "DRIVER_LICENSE",
            "证件",
            "身份" -> ItemType.DOCUMENT

            ItemType.NOTE.name,
            "NOTES",
            "SECURE_NOTE",
            "笔记" -> ItemType.NOTE

            else -> null
        }
    }

    private fun inferTypeFromItemData(itemData: String): ItemType? {
        val root = runCatching { json.parseToJsonElement(itemData).jsonObject }.getOrNull() ?: return null
        val keys = root.keys

        if (looksLikeTotp(keys)) return ItemType.TOTP
        if (looksLikeBankCard(keys)) return ItemType.BANK_CARD
        if (looksLikeDocument(keys)) return ItemType.DOCUMENT
        if (looksLikeNote(keys)) return ItemType.NOTE

        return null
    }

    private fun looksLikeTotp(keys: Set<String>): Boolean {
        return "secret" in keys ||
            "otpType" in keys ||
            ("issuer" in keys && "accountName" in keys)
    }

    private fun looksLikeBankCard(keys: Set<String>): Boolean {
        val strongSignals = setOf(
            "cardNumber",
            "cardholderName",
            "expiryMonth",
            "expiryYear",
            "cvv",
            "bankName",
            "iban",
            "swiftBic",
            "routingNumber",
            "accountNumber",
            "branchCode",
            "customerServicePhone"
        )
        val legacySignals = setOf(
            "number",
            "expMonth",
            "expYear",
            "code",
            "fromMonth",
            "fromYear"
        )

        val strongCount = keys.count { it in strongSignals }
        val legacyCount = keys.count { it in legacySignals }
        return strongCount >= 1 || legacyCount >= 2
    }

    private fun looksLikeDocument(keys: Set<String>): Boolean {
        val strongSignals = setOf(
            "documentNumber",
            "documentType",
            "passportNumber",
            "licenseNumber",
            "driverLicense",
            "ssn",
            "issueDate",
            "issuedDate",
            "issuingAuthority",
            "issuedBy",
            "nationality"
        )
        val profileSignals = setOf(
            "firstName",
            "middleName",
            "lastName",
            "address1",
            "address2",
            "address3",
            "postalCode",
            "state",
            "stateProvince",
            "country",
            "company"
        )

        val strongCount = keys.count { it in strongSignals }
        val profileCount = keys.count { it in profileSignals }
        return strongCount >= 1 || profileCount >= 2
    }

    private fun looksLikeNote(keys: Set<String>): Boolean {
        return "content" in keys || "markdown" in keys || "tags" in keys
    }
}

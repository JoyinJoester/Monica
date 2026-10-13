package takagi.ru.monica.data

import java.util.Locale
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.EmbeddedWalletContent
import takagi.ru.monica.data.model.EntryContentFields
import takagi.ru.monica.data.model.ProjectCredentialGroup
import takagi.ru.monica.data.model.BillingAddress
import takagi.ru.monica.data.model.displayFullName

/** Explicit allowlist: passwords, PINs and identity/card numbers never become suggestions. */
enum class CommonSuggestionField(val key: String) {
    BANK_NAME("bankName"), BANK_BRANCH("bankBranch"), BRANCH_CODE("branchCode"), CUSTOMER_SERVICE_PHONE("customerServicePhone"),
    CREDENTIAL_LABEL("credentialLabel"), WEBSITE("website"), ISSUED_BY("issuedBy"),
    FULL_NAME("fullName"), FIRST_NAME("firstName"), MIDDLE_NAME("middleName"), LAST_NAME("lastName"),
    STREET("streetAddress"), APARTMENT("apartment"), ADDRESS_LINE_3("address3"),
    CITY("city"), REGION("stateProvince"), POSTAL_CODE("postalCode"), COUNTRY("country");

    companion object {
        fun forSupplementalKey(key: String): CommonSuggestionField? = when (key) {
            "bankName" -> BANK_NAME
            "bankBranch" -> BANK_BRANCH
            "branchCode" -> BRANCH_CODE
            "customerServicePhone" -> CUSTOMER_SERVICE_PHONE
            "issuedBy" -> ISSUED_BY
            "fullName", "cardholderName" -> FULL_NAME
            "firstName" -> FIRST_NAME
            "middleName" -> MIDDLE_NAME
            "lastName" -> LAST_NAME
            "streetAddress", "address1" -> STREET
            "apartment", "address2" -> APARTMENT
            "address3" -> ADDRESS_LINE_3
            "city" -> CITY
            "stateProvince" -> REGION
            "postalCode" -> POSTAL_CODE
            "country" -> COUNTRY
            else -> null
        }
    }
}

/** Room projection intentionally excludes passwords and all other credential secrets. */
data class CommonSuggestionOwner(
    val id: Long, val website: String,
    val keepassDatabaseId: Long?, val bitwardenVaultId: Long?, val mdbxDatabaseId: Long?,
    val creditCardHolder: String = "", val addressLine: String = "", val city: String = "",
    val state: String = "", val zipCode: String = "", val country: String = "",
)

internal fun commonSuggestionSourceAccessible(
    keepassId: Long?, bitwardenId: Long?, mdbxId: Long?, sources: Set<String>,
): Boolean {
    if (listOfNotNull(keepassId, bitwardenId, mdbxId).size > 1) return false
    val source = when {
        keepassId != null -> "keepass:$keepassId"
        bitwardenId != null -> "bitwarden:$bitwardenId"
        mdbxId != null -> "mdbx:$mdbxId"
        else -> "local"
    }
    return source in sources
}

data class CommonFieldCandidate(val value: String, val uses: Int)

class CommonFieldSuggestionIndex(values: List<String>, field: CommonSuggestionField) {
    val candidates: List<CommonFieldCandidate> = values.asSequence().map(String::trim).filter(String::isNotEmpty)
        // URL paths may be case sensitive. Matching ignores case, stored values do not.
        .groupBy { if (field == CommonSuggestionField.WEBSITE) it else it.lowercase(Locale.ROOT) }
        .values.map { CommonFieldCandidate(it.first(), it.size) }

    fun match(query: String, limit: Int = 3): List<CommonFieldCandidate> {
        val text = query.trim()
        if (text.isEmpty() || limit <= 0) return emptyList()
        return candidates.asSequence()
            .filter { it.value != text && it.value.contains(text, ignoreCase = true) }
            .sortedWith(compareByDescending<CommonFieldCandidate> { it.value.startsWith(text, ignoreCase = true) }
                .thenByDescending { it.uses }.thenBy { it.value.length }
                .thenBy { it.value.lowercase(Locale.ROOT) }.thenBy { it.value })
            .take(limit).toList()
    }
}

/** Source types and field mappings are explicit; unrelated JSON/custom values are never indexed. */
internal fun CommonSuggestionField.walletKinds(): List<EmbeddedWalletContent.Kind> = when (this) {
    CommonSuggestionField.WEBSITE, CommonSuggestionField.CREDENTIAL_LABEL -> emptyList()
    CommonSuggestionField.BANK_NAME, CommonSuggestionField.BANK_BRANCH, CommonSuggestionField.BRANCH_CODE, CommonSuggestionField.CUSTOMER_SERVICE_PHONE -> listOf(EmbeddedWalletContent.Kind.BANK_CARD)
    CommonSuggestionField.ISSUED_BY, CommonSuggestionField.FIRST_NAME, CommonSuggestionField.MIDDLE_NAME,
    CommonSuggestionField.LAST_NAME, CommonSuggestionField.ADDRESS_LINE_3 -> listOf(EmbeddedWalletContent.Kind.DOCUMENT)
    else -> listOf(EmbeddedWalletContent.Kind.BANK_CARD, EmbeddedWalletContent.Kind.DOCUMENT, EmbeddedWalletContent.Kind.ADDRESS)
}

internal fun CommonSuggestionField.metadataTitles(): List<String> {
    val scalar = when (this) {
        CommonSuggestionField.WEBSITE -> emptyList()
        CommonSuggestionField.CREDENTIAL_LABEL -> listOf(ProjectCredentialGroup.FIELD)
        CommonSuggestionField.BANK_NAME, CommonSuggestionField.BANK_BRANCH, CommonSuggestionField.BRANCH_CODE, CommonSuggestionField.CUSTOMER_SERVICE_PHONE -> listOf(EntryContentFields.key("PAYMENT", key))
        CommonSuggestionField.FULL_NAME -> listOf(EntryContentFields.key("CONTACT", "fullName"), EntryContentFields.key("ADDRESS", "fullName"))
        CommonSuggestionField.STREET -> listOf(EntryContentFields.key("CONTACT", "address1"), EntryContentFields.key("ADDRESS", "streetAddress"))
        CommonSuggestionField.APARTMENT -> listOf(EntryContentFields.key("CONTACT", "address2"), EntryContentFields.key("ADDRESS", "apartment"))
        CommonSuggestionField.CITY, CommonSuggestionField.REGION, CommonSuggestionField.POSTAL_CODE,
        CommonSuggestionField.COUNTRY, CommonSuggestionField.ADDRESS_LINE_3 -> listOf(EntryContentFields.key("CONTACT", key), EntryContentFields.key("ADDRESS", key))
        else -> listOf(EntryContentFields.key("CONTACT", key))
    }
    return walletKinds().map(EmbeddedWalletContent::fieldName) + scalar
}

internal fun CommonSuggestionField.ownerValue(owner: CommonSuggestionOwner): String? = when (this) {
    CommonSuggestionField.FULL_NAME -> owner.creditCardHolder
    CommonSuggestionField.STREET -> owner.addressLine
    CommonSuggestionField.CITY -> owner.city
    CommonSuggestionField.REGION -> owner.state
    CommonSuggestionField.POSTAL_CODE -> owner.zipCode
    CommonSuggestionField.COUNTRY -> owner.country
    else -> null
}

internal fun CommonSuggestionField.addressValue(address: BillingAddress): String? = when (this) {
    CommonSuggestionField.STREET -> address.streetAddress
    CommonSuggestionField.APARTMENT -> address.apartment
    CommonSuggestionField.CITY -> address.city
    CommonSuggestionField.REGION -> address.stateProvince
    CommonSuggestionField.POSTAL_CODE -> address.postalCode
    CommonSuggestionField.COUNTRY -> address.country
    else -> null
}

internal fun CommonSuggestionField.walletValue(raw: String, type: ItemType = walletKinds().firstOrNull()?.itemType ?: ItemType.NOTE): String? {
    if (walletKinds().none { it.itemType == type }) return null
    return when (type) {
        ItemType.BANK_CARD -> CardWalletDataCodec.parseBankCardData(raw)?.let { card -> when (this) {
            CommonSuggestionField.BANK_NAME -> card.bankName
            CommonSuggestionField.BANK_BRANCH -> card.bankBranch
            CommonSuggestionField.BRANCH_CODE -> card.branchCode
            CommonSuggestionField.CUSTOMER_SERVICE_PHONE -> card.customerServicePhone
            CommonSuggestionField.FULL_NAME -> card.cardholderName
            else -> addressValue(CardWalletDataCodec.parseBillingAddress(card.billingAddress))
        } }
        ItemType.DOCUMENT -> CardWalletDataCodec.parseDocumentData(raw)?.let { doc -> when (this) {
            CommonSuggestionField.FULL_NAME -> doc.fullName.ifBlank { doc.displayFullName() }
            CommonSuggestionField.FIRST_NAME -> doc.firstName
            CommonSuggestionField.MIDDLE_NAME -> doc.middleName
            CommonSuggestionField.LAST_NAME -> doc.lastName
            CommonSuggestionField.ISSUED_BY -> doc.issuedBy
            CommonSuggestionField.ADDRESS_LINE_3 -> doc.address3
            else -> addressValue(BillingAddress(doc.address1, doc.address2, doc.city, doc.stateProvince, doc.postalCode, doc.country))
        } }
        ItemType.BILLING_ADDRESS -> CardWalletDataCodec.parseBillingAddressData(raw)?.let { address -> when (this) {
            CommonSuggestionField.FULL_NAME -> address.fullName
            else -> addressValue(BillingAddress(address.streetAddress, address.apartment, address.city, address.stateProvince, address.postalCode, address.country))
        } }
        else -> null
    }
}

internal fun CommonSuggestionField.metadataValue(title: String, plain: String): String? {
    if (title !in metadataTitles()) return null
    if (this == CommonSuggestionField.CREDENTIAL_LABEL) return ProjectCredentialGroup.parse(plain)?.label
    if (title.startsWith(EmbeddedWalletContent.PREFIX)) {
        val snapshot = (EmbeddedWalletContent.read(plain) as? EmbeddedWalletContent.ReadResult.Available)?.snapshot ?: return null
        return if (title == EmbeddedWalletContent.fieldName(snapshot.kind)) walletValue(snapshot.itemData.toString(), snapshot.kind.itemType) else null
    }
    return plain
}

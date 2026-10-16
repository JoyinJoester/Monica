package takagi.ru.monica.autofill_ng

import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.BillingAddress
import takagi.ru.monica.data.model.BillingAddressData
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.displayFullName
import takagi.ru.monica.data.model.formatForDisplay

internal data class AutofillPickerRequestProfile(
    val wantsPasswords: Boolean,
    val wantsBankCards: Boolean,
    val wantsDocuments: Boolean,
    val wantsBillingAddresses: Boolean,
)

internal fun buildAutofillPickerRequestProfile(hints: List<String>?): AutofillPickerRequestProfile {
    val normalizedHints = hints.orEmpty().map(::normalizeAutofillHint)
    val loginHintCount = normalizedHints.count(::isLoginAutofillHint)
    val bankCardHintCount = normalizedHints.count(::isBankCardAutofillHint)
    val documentHintCount = normalizedHints.count(::isDocumentAutofillHint)
    val bankCardKeyHintCount = normalizedHints.count(::isBankCardKeyAutofillHint)
    val documentKeyHintCount = normalizedHints.count(::isDocumentKeyAutofillHint)
    val billingAddressKeyHintCount = normalizedHints.count(::isBillingAddressKeyAutofillHint)

    val hasLoginHints = loginHintCount > 0
    val hasBankCardHints = bankCardHintCount > 0
    val hasDocumentHints = documentHintCount > 0
    val hasBillingAddressHints = billingAddressKeyHintCount > 0
    val wantsBankCards = hasBankCardHints &&
        (bankCardKeyHintCount > 0 || bankCardHintCount >= documentHintCount)
    val wantsDocuments = hasDocumentHints &&
        (documentKeyHintCount > 0 || documentHintCount >= bankCardHintCount)
    val wantsBillingAddresses = hasBillingAddressHints && (normalizedHints.any { canonicalHint(it) in addressHints } ||
        (bankCardKeyHintCount == 0 && normalizedHints.none { canonicalHint(it) in identityHints }))
    val wantsStructuredItems = wantsBankCards || wantsDocuments || wantsBillingAddresses
    val wantsPasswords = hasLoginHints || !wantsStructuredItems

    return AutofillPickerRequestProfile(
        wantsPasswords = wantsPasswords,
        wantsBankCards = wantsBankCards,
        wantsDocuments = wantsDocuments,
        wantsBillingAddresses = wantsBillingAddresses,
    )
}

private fun canonicalHint(hint: String?) = AutofillProfileValues.canonical(hint)
private val addressHints = setOf("postal-address", "billing-address", "street-address", "address-line1", "address-line2", "address-line3", "address-level1", "address-level2", "address-level3", "postal-code", "country", "country-name")
private val nameHints = setOf("name", "given-name", "additional-name", "family-name", "honorific-prefix", "organization")
private val identityHints = setOf("identity-number", "document-number", "passport-number", "license-number", "ssn", "social-security-number")
private fun isContactHint(hint: String): Boolean {
    val base = hint.removePrefix("home-").removePrefix("work-")
    return base == "email" || base == "tel" || base.startsWith("tel-")
}
internal fun isBankCardKeyAutofillHint(rawHint: String?): Boolean = canonicalHint(rawHint).startsWith("cc-")
internal fun isDocumentKeyAutofillHint(rawHint: String?): Boolean = canonicalHint(rawHint).let { it in identityHints || it in nameHints || it in addressHints }
internal fun isBillingAddressKeyAutofillHint(rawHint: String?): Boolean = canonicalHint(rawHint).let { it in addressHints || it in nameHints || isContactHint(it) }
internal fun isLoginAutofillHint(rawHint: String?): Boolean = canonicalHint(rawHint) in setOf("username", "password", "new-password", "email", "tel")
internal fun isBankCardAutofillHint(rawHint: String?): Boolean = isBankCardKeyAutofillHint(rawHint) || canonicalHint(rawHint) in addressHints
internal fun isDocumentAutofillHint(rawHint: String?): Boolean = isDocumentKeyAutofillHint(rawHint) || isContactHint(canonicalHint(rawHint))

internal fun mapBankCardAutofillValue(rawHint: String?, data: BankCardData): String? = AutofillProfileValues.card(rawHint, data)

internal fun mapDocumentAutofillValue(rawHint: String?, data: DocumentData): String? = AutofillProfileValues.document(rawHint, data)

internal fun mapBillingAddressAutofillValue(rawHint: String?, data: BillingAddressData): String? = AutofillProfileValues.address(rawHint, data)

internal fun parseBankCardCandidate(
    item: SecureItem,
    decryptIfNeeded: ((String) -> String)? = null
): Pair<SecureItem, BankCardData>? {
    val data = CardWalletDataCodec.parseBankCardData(
        raw = item.itemData,
        decryptIfNeeded = decryptIfNeeded
    ) ?: return null
    return item to data.copy(
        cardholderFirstName = readableAutofillText(data.cardholderFirstName, decryptIfNeeded),
        cardholderMiddleName = readableAutofillText(data.cardholderMiddleName, decryptIfNeeded),
        cardholderLastName = readableAutofillText(data.cardholderLastName, decryptIfNeeded),

        cardNumber = readableAutofillText(data.cardNumber, decryptIfNeeded),
        cardholderName = readableAutofillText(data.cardholderName, decryptIfNeeded),
        expiryMonth = readableAutofillText(data.expiryMonth, decryptIfNeeded),
        expiryYear = readableAutofillText(data.expiryYear, decryptIfNeeded),
        cvv = readableAutofillText(data.cvv, decryptIfNeeded),
        pin = readableAutofillText(data.pin, decryptIfNeeded),
        accountNumber = readableAutofillText(data.accountNumber, decryptIfNeeded),
        bankName = readableAutofillText(data.bankName, decryptIfNeeded),
        bankBranch = readableAutofillText(data.bankBranch, decryptIfNeeded),
        nickname = readableAutofillText(data.nickname, decryptIfNeeded),
        iban = readableAutofillText(data.iban, decryptIfNeeded),
        swiftBic = readableAutofillText(data.swiftBic, decryptIfNeeded),
        routingNumber = readableAutofillText(data.routingNumber, decryptIfNeeded),
        billingAddress = readableAutofillText(data.billingAddress, decryptIfNeeded)
    )
}

internal fun parseDocumentCandidate(
    item: SecureItem,
    decryptIfNeeded: ((String) -> String)? = null
): Pair<SecureItem, DocumentData>? {
    val data = CardWalletDataCodec.parseDocumentData(
        raw = item.itemData,
        decryptIfNeeded = decryptIfNeeded
    ) ?: return null
    return item to data.copy(
        district = readableAutofillText(data.district, decryptIfNeeded),
        homeEmail = readableAutofillText(data.homeEmail, decryptIfNeeded),
        homePhone = readableAutofillText(data.homePhone, decryptIfNeeded),
        workEmail = readableAutofillText(data.workEmail, decryptIfNeeded),
        workPhone = readableAutofillText(data.workPhone, decryptIfNeeded),

        documentNumber = readableAutofillText(data.documentNumber, decryptIfNeeded),
        fullName = readableAutofillText(data.fullName, decryptIfNeeded),
        firstName = readableAutofillText(data.firstName, decryptIfNeeded),
        middleName = readableAutofillText(data.middleName, decryptIfNeeded),
        lastName = readableAutofillText(data.lastName, decryptIfNeeded),
        username = readableAutofillText(data.username, decryptIfNeeded),
        email = readableAutofillText(data.email, decryptIfNeeded),
        phone = readableAutofillText(data.phone, decryptIfNeeded),
        ssn = readableAutofillText(data.ssn, decryptIfNeeded),
        passportNumber = readableAutofillText(data.passportNumber, decryptIfNeeded),
        licenseNumber = readableAutofillText(data.licenseNumber, decryptIfNeeded),
        address1 = readableAutofillText(data.address1, decryptIfNeeded),
        address2 = readableAutofillText(data.address2, decryptIfNeeded),
        address3 = readableAutofillText(data.address3, decryptIfNeeded),
        city = readableAutofillText(data.city, decryptIfNeeded),
        stateProvince = readableAutofillText(data.stateProvince, decryptIfNeeded),
        postalCode = readableAutofillText(data.postalCode, decryptIfNeeded),
        country = readableAutofillText(data.country, decryptIfNeeded),
        company = readableAutofillText(data.company, decryptIfNeeded),
        expiryDate = readableAutofillText(data.expiryDate, decryptIfNeeded)
    )
}

internal fun parseBillingAddressCandidate(
    item: SecureItem,
    decryptIfNeeded: ((String) -> String)? = null
): Pair<SecureItem, BillingAddressData>? {
    val data = CardWalletDataCodec.parseBillingAddressData(
        raw = item.itemData,
        decryptIfNeeded = decryptIfNeeded
    ) ?: return null
    return item to data.copy(
        firstName = readableAutofillText(data.firstName, decryptIfNeeded),
        middleName = readableAutofillText(data.middleName, decryptIfNeeded),
        lastName = readableAutofillText(data.lastName, decryptIfNeeded),
        addressLine3 = readableAutofillText(data.addressLine3, decryptIfNeeded),
        district = readableAutofillText(data.district, decryptIfNeeded),
        homeEmail = readableAutofillText(data.homeEmail, decryptIfNeeded),
        homePhone = readableAutofillText(data.homePhone, decryptIfNeeded),
        workEmail = readableAutofillText(data.workEmail, decryptIfNeeded),
        workPhone = readableAutofillText(data.workPhone, decryptIfNeeded),

        fullName = readableAutofillText(data.fullName, decryptIfNeeded),
        company = readableAutofillText(data.company, decryptIfNeeded),
        streetAddress = readableAutofillText(data.streetAddress, decryptIfNeeded),
        apartment = readableAutofillText(data.apartment, decryptIfNeeded),
        city = readableAutofillText(data.city, decryptIfNeeded),
        stateProvince = readableAutofillText(data.stateProvince, decryptIfNeeded),
        postalCode = readableAutofillText(data.postalCode, decryptIfNeeded),
        country = readableAutofillText(data.country, decryptIfNeeded),
        phone = readableAutofillText(data.phone, decryptIfNeeded),
        email = readableAutofillText(data.email, decryptIfNeeded)
    )
}

internal fun BankCardData.matchesAutofillSearch(query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true
    val billingAddress = CardWalletDataCodec.parseBillingAddress(billingAddress)
    return listOf(
        cardholderFirstName,
        cardholderMiddleName,
        cardholderLastName,

        cardNumber,
        cardholderName,
        bankName,
        bankBranch,
        brand,
        nickname,
        billingAddress.toAutofillAddress(),
        billingAddress.postalCode,
        iban,
        accountNumber,
        customerServicePhone,
    ).any { it.contains(normalized, ignoreCase = true) }
}

internal fun DocumentData.matchesAutofillSearch(query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true
    return listOf(
        firstName,
        middleName,
        lastName,
        district,
        homeEmail,
        homePhone,
        workEmail,
        workPhone,

        documentNumber,
        fullName,
        displayFullName(),
        issuedBy,
        company,
        email,
        phone,
        username,
        passportNumber,
        licenseNumber,
        ssn,
        toAutofillAddress(),
    ).any { it.contains(normalized, ignoreCase = true) }
}

internal fun BillingAddressData.matchesAutofillSearch(query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true
    return listOf(
        firstName,
        middleName,
        lastName,
        addressLine3,
        district,
        homeEmail,
        homePhone,
        workEmail,
        workPhone,

        fullName,
        company,
        streetAddress,
        apartment,
        city,
        stateProvince,
        postalCode,
        country,
        phone,
        email,
        toAutofillAddress(),
    ).any { it.contains(normalized, ignoreCase = true) }
}

internal fun maskBankCardNumber(cardNumber: String): String {
    val compact = cardNumber.filter(Char::isDigit)
    if (compact.isBlank()) return cardNumber
    return "•••• ${compact.takeLast(4)}"
}

internal fun maskDocumentNumber(documentNumber: String): String {
    val trimmed = documentNumber.trim()
    if (trimmed.length <= 4) return trimmed
    return buildString(trimmed.length) {
        repeat(trimmed.length - 4) { append('•') }
        append(trimmed.takeLast(4))
    }
}

internal fun billingAddressDisplayTitle(item: SecureItem, data: BillingAddressData): String {
    return item.title.ifBlank {
        data.fullName.ifBlank { data.company.ifBlank { "Billing Address" } }
    }
}

internal fun billingAddressDisplaySubtitle(data: BillingAddressData): String {
    return listOfNotNull(
        data.fullName.takeIf { it.isNotBlank() },
        data.company.takeIf { it.isNotBlank() },
        data.toAutofillAddress().takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

internal fun documentDisplayTitle(item: SecureItem, data: DocumentData): String {
    return item.title.ifBlank {
        data.displayFullName().ifBlank { data.fullName.ifBlank { "Document" } }
    }
}

internal fun bankCardDisplayTitle(item: SecureItem, data: BankCardData): String {
    return item.title.ifBlank {
        data.bankName.ifBlank { data.cardholderName.ifBlank { "Bank Card" } }
    }
}

internal fun bankCardDisplaySubtitle(data: BankCardData): String {
    return listOfNotNull(
        data.bankName.takeIf { it.isNotBlank() },
        data.cardholderName.takeIf { it.isNotBlank() },
        data.cardNumber.takeIf { it.isNotBlank() }?.let(::maskBankCardNumber),
    ).joinToString(" · ")
}

internal fun documentDisplaySubtitle(data: DocumentData): String {
    return listOfNotNull(
        data.displayFullName().takeIf { it.isNotBlank() } ?: data.fullName.takeIf { it.isNotBlank() },
        resolveIdentityNumber("", data).takeIf { it.isNotBlank() }?.let(::maskDocumentNumber),
        data.issuedBy.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

internal fun bankCardBillingAddressDisplay(data: BankCardData): String {
    return CardWalletDataCodec.parseBillingAddress(data.billingAddress)
        .toAutofillAddress()
}

internal fun documentBillingAddressDisplay(data: DocumentData): String {
    return data.toAutofillAddress()
}

private fun normalizeAutofillHint(rawHint: String?): String {
    return rawHint
        ?.trim()
        ?.lowercase()
        ?.replace('-', '_')
        ?.replace(' ', '_')
        .orEmpty()
}

private fun buildCardExpirationValue(data: BankCardData): String {
    val month = data.expiryMonth.trim()
    val year = data.expiryYear.trim()
    return when {
        month.isBlank() -> year
        year.isBlank() -> month
        year.length <= 2 -> "$month/$year"
        else -> "$month/$year"
    }
}

private fun resolveIdentityNumber(normalizedHint: String, data: DocumentData): String {
    return when {
        normalizedHint.contains("passport") -> data.passportNumber.ifBlank { data.documentNumber }
        normalizedHint.contains("license") -> data.licenseNumber.ifBlank { data.documentNumber }
        normalizedHint.contains("ssn") || normalizedHint.contains("social") -> data.ssn.ifBlank { data.documentNumber }
        else -> listOf(
            data.documentNumber,
            data.passportNumber,
            data.licenseNumber,
            data.ssn,
        ).firstOrNull { it.isNotBlank() }.orEmpty()
    }
}

private fun DocumentData.toAutofillAddress(): String {
    return listOf(
        address1,
        address2,
        address3,
        city,
        stateProvince,
        postalCode,
        country,
    ).filter { it.isNotBlank() }.joinToString(", ")
}

private fun BillingAddress.toAutofillAddress(): String {
    return formatForDisplay().replace("\n", ", ").trim()
}

private fun BillingAddressData.toAutofillAddress(): String {
    return listOf(
        streetAddress,
        apartment,
        city,
        stateProvince,
        postalCode,
        country,
    ).filter { it.isNotBlank() }.joinToString(", ")
}

/** A damaged individual field is unavailable, never ciphertext offered as fill text. */
private fun readableAutofillText(value: String, decrypt: ((String) -> String)?): String = try {
    decrypt?.invoke(value) ?: value
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: Exception) {
    ""
}

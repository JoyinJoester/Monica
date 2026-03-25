package takagi.ru.monica.autofill_ng

import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.BillingAddress
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.displayFullName
import takagi.ru.monica.data.model.formatForDisplay

internal data class AutofillPickerRequestProfile(
    val wantsPasswords: Boolean,
    val wantsBankCards: Boolean,
    val wantsDocuments: Boolean,
)

internal fun buildAutofillPickerRequestProfile(hints: List<String>?): AutofillPickerRequestProfile {
    val normalizedHints = hints.orEmpty().map(::normalizeAutofillHint)
    val loginHintCount = normalizedHints.count(::isLoginAutofillHint)
    val bankCardHintCount = normalizedHints.count(::isBankCardAutofillHint)
    val documentHintCount = normalizedHints.count(::isDocumentAutofillHint)
    val bankCardKeyHintCount = normalizedHints.count(::isBankCardKeyAutofillHint)
    val documentKeyHintCount = normalizedHints.count(::isDocumentKeyAutofillHint)

    val hasLoginHints = loginHintCount > 0
    val dominantBankCard = bankCardHintCount >= 2 &&
        bankCardKeyHintCount >= 1 &&
        bankCardHintCount >= documentHintCount + 1
    val dominantDocument = documentHintCount >= 2 &&
        documentKeyHintCount >= 1 &&
        documentHintCount >= bankCardHintCount + 1
    val wantsBankCards = !hasLoginHints && dominantBankCard
    val wantsDocuments = !hasLoginHints && dominantDocument

    // Keep login suggestions as the default path whenever login hints are present.
    // Structured entry points are enabled only for high-confidence non-login requests.
    val wantsPasswords = hasLoginHints || (!wantsBankCards && !wantsDocuments)

    return AutofillPickerRequestProfile(
        wantsPasswords = wantsPasswords,
        wantsBankCards = wantsBankCards,
        wantsDocuments = wantsDocuments,
    )
}

internal fun isBankCardKeyAutofillHint(rawHint: String?): Boolean {
    val hint = normalizeAutofillHint(rawHint)
    if (hint.isBlank()) return false
    return hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_NUMBER.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_SECURITY_CODE.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_DATE.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_MONTH.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_YEAR.name.lowercase() ||
        hint.contains("card_number") ||
        hint.contains("cc_number") ||
        hint.contains("cvv") ||
        hint.contains("cvc") ||
        hint.contains("expiration") ||
        hint.contains("expiry")
}

internal fun isDocumentKeyAutofillHint(rawHint: String?): Boolean {
    val hint = normalizeAutofillHint(rawHint)
    if (hint.isBlank()) return false
    return hint == EnhancedAutofillStructureParserV2.FieldHint.IDENTITY_NUMBER.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.PERSON_NAME.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.POSTAL_ADDRESS.name.lowercase() ||
        hint.contains("identity") ||
        hint.contains("document") ||
        hint.contains("passport") ||
        hint.contains("license") ||
        hint.contains("street_address") ||
        hint.contains("address_line")
}

internal fun isLoginAutofillHint(rawHint: String?): Boolean {
    val hint = normalizeAutofillHint(rawHint)
    if (hint.isBlank()) return false
    return hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD.name.lowercase() ||
        hint.contains("username") ||
        hint.contains("email") ||
        hint.contains("phone") ||
        hint.contains("mobile") ||
        hint.contains("password")
}

internal fun isBankCardAutofillHint(rawHint: String?): Boolean {
    val hint = normalizeAutofillHint(rawHint)
    if (hint.isBlank()) return false
    return hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_NUMBER.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_DATE.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_MONTH.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_YEAR.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_SECURITY_CODE.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_HOLDER_NAME.name.lowercase() ||
        hint.contains("cc_") ||
        hint.contains("credit_card") ||
        hint.contains("creditcard") ||
        hint.contains("card_number") ||
        hint.contains("cardholder") ||
        hint.contains("holder_name") ||
        hint.contains("cvv") ||
        hint.contains("cvc")
}

internal fun isDocumentAutofillHint(rawHint: String?): Boolean {
    val hint = normalizeAutofillHint(rawHint)
    if (hint.isBlank()) return false
    return hint == EnhancedAutofillStructureParserV2.FieldHint.PERSON_NAME.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.PERSON_FIRST_NAME.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.PERSON_LAST_NAME.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.POSTAL_ADDRESS.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.POSTAL_CODE.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.ADDRESS_CITY.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.ADDRESS_REGION.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.ADDRESS_COUNTRY.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.COMPANY_NAME.name.lowercase() ||
        hint == EnhancedAutofillStructureParserV2.FieldHint.IDENTITY_NUMBER.name.lowercase() ||
        hint.contains("given_name") ||
        hint.contains("first_name") ||
        hint.contains("family_name") ||
        hint.contains("last_name") ||
        hint.contains("full_name") ||
        hint.contains("street_address") ||
        hint.contains("address_line") ||
        hint.contains("address_level") ||
        hint.contains("postal") ||
        hint.contains("zip") ||
        hint.contains("city") ||
        hint.contains("state") ||
        hint.contains("province") ||
        hint.contains("region") ||
        hint.contains("country") ||
        hint.contains("organization") ||
        hint.contains("company") ||
        hint.contains("passport") ||
        hint.contains("license") ||
        hint.contains("document") ||
        hint.contains("identity") ||
        hint.contains("ssn")
}

internal fun mapBankCardAutofillValue(rawHint: String?, data: BankCardData): String? {
    val hint = normalizeAutofillHint(rawHint)
    val billingAddress = CardWalletDataCodec.parseBillingAddress(data.billingAddress)
    return when {
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_NUMBER.name.lowercase() -> data.cardNumber
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_HOLDER_NAME.name.lowercase() -> data.cardholderName
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_SECURITY_CODE.name.lowercase() -> data.cvv
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_MONTH.name.lowercase() -> data.expiryMonth
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_YEAR.name.lowercase() -> data.expiryYear
        hint == EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_DATE.name.lowercase() -> buildCardExpirationValue(data)
        hint == EnhancedAutofillStructureParserV2.FieldHint.POSTAL_ADDRESS.name.lowercase() -> billingAddress.toAutofillAddress()
        hint == EnhancedAutofillStructureParserV2.FieldHint.POSTAL_CODE.name.lowercase() -> billingAddress.postalCode
        hint == EnhancedAutofillStructureParserV2.FieldHint.ADDRESS_CITY.name.lowercase() -> billingAddress.city
        hint == EnhancedAutofillStructureParserV2.FieldHint.ADDRESS_REGION.name.lowercase() -> billingAddress.stateProvince
        hint == EnhancedAutofillStructureParserV2.FieldHint.ADDRESS_COUNTRY.name.lowercase() -> billingAddress.country
        hint == EnhancedAutofillStructureParserV2.FieldHint.PERSON_NAME.name.lowercase() -> data.cardholderName
        hint.contains("cc_number") || hint.contains("card_number") -> data.cardNumber
        hint.contains("cc_name") || hint.contains("cardholder") || hint.contains("holder_name") -> data.cardholderName
        hint.contains("cc_csc") || hint.contains("cvv") || hint.contains("cvc") -> data.cvv
        hint.contains("cc_exp_month") -> data.expiryMonth
        hint.contains("cc_exp_year") -> data.expiryYear
        hint.contains("cc_exp") || hint.contains("expiration") || hint.contains("expiry") -> buildCardExpirationValue(data)
        hint.contains("street_address") || hint.contains("address_line") || hint.contains("postal_address") -> billingAddress.toAutofillAddress()
        hint.contains("postal") || hint.contains("zip") -> billingAddress.postalCode
        hint.contains("city") -> billingAddress.city
        hint.contains("state") || hint.contains("province") || hint.contains("region") -> billingAddress.stateProvince
        hint.contains("country") -> billingAddress.country
        else -> null
    }?.takeIf { it.isNotBlank() }
}

internal fun mapDocumentAutofillValue(rawHint: String?, data: DocumentData): String? {
    val hint = normalizeAutofillHint(rawHint)
    return when {
        hint == EnhancedAutofillStructureParserV2.FieldHint.PERSON_NAME.name.lowercase() -> data.displayFullName().ifBlank { data.fullName }
        hint == EnhancedAutofillStructureParserV2.FieldHint.PERSON_FIRST_NAME.name.lowercase() -> resolveDocumentFirstName(data)
        hint == EnhancedAutofillStructureParserV2.FieldHint.PERSON_LAST_NAME.name.lowercase() -> resolveDocumentLastName(data)
        hint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name.lowercase() -> data.email
        hint == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name.lowercase() -> data.phone
        hint == EnhancedAutofillStructureParserV2.FieldHint.POSTAL_ADDRESS.name.lowercase() -> data.toAutofillAddress()
        hint == EnhancedAutofillStructureParserV2.FieldHint.POSTAL_CODE.name.lowercase() -> data.postalCode
        hint == EnhancedAutofillStructureParserV2.FieldHint.ADDRESS_CITY.name.lowercase() -> data.city
        hint == EnhancedAutofillStructureParserV2.FieldHint.ADDRESS_REGION.name.lowercase() -> data.stateProvince
        hint == EnhancedAutofillStructureParserV2.FieldHint.ADDRESS_COUNTRY.name.lowercase() -> data.country
        hint == EnhancedAutofillStructureParserV2.FieldHint.COMPANY_NAME.name.lowercase() -> data.company
        hint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name.lowercase() -> data.username
        hint == EnhancedAutofillStructureParserV2.FieldHint.IDENTITY_NUMBER.name.lowercase() -> resolveIdentityNumber(hint, data)
        hint.contains("given_name") || hint.contains("first_name") -> resolveDocumentFirstName(data)
        hint.contains("family_name") || hint.contains("last_name") || hint.contains("surname") -> resolveDocumentLastName(data)
        hint.contains("full_name") || hint.contains("person_name") || hint.contains("name") -> data.displayFullName().ifBlank { data.fullName }
        hint.contains("street_address") || hint.contains("address_line") || hint.contains("postal_address") -> data.toAutofillAddress()
        hint.contains("postal") || hint.contains("zip") -> data.postalCode
        hint.contains("city") -> data.city
        hint.contains("state") || hint.contains("province") || hint.contains("region") -> data.stateProvince
        hint.contains("country") -> data.country
        hint.contains("organization") || hint.contains("company") -> data.company
        hint.contains("email") -> data.email
        hint.contains("phone") || hint.contains("mobile") || hint.contains("tel") -> data.phone
        hint.contains("passport") || hint.contains("license") || hint.contains("document") || hint.contains("identity") || hint.contains("ssn") -> resolveIdentityNumber(hint, data)
        else -> null
    }?.takeIf { it.isNotBlank() }
}

internal fun parseBankCardCandidate(item: SecureItem): Pair<SecureItem, BankCardData>? {
    val data = CardWalletDataCodec.parseBankCardData(item.itemData) ?: return null
    return item to data
}

internal fun parseDocumentCandidate(item: SecureItem): Pair<SecureItem, DocumentData>? {
    val data = CardWalletDataCodec.parseDocumentData(item.itemData) ?: return null
    return item to data
}

internal fun BankCardData.matchesAutofillSearch(query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true
    val billingAddress = CardWalletDataCodec.parseBillingAddress(billingAddress)
    return listOf(
        cardNumber,
        cardholderName,
        bankName,
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

private fun resolveDocumentFirstName(data: DocumentData): String {
    if (data.firstName.isNotBlank()) return data.firstName
    return data.displayFullName()
        .split(' ')
        .firstOrNull()
        .orEmpty()
}

private fun resolveDocumentLastName(data: DocumentData): String {
    if (data.lastName.isNotBlank()) return data.lastName
    return data.displayFullName()
        .split(' ')
        .lastOrNull()
        .orEmpty()
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

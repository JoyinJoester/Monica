package takagi.ru.monica.autofill_ng

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale
import takagi.ru.monica.data.model.*

/** Exact field semantics. Full names and explicitly entered name parts never replace each other. */
internal object AutofillProfileValues {
    fun canonical(raw: String?): String {
        val hint = raw.orEmpty().trim().lowercase(Locale.ROOT).replace('_', '-').replace(' ', '-')
        for (group in listOf("home", "work", "mobile", "fax", "pager")) {
            if (hint.startsWith("$group-")) return "$group-${canonical(hint.removePrefix("$group-"))}"
        }
        return when (hint) {
            "personname", "person-name", "full-name" -> "name"
            "person-first-name", "first-name" -> "given-name"
            "person-last-name", "last-name", "surname" -> "family-name"
            "middle-name" -> "additional-name"
            "email-address" -> "email"
            "phone", "phone-number", "telephone", "mobile" -> "tel"
            "postalcode", "zip", "zip-code" -> "postal-code"
            "address-city", "city" -> "address-level2"
            "address-region", "region", "state", "state-province", "province" -> "address-level1"
            "address-country" -> "country"
            "company-name", "company" -> "organization"
            "address-line-1" -> "address-line1"
            "address-line-2" -> "address-line2"
            "address-line-3" -> "address-line3"
            "credit-card-number", "card-number" -> "cc-number"
            "credit-card-holder-name", "cardholder-name", "holder-name" -> "cc-name"
            "credit-card-security-code", "cvv", "cvc" -> "cc-csc"
            "credit-card-expiration-month" -> "cc-exp-month"
            "credit-card-expiration-year" -> "cc-exp-year"
            "credit-card-expiration-date" -> "cc-exp"
            else -> hint
        }
    }

    fun countryCode(country: String): String? {
        val value = country.trim()
        val codes = Locale.getISOCountries()
        return codes.firstOrNull { it.equals(value, true) } ?: codes.firstOrNull {
            val locale = Locale.Builder().setRegion(it).build()
            locale.getDisplayCountry(Locale.getDefault()).equals(value, true) ||
                locale.getDisplayCountry(Locale.ENGLISH).equals(value, true)
        }
    }

    private fun countryName(country: String): String = countryCode(country)?.let {
        Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.getDefault())
    } ?: country

    fun phonePart(hint: String, phone: String, country: String): String? {
        if (phone.isBlank()) return null
        if (hint == "tel") return phone
        val util = PhoneNumberUtil.getInstance()
        val parsed = runCatching { util.parse(phone, countryCode(country) ?: "ZZ") }.getOrNull() ?: return null
        if (!util.isPossibleNumber(parsed)) return null
        val national = util.getNationalSignificantNumber(parsed)
        val areaLength = util.getLengthOfGeographicalAreaCode(parsed)
        val local = national.drop(areaLength)
        return when (hint) {
            "tel-country-code" -> parsed.countryCode.toString()
            "tel-national" -> national
            "tel-area-code" -> national.take(areaLength)
            "tel-local" -> local
            // HTML's prefix/suffix fields describe a seven-digit local number.
            "tel-local-prefix" -> local.takeIf { it.length == 7 }?.take(3)
            "tel-local-suffix" -> local.takeIf { it.length == 7 }?.takeLast(4)
            "tel-extension" -> parsed.extension
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    fun address(raw: String?, data: BillingAddressData): String? {
        var hint = canonical(raw)
        val group = listOf("home", "work", "mobile", "fax", "pager").firstOrNull { hint.startsWith("$it-") }
        if (group != null) hint = canonical(hint.removePrefix("$group-"))
        val email = when (group) { "home" -> data.homeEmail; "work" -> data.workEmail; "fax", "pager" -> ""; else -> data.email }
        val phone = when (group) { "home" -> data.homePhone; "work" -> data.workPhone; "fax", "pager" -> ""; else -> data.phone }
        if (hint == "email") return email.takeIf(String::isNotBlank)
        if (hint == "tel" || hint.startsWith("tel-")) return phonePart(hint, phone, data.country)
        if (group != null) return null
        return when (hint) {
            "name" -> data.fullName
            "given-name" -> data.firstName
            "additional-name" -> data.middleName
            "family-name" -> data.lastName
            "organization" -> data.company
            "address-line1" -> data.streetAddress
            "address-line2" -> data.apartment
            "address-line3" -> data.addressLine3
            "street-address" -> listOf(data.streetAddress, data.apartment, data.addressLine3).filter(String::isNotBlank).joinToString("\n")
            "postal-address", "billing-address" -> listOf(data.streetAddress, data.apartment, data.addressLine3, data.district, data.city, data.stateProvince, data.postalCode, data.country).filter(String::isNotBlank).joinToString(", ")
            "address-level3" -> data.district
            "address-level2" -> data.city
            "address-level1" -> data.stateProvince
            "postal-code" -> data.postalCode
            "country" -> countryCode(data.country) ?: data.country
            "country-name" -> countryName(data.country)
            else -> null
        }?.takeIf(String::isNotBlank)
    }

    fun document(raw: String?, data: DocumentData): String? {
        val specific = when (canonical(raw)) {
            "username" -> data.username
            "identity-number", "document-number" -> data.documentNumber
            "passport-number" -> data.passportNumber.ifBlank { data.documentNumber }
            "license-number" -> data.licenseNumber.ifBlank { data.documentNumber }
            "ssn", "social-security-number" -> data.ssn.ifBlank { data.documentNumber }
            "honorific-prefix" -> data.title
            else -> null
        }
        return specific?.takeIf(String::isNotBlank) ?: address(raw, BillingAddressData(
            fullName = data.fullName, firstName = data.firstName, middleName = data.middleName, lastName = data.lastName,
            company = data.company, streetAddress = data.address1, apartment = data.address2, addressLine3 = data.address3,
            district = data.district, city = data.city, stateProvince = data.stateProvince, country = data.country,
            postalCode = data.postalCode, email = data.email, phone = data.phone, homeEmail = data.homeEmail,
            homePhone = data.homePhone, workEmail = data.workEmail, workPhone = data.workPhone))
    }

    fun card(raw: String?, data: BankCardData): String? {
        val hint = canonical(raw)
        val value = when (hint) {
            "cc-name" -> data.cardholderName
            "cc-given-name" -> data.cardholderFirstName
            "cc-additional-name" -> data.cardholderMiddleName
            "cc-family-name" -> data.cardholderLastName
            "cc-number" -> data.cardNumber
            "cc-csc" -> data.cvv
            "cc-exp-month" -> data.expiryMonth
            "cc-exp-year" -> data.expiryYear
            "cc-exp" -> listOf(data.expiryMonth, data.expiryYear).filter(String::isNotBlank).joinToString("/")
            else -> null
        }
        if (hint.startsWith("cc-")) return value?.takeIf(String::isNotBlank)
        val a = CardWalletDataCodec.parseBillingAddress(data.billingAddress)
        return address(raw, BillingAddressData(streetAddress = a.streetAddress, apartment = a.apartment,
            addressLine3 = a.addressLine3, district = a.district, city = a.city, stateProvince = a.stateProvince,
            country = a.country, postalCode = a.postalCode))
    }
}

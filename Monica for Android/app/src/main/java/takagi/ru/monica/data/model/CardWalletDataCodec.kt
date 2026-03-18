package takagi.ru.monica.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.CustomFieldDraft

object CardWalletDataCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun parseBankCardData(raw: String): BankCardData? {
        return runCatching { json.decodeFromString<BankCardData>(raw) }
            .getOrElse {
                parseLegacyBankCardData(raw)
            }
    }

    fun parseDocumentData(raw: String): DocumentData? {
        return runCatching { json.decodeFromString<DocumentData>(raw) }
            .getOrElse {
                parseLegacyDocumentData(raw)
            }
    }

    fun encodeBankCardData(data: BankCardData): String = json.encodeToString(BankCardData.serializer(), data)

    fun encodeDocumentData(data: DocumentData): String = json.encodeToString(DocumentData.serializer(), data)

    fun parseBillingAddress(raw: String): BillingAddress {
        if (raw.isBlank()) return BillingAddress()
        return runCatching { json.decodeFromString<BillingAddress>(raw) }.getOrDefault(BillingAddress())
    }

    fun encodeBillingAddress(address: BillingAddress): String {
        return if (address.isEmpty()) "" else json.encodeToString(BillingAddress.serializer(), address)
    }

    fun customFieldsToDrafts(fields: List<SecureCustomField>): List<CustomFieldDraft> {
        return fields.map { field ->
            CustomFieldDraft(
                id = CustomFieldDraft.nextTempId(),
                title = field.label,
                value = field.value,
                isProtected = field.isProtected()
            )
        }
    }

    fun draftsToCustomFields(drafts: List<CustomFieldDraft>): List<SecureCustomField> {
        return drafts
            .filter { it.isValid() }
            .map { draft ->
                SecureCustomField(
                    label = draft.title.trim(),
                    value = draft.value,
                    type = if (draft.isProtected) {
                        SecureCustomFieldType.HIDDEN
                    } else {
                        SecureCustomFieldType.TEXT
                    }
                )
            }
    }

    fun customFieldsToDisplay(fields: List<SecureCustomField>): List<CustomField> {
        return fields.mapIndexed { index, field ->
            CustomField(
                id = -(index + 1L),
                entryId = 0L,
                title = field.label,
                value = field.value,
                isProtected = field.isProtected(),
                sortOrder = index
            )
        }
    }

    private fun parseLegacyBankCardData(raw: String): BankCardData? {
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        return BankCardData(
            cardNumber = obj.string("cardNumber", "number"),
            cardholderName = obj.string("cardholderName"),
            expiryMonth = obj.string("expiryMonth", "expMonth"),
            expiryYear = obj.string("expiryYear", "expYear"),
            cvv = obj.string("cvv", "code"),
            bankName = obj.string("bankName"),
            billingAddress = obj.string("billingAddress"),
            brand = obj.string("brand"),
            validFromMonth = obj.string("validFromMonth", "fromMonth"),
            validFromYear = obj.string("validFromYear", "fromYear"),
            customFields = parseEmbeddedCustomFields(obj)
        )
    }

    private fun parseLegacyDocumentData(raw: String): DocumentData? {
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val legacy = runCatching { json.decodeFromString<LegacyDocumentItemData>(raw) }.getOrNull()
        val firstName = legacy?.firstName ?: obj.string("firstName")
        val middleName = legacy?.middleName ?: obj.string("middleName")
        val lastName = legacy?.lastName ?: obj.string("lastName")
        val fullName = listOf(firstName, middleName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { obj.string("fullName", "name") }
        val documentType = parseDocumentType(
            legacy?.documentType
                ?: obj.string("documentType", "type")
        )

        return DocumentData(
            documentType = documentType,
            documentNumber = legacy?.documentNumber ?: obj.string("documentNumber", "number"),
            fullName = fullName,
            issuedDate = legacy?.issueDate ?: obj.string("issuedDate", "issueDate"),
            expiryDate = legacy?.expiryDate ?: obj.string("expiryDate"),
            issuedBy = legacy?.issuingAuthority ?: obj.string("issuedBy", "issuingAuthority"),
            nationality = obj.string("nationality"),
            additionalInfo = legacy?.additionalInfo ?: obj.string("additionalInfo"),
            title = legacy?.title ?: obj.string("title"),
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            address1 = legacy?.address1 ?: obj.string("address1"),
            address2 = legacy?.address2 ?: obj.string("address2"),
            address3 = obj.string("address3"),
            city = legacy?.city ?: obj.string("city"),
            stateProvince = legacy?.state ?: obj.string("stateProvince", "state"),
            postalCode = legacy?.postalCode ?: obj.string("postalCode"),
            country = legacy?.country ?: obj.string("country"),
            company = legacy?.company ?: obj.string("company"),
            email = legacy?.email ?: obj.string("email"),
            phone = legacy?.phone ?: obj.string("phone"),
            ssn = legacy?.ssn ?: obj.string("ssn"),
            username = legacy?.username ?: obj.string("username"),
            passportNumber = legacy?.passportNumber ?: obj.string("passportNumber"),
            licenseNumber = legacy?.licenseNumber ?: obj.string("licenseNumber", "driverLicense"),
            customFields = parseEmbeddedCustomFields(obj)
        )
    }

    private fun parseEmbeddedCustomFields(obj: JsonObject): List<SecureCustomField> {
        return runCatching {
            json.decodeFromString<List<SecureCustomField>>(obj["customFields"].toString())
        }.getOrElse {
            emptyList()
        }
    }

    private fun parseDocumentType(raw: String?): DocumentType {
        return when (raw?.trim()?.lowercase()) {
            "passport" -> DocumentType.PASSPORT
            "driver_license", "driverlicense", "license" -> DocumentType.DRIVER_LICENSE
            "social_security", "socialsecurity", "ssn" -> DocumentType.SOCIAL_SECURITY
            "other" -> DocumentType.OTHER
            else -> DocumentType.ID_CARD
        }
    }

    private fun JsonObject.string(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key -> this[key].primitiveString() }.orEmpty()
    }

    private fun kotlinx.serialization.json.JsonElement?.primitiveString(): String? {
        return (this as? JsonPrimitive)?.contentOrNull
    }

    @Serializable
    private data class LegacyDocumentItemData(
        val documentType: String = "",
        val documentNumber: String = "",
        val issueDate: String = "",
        val expiryDate: String = "",
        val issuingAuthority: String = "",
        val title: String = "",
        val firstName: String = "",
        val middleName: String = "",
        val lastName: String = "",
        val address1: String = "",
        val address2: String = "",
        val city: String = "",
        val state: String = "",
        val postalCode: String = "",
        val country: String = "",
        val company: String = "",
        val email: String = "",
        val phone: String = "",
        val additionalInfo: String = "",
        val ssn: String = "",
        val username: String = "",
        val passportNumber: String = "",
        val licenseNumber: String = ""
    )
}

fun SecureCustomField.toDraft(): CustomFieldDraft {
    return CustomFieldDraft(
        id = CustomFieldDraft.nextTempId(),
        title = label,
        value = value,
        isProtected = isProtected()
    )
}

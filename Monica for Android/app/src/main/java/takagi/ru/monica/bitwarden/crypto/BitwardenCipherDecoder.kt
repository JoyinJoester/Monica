package takagi.ru.monica.bitwarden.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey

/**
 * Bitwarden Cipher 解码器
 * 
 * 负责解密 Bitwarden Cipher 对象的各个字段
 * 
 * Cipher 类型:
 * - 1: Login (用户名/密码)
 * - 2: SecureNote (安全笔记)
 * - 3: Card (信用卡)
 * - 4: Identity (身份信息)
 */
class BitwardenCipherDecoder(
    private val key: SymmetricCryptoKey
) {
    
    companion object {
        const val CIPHER_TYPE_LOGIN = 1
        const val CIPHER_TYPE_SECURE_NOTE = 2
        const val CIPHER_TYPE_CARD = 3
        const val CIPHER_TYPE_IDENTITY = 4
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * 解密字符串字段
     * 如果字段为 null 或空，返回 null
     */
    fun decryptString(cipherString: String?): String? {
        if (cipherString.isNullOrBlank()) return null
        return try {
            BitwardenCrypto.decryptToString(cipherString, key)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 解密字符串字段，如果失败返回默认值
     */
    fun decryptStringOrDefault(cipherString: String?, default: String = ""): String {
        return decryptString(cipherString) ?: default
    }
    
    /**
     * 解密 Login Cipher
     */
    fun decryptLogin(loginData: CipherLoginData): DecryptedLogin {
        return DecryptedLogin(
            username = decryptString(loginData.username),
            password = decryptString(loginData.password),
            totp = decryptString(loginData.totp),
            uris = loginData.uris?.mapNotNull { uri ->
                decryptString(uri.uri)?.let { decryptedUri ->
                    DecryptedUri(
                        uri = decryptedUri,
                        match = uri.match
                    )
                }
            } ?: emptyList()
        )
    }
    
    /**
     * 解密 Card Cipher
     */
    fun decryptCard(cardData: CipherCardData): DecryptedCard {
        return DecryptedCard(
            cardholderName = decryptString(cardData.cardholderName),
            brand = decryptString(cardData.brand),
            number = decryptString(cardData.number),
            expMonth = decryptString(cardData.expMonth),
            expYear = decryptString(cardData.expYear),
            code = decryptString(cardData.code)
        )
    }
    
    /**
     * 解密 Identity Cipher
     */
    fun decryptIdentity(identityData: CipherIdentityData): DecryptedIdentity {
        return DecryptedIdentity(
            title = decryptString(identityData.title),
            firstName = decryptString(identityData.firstName),
            middleName = decryptString(identityData.middleName),
            lastName = decryptString(identityData.lastName),
            address1 = decryptString(identityData.address1),
            address2 = decryptString(identityData.address2),
            address3 = decryptString(identityData.address3),
            city = decryptString(identityData.city),
            state = decryptString(identityData.state),
            postalCode = decryptString(identityData.postalCode),
            country = decryptString(identityData.country),
            company = decryptString(identityData.company),
            email = decryptString(identityData.email),
            phone = decryptString(identityData.phone),
            ssn = decryptString(identityData.ssn),
            username = decryptString(identityData.username),
            passportNumber = decryptString(identityData.passportNumber),
            licenseNumber = decryptString(identityData.licenseNumber)
        )
    }
    
    /**
     * 解密 Secure Note Cipher
     */
    fun decryptSecureNote(noteData: CipherSecureNoteData): DecryptedSecureNote {
        return DecryptedSecureNote(
            type = noteData.type
        )
    }
    
    /**
     * 解密自定义字段
     */
    fun decryptFields(fields: List<CipherFieldData>?): List<DecryptedField> {
        return fields?.mapNotNull { field ->
            val name = decryptString(field.name)
            val value = decryptString(field.value)
            if (name != null) {
                DecryptedField(
                    name = name,
                    value = value ?: "",
                    type = field.type,
                    linkedId = field.linkedId
                )
            } else null
        } ?: emptyList()
    }
    
    /**
     * 解密完整的 Cipher
     */
    fun decryptCipher(cipher: CipherResponse): DecryptedCipher {
        return DecryptedCipher(
            id = cipher.id,
            organizationId = cipher.organizationId,
            folderId = cipher.folderId,
            type = cipher.type,
            name = decryptStringOrDefault(cipher.name),
            notes = decryptString(cipher.notes),
            login = cipher.login?.let { decryptLogin(it) },
            card = cipher.card?.let { decryptCard(it) },
            identity = cipher.identity?.let { decryptIdentity(it) },
            secureNote = cipher.secureNote?.let { decryptSecureNote(it) },
            fields = decryptFields(cipher.fields),
            favorite = cipher.favorite,
            reprompt = cipher.reprompt,
            revisionDate = cipher.revisionDate,
            creationDate = cipher.creationDate,
            deletedDate = cipher.deletedDate
        )
    }
}

// ========== 加密数据模型 (服务器返回的格式) ==========

@Serializable
data class CipherResponse(
    val id: String,
    val organizationId: String? = null,
    val folderId: String? = null,
    val type: Int,
    val name: String?,
    val notes: String? = null,
    val login: CipherLoginData? = null,
    val card: CipherCardData? = null,
    val identity: CipherIdentityData? = null,
    val secureNote: CipherSecureNoteData? = null,
    val fields: List<CipherFieldData>? = null,
    val favorite: Boolean = false,
    val reprompt: Int = 0,
    val revisionDate: String,
    val creationDate: String? = null,
    val deletedDate: String? = null
)

@Serializable
data class CipherLoginData(
    val username: String? = null,
    val password: String? = null,
    val totp: String? = null,
    val uris: List<CipherUriData>? = null
)

@Serializable
data class CipherUriData(
    val uri: String?,
    val match: Int? = null
)

@Serializable
data class CipherCardData(
    val cardholderName: String? = null,
    val brand: String? = null,
    val number: String? = null,
    val expMonth: String? = null,
    val expYear: String? = null,
    val code: String? = null
)

@Serializable
data class CipherIdentityData(
    val title: String? = null,
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val address1: String? = null,
    val address2: String? = null,
    val address3: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val company: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val ssn: String? = null,
    val username: String? = null,
    val passportNumber: String? = null,
    val licenseNumber: String? = null
)

@Serializable
data class CipherSecureNoteData(
    val type: Int = 0
)

@Serializable
data class CipherFieldData(
    val name: String?,
    val value: String?,
    val type: Int = 0,       // 0=Text, 1=Hidden, 2=Boolean, 3=Linked
    val linkedId: Int? = null
)

// ========== 解密后的数据模型 ==========

data class DecryptedCipher(
    val id: String,
    val organizationId: String?,
    val folderId: String?,
    val type: Int,
    val name: String,
    val notes: String?,
    val login: DecryptedLogin?,
    val card: DecryptedCard?,
    val identity: DecryptedIdentity?,
    val secureNote: DecryptedSecureNote?,
    val fields: List<DecryptedField>,
    val favorite: Boolean,
    val reprompt: Int,
    val revisionDate: String,
    val creationDate: String?,
    val deletedDate: String?
) {
    fun isLogin() = type == BitwardenCipherDecoder.CIPHER_TYPE_LOGIN
    fun isSecureNote() = type == BitwardenCipherDecoder.CIPHER_TYPE_SECURE_NOTE
    fun isCard() = type == BitwardenCipherDecoder.CIPHER_TYPE_CARD
    fun isIdentity() = type == BitwardenCipherDecoder.CIPHER_TYPE_IDENTITY
    fun isDeleted() = deletedDate != null
}

data class DecryptedLogin(
    val username: String?,
    val password: String?,
    val totp: String?,
    val uris: List<DecryptedUri>
) {
    /**
     * 获取主 URI (用于匹配网站)
     */
    fun getPrimaryUri(): String? = uris.firstOrNull()?.uri
}

data class DecryptedUri(
    val uri: String,
    val match: Int?  // 0=Domain, 1=Host, 2=StartsWith, 3=Exact, 4=Regex, 5=Never
)

data class DecryptedCard(
    val cardholderName: String?,
    val brand: String?,
    val number: String?,
    val expMonth: String?,
    val expYear: String?,
    val code: String?
) {
    /**
     * 获取格式化的过期日期 (MM/YY)
     */
    fun getFormattedExpiry(): String? {
        val month = expMonth?.padStart(2, '0') ?: return null
        val year = expYear?.takeLast(2) ?: return null
        return "$month/$year"
    }
    
    /**
     * 获取遮蔽的卡号 (只显示后 4 位)
     */
    fun getMaskedNumber(): String? {
        val num = number ?: return null
        if (num.length < 4) return num
        return "**** **** **** ${num.takeLast(4)}"
    }
}

data class DecryptedIdentity(
    val title: String?,
    val firstName: String?,
    val middleName: String?,
    val lastName: String?,
    val address1: String?,
    val address2: String?,
    val address3: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String?,
    val company: String?,
    val email: String?,
    val phone: String?,
    val ssn: String?,
    val username: String?,
    val passportNumber: String?,
    val licenseNumber: String?
) {
    /**
     * 获取完整姓名
     */
    fun getFullName(): String {
        return listOfNotNull(title, firstName, middleName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
    
    /**
     * 获取格式化地址
     */
    fun getFormattedAddress(): String {
        return listOfNotNull(address1, address2, address3, city, state, postalCode, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }
}

data class DecryptedSecureNote(
    val type: Int
)

data class DecryptedField(
    val name: String,
    val value: String,
    val type: Int,      // 0=Text, 1=Hidden, 2=Boolean, 3=Linked
    val linkedId: Int?
) {
    fun isHidden() = type == 1
    fun isBoolean() = type == 2
    fun isLinked() = type == 3
}

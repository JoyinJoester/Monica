@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package takagi.ru.monica.bitwarden.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import retrofit2.Response
import retrofit2.http.*

/**
 * Bitwarden Identity API - 认证和令牌管理
 * 
 * 端点: https://identity.bitwarden.com (官方)
 *       或自托管服务的 /identity 路径
 */
interface BitwardenIdentityApi {
    
    /**
     * 获取预登录信息 (KDF 类型和参数)
     */
    @POST("accounts/prelogin")
    suspend fun preLogin(
        @Body request: PreLoginRequest
    ): Response<PreLoginResponse>
    
    /**
     * 登录获取访问令牌
     * 
     * 使用 Resource Owner Password Grant
     * 参考 keyguard: 需要 Auth-Email header (Base64 编码的邮箱)
     * 
     * 完全模拟 Keyguard 的 Linux Desktop 模式
     * deviceType: 8 = Linux
     * client_id: desktop
     */
    @FormUrlEncoded
    @POST("connect/token")
    suspend fun login(
        @Header("Auth-Email") authEmail: String,      // URL-safe Base64 编码的邮箱 (重要!)
        @Header("device-type") deviceTypeHeader: String = "8",  // 8 = Linux (与 Keyguard 一致)
        @Header("cache-control") cacheControl: String = "no-store",
        @Header("Bitwarden-Client-Name") clientName: String = "desktop",
        @Header("Bitwarden-Client-Version") clientVersion: String = "2025.9.1",  // 与 Keyguard 一致
        @Field("grant_type") grantType: String = "password",
        @Field("username") username: String,
        @Field("password") passwordHash: String,  // 标准 Base64 编码的 Master Password Hash
        @Field("scope") scope: String = "api offline_access",
        @Field("client_id") clientId: String = "desktop",  // 使用 desktop (与 Keyguard 一致)
        @Field("deviceIdentifier") deviceIdentifier: String,
        @Field("deviceType") deviceType: String = "8",  // 8 = Linux (与 Keyguard 一致)
        @Field("deviceName") deviceName: String = "linux"  // 与 Keyguard 一致
    ): Response<TokenResponse>
    
    /**
     * 刷新访问令牌
     */
    @FormUrlEncoded
    @POST("connect/token")
    suspend fun refreshToken(
        @Header("Bitwarden-Client-Name") clientName: String = "desktop",
        @Header("Bitwarden-Client-Version") clientVersion: String = "2025.9.1",
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String = "desktop"
    ): Response<TokenResponse>
    
    /**
     * 两步验证登录
     */
    @FormUrlEncoded
    @POST("connect/token")
    suspend fun loginTwoFactor(
        @Header("Auth-Email") authEmail: String,
        @Header("device-type") deviceTypeHeader: String = "8",  // 8 = Linux (与 Keyguard 一致)
        @Header("cache-control") cacheControl: String = "no-store",
        @Header("Bitwarden-Client-Name") clientName: String = "desktop",
        @Header("Bitwarden-Client-Version") clientVersion: String = "2025.9.1",  // 与 Keyguard 一致
        @Field("grant_type") grantType: String = "password",
        @Field("username") username: String,
        @Field("password") passwordHash: String,
        @Field("scope") scope: String = "api offline_access",
        @Field("client_id") clientId: String = "desktop",  // 与 Keyguard 一致
        @Field("deviceIdentifier") deviceIdentifier: String,
        @Field("deviceType") deviceType: String = "8",  // 8 = Linux (与 Keyguard 一致)
        @Field("deviceName") deviceName: String = "linux",  // 与 Keyguard 一致
        @Field("twoFactorToken") twoFactorToken: String,
        @Field("twoFactorProvider") twoFactorProvider: Int,
        @Field("twoFactorRemember") twoFactorRemember: Int = 0
    ): Response<TokenResponse>

    /**
     * 新设备验证（Email New Device OTP）
     */
    @FormUrlEncoded
    @POST("connect/token")
    suspend fun loginNewDeviceOtp(
        @Header("Auth-Email") authEmail: String,
        @Header("device-type") deviceTypeHeader: String = "8",  // 8 = Linux (与 Keyguard 一致)
        @Header("cache-control") cacheControl: String = "no-store",
        @Header("Bitwarden-Client-Name") clientName: String = "desktop",
        @Header("Bitwarden-Client-Version") clientVersion: String = "2025.9.1",
        @Field("grant_type") grantType: String = "password",
        @Field("username") username: String,
        @Field("password") passwordHash: String,
        @Field("scope") scope: String = "api offline_access",
        @Field("client_id") clientId: String = "desktop",
        @Field("deviceIdentifier") deviceIdentifier: String,
        @Field("deviceType") deviceType: String = "8",
        @Field("deviceName") deviceName: String = "linux",
        @Field("newDeviceOtp") newDeviceOtp: String
    ): Response<TokenResponse>
}

/**
 * Bitwarden Vault API - 密码库数据操作
 * 
 * 端点: https://api.bitwarden.com (官方)
 *       或自托管服务的 /api 路径
 */
interface BitwardenVaultApi {
    
    /**
     * 同步全部数据
     * 
     * 返回所有 ciphers, folders, collections, policies 等
     */
    @GET("sync")
    suspend fun sync(
        @Header("Authorization") authorization: String,
        @Query("excludeDomains") excludeDomains: Boolean = true
    ): Response<SyncResponse>
    
    /**
     * 获取单个 Cipher
     */
    @GET("ciphers/{id}")
    suspend fun getCipher(
        @Header("Authorization") authorization: String,
        @Path("id") cipherId: String
    ): Response<CipherApiResponse>
    
    /**
     * 创建 Cipher
     */
    @POST("ciphers")
    suspend fun createCipher(
        @Header("Authorization") authorization: String,
        @Body cipher: CipherCreateRequest
    ): Response<CipherApiResponse>
    
    /**
     * 更新 Cipher
     */
    @PUT("ciphers/{id}")
    suspend fun updateCipher(
        @Header("Authorization") authorization: String,
        @Path("id") cipherId: String,
        @Body cipher: CipherUpdateRequest
    ): Response<CipherApiResponse>
    
    /**
     * 删除 Cipher (软删除到回收站)
     */
    @DELETE("ciphers/{id}")
    suspend fun deleteCipher(
        @Header("Authorization") authorization: String,
        @Path("id") cipherId: String
    ): Response<Unit>
    
    /**
     * 永久删除 Cipher
     */
    @DELETE("ciphers/{id}/delete")
    suspend fun permanentDeleteCipher(
        @Header("Authorization") authorization: String,
        @Path("id") cipherId: String
    ): Response<Unit>
    
    /**
     * 恢复已删除的 Cipher
     */
    @PUT("ciphers/{id}/restore")
    suspend fun restoreCipher(
        @Header("Authorization") authorization: String,
        @Path("id") cipherId: String
    ): Response<CipherApiResponse>
    
    // ========== Folder 操作 ==========
    
    /**
     * 获取所有文件夹
     */
    @GET("folders")
    suspend fun getFolders(
        @Header("Authorization") authorization: String
    ): Response<FoldersResponse>
    
    /**
     * 创建文件夹
     */
    @POST("folders")
    suspend fun createFolder(
        @Header("Authorization") authorization: String,
        @Body folder: FolderCreateRequest
    ): Response<FolderApiResponse>
    
    /**
     * 更新文件夹
     */
    @PUT("folders/{id}")
    suspend fun updateFolder(
        @Header("Authorization") authorization: String,
        @Path("id") folderId: String,
        @Body folder: FolderUpdateRequest
    ): Response<FolderApiResponse>
    
    /**
     * 删除文件夹
     */
    @DELETE("folders/{id}")
    suspend fun deleteFolder(
        @Header("Authorization") authorization: String,
        @Path("id") folderId: String
    ): Response<Unit>

    // ========== Send 操作 ==========

    /**
     * 创建 Send（目前主要用于文本 Send）
     */
    @POST("sends")
    suspend fun createSend(
        @Header("Authorization") authorization: String,
        @Body send: SendCreateRequest
    ): Response<SendApiResponse>

    /**
     * 获取单个 Send
     */
    @GET("sends/{id}")
    suspend fun getSend(
        @Header("Authorization") authorization: String,
        @Path("id") sendId: String
    ): Response<SendApiResponse>

    /**
     * 更新 Send
     */
    @PUT("sends/{id}")
    suspend fun updateSend(
        @Header("Authorization") authorization: String,
        @Path("id") sendId: String,
        @Body send: SendCreateRequest
    ): Response<SendApiResponse>

    /**
     * 删除 Send
     */
    @DELETE("sends/{id}")
    suspend fun deleteSend(
        @Header("Authorization") authorization: String,
        @Path("id") sendId: String
    ): Response<Unit>
}

// ========== 请求/响应数据模型 ==========

@Serializable
data class PreLoginRequest(
    val email: String
)

/**
 * PreLogin 响应
 * 
 * 使用 @JsonNames 兼容服务器返回的不同大小写
 * 使用默认值防止服务器未返回某些字段时崩溃
 */
@Serializable
data class PreLoginResponse(
    @JsonNames("kdf")
    @SerialName("Kdf")
    val kdf: Int = 0,                    // 0=PBKDF2, 1=Argon2id
    @JsonNames("kdfIterations")
    @SerialName("KdfIterations")
    val kdfIterations: Int = 600000,     // PBKDF2 默认迭代次数
    @JsonNames("kdfMemory")
    @SerialName("KdfMemory")
    val kdfMemory: Int? = null,          // Argon2 专用
    @JsonNames("kdfParallelism")
    @SerialName("KdfParallelism")
    val kdfParallelism: Int? = null      // Argon2 专用
)

/**
 * Token 响应
 * 
 * 登录成功后返回的令牌和密钥信息
 */
@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String = "",
    @SerialName("expires_in")
    val expiresIn: Int = 3600,       // 秒数
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @JsonNames("key")
    @SerialName("Key")
    val key: String? = null,          // Protected Symmetric Key (加密)
    @JsonNames("privateKey")
    @SerialName("PrivateKey")
    val privateKey: String? = null,   // 私钥 (加密)
    @JsonNames("kdf")
    @SerialName("Kdf")
    val kdf: Int? = null,
    @JsonNames("kdfIterations")
    @SerialName("KdfIterations")
    val kdfIterations: Int? = null,
    @JsonNames("kdfMemory")
    @SerialName("KdfMemory")
    val kdfMemory: Int? = null,
    @JsonNames("kdfParallelism")
    @SerialName("KdfParallelism")
    val kdfParallelism: Int? = null,
    @JsonNames("twoFactorToken")
    @SerialName("TwoFactorToken")
    val twoFactorToken: String? = null,
    // 两步验证相关
    @SerialName("error")
    val error: String? = null,
    @SerialName("error_description")
    val errorDescription: String? = null,
    @JsonNames("errorModel")
    @SerialName("ErrorModel")
    val errorModel: ErrorModel? = null,
    @JsonNames("twoFactorProviders")
    @SerialName("TwoFactorProviders")
    val twoFactorProviders: List<Int>? = null,
    @JsonNames("twoFactorProviders2")
    @SerialName("TwoFactorProviders2")
    val twoFactorProviders2: Map<String, JsonElement>? = null,
    @JsonNames("resetMasterPassword")
    @SerialName("ResetMasterPassword")
    val resetMasterPassword: Boolean? = null,
    @SerialName("scope")
    val scope: String? = null
)

@Serializable
data class ErrorModel(
    @JsonNames("message")
    @SerialName("Message")
    val message: String? = null
)

@Serializable
data class SyncResponse(
    @JsonNames("profile")
    @SerialName("Profile")
    val profile: ProfileResponse,
    @JsonNames("folders")
    @SerialName("Folders")
    val folders: List<FolderApiResponse> = emptyList(),
    @JsonNames("ciphers")
    @SerialName("Ciphers")
    val ciphers: List<CipherApiResponse> = emptyList(),
    @JsonNames("collections")
    @SerialName("Collections")
    val collections: List<CollectionResponse>? = null,
    @JsonNames("policies")
    @SerialName("Policies")
    val policies: List<PolicyResponse>? = null,
    @JsonNames("sends")
    @SerialName("Sends")
    val sends: List<SendApiResponse>? = null
)

@Serializable
data class ProfileResponse(
    @JsonNames("id")
    @SerialName("Id")
    val id: String = "",
    @JsonNames("name")
    @SerialName("Name")
    val name: String? = null,
    @JsonNames("email")
    @SerialName("Email")
    val email: String = "",
    @JsonNames("premium")
    @SerialName("Premium")
    val premium: Boolean = false,
    @JsonNames("key")
    @SerialName("Key")
    val key: String? = null,
    @JsonNames("privateKey")
    @SerialName("PrivateKey")
    val privateKey: String? = null,
    @JsonNames("securityStamp")
    @SerialName("SecurityStamp")
    val securityStamp: String? = null
)

@Serializable
data class FolderApiResponse(
    @JsonNames("id")
    @SerialName("Id")
    val id: String = "",
    @JsonNames("name")
    @SerialName("Name")
    val name: String? = null,       // 加密的名称
    @JsonNames("revisionDate")
    @SerialName("RevisionDate")
    val revisionDate: String = ""
)

@Serializable
data class FoldersResponse(
    @JsonNames("data")
    @SerialName("Data")
    val data: List<FolderApiResponse> = emptyList()
)

@Serializable
data class CipherApiResponse(
    @JsonNames("id")
    @SerialName("Id")
    val id: String = "",
    @JsonNames("organizationId")
    @SerialName("OrganizationId")
    val organizationId: String? = null,
    @JsonNames("folderId")
    @SerialName("FolderId")
    val folderId: String? = null,
    @JsonNames("type")
    @SerialName("Type")
    val type: Int = 1,
    @JsonNames("name")
    @SerialName("Name")
    val name: String? = null,
    @JsonNames("notes")
    @SerialName("Notes")
    val notes: String? = null,
    @JsonNames("login")
    @SerialName("Login")
    val login: CipherLoginApiData? = null,
    @JsonNames("card")
    @SerialName("Card")
    val card: CipherCardApiData? = null,
    @JsonNames("identity")
    @SerialName("Identity")
    val identity: CipherIdentityApiData? = null,
    @JsonNames("secureNote")
    @SerialName("SecureNote")
    val secureNote: CipherSecureNoteApiData? = null,
    @JsonNames("fields")
    @SerialName("Fields")
    val fields: List<CipherFieldApiData>? = null,
    @JsonNames("favorite")
    @SerialName("Favorite")
    val favorite: Boolean = false,
    @JsonNames("reprompt")
    @SerialName("Reprompt")
    val reprompt: Int = 0,
    @JsonNames("revisionDate")
    @SerialName("RevisionDate")
    val revisionDate: String = "",
    @JsonNames("creationDate")
    @SerialName("CreationDate")
    val creationDate: String? = null,
    @JsonNames("deletedDate")
    @SerialName("DeletedDate")
    val deletedDate: String? = null
)

@Serializable
data class CipherLoginApiData(
    @JsonNames("username")
    @SerialName("Username")
    val username: String? = null,
    @JsonNames("password")
    @SerialName("Password")
    val password: String? = null,
    @JsonNames("totp")
    @SerialName("Totp")
    val totp: String? = null,
    @JsonNames("uris")
    @SerialName("Uris")
    val uris: List<CipherUriApiData>? = null
)

@Serializable
data class CipherUriApiData(
    @JsonNames("uri")
    @SerialName("Uri")
    val uri: String? = null,
    @JsonNames("match")
    @SerialName("Match")
    val match: Int? = null
)

@Serializable
data class CipherCardApiData(
    @JsonNames("cardholderName")
    @SerialName("CardholderName")
    val cardholderName: String? = null,
    @JsonNames("brand")
    @SerialName("Brand")
    val brand: String? = null,
    @JsonNames("number")
    @SerialName("Number")
    val number: String? = null,
    @JsonNames("expMonth")
    @SerialName("ExpMonth")
    val expMonth: String? = null,
    @JsonNames("expYear")
    @SerialName("ExpYear")
    val expYear: String? = null,
    @JsonNames("code")
    @SerialName("Code")
    val code: String? = null
)

@Serializable
data class CipherIdentityApiData(
    @JsonNames("title")
    @SerialName("Title")
    val title: String? = null,
    @JsonNames("firstName")
    @SerialName("FirstName")
    val firstName: String? = null,
    @JsonNames("middleName")
    @SerialName("MiddleName")
    val middleName: String? = null,
    @JsonNames("lastName")
    @SerialName("LastName")
    val lastName: String? = null,
    @JsonNames("address1")
    @SerialName("Address1")
    val address1: String? = null,
    @JsonNames("address2")
    @SerialName("Address2")
    val address2: String? = null,
    @JsonNames("address3")
    @SerialName("Address3")
    val address3: String? = null,
    @JsonNames("city")
    @SerialName("City")
    val city: String? = null,
    @JsonNames("state")
    @SerialName("State")
    val state: String? = null,
    @JsonNames("postalCode")
    @SerialName("PostalCode")
    val postalCode: String? = null,
    @JsonNames("country")
    @SerialName("Country")
    val country: String? = null,
    @JsonNames("company")
    @SerialName("Company")
    val company: String? = null,
    @JsonNames("email")
    @SerialName("Email")
    val email: String? = null,
    @JsonNames("phone")
    @SerialName("Phone")
    val phone: String? = null,
    @JsonNames("ssn")
    @SerialName("SSN")
    val ssn: String? = null,
    @JsonNames("username")
    @SerialName("Username")
    val username: String? = null,
    @JsonNames("passportNumber")
    @SerialName("PassportNumber")
    val passportNumber: String? = null,
    @JsonNames("licenseNumber")
    @SerialName("LicenseNumber")
    val licenseNumber: String? = null
)

@Serializable
data class CipherSecureNoteApiData(
    @JsonNames("type")
    @SerialName("Type")
    val type: Int = 0
)

@Serializable
data class CipherFieldApiData(
    @JsonNames("name")
    @SerialName("Name")
    val name: String? = null,
    @JsonNames("value")
    @SerialName("Value")
    val value: String? = null,
    @JsonNames("type")
    @SerialName("Type")
    val type: Int = 0,
    @JsonNames("linkedId")
    @SerialName("LinkedId")
    val linkedId: Int? = null
)

@Serializable
data class CollectionResponse(
    @JsonNames("id")
    @SerialName("Id")
    val id: String = "",
    @JsonNames("name")
    @SerialName("Name")
    val name: String? = null
)

@Serializable
data class PolicyResponse(
    @JsonNames("id")
    @SerialName("Id")
    val id: String = "",
    @JsonNames("type")
    @SerialName("Type")
    val type: Int = 0,
    @JsonNames("enabled")
    @SerialName("Enabled")
    val enabled: Boolean = false
)

@Serializable
data class SendApiResponse(
    @JsonNames("id")
    @SerialName("Id")
    val id: String = "",
    @JsonNames("accessId")
    @SerialName("AccessId")
    val accessId: String = "",
    @JsonNames("key")
    @SerialName("Key")
    val key: String = "",
    @JsonNames("type")
    @SerialName("Type")
    val type: Int = 0, // 0=Text, 1=File
    @JsonNames("name")
    @SerialName("Name")
    val name: String? = null,
    @JsonNames("notes")
    @SerialName("Notes")
    val notes: String? = null,
    @JsonNames("file")
    @SerialName("File")
    val file: SendFileApiData? = null,
    @JsonNames("text")
    @SerialName("Text")
    val text: SendTextApiData? = null,
    @JsonNames("accessCount")
    @SerialName("AccessCount")
    val accessCount: Int = 0,
    @JsonNames("maxAccessCount")
    @SerialName("MaxAccessCount")
    val maxAccessCount: Int? = null,
    @JsonNames("revisionDate")
    @SerialName("RevisionDate")
    val revisionDate: String = "",
    @JsonNames("expirationDate")
    @SerialName("ExpirationDate")
    val expirationDate: String? = null,
    @JsonNames("deletionDate")
    @SerialName("DeletionDate")
    val deletionDate: String? = null,
    @JsonNames("password")
    @SerialName("Password")
    val password: String? = null,
    @JsonNames("disabled")
    @SerialName("Disabled")
    val disabled: Boolean = false,
    @JsonNames("hideEmail")
    @SerialName("HideEmail")
    val hideEmail: Boolean? = null
)

@Serializable
data class SendTextApiData(
    @JsonNames("text")
    @SerialName("Text")
    val text: String? = null,
    @JsonNames("hidden")
    @SerialName("Hidden")
    val hidden: Boolean? = null
)

@Serializable
data class SendFileApiData(
    @JsonNames("id")
    @SerialName("Id")
    val id: String? = null,
    @JsonNames("fileName")
    @SerialName("FileName")
    val fileName: String? = null,
    @JsonNames("size")
    @SerialName("Size")
    val size: String? = null,
    @JsonNames("sizeName")
    @SerialName("SizeName")
    val sizeName: String? = null,
    @JsonNames("key")
    @SerialName("Key")
    val key: String? = null
)

// ========== 创建/更新请求 ==========

@Serializable
data class CipherCreateRequest(
    @SerialName("Type")
    val type: Int,
    @SerialName("FolderId")
    val folderId: String? = null,
    @SerialName("Name")
    val name: String,          // 加密
    @SerialName("Notes")
    val notes: String? = null, // 加密
    @SerialName("Login")
    val login: CipherLoginApiData? = null,
    @SerialName("Card")
    val card: CipherCardApiData? = null,
    @SerialName("Identity")
    val identity: CipherIdentityApiData? = null,
    @SerialName("SecureNote")
    val secureNote: CipherSecureNoteApiData? = null,
    @SerialName("Fields")
    val fields: List<CipherFieldApiData>? = null,
    @SerialName("Favorite")
    val favorite: Boolean = false,
    @SerialName("Reprompt")
    val reprompt: Int = 0
)

@Serializable
data class CipherUpdateRequest(
    @SerialName("Type")
    val type: Int,
    @SerialName("FolderId")
    val folderId: String? = null,
    @SerialName("Name")
    val name: String,
    @SerialName("Notes")
    val notes: String? = null,
    @SerialName("Login")
    val login: CipherLoginApiData? = null,
    @SerialName("Card")
    val card: CipherCardApiData? = null,
    @SerialName("Identity")
    val identity: CipherIdentityApiData? = null,
    @SerialName("SecureNote")
    val secureNote: CipherSecureNoteApiData? = null,
    @SerialName("Fields")
    val fields: List<CipherFieldApiData>? = null,
    @SerialName("Favorite")
    val favorite: Boolean = false,
    @SerialName("Reprompt")
    val reprompt: Int = 0
)

@Serializable
data class FolderCreateRequest(
    @SerialName("Name")
    val name: String  // 加密
)

@Serializable
data class FolderUpdateRequest(
    @SerialName("Name")
    val name: String  // 加密
)

@Serializable
data class SendCreateRequest(
    @SerialName("key")
    val key: String,
    @SerialName("type")
    val type: Int, // 0=Text, 1=File
    @SerialName("name")
    val name: String,
    @SerialName("notes")
    val notes: String? = null,
    @SerialName("password")
    val password: String? = null,
    @SerialName("disabled")
    val disabled: Boolean = false,
    @SerialName("hideEmail")
    val hideEmail: Boolean = false,
    @SerialName("deletionDate")
    val deletionDate: String,
    @SerialName("expirationDate")
    val expirationDate: String? = null,
    @SerialName("maxAccessCount")
    val maxAccessCount: Int? = null,
    @SerialName("text")
    val text: SendTextCreateRequest? = null,
    @SerialName("file")
    val file: SendFileCreateRequest? = null
)

@Serializable
data class SendTextCreateRequest(
    @SerialName("text")
    val text: String,
    @SerialName("hidden")
    val hidden: Boolean = false
)

@Serializable
data class SendFileCreateRequest(
    @SerialName("fileName")
    val fileName: String? = null
)

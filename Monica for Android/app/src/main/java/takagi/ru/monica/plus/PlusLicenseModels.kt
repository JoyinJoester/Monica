package takagi.ru.monica.plus

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlusActivateRequest(
    val cdk: String,
    @SerialName("device_fingerprint")
    val deviceFingerprint: String,
    val platform: String = "android",
    @SerialName("app_version")
    val appVersion: String
)

@Serializable
data class PlusVerifyRequest(
    val cdk: String,
    @SerialName("device_fingerprint")
    val deviceFingerprint: String,
    val platform: String = "android",
    @SerialName("app_version")
    val appVersion: String
)

@Serializable
data class PlusLicenseResponse(
    val success: Boolean? = null,
    val valid: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
    val code: String? = null,
    @SerialName("license_id")
    val licenseId: String? = null,
    @SerialName("expires_at")
    val expiresAt: Long? = null,
    @SerialName("max_devices")
    val maxDevices: Int? = null,
    @SerialName("bound_devices")
    val boundDevices: Int? = null,
    @SerialName("remaining_devices")
    val remainingDevices: Int? = null,
    val token: String? = null
)

@Serializable
data class PlusApiErrorResponse(
    val error: String? = null,
    val code: String? = null,
    val message: String? = null
)

sealed class PlusApiCallResult {
    data class Success(
        val payload: PlusLicenseResponse,
        val statusCode: Int
    ) : PlusApiCallResult()

    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val code: String? = null
    ) : PlusApiCallResult()
}

data class PlusActivationUiResult(
    val success: Boolean,
    val message: String
)

package takagi.ru.monica.plus

import android.content.Context
import takagi.ru.monica.BuildConfig
import takagi.ru.monica.utils.SettingsManager

class PlusLicenseManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val apiService: PlusLicenseApiService = PlusLicenseApiService(BuildConfig.CF_LICENSE_BASE_URL)
) {

    suspend fun activatePlus(cdkInput: String): PlusActivationUiResult {
        val cdk = cdkInput.trim()
        if (cdk.isBlank()) {
            return PlusActivationUiResult(
                success = false,
                message = "请输入激活码"
            )
        }

        if (!apiService.isConfigured()) {
            return PlusActivationUiResult(
                success = false,
                message = "未配置 Cloudflare 校验地址，请先在构建参数中设置"
            )
        }

        val fingerprint = getOrCreateFingerprint()
        val request = PlusActivateRequest(
            cdk = cdk,
            deviceFingerprint = fingerprint,
            appVersion = BuildConfig.FULL_VERSION_NAME
        )

        return when (val result = apiService.activate(request)) {
            is PlusApiCallResult.Success -> {
                val valid = result.payload.valid ?: result.payload.success ?: false
                if (valid) {
                    settingsManager.updatePlusActivated(true)
                    settingsManager.updatePlusLicenseCdk(cdk)
                    settingsManager.updatePlusLicenseLastVerifiedAt(nowEpochSeconds())

                    PlusActivationUiResult(
                        success = true,
                        message = result.payload.message ?: "Plus 激活成功"
                    )
                } else {
                    PlusActivationUiResult(
                        success = false,
                        message = result.payload.error ?: result.payload.message ?: "激活失败"
                    )
                }
            }
            is PlusApiCallResult.Failure -> {
                PlusActivationUiResult(
                    success = false,
                    message = mapActivationFailureMessage(result)
                )
            }
        }
    }

    suspend fun verifyStoredLicenseIfNeeded(force: Boolean = false) {
        if (!apiService.isConfigured()) {
            return
        }

        val cdk = settingsManager.getPlusLicenseCdk()?.trim().orEmpty()
        if (cdk.isBlank()) {
            return
        }

        val now = nowEpochSeconds()
        val lastVerified = settingsManager.getPlusLicenseLastVerifiedAt()
        if (!force && lastVerified > 0L) {
            val interval = BuildConfig.CF_LICENSE_VERIFY_INTERVAL_SECONDS.coerceAtLeast(3600L)
            if (now - lastVerified < interval) {
                return
            }
        }

        val fingerprint = getOrCreateFingerprint()
        val request = PlusVerifyRequest(
            cdk = cdk,
            deviceFingerprint = fingerprint,
            appVersion = BuildConfig.FULL_VERSION_NAME
        )

        when (val result = apiService.verify(request)) {
            is PlusApiCallResult.Success -> {
                when (result.payload.valid) {
                    true -> {
                        settingsManager.updatePlusActivated(true)
                        settingsManager.updatePlusLicenseLastVerifiedAt(now)
                    }
                    false -> {
                        settingsManager.updatePlusActivated(false)
                    }
                    null -> {
                        // no-op
                    }
                }
            }
            is PlusApiCallResult.Failure -> {
                if (result.statusCode != null && result.statusCode in setOf(400, 401, 403, 404)) {
                    settingsManager.updatePlusActivated(false)
                }
            }
        }
    }

    suspend fun clearLocalLicenseState() {
        settingsManager.clearPlusLicenseData()
    }

    private suspend fun getOrCreateFingerprint(): String {
        val saved = settingsManager.getPlusLicenseDeviceFingerprint()?.takeIf { it.isNotBlank() }
        if (saved != null) {
            return saved
        }

        val generated = PlusDeviceFingerprint.create(context)
        settingsManager.updatePlusLicenseDeviceFingerprint(generated)
        return generated
    }

    private fun nowEpochSeconds(): Long {
        return System.currentTimeMillis() / 1000L
    }

    private fun mapActivationFailureMessage(result: PlusApiCallResult.Failure): String {
        if (result.code == "NETWORK_ERROR") {
            return "当前网络无法直连激活服务，请开启 VPN 激活一次；激活成功后可在无 VPN 网络继续使用。"
        }
        return result.message
    }
}

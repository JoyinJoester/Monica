package takagi.ru.monica.plus

class PlusActivationBridge(
    private val plusLicenseManager: PlusLicenseManager
) {

    suspend fun activatePlusWithCDK(
        cdk: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val result = plusLicenseManager.activatePlus(cdk)
        onResult(result.success, result.message)
    }
}

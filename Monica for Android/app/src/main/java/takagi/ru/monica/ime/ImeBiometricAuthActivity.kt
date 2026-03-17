package takagi.ru.monica.ime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.utils.BiometricAuthHelper
import takagi.ru.monica.utils.SettingsManager

class ImeBiometricAuthActivity : AppCompatActivity() {

    private lateinit var biometricAuthHelper: BiometricAuthHelper
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transparent)

        biometricAuthHelper = BiometricAuthHelper(this)
        settingsManager = SettingsManager(this)

        lifecycleScope.launch {
            val settings = withContext(Dispatchers.IO) {
                settingsManager.settingsFlow.first()
            }
            if (!settings.biometricEnabled || !biometricAuthHelper.isBiometricAvailable()) {
                publishResult(false, getString(R.string.biometric_not_available))
                return@launch
            }
            biometricAuthHelper.authenticate(
                activity = this@ImeBiometricAuthActivity,
                title = getString(R.string.biometric_login_title),
                subtitle = getString(R.string.biometric_login_subtitle),
                description = getString(R.string.biometric_login_description),
                negativeButtonText = getString(R.string.use_password),
                onSuccess = {
                    publishResult(true, null)
                },
                onError = { _, errorMessage ->
                    publishResult(
                        false,
                        getString(R.string.biometric_auth_error, errorMessage)
                    )
                },
                onCancel = {
                    publishResult(false, null)
                }
            )
        }
    }

    private fun publishResult(success: Boolean, errorMessage: String?) {
        sendBroadcast(
            Intent(MonicaInputMethodService.ACTION_IME_BIOMETRIC_RESULT).apply {
                setPackage(packageName)
                putExtra(MonicaInputMethodService.EXTRA_IME_BIOMETRIC_SUCCESS, success)
                putExtra(MonicaInputMethodService.EXTRA_IME_BIOMETRIC_ERROR, errorMessage)
            }
        )
        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
        overridePendingTransition(0, 0)
    }
}

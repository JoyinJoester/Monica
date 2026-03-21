package takagi.ru.monica.ime

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.ui.components.MonicaPasswordDialogAuthScreen
import takagi.ru.monica.utils.BiometricAuthHelper
import takagi.ru.monica.utils.SettingsManager

class ImeUnlockActivity : AppCompatActivity() {

    private var resultPublished = false
    private lateinit var securityManager: SecurityManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var biometricAuthHelper: BiometricAuthHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            findViewById<View?>(android.R.id.content)?.importantForAutofill =
                View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        securityManager = SecurityManager(applicationContext)
        settingsManager = SettingsManager(applicationContext)
        biometricAuthHelper = BiometricAuthHelper(this)

        if (!securityManager.isMasterPasswordSet() || SessionManager.canSkipVerification(this)) {
            publishResult(success = true, errorMessage = null)
            return
        }

        lifecycleScope.launch {
            val settings = settingsManager.settingsFlow.first()
            if (settings.biometricEnabled && biometricAuthHelper.isBiometricAvailable()) {
                showBiometricAuthentication()
            } else {
                showPasswordAuthentication()
            }
        }
    }

    override fun onDestroy() {
        if (!resultPublished && !isChangingConfigurations) {
            publishResult(success = false, errorMessage = null)
        }
        super.onDestroy()
    }

    private fun showBiometricAuthentication() {
        biometricAuthHelper.authenticate(
            activity = this,
            title = getString(R.string.ime_unlock_title),
            subtitle = getString(R.string.autofill_auth_subtitle),
            description = getString(R.string.ime_unlock_in_app_message),
            onSuccess = {
                val unlocked = runCatching { securityManager.unlockVaultWithBiometric() }.getOrDefault(false)
                if (unlocked) {
                    securityManager.markVaultAuthenticated()
                    publishResult(success = true, errorMessage = null)
                } else {
                    showPasswordAuthentication()
                }
            },
            onError = { _, _ ->
                showPasswordAuthentication()
            },
            onCancel = {
                showPasswordAuthentication()
            }
        )
    }

    private fun showPasswordAuthentication() {
        setContent {
            MonicaPasswordDialogAuthScreen(
                settingsFlow = settingsManager.settingsFlow,
                appName = getString(R.string.app_name),
                title = getString(R.string.ime_unlock_title),
                subtitle = getString(R.string.ime_unlock_in_app_message),
                passwordLabel = getString(R.string.ime_unlock_password_label),
                description = getString(R.string.enter_master_password),
                confirmText = getString(R.string.unlock),
                cancelText = getString(R.string.cancel),
                emptyError = getString(R.string.current_password_required),
                numericError = getString(R.string.error_password_must_be_numeric),
                minLengthError = getString(R.string.error_password_min_6_digits),
                incorrectError = getString(R.string.ime_unlock_error),
                verifyPassword = { input -> securityManager.unlockVaultWithPassword(input) },
                onSuccess = {
                    securityManager.markVaultAuthenticated()
                    publishResult(success = true, errorMessage = null)
                },
                onCancel = { publishResult(success = false, errorMessage = null) }
            )
        }
    }

    private fun publishResult(success: Boolean, errorMessage: String?) {
        if (resultPublished) return
        resultPublished = true
        sendBroadcast(
            Intent(MonicaInputMethodService.ACTION_IME_BIOMETRIC_RESULT).apply {
                setPackage(packageName)
                putExtra(MonicaInputMethodService.EXTRA_IME_BIOMETRIC_SUCCESS, success)
                putExtra(MonicaInputMethodService.EXTRA_IME_BIOMETRIC_ERROR, errorMessage)
            }
        )
        finish()
    }
}

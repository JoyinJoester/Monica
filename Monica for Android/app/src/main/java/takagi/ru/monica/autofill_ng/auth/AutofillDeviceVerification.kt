package takagi.ru.monica.autofill_ng.auth

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.atomic.AtomicBoolean
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.BiometricAuthHelper

/** System identity verification shared by suggestion selection and the full picker. */
@Composable
internal fun AutofillDeviceVerification(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.fragmentActivity() }
    val success = rememberUpdatedState(onSuccess)
    val cancel = rememberUpdatedState(onCancel)
    val finished = remember { AtomicBoolean(false) }
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    val security = remember { lazy { SecurityManager(context.applicationContext) } }
    val helper = remember(context) { BiometricAuthHelper(context) }

    fun cancelOnce(message: CharSequence? = null) {
        if (!finished.compareAndSet(false, true)) return
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        cancel.value()
    }

    fun verified() {
        if (finished.get()) return
        if (activity == null || activity.isFinishing || activity.isDestroyed ||
            context.getSystemService(PowerManager::class.java)?.isInteractive != true ||
            keyguard?.isKeyguardLocked == true) {
            cancelOnce()
            return
        }
        // A successful Android prompt does not manufacture an unavailable vault key.
        // Rebuilding an invalidated wrapper still requires a normal main-app unlock.
        if (!security.value.isVaultRuntimeUnlocked() && !security.value.unlockVaultWithBiometric()) {
            cancelOnce(context.getString(R.string.autofill_device_auth_unlock_app))
            return
        }
        if (finished.compareAndSet(false, true)) success.value()
    }

    val credentialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) verified() else cancelOnce()
    }
    fun launchDeviceCredential() {
        @Suppress("DEPRECATION")
        val intent = keyguard?.takeIf { it.isDeviceSecure }?.createConfirmDeviceCredentialIntent(
            context.getString(R.string.autofill_auth_title),
            context.getString(R.string.autofill_device_auth_description),
        )
        if (intent == null) cancelOnce(context.getString(R.string.autofill_device_auth_unavailable))
        else runCatching { credentialLauncher.launch(intent) }.onFailure {
            cancelOnce(context.getString(R.string.autofill_device_auth_unavailable))
        }
    }

    DisposableEffect(helper) {
        onDispose { finished.set(true); helper.cancelAuthentication() }
    }
    LaunchedEffect(helper, activity) {
        if (activity != null && helper.isSystemAuthenticationAvailable()) {
            runCatching {
                // Reuse Monica's existing native authentication component. This
                // composable renders no account list or custom verification UI.
                helper.authenticate(
                    activity = activity,
                    title = context.getString(R.string.autofill_auth_title),
                    subtitle = null,
                    description = context.getString(R.string.autofill_device_auth_description),
                    negativeButtonText = context.getString(
                        if (keyguard?.isDeviceSecure == true) R.string.autofill_use_screen_lock else R.string.cancel),
                    onSuccess = { verified() },
                    onError = { code, message ->
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && keyguard?.isDeviceSecure == true &&
                            code in setOf(BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT)) {
                            launchDeviceCredential()
                        } else if (code == BiometricPrompt.ERROR_CANCELED) cancelOnce()
                        else cancelOnce(message)
                    },
                    onCancel = { cancelOnce() },
                    onNegativeButton = { launchDeviceCredential() },
                )
            }.onFailure { cancelOnce(context.getString(R.string.autofill_device_auth_unavailable)) }
        } else if (activity != null && keyguard?.isDeviceSecure == true) {
            launchDeviceCredential()
        } else cancelOnce(context.getString(R.string.autofill_device_auth_unavailable))
    }
}

private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

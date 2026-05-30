package takagi.ru.monica.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.Prompt
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OneDriveAccountSession(
    val accountId: String,
    val username: String,
    val displayName: String,
    val authority: String? = null,
    val accessToken: String? = null
)

class OneDriveAuthTemporarilyUnavailableException(
    message: String = "OneDrive 暂时无法刷新登录状态。请关闭系统电池优化，或点亮屏幕并重新打开 Monica 后再试。",
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class OneDriveAuthManager(context: Context) {
    private val appContext = context.applicationContext

    suspend fun signIn(activity: Activity): OneDriveAccountSession = withContext(Dispatchers.Main) {
        val application = getApplication()
        suspendCancellableCoroutine { continuation ->
            val parameters = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(SCOPES)
                .withPrompt(Prompt.SELECT_ACCOUNT)
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        continuation.resume(authenticationResult.toSession())
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resumeWithException(exception)
                    }

                    override fun onCancel() {
                        continuation.resumeWithException(IllegalStateException("已取消 OneDrive 登录"))
                    }
                })
                .build()
            application.acquireToken(parameters)
        }
    }

    suspend fun getCachedSession(): OneDriveAccountSession? {
        val account = getAccounts().firstOrNull() ?: return null
        return account.toSession()
    }

    suspend fun acquireAccessToken(accountId: String): OneDriveAccountSession {
        val application = getApplication()
        val account = getAccount(accountId)
            ?: throw IllegalStateException("OneDrive 账户已失效，请重新登录")

        return withContext(Dispatchers.IO) {
            throwIfSilentRefreshBlockedByPowerState()
            val result = try {
                application.acquireTokenSilent(
                    SCOPES.toTypedArray(),
                    account,
                    account.authority ?: COMMON_AUTHORITY
                )
            } catch (exception: MsalException) {
                if (exception.isPowerOptimizationRefreshFailure()) {
                    throw OneDriveAuthTemporarilyUnavailableException(cause = exception)
                }
                throw exception
            }
            result.toSession()
        }
    }

    private fun throwIfSilentRefreshBlockedByPowerState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val isIdle = powerManager.isDeviceIdleMode
        val isOptimized = !powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
        if (isIdle && isOptimized) {
            throw OneDriveAuthTemporarilyUnavailableException()
        }
    }

    private suspend fun getApplication(): IMultipleAccountPublicClientApplication {
        cachedApplication?.let { return it }

        return applicationMutex.withLock {
            cachedApplication?.let { return@withLock it }

            val application = withContext(Dispatchers.IO) {
                PublicClientApplication.createMultipleAccountPublicClientApplication(
                    appContext,
                    R.raw.onedrive_msal_config
                )
            }
            cachedApplication = application
            application
        }
    }

    private suspend fun getAccounts(): List<IAccount> {
        val application = getApplication()
        return withContext(Dispatchers.IO) {
            application.getAccounts().orEmpty()
        }
    }

    private suspend fun getAccount(accountId: String): IAccount? {
        val application = getApplication()
        return withContext(Dispatchers.IO) {
            application.getAccount(accountId)
        }
    }

    private fun IAccount.toSession(accessToken: String? = null): OneDriveAccountSession {
        val resolvedId = id?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("OneDrive 账户标识为空")
        val resolvedUsername = username.orEmpty()
        val resolvedDisplayName = claims?.get("name") as? String
            ?: resolvedUsername.ifBlank { "OneDrive" }
        return OneDriveAccountSession(
            accountId = resolvedId,
            username = resolvedUsername,
            displayName = resolvedDisplayName,
            authority = authority,
            accessToken = accessToken
        )
    }

    private fun IAuthenticationResult.toSession(): OneDriveAccountSession {
        return account.toSession(accessToken = accessToken)
    }

    companion object {
        val SCOPES: List<String> = listOf(
            "User.Read",
            "Files.ReadWrite"
        )

        private const val COMMON_AUTHORITY = "https://login.microsoftonline.com/common"
        @Volatile
        private var cachedApplication: IMultipleAccountPublicClientApplication? = null
        private val applicationMutex = Mutex()
    }
}

fun Throwable.isOneDriveAuthTemporarilyUnavailable(): Boolean {
    return generateSequence(this) { it.cause }.any { error ->
        error is OneDriveAuthTemporarilyUnavailableException ||
            error.message.orEmpty().contains("Connection is not available to refresh token", ignoreCase = true) ||
            error.message.orEmpty().contains("power optimization", ignoreCase = true) ||
            error.message.orEmpty().contains("doze mode", ignoreCase = true) ||
            error.message.orEmpty().contains("app is standby", ignoreCase = true)
    }
}

fun Throwable.toOneDriveUserMessage(fallback: String = "OneDrive 操作失败"): String {
    if (isOneDriveAuthTemporarilyUnavailable()) {
        return "OneDrive 暂时无法刷新登录状态。请关闭系统电池优化，或点亮屏幕并重新打开 Monica 后再试。"
    }
    return message?.takeIf { it.isNotBlank() } ?: fallback
}

private fun Throwable.isPowerOptimizationRefreshFailure(): Boolean {
    return isOneDriveAuthTemporarilyUnavailable()
}

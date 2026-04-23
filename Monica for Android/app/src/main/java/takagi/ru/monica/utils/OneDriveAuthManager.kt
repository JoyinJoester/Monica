package takagi.ru.monica.utils

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
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

class OneDriveAuthManager(context: Context) {
    private val appContext = context.applicationContext

    suspend fun signIn(activity: Activity): OneDriveAccountSession = withContext(Dispatchers.Main) {
        val application = getApplication()
        suspendCancellableCoroutine { continuation ->
            val parameters = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(SCOPES)
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
            val result = application.acquireTokenSilent(
                SCOPES.toTypedArray(),
                account,
                account.authority ?: COMMON_AUTHORITY
            )
            result.toSession()
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

package takagi.ru.monica.utils

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GoogleDriveAccountSession(
    val accountId: String,
    val username: String,
    val displayName: String,
    val accessToken: String? = null
)

sealed interface GoogleDriveAuthorizationStep {
    data class Authorized(val session: GoogleDriveAccountSession) : GoogleDriveAuthorizationStep
    data class ResolutionRequired(val pendingIntent: PendingIntent) : GoogleDriveAuthorizationStep
}

@Serializable
private data class GoogleUserInfoResponse(
    val email: String? = null,
    val name: String? = null
)

class GoogleDriveAuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val authClient by lazy { Identity.getAuthorizationClient(appContext) }
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun beginAuthorization(expectedAccountId: String? = null): GoogleDriveAuthorizationStep =
        withContext(Dispatchers.IO) {
            val requestBuilder = AuthorizationRequest.builder()
                .setRequestedScopes(SCOPES)
            expectedAccountId
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { requestBuilder.setAccount(Account(it, GOOGLE_ACCOUNT_TYPE)) }

            val result = authClient.authorize(requestBuilder.build()).await()
            val pendingIntent = result.pendingIntent
            if (pendingIntent != null) {
                GoogleDriveAuthorizationStep.ResolutionRequired(pendingIntent)
            } else {
                GoogleDriveAuthorizationStep.Authorized(
                    result.toSession(expectedAccountId)
                )
            }
        }

    suspend fun completeAuthorization(data: Intent?, expectedAccountId: String? = null): GoogleDriveAccountSession =
        withContext(Dispatchers.IO) {
            val result = authClient.getAuthorizationResultFromIntent(data)
            result.toSession(expectedAccountId)
        }

    suspend fun getCachedSession(expectedAccountId: String? = null): GoogleDriveAccountSession? {
        return runCatching {
            when (val step = beginAuthorization(expectedAccountId)) {
                is GoogleDriveAuthorizationStep.Authorized -> step.session
                is GoogleDriveAuthorizationStep.ResolutionRequired -> null
            }
        }.getOrNull()
    }

    suspend fun acquireAccessToken(accountId: String): GoogleDriveAccountSession {
        return when (val step = beginAuthorization(accountId)) {
            is GoogleDriveAuthorizationStep.Authorized -> step.session
            is GoogleDriveAuthorizationStep.ResolutionRequired -> {
                throw IllegalStateException("Google Drive 需要重新授权，请重新连接账户")
            }
        }
    }

    suspend fun revokeAccess(accountId: String) {
        withContext(Dispatchers.IO) {
            val request = RevokeAccessRequest.builder()
                .setAccount(Account(accountId, GOOGLE_ACCOUNT_TYPE))
                .setScopes(SCOPES)
                .build()
            authClient.revokeAccess(request).await()
        }
    }

    suspend fun clearAccessToken(accessToken: String) {
        withContext(Dispatchers.IO) {
            val request = ClearTokenRequest.builder()
                .setToken(accessToken)
                .build()
            authClient.clearToken(request).await()
        }
    }

    private suspend fun AuthorizationResult.toSession(expectedAccountId: String? = null): GoogleDriveAccountSession {
        val token = accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Drive 访问令牌为空")
        val userInfo = fetchUserInfo(token)
        val email = userInfo.email?.trim().orEmpty()
        if (email.isBlank()) {
            throw IllegalStateException("Google Drive 账户邮箱为空")
        }
        if (!expectedAccountId.isNullOrBlank() && !email.equals(expectedAccountId, ignoreCase = true)) {
            throw IllegalStateException("当前 Google 账户与已接入账户不一致，请切换到 $expectedAccountId")
        }
        return GoogleDriveAccountSession(
            accountId = email,
            username = email,
            displayName = userInfo.name?.takeIf { it.isNotBlank() } ?: email,
            accessToken = token
        )
    }

    private suspend fun fetchUserInfo(accessToken: String): GoogleUserInfoResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(USER_INFO_URL)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(
                    responseBody.ifBlank { "获取 Google 账户信息失败: HTTP ${response.code}" }
                )
            }
            json.decodeFromString(GoogleUserInfoResponse.serializer(), responseBody)
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener {
            continuation.resumeWithException(IllegalStateException("已取消 Google Drive 授权"))
        }
    }

    companion object {
        val SCOPES: List<Scope> = listOf(
            Scope("https://www.googleapis.com/auth/drive"),
            Scope("openid"),
            Scope("email"),
            Scope("profile")
        )

        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
    }
}

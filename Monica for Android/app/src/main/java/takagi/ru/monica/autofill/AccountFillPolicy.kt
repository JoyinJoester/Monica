package takagi.ru.monica.autofill

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.SettingsManager

object AccountFillPolicy {
    fun resolveAccountIdentifier(
        entry: PasswordEntry,
        securityManager: SecurityManager
    ): String {
        return try {
            if (entry.username.contains("==") && entry.username.length > 20) {
                securityManager.decryptData(entry.username)
            } else {
                entry.username
            }
        } catch (_: Exception) {
            entry.username
        }
    }

    fun shouldFillEmailWithAccount(context: Context): Boolean {
        return runCatching {
            runBlocking {
                SettingsManager(context).settingsFlow.first().separateUsernameAccountEnabled
            }
        }.getOrDefault(false)
    }
}


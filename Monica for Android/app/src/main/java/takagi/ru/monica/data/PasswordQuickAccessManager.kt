package takagi.ru.monica.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.passwordQuickAccessDataStore: DataStore<Preferences> by preferencesDataStore(name = "password_quick_access")

@Serializable
data class PasswordQuickAccessRecord(
    val passwordId: Long,
    val openCount: Int,
    val lastOpenedAt: Long
)

class PasswordQuickAccessManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val QUICK_ACCESS_KEY = stringPreferencesKey("password_quick_access_records")
        private const val MAX_RECORDS = 400
    }

    val recordsFlow: Flow<List<PasswordQuickAccessRecord>> = context.passwordQuickAccessDataStore.data
        .map { preferences ->
            decodeRecords(preferences[QUICK_ACCESS_KEY])
        }

    val statsFlow: Flow<Map<Long, PasswordQuickAccessRecord>> = recordsFlow
        .map { records -> records.associateBy { it.passwordId } }

    suspend fun recordOpen(passwordId: Long) {
        if (passwordId <= 0L) return

        context.passwordQuickAccessDataStore.edit { preferences ->
            val existing = decodeRecords(preferences[QUICK_ACCESS_KEY])
                .associateBy { it.passwordId }
                .toMutableMap()

            val now = System.currentTimeMillis()
            val current = existing[passwordId]
            existing[passwordId] = PasswordQuickAccessRecord(
                passwordId = passwordId,
                openCount = (current?.openCount ?: 0) + 1,
                lastOpenedAt = now
            )

            val trimmed = existing.values
                .asSequence()
                .filter { it.passwordId > 0L && it.openCount > 0 }
                .sortedByDescending { it.lastOpenedAt }
                .take(MAX_RECORDS)
                .toList()

            preferences[QUICK_ACCESS_KEY] = json.encodeToString(trimmed)
        }
    }

    private fun decodeRecords(raw: String?): List<PasswordQuickAccessRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<PasswordQuickAccessRecord>>(raw)
                .filter { it.passwordId > 0L && it.openCount > 0 }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

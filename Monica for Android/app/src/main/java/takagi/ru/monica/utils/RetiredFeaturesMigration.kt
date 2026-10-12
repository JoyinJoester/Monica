package takagi.ru.monica.utils

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/** Removes retired preferences without resetting unrelated settings or touching vault data. */
internal object RetiredFeaturesMigration : DataMigration<Preferences> {
    private val languageKey = stringPreferencesKey("language")
    private val retiredLanguages = setOf("NYA", "SNOW_LEOPARD")
    private val retiredNames = setOf(
        "use_draggable_bottom_nav",
        "passkey_hyperos_biometric_bypass_enabled",
    )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.asMap().keys.any { it.name in retiredNames } || currentData[languageKey] in retiredLanguages

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        asMap().keys.filter { it.name in retiredNames }.forEach { remove(it) }
        if (this[languageKey] in retiredLanguages) this[languageKey] = "CHINESE"
    }

    override suspend fun cleanUp() = Unit
}

package takagi.ru.monica.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.generatorPreferencesDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "generator_preferences")

@Serializable
data class GeneratorPreferences(
    val selectedGenerator: String = "SYMBOL",

    // SYMBOL
    val symbolLength: Int = 12,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val useSymbolExclusionMode: Boolean = true,
    val excludedSymbols: String = "",
    val customSymbols: String = DEFAULT_SYMBOLS,
    val excludeSimilar: Boolean = false,
    val excludeAmbiguous: Boolean = false,
    val analyzeCommonPasswords: Boolean = false,
    val analyzeWeight: Int = 60,
    val uppercaseMin: Int = 0,
    val lowercaseMin: Int = 0,
    val numbersMin: Int = 0,
    val symbolsMin: Int = 0,

    // PASSPHRASE
    val passphraseWordCount: Int = 5,
    val passphraseDelimiter: String = "-",
    val passphraseCapitalize: Boolean = false,
    val passphraseIncludeNumber: Boolean = false,
    val passphraseCustomWord: String = "",
    val passphraseCustomWords: String = "",

    // PIN
    val pinLength: Int = 6,

    // PASSWORD (word-based)
    val passwordLength: Int = 12,
    val firstLetterUppercase: Boolean = false,
    val includeNumbersInPassword: Boolean = true,
    val customSeparator: String = "",
    val separatorCountsTowardsLength: Boolean = false,
    val segmentLength: Int = 0,

    // SSH_KEY
    val sshKeyAlgorithm: String = "ED25519",
    val sshKeyRsaSize: Int = 3072
) {
    companion object {
        const val DEFAULT_SYMBOLS = "!@#\$%^&*()_+-=[]{}|;:,.<>?"
    }
}

class GeneratorPreferencesManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val PREFERENCES_KEY = stringPreferencesKey("generator_prefs_json")
    }

    val preferencesFlow: Flow<GeneratorPreferences> =
        context.generatorPreferencesDataStore.data.map { preferences ->
            val jsonStr = preferences[PREFERENCES_KEY] ?: return@map GeneratorPreferences()
            runCatching { json.decodeFromString<GeneratorPreferences>(jsonStr) }
                .getOrDefault(GeneratorPreferences())
        }

    suspend fun load(): GeneratorPreferences = preferencesFlow.first()

    suspend fun save(prefs: GeneratorPreferences) {
        context.generatorPreferencesDataStore.edit { store ->
            store[PREFERENCES_KEY] = json.encodeToString(prefs)
        }
    }
}

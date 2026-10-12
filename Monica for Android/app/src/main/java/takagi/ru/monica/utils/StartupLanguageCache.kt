package takagi.ru.monica.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import takagi.ru.monica.data.Language

private const val STARTUP_LANGUAGE_PREFS = "monica_startup_language"
private const val STARTUP_LANGUAGE_KEY = "language"
private const val LEGACY_DATASTORE_MIGRATION_TIMEOUT_MS = 200L
private const val ACTION_LANGUAGE_CHANGED = "takagi.ru.monica.action.LANGUAGE_CHANGED"
private const val EXTRA_LANGUAGE = "language"
private const val EXTRA_SENDER_PID = "sender_pid"

internal fun parseStartupLanguage(value: String?): Language =
    when (value) {
        "NYA", "SNOW_LEOPARD" -> Language.CHINESE
        else -> value?.let { raw -> runCatching { Language.valueOf(raw) }.getOrNull() } ?: Language.SYSTEM
    }

/**
 * Small startup-only mirror of the DataStore language setting.
 *
 * Activity.attachBaseContext() needs the language before normal coroutine
 * collection can start. A SharedPreferences mirror avoids waiting for the
 * complete AppSettings DataStore on every Activity creation. Existing installs
 * perform one bounded migration when the mirror is first introduced.
 */
object StartupLanguageCache {

    @Volatile
    private var processLanguage: Language? = null

    private val languageChanges = MutableStateFlow(Language.SYSTEM)
    private val observedLanguage = languageChanges.asStateFlow()

    @Volatile
    private var languageReceiverRegistered = false

    fun read(context: Context): Language {
        registerLanguageReceiver(context.applicationContext)
        processLanguage?.let { return it }

        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(
            STARTUP_LANGUAGE_PREFS,
            Context.MODE_PRIVATE,
        )
        val cachedValue = if (preferences.contains(STARTUP_LANGUAGE_KEY)) {
            parseStartupLanguage(preferences.getString(STARTUP_LANGUAGE_KEY, null))
        } else {
            migrateLegacyDataStoreLanguage(appContext)
        }
        if (preferences.getString(STARTUP_LANGUAGE_KEY, null) in setOf("NYA", "SNOW_LEOPARD")) {
            preferences.edit().putString(STARTUP_LANGUAGE_KEY, cachedValue.name).apply()
        }
        processLanguage = cachedValue
        languageChanges.value = cachedValue
        return cachedValue
    }

    /** Includes changes made by the main app while the separate IME process lives. */
    fun languageFlow(context: Context): StateFlow<Language> {
        read(context)
        return observedLanguage
    }

    fun write(context: Context, language: Language) {
        val appContext = context.applicationContext
        processLanguage = language
        languageChanges.value = language
        val preferences = appContext.getSharedPreferences(
            STARTUP_LANGUAGE_PREFS,
            Context.MODE_PRIVATE,
        )
        if (preferences.getString(STARTUP_LANGUAGE_KEY, null) == language.name) return
        preferences.edit().putString(STARTUP_LANGUAGE_KEY, language.name).apply()
        appContext.sendBroadcast(
            Intent(ACTION_LANGUAGE_CHANGED)
                .setPackage(appContext.packageName)
                .putExtra(EXTRA_LANGUAGE, language.name)
                .putExtra(EXTRA_SENDER_PID, Process.myPid())
        )
    }

    private fun registerLanguageReceiver(appContext: Context) {
        if (languageReceiverRegistered) return
        synchronized(this) {
            if (languageReceiverRegistered) return
            // The application context owns this receiver for the process lifetime.
            // Only display language crosses processes; the settings DataStore stays unchanged.
            ContextCompat.registerReceiver(
                appContext,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action != ACTION_LANGUAGE_CHANGED) return
                        if (intent.getIntExtra(EXTRA_SENDER_PID, -1) == Process.myPid()) return
                        val rawLanguage = intent.getStringExtra(EXTRA_LANGUAGE) ?: return
                        val language = Language.entries.firstOrNull { it.name == rawLanguage } ?: return
                        processLanguage = language
                        languageChanges.value = language
                        LocaleHelper.setLocale(appContext, language)
                    }
                },
                IntentFilter(ACTION_LANGUAGE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            languageReceiverRegistered = true
        }
    }

    private fun migrateLegacyDataStoreLanguage(context: Context): Language {
        val dataStoreFile = File(context.filesDir, "datastore/settings.preferences_pb")
        val migratedLanguage = if (dataStoreFile.isFile) {
            runBlocking {
                withTimeoutOrNull(LEGACY_DATASTORE_MIGRATION_TIMEOUT_MS) {
                    runCatching {
                        SettingsManager(context).settingsFlow.first().language
                    }.getOrDefault(Language.SYSTEM)
                }
            } ?: Language.SYSTEM
        } else {
            Language.SYSTEM
        }
        write(context, migratedLanguage)
        return migratedLanguage
    }
}

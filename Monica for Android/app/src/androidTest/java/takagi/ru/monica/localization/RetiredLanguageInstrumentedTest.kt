package takagi.ru.monica.localization

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.R
import takagi.ru.monica.data.AppLauncherIcon
import takagi.ru.monica.data.AppLauncherLabel
import takagi.ru.monica.data.Language
import takagi.ru.monica.utils.AppLauncherIconManager
import takagi.ru.monica.utils.LocaleHelper
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.utils.StartupLanguageCache
import java.util.Locale

class RetiredLanguageInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun coldStartupRewritesRetiredLanguageAndUsesNormalChineseResources() {
        val original = StartupLanguageCache.read(context)
        val originalLocale = Locale.getDefault()
        val preferences = context.getSharedPreferences("monica_startup_language", Context.MODE_PRIVATE)
        try {
            for (retired in listOf("NYA", "SNOW_LEOPARD")) {
                assertTrue(preferences.edit().putString("language", retired).commit())
                StartupLanguageCache::class.java.getDeclaredField("processLanguage").apply {
                    isAccessible = true
                    set(null, null)
                }
                assertEquals(Language.CHINESE, StartupLanguageCache.read(context))
                assertEquals("CHINESE", preferences.getString("language", null))
                val localized = LocaleHelper.setLocale(context, StartupLanguageCache.read(context))
                assertEquals("取消", localized.getString(R.string.cancel))
                assertEquals(Language.CHINESE, LocaleHelper.getCurrentLanguage(localized))
            }
        } finally {
            StartupLanguageCache.write(context, original)
            Locale.setDefault(originalLocale)
        }
    }

    @Test fun upgradeFromOnlyRetiredLauncherPreservesOneNormalEntryAndIconPreference() = runBlocking {
        val original = SettingsManager(context).settingsFlow.first()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val retired = ComponentName(context, "takagi.ru.monica.SnowLeopardVisibleLauncherAlias")
        try {
            for (icon in AppLauncherIcon.entries) for (label in AppLauncherLabel.entries) {
                pm.setComponentEnabledSetting(retired, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                pm.queryIntentActivities(intent, 0).filter { it.activityInfo.name != retired.className }.forEach {
                    pm.setComponentEnabledSetting(ComponentName(context, it.activityInfo.name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
                val previous = pm.queryIntentActivities(intent, 0).single().activityInfo
                assertEquals(retired.className, previous.name)
                assertTrue(previous.icon in setOf(R.mipmap.ic_launcher_modern, R.mipmap.ic_launcher_modern_round))
                AppLauncherIconManager.repairLaunchEntryPointsAfterUpgrade(context, icon, label)
                val active = pm.queryIntentActivities(intent, 0).single().activityInfo
                val base = if (icon == AppLauncherIcon.BLUE_STAR) "BlueStarVisibleLauncherAlias" else "ModernVisibleLauncherAlias"
                val suffix = if (label == AppLauncherLabel.MONICA_PASS) "" else "Monica"
                assertEquals("takagi.ru.monica.$base$suffix", active.name)
                assertEquals("takagi.ru.monica.MainActivity", active.targetActivity)
                assertEquals(icon, AppLauncherIconManager.getCurrentSelection(context))
            }
        } finally {
            AppLauncherIconManager.apply(context, original.appLauncherIcon, original.appLauncherLabel)
        }
    }
}

package takagi.ru.monica.localization

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

class SnowLeopardLanguageInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun playfulTranslationsUseChineseFallbackAndKeepDestructiveWarningsClear() {
        val originalLocale = Locale.getDefault()
        try {
            val chinese = LocaleHelper.setLocale(context, Language.CHINESE)
            val snow = LocaleHelper.setLocale(context, Language.SNOW_LEOPARD)
            assertEquals(Language.SNOW_LEOPARD, LocaleHelper.getCurrentLanguage(snow))
            assertEquals("算了嗷呜", snow.getString(R.string.cancel))
            assertEquals("芝士雪豹语", snow.getString(R.string.language_snow_leopard))
            // These newer features are intentionally absent from the one-release language pack.
            listOf(R.string.api_token_name, R.string.api_token_advanced).forEach { id ->
                assertEquals(chinese.getString(id), snow.getString(id))
            }
            val warning = snow.getString(R.string.clear_all_data_warning)
            assertTrue(warning.contains("永久删除"))
            assertTrue(warning.contains("无法恢复"))
            assertTrue(warning.contains("备份"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test fun switchingLanguageAndUpgradeRepairKeepOneWorkingLauncherEntry() = runBlocking {
        val manager = SettingsManager(context)
        val original = manager.settingsFlow.first()
        try {
            manager.updateAppLauncherIcon(AppLauncherIcon.MODERN)
            AppLauncherLabel.entries.forEach { label ->
                manager.updateAppLauncherLabel(label)
                withTimeout(5_000) { manager.settingsFlow.first { it.appLauncherLabel == label } }
                manager.updateLanguage(Language.SNOW_LEOPARD)
                withTimeout(5_000) { manager.settingsFlow.first { it.language == Language.SNOW_LEOPARD } }
                assertEquals(Language.SNOW_LEOPARD, StartupLanguageCache.read(context))
                assertSingleLauncher("SnowLeopardVisibleLauncherAlias", R.mipmap.ic_launcher_snow_leopard)
                AppLauncherIconManager.repairLaunchEntryPointsAfterUpgrade(context, AppLauncherIcon.MODERN, label)
                assertSingleLauncher("SnowLeopardVisibleLauncherAlias", R.mipmap.ic_launcher_snow_leopard)
                listOf(Language.CHINESE, Language.NYA, Language.ENGLISH).forEach { language ->
                    manager.updateLanguage(language)
                    withTimeout(5_000) { manager.settingsFlow.first { it.language == language } }
                    val normalAlias = if (label == AppLauncherLabel.MONICA_PASS) "ModernVisibleLauncherAlias"
                        else "ModernVisibleLauncherAliasMonica"
                    assertSingleLauncher(normalAlias)
                }
            }
        } finally {
            manager.updateAppLauncherLabel(original.appLauncherLabel)
            withTimeout(5_000) { manager.settingsFlow.first { it.appLauncherLabel == original.appLauncherLabel } }
            manager.updateLanguage(original.language)
            manager.updateAppLauncherIcon(original.appLauncherIcon)
        }
    }

    private fun assertSingleLauncher(alias: String, icon: Int? = null) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val launchers = context.packageManager.queryIntentActivities(intent, 0)
        assertEquals("Exactly one visible launcher must remain enabled", 1, launchers.size)
        val activity = launchers.single().activityInfo
        assertEquals("takagi.ru.monica.$alias", activity.name)
        assertEquals("takagi.ru.monica.MainActivity", activity.targetActivity)
        if (icon != null) {
            // Android may select android:roundIcon according to the launcher configuration.
            assertTrue(activity.icon == icon || activity.icon == R.mipmap.ic_launcher_snow_leopard_round)
            // Keep comparable exports at launcher, review and zoomed inspection sizes.
            for (size in listOf(128, 256, 512)) {
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                activity.loadIcon(context.packageManager).apply {
                    setBounds(0, 0, size, size)
                    draw(Canvas(bitmap))
                }
                val name = if (size == 256) "snow-leopard-launcher-icon.png"
                    else "snow-leopard-launcher-icon-$size.png"
                File(context.filesDir, name).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }
}

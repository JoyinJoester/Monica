package takagi.ru.monica.ui

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class GpgLocalizationDeviceTest {
    @Test fun everySupportedLocaleResolvesLocalizedGpgResources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = mapOf(
            "en" to "GPG key", "zh-CN" to "GPG 密钥", "zh" to "GPG 密钥",
            "zh-HK" to "GPG 金鑰", "zh-TW" to "GPG 金鑰", "zh-Hant" to "GPG 金鑰",
            "lzh" to "GPG 密钥", "de" to "GPG-Schlüssel",
            "it" to "Chiave GPG", "es" to "Clave GPG", "fr" to "Clé GPG", "ja" to "GPG 鍵",
            "ko" to "GPG 키", "pl" to "Klucz GPG", "ru" to "Ключ GPG", "vi" to "Khóa GPG")
        for ((tag, title) in expected) {
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val localized = context.createConfigurationContext(config)
            assertEquals(tag, title, localized.getString(R.string.gpg_title))
            assertTrue(localized.getString(R.string.gpg_days, 365).contains("365"))
            if (tag != "en") {
                assertNotEquals(tag, context.createConfigurationContext(Configuration(config).apply {
                    setLocale(Locale.ENGLISH)
                }).getString(R.string.gpg_email_invalid), localized.getString(R.string.gpg_email_invalid))
            }
        }
    }
}

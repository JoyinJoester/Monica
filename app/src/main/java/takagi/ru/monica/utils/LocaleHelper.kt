package takagi.ru.monica.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import takagi.ru.monica.data.Language
import java.util.*

object LocaleHelper {
    
    fun setLocale(context: Context, language: Language): Context {
        val locale = when (language) {
            Language.SYSTEM -> getSystemLocale()
            Language.ENGLISH -> Locale.ENGLISH
            Language.CHINESE -> Locale.CHINA  // 使用 Locale.CHINA 代替 SIMPLIFIED_CHINESE
            Language.VIETNAMESE -> Locale("vi", "VN")  // 越南语
        }
        
        return updateResources(context, locale)
    }
    
    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Locale.getDefault()
        } else {
            Locale.getDefault()
        }
    }
    
    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
    
    fun getCurrentLanguage(context: Context): Language {
        val currentLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        
        return when (currentLocale.language) {
            "zh" -> Language.CHINESE
            "en" -> Language.ENGLISH
            "vi" -> Language.VIETNAMESE
            else -> Language.SYSTEM
        }
    }
}
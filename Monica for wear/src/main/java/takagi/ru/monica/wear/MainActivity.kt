package takagi.ru.monica.wear

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.util.Locale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.ContextWrapper
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import takagi.ru.monica.wear.data.PasswordDatabase
import takagi.ru.monica.wear.repository.TotpRepositoryImpl
import takagi.ru.monica.wear.security.WearSecurityManager
import takagi.ru.monica.wear.ui.screens.PinLockScreen
import takagi.ru.monica.wear.ui.screens.SettingsScreen
import takagi.ru.monica.wear.ui.screens.TotpPagerScreen
import takagi.ru.monica.wear.ui.theme.MonicaWearTheme
import takagi.ru.monica.wear.viewmodel.SettingsViewModel
import takagi.ru.monica.wear.viewmodel.TotpViewModel

/**
 * Monica-wear 主Activity
 * 手表版TOTP验证器应用
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var totpViewModel: TotpViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var securityManager: WearSecurityManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 应用语言设置
        applyLanguageSetting()
        
        // 设置 FLAG_SECURE 防止截屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        // 初始化安全管理器
        securityManager = WearSecurityManager(applicationContext)
        
        // 初始化数据库和Repository
        val database = PasswordDatabase.getDatabase(applicationContext)
        val repository = TotpRepositoryImpl(database.secureItemDao())
        
        // 初始化ViewModels
        totpViewModel = TotpViewModel(repository, applicationContext)
        settingsViewModel = SettingsViewModel(application)
        
        setContent {
            MonicaWearApp(
                totpViewModel = totpViewModel,
                settingsViewModel = settingsViewModel,
                securityManager = securityManager,
                context = this
            )
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 检查自动同步
        if (::settingsViewModel.isInitialized) {
            settingsViewModel.checkAutoSync()
        }
    }
    
    /**
     * 应用语言设置
     */
    private fun applyLanguageSetting() {
        val prefs = getSharedPreferences("monica_wear_prefs", Context.MODE_PRIVATE)
        val languageName = prefs.getString("app_language", null)
        
        if (languageName != null) {
            try {
                val language = takagi.ru.monica.wear.viewmodel.AppLanguage.valueOf(languageName)
                val localeTag = language.localeTag
                
                if (localeTag != null) {
                    // 设置应用语言
                    val locale = Locale(localeTag)
                    Locale.setDefault(locale)
                    val config = Configuration(resources.configuration)
                    config.setLocale(locale)
                    @Suppress("DEPRECATION")
                    resources.updateConfiguration(config, resources.displayMetrics)
                }
            } catch (e: Exception) {
                // 语言设置失败，使用默认
            }
        }
    }
}

/**
 * Monica Wear 应用主组件
 */
@Composable
fun MonicaWearApp(
    totpViewModel: TotpViewModel,
    settingsViewModel: SettingsViewModel,
    securityManager: WearSecurityManager,
    context: ComponentActivity
) {
    val currentColorScheme by settingsViewModel.currentColorScheme.collectAsState()
    val useOledBlack by settingsViewModel.useOledBlack.collectAsState()
    val currentLanguage by settingsViewModel.currentLanguage.collectAsState()
    
    // 创建带有当前语言设置的 Context
    val localizedContext = remember(currentLanguage) {
        createLocalizedContext(context, currentLanguage)
    }
    
    CompositionLocalProvider(LocalContext provides localizedContext) {
        MonicaWearTheme(
            colorScheme = currentColorScheme,
            useOledBlack = useOledBlack
        ) {
        var isAuthenticated by remember { mutableStateOf(false) }
        val isFirstTime = !securityManager.isLockSet()
        
        when {
            !isAuthenticated -> {
                // PIN码锁定屏幕
                PinLockScreen(
                    isFirstTime = isFirstTime,
                    onPinEntered = { pin ->
                        if (isFirstTime) {
                            // 首次设置PIN
                            securityManager.setPin(pin)
                            isAuthenticated = true
                            Toast.makeText(context, "PIN码设置成功", Toast.LENGTH_SHORT).show()
                        } else {
                            // 验证PIN
                            if (securityManager.verifyPin(pin)) {
                                isAuthenticated = true
                            } else {
                                Toast.makeText(context, "PIN码错误", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // 主应用界面
                val navController = rememberSwipeDismissableNavController()
            
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "totp_pager",
                    modifier = Modifier.fillMaxSize()
                ) {
                    // TOTP分页浏览屏幕
                    composable("totp_pager") {
                        TotpPagerScreen(
                            viewModel = totpViewModel,
                            settingsViewModel = settingsViewModel,
                            onShowSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }
                    
                    // 设置屏幕
                    composable("settings") {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            securityManager = securityManager,
                            onBack = {
                                navController.popBackStack()
                            },
                            onLanguageChanged = {
                                // 语言已通过 CompositionLocal 动态更新，无需重启
                            }
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * 创建带有指定语言的本地化 Context
 */
private fun createLocalizedContext(
    context: Context,
    language: takagi.ru.monica.wear.viewmodel.AppLanguage
): Context {
    val localeTag = language.localeTag
    
    return if (localeTag != null) {
        val locale = Locale(localeTag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.createConfigurationContext(config)
    } else {
        // 跟随系统语言
        context
    }
}

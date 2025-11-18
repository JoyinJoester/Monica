package takagi.ru.monica.wear

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.MaterialTheme
import takagi.ru.monica.wear.data.PasswordDatabase
import takagi.ru.monica.wear.repository.TotpRepositoryImpl
import takagi.ru.monica.wear.security.WearSecurityManager
import takagi.ru.monica.wear.ui.screens.PinLockScreen
import takagi.ru.monica.wear.ui.screens.SettingsScreen
import takagi.ru.monica.wear.ui.screens.TotpPagerScreen
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
    MaterialTheme {
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
                val navController = rememberNavController()
            
                NavHost(
                    navController = navController,
                    startDestination = "totp_pager",
                    modifier = Modifier.fillMaxSize()
                ) {
                    // TOTP分页浏览屏幕
                    composable("totp_pager") {
                        TotpPagerScreen(
                            viewModel = totpViewModel,
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
                            }
                        )
                    }
                }
            }
        }
    }
}

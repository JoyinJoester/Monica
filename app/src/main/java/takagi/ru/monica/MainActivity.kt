package takagi.ru.monica

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.navigation.Screen
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.screens.LoginScreen
import takagi.ru.monica.ui.SimpleMainScreen
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.ui.screens.ResetPasswordScreen
import takagi.ru.monica.ui.screens.ForgotPasswordScreen
import takagi.ru.monica.ui.screens.SecurityQuestionsSetupScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import takagi.ru.monica.ui.screens.SupportAuthorScreen
import takagi.ru.monica.utils.ScreenshotProtection
import takagi.ru.monica.ui.screens.SecurityQuestionsVerificationScreen
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.utils.LocaleHelper
import takagi.ru.monica.data.ThemeMode
import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    
    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            val settingsManager = SettingsManager(newBase)
            val language = runBlocking { 
                settingsManager.settingsFlow.first().language 
            }
            super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
        } else {
            super.attachBaseContext(newBase)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        // Initialize dependencies
        val database = PasswordDatabase.getDatabase(this)
        val repository = PasswordRepository(database.passwordEntryDao())
        val secureItemRepository = takagi.ru.monica.repository.SecureItemRepository(database.secureItemDao())
        val securityManager = SecurityManager(this)
        val settingsManager = SettingsManager(this)
        
        setContent {
            MonicaApp(repository, secureItemRepository, securityManager, settingsManager)
        }
    }
}

@Composable
fun MonicaApp(
    repository: PasswordRepository,
    secureItemRepository: takagi.ru.monica.repository.SecureItemRepository,
    securityManager: SecurityManager,
    settingsManager: SettingsManager
) {
    val navController = rememberNavController()
    val viewModel: PasswordViewModel = viewModel {
        PasswordViewModel(repository, securityManager)
    }
    val totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel = viewModel {
        takagi.ru.monica.viewmodel.TotpViewModel(secureItemRepository)
    }
    val bankCardViewModel: takagi.ru.monica.viewmodel.BankCardViewModel = viewModel {
        takagi.ru.monica.viewmodel.BankCardViewModel(secureItemRepository)
    }
    val documentViewModel: takagi.ru.monica.viewmodel.DocumentViewModel = viewModel {
        takagi.ru.monica.viewmodel.DocumentViewModel(secureItemRepository)
    }
    val dataExportImportViewModel: takagi.ru.monica.viewmodel.DataExportImportViewModel = viewModel {
        takagi.ru.monica.viewmodel.DataExportImportViewModel(secureItemRepository, repository, navController.context)
    }
    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(settingsManager)
    }
    
    val settings by settingsViewModel.settings.collectAsState()
    val isSystemInDarkTheme = isSystemInDarkTheme()
    
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    
    MonicaTheme(darkTheme = darkTheme) {
        // 应用防截屏保护
        ScreenshotProtection(enabled = settings.screenshotProtectionEnabled)
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
        MonicaContent(navController, viewModel, totpViewModel, bankCardViewModel, documentViewModel, dataExportImportViewModel, settingsViewModel, securityManager, repository, secureItemRepository)
        }
    }
}

@Composable
fun MonicaContent(
    navController: androidx.navigation.NavHostController,
    viewModel: PasswordViewModel,
    totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel,
    bankCardViewModel: takagi.ru.monica.viewmodel.BankCardViewModel,
    documentViewModel: takagi.ru.monica.viewmodel.DocumentViewModel,
    dataExportImportViewModel: takagi.ru.monica.viewmodel.DataExportImportViewModel,
    settingsViewModel: SettingsViewModel,
    securityManager: SecurityManager,
    repository: PasswordRepository,
    secureItemRepository: SecureItemRepository
) {
    
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    
    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated) Screen.Main.createRoute() else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel,
                settingsViewModel = settingsViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Main.createRoute()) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                }
            )
        }
        
        composable(
            route = Screen.Main.routePattern,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getInt("tab") ?: 0
            val scope = rememberCoroutineScope()
            SimpleMainScreen(
                passwordViewModel = viewModel,
                settingsViewModel = settingsViewModel,
                totpViewModel = totpViewModel,
                bankCardViewModel = bankCardViewModel,
                documentViewModel = documentViewModel,
                initialTab = tab,
                onNavigateToAddPassword = { passwordId ->
                    navController.navigate(Screen.AddEditPassword.createRoute(passwordId))
                },
                onNavigateToAddTotp = { totpId ->
                    navController.navigate(Screen.AddEditTotp.createRoute(totpId))
                },
                onNavigateToAddBankCard = { cardId ->
                    navController.navigate(Screen.AddEditBankCard.createRoute(cardId))
                },
                onNavigateToAddDocument = { documentId ->
                    navController.navigate(Screen.AddEditDocument.createRoute(documentId))
                },
                onNavigateToChangePassword = {
                    navController.navigate(Screen.ChangePassword.route)
                },
                onNavigateToSecurityQuestion = {
                    navController.navigate(Screen.SecurityQuestion.route)
                },
                onNavigateToSupportAuthor = {
                    navController.navigate(Screen.SupportAuthor.route)
                },
                onNavigateToExportData = {
                    navController.navigate(Screen.ExportData.route)
                },
                onNavigateToImportData = {
                    navController.navigate(Screen.ImportData.route)
                },
                onClearAllData = {
                    // 清空所有数据
                    android.util.Log.d("MainActivity", "onClearAllData called from SimpleMainScreen")
                    scope.launch {
                        try {
                            // 清空PasswordEntry表
                            val passwords = repository.getAllPasswordEntries().first()
                            android.util.Log.d("MainActivity", "Found ${passwords.size} passwords to delete")
                            passwords.forEach { repository.deletePasswordEntry(it) }
                            
                            // 清空SecureItem表
                            val items = secureItemRepository.getAllItems().first()
                            android.util.Log.d("MainActivity", "Found ${items.size} secure items to delete")
                            items.forEach { secureItemRepository.deleteItem(it) }
                            
                            // 显示成功消息
                            android.widget.Toast.makeText(
                                navController.context,
                                "所有数据已清空",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            android.util.Log.d("MainActivity", "All data cleared successfully")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to clear data", e)
                            android.widget.Toast.makeText(
                                navController.context,
                                "清空失败: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
        
        composable(Screen.AddEditPassword.route) { backStackEntry ->
            val passwordId = backStackEntry.arguments?.getString("passwordId")?.toLongOrNull() ?: -1L
            AddEditPasswordScreen(
                viewModel = viewModel,
                passwordId = if (passwordId == -1L) null else passwordId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.AddEditTotp.route) { backStackEntry ->
            val totpId = backStackEntry.arguments?.getString("totpId")?.toLongOrNull() ?: -1L
            
            var initialData by remember { mutableStateOf<takagi.ru.monica.data.model.TotpData?>(null) }
            var initialTitle by remember { mutableStateOf("") }
            var initialNotes by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(true) }
            
            // 从QR扫描获取的数据
            val qrResult = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.get<String>("qr_result")
            
            // 处理QR扫描结果
            LaunchedEffect(qrResult) {
                qrResult?.let { uri ->
                    val parseResult = takagi.ru.monica.util.TotpUriParser.parseUri(uri)
                    if (parseResult != null) {
                        initialData = parseResult.totpData
                        if (initialTitle.isBlank()) {
                            initialTitle = parseResult.label
                        }
                    }
                    // 清除结果
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.remove<String>("qr_result")
                }
            }
            
            LaunchedEffect(totpId) {
                if (totpId > 0) {
                    val item = totpViewModel.getTotpItemById(totpId)
                    if (item != null) {
                        initialTitle = item.title
                        initialNotes = item.notes
                        initialData = try {
                            kotlinx.serialization.json.Json.decodeFromString(item.itemData)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                isLoading = false
            }
            
            if (!isLoading) {
                takagi.ru.monica.ui.screens.AddEditTotpScreen(
                    totpId = if (totpId > 0) totpId else null,
                    initialData = initialData,
                    initialTitle = initialTitle,
                    initialNotes = initialNotes,
                    onSave = { title, notes, totpData ->
                        totpViewModel.saveTotpItem(
                            id = if (totpId > 0) totpId else null,
                            title = title,
                            notes = notes,
                            totpData = totpData
                        )
                        navController.popBackStack()
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onScanQrCode = {
                        navController.navigate(Screen.QrScanner.route)
                    }
                )
            }
        }
        
        composable(Screen.AddEditBankCard.route) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId")?.toLongOrNull() ?: -1L
            
            takagi.ru.monica.ui.screens.AddEditBankCardScreen(
                viewModel = bankCardViewModel,
                cardId = if (cardId > 0) cardId else null,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.AddEditDocument.route) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId")?.toLongOrNull() ?: -1L
            
            takagi.ru.monica.ui.screens.AddEditDocumentScreen(
                viewModel = documentViewModel,
                documentId = if (documentId > 0) documentId else null,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.QrScanner.route) {
            takagi.ru.monica.ui.screens.QrScannerScreen(
                onQrCodeScanned = { qrData ->
                    // 将QR码数据保存到前一个页面的savedStateHandle
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("qr_result", qrData)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // 导出数据
        composable(Screen.ExportData.route) {
            takagi.ru.monica.ui.screens.ExportDataScreen(
                onNavigateBack = {
                    navController.navigate(Screen.Main.createRoute(tab = 4)) {
                        popUpTo(Screen.Main.routePattern) { inclusive = true }
                    }
                },
                onExport = { uri ->
                    dataExportImportViewModel.exportData(uri)
                }
            )
        }
        
        // 导入数据
        composable(Screen.ImportData.route) {
            takagi.ru.monica.ui.screens.ImportDataScreen(
                onNavigateBack = {
                    navController.navigate(Screen.Main.createRoute(tab = 4)) {
                        popUpTo(Screen.Main.routePattern) { inclusive = true }
                    }
                },
                onImport = { uri ->
                    dataExportImportViewModel.importData(uri)
                }
            )
        }
        
        composable(Screen.ChangePassword.route) {
            takagi.ru.monica.ui.screens.ChangePasswordScreen(
                onNavigateBack = {
                    navController.navigate(Screen.Main.createRoute(tab = 4)) {
                        popUpTo(Screen.Main.routePattern) { inclusive = true }
                    }
                },
                onPasswordChanged = { currentPassword, newPassword ->
                    // TODO: 实现修改密码逻辑
                    viewModel.changePassword(currentPassword, newPassword)
                }
            )
        }
        
        composable(Screen.SecurityQuestion.route) {
            SecurityQuestionsSetupScreen(
                securityManager = securityManager,
                onNavigateBack = {
                    navController.navigate(Screen.Main.createRoute(tab = 4)) {
                        popUpTo(Screen.Main.routePattern) { inclusive = true }
                    }
                },
                onSetupComplete = {
                    navController.navigate(Screen.Main.createRoute(tab = 4)) {
                        popUpTo(Screen.Main.routePattern) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Settings.route) {
            val scope = rememberCoroutineScope()
            
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onResetPassword = {
                    navController.navigate(Screen.ResetPassword.route)
                },
                onSecurityQuestions = {
                    navController.navigate(Screen.SecurityQuestionsSetup.route)
                },
                onSupportAuthor = {
                    navController.navigate(Screen.SupportAuthor.route)
                },
                onClearAllData = {
                    // 清空所有数据
                    android.util.Log.d("MainActivity", "onClearAllData called")
                    scope.launch {
                        try {
                            // 清空PasswordEntry表
                            val passwords = repository.getAllPasswordEntries().first()
                            android.util.Log.d("MainActivity", "Found ${passwords.size} passwords to delete")
                            passwords.forEach { repository.deletePasswordEntry(it) }
                            
                            // 清空SecureItem表
                            val items = secureItemRepository.getAllItems().first()
                            android.util.Log.d("MainActivity", "Found ${items.size} secure items to delete")
                            items.forEach { secureItemRepository.deleteItem(it) }
                            
                            // 显示成功消息
                            android.widget.Toast.makeText(
                                navController.context,
                                "所有数据已清空",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            android.util.Log.d("MainActivity", "All data cleared successfully")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to clear data", e)
                            android.widget.Toast.makeText(
                                navController.context,
                                "清空失败: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
        
        composable(
            route = Screen.ResetPassword.route,
            arguments = listOf(navArgument("skipCurrentPassword") { 
                type = NavType.BoolType 
                defaultValue = false
            })
        ) { backStackEntry ->
            val skipCurrentPassword = backStackEntry.arguments?.getBoolean("skipCurrentPassword") ?: false
            ResetPasswordScreen(
                securityManager = securityManager,
                skipCurrentPassword = skipCurrentPassword,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onResetSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.ForgotPassword.route) {
            // Check if security questions are set and route accordingly
            LaunchedEffect(Unit) {
                if (securityManager.areSecurityQuestionsSet()) {
                    navController.navigate(Screen.SecurityQuestionsVerification.route) {
                        popUpTo(Screen.ForgotPassword.route) { inclusive = true }
                    }
                }
            }
            
            // Show full reset option if no security questions are set
            if (!securityManager.areSecurityQuestionsSet()) {
                ForgotPasswordScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onResetComplete = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
        
        composable(Screen.SecurityQuestionsSetup.route) {
            SecurityQuestionsSetupScreen(
                securityManager = securityManager,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSetupComplete = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.SecurityQuestionsVerification.route) {
            SecurityQuestionsVerificationScreen(
                securityManager = securityManager,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onVerificationSuccess = {
                    navController.navigate(Screen.ResetPassword.createRoute(skipCurrentPassword = true)) {
                        popUpTo(Screen.SecurityQuestionsVerification.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.SupportAuthor.route) {
            SupportAuthorScreen(
                onNavigateBack = {
                    navController.navigate(Screen.Main.createRoute(tab = 4)) {
                        popUpTo(Screen.Main.routePattern) { inclusive = true }
                    }
                }
            )
        }
    }
}
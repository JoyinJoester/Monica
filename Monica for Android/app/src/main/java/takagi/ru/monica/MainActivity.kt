package takagi.ru.monica

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordHistoryManager
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.navigation.Screen
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.SimpleMainScreen
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.ui.screens.AutofillSettingsScreen
import takagi.ru.monica.ui.screens.BankCardDetailScreen
import takagi.ru.monica.ui.screens.BottomNavSettingsScreen
import takagi.ru.monica.ui.screens.ChangePasswordScreen
import takagi.ru.monica.ui.screens.DocumentDetailScreen
import takagi.ru.monica.ui.screens.ExportDataScreen
import takagi.ru.monica.ui.screens.ForgotPasswordScreen
import takagi.ru.monica.ui.screens.ImportDataScreen
import takagi.ru.monica.ui.screens.LoginScreen
import takagi.ru.monica.ui.screens.QrScannerScreen
import takagi.ru.monica.ui.screens.ResetPasswordScreen
import takagi.ru.monica.ui.screens.SecurityAnalysisScreen
import takagi.ru.monica.ui.screens.SecurityQuestionsSetupScreen
import takagi.ru.monica.ui.screens.SecurityQuestionsVerificationScreen
import takagi.ru.monica.ui.screens.SettingsScreen
import takagi.ru.monica.ui.screens.PermissionManagementScreen
import takagi.ru.monica.ui.screens.MonicaPlusScreen
import takagi.ru.monica.ui.screens.PaymentScreen
import takagi.ru.monica.ui.screens.SupportAuthorScreen
import takagi.ru.monica.ui.screens.WebDavBackupScreen
import takagi.ru.monica.ui.screens.KeePassWebDavViewModel
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.LocaleHelper
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.GeneratorViewModel
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.viewmodel.SecurityAnalysisViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel
import takagi.ru.monica.viewmodel.TotpViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.utils.ScreenshotProtection
import takagi.ru.monica.ui.base.BaseMonicaActivity
import takagi.ru.monica.security.SessionManager
import androidx.compose.runtime.collectAsState
import takagi.ru.monica.util.FileOperationHelper
import takagi.ru.monica.util.PhotoPickerHelper
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.utils.WebDavHelper
import takagi.ru.monica.utils.AutoBackupManager

class MainActivity : BaseMonicaActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    // attachBaseContext 已由 BaseMonicaActivity 统一处理（语言、超时保护）

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // BaseMonicaActivity 已调用 enableEdgeToEdge()

        // 注意：enableEdgeToEdge() 已在基类调用，这里不再重复

        installSplashScreen()

        // Initialize dependencies
        val database = PasswordDatabase.getDatabase(this)
        val repository = PasswordRepository(
            database.passwordEntryDao(), 
            database.categoryDao(),
            database.bitwardenFolderDao(),
            database.secureItemDao(),
            database.passkeyDao()
        )
        val secureItemRepository = takagi.ru.monica.repository.SecureItemRepository(database.secureItemDao())
        val securityManager = SecurityManager(this)
        val settingsManager = SettingsManager(this)
        
        // Initialize OperationLogger for timeline tracking
        takagi.ru.monica.utils.OperationLogger.init(this)
        
        // Initialize auto backup if enabled
        initializeAutoBackup()

        // Initialize Notification Validator Service
        lifecycleScope.launch {
            settingsManager.settingsFlow
                .map {
                    Triple(
                        it.notificationValidatorEnabled,
                        it.notificationValidatorId,
                        it.notificationValidatorAutoMatch
                    )
                }
                .distinctUntilChanged()
                .collect { (enabled, id, autoMatch) ->
                    val intent = Intent(this@MainActivity, takagi.ru.monica.service.NotificationValidatorService::class.java)
                    // Start service if enabled and either a specific validator is chosen or auto-match is on.
                    if (enabled && (id != -1L || autoMatch)) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    } else {
                        stopService(intent)
                    }
                }
        }

        setContent {
            MonicaApp(repository, secureItemRepository, securityManager, settingsManager, database)
        }
    }
    
    /**
     * 初始化自动备份
     * 检查是否需要执行备份(每天首次打开时,如果距离上次备份超过24小时)
     */
    private fun initializeAutoBackup() {
        try {
            val webDavHelper = WebDavHelper(this)
            
            // 检查自动备份是否已启用
            if (webDavHelper.isAutoBackupEnabled()) {
                // 检查是否需要备份(距离上次备份超过24小时)
                if (webDavHelper.shouldAutoBackup()) {
                    Log.d("MainActivity", "Auto backup needed, triggering backup...")
                    
                    // 使用 WorkManager 在后台执行备份
                    val autoBackupManager = AutoBackupManager(this)
                    autoBackupManager.triggerBackupNow()
                } else {
                    Log.d("MainActivity", "Auto backup not needed (backed up within 24 hours)")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize auto backup: ${e.message}")
        }
    }

    // enableEdgeToEdge() 已由 BaseMonicaActivity 统一处理

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        // 处理照片选择器的权限请求结果
        if (PhotoPickerHelper.handlePermissionResult(requestCode, permissions, grantResults)) return
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // 处理文件导出/导入结果
        FileOperationHelper.handleExportResult(requestCode, resultCode, data)
        FileOperationHelper.handleImportResult(requestCode, resultCode, data)

        // 处理照片选择结果
        if (PhotoPickerHelper.handleCameraResult(requestCode, resultCode, data)) return
        if (PhotoPickerHelper.handleGalleryResult(requestCode, resultCode, data)) return
    }
}

@Composable
fun MonicaApp(
    repository: PasswordRepository,
    secureItemRepository: takagi.ru.monica.repository.SecureItemRepository,
    securityManager: SecurityManager,
    settingsManager: SettingsManager,
    database: PasswordDatabase
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    
    // 同步预读取 disablePasswordVerification 设置（避免异步加载时登录页面闪烁）
    // 使用超时保护，防止 ANR
    val initialDisablePasswordVerification = remember {
        try {
            runBlocking {
                withTimeout(200) {
                    try {
                        settingsManager.settingsFlow.first().disablePasswordVerification
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // 创建权限共享 launcher
    var pendingSupportPermissionCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val sharedSupportPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingSupportPermissionCallback?.invoke(granted)
        pendingSupportPermissionCallback = null
    }

    val viewModel: PasswordViewModel = viewModel {
        val customFieldRepository = takagi.ru.monica.repository.CustomFieldRepository(database.customFieldDao())
        PasswordViewModel(
            repository,
            securityManager,
            secureItemRepository,
            customFieldRepository,
            navController.context,
            database.localKeePassDatabaseDao()
        )
    }
    val totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel = viewModel {
        takagi.ru.monica.viewmodel.TotpViewModel(
            secureItemRepository,
            repository,
            navController.context,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }
    val bankCardViewModel: takagi.ru.monica.viewmodel.BankCardViewModel = viewModel {
        takagi.ru.monica.viewmodel.BankCardViewModel(
            secureItemRepository,
            navController.context,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }
    val documentViewModel: takagi.ru.monica.viewmodel.DocumentViewModel = viewModel {
        takagi.ru.monica.viewmodel.DocumentViewModel(
            secureItemRepository,
            navController.context,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }
    val dataExportImportViewModel: takagi.ru.monica.viewmodel.DataExportImportViewModel = viewModel {
        takagi.ru.monica.viewmodel.DataExportImportViewModel(secureItemRepository, repository, navController.context)
    }

    val passwordHistoryManager = remember { PasswordHistoryManager(navController.context) }
    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(settingsManager, secureItemRepository)
    }
    val generatorViewModel: GeneratorViewModel = viewModel {
        GeneratorViewModel()
    }
    val noteViewModel: takagi.ru.monica.viewmodel.NoteViewModel = viewModel {
        takagi.ru.monica.viewmodel.NoteViewModel(
            secureItemRepository,
            navController.context,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }
    
    // Passkey 通行密钥
    val passkeyRepository = remember { takagi.ru.monica.repository.PasskeyRepository(database.passkeyDao()) }
    val passkeyViewModel: takagi.ru.monica.viewmodel.PasskeyViewModel = viewModel {
        takagi.ru.monica.viewmodel.PasskeyViewModel(passkeyRepository)
    }
    
    // KeePass KDBX 导出/导入
    val keePassViewModel = remember { KeePassWebDavViewModel() }
    
    // 本地 KeePass 数据库管理
    val localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel = viewModel {
        takagi.ru.monica.viewmodel.LocalKeePassViewModel(
            context.applicationContext as android.app.Application,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }

    val settings by settingsViewModel.settings.collectAsState()
    val isSystemInDarkTheme = isSystemInDarkTheme()

    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MonicaTheme(
        darkTheme = darkTheme, 
        colorScheme = settings.colorScheme,
        customPrimaryColor = settings.customPrimaryColor,
        customSecondaryColor = settings.customSecondaryColor,
        customTertiaryColor = settings.customTertiaryColor,
        customNeutralColor = settings.customNeutralColor,
        customNeutralVariantColor = settings.customNeutralVariantColor
    ) {
        // 应用防截屏保护
        ScreenshotProtection(enabled = settings.screenshotProtectionEnabled)

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MonicaContent(
                navController = navController,
                viewModel = viewModel,
                totpViewModel = totpViewModel,
                bankCardViewModel = bankCardViewModel,
                documentViewModel = documentViewModel,
                dataExportImportViewModel = dataExportImportViewModel,
                settingsViewModel = settingsViewModel,
                generatorViewModel = generatorViewModel,
                noteViewModel = noteViewModel,
                passkeyViewModel = passkeyViewModel,
                keePassViewModel = keePassViewModel,
                localKeePassViewModel = localKeePassViewModel,
                securityManager = securityManager,
                repository = repository,
                secureItemRepository = secureItemRepository,
                passwordHistoryManager = passwordHistoryManager,
                initialDisablePasswordVerification = initialDisablePasswordVerification,
                onPermissionRequested = { permission, callback ->
                    pendingSupportPermissionCallback = callback
                    sharedSupportPermissionLauncher.launch(permission)
                }
            )
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
    generatorViewModel: GeneratorViewModel,
    noteViewModel: takagi.ru.monica.viewmodel.NoteViewModel,
    passkeyViewModel: takagi.ru.monica.viewmodel.PasskeyViewModel,
    keePassViewModel: KeePassWebDavViewModel,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    securityManager: SecurityManager,
    repository: PasswordRepository,
    secureItemRepository: SecureItemRepository,
    passwordHistoryManager: PasswordHistoryManager,
    initialDisablePasswordVerification: Boolean = false,
    onPermissionRequested: (String, (Boolean) -> Unit) -> Unit
) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastBackgroundTimestamp by remember { mutableStateOf<Long?>(null) }
    
    // 追踪设置是否已加载完成
    var settingsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 使用 first() 获取第一个实际值后标记为已加载
        settingsViewModel.settings.first()
        settingsLoaded = true
    }
    
    // 检查是否是首次使用（还没设置过主密码）
    val isFirstTime = remember { !viewModel.isMasterPasswordSet() }
    // 当 disablePasswordVerification 开启且非首次使用时，自动跳过登录
    // 使用预读取的值（initialDisablePasswordVerification）来避免闪烁
    val disablePasswordVerification = if (settingsLoaded) settings.disablePasswordVerification else initialDisablePasswordVerification
    val shouldSkipLogin = disablePasswordVerification && !isFirstTime
    
    // 使用 rememberUpdatedState 确保生命周期观察者闭包始终访问最新值
    val currentIsAuthenticated by rememberUpdatedState(isAuthenticated)
    val currentSettings by rememberUpdatedState(settings)
    val currentIsFirstTime by rememberUpdatedState(isFirstTime)

    // Auto-lock logic: only use lifecycleOwner as key to prevent observer recreation
    // The settings and auth state are captured by closure and always reflect latest values
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Only record timestamp when user is authenticated
                    // 使用 currentIsAuthenticated 确保访问最新值
                    if (currentIsAuthenticated) {
                        lastBackgroundTimestamp = System.currentTimeMillis()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // 如果已禁用密码验证，跳过自动锁定
                    // 使用 currentSettings 和 currentIsFirstTime 确保访问最新值
                    if (currentSettings.disablePasswordVerification && !currentIsFirstTime) {
                        lastBackgroundTimestamp = null
                        return@LifecycleEventObserver
                    }
                    
                    val minutes = currentSettings.autoLockMinutes
                    val timeoutMs = when {
                        minutes == -1 -> null // Never auto-lock
                        minutes <= 0 -> 0L // Immediate lock after background
                        else -> minutes.toLong() * 60_000L
                    }

                    val lastBackground = lastBackgroundTimestamp
                    if (
                        currentIsAuthenticated &&
                        timeoutMs != null &&
                        lastBackground != null &&
                        System.currentTimeMillis() - lastBackground >= timeoutMs
                    ) {
                        viewModel.logout()
                        lastBackgroundTimestamp = null
                        navController.navigate(Screen.Login.route) {
                            launchSingleTop = true
                            popUpTo(0) { inclusive = true }
                        }
                    } else if (timeoutMs == null) {
                        lastBackgroundTimestamp = null
                    }
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 当 shouldSkipLogin 为 true 且设置已加载完成时，自动设置认证状态
    LaunchedEffect(shouldSkipLogin, settingsLoaded) {
        if (settingsLoaded && shouldSkipLogin && !isAuthenticated) {
            // 自动认证，跳过登录页面
            viewModel.authenticate("")
        }
    }
    
    // 使用固定的 startDestination 避免竞态条件
    // 认证状态变化时通过 LaunchedEffect 处理导航
    val fixedStartDestination = remember {
        if (initialDisablePasswordVerification && !isFirstTime) Screen.Main.createRoute() else Screen.Login.route
    }
    
    // 当认证状态变化时处理导航
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute == Screen.Login.route) {
                navController.navigate(Screen.Main.createRoute()) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
        }
    }

    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    androidx.compose.animation.SharedTransitionLayout {
        androidx.compose.runtime.CompositionLocalProvider(
            takagi.ru.monica.ui.LocalSharedTransitionScope provides this,
            takagi.ru.monica.ui.LocalReduceAnimations provides settings.reduceAnimations
        ) {
            NavHost(
                navController = navController,
                startDestination = fixedStartDestination
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

            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            // V1 经典本地密码库界面
            SimpleMainScreen(
                passwordViewModel = viewModel,
                settingsViewModel = settingsViewModel,
                totpViewModel = totpViewModel,
                bankCardViewModel = bankCardViewModel,
                documentViewModel = documentViewModel,
                generatorViewModel = generatorViewModel,
                noteViewModel = noteViewModel,
                passkeyViewModel = passkeyViewModel,
                localKeePassViewModel = localKeePassViewModel,
                securityManager = securityManager,
                onNavigateToAddPassword = { passwordId ->
                    navController.navigate(Screen.AddEditPassword.createRoute(passwordId))
                },
                onNavigateToAddTotp = { totpId ->
                    navController.navigate(Screen.AddEditTotp.createRoute(totpId))
                },
                onNavigateToQuickTotpScan = {
                    navController.navigate(Screen.QuickTotpScan.route)
                },
                onNavigateToAddBankCard = { cardId ->
                    navController.navigate(Screen.AddEditBankCard.createRoute(cardId))
                },
                onNavigateToAddDocument = { documentId ->
                    navController.navigate(Screen.AddEditDocument.createRoute(documentId))
                },
                onNavigateToAddNote = { noteId ->
                    navController.navigate(Screen.AddEditNote.createRoute(noteId))
                },
                onNavigateToPasswordDetail = { passwordId ->
                    navController.navigate(Screen.PasswordDetail.createRoute(passwordId))
                },
                onNavigateToBankCardDetail = { cardId ->
                    navController.navigate("bank_card_detail/$cardId")
                },
                onNavigateToDocumentDetail = { documentId ->
                    navController.navigate(Screen.DocumentDetail.createRoute(documentId))
                },
                onNavigateToChangePassword = {
                    navController.navigate(Screen.ChangePassword.route)
                },
                onNavigateToSecurityQuestion = {
                    navController.navigate(Screen.SecurityQuestion.route)
                },
                onNavigateToSyncBackup = {
                    navController.navigate(Screen.SyncBackup.route)
                },
                onNavigateToAutofill = {
                    navController.navigate(Screen.AutofillSettings.route)
                },
                onNavigateToPasskeySettings = {
                    navController.navigate(Screen.PasskeySettings.route)
                },
                onNavigateToBottomNavSettings = {
                    navController.navigate(Screen.BottomNavSettings.route)
                },
                onNavigateToColorScheme = {
                    navController.navigate(Screen.ColorSchemeSelection.route)
                },
                onSecurityAnalysis = {
                    navController.navigate(Screen.SecurityAnalysis.route)
                },
                onNavigateToDeveloperSettings = {
                    navController.navigate(Screen.DeveloperSettings.route)
                },
                onNavigateToPermissionManagement = {
                    navController.navigate(Screen.PermissionManagement.route)
                },
                onNavigateToMonicaPlus = {
                    android.util.Log.d("MainActivity", "Navigating to Monica Plus"); navController.navigate(Screen.MonicaPlus.route)
                },
                onNavigateToExtensions = {
                    navController.navigate(Screen.Extensions.route)
                },
                onNavigateToPageCustomization = {
                    navController.navigate(Screen.PageAdjustmentCustomization.route)
                },
                onClearAllData = { clearPasswords: Boolean, clearTotp: Boolean, clearNotes: Boolean, clearDocuments: Boolean, clearBankCards: Boolean, clearGeneratorHistory: Boolean ->
                    // 清空所有数据
                    android.util.Log.d(
                        "MainActivity",
                        "onClearAllData called with options: passwords=$clearPasswords, totp=$clearTotp, notes=$clearNotes, documents=$clearDocuments, bankCards=$clearBankCards, generatorHistory=$clearGeneratorHistory"
                    )
                    scope.launch {
                        try {
                            // 根据选项清空PasswordEntry表
                            if (clearPasswords) {
                                val passwords = repository.getAllPasswordEntries().first()
                                android.util.Log.d("MainActivity", "Found ${passwords.size} passwords to delete")
                                passwords.forEach { repository.deletePasswordEntry(it) }
                            }
                            
                            // 根据选项清空SecureItem表
                            if (clearTotp || clearDocuments || clearBankCards || clearNotes) {
                                val items = secureItemRepository.getAllItems().first()
                                android.util.Log.d("MainActivity", "Found ${items.size} secure items to delete")
                                items.forEach { item ->
                                    val shouldDelete = when (item.itemType) {
                                        ItemType.TOTP -> clearTotp
                                        ItemType.DOCUMENT -> clearDocuments
                                        ItemType.BANK_CARD -> clearBankCards
                                        ItemType.NOTE -> clearNotes
                                        else -> false
                                    }
                                    if (shouldDelete) {
                                        secureItemRepository.deleteItem(item)
                                    }
                                }
                            }

                            if (clearGeneratorHistory) {
                                passwordHistoryManager.clearHistory()
                            }
                            
                            // 显示成功消息
                            android.widget.Toast.makeText(
                                navController.context,
                                "数据已清空",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            android.util.Log.d("MainActivity", "All selected data cleared successfully")
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to clear data", e)
                            android.widget.Toast.makeText(
                                navController.context,
                                "清空失败: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                initialTab = tab
            )
            } // end CompositionLocalProvider
        }

        composable(Screen.AddEditPassword.route) { backStackEntry ->
            val passwordId = backStackEntry.arguments?.getString("passwordId")?.toLongOrNull() ?: -1L
            AddEditPasswordScreen(
                viewModel = viewModel,
                totpViewModel = totpViewModel,
                bankCardViewModel = bankCardViewModel,
                localKeePassViewModel = localKeePassViewModel,
                passwordId = if (passwordId == -1L) null else passwordId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AddEditTotp.route) { backStackEntry ->
            val totpId = backStackEntry.arguments?.getString("totpId")?.toLongOrNull() ?: -1L
            val currentTotpFilter by totpViewModel.categoryFilter.collectAsState()
            val context = LocalContext.current

            var initialData by remember { mutableStateOf<takagi.ru.monica.data.model.TotpData?>(null) }
            var initialTitle by remember { mutableStateOf("") }
            var initialNotes by remember { mutableStateOf("") }
            var initialBitwardenVaultId by remember { mutableStateOf<Long?>(null) }
            var initialBitwardenFolderId by remember { mutableStateOf<String?>(null) }
            var isLoading by remember { mutableStateOf(true) }

            // 从QR扫描获取的数据
            val qrResult = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.get<String>("qr_result")

            // 处理QR扫描结果
            LaunchedEffect(qrResult) {
                qrResult?.let { uri ->
                    when (val scanResult = takagi.ru.monica.util.TotpUriParser.parseScannedContent(uri)) {
                        is takagi.ru.monica.util.TotpScanParseResult.Single -> {
                            initialData = scanResult.item.totpData
                            if (initialTitle.isBlank()) {
                                initialTitle = scanResult.item.label
                            }
                        }
                        is takagi.ru.monica.util.TotpScanParseResult.Multiple -> {
                            scanResult.items.firstOrNull()?.let { first ->
                                initialData = first.totpData
                                if (initialTitle.isBlank()) {
                                    initialTitle = first.label
                                }
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.qr_migration_multiple_fill_first, scanResult.items.size),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        takagi.ru.monica.util.TotpScanParseResult.UnsupportedPhoneFactor -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.qr_phonefactor_not_supported),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        takagi.ru.monica.util.TotpScanParseResult.InvalidFormat -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.qr_invalid_authenticator),
                                Toast.LENGTH_SHORT
                            ).show()
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
                        initialBitwardenVaultId = item.bitwardenVaultId
                        initialBitwardenFolderId = item.bitwardenFolderId
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
                val totpCategories by totpViewModel.categories.collectAsState()
                data class TotpStorageDefaults(
                    val categoryId: Long? = null,
                    val keepassDatabaseId: Long? = null,
                    val bitwardenVaultId: Long? = null,
                    val bitwardenFolderId: String? = null
                )
                val filterDefaults = remember(currentTotpFilter) {
                    when (val filter = currentTotpFilter) {
                        is takagi.ru.monica.viewmodel.TotpCategoryFilter.Custom -> {
                            TotpStorageDefaults(categoryId = filter.categoryId)
                        }
                        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabase -> {
                            TotpStorageDefaults(keepassDatabaseId = filter.databaseId)
                        }
                        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseStarred -> {
                            TotpStorageDefaults(keepassDatabaseId = filter.databaseId)
                        }
                        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassDatabaseUncategorized -> {
                            TotpStorageDefaults(keepassDatabaseId = filter.databaseId)
                        }
                        is takagi.ru.monica.viewmodel.TotpCategoryFilter.KeePassGroupFilter -> {
                            TotpStorageDefaults(keepassDatabaseId = filter.databaseId)
                        }
                        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVault -> {
                            TotpStorageDefaults(bitwardenVaultId = filter.vaultId)
                        }
                        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultStarred -> {
                            TotpStorageDefaults(bitwardenVaultId = filter.vaultId)
                        }
                        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenVaultUncategorized -> {
                            TotpStorageDefaults(bitwardenVaultId = filter.vaultId)
                        }
                        is takagi.ru.monica.viewmodel.TotpCategoryFilter.BitwardenFolderFilter -> {
                            TotpStorageDefaults(
                                bitwardenVaultId = filter.vaultId,
                                bitwardenFolderId = filter.folderId
                            )
                        }
                        else -> TotpStorageDefaults()
                    }
                }
                val initialCategoryId = initialData?.categoryId ?: filterDefaults.categoryId
                val initialKeePassDatabaseId = initialData?.keepassDatabaseId ?: filterDefaults.keepassDatabaseId
                val initialVaultId = initialBitwardenVaultId ?: filterDefaults.bitwardenVaultId
                val initialFolderId = initialBitwardenFolderId ?: filterDefaults.bitwardenFolderId
                takagi.ru.monica.ui.screens.AddEditTotpScreen(
                    totpId = if (totpId > 0) totpId else null,
                    initialData = initialData,
                    initialTitle = initialTitle,
                    initialNotes = initialNotes,
                    initialCategoryId = initialCategoryId,
                    initialKeePassDatabaseId = initialKeePassDatabaseId,
                    initialBitwardenVaultId = initialVaultId,
                    initialBitwardenFolderId = initialFolderId,
                    categories = totpCategories,
                    passwordViewModel = viewModel,
                    localKeePassViewModel = localKeePassViewModel,
                    onSave = { title, notes, totpData, categoryId, keepassDatabaseId, bitwardenVaultId, bitwardenFolderId ->
                        totpViewModel.saveTotpItem(
                            id = if (totpId > 0) totpId else null,
                            title = title,
                            notes = notes,
                            totpData = totpData,
                            categoryId = categoryId,
                            keepassDatabaseId = keepassDatabaseId,
                            bitwardenVaultId = bitwardenVaultId,
                            bitwardenFolderId = bitwardenFolderId
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

        composable("bank_card_detail/{cardId}") { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId")?.toLongOrNull() ?: -1L

            if (cardId > 0) {
                takagi.ru.monica.ui.screens.BankCardDetailScreen(
                    viewModel = bankCardViewModel,
                    cardId = cardId,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onEditCard = { id ->
                        navController.navigate(Screen.AddEditBankCard.createRoute(id))
                    }
                )
            }
        }

        composable(Screen.AddEditNote.route) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull() ?: -1L

            takagi.ru.monica.ui.screens.AddEditNoteScreen(
                noteId = noteId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = noteViewModel
            )
        }

        composable(Screen.DocumentDetail.route) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId")?.toLongOrNull() ?: -1L

            if (documentId > 0) {
                takagi.ru.monica.ui.screens.DocumentDetailScreen(
                    viewModel = documentViewModel,
                    documentId = documentId,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onEditDocument = { id ->
                        navController.navigate(Screen.AddEditDocument.createRoute(id))
                    }
                )
            }
        }

        composable(Screen.PasswordDetail.route) { backStackEntry ->
            val passwordId = backStackEntry.arguments?.getString("passwordId")?.toLongOrNull() ?: -1L

            if (passwordId > 0) {
                androidx.compose.runtime.CompositionLocalProvider(
                    takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
                ) {
                    takagi.ru.monica.ui.screens.PasswordDetailScreen(
                        viewModel = viewModel,
                        passkeyViewModel = passkeyViewModel,
                        passwordId = passwordId,
                        disablePasswordVerification = settings.disablePasswordVerification,
                        biometricEnabled = settings.biometricEnabled,
                        iconCardsEnabled = settings.iconCardsEnabled && settings.passwordPageIconEnabled,
                        unmatchedIconHandlingStrategy = settings.unmatchedIconHandlingStrategy,
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onEditPassword = { id ->
                            navController.navigate(Screen.AddEditPassword.createRoute(id))
                        }
                    )
                }
            }
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

        // 快速扫码添加验证器 - 扫描后直接保存到数据库
        composable(Screen.QuickTotpScan.route) {
            val context = LocalContext.current
            takagi.ru.monica.ui.screens.QrScannerScreen(
                onQrCodeScanned = { qrData ->
                    fun resolveTitle(item: takagi.ru.monica.util.TotpParseResult): String {
                        return item.label.takeIf { it.isNotBlank() }
                            ?: item.totpData.issuer.takeIf { it.isNotBlank() }
                            ?: item.totpData.accountName.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.untitled)
                    }

                    when (val scanResult = takagi.ru.monica.util.TotpUriParser.parseScannedContent(qrData)) {
                        is takagi.ru.monica.util.TotpScanParseResult.Single -> {
                            val title = resolveTitle(scanResult.item)
                            val existingItem = totpViewModel.findTotpBySecret(scanResult.item.totpData.secret)
                            if (existingItem != null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.qr_authenticator_duplicate, existingItem.title),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                totpViewModel.saveTotpItem(
                                    id = null,
                                    title = title,
                                    notes = "",
                                    totpData = scanResult.item.totpData,
                                    isFavorite = false
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.qr_authenticator_added, title),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        is takagi.ru.monica.util.TotpScanParseResult.Multiple -> {
                            var addedCount = 0
                            var duplicateCount = 0
                            var invalidCount = 0
                            val batchSecretSet = mutableSetOf<String>()

                            scanResult.items.forEach { item ->
                                val secret = item.totpData.secret.trim()
                                if (secret.isBlank()) {
                                    invalidCount++
                                    return@forEach
                                }

                                if (!batchSecretSet.add(secret)) {
                                    duplicateCount++
                                    return@forEach
                                }

                                val existingItem = totpViewModel.findTotpBySecret(secret)
                                if (existingItem != null) {
                                    duplicateCount++
                                    return@forEach
                                }

                                val title = resolveTitle(item)
                                totpViewModel.saveTotpItem(
                                    id = null,
                                    title = title,
                                    notes = "",
                                    totpData = item.totpData,
                                    isFavorite = false
                                )
                                addedCount++
                            }

                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.qr_authenticator_migration_result,
                                    addedCount,
                                    duplicateCount,
                                    invalidCount
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        takagi.ru.monica.util.TotpScanParseResult.UnsupportedPhoneFactor -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.qr_phonefactor_not_supported),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        takagi.ru.monica.util.TotpScanParseResult.InvalidFormat -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.qr_invalid_authenticator),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
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
                    navController.popBackStack()
                },
                onExportAll = { uri ->
                    dataExportImportViewModel.exportData(uri)
                },
                onExportPasswords = { uri ->
                    dataExportImportViewModel.exportPasswords(uri)
                },
                onExportTotp = { uri, format, password ->
                    dataExportImportViewModel.exportTotp(uri, format, password)
                },
                onExportBankCardsAndDocs = { uri ->
                    dataExportImportViewModel.exportBankCardsAndDocuments(uri)
                },
                onExportNotes = { uri ->
                    dataExportImportViewModel.exportNotes(uri)
                },
                onExportZip = { uri, preferences ->
                    dataExportImportViewModel.exportZipBackup(uri, preferences)
                },
                onExportKdbx = { uri, password ->
                    val ctx = navController.context
                    val outputStream = ctx.contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        val result = keePassViewModel.exportToLocalKdbx(ctx, outputStream, password)
                        result.fold(
                            onSuccess = { count: Int ->
                                Result.success("成功导出 $count 条记录到 KDBX 文件")
                            },
                            onFailure = { error: Throwable ->
                                Result.failure(error)
                            }
                        )
                    } else {
                        Result.failure(Exception("无法打开文件"))
                    }
                }
            )
        }

        // 导入数据
        composable(Screen.ImportData.route) {
            takagi.ru.monica.ui.screens.ImportDataScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onImport = { uri ->
                    dataExportImportViewModel.importData(uri)
                },
                onImportKeePassCsv = { uri ->
                    dataExportImportViewModel.importKeePassCsv(uri)
                },
                onImportBitwardenCsv = { uri ->
                    dataExportImportViewModel.importBitwardenCsv(uri)
                },
                onImportAegis = { uri ->
                    dataExportImportViewModel.importAegisJson(uri)
                },
                onImportEncryptedAegis = { uri, password ->
                    dataExportImportViewModel.importEncryptedAegisJson(uri, password)
                },
                onImportSteamMaFile = { uri ->
                    dataExportImportViewModel.importSteamMaFile(uri)
                },
                onBeginSteamLoginImport = { userName, password, customName ->
                    dataExportImportViewModel.beginSteamLoginImport(userName, password, customName)
                },
                onSubmitSteamLoginImportCode = { pendingSessionId, code, confirmationType, customName ->
                    dataExportImportViewModel.submitSteamLoginImportCode(
                        pendingSessionId = pendingSessionId,
                        code = code,
                        confirmationType = confirmationType,
                        customName = customName
                    )
                },
                onClearSteamLoginImportSession = { sessionId ->
                    dataExportImportViewModel.clearSteamLoginImportSession(sessionId)
                },
                onImportZip = { uri, password ->
                    dataExportImportViewModel.importZipBackup(uri, password)
                },
                onImportStratum = { uri, password ->
                    dataExportImportViewModel.importStratum(uri, password)
                },
                onImportKdbx = { uri, password ->
                    val ctx = navController.context
                    val result = keePassViewModel.importFromLocalKdbx(ctx, uri, password)
                    result.fold(
                        onSuccess = { count: Int -> Result.success(count) },
                        onFailure = { error: Throwable -> Result.failure(error) }
                    )
                }
            )
        }

        composable(Screen.ChangePassword.route) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
                takagi.ru.monica.ui.screens.ChangePasswordScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onPasswordChanged = { currentPassword, newPassword ->
                        // TODO: 实现修改密码逻辑
                        viewModel.changePassword(currentPassword, newPassword)
                    }
                )
            }
        }

        composable(Screen.SecurityQuestion.route) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
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
        }

        composable(Screen.Settings.route) {
            val scope = rememberCoroutineScope()

            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
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
                onNavigateToSyncBackup = {
                    navController.navigate(Screen.SyncBackup.route)
                },
                onNavigateToAutofill = {
                    navController.navigate(Screen.AutofillSettings.route)
                },
                onNavigateToPasskeySettings = {
                    navController.navigate(Screen.PasskeySettings.route)
                },
                onNavigateToBottomNavSettings = {
                    navController.navigate(Screen.BottomNavSettings.route)
                },
                onNavigateToColorScheme = {
                    navController.navigate(Screen.ColorSchemeSelection.route)
                },
                onSecurityAnalysis = {
                    navController.navigate(Screen.SecurityAnalysis.route)
                },
                onNavigateToDeveloperSettings = {
                    navController.navigate(Screen.DeveloperSettings.route)
                },
                onNavigateToPermissionManagement = {
                    navController.navigate(Screen.PermissionManagement.route)
                },
                onNavigateToMonicaPlus = {
                    android.util.Log.d("MainActivity", "Navigating to Monica Plus"); navController.navigate(Screen.MonicaPlus.route)
                },
                onNavigateToExtensions = {
                    navController.navigate(Screen.Extensions.route)
                },
                onNavigateToPageCustomization = {
                    navController.navigate(Screen.PageAdjustmentCustomization.route)
                },
                onClearAllData = { clearPasswords: Boolean, clearTotp: Boolean, clearNotes: Boolean, clearDocuments: Boolean, clearBankCards: Boolean, clearGeneratorHistory: Boolean ->
                    // 清空所有数据
                    android.util.Log.d(
                        "MainActivity",
                        "onClearAllData called with options: passwords=$clearPasswords, totp=$clearTotp, notes=$clearNotes, documents=$clearDocuments, bankCards=$clearBankCards, generatorHistory=$clearGeneratorHistory"
                    )
                    scope.launch {
                        try {
                            // 根据选项清空PasswordEntry表
                            if (clearPasswords) {
                                val passwords = repository.getAllPasswordEntries().first()
                                android.util.Log.d("MainActivity", "Found ${passwords.size} passwords to delete")
                                passwords.forEach { repository.deletePasswordEntry(it) }
                            }
                            
                            // 根据选项清空SecureItem表
                            if (clearTotp || clearDocuments || clearBankCards || clearNotes) {
                                val items = secureItemRepository.getAllItems().first()
                                android.util.Log.d("MainActivity", "Found ${items.size} secure items to delete")
                                items.forEach { item ->
                                    val shouldDelete = when (item.itemType) {
                                        ItemType.TOTP -> clearTotp
                                        ItemType.DOCUMENT -> clearDocuments
                                        ItemType.BANK_CARD -> clearBankCards
                                        ItemType.NOTE -> clearNotes
                                        else -> false
                                    }
                                    if (shouldDelete) {
                                        secureItemRepository.deleteItem(item)
                                    }
                                }
                            }

                            if (clearGeneratorHistory) {
                                passwordHistoryManager.clearHistory()
                            }
                            
                            // 显示成功消息
                            android.widget.Toast.makeText(
                                navController.context,
                                "数据已清空",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            android.util.Log.d("MainActivity", "All selected data cleared successfully")
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
        }

        composable(
            route = Screen.BottomNavSettings.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            BottomNavSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
            }
        }

        composable(
            route = Screen.ResetPassword.route,
            arguments = listOf(navArgument("skipCurrentPassword") {
                type = NavType.BoolType
                defaultValue = false
            }),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) { backStackEntry ->
            val skipCurrentPassword = backStackEntry.arguments?.getBoolean("skipCurrentPassword") ?: false
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
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

        composable(
            route = Screen.SecurityQuestionsSetup.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
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
                    navController.popBackStack()
                },
                onRequestPermission = onPermissionRequested
            )
        }

        composable(Screen.WebDavBackup.route) {
            WebDavBackupScreen(
                passwordRepository = repository,
                secureItemRepository = secureItemRepository,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.AutofillSettings.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
                takagi.ru.monica.ui.screens.AutofillSettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(
            route = Screen.PasskeySettings.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
                takagi.ru.monica.ui.screens.PasskeySettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(
            route = Screen.SecurityAnalysis.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            val securityViewModel: takagi.ru.monica.viewmodel.SecurityAnalysisViewModel = viewModel {
                takagi.ru.monica.viewmodel.SecurityAnalysisViewModel(repository)
            }
            val analysisData by securityViewModel.analysisData.collectAsState()

            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            takagi.ru.monica.ui.screens.SecurityAnalysisScreen(
                analysisData = analysisData,
                onStartAnalysis = {
                    securityViewModel.performSecurityAnalysis()
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPassword = { passwordId ->
                    navController.navigate(Screen.AddEditPassword.createRoute(passwordId))
                }
            )
            }
        }
        
        composable(
            route = Screen.DeveloperSettings.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            takagi.ru.monica.ui.screens.DeveloperSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
            }
        }
        
        composable(
            route = Screen.Extensions.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            val settings by settingsViewModel.settings.collectAsState()
            val totpItems by totpViewModel.totpItems.collectAsState()
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            takagi.ru.monica.ui.screens.ExtensionsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                isPlusActivated = settings.isPlusActivated,
                validatorVibrationEnabled = settings.validatorVibrationEnabled,
                onValidatorVibrationChange = { enabled ->
                    settingsViewModel.updateValidatorVibrationEnabled(enabled)
                },
                copyNextCodeWhenExpiring = settings.copyNextCodeWhenExpiring,
                onCopyNextCodeWhenExpiringChange = { enabled ->
                    settingsViewModel.updateCopyNextCodeWhenExpiring(enabled)
                },
                smartDeduplicationEnabled = settings.smartDeduplicationEnabled,
                onSmartDeduplicationEnabledChange = { enabled ->
                    settingsViewModel.updateSmartDeduplicationEnabled(enabled)
                },
                passwordCardDisplayMode = settings.passwordCardDisplayMode,
                onPasswordCardDisplayModeChange = { mode ->
                    settingsViewModel.updatePasswordCardDisplayMode(mode)
                },
                validatorUnifiedProgressBar = settings.validatorUnifiedProgressBar,
                onValidatorUnifiedProgressBarChange = { mode ->
                    settingsViewModel.updateValidatorUnifiedProgressBar(mode)
                },
                // 通知栏验证器参数
                notificationValidatorEnabled = settings.notificationValidatorEnabled,
                notificationValidatorAutoMatch = settings.notificationValidatorAutoMatch,
                notificationValidatorId = settings.notificationValidatorId,
                totpItems = totpItems,
                onNotificationValidatorEnabledChange = { enabled ->
                    settingsViewModel.updateNotificationValidatorEnabled(enabled)
                },
                onNotificationValidatorAutoMatchChange = { enabled ->
                    settingsViewModel.updateNotificationValidatorAutoMatch(enabled)
                },
                onNotificationValidatorSelected = { id ->
                    settingsViewModel.updateNotificationValidatorId(id)
                }
            )
            }
        }

        composable(
            route = Screen.PageAdjustmentCustomization.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            takagi.ru.monica.ui.screens.PageAdjustmentCustomizationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPasswordListCustomization = {
                    navController.navigate(Screen.PasswordListCustomization.route)
                },
                onNavigateToPasswordCardAdjustment = {
                    navController.navigate(Screen.PasswordCardAdjustment.route)
                },
                onNavigateToAuthenticatorCardAdjustment = {
                    navController.navigate(Screen.AuthenticatorCardAdjustment.route)
                },
                onNavigateToPasswordFieldCustomization = {
                    navController.navigate(Screen.PasswordFieldCustomization.route)
                },
                onNavigateToIconSettings = {
                    navController.navigate(Screen.IconSettings.route)
                }
            )
        }

        composable(
            route = Screen.PasswordListCustomization.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            takagi.ru.monica.ui.screens.PasswordListCustomizationScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.PasswordCardAdjustment.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            takagi.ru.monica.ui.screens.PasswordCardAdjustmentScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.AuthenticatorCardAdjustment.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            takagi.ru.monica.ui.screens.AuthenticatorCardAdjustmentScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.IconSettings.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            takagi.ru.monica.ui.screens.IconSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // 添加密码页面字段定制页面
        composable(
            route = Screen.PasswordFieldCustomization.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            takagi.ru.monica.ui.screens.PasswordFieldCustomizationScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.SyncBackup.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            takagi.ru.monica.ui.screens.SyncBackupScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToExportData = {
                    navController.navigate(Screen.ExportData.route)
                },
                onNavigateToImportData = {
                    navController.navigate(Screen.ImportData.route)
                },
                onNavigateToWebDav = {
                    navController.navigate(Screen.WebDavBackup.route)
                },
                onNavigateToLocalKeePass = {
                    navController.navigate(Screen.LocalKeePass.route)
                },
                onNavigateToBitwarden = {
                    navController.navigate(Screen.BitwardenSettings.route)
                },
                isPlusActivated = settingsViewModel.settings.collectAsState().value.isPlusActivated
            )
            }
        }

        composable(Screen.LocalKeePass.route) {
            takagi.ru.monica.ui.screens.LocalKeePassScreen(
                viewModel = localKeePassViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.ColorSchemeSelection.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            takagi.ru.monica.ui.screens.ColorSchemeSelectionScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCustomColors = {
                    navController.navigate(Screen.CustomColorSettings.route)
                }
            )
            }
        }
        
        composable(Screen.CustomColorSettings.route) {
            takagi.ru.monica.ui.screens.CustomColorSettingsScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // 添加生成器页面的导航支持
        composable(Screen.Generator.route) {
            takagi.ru.monica.ui.screens.GeneratorScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                passwordViewModel = viewModel
            )
        }
        
        // 权限管理页面
        composable(
            route = Screen.PermissionManagement.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            PermissionManagementScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
            }
        }

        composable(
            route = Screen.MonicaPlus.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            val settings by settingsViewModel.settings.collectAsState()
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            MonicaPlusScreen(
                isPlusActivated = settings.isPlusActivated,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPayment = {
                    navController.navigate(Screen.Payment.route)
                },
                onDeactivatePlus = {
                    settingsViewModel.updatePlusActivated(false)
                }
            )
        }
        }

        composable(Screen.Payment.route) {
            PaymentScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onActivatePlus = {
                    settingsViewModel.updatePlusActivated(true)
                    navController.popBackStack()
                }
            )
        }
        
        // Bitwarden 登录页面
        composable(
            route = Screen.BitwardenLogin.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            val bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = 
                androidx.lifecycle.viewmodel.compose.viewModel()
            takagi.ru.monica.bitwarden.ui.BitwardenLoginScreen(
                viewModel = bitwardenViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLoginSuccess = {
                    navController.navigate(Screen.BitwardenSettings.route) {
                        popUpTo(Screen.BitwardenLogin.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Bitwarden 设置/管理页面
        composable(
            route = Screen.BitwardenSettings.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            val bitwardenViewModel: takagi.ru.monica.bitwarden.viewmodel.BitwardenViewModel = 
                androidx.lifecycle.viewmodel.compose.viewModel()
            takagi.ru.monica.bitwarden.ui.BitwardenSettingsScreen(
                viewModel = bitwardenViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.BitwardenLogin.route)
                },
                onNavigateToVault = { vaultId ->
                    // 未来可以添加 Vault 详情页面
                    // 目前保持空实现
                },
                onNavigateToSyncQueue = {
                    navController.navigate(Screen.SyncQueue.route)
                }
            )
        }
        
        // 同步队列管理页面
        composable(
            route = Screen.SyncQueue.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            // 临时使用空列表，待集成 SyncQueueManager
            var queueItems by remember { mutableStateOf(emptyList<takagi.ru.monica.bitwarden.ui.SyncQueueItem>()) }
            
            takagi.ru.monica.bitwarden.ui.SyncQueueScreen(
                queueItems = queueItems,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRetryItem = { item ->
                    // TODO: 实现重试逻辑
                },
                onDeleteItem = { item ->
                    // TODO: 实现删除逻辑
                    queueItems = queueItems.filter { it.id != item.id }
                },
                onRetryAll = {
                    // TODO: 实现全部重试逻辑
                },
                onClearCompleted = {
                    // TODO: 实现清除已完成逻辑
                    queueItems = queueItems.filter { 
                        it.status != takagi.ru.monica.bitwarden.sync.SyncStatus.SYNCED 
                    }
                }
            )
        }
    }
        }
    }
}

/**
 * 安全的视图填充函数，添加错误处理和降级方案
 */
private fun inflateViewSafely(
    layoutInflater: LayoutInflater,
    layoutId: Int,
    parent: ViewGroup?,
    attachToRoot: Boolean
): android.view.View? {
    try {
        return layoutInflater.inflate(layoutId, parent, attachToRoot)
    } catch (e: Exception) {
        Log.e("MainActivity", "Error inflating layout: $layoutId", e)
        // 返回一个简单的降级视图
        return TextView(parent?.context ?: layoutInflater.context).apply {
            text = "无法加载视图"
            gravity = Gravity.CENTER
        }
    }
}

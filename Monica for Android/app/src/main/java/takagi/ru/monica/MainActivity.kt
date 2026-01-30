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
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordHistoryManager
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.navigation.Screen
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.SimpleMainScreen
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
import androidx.compose.runtime.collectAsState
import takagi.ru.monica.util.FileOperationHelper
import takagi.ru.monica.util.PhotoPickerHelper
import takagi.ru.monica.utils.SettingsManager
import takagi.ru.monica.utils.WebDavHelper
import takagi.ru.monica.utils.AutoBackupManager

class MainActivity : FragmentActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

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

        // 启用沉浸式状态栏
        enableEdgeToEdge()

        installSplashScreen()

        // Initialize dependencies
        val database = PasswordDatabase.getDatabase(this)
        val repository = PasswordRepository(database.passwordEntryDao(), database.categoryDao())
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

    private fun enableEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

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
    val initialDisablePasswordVerification = remember {
        runBlocking {
            settingsManager.settingsFlow.first().disablePasswordVerification
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
        PasswordViewModel(
            repository,
            securityManager,
            secureItemRepository,
            navController.context,
            database.localKeePassDatabaseDao()
        )
    }
    val totpViewModel: takagi.ru.monica.viewmodel.TotpViewModel = viewModel {
        takagi.ru.monica.viewmodel.TotpViewModel(secureItemRepository, repository)
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

    val passwordHistoryManager = remember { PasswordHistoryManager(navController.context) }
    val settingsViewModel: SettingsViewModel = viewModel {
        SettingsViewModel(settingsManager, secureItemRepository)
    }
    val generatorViewModel: GeneratorViewModel = viewModel {
        GeneratorViewModel()
    }
    val noteViewModel: takagi.ru.monica.viewmodel.NoteViewModel = viewModel {
        takagi.ru.monica.viewmodel.NoteViewModel(secureItemRepository)
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
        dynamicColor = settings.dynamicColorEnabled,
        colorScheme = settings.colorScheme,
        customPrimaryColor = settings.customPrimaryColor,
        customSecondaryColor = settings.customSecondaryColor,
        customTertiaryColor = settings.customTertiaryColor
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

    // Auto-lock logic: only use lifecycleOwner as key to prevent observer recreation
    // The settings and auth state are captured by closure and always reflect latest values
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Only record timestamp when user is authenticated
                    if (isAuthenticated) {
                        lastBackgroundTimestamp = System.currentTimeMillis()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // 如果已禁用密码验证，跳过自动锁定
                    if (settings.disablePasswordVerification && !isFirstTime) {
                        lastBackgroundTimestamp = null
                        return@LifecycleEventObserver
                    }
                    
                    val minutes = settings.autoLockMinutes
                    val timeoutMs = when {
                        minutes == -1 -> null // Never auto-lock
                        minutes <= 0 -> 0L // Immediate lock after background
                        else -> minutes.toLong() * 60_000L
                    }

                    val lastBackground = lastBackgroundTimestamp
                    if (
                        isAuthenticated &&
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
    
    // 当 shouldSkipLogin 为 true 时，自动设置认证状态
    LaunchedEffect(shouldSkipLogin) {
        if (shouldSkipLogin && !isAuthenticated) {
            // 自动认证，跳过登录页面
            viewModel.authenticate("")
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated || shouldSkipLogin) Screen.Main.createRoute() else Screen.Login.route
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
                generatorViewModel = generatorViewModel,
                noteViewModel = noteViewModel,
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
                    passwordViewModel = viewModel,
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
                takagi.ru.monica.ui.screens.PasswordDetailScreen(
                    viewModel = viewModel,
                    passwordId = passwordId,
                    disablePasswordVerification = settings.disablePasswordVerification,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onEditPassword = { id ->
                        navController.navigate(Screen.AddEditPassword.createRoute(id))
                    }
                )
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
                    // 解析扫描到的 otpauth:// URI
                    val parseResult = takagi.ru.monica.util.TotpUriParser.parseUri(qrData)
                    if (parseResult != null) {
                        // 使用label作为标题，如果为空则用issuer或accountName
                        val title = parseResult.label.takeIf { it.isNotBlank() }
                            ?: parseResult.totpData.issuer.takeIf { it.isNotBlank() }
                            ?: parseResult.totpData.accountName.takeIf { it.isNotBlank() }
                            ?: "未命名验证器"
                        
                        // 检查是否已存在相同密钥的验证器
                        val existingItem = totpViewModel.findTotpBySecret(parseResult.totpData.secret)
                        if (existingItem != null) {
                            // 已存在相同密钥，显示提示
                            android.widget.Toast.makeText(
                                context,
                                "该验证器已存在: ${existingItem.title}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // 直接保存到数据库
                            totpViewModel.saveTotpItem(
                                id = null,
                                title = title,
                                notes = "",
                                totpData = parseResult.totpData,
                                isFavorite = false
                            )
                            android.widget.Toast.makeText(
                                context,
                                "已添加验证器: $title",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        // 解析失败，显示错误提示
                        android.widget.Toast.makeText(
                            context,
                            "无效的二维码，请扫描有效的验证器二维码",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
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
                onImportAegis = { uri ->
                    dataExportImportViewModel.importAegisJson(uri)
                },
                onImportEncryptedAegis = { uri, password ->
                    dataExportImportViewModel.importEncryptedAegisJson(uri, password)
                },
                onImportSteamMaFile = { uri ->
                    dataExportImportViewModel.importSteamMaFile(uri)
                },
                onImportZip = { uri, password ->
                    dataExportImportViewModel.importZipBackup(uri, password)
                },
                onImportKdbx = { uri, password ->
                    val ctx = navController.context
                    val inputStream = ctx.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val result = keePassViewModel.importFromLocalKdbx(ctx, inputStream, password)
                        result.fold(
                            onSuccess = { count: Int -> Result.success(count) },
                            onFailure = { error: Throwable -> Result.failure(error) }
                        )
                    } else {
                        Result.failure(Exception("无法打开文件"))
                    }
                }
            )
        }

        composable(Screen.ChangePassword.route) {
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

        composable(Screen.SecurityQuestion.route) {
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
                onNavigateToSyncBackup = {
                    navController.navigate(Screen.SyncBackup.route)
                },
                onNavigateToAutofill = {
                    navController.navigate(Screen.AutofillSettings.route)
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

        composable(Screen.BottomNavSettings.route) {
            BottomNavSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
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

        composable(Screen.KeePassWebDav.route) {
            takagi.ru.monica.ui.screens.KeePassWebDavScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AutofillSettings.route) {
            takagi.ru.monica.ui.screens.AutofillSettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.SecurityAnalysis.route) {
            val securityViewModel: takagi.ru.monica.viewmodel.SecurityAnalysisViewModel = viewModel {
                takagi.ru.monica.viewmodel.SecurityAnalysisViewModel(repository)
            }
            val analysisData by securityViewModel.analysisData.collectAsState()

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
        
        composable(Screen.DeveloperSettings.route) {
            takagi.ru.monica.ui.screens.DeveloperSettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Extensions.route) {
            val settings by settingsViewModel.settings.collectAsState()
            val totpItems by totpViewModel.totpItems.collectAsState()
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
                iconCardsEnabled = settings.iconCardsEnabled,
                onIconCardsEnabledChange = { enabled ->
                    settingsViewModel.updateIconCardsEnabled(enabled)
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
        
        composable(Screen.SyncBackup.route) {
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
                onNavigateToKeePass = {
                    navController.navigate(Screen.KeePassWebDav.route)
                },
                onNavigateToLocalKeePass = {
                    navController.navigate(Screen.LocalKeePass.route)
                },
                isPlusActivated = settingsViewModel.settings.collectAsState().value.isPlusActivated
            )
        }
        
        composable(Screen.LocalKeePass.route) {
            takagi.ru.monica.ui.screens.LocalKeePassScreen(
                viewModel = localKeePassViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.ColorSchemeSelection.route) {
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
        composable(Screen.PermissionManagement.route) {
            PermissionManagementScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.MonicaPlus.route) {
            val settings by settingsViewModel.settings.collectAsState()
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

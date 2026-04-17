package takagi.ru.monica

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordHistoryManager
import takagi.ru.monica.data.ThemeMode
import takagi.ru.monica.navigation.Screen
import takagi.ru.monica.data.dedup.DedupEngine
import takagi.ru.monica.data.dedup.DedupIgnoreStore
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.PasskeyRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.lock.MainAppAccessState
import takagi.ru.monica.security.lock.MainAppLockPolicy
import takagi.ru.monica.ui.SimpleMainScreen
import takagi.ru.monica.ui.screens.AddEditBankCardScreen
import takagi.ru.monica.ui.screens.AddEditDocumentScreen
import takagi.ru.monica.ui.screens.AddEditPasswordScreen
import takagi.ru.monica.ui.screens.AddEditTotpScreen
import takagi.ru.monica.ui.screens.AutofillBlockedFieldsScreen
import takagi.ru.monica.ui.screens.AutofillSaveBlockedTargetsScreen
import takagi.ru.monica.ui.screens.AutofillSettingsV2Screen
import takagi.ru.monica.ui.screens.BankCardDetailScreen
import takagi.ru.monica.ui.screens.BottomNavSettingsScreen
import takagi.ru.monica.ui.screens.ChangePasswordScreen
import takagi.ru.monica.ui.screens.DocumentDetailScreen
import takagi.ru.monica.ui.screens.ExportDataScreen
import takagi.ru.monica.ui.screens.ForgotPasswordScreen
import takagi.ru.monica.ui.screens.ImportDataScreen
import takagi.ru.monica.ui.screens.LoginScreen
import takagi.ru.monica.ui.screens.MasterPasswordLockingSettingsScreen
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
import takagi.ru.monica.ui.screens.KeePassKdbxViewModel
import takagi.ru.monica.ui.theme.MonicaTheme
import takagi.ru.monica.utils.LocaleHelper
import takagi.ru.monica.viewmodel.BankCardViewModel
import takagi.ru.monica.viewmodel.DocumentViewModel
import takagi.ru.monica.viewmodel.DedupEngineViewModel
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
import takagi.ru.monica.utils.AutoBackupManager

private data class PendingAddStorageDefaults(
    val categoryId: Long? = null,
    val keepassDatabaseId: Long? = null,
    val keepassGroupPath: String? = null,
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null
)

private const val KEY_PENDING_ADD_CATEGORY_ID = "pending_add_category_id"
private const val KEY_PENDING_ADD_KEEPASS_DATABASE_ID = "pending_add_keepass_database_id"
private const val KEY_PENDING_ADD_KEEPASS_GROUP_PATH = "pending_add_keepass_group_path"
private const val KEY_PENDING_ADD_BITWARDEN_VAULT_ID = "pending_add_bitwarden_vault_id"
private const val KEY_PENDING_ADD_BITWARDEN_FOLDER_ID = "pending_add_bitwarden_folder_id"

private fun PendingAddStorageDefaults.hasAnyValue(): Boolean {
    return categoryId != null ||
        keepassDatabaseId != null ||
        !keepassGroupPath.isNullOrBlank() ||
        bitwardenVaultId != null ||
        !bitwardenFolderId.isNullOrBlank()
}

private fun SavedStateHandle.clearPendingAddStorageDefaults() {
    remove<Long>(KEY_PENDING_ADD_CATEGORY_ID)
    remove<Long>(KEY_PENDING_ADD_KEEPASS_DATABASE_ID)
    remove<String>(KEY_PENDING_ADD_KEEPASS_GROUP_PATH)
    remove<Long>(KEY_PENDING_ADD_BITWARDEN_VAULT_ID)
    remove<String>(KEY_PENDING_ADD_BITWARDEN_FOLDER_ID)
}

private fun SavedStateHandle.setPendingAddStorageDefaults(defaults: PendingAddStorageDefaults?) {
    if (defaults == null || !defaults.hasAnyValue()) {
        clearPendingAddStorageDefaults()
        return
    }

    if (defaults.categoryId != null) {
        set(KEY_PENDING_ADD_CATEGORY_ID, defaults.categoryId)
    } else {
        remove<Long>(KEY_PENDING_ADD_CATEGORY_ID)
    }
    if (defaults.keepassDatabaseId != null) {
        set(KEY_PENDING_ADD_KEEPASS_DATABASE_ID, defaults.keepassDatabaseId)
    } else {
        remove<Long>(KEY_PENDING_ADD_KEEPASS_DATABASE_ID)
    }
    val keepassGroupPath = defaults.keepassGroupPath?.takeIf { it.isNotBlank() }
    if (keepassGroupPath != null) {
        set(KEY_PENDING_ADD_KEEPASS_GROUP_PATH, keepassGroupPath)
    } else {
        remove<String>(KEY_PENDING_ADD_KEEPASS_GROUP_PATH)
    }
    if (defaults.bitwardenVaultId != null) {
        set(KEY_PENDING_ADD_BITWARDEN_VAULT_ID, defaults.bitwardenVaultId)
    } else {
        remove<Long>(KEY_PENDING_ADD_BITWARDEN_VAULT_ID)
    }
    val bitwardenFolderId = defaults.bitwardenFolderId?.takeIf { it.isNotBlank() }
    if (bitwardenFolderId != null) {
        set(KEY_PENDING_ADD_BITWARDEN_FOLDER_ID, bitwardenFolderId)
    } else {
        remove<String>(KEY_PENDING_ADD_BITWARDEN_FOLDER_ID)
    }
}

private fun SavedStateHandle.consumePendingAddStorageDefaults(): PendingAddStorageDefaults? {
    val defaults = PendingAddStorageDefaults(
        categoryId = get<Long>(KEY_PENDING_ADD_CATEGORY_ID),
        keepassDatabaseId = get<Long>(KEY_PENDING_ADD_KEEPASS_DATABASE_ID),
        keepassGroupPath = get<String>(KEY_PENDING_ADD_KEEPASS_GROUP_PATH)?.takeIf { it.isNotBlank() },
        bitwardenVaultId = get<Long>(KEY_PENDING_ADD_BITWARDEN_VAULT_ID),
        bitwardenFolderId = get<String>(KEY_PENDING_ADD_BITWARDEN_FOLDER_ID)?.takeIf { it.isNotBlank() }
    )
    clearPendingAddStorageDefaults()
    return defaults.takeIf { it.hasAnyValue() }
}

class MainActivity : BaseMonicaActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    companion object {
        private const val TAG = "MainActivity"
        private const val AUTO_BACKUP_PREFS_NAME = "webdav_config"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val AUTO_BACKUP_INIT_DELAY_MS = 1500L
        private const val AUTO_BACKUP_INTERVAL_HOURS = 12L
    }

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
            database.passkeyDao(),
            database.passwordArchiveSyncMetaDao(),
            database.passwordHistoryDao()
        )
        val secureItemRepository = takagi.ru.monica.repository.SecureItemRepository(database.secureItemDao())
        val securityManager = SecurityManager(this)
        val settingsManager = SettingsManager(this)
        
        // Initialize OperationLogger for timeline tracking
        takagi.ru.monica.utils.OperationLogger.init(this)
        
        // Initialize auto backup if enabled (deferred to reduce cold-start contention)
        initializeAutoBackupDeferred()

        // Notification Validator is temporarily disabled for stability.
        lifecycleScope.launch {
            settingsManager.updateNotificationValidatorEnabled(false)
            settingsManager.updateNotificationValidatorAutoMatch(false)
            settingsManager.updateNotificationValidatorId(-1L)
            val intent = Intent(this@MainActivity, takagi.ru.monica.service.NotificationValidatorService::class.java)
            stopService(intent)
        }

        setContent {
            MonicaApp(repository, secureItemRepository, securityManager, settingsManager, database)
        }
    }
    
    /**
     * 初始化自动备份
     * 检查是否需要执行备份(每天首次打开时,如果距离上次备份超过24小时)
     */
    private fun initializeAutoBackupDeferred() {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                delay(AUTO_BACKUP_INIT_DELAY_MS)
                val prefs = getSharedPreferences(AUTO_BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
                val autoBackupEnabled = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
                if (!autoBackupEnabled) return@launch

                val lastBackupTime = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
                val currentTime = System.currentTimeMillis()
                if (shouldTriggerAutoBackup(lastBackupTime, currentTime)) {
                    Log.d(TAG, "Auto backup needed, triggering backup...")
                    AutoBackupManager(this@MainActivity).triggerBackupNow()
                } else {
                    Log.d(TAG, "Auto backup not needed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto backup init skipped: ${e.message}")
            }
        }
    }

    private fun shouldTriggerAutoBackup(lastBackupTime: Long, currentTime: Long): Boolean {
        if (lastBackupTime == 0L) return true

        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = lastBackupTime
        val lastBackupDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val lastBackupYear = calendar.get(java.util.Calendar.YEAR)

        calendar.timeInMillis = currentTime
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val currentYear = calendar.get(java.util.Calendar.YEAR)

        val isNewDay = (currentYear > lastBackupYear) ||
            (currentYear == lastBackupYear && currentDay > lastBackupDay)
        if (isNewDay) return true

        val hoursSinceLastBackup = (currentTime - lastBackupTime) / (1000 * 60 * 60)
        return hoursSinceLastBackup >= AUTO_BACKUP_INTERVAL_HOURS
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
            repository,
            navController.context,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }
    
    // Passkey 通行密钥
    val passkeyRepository = remember { takagi.ru.monica.repository.PasskeyRepository(database.passkeyDao()) }
    val passkeyViewModel: takagi.ru.monica.viewmodel.PasskeyViewModel = viewModel {
        takagi.ru.monica.viewmodel.PasskeyViewModel(
            repository = passkeyRepository,
            context = navController.context,
            localKeePassDatabaseDao = database.localKeePassDatabaseDao(),
            securityManager = securityManager
        )
    }
    
    // KeePass KDBX 导出/导入
    val keePassViewModel = remember { KeePassKdbxViewModel() }
    
    // 本地 KeePass 数据库管理
    val localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel = viewModel {
        takagi.ru.monica.viewmodel.LocalKeePassViewModel(
            context.applicationContext as android.app.Application,
            database.localKeePassDatabaseDao(),
            securityManager
        )
    }

    var startupAuthState by remember { mutableStateOf<MainAppAccessState?>(null) }
    LaunchedEffect(viewModel, settingsManager) {
        val loadedState = withContext(Dispatchers.IO) {
            val settingsSnapshot = runCatching {
                settingsManager.settingsFlow.first()
            }.getOrElse { AppSettings() }
            runCatching {
                SessionManager.updateAutoLockTimeout(settingsSnapshot.autoLockMinutes)
                MainAppLockPolicy.resolveAccessState(
                    securityManager,
                    context.applicationContext,
                    settingsSnapshot.autoLockMinutes,
                    settingsSnapshot.disablePasswordVerification
                )
            }.getOrElse {
                MainAppAccessState(
                    isFirstTime = false,
                    bypassEnabled = false,
                    canRestoreSession = false,
                    reason = "startup_load_error"
                )
            }
        }
        if (loadedState.bypassEnabled) {
            android.util.Log.w(
                "MonicaAuthGate",
                "Startup allowed via ${loadedState.reason}"
            )
            viewModel.markAuthenticatedForBypass()
        } else if (loadedState.canRestoreSession) {
            android.util.Log.d(
                "MonicaAuthGate",
                "Startup restored via ${loadedState.reason}"
            )
            viewModel.restoreAuthenticatedUiState()
        } else {
            android.util.Log.d(
                "MonicaAuthGate",
                "Startup requires authentication via ${loadedState.reason}"
            )
        }
        startupAuthState = loadedState
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
        oledPureBlackEnabled = settings.oledPureBlackEnabled,
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
            val authState = startupAuthState
            if (authState == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                MonicaContent(
                    navController = navController,
                    viewModel = viewModel,
                    totpViewModel = totpViewModel,
                    bankCardViewModel = bankCardViewModel,
                    documentViewModel = documentViewModel,
                    settingsViewModel = settingsViewModel,
                    generatorViewModel = generatorViewModel,
                    noteViewModel = noteViewModel,
                    passkeyViewModel = passkeyViewModel,
                    keePassViewModel = keePassViewModel,
                    localKeePassViewModel = localKeePassViewModel,
                    securityManager = securityManager,
                    repository = repository,
                    database = database,
                    secureItemRepository = secureItemRepository,
                    passwordHistoryManager = passwordHistoryManager,
                    initialAuthState = authState,
                    onPermissionRequested = { permission, callback ->
                        pendingSupportPermissionCallback = callback
                        sharedSupportPermissionLauncher.launch(permission)
                    }
                )
            }
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
    settingsViewModel: SettingsViewModel,
    generatorViewModel: GeneratorViewModel,
    noteViewModel: takagi.ru.monica.viewmodel.NoteViewModel,
    passkeyViewModel: takagi.ru.monica.viewmodel.PasskeyViewModel,
    keePassViewModel: KeePassKdbxViewModel,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    securityManager: SecurityManager,
    repository: PasswordRepository,
    database: PasswordDatabase,
    secureItemRepository: SecureItemRepository,
    passwordHistoryManager: PasswordHistoryManager,
    initialAuthState: MainAppAccessState =
        MainAppAccessState(
            isFirstTime = false,
            bypassEnabled = false,
            canRestoreSession = false,
            reason = "default"
        ),
    onPermissionRequested: (String, (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val isFirstTime = initialAuthState.isFirstTime
    val currentRoute = navBackStackEntry?.destination?.route
    val authRouteSet = remember {
        setOf(
            Screen.Login.route,
            Screen.ForgotPassword.route,
            Screen.ResetPassword.route,
            Screen.SecurityQuestionsSetup.route,
            Screen.SecurityQuestionsVerification.route
        )
    }
    var hasRenderedLoginFirstFrame by remember { mutableStateOf(false) }
    val mainAppAccessState = MainAppLockPolicy.resolveAccessState(
        securityManager,
        context.applicationContext,
        settings.autoLockMinutes,
        settings.disablePasswordVerification
    )
    val shouldRequireAuthentication = !isAuthenticated && !mainAppAccessState.canEnterMainApp
    val isOnAuthRoute = currentRoute != null && currentRoute in authRouteSet
    val showAuthTransitionGate = shouldRequireAuthentication && (
        !isOnAuthRoute ||
            (currentRoute == Screen.Login.route && !hasRenderedLoginFirstFrame)
        )

    LaunchedEffect(shouldRequireAuthentication, currentRoute) {
        if (!shouldRequireAuthentication || currentRoute != Screen.Login.route) {
            hasRenderedLoginFirstFrame = false
        }
    }
    
    // 使用 rememberUpdatedState 确保生命周期观察者闭包始终访问最新值
    val currentIsAuthenticated by rememberUpdatedState(isAuthenticated)
    val currentSettings by rememberUpdatedState(settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val accessState = MainAppLockPolicy.resolveAccessState(
                        securityManager,
                        context.applicationContext,
                        currentSettings.autoLockMinutes,
                        currentSettings.disablePasswordVerification
                    )

                    if (!currentIsAuthenticated && accessState.canEnterMainApp) {
                        android.util.Log.d(
                            "MonicaAuthGate",
                            "ON_START allowed while unauthenticated via ${accessState.reason}"
                        )
                        if (accessState.bypassEnabled) {
                            viewModel.markAuthenticatedForBypass()
                        } else if (accessState.canRestoreSession) {
                            viewModel.restoreAuthenticatedUiState()
                        }
                    }

                    if (currentIsAuthenticated && !accessState.canEnterMainApp) {
                        android.util.Log.w(
                            "MonicaAuthGate",
                            "ON_START revoked authenticated UI state via ${accessState.reason}"
                        )
                        viewModel.logout()
                        return@LifecycleEventObserver
                    }

                    if (currentIsAuthenticated || accessState.bypassEnabled) {
                        viewModel.refreshKeePassFromSourceForCurrentContext()
                    }
                    if (!currentIsAuthenticated && !accessState.canEnterMainApp) {
                        android.util.Log.d(
                            "MonicaAuthGate",
                            "ON_START navigating to auth via ${accessState.reason}"
                        )
                        navController.navigate(Screen.Login.route) {
                            launchSingleTop = true
                            popUpTo(0) { inclusive = true }
                        }
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
    
    // 使用固定的 startDestination 避免竞态条件
    // 认证状态变化时通过 LaunchedEffect 处理导航
    val fixedStartDestination = remember(initialAuthState.canEnterMainApp) {
        if (initialAuthState.canEnterMainApp) {
            Screen.Main.createRoute()
        } else {
            Screen.Login.route
        }
    }
    
    // 当认证状态变化时处理导航
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            viewModel.refreshKeePassFromSourceForCurrentContext()
            val currentRoute = navController.currentDestination?.route
            if (currentRoute == Screen.Login.route) {
                navController.navigate(Screen.Main.createRoute()) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
        } else {
            if (mainAppAccessState.bypassEnabled) {
                android.util.Log.w(
                    "MonicaAuthGate",
                    "Auth effect allowed via ${mainAppAccessState.reason}"
                )
                viewModel.markAuthenticatedForBypass()
                return@LaunchedEffect
            }
            if (mainAppAccessState.canRestoreSession) {
                android.util.Log.d(
                    "MonicaAuthGate",
                    "Auth effect restored via ${mainAppAccessState.reason}"
                )
                viewModel.restoreAuthenticatedUiState()
                return@LaunchedEffect
            }
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return@LaunchedEffect
            }
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != Screen.Login.route) {
                navController.navigate(Screen.Login.route) {
                    launchSingleTop = true
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    // Emergency safe mode: disable global shared transition lookahead to avoid
    // "Placement happened before lookahead" crashes on affected devices/builds.
    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    androidx.compose.runtime.CompositionLocalProvider(
        takagi.ru.monica.ui.LocalSharedTransitionScope provides null,
        takagi.ru.monica.ui.LocalReduceAnimations provides true
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = fixedStartDestination
            ) {
            composable(
                route = Screen.Login.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                LoginScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    onFirstFrameRendered = {
                        hasRenderedLoginFirstFrame = true
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
                    navController.navigate(Screen.AddEditPassword.createRoute(passwordId)) {
                        launchSingleTop = true
                    }
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
                onNavigateToWalletAdd = { initialType ->
                    navController.navigate(Screen.WalletAdd.createRoute(initialType.name))
                },
                onPreparePasswordAddStorageDefaults = { categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.setPendingAddStorageDefaults(
                            PendingAddStorageDefaults(
                                categoryId = categoryId,
                                keepassDatabaseId = keepassDatabaseId,
                                keepassGroupPath = keepassGroupPath,
                                bitwardenVaultId = bitwardenVaultId,
                                bitwardenFolderId = bitwardenFolderId
                            )
                        )
                },
                onPrepareTotpAddStorageDefaults = { categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.setPendingAddStorageDefaults(
                            PendingAddStorageDefaults(
                                categoryId = categoryId,
                                keepassDatabaseId = keepassDatabaseId,
                                keepassGroupPath = keepassGroupPath,
                                bitwardenVaultId = bitwardenVaultId,
                                bitwardenFolderId = bitwardenFolderId
                            )
                        )
                },
                onPrepareNoteAddStorageDefaults = { categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.setPendingAddStorageDefaults(
                            PendingAddStorageDefaults(
                                categoryId = categoryId,
                                keepassDatabaseId = keepassDatabaseId,
                                keepassGroupPath = keepassGroupPath,
                                bitwardenVaultId = bitwardenVaultId,
                                bitwardenFolderId = bitwardenFolderId
                            )
                        )
                },
                onPrepareWalletAddStorageDefaults = { categoryId, keepassDatabaseId, keepassGroupPath, bitwardenVaultId, bitwardenFolderId ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.setPendingAddStorageDefaults(
                            PendingAddStorageDefaults(
                                categoryId = categoryId,
                                keepassDatabaseId = keepassDatabaseId,
                                keepassGroupPath = keepassGroupPath,
                                bitwardenVaultId = bitwardenVaultId,
                                bitwardenFolderId = bitwardenFolderId
                            )
                        )
                },
                onNavigateToAddNote = { noteId ->
                    if (noteId == null) {
                        navController.navigate(Screen.AddEditNote.createRoute())
                    } else {
                        navController.navigate(Screen.NoteDetail.createRoute(noteId))
                    }
                },
                onNavigateToPasswordDetail = { passwordId ->
                    navController.navigate(Screen.PasswordDetail.createRoute(passwordId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToPasskeyDetail = { recordId ->
                    navController.navigate(Screen.PasskeyDetail.createRoute(recordId))
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
                onNavigateToMasterPasswordLocking = {
                    navController.navigate(Screen.MasterPasswordLockingSettings.route)
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
                onNavigateToCommonAccountTemplates = {
                    navController.navigate(Screen.CommonAccountTemplates.route)
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

        composable(
            route = Screen.AddEditPassword.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
            val passwordId = backStackEntry.arguments?.getString("passwordId")?.toLongOrNull() ?: -1L
            val pendingStorageDefaults = remember(backStackEntry, passwordId) {
                if (passwordId > 0) {
                    navController.previousBackStackEntry?.savedStateHandle?.clearPendingAddStorageDefaults()
                    null
                } else {
                    navController.previousBackStackEntry?.savedStateHandle?.consumePendingAddStorageDefaults()
                }
            }
            val qrResult = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.get<String>("qr_result")
            val navigateBackFromAddEditPassword = {
                val popped = navController.popBackStack()
                if (!popped) {
                    navController.navigate(Screen.Main.createRoute()) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            AddEditPasswordScreen(
                viewModel = viewModel,
                totpViewModel = totpViewModel,
                bankCardViewModel = bankCardViewModel,
                noteViewModel = noteViewModel,
                localKeePassViewModel = localKeePassViewModel,
                passwordId = if (passwordId == -1L) null else passwordId,
                initialCategoryId = pendingStorageDefaults?.categoryId,
                initialKeePassDatabaseId = pendingStorageDefaults?.keepassDatabaseId,
                initialKeePassGroupPath = pendingStorageDefaults?.keepassGroupPath,
                initialBitwardenVaultId = pendingStorageDefaults?.bitwardenVaultId,
                initialBitwardenFolderId = pendingStorageDefaults?.bitwardenFolderId,
                pendingQrResult = qrResult,
                onConsumePendingQrResult = {
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.remove<String>("qr_result")
                },
                onScanAuthenticatorQrCode = {
                    navController.navigate(Screen.QrScanner.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = navigateBackFromAddEditPassword
            )
        }

        composable(
            route = Screen.AddEditTotp.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
            val totpId = backStackEntry.arguments?.getString("totpId")?.toLongOrNull() ?: -1L
            val currentTotpFilter by totpViewModel.categoryFilter.collectAsState()
            val context = LocalContext.current
            val pendingStorageDefaults = remember(backStackEntry, totpId) {
                if (totpId > 0) {
                    navController.previousBackStackEntry?.savedStateHandle?.clearPendingAddStorageDefaults()
                    null
                } else {
                    navController.previousBackStackEntry?.savedStateHandle?.consumePendingAddStorageDefaults()
                }
            }

            var initialData by remember { mutableStateOf<takagi.ru.monica.data.model.TotpData?>(null) }
            var initialTitle by remember { mutableStateOf("") }
            var initialNotes by remember { mutableStateOf("") }
            var initialKeePassGroupPath by remember { mutableStateOf<String?>(null) }
            var initialBitwardenVaultId by remember { mutableStateOf<Long?>(null) }
            var initialBitwardenFolderId by remember { mutableStateOf<String?>(null) }
            var initialReplicaGroupId by remember { mutableStateOf<String?>(null) }
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
                        initialKeePassGroupPath = item.keepassGroupPath
                        initialBitwardenVaultId = item.bitwardenVaultId
                        initialBitwardenFolderId = item.bitwardenFolderId
                        initialReplicaGroupId = item.replicaGroupId
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
                    val keepassGroupPath: String? = null,
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
                            TotpStorageDefaults(
                                keepassDatabaseId = filter.databaseId,
                                keepassGroupPath = filter.groupPath
                            )
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
                val hasPendingStorageDefaults = pendingStorageDefaults?.hasAnyValue() == true
                val initialCategoryId = when {
                    initialData?.categoryId != null -> initialData?.categoryId
                    hasPendingStorageDefaults -> pendingStorageDefaults?.categoryId
                    else -> filterDefaults.categoryId
                }
                val initialKeePassDatabaseId = when {
                    initialData?.keepassDatabaseId != null -> initialData?.keepassDatabaseId
                    hasPendingStorageDefaults -> pendingStorageDefaults?.keepassDatabaseId
                    else -> filterDefaults.keepassDatabaseId
                }
                val resolvedInitialKeePassGroupPath = when {
                    !initialKeePassGroupPath.isNullOrBlank() -> initialKeePassGroupPath
                    hasPendingStorageDefaults -> pendingStorageDefaults?.keepassGroupPath
                    else -> filterDefaults.keepassGroupPath
                }
                val initialVaultId = when {
                    initialBitwardenVaultId != null -> initialBitwardenVaultId
                    hasPendingStorageDefaults -> pendingStorageDefaults?.bitwardenVaultId
                    else -> filterDefaults.bitwardenVaultId
                }
                val initialFolderId = when {
                    !initialBitwardenFolderId.isNullOrBlank() -> initialBitwardenFolderId
                    hasPendingStorageDefaults -> pendingStorageDefaults?.bitwardenFolderId
                    else -> filterDefaults.bitwardenFolderId
                }
                takagi.ru.monica.ui.screens.AddEditTotpScreen(
                    totpId = if (totpId > 0) totpId else null,
                    initialData = initialData,
                    initialTitle = initialTitle,
                    initialNotes = initialNotes,
                    initialCategoryId = initialCategoryId,
                    initialKeePassDatabaseId = initialKeePassDatabaseId,
                    initialKeePassGroupPath = resolvedInitialKeePassGroupPath,
                    initialBitwardenVaultId = initialVaultId,
                    initialBitwardenFolderId = initialFolderId,
                    initialReplicaGroupId = initialReplicaGroupId,
                    categories = totpCategories,
                    passwordViewModel = viewModel,
                    totpViewModel = totpViewModel,
                    localKeePassViewModel = localKeePassViewModel,
                    onSave = { title, notes, totpData, targets ->
                        totpViewModel.saveTotpAcrossTargets(
                            id = if (totpId > 0) totpId else null,
                            title = title,
                            notes = notes,
                            totpData = totpData,
                            targets = targets
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

        composable(
            route = Screen.WalletAdd.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
            val initialTypeRaw = backStackEntry.arguments?.getString("initialType").orEmpty()
            val initialType = runCatching {
                takagi.ru.monica.ui.screens.CardWalletTab.valueOf(initialTypeRaw)
            }.getOrDefault(takagi.ru.monica.ui.screens.CardWalletTab.BANK_CARDS)
            val pendingStorageDefaults = remember(backStackEntry) {
                navController.previousBackStackEntry?.savedStateHandle?.consumePendingAddStorageDefaults()
            }
            val walletAddStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            var selectedType by androidx.compose.runtime.saveable.rememberSaveable(initialTypeRaw) {
                mutableStateOf(initialType)
            }
            val walletAddVisibleState = remember {
                androidx.compose.animation.core.MutableTransitionState(false).apply {
                    targetState = true
                }
            }

            AnimatedVisibility(
                visibleState = walletAddVisibleState,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(220)
                ) + fadeIn(animationSpec = tween(180)),
                exit = ExitTransition.None
            ) {
                takagi.ru.monica.ui.UnifiedWalletAddScreen(
                    selectedType = selectedType,
                    onTypeSelected = { selectedType = it },
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    bankCardViewModel = bankCardViewModel,
                    documentViewModel = documentViewModel,
                    stateHolder = walletAddStateHolder,
                    initialCategoryId = pendingStorageDefaults?.categoryId,
                    initialKeePassDatabaseId = pendingStorageDefaults?.keepassDatabaseId,
                    initialKeePassGroupPath = pendingStorageDefaults?.keepassGroupPath,
                    initialBitwardenVaultId = pendingStorageDefaults?.bitwardenVaultId,
                    initialBitwardenFolderId = pendingStorageDefaults?.bitwardenFolderId
                )
            }
        }

        composable(
            route = Screen.AddEditBankCard.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId")?.toLongOrNull() ?: -1L
            val pendingStorageDefaults = remember(backStackEntry, cardId) {
                if (cardId > 0) {
                    navController.previousBackStackEntry?.savedStateHandle?.clearPendingAddStorageDefaults()
                    null
                } else {
                    navController.previousBackStackEntry?.savedStateHandle?.consumePendingAddStorageDefaults()
                }
            }

            takagi.ru.monica.ui.screens.AddEditBankCardScreen(
                viewModel = bankCardViewModel,
                cardId = if (cardId > 0) cardId else null,
                initialCategoryId = pendingStorageDefaults?.categoryId,
                initialKeePassDatabaseId = pendingStorageDefaults?.keepassDatabaseId,
                initialKeePassGroupPath = pendingStorageDefaults?.keepassGroupPath,
                initialBitwardenVaultId = pendingStorageDefaults?.bitwardenVaultId,
                initialBitwardenFolderId = pendingStorageDefaults?.bitwardenFolderId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.AddEditDocument.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId")?.toLongOrNull() ?: -1L
            val pendingStorageDefaults = remember(backStackEntry, documentId) {
                if (documentId > 0) {
                    navController.previousBackStackEntry?.savedStateHandle?.clearPendingAddStorageDefaults()
                    null
                } else {
                    navController.previousBackStackEntry?.savedStateHandle?.consumePendingAddStorageDefaults()
                }
            }

            takagi.ru.monica.ui.screens.AddEditDocumentScreen(
                viewModel = documentViewModel,
                documentId = if (documentId > 0) documentId else null,
                initialCategoryId = pendingStorageDefaults?.categoryId,
                initialKeePassDatabaseId = pendingStorageDefaults?.keepassDatabaseId,
                initialKeePassGroupPath = pendingStorageDefaults?.keepassGroupPath,
                initialBitwardenVaultId = pendingStorageDefaults?.bitwardenVaultId,
                initialBitwardenFolderId = pendingStorageDefaults?.bitwardenFolderId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "bank_card_detail/{cardId}",
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
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

        composable(
            route = Screen.AddEditNote.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull() ?: -1L
            val pendingStorageDefaults = remember(backStackEntry, noteId) {
                if (noteId > 0) {
                    navController.previousBackStackEntry?.savedStateHandle?.clearPendingAddStorageDefaults()
                    null
                } else {
                    navController.previousBackStackEntry?.savedStateHandle?.consumePendingAddStorageDefaults()
                }
            }

            takagi.ru.monica.ui.screens.AddEditNoteScreen(
                noteId = noteId,
                initialCategoryId = pendingStorageDefaults?.categoryId,
                initialKeePassDatabaseId = pendingStorageDefaults?.keepassDatabaseId,
                initialKeePassGroupPath = pendingStorageDefaults?.keepassGroupPath,
                initialBitwardenVaultId = pendingStorageDefaults?.bitwardenVaultId,
                initialBitwardenFolderId = pendingStorageDefaults?.bitwardenFolderId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = noteViewModel
            )
        }

        composable(
            route = Screen.NoteDetail.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull() ?: -1L

            if (noteId > 0) {
                takagi.ru.monica.ui.screens.NoteDetailScreen(
                    viewModel = noteViewModel,
                    noteId = noteId,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onEditNote = { id ->
                        navController.navigate(Screen.AddEditNote.createRoute(id))
                    }
                )
            }
        }

        composable(
            route = Screen.DocumentDetail.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
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

        composable(
            route = Screen.PasswordDetail.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
            val passwordId = backStackEntry.arguments?.getString("passwordId")?.toLongOrNull() ?: -1L
            val scope = rememberCoroutineScope()

            if (passwordId > 0) {
                val navigateBackFromPasswordDetail = {
                    val popped = navController.popBackStack()
                    if (!popped) {
                        navController.navigate(Screen.Main.createRoute()) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
                ) {
                    takagi.ru.monica.ui.screens.PasswordDetailScreen(
                        viewModel = viewModel,
                        passkeyViewModel = passkeyViewModel,
                        noteViewModel = noteViewModel,
                        passwordId = passwordId,
                        biometricEnabled = settings.biometricEnabled,
                        iconCardsEnabled = settings.iconCardsEnabled && settings.passwordPageIconEnabled,
                        unmatchedIconHandlingStrategy = settings.unmatchedIconHandlingStrategy,
                        enableSharedBounds = false,
                        onNavigateBack = navigateBackFromPasswordDetail,
                        onOpenBoundNote = { noteId ->
                            scope.launch {
                                navController.navigate(Screen.NoteDetail.createRoute(noteId)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onEditPassword = { id ->
                            scope.launch {
                                navController.navigate(Screen.AddEditPassword.createRoute(id)) {
                                    launchSingleTop = true
                                }
                            }
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
        composable(
            route = Screen.ExportData.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            val dataExportImportViewModel: takagi.ru.monica.viewmodel.DataExportImportViewModel = viewModel {
                takagi.ru.monica.viewmodel.DataExportImportViewModel(
                    secureItemRepository,
                    repository,
                    navController.context
                )
            }
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
        composable(
            route = Screen.ImportData.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            val dataExportImportViewModel: takagi.ru.monica.viewmodel.DataExportImportViewModel = viewModel {
                takagi.ru.monica.viewmodel.DataExportImportViewModel(
                    secureItemRepository,
                    repository,
                    navController.context
                )
            }
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
                onImportPasswordKeyboardCsv = { uri, tagHandling ->
                    dataExportImportViewModel.importPasswordKeyboardCsv(uri, tagHandling)
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
                onImportKdbx = { uri, password, keyFileUri ->
                    val ctx = navController.context
                    val result = keePassViewModel.importFromLocalKdbx(
                        context = ctx,
                        sourceUri = uri,
                        kdbxPassword = password,
                        keyFileUri = keyFileUri
                    )
                    result.fold(
                        onSuccess = { count: Int -> Result.success(count) },
                        onFailure = { error: Throwable -> Result.failure(error) }
                    )
                }
            )
        }

        composable(
            route = Screen.ChangePassword.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
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

        composable(
            route = Screen.SecurityQuestion.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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

        composable(
            route = Screen.Settings.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
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
                    navController.navigate(Screen.ResetPassword.createRoute()) {
                        launchSingleTop = true
                    }
                },
                onSecurityQuestions = {
                    navController.navigate(Screen.SecurityQuestionsSetup.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToMasterPasswordLocking = {
                    navController.navigate(Screen.MasterPasswordLockingSettings.route) {
                        launchSingleTop = true
                    }
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
            route = Screen.MasterPasswordLockingSettings.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
                MasterPasswordLockingSettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onResetPassword = {
                        navController.navigate(Screen.ResetPassword.createRoute()) {
                            launchSingleTop = true
                        }
                    },
                    onSecurityQuestions = {
                        navController.navigate(Screen.SecurityQuestionsSetup.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable(
            route = Screen.BottomNavSettings.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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

        composable(
            route = Screen.SecurityQuestionsVerification.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
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

        composable(
            route = Screen.SupportAuthor.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            SupportAuthorScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRequestPermission = onPermissionRequested
            )
        }

        composable(
            route = Screen.WebDavBackup.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
                AutofillSettingsV2Screen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToBlockedFields = {
                        navController.navigate(Screen.AutofillBlockedFields.route)
                    },
                    onNavigateToSaveBlockedTargets = {
                        navController.navigate(Screen.AutofillSaveBlockedTargets.route)
                    }
                )
            }
        }

        composable(
            route = Screen.AutofillBlockedFields.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
                AutofillBlockedFieldsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(
            route = Screen.AutofillSaveBlockedTargets.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
                AutofillSaveBlockedTargetsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(
            route = Screen.PasskeySettings.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            route = Screen.PasskeyDetail.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString("recordId")?.toLongOrNull() ?: -1L
            takagi.ru.monica.ui.screens.PasskeyDetailScreen(
                recordId = recordId,
                passkeyViewModel = passkeyViewModel,
                passwordViewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPasswordDetail = { passwordId ->
                    navController.navigate(Screen.PasswordDetail.createRoute(passwordId))
                }
            )
        }

        composable(
            route = Screen.SecurityAnalysis.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            val context = LocalContext.current
            val passkeySupportCatalog = remember(context.applicationContext) {
                takagi.ru.monica.utils.PasskeySupportCatalog(context.applicationContext)
            }
            val securityViewModel: takagi.ru.monica.viewmodel.SecurityAnalysisViewModel = viewModel {
                takagi.ru.monica.viewmodel.SecurityAnalysisViewModel(
                    repository,
                    securityManager,
                    database.localKeePassDatabaseDao(),
                    database.bitwardenVaultDao(),
                    database.passkeyDao(),
                    passkeySupportCatalog
                )
            }
            val securitySettings by settingsViewModel.settings.collectAsState()
            val analysisData by securityViewModel.analysisData.collectAsState()
            LaunchedEffect(securitySettings.securityAnalysisAutoEnabled) {
                securityViewModel.setAutoAnalysisEnabled(securitySettings.securityAnalysisAutoEnabled)
            }

            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            takagi.ru.monica.ui.screens.SecurityAnalysisScreen(
                analysisData = analysisData,
                autoAnalysisEnabled = securitySettings.securityAnalysisAutoEnabled,
                onStartAnalysis = {
                    securityViewModel.refreshRealtimeAnalysis()
                },
                onAutoAnalysisEnabledChange = { enabled ->
                    settingsViewModel.updateSecurityAnalysisAutoEnabled(enabled)
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPassword = { passwordId ->
                    navController.navigate(Screen.PasswordDetail.createRoute(passwordId))
                },
                onSelectScope = { scopeKey ->
                    securityViewModel.selectScope(scopeKey)
                }
            )
            }
        }
        
        composable(
            route = Screen.DeveloperSettings.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
                onNavigateToMonicaPlus = {
                    navController.navigate(Screen.MonicaPlus.route)
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
            route = Screen.CommonAccountTemplates.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                takagi.ru.monica.ui.LocalAnimatedVisibilityScope provides this
            ) {
            takagi.ru.monica.ui.screens.CommonAccountTemplatesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
            }
        }

        composable(
            route = Screen.PageAdjustmentCustomization.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            route = Screen.AddButtonCustomization.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            takagi.ru.monica.ui.screens.AddButtonCustomizationScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.PasswordListCustomization.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
                onNavigateToDedupEngine = {
                    navController.navigate(Screen.DedupEngine.route)
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

        composable(
            route = Screen.DedupEngine.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            val context = LocalContext.current
            val passkeyRepository = remember(context.applicationContext) {
                PasskeyRepository(database.passkeyDao())
            }
            val dedupViewModel: DedupEngineViewModel = viewModel {
                DedupEngineViewModel(
                    dedupEngine = DedupEngine(
                        passwordRepository = repository,
                        secureItemRepository = secureItemRepository,
                        passkeyRepository = passkeyRepository,
                        localKeePassDatabaseDao = database.localKeePassDatabaseDao(),
                        bitwardenVaultDao = database.bitwardenVaultDao(),
                        securityManager = securityManager,
                        ignoreStore = DedupIgnoreStore(context.applicationContext)
                    ),
                    localKeePassDatabaseDao = database.localKeePassDatabaseDao(),
                    bitwardenVaultDao = database.bitwardenVaultDao()
                )
            }
            val uiState by dedupViewModel.uiState.collectAsState()

            takagi.ru.monica.ui.screens.DedupEngineScreen(
                uiState = uiState,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRefresh = {
                    dedupViewModel.refresh()
                },
                onPreferredSourceChange = { source ->
                    dedupViewModel.updatePreferredSource(source)
                },
                onPreferredKeepassDatabaseChange = { databaseId ->
                    dedupViewModel.updatePreferredKeepassDatabase(databaseId)
                },
                onPreferredBitwardenVaultChange = { vaultId ->
                    dedupViewModel.updatePreferredBitwardenVault(vaultId)
                },
                onScopeChange = { scope ->
                    dedupViewModel.updateScope(scope)
                },
                onScopeKeepassDatabaseChange = { databaseId ->
                    dedupViewModel.updateSelectedKeepassDatabase(databaseId)
                },
                onScopeBitwardenVaultChange = { vaultId ->
                    dedupViewModel.updateSelectedBitwardenVault(vaultId)
                },
                onTypeChange = { type ->
                    dedupViewModel.updateType(type)
                },
                onClusterAction = { cluster, action ->
                    dedupViewModel.performAction(cluster, action)
                },
                onEnterSelectionMode = {
                    dedupViewModel.enterSelectionMode()
                },
                onExitSelectionMode = {
                    dedupViewModel.exitSelectionMode()
                },
                onToggleClusterSelection = { clusterId ->
                    dedupViewModel.toggleClusterSelection(clusterId)
                },
                onSelectAllVisible = { clusterIds ->
                    dedupViewModel.selectAll(clusterIds)
                },
                onClearSelected = {
                    dedupViewModel.clearSelected()
                },
                onBatchAction = { clusters, action ->
                    dedupViewModel.performBatchAction(clusters, action)
                },
                onConsumeMessage = {
                    dedupViewModel.consumeMessage()
                }
            )
        }

        composable(
            route = Screen.LocalKeePass.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
            takagi.ru.monica.ui.screens.LocalKeePassScreen(
                viewModel = localKeePassViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.ColorSchemeSelection.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
        
        composable(
            route = Screen.CustomColorSettings.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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

        composable(
            route = Screen.Payment.route,
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
        ) {
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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
            enterTransition = { rightSlideEnterTransition() },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { rightSlidePopExitTransition() }
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

            if (showAuthTransitionGate) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun rightSlideEnterTransition() = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(220)
)

private fun rightSlidePopExitTransition() = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(200)
)
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

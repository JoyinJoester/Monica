package takagi.ru.monica.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.isLocalPasswordOwnership
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.autofill_ng.AutofillSecretResolver
import takagi.ru.monica.autofill_ng.AutofillPickerActivityV2
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.util.TotpDataResolver
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.utils.SettingsManager

class MonicaInputMethodService : InputMethodService() {

    companion object {
        const val ACTION_IME_BIOMETRIC_RESULT = "takagi.ru.monica.ime.action.BIOMETRIC_RESULT"
        const val EXTRA_IME_BIOMETRIC_SUCCESS = "extra_ime_biometric_success"
        const val EXTRA_IME_BIOMETRIC_ERROR = "extra_ime_biometric_error"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleOwner = ServiceComposeViewLifecycleOwner()
    private val uiState = MutableStateFlow(MonicaImeUiState())

    private lateinit var securityManager: SecurityManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var database: PasswordDatabase
    private var composeView: ComposeView? = null
    private var recomposer: Recomposer? = null
    private var refreshJob: Job? = null
    private var databaseObserverJob: Job? = null
    private var authenticatorTickerJob: Job? = null
    private var pendingUnlockPanel: MonicaImePanel? = null
    private var pendingClearedInputText: String? = null
    private var unlockFlowInProgress = false
    private var suppressAutoUnlockUntilNextAttempt = false
    private val imeUnlockResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_IME_BIOMETRIC_RESULT) {
                handleImeBiometricResult(intent)
            }
        }
    }

    private fun handleImeBiometricResult(intent: Intent) {
        val success = intent.getBooleanExtra(EXTRA_IME_BIOMETRIC_SUCCESS, false)
        val errorMessage = intent.getStringExtra(EXTRA_IME_BIOMETRIC_ERROR)
        val targetPanel = pendingUnlockPanel ?: MonicaImePanel.PASSWORDS
        unlockFlowInProgress = false
        pendingUnlockPanel = null

        if (success) {
            suppressAutoUnlockUntilNextAttempt = false
            uiState.update {
                it.copy(
                    unlocked = true,
                    activePanel = targetPanel,
                    isAutofillPanelVisible = targetPanel != MonicaImePanel.KEYBOARD,
                    isAutofillLoading = targetPanel == MonicaImePanel.PASSWORDS,
                    errorMessage = null
                )
            }
            requestRefreshVaultEntries(force = true)
        } else {
            suppressAutoUnlockUntilNextAttempt = true
            uiState.update {
                it.copy(
                    unlocked = false,
                    activePanel = targetPanel,
                    isAutofillPanelVisible = targetPanel != MonicaImePanel.KEYBOARD,
                    isAutofillLoading = false,
                    errorMessage = errorMessage ?: getString(takagi.ru.monica.R.string.ime_unlock_required)
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        securityManager = SecurityManager(applicationContext)
        settingsManager = SettingsManager(applicationContext)
        database = PasswordDatabase.getDatabase(applicationContext)
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        registerReceiver(
            imeUnlockResultReceiver,
            IntentFilter().apply {
                addAction(ACTION_IME_BIOMETRIC_RESULT)
            },
            receiverFlags
        )
        observeDatabaseSources()
        observeAuthenticatorTicker()

        serviceScope.launch {
            val settings = settingsManager.settingsFlow.first()
            uiState.update { it.copy(autoLockMinutes = settings.autoLockMinutes) }
            refreshVaultEntries()
        }
    }

    override fun onCreateInputView(): View {
        if (composeView == null) {
            recomposer = Recomposer(AndroidUiDispatcher.Main).also { createdRecomposer ->
                serviceScope.launch(AndroidUiDispatcher.Main) {
                    createdRecomposer.runRecomposeAndApplyChanges()
                }
            }
            composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setParentCompositionContext(recomposer)
                setContent {
                    val settings = settingsManager.settingsFlow.collectAsState(
                        initial = AppSettings()
                    ).value
                    val state = uiState.collectAsState().value

                    MonicaImeContent(
                        settings = settings,
                        uiState = state,
                        onQueryChanged = { query ->
                            uiState.update { it.copy(query = query) }
                            requestRefreshVaultEntries()
                        },
                        onDatabaseScopeSelected = { scope ->
                            uiState.update { it.copy(selectedDatabaseScope = scope) }
                            requestRefreshVaultEntries(force = true)
                        },
                        onInsertPassword = { commitExternalText(it.password) },
                        onInsertUsername = { commitExternalText(it.username) },
                        onSmartFillPassword = ::handleSmartFillPassword,
                        onInsertAuthenticatorCode = { commitExternalText(it.code) },
                        onInsertCardWalletValue = { commitExternalText(it.value) },
                        onSmartFillCardWallet = ::handleSmartFillCardWallet,
                        onKeyPressed = ::handleKeyPress,
                        onBackspace = ::handleBackspace,
                        onDeleteAll = ::handleDeleteAll,
                        onUndoDeleteAll = ::handleUndoDeleteAll,
                        onEnter = ::handleEnter,
                        onSpace = { handleKeyPress(" ") },
                        onShiftToggle = {
                            uiState.update { it.copy(isUppercase = !it.isUppercase) }
                        },
                        onKeyboardModeChange = { mode ->
                            uiState.update { it.copy(keyboardMode = mode) }
                        },
                        onOpenUnlockApp = ::openMonicaAppForUnlock,
                        onOpenAutofillSettings = ::openAutofillPickerPage,
                        onSwitchInputMethod = ::switchToNextInputMethod,
                        onPanelSelected = ::handlePanelSelection,
                        onDismiss = { requestHideSelf(0) }
                    )
                }
            }
        }
        return composeView!!
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        window?.window?.let { imeWindow ->
            imeWindow.navigationBarColor = Color.BLACK
            imeWindow.decorView.setBackgroundColor(Color.BLACK)
        }
        val previousState = uiState.value
        val incomingPackageName = info?.packageName?.takeIf { it.isNotBlank() }
        val effectivePackageName = incomingPackageName ?: previousState.activePackageName
        val packageUnchanged =
            incomingPackageName == null || previousState.activePackageName == incomingPackageName
        val preserveAutofillPanel =
            previousState.activePanel != MonicaImePanel.KEYBOARD && packageUnchanged
        val shouldRefreshForCurrentView = previousState.unlocked ||
            !preserveAutofillPanel ||
            previousState.entries.isNotEmpty() ||
            previousState.authenticatorEntries.isNotEmpty() ||
            previousState.cardWalletEntries.isNotEmpty()
        uiState.update {
            it.copy(
                activePackageName = effectivePackageName,
                activePanel = if (preserveAutofillPanel) previousState.activePanel else MonicaImePanel.KEYBOARD,
                isAutofillPanelVisible = preserveAutofillPanel,
                isAutofillLoading = preserveAutofillPanel &&
                    previousState.activePanel == MonicaImePanel.PASSWORDS &&
                    shouldRefreshForCurrentView,
                query = if (preserveAutofillPanel) previousState.query else "",
                selectedDatabaseScope = if (preserveAutofillPanel) {
                    previousState.selectedDatabaseScope
                } else {
                    MonicaImeDatabaseScope.All
                },
                errorMessage = null
            )
        }
        if (shouldRefreshForCurrentView) {
            requestRefreshVaultEntries(force = true)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        requestRefreshVaultEntries()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        requestRefreshVaultEntries(force = true)
    }

    override fun onDestroy() {
        authenticatorTickerJob?.cancel()
        databaseObserverJob?.cancel()
        refreshJob?.cancel()
        unregisterReceiver(imeUnlockResultReceiver)
        composeView?.disposeComposition()
        composeView = null
        recomposer?.cancel()
        recomposer = null
        serviceScope.cancel()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun openMonicaAppForUnlock() {
        val targetPanel = pendingUnlockPanel
            ?: uiState.value.activePanel.takeIf { it != MonicaImePanel.KEYBOARD }
            ?: MonicaImePanel.PASSWORDS

        pendingUnlockPanel = targetPanel
        suppressAutoUnlockUntilNextAttempt = false

        if (unlockFlowInProgress) {
            return
        }

        unlockFlowInProgress = true
        runCatching {
            startActivity(
                Intent(this, ImeUnlockActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
            )
        }.onFailure { error ->
            unlockFlowInProgress = false
            uiState.update {
                it.copy(errorMessage = error.message ?: getString(takagi.ru.monica.R.string.ime_unlock_open_app_error))
            }
        }
    }

    private fun openAutofillPickerPage() {
        clearPendingDeleteUndo()
        val activePackageName = uiState.value.activePackageName.takeIf { it.isNotBlank() }
        requestHideSelf(0)
        runCatching {
            val pickerArgs = AutofillPickerActivityV2.Args(
                applicationId = activePackageName,
                isSaveMode = false,
                rememberLastFilled = false
            )
            startActivity(
                AutofillPickerActivityV2.getIntent(this, pickerArgs).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    putExtra(AutofillPickerActivityV2.EXTRA_MANUAL_MODE, true)
                    putExtra(AutofillPickerActivityV2.EXTRA_IME_MODE, true)
                }
            )
        }.onFailure { error ->
            uiState.update {
                it.copy(errorMessage = error.message ?: getString(takagi.ru.monica.R.string.ime_unlock_open_app_error))
            }
        }
    }

    private fun requestRefreshVaultEntries(force: Boolean = false) {
        refreshJob?.cancel()
        val currentState = uiState.value
        if (
            currentState.unlocked &&
            currentState.activePanel == MonicaImePanel.PASSWORDS &&
            currentState.isAutofillPanelVisible
        ) {
            uiState.update { it.copy(isAutofillLoading = true) }
        }
        refreshJob = serviceScope.launch {
            try {
                refreshVaultEntries(force = force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MonicaIME", "Failed to refresh IME autofill entries", e)
                uiState.update {
                    it.copy(
                        isAutofillLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    private fun observeDatabaseSources() {
        databaseObserverJob?.cancel()
        databaseObserverJob = serviceScope.launch {
            combine(
                database.localKeePassDatabaseDao().getAllDatabases()
                    .map { databases ->
                        databases.map { database ->
                            Triple(database.id, database.name, database.filePath)
                        }
                    }
                    .distinctUntilChanged(),
                database.bitwardenVaultDao().getAllVaultsFlow()
                    .map { vaults ->
                        vaults.map { vault ->
                            Triple(vault.id, vault.email, vault.displayName.orEmpty())
                        }
                    }
                    .distinctUntilChanged()
            ) { keepassSignatures, bitwardenSignatures ->
                keepassSignatures to bitwardenSignatures
            }.collect {
                val currentState = uiState.value
                if (currentState.activePanel != MonicaImePanel.KEYBOARD || currentState.unlocked) {
                    requestRefreshVaultEntries(force = true)
                }
            }
        }
    }

    private fun observeAuthenticatorTicker() {
        authenticatorTickerJob?.cancel()
        authenticatorTickerJob = serviceScope.launch {
            while (true) {
                delay(1_000)
                val currentState = uiState.value
                if (
                    currentState.unlocked &&
                    currentState.activePanel == MonicaImePanel.AUTHENTICATORS &&
                    currentState.isAutofillPanelVisible
                ) {
                    refreshVaultEntries()
                }
            }
        }
    }

    private fun handlePanelSelection(panel: MonicaImePanel) {
        clearPendingDeleteUndo()
        if (panel == MonicaImePanel.KEYBOARD) {
            suppressAutoUnlockUntilNextAttempt = false
            uiState.update {
                it.copy(
                    activePanel = MonicaImePanel.KEYBOARD,
                    isAutofillPanelVisible = false,
                    isAutofillLoading = false,
                    query = "",
                    selectedDatabaseScope = MonicaImeDatabaseScope.All,
                    errorMessage = null
                )
            }
            return
        }
        if (panel == MonicaImePanel.GENERATOR) {
            suppressAutoUnlockUntilNextAttempt = false
            uiState.update {
                it.copy(
                    activePanel = MonicaImePanel.GENERATOR,
                    isAutofillPanelVisible = true,
                    isAutofillLoading = false,
                    query = "",
                    selectedDatabaseScope = MonicaImeDatabaseScope.All,
                    errorMessage = null
                )
            }
            return
        }

        serviceScope.launch {
            val settings = settingsManager.settingsFlow.first()
            val unlockedNow = updateUnlockState(settings.autoLockMinutes)
            if (!unlockedNow) {
                pendingUnlockPanel = panel
                suppressAutoUnlockUntilNextAttempt = false
                uiState.update {
                    it.copy(
                        unlocked = false,
                        activePanel = panel,
                        isAutofillPanelVisible = true,
                        entries = emptyList(),
                        authenticatorEntries = emptyList(),
                        cardWalletEntries = emptyList(),
                        databaseOptions = emptyList(),
                        isAutofillLoading = false,
                        selectedDatabaseScope = MonicaImeDatabaseScope.All,
                        errorMessage = getString(takagi.ru.monica.R.string.ime_unlock_required)
                    )
                }
                openMonicaAppForUnlock()
                return@launch
            }

            uiState.update {
                it.copy(
                    activePanel = panel,
                    isAutofillPanelVisible = true,
                    isAutofillLoading = panel == MonicaImePanel.PASSWORDS,
                    errorMessage = null,
                    query = if (panel == MonicaImePanel.PASSWORDS) "" else it.query,
                    selectedDatabaseScope = it.selectedDatabaseScope
                )
            }
            requestRefreshVaultEntries(force = true)
        }
    }

    private suspend fun refreshVaultEntries(force: Boolean = false) {
        val settings = settingsManager.settingsFlow.first()
        val isUnlocked = updateUnlockState(settings.autoLockMinutes)
        if (!isUnlocked) {
            val currentState = uiState.value
            uiState.update {
                it.copy(
                    entries = emptyList(),
                    authenticatorEntries = emptyList(),
                    cardWalletEntries = emptyList(),
                    autoLockMinutes = settings.autoLockMinutes,
                    isAutofillLoading = false
                )
            }
            return
        }

        val currentState = uiState.value
        val activePackage = currentState.activePackageName
        val query = if (currentState.activePanel == MonicaImePanel.PASSWORDS) {
            ""
        } else {
            currentState.query.trim()
        }
        val localLabel = getString(takagi.ru.monica.R.string.filter_monica)
        val keepassLabel = getString(takagi.ru.monica.R.string.filter_keepass)
        val mdbxLabel = "MDBX"
        val bitwardenLabel = getString(takagi.ru.monica.R.string.filter_bitwarden)
        val allDatabasesLabel = getString(takagi.ru.monica.R.string.password_picker_all_databases)
        val snapshot = withContext(Dispatchers.IO) {
            val keepassDatabases = database.localKeePassDatabaseDao().getAllDatabasesSync()
            val mdbxDatabases = database.localMdbxDatabaseDao().getAllDatabasesSnapshot()
            val bitwardenVaults = database.bitwardenVaultDao().getAllVaults()
            val keepassLookup = keepassDatabases.associateBy { it.id }
            val mdbxLookup = mdbxDatabases.associateBy { it.id }
            val bitwardenLookup = bitwardenVaults.associateBy { it.id }
            val databaseOptions = buildDatabaseOptions(
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel,
                allDatabasesLabel = allDatabasesLabel,
                keepassDatabases = keepassDatabases,
                mdbxDatabases = mdbxDatabases,
                bitwardenVaults = bitwardenVaults
            )
            val selectedScope = currentState.selectedDatabaseScope
                .takeIf { scope -> databaseOptions.any { it.scope == scope } }
                ?: MonicaImeDatabaseScope.All

            val passwordEntries = database.passwordEntryDao()
                .getAllPasswordEntriesSync()
            val results = passwordEntries
                .mapNotNull { entry ->
                    entry.toImeEntryOrNull(
                        keepassLookup = keepassLookup,
                        mdbxLookup = mdbxLookup,
                        bitwardenLookup = bitwardenLookup,
                        localLabel = localLabel,
                        keepassLabel = keepassLabel,
                        mdbxLabel = mdbxLabel,
                        bitwardenLabel = bitwardenLabel
                    )
                }
                .filter { result ->
                    val entry = result.value
                    entryMatchesScope(entry, selectedScope) &&
                        queryMatches(entry, query)
                }
                .sortedWith(
                    compareByDescending<ImeRefreshResult> {
                        it.value.let { entry -> entryMatchesPackage(entry, activePackage) }
                    }.thenByDescending {
                        it.value.isFavorite
                    }.thenBy {
                        it.value.title.lowercase()
                    }
                )
                // 不限制条目数量，由 LazyColumn 自行处理懒加载渲染。

            val authenticatorResults = buildAuthenticatorEntries(
                secureItems = database.secureItemDao().getActiveItemsByTypeSync(ItemType.TOTP),
                passwordEntries = passwordEntries,
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel,
                query = query,
                selectedScope = selectedScope
            )

            val cardWalletResults = buildCardWalletEntries(
                secureItems = database.secureItemDao().getActiveItemsByTypeSync(ItemType.BANK_CARD) +
                    database.secureItemDao().getActiveItemsByTypeSync(ItemType.DOCUMENT),
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel,
                query = query,
                selectedScope = selectedScope
            )

            ImeRefreshSnapshot(
                results = results,
                authenticatorResults = authenticatorResults,
                cardWalletResults = cardWalletResults,
                databaseOptions = databaseOptions,
                selectedScope = selectedScope
            )
        }

        val entries = snapshot.results.map { it.value }

        uiState.update {
            it.copy(
                unlocked = true,
                entries = entries,
                authenticatorEntries = snapshot.authenticatorResults,
                cardWalletEntries = snapshot.cardWalletResults,
                databaseOptions = snapshot.databaseOptions,
                selectedDatabaseScope = snapshot.selectedScope,
                autoLockMinutes = settings.autoLockMinutes,
                isAutofillLoading = false,
                errorMessage = null
            )
        }
    }

    private fun updateUnlockState(autoLockMinutes: Int): Boolean {
        val unlocked = securityManager.canAccessVaultNowStrict(this, autoLockMinutes)
        if (unlocked) {
            uiState.update { it.copy(unlocked = true, errorMessage = null) }
        } else {
            uiState.update {
                val panelStillVisible = it.activePanel != MonicaImePanel.KEYBOARD && it.isAutofillPanelVisible
                it.copy(
                    unlocked = false,
                    entries = emptyList(),
                    authenticatorEntries = emptyList(),
                    cardWalletEntries = emptyList(),
                    databaseOptions = emptyList(),
                    isAutofillLoading = false,
                    selectedDatabaseScope = MonicaImeDatabaseScope.All,
                    errorMessage = if (panelStillVisible) {
                        getString(takagi.ru.monica.R.string.ime_unlock_required)
                    } else {
                        null
                    }
                )
            }
        }
        return unlocked
    }

    private fun entryMatchesPackage(entry: MonicaImePasswordEntry, activePackage: String): Boolean {
        return imeEntryMatchesPackage(
            entryPackageName = entry.packageName,
            website = entry.website,
            title = entry.title,
            activePackageName = activePackage
        )
    }

    private fun entryMatchesScope(
        entry: MonicaImePasswordEntry,
        scope: MonicaImeDatabaseScope
    ): Boolean {
        return when (scope) {
            MonicaImeDatabaseScope.All -> true
            MonicaImeDatabaseScope.Local -> isLocalPasswordOwnership(
                entry.keepassDatabaseId,
                entry.bitwardenVaultId,
                entry.mdbxDatabaseId
            )
            is MonicaImeDatabaseScope.KeePass -> entry.keepassDatabaseId == scope.databaseId
            is MonicaImeDatabaseScope.Mdbx -> entry.mdbxDatabaseId == scope.databaseId
            is MonicaImeDatabaseScope.Bitwarden -> entry.bitwardenVaultId == scope.vaultId
        }
    }

    private fun entryMatchesScope(
        entry: MonicaImeAuthenticatorEntry,
        scope: MonicaImeDatabaseScope
    ): Boolean {
        return when (scope) {
            MonicaImeDatabaseScope.All -> true
            MonicaImeDatabaseScope.Local -> isLocalPasswordOwnership(
                entry.keepassDatabaseId,
                entry.bitwardenVaultId,
                entry.mdbxDatabaseId
            )
            is MonicaImeDatabaseScope.KeePass -> entry.keepassDatabaseId == scope.databaseId
            is MonicaImeDatabaseScope.Mdbx -> entry.mdbxDatabaseId == scope.databaseId
            is MonicaImeDatabaseScope.Bitwarden -> entry.bitwardenVaultId == scope.vaultId
        }
    }

    private fun entryMatchesScope(
        entry: MonicaImeCardWalletEntry,
        scope: MonicaImeDatabaseScope
    ): Boolean {
        return when (scope) {
            MonicaImeDatabaseScope.All -> true
            MonicaImeDatabaseScope.Local -> isLocalPasswordOwnership(
                entry.keepassDatabaseId,
                entry.bitwardenVaultId,
                entry.mdbxDatabaseId
            )
            is MonicaImeDatabaseScope.KeePass -> entry.keepassDatabaseId == scope.databaseId
            is MonicaImeDatabaseScope.Mdbx -> entry.mdbxDatabaseId == scope.databaseId
            is MonicaImeDatabaseScope.Bitwarden -> entry.bitwardenVaultId == scope.vaultId
        }
    }

    private fun queryMatches(entry: MonicaImePasswordEntry, query: String): Boolean {
        if (query.isBlank()) return true
        val haystack = listOf(
            entry.title,
            entry.username,
            entry.website,
            entry.packageName,
            entry.sourceLabel
        ).joinToString(" ").lowercase()
        return haystack.contains(query.lowercase())
    }

    private fun queryMatches(entry: MonicaImeAuthenticatorEntry, query: String): Boolean {
        if (query.isBlank()) return true
        val haystack = listOf(
            entry.title,
            entry.issuer,
            entry.accountName,
            entry.sourceLabel
        ).joinToString(" ").lowercase()
        return haystack.contains(query.lowercase())
    }

    private fun queryMatches(entry: MonicaImeCardWalletEntry, query: String): Boolean {
        if (query.isBlank()) return true
        val haystack = buildString {
            append(entry.title)
            append(' ')
            append(entry.subtitle)
            append(' ')
            append(entry.typeLabel)
            append(' ')
            append(entry.sourceLabel)
            entry.fields.forEach { field ->
                append(' ')
                append(field.label)
                append(' ')
                append(field.value)
            }
        }.lowercase()
        return haystack.contains(query.lowercase())
    }

    private fun buildAuthenticatorEntries(
        secureItems: List<SecureItem>,
        passwordEntries: List<PasswordEntry>,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String,
        query: String,
        selectedScope: MonicaImeDatabaseScope
    ): List<MonicaImeAuthenticatorEntry> {
        val storedEntries = secureItems.mapNotNull { item ->
            item.toImeAuthenticatorEntryOrNull(
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel
            )
        }
        val existingKeys = storedEntries.map { entry ->
            listOf(entry.issuer, entry.accountName, entry.title)
                .joinToString("|")
                .lowercase()
        }.toSet()
        val virtualEntries = passwordEntries.mapNotNull { password ->
            password.toVirtualImeAuthenticatorEntryOrNull(
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel
            )?.takeUnless { entry ->
                listOf(entry.issuer, entry.accountName, entry.title)
                    .joinToString("|")
                    .lowercase() in existingKeys
            }
        }

        return (storedEntries + virtualEntries)
            .filter { entryMatchesScope(it, selectedScope) }
            .filter { queryMatches(it, query) }
            .sortedWith(
                compareByDescending<MonicaImeAuthenticatorEntry> { it.isFavorite }
                    .thenBy { it.title.lowercase() }
            )
    }

    private fun buildCardWalletEntries(
        secureItems: List<SecureItem>,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String,
        query: String,
        selectedScope: MonicaImeDatabaseScope
    ): List<MonicaImeCardWalletEntry> {
        return secureItems
            .mapNotNull { item ->
                item.toImeCardWalletEntryOrNull(
                    keepassLookup = keepassLookup,
                    mdbxLookup = mdbxLookup,
                    bitwardenLookup = bitwardenLookup,
                    localLabel = localLabel,
                    keepassLabel = keepassLabel,
                    mdbxLabel = mdbxLabel,
                    bitwardenLabel = bitwardenLabel
                )
            }
            .filter { entryMatchesScope(it, selectedScope) }
            .filter { queryMatches(it, query) }
            .sortedWith(
                compareByDescending<MonicaImeCardWalletEntry> { it.isFavorite }
                    .thenBy { it.typeLabel }
                    .thenBy { it.title.lowercase() }
            )
    }

    private fun SecureItem.toImeAuthenticatorEntryOrNull(
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String
    ): MonicaImeAuthenticatorEntry? {
        if (itemType != ItemType.TOTP) return null
        val parsed = TotpDataResolver.parseStoredItemData(
            itemData = itemData,
            fallbackIssuer = title,
            fallbackAccountName = "",
            decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext
        ) ?: return null
        val resolved = parsed.resolveReadableTotpData() ?: return null
        val code = TotpGenerator.generateOtp(resolved)
        if (code.isBlank()) return null
        return MonicaImeAuthenticatorEntry(
            id = id,
            title = title.ifBlank {
                resolved.issuer.ifBlank {
                    resolved.accountName.ifBlank { getString(takagi.ru.monica.R.string.authenticator) }
                }
            },
            issuer = resolved.issuer,
            accountName = resolved.accountName,
            code = code,
            remainingSeconds = if (resolved.otpType == OtpType.HOTP) {
                0
            } else {
                TotpGenerator.getRemainingSeconds(resolved.period)
            },
            isFavorite = isFavorite,
            sourceLabel = resolveSourceLabel(
                item = this,
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel
            ),
            keepassDatabaseId = keepassDatabaseId,
            mdbxDatabaseId = mdbxDatabaseId,
            bitwardenVaultId = bitwardenVaultId
        )
    }

    private fun decryptStoredSensitiveValue(value: String): String {
        return runCatching {
            securityManager.decryptDataIfMonicaCiphertext(value)
        }.getOrDefault(value)
    }

    private fun PasswordEntry.toVirtualImeAuthenticatorEntryOrNull(
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String
    ): MonicaImeAuthenticatorEntry? {
        val resolvedUsername = resolveFillableField(username).orEmpty()
        val parsed = TotpDataResolver.fromAuthenticatorKey(
            rawKey = decryptStoredSensitiveValue(authenticatorKey),
            fallbackIssuer = title,
            fallbackAccountName = resolvedUsername
        ) ?: return null
        val resolved = parsed.resolveReadableTotpData() ?: return null
        val code = TotpGenerator.generateOtp(resolved)
        if (code.isBlank()) return null
        return MonicaImeAuthenticatorEntry(
            id = -id,
            title = title.ifBlank {
                resolved.issuer.ifBlank {
                    resolved.accountName.ifBlank { getString(takagi.ru.monica.R.string.authenticator) }
                }
            },
            issuer = resolved.issuer,
            accountName = resolved.accountName,
            code = code,
            remainingSeconds = if (resolved.otpType == OtpType.HOTP) {
                0
            } else {
                TotpGenerator.getRemainingSeconds(resolved.period)
            },
            isFavorite = isFavorite,
            sourceLabel = resolveSourceLabel(
                entry = this,
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel
            ),
            keepassDatabaseId = keepassDatabaseId,
            mdbxDatabaseId = mdbxDatabaseId,
            bitwardenVaultId = bitwardenVaultId
        )
    }

    private fun TotpData.resolveReadableTotpData(): TotpData? {
        val normalized = TotpDataResolver.normalizeTotpData(this)
        val readableSecret = resolveSecretValue(normalized.secret) ?: return null
        val readablePin = resolveSecretValue(normalized.pin) ?: normalized.pin
        return normalized.copy(secret = readableSecret, pin = readablePin)
    }

    private fun SecureItem.toImeCardWalletEntryOrNull(
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String
    ): MonicaImeCardWalletEntry? {
        return when (itemType) {
            ItemType.BANK_CARD -> toImeBankCardEntryOrNull(
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel
            )
            ItemType.DOCUMENT -> toImeDocumentEntryOrNull(
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel
            )
            else -> null
        }
    }

    private fun SecureItem.toImeBankCardEntryOrNull(
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String
    ): MonicaImeCardWalletEntry? {
        val data = CardWalletDataCodec.parseBankCardData(
            raw = itemData,
            decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext
        ) ?: return null
        val fields = listOfNotNull(
            fieldOrNull(getString(takagi.ru.monica.R.string.card_number), resolveSecretValue(data.cardNumber)),
            fieldOrNull(getString(takagi.ru.monica.R.string.cardholder_name), data.cardholderName),
            fieldOrNull(getString(takagi.ru.monica.R.string.expiry_date), formatExpiry(data.expiryMonth, data.expiryYear)),
            fieldOrNull(getString(takagi.ru.monica.R.string.cvv), resolveSecretValue(data.cvv)),
            fieldOrNull(getString(takagi.ru.monica.R.string.bank_card_pin_label), resolveSecretValue(data.pin)),
            fieldOrNull(getString(takagi.ru.monica.R.string.bank_card_account_number_label), resolveSecretValue(data.accountNumber)),
            fieldOrNull(getString(takagi.ru.monica.R.string.bank_card_routing_number_label), data.routingNumber),
            fieldOrNull("IBAN", data.iban),
            fieldOrNull("SWIFT/BIC", data.swiftBic)
        )
        if (fields.isEmpty()) return null
        val title = title.ifBlank {
            data.nickname.ifBlank {
                data.bankName.ifBlank { getString(takagi.ru.monica.R.string.bank_card_default_title) }
            }
        }
        val subtitle = listOf(data.bankName, maskCardNumber(resolveSecretValue(data.cardNumber).orEmpty()))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        return MonicaImeCardWalletEntry(
            id = id,
            title = title,
            subtitle = subtitle,
            typeLabel = getString(takagi.ru.monica.R.string.item_type_bank_card),
            isFavorite = isFavorite,
            sourceLabel = resolveSourceLabel(
                item = this,
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel
            ),
            fields = fields,
            keepassDatabaseId = keepassDatabaseId,
            mdbxDatabaseId = mdbxDatabaseId,
            bitwardenVaultId = bitwardenVaultId
        )
    }

    private fun SecureItem.toImeDocumentEntryOrNull(
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String
    ): MonicaImeCardWalletEntry? {
        val data = CardWalletDataCodec.parseDocumentData(
            raw = itemData,
            decryptIfNeeded = securityManager::decryptDataIfMonicaCiphertext
        ) ?: return null
        val fullName = data.displayName()
        val fields = listOfNotNull(
            fieldOrNull(getString(takagi.ru.monica.R.string.document_number), resolveSecretValue(data.documentNumber)),
            fieldOrNull(getString(takagi.ru.monica.R.string.full_name), fullName),
            fieldOrNull(getString(takagi.ru.monica.R.string.expiry_date_label), data.expiryDate),
            fieldOrNull(getString(takagi.ru.monica.R.string.cardholder_label), data.username),
            fieldOrNull(getString(takagi.ru.monica.R.string.email), data.email),
            fieldOrNull(getString(takagi.ru.monica.R.string.phone), data.phone),
            fieldOrNull("SSN", resolveSecretValue(data.ssn))
        )
        if (fields.isEmpty()) return null
        return MonicaImeCardWalletEntry(
            id = id,
            title = title.ifBlank { fullName.ifBlank { getString(takagi.ru.monica.R.string.documents) } },
            subtitle = fullName,
            typeLabel = getString(takagi.ru.monica.R.string.documents),
            isFavorite = isFavorite,
            sourceLabel = resolveSourceLabel(
                item = this,
                keepassLookup = keepassLookup,
                mdbxLookup = mdbxLookup,
                bitwardenLookup = bitwardenLookup,
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                mdbxLabel = mdbxLabel,
                bitwardenLabel = bitwardenLabel
            ),
            fields = fields,
            keepassDatabaseId = keepassDatabaseId,
            mdbxDatabaseId = mdbxDatabaseId,
            bitwardenVaultId = bitwardenVaultId
        )
    }

    private fun PasswordEntry.toImeEntryOrNull(
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String
    ): ImeRefreshResult? {
        val decryptedUsername = resolveFillableField(username)
        val decryptedPassword = resolveFillableField(password)

        if (decryptedUsername.isNullOrBlank() && decryptedPassword.isNullOrBlank()) {
            return null
        }

        // 如果密码条目绑定了验证器密钥，生成当前 TOTP 码
        val totpCode = runCatching {
            val parsed = TotpDataResolver.fromAuthenticatorKey(
                rawKey = decryptStoredSensitiveValue(authenticatorKey),
                fallbackIssuer = title,
                fallbackAccountName = username
            )
            val resolved = parsed?.resolveReadableTotpData()
            if (resolved != null) TotpGenerator.generateOtp(resolved) else ""
        }.getOrDefault("")

        return ImeRefreshResult(
            value = MonicaImePasswordEntry(
                id = id,
                title = title,
                username = decryptedUsername.orEmpty(),
                website = website,
                packageName = appPackageName,
                password = decryptedPassword.orEmpty(),
                isFavorite = isFavorite,
                totpCode = totpCode,
                sourceLabel = resolveSourceLabel(
                    entry = this,
                    keepassLookup = keepassLookup,
                    mdbxLookup = mdbxLookup,
                    bitwardenLookup = bitwardenLookup,
                    localLabel = localLabel,
                    keepassLabel = keepassLabel,
                    mdbxLabel = mdbxLabel,
                    bitwardenLabel = bitwardenLabel
                ),
                keepassDatabaseId = keepassDatabaseId,
                mdbxDatabaseId = mdbxDatabaseId,
                bitwardenVaultId = bitwardenVaultId
            )
        )
    }

    private fun resolveSourceLabel(
        entry: PasswordEntry,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String
    ): String {
        return when {
            entry.bitwardenVaultId != null -> {
                val vaultName = bitwardenLookup[entry.bitwardenVaultId]?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: bitwardenLookup[entry.bitwardenVaultId]?.email
                listOf(bitwardenLabel, vaultName).filterNotNull().joinToString(" · ")
            }
            entry.keepassDatabaseId != null -> {
                val databaseName = keepassLookup[entry.keepassDatabaseId]?.name
                listOf(keepassLabel, databaseName).filterNotNull().joinToString(" · ")
            }
            entry.mdbxDatabaseId != null -> {
                val databaseName = mdbxLookup[entry.mdbxDatabaseId]?.name
                listOf(mdbxLabel, databaseName).filterNotNull().joinToString(" · ")
            }
            else -> localLabel
        }
    }

    private fun resolveSourceLabel(
        item: SecureItem,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        mdbxLookup: Map<Long, LocalMdbxDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String
    ): String {
        return when {
            item.bitwardenVaultId != null -> {
                val vaultName = bitwardenLookup[item.bitwardenVaultId]?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: bitwardenLookup[item.bitwardenVaultId]?.email
                listOf(bitwardenLabel, vaultName).filterNotNull().joinToString(" · ")
            }
            item.keepassDatabaseId != null -> {
                val databaseName = keepassLookup[item.keepassDatabaseId]?.name
                listOf(keepassLabel, databaseName).filterNotNull().joinToString(" · ")
            }
            item.mdbxDatabaseId != null -> {
                val databaseName = mdbxLookup[item.mdbxDatabaseId]?.name
                listOf(mdbxLabel, databaseName).filterNotNull().joinToString(" · ")
            }
            else -> localLabel
        }
    }

    private fun resolveSecretValue(value: String): String? {
        return AutofillSecretResolver.decryptPasswordOrNull(
            securityManager = securityManager,
            encryptedOrPlain = value,
            logTag = "MonicaIme"
        )
    }

    private fun resolveFillableField(value: String): String? {
        return AutofillSecretResolver.decryptPasswordOrNull(
            securityManager = securityManager,
            encryptedOrPlain = value,
            logTag = "MonicaIme"
        )
    }

    private fun fieldOrNull(label: String, value: String?): MonicaImeCardWalletField? {
        val resolved = value?.trim().orEmpty()
        if (resolved.isBlank()) return null
        return MonicaImeCardWalletField(label = label, value = resolved)
    }

    private fun formatExpiry(month: String, year: String): String {
        val normalizedMonth = month.trim()
        val normalizedYear = year.trim()
        return when {
            normalizedMonth.isNotBlank() && normalizedYear.isNotBlank() -> "$normalizedMonth/$normalizedYear"
            normalizedMonth.isNotBlank() -> normalizedMonth
            else -> normalizedYear
        }
    }

    private fun maskCardNumber(cardNumber: String): String {
        val digits = cardNumber.filter { it.isDigit() }
        if (digits.length < 4) return ""
        return "•••• ${digits.takeLast(4)}"
    }

    private fun DocumentData.displayName(): String {
        val parts = listOf(firstName, middleName, lastName).filter { it.isNotBlank() }
        return when {
            parts.isNotEmpty() -> parts.joinToString(" ")
            fullName.isNotBlank() -> fullName
            else -> ""
        }
    }

    private fun buildDatabaseOptions(
        localLabel: String,
        keepassLabel: String,
        mdbxLabel: String,
        bitwardenLabel: String,
        allDatabasesLabel: String,
        keepassDatabases: List<LocalKeePassDatabase>,
        mdbxDatabases: List<LocalMdbxDatabase>,
        bitwardenVaults: List<BitwardenVault>
    ): List<MonicaImeDatabaseOption> {
        return buildList {
            add(MonicaImeDatabaseOption(MonicaImeDatabaseScope.All, allDatabasesLabel))
            add(MonicaImeDatabaseOption(MonicaImeDatabaseScope.Local, localLabel))
            keepassDatabases
                .sortedWith(
                    compareByDescending<LocalKeePassDatabase> { it.isDefault }
                        .thenBy { it.sortOrder }
                        .thenBy { it.name.lowercase() }
                )
                .forEach { database ->
                    add(
                        MonicaImeDatabaseOption(
                            scope = MonicaImeDatabaseScope.KeePass(database.id),
                            label = "$keepassLabel · ${database.name}"
                        )
                    )
                }
            mdbxDatabases
                .sortedWith(
                    compareByDescending<LocalMdbxDatabase> { it.isDefault }
                        .thenBy { it.sortOrder }
                        .thenBy { it.name.lowercase() }
                )
                .forEach { database ->
                    add(
                        MonicaImeDatabaseOption(
                            scope = MonicaImeDatabaseScope.Mdbx(database.id),
                            label = "$mdbxLabel · ${database.name}"
                        )
                    )
                }
            bitwardenVaults
                .sortedWith(
                    compareByDescending<BitwardenVault> { it.isDefault }
                        .thenBy { (it.displayName ?: it.email).lowercase() }
                )
                .forEach { vault ->
                    val vaultName = vault.displayName?.takeIf { it.isNotBlank() } ?: vault.email
                    add(
                        MonicaImeDatabaseOption(
                            scope = MonicaImeDatabaseScope.Bitwarden(vault.id),
                            label = "$bitwardenLabel · $vaultName"
                        )
                    )
                }
        }
    }

    private fun switchToNextInputMethod() {
        clearPendingDeleteUndo()
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { switchToPreviousInputMethod() }.getOrDefault(false)
        } else {
            false
        }
        if (!switched) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }
    }

    private fun handleKeyPress(text: String) {
        clearPendingDeleteUndo()
        commitExternalText(text)
    }

    private fun commitExternalText(text: String) {
        if (text.isEmpty()) return
        currentInputConnection?.commitText(text, 1)
    }

    private fun handleSmartFillPassword(entry: MonicaImePasswordEntry) {
        clearPendingDeleteUndo()
        val values = if (isCurrentFieldLikelyPassword()) {
            listOf(entry.password)
        } else {
            listOf(entry.username, entry.password)
        }.filter { it.isNotBlank() }
        performSequentialImeFill(values)
    }

    private fun handleSmartFillCardWallet(entry: MonicaImeCardWalletEntry) {
        clearPendingDeleteUndo()
        performSequentialImeFill(entry.fields.map { it.value }.filter { it.isNotBlank() })
    }

    private fun performSequentialImeFill(values: List<String>) {
        if (values.isEmpty()) return
        serviceScope.launch {
            values.forEachIndexed { index, value ->
                val connection = currentInputConnection ?: return@launch
                connection.commitText(value, 1)
                if (index < values.lastIndex) {
                    delay(ImeSequentialFillStepDelayMs)
                    if (!moveToNextInputField(connection)) {
                        return@launch
                    }
                    delay(ImeSequentialFillFocusDelayMs)
                }
            }
        }
    }

    private fun moveToNextInputField(connection: android.view.inputmethod.InputConnection): Boolean {
        if (connection.performEditorAction(EditorInfo.IME_ACTION_NEXT)) {
            return true
        }
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB)
        return connection.sendKeyEvent(down) && connection.sendKeyEvent(up)
    }

    private fun isCurrentFieldLikelyPassword(): Boolean {
        val inputType = currentInputEditorInfo?.inputType ?: return false
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun handleBackspace() {
        clearPendingDeleteUndo()
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun handleDeleteAll() {
        val connection = currentInputConnection ?: return
        val beforeCursor = connection.getTextBeforeCursor(MaxImeClearChars, 0)?.toString().orEmpty()
        val selectedText = connection.getSelectedText(0)?.toString().orEmpty()
        val afterCursor = connection.getTextAfterCursor(MaxImeClearChars, 0)?.toString().orEmpty()
        val fullText = beforeCursor + selectedText + afterCursor
        if (fullText.isEmpty()) return

        connection.beginBatchEdit()
        if (selectedText.isNotEmpty()) {
            connection.commitText("", 1)
        }
        connection.deleteSurroundingText(beforeCursor.length, afterCursor.length)
        connection.endBatchEdit()

        pendingClearedInputText = fullText
        uiState.update { it.copy(pendingClearedInput = fullText) }
    }

    private fun handleUndoDeleteAll() {
        val textToRestore = pendingClearedInputText ?: return
        pendingClearedInputText = null
        uiState.update { it.copy(pendingClearedInput = null) }
        currentInputConnection?.commitText(textToRestore, 1)
    }

    private fun handleEnter() {
        clearPendingDeleteUndo()
        val connection = currentInputConnection ?: return
        if (!connection.performEditorAction(EditorInfo.IME_ACTION_DONE)) {
            connection.commitText("\n", 1)
        }
    }

    private fun clearPendingDeleteUndo() {
        if (pendingClearedInputText == null && uiState.value.pendingClearedInput == null) return
        pendingClearedInputText = null
        uiState.update { it.copy(pendingClearedInput = null) }
    }
}

private const val MaxImeClearChars = 10_000
private const val ImeSequentialFillStepDelayMs = 90L
private const val ImeSequentialFillFocusDelayMs = 140L

private data class ImeRefreshResult(
    val value: MonicaImePasswordEntry
)

private data class ImeRefreshSnapshot(
    val results: List<ImeRefreshResult>,
    val authenticatorResults: List<MonicaImeAuthenticatorEntry>,
    val cardWalletResults: List<MonicaImeCardWalletEntry>,
    val databaseOptions: List<MonicaImeDatabaseOption>,
    val selectedScope: MonicaImeDatabaseScope
)

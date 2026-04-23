package takagi.ru.monica.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.isLocalPasswordOwnership
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.autofill_ng.AutofillSecretResolver
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
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
    private var pendingUnlockPanel: MonicaImePanel? = null
    private var unlockFlowInProgress = false
    private var suppressAutoUnlockUntilNextAttempt = false
    private val imeUnlockResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_IME_BIOMETRIC_RESULT) return

            val success = intent.getBooleanExtra(EXTRA_IME_BIOMETRIC_SUCCESS, false)
            val errorMessage = intent.getStringExtra(EXTRA_IME_BIOMETRIC_ERROR)
            val targetPanel = pendingUnlockPanel ?: MonicaImePanel.PASSWORDS
            unlockFlowInProgress = false
            pendingUnlockPanel = null

            if (success) {
                suppressAutoUnlockUntilNextAttempt = false
                uiState.update {
                    it.copy(
                        activePanel = targetPanel,
                        isAutofillPanelVisible = targetPanel != MonicaImePanel.KEYBOARD,
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
                        errorMessage = errorMessage ?: getString(takagi.ru.monica.R.string.ime_unlock_required)
                    )
                }
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
            IntentFilter(ACTION_IME_BIOMETRIC_RESULT),
            receiverFlags
        )
        observeDatabaseSources()

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
                        onKeyPressed = ::handleKeyPress,
                        onBackspace = ::handleBackspace,
                        onEnter = ::handleEnter,
                        onSpace = { handleKeyPress(" ") },
                        onShiftToggle = {
                            uiState.update { it.copy(isUppercase = !it.isUppercase) }
                        },
                        onKeyboardModeChange = { mode ->
                            uiState.update { it.copy(keyboardMode = mode) }
                        },
                        onOpenUnlockApp = ::openMonicaAppForUnlock,
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
        uiState.update {
            it.copy(
                activePackageName = effectivePackageName,
                activePanel = if (preserveAutofillPanel) previousState.activePanel else MonicaImePanel.KEYBOARD,
                isAutofillPanelVisible = preserveAutofillPanel,
                query = if (preserveAutofillPanel) previousState.query else "",
                selectedDatabaseScope = if (preserveAutofillPanel) {
                    previousState.selectedDatabaseScope
                } else {
                    MonicaImeDatabaseScope.All
                },
                errorMessage = null
            )
        }
        requestRefreshVaultEntries(force = true)
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

    private fun requestRefreshVaultEntries(force: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = serviceScope.launch {
            refreshVaultEntries(force = force)
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

    private fun handlePanelSelection(panel: MonicaImePanel) {
        if (panel == MonicaImePanel.KEYBOARD) {
            suppressAutoUnlockUntilNextAttempt = false
            uiState.update {
                it.copy(
                    activePanel = MonicaImePanel.KEYBOARD,
                    isAutofillPanelVisible = false,
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
                        databaseOptions = emptyList(),
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
                    errorMessage = null,
                    query = if (panel == MonicaImePanel.PASSWORDS) "" else it.query,
                    selectedDatabaseScope = if (panel == MonicaImePanel.PASSWORDS) {
                        it.selectedDatabaseScope
                    } else {
                        MonicaImeDatabaseScope.All
                    }
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
            if (
                force &&
                currentState.activePanel != MonicaImePanel.KEYBOARD &&
                currentState.isAutofillPanelVisible &&
                !unlockFlowInProgress &&
                !suppressAutoUnlockUntilNextAttempt
            ) {
                pendingUnlockPanel = currentState.activePanel
                openMonicaAppForUnlock()
            }
            uiState.update {
                it.copy(
                    entries = emptyList(),
                    autoLockMinutes = settings.autoLockMinutes
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
        val bitwardenLabel = getString(takagi.ru.monica.R.string.filter_bitwarden)
        val allDatabasesLabel = getString(takagi.ru.monica.R.string.password_picker_all_databases)
        val snapshot = withContext(Dispatchers.IO) {
            val keepassDatabases = database.localKeePassDatabaseDao().getAllDatabasesSync()
            val bitwardenVaults = database.bitwardenVaultDao().getAllVaults()
            val keepassLookup = keepassDatabases.associateBy { it.id }
            val bitwardenLookup = bitwardenVaults.associateBy { it.id }
            val databaseOptions = buildDatabaseOptions(
                localLabel = localLabel,
                keepassLabel = keepassLabel,
                bitwardenLabel = bitwardenLabel,
                allDatabasesLabel = allDatabasesLabel,
                keepassDatabases = keepassDatabases,
                bitwardenVaults = bitwardenVaults
            )
            val selectedScope = currentState.selectedDatabaseScope
                .takeIf { scope -> databaseOptions.any { it.scope == scope } }
                ?: MonicaImeDatabaseScope.All

            val results = database.passwordEntryDao()
                .getAllPasswordEntriesSync()
                .mapNotNull { entry ->
                    entry.toImeEntryOrNull(
                        keepassLookup = keepassLookup,
                        bitwardenLookup = bitwardenLookup,
                        localLabel = localLabel,
                        keepassLabel = keepassLabel,
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
                .take(if (force || query.isNotBlank()) 50 else 20)

            ImeRefreshSnapshot(
                results = results,
                databaseOptions = databaseOptions,
                selectedScope = selectedScope
            )
        }

        val entries = snapshot.results.map { it.value }

        uiState.update {
            it.copy(
                unlocked = true,
                entries = entries,
                databaseOptions = snapshot.databaseOptions,
                selectedDatabaseScope = snapshot.selectedScope,
                autoLockMinutes = settings.autoLockMinutes,
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
                it.copy(
                    unlocked = false,
                    entries = emptyList(),
                    databaseOptions = emptyList(),
                    selectedDatabaseScope = MonicaImeDatabaseScope.All,
                    errorMessage = getString(takagi.ru.monica.R.string.ime_unlock_required)
                )
            }
        }
        return unlocked
    }

    private fun entryMatchesPackage(entry: MonicaImePasswordEntry, activePackage: String): Boolean {
        if (activePackage.isBlank()) return false
        val packageHint = activePackage.substringAfterLast('.')
        return entry.packageName.equals(activePackage, ignoreCase = true) ||
            entry.website.contains(packageHint, ignoreCase = true) ||
            entry.title.contains(packageHint, ignoreCase = true)
    }

    private fun entryMatchesScope(
        entry: MonicaImePasswordEntry,
        scope: MonicaImeDatabaseScope
    ): Boolean {
        return when (scope) {
            MonicaImeDatabaseScope.All -> true
            MonicaImeDatabaseScope.Local -> isLocalPasswordOwnership(entry.keepassDatabaseId, entry.bitwardenVaultId)
            is MonicaImeDatabaseScope.KeePass -> entry.keepassDatabaseId == scope.databaseId
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

    private fun PasswordEntry.toImeEntryOrNull(
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
        bitwardenLabel: String
    ): ImeRefreshResult? {
        val decryptedPassword = AutofillSecretResolver.decryptPasswordOrNull(
            securityManager = securityManager,
            encryptedOrPlain = password,
            logTag = "MonicaIme"
        )

        if (username.isBlank() && decryptedPassword.isNullOrBlank()) {
            return null
        }

        return ImeRefreshResult(
            value = MonicaImePasswordEntry(
                id = id,
                title = title,
                username = username,
                website = website,
                packageName = appPackageName,
                password = decryptedPassword.orEmpty(),
                isFavorite = isFavorite,
                sourceLabel = resolveSourceLabel(
                    entry = this,
                    keepassLookup = keepassLookup,
                    bitwardenLookup = bitwardenLookup,
                    localLabel = localLabel,
                    keepassLabel = keepassLabel,
                    bitwardenLabel = bitwardenLabel
                ),
                keepassDatabaseId = keepassDatabaseId,
                bitwardenVaultId = bitwardenVaultId
            )
        )
    }

    private fun resolveSourceLabel(
        entry: PasswordEntry,
        keepassLookup: Map<Long, LocalKeePassDatabase>,
        bitwardenLookup: Map<Long, BitwardenVault>,
        localLabel: String,
        keepassLabel: String,
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
            else -> localLabel
        }
    }

    private fun buildDatabaseOptions(
        localLabel: String,
        keepassLabel: String,
        bitwardenLabel: String,
        allDatabasesLabel: String,
        keepassDatabases: List<LocalKeePassDatabase>,
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
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun handleKeyPress(text: String) {
        commitExternalText(text)
    }

    private fun commitExternalText(text: String) {
        if (text.isEmpty()) return
        currentInputConnection?.commitText(text, 1)
    }

    private fun handleBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun handleEnter() {
        val connection = currentInputConnection ?: return
        if (!connection.performEditorAction(EditorInfo.IME_ACTION_DONE)) {
            connection.commitText("\n", 1)
        }
    }
}

private data class ImeRefreshResult(
    val value: MonicaImePasswordEntry
)

private data class ImeRefreshSnapshot(
    val results: List<ImeRefreshResult>,
    val databaseOptions: List<MonicaImeDatabaseOption>,
    val selectedScope: MonicaImeDatabaseScope
)

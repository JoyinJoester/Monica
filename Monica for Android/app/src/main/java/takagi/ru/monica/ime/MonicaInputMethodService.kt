package takagi.ru.monica.ime

import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.BitwardenVault
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

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        securityManager = SecurityManager(applicationContext)
        settingsManager = SettingsManager(applicationContext)
        database = PasswordDatabase.getDatabase(applicationContext)

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
                        onFillEntry = ::fillCurrentField,
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
                        onPanelSelected = ::handlePanelSelection,
                        onDismiss = { requestHideSelf(0) }
                    )
                }
            }
        }
        return composeView!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        window?.window?.let { imeWindow ->
            imeWindow.navigationBarColor = Color.BLACK
            imeWindow.decorView.setBackgroundColor(Color.BLACK)
        }
        uiState.update {
            it.copy(
                activePackageName = info?.packageName.orEmpty(),
                activePanel = MonicaImePanel.KEYBOARD,
                isAutofillPanelVisible = false,
                query = "",
                selectedDatabaseScope = MonicaImeDatabaseScope.All,
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
        refreshJob?.cancel()
        composeView?.disposeComposition()
        composeView = null
        recomposer?.cancel()
        recomposer = null
        serviceScope.cancel()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    private fun openUnlockInMonica() {
        runCatching {
            startActivity(
                Intent(this, takagi.ru.monica.MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
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
        refreshJob = serviceScope.launch {
            refreshVaultEntries(force = force)
        }
    }

    private fun handlePanelSelection(panel: MonicaImePanel) {
        if (panel == MonicaImePanel.KEYBOARD) {
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
                openUnlockInMonica()
                return@launch
            }

            uiState.update {
                it.copy(
                    activePanel = panel,
                    isAutofillPanelVisible = true,
                    errorMessage = null,
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
        val query = currentState.query.trim()
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
                    if (result is ImeRefreshResult.UnlockRequired) {
                        true
                    } else {
                        val entry = (result as ImeRefreshResult.Entry).value
                        entryMatchesScope(entry, selectedScope) &&
                            queryMatches(entry, query)
                    }
                }
                .sortedWith(
                    compareByDescending<ImeRefreshResult> {
                        (it as? ImeRefreshResult.Entry)?.value?.let { entry ->
                            entryMatchesPackage(entry, activePackage)
                        } ?: false
                    }.thenByDescending {
                        (it as? ImeRefreshResult.Entry)?.value?.isFavorite ?: false
                    }.thenBy {
                        (it as? ImeRefreshResult.Entry)?.value?.title?.lowercase().orEmpty()
                    }
                )
                .take(if (force || query.isNotBlank()) 50 else 20)

            ImeRefreshSnapshot(
                results = results,
                databaseOptions = databaseOptions,
                selectedScope = selectedScope
            )
        }

        if (snapshot.results.any { it is ImeRefreshResult.UnlockRequired }) {
            uiState.update {
                it.copy(
                    unlocked = false,
                    entries = emptyList(),
                    databaseOptions = emptyList(),
                    selectedDatabaseScope = MonicaImeDatabaseScope.All,
                    autoLockMinutes = settings.autoLockMinutes,
                    errorMessage = getString(takagi.ru.monica.R.string.ime_unlock_required)
                )
            }
            return
        }

        val entries = snapshot.results.mapNotNull { (it as? ImeRefreshResult.Entry)?.value }

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
        if (!securityManager.isMasterPasswordSet()) {
            uiState.update { it.copy(unlocked = true, errorMessage = null) }
            return true
        }
        // vault 运行时已解锁（processCachedMdk 有值）说明本进程内已完成过验证，直接放行
        if (securityManager.isVaultRuntimeUnlocked()) {
            uiState.update { it.copy(unlocked = true, errorMessage = null) }
            return true
        }
        // 否则依赖 SessionManager 会话窗口判断
        SessionManager.updateAutoLockTimeout(autoLockMinutes)
        val unlocked = SessionManager.canSkipVerification(this)
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
            MonicaImeDatabaseScope.Local -> entry.keepassDatabaseId == null && entry.bitwardenVaultId == null
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
        return try {
            ImeRefreshResult.Entry(
                MonicaImePasswordEntry(
                    id = id,
                    title = title,
                    username = username,
                    website = website,
                    packageName = appPackageName,
                    password = securityManager.decryptData(password),
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
        } catch (error: Exception) {
            if (error is android.security.keystore.KeyPermanentlyInvalidatedException ||
                error is KeyStoreException ||
                error is UnrecoverableKeyException ||
                error.message?.contains("MDK not available", ignoreCase = true) == true
            ) {
                return ImeRefreshResult.UnlockRequired
            }
            null
        }
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

    private fun fillCurrentField(entry: MonicaImePasswordEntry) {
        val textToCommit = when {
            isPasswordField() -> entry.password
            entry.username.isNotBlank() -> entry.username
            else -> entry.password
        }
        commitExternalText(textToCommit)
        uiState.update {
            it.copy(
                activePanel = MonicaImePanel.KEYBOARD,
                isAutofillPanelVisible = false,
                query = "",
                selectedDatabaseScope = MonicaImeDatabaseScope.All,
                errorMessage = null
            )
        }
    }

    private fun isPasswordField(): Boolean {
        val inputType = currentInputEditorInfo?.inputType ?: return true
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> {
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
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

private sealed interface ImeRefreshResult {
    data class Entry(val value: MonicaImePasswordEntry) : ImeRefreshResult
    data object UnlockRequired : ImeRefreshResult
}

private data class ImeRefreshSnapshot(
    val results: List<ImeRefreshResult>,
    val databaseOptions: List<MonicaImeDatabaseOption>,
    val selectedScope: MonicaImeDatabaseScope
)

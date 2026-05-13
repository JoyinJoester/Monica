package takagi.ru.monica.autofill_ng

import android.app.Activity
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill_ng.builder.AutofillDatasetBuilder
import takagi.ru.monica.autofill_ng.core.AutofillLogger
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.MonicaPasswordDialogAuthScreen
import takagi.ru.monica.utils.BiometricAuthHelper
import takagi.ru.monica.utils.SettingsManager

class AutofillCipherCallbackActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ARGS = "extra_args"
        private const val EXTRA_ARGS_BUNDLE = "extra_args_bundle"
        private const val EXTRA_ARGS_TOKEN = "extra_args_token"
        private const val TAG = "AutofillCipherCallback"
        private val pendingArgsByToken = ConcurrentHashMap<String, Args>()

        fun getIntent(context: Context, args: Args): Intent {
            val token = UUID.randomUUID().toString()
            pendingArgsByToken[token] = args
            return Intent(context, AutofillCipherCallbackActivity::class.java).apply {
                putExtra(EXTRA_ARGS_TOKEN, token)
                putExtra(
                    EXTRA_ARGS_BUNDLE,
                    Bundle().apply {
                        classLoader = Args::class.java.classLoader
                        putParcelable(EXTRA_ARGS, args)
                    }
                )
                putExtra(EXTRA_ARGS, args)
            }
        }
    }

    @Parcelize
    data class Args(
        val passwordId: Long,
        val applicationId: String? = null,
        val webDomain: String? = null,
        val interactionIdentifier: String? = null,
        val interactionIdentifierAliases: ArrayList<String>? = null,
        val autofillIds: ArrayList<AutofillId>? = null,
        val autofillHints: ArrayList<String>? = null,
        val fieldSignatureKey: String? = null,
        val rememberLastFilled: Boolean = true,
        val requireAuthentication: Boolean = false,
    ) : Parcelable

    private var callbackArgs: Args? = null
    private var callbackArgsToken: String? = null
    private lateinit var securityManager: SecurityManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var biometricAuthHelper: BiometricAuthHelper
    private var biometricPromptShown = false
    private var resultPublished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        setContentView(R.layout.activity_transparent)
        callbackArgs = resolveArgsFromIntent(intent)
        securityManager = SecurityManager(applicationContext)
        settingsManager = SettingsManager(applicationContext)
        biometricAuthHelper = BiometricAuthHelper(this)

        val args = callbackArgs
        AutofillLogger.i(
            "CALLBACK",
            "Autofill callback activity started",
            metadata = mapOf(
                "hasArgs" to (args != null),
                "requireAuthentication" to (args?.requireAuthentication ?: false),
                "passwordId" to (args?.passwordId ?: -1L),
            )
        )
        if (args?.requireAuthentication == true) {
            startAuthentication()
        } else {
            lifecycleScope.launch {
                completeCipherAutofill()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callbackArgs = resolveArgsFromIntent(intent)
    }

    private fun startAuthentication() {
        lifecycleScope.launch {
            val biometricEnabled = settingsManager.settingsFlow.first().biometricEnabled
            val biometricAvailable = biometricAuthHelper.isBiometricAvailable()
            AutofillLogger.i(
                "CALLBACK",
                "Starting autofill authentication",
                metadata = mapOf(
                    "biometricEnabled" to biometricEnabled,
                    "biometricAvailable" to biometricAvailable,
                )
            )
            if (biometricEnabled && biometricAvailable) {
                showBiometricPrompt()
            } else {
                showPasswordAuthentication()
            }
        }
    }

    private fun showBiometricPrompt() {
        if (biometricPromptShown || resultPublished || isFinishing || isDestroyed) {
            return
        }
        biometricPromptShown = true
        AutofillLogger.i("CALLBACK", "Showing biometric prompt for autofill")
        runCatching {
            biometricAuthHelper.authenticate(
                activity = this@AutofillCipherCallbackActivity,
                title = getString(R.string.autofill_auth_title),
                subtitle = getString(R.string.autofill_auth_subtitle),
                description = getString(R.string.autofill_auth_description),
                negativeButtonText = getString(R.string.use_password),
                onSuccess = {
                    AutofillLogger.i("CALLBACK", "Biometric prompt succeeded")
                    if (securityManager.unlockVaultWithBiometric()) {
                        lifecycleScope.launch { completeCipherAutofill() }
                    } else {
                        AutofillLogger.w("CALLBACK", "Biometric unlock failed, falling back to password")
                        showPasswordAuthentication()
                    }
                },
                onError = { code, message ->
                    AutofillLogger.w("CALLBACK", "Biometric prompt error: $code $message")
                    showPasswordAuthentication()
                },
                onCancel = {
                    AutofillLogger.w("CALLBACK", "Biometric prompt cancelled")
                    cancelAndFinish("authentication_cancelled")
                },
            )
        }.onFailure { error ->
            biometricPromptShown = false
            AutofillLogger.w(
                "CALLBACK",
                "Biometric prompt failed to show, falling back to password: ${error.message.orEmpty()}"
            )
            showPasswordAuthentication()
        }
    }

    private fun showPasswordAuthentication() {
        AutofillLogger.i("CALLBACK", "Showing password authentication for autofill")
        setContent {
            MonicaPasswordDialogAuthScreen(
                settingsFlow = settingsManager.settingsFlow,
                appName = getString(R.string.app_name),
                title = getString(R.string.verify_identity),
                subtitle = getString(R.string.autofill_auth_subtitle),
                passwordLabel = getString(R.string.master_password),
                description = getString(R.string.enter_master_password),
                confirmText = getString(R.string.confirm),
                cancelText = getString(R.string.cancel),
                emptyError = getString(R.string.current_password_required),
                unsupportedCharacterError = getString(R.string.error_password_contains_unsupported_characters),
                minLengthError = getString(R.string.error_password_too_short),
                incorrectError = getString(R.string.password_incorrect),
                verifyPassword = { input -> securityManager.unlockVaultWithPassword(input) },
                onSuccess = {
                    lifecycleScope.launch { completeCipherAutofill() }
                },
                onCancel = { cancelAndFinish("authentication_cancelled") },
            )
        }
    }

    private suspend fun completeCipherAutofill() {
        val callbackArgs = callbackArgs ?: run {
            cancelAndFinish("missing_args")
            return
        }
        val repository = PasswordRepository(
            PasswordDatabase.getDatabase(applicationContext).passwordEntryDao()
        )
        val passwordEntry = withContext(Dispatchers.IO) {
            repository.getPasswordEntryById(callbackArgs.passwordId)
        } ?: run {
            cancelAndFinish("missing_password_entry")
            return
        }

        val accountValue = AccountFillPolicy.resolveAccountIdentifier(passwordEntry, securityManager)
        val decryptedPassword = AutofillSecretResolver.decryptPasswordOrNull(
            securityManager = securityManager,
            encryptedOrPlain = passwordEntry.password,
            logTag = TAG,
        )
        val resolvedTargets = resolveAutofillTargets(callbackArgs)
        if (resolvedTargets.ids.isEmpty()) {
            cancelAndFinish("missing_autofill_ids")
            return
        }
        val filledValues = resolveFilledValues(
            accountValue = accountValue,
            decryptedPassword = decryptedPassword,
            autofillIds = resolvedTargets.ids,
            autofillHints = resolvedTargets.hints,
        )
        if (filledValues.isEmpty()) {
            cancelAndFinish("no_resolved_values")
            return
        }

        val title = passwordEntry.title.ifBlank {
            getString(R.string.autofill_manual_entry_title)
        }
        val subtitle = accountValue.ifBlank {
            callbackArgs.webDomain
                ?: callbackArgs.applicationId
                ?: getString(R.string.app_name)
        }
        val menuPresentation = AutofillDatasetBuilder.RemoteViewsFactory.createPasswordEntry(
            context = this,
            title = title,
            username = subtitle
        )
        val fields = linkedMapOf<AutofillId, AutofillDatasetBuilder.FieldData?>()
        filledValues.forEach { (autofillId, value) ->
            fields[autofillId] = AutofillDatasetBuilder.FieldData(
                value = AutofillValue.forText(value),
                presentation = menuPresentation
            )
        }
        val dataset = AutofillDatasetBuilder.create(
            menuPresentation = menuPresentation,
            fields = fields
        ) { null }.build()

        withContext(Dispatchers.IO) {
            if (callbackArgs.rememberLastFilled) {
                rememberLastFilledCredential(
                    passwordId = passwordEntry.id,
                    primaryIdentifier = callbackArgs.interactionIdentifier,
                    aliases = callbackArgs.interactionIdentifierAliases.orEmpty(),
                )
            }
            rememberLearnedFieldSignature(callbackArgs.fieldSignatureKey)
        }

        AutofillLogger.i(
            "CALLBACK",
            "Returning authenticated dataset without picker UI",
            metadata = mapOf(
                "passwordId" to passwordEntry.id,
                "filledCount" to filledValues.size,
                "applicationId" to (callbackArgs.applicationId ?: "none"),
                "webDomain" to (callbackArgs.webDomain ?: "none"),
                "targetSource" to resolvedTargets.source,
            )
        )

        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
            }
        )
        finishWithoutAnimation()
    }

    private fun resolveArgsFromIntent(intent: Intent?): Args? {
        if (intent == null) return null
        callbackArgsToken = intent.getStringExtra(EXTRA_ARGS_TOKEN)
        callbackArgsToken
            ?.let { pendingArgsByToken[it] }
            ?.let { return it }

        intent.getBundleExtra(EXTRA_ARGS_BUNDLE)
            ?.apply { classLoader = Args::class.java.classLoader }
            ?.let { bundle ->
                val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bundle.getParcelable(EXTRA_ARGS, Args::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    bundle.getParcelable(EXTRA_ARGS)
                }
                if (args != null) return args
            }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ARGS, Args::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ARGS)
        }
    }

    private data class ResolvedAutofillTargets(
        val ids: List<AutofillId>,
        val hints: List<String>,
        val source: String,
    )

    private fun resolveAutofillTargets(callbackArgs: Args): ResolvedAutofillTargets {
        val assistStructure = getAssistStructureOrNull()
        if (assistStructure != null) {
            val parsedTargets = runCatching {
                val parser = EnhancedAutofillStructureParserV2()
                val parsed = parser.parse(assistStructure, respectAutofillOff = false)
                selectLoginFillableTargets(parsed.items)
            }.getOrDefault(emptyList())

            if (parsedTargets.isNotEmpty()) {
                return ResolvedAutofillTargets(
                    ids = parsedTargets.map { it.id },
                    hints = parsedTargets.map { it.hint.name },
                    source = "assist_structure",
                )
            }
        }

        return ResolvedAutofillTargets(
            ids = callbackArgs.autofillIds?.distinct().orEmpty(),
            hints = callbackArgs.autofillHints.orEmpty(),
            source = "callback_args",
        )
    }

    private fun getAssistStructureOrNull(): AssistStructure? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE)
        }
    }

    private fun selectLoginFillableTargets(items: List<ParsedItem>): List<ParsedItem> {
        if (items.isEmpty()) return emptyList()
        val filtered = items.filter { isLoginHint(it.hint) }
        if (filtered.isEmpty()) return emptyList()

        val deduped = linkedMapOf<String, ParsedItem>()
        filtered.sortedWith(
            compareByDescending<ParsedItem> { it.isFocused }
                .thenByDescending { loginHintPriority(it.hint) }
                .thenByDescending { it.accuracy.score }
                .thenBy { it.traversalIndex }
        ).forEach { item ->
            deduped.putIfAbsent(item.id.toString(), item)
        }
        return deduped.values.toList()
    }

    private fun loginHintPriority(hint: FieldHint): Int = when (hint) {
        FieldHint.PASSWORD, FieldHint.NEW_PASSWORD -> 3
        FieldHint.USERNAME, FieldHint.EMAIL_ADDRESS, FieldHint.PHONE_NUMBER -> 2
        else -> 0
    }

    private fun isLoginHint(hint: FieldHint): Boolean {
        return hint == FieldHint.USERNAME ||
            hint == FieldHint.EMAIL_ADDRESS ||
            hint == FieldHint.PHONE_NUMBER ||
            hint == FieldHint.PASSWORD ||
            hint == FieldHint.NEW_PASSWORD
    }

    private fun resolveFilledValues(
        accountValue: String,
        decryptedPassword: String?,
        autofillIds: List<AutofillId>,
        autofillHints: List<String>,
    ): LinkedHashMap<AutofillId, String> {
        val normalizedHints = autofillHints.map { it.trim().lowercase() }
        val hasPasswordTarget = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD.name.lowercase() ||
                it == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD.name.lowercase() ||
                it.contains("password") ||
                it.contains("pass")
        }
        if (hasPasswordTarget && decryptedPassword.isNullOrBlank()) {
            Log.w(TAG, "Authentication callback canceled: password decryption unavailable")
            return linkedMapOf()
        }

        val fillEmailWithAccount = AccountFillPolicy.shouldFillEmailWithAccount(applicationContext)
        val hasUsernameHint = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name.lowercase() ||
                it.contains("username")
        }
        val hasPhoneHint = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name.lowercase() ||
                it.contains("phone") ||
                it.contains("mobile") ||
                it.contains("tel")
        }
        val hasEmailHint = normalizedHints.any {
            it == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name.lowercase() ||
                it.contains("email")
        }
        val hasAccountHint = hasUsernameHint || hasPhoneHint
        val allowAccountInEmailField =
            fillEmailWithAccount || accountValue.contains("@") || (!hasAccountHint && hasEmailHint)

        val filledValues = linkedMapOf<AutofillId, String>()
        autofillIds.forEachIndexed { index, autofillId ->
            val normalizedHint = autofillHints.getOrNull(index)?.trim()?.lowercase().orEmpty()
            val value = when {
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.USERNAME.name.lowercase() ||
                    normalizedHint.contains("username") -> accountValue
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER.name.lowercase() ||
                    normalizedHint.contains("phone") ||
                    normalizedHint.contains("mobile") ||
                    normalizedHint.contains("tel") -> accountValue
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS.name.lowercase() ||
                    normalizedHint.contains("email") -> if (allowAccountInEmailField) accountValue else null
                normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.PASSWORD.name.lowercase() ||
                    normalizedHint == EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD.name.lowercase() ||
                    normalizedHint.contains("password") ||
                    normalizedHint.contains("pass") -> decryptedPassword
                else -> null
            }
            if (!value.isNullOrBlank()) {
                filledValues[autofillId] = value
            }
        }

        if (filledValues.isNotEmpty()) {
            return filledValues
        }

        Log.w(TAG, "No strict hint matched in callback, trying controlled fallback")
        autofillIds.forEachIndexed { index, autofillId ->
            val normalizedHint = autofillHints.getOrNull(index)?.lowercase().orEmpty()
            val fallbackValue = when {
                normalizedHint.contains("pass") -> decryptedPassword
                normalizedHint.contains("user") ||
                    normalizedHint.contains("email") ||
                    normalizedHint.contains("phone") ||
                    normalizedHint.contains("mobile") ||
                    normalizedHint.contains("tel") ||
                    normalizedHint.contains("号码") ||
                    normalizedHint.contains("手机号") ||
                    normalizedHint.contains("account") ||
                    normalizedHint.contains("login") -> accountValue
                autofillIds.size == 1 -> if (accountValue.isNotBlank()) accountValue else decryptedPassword
                index == 0 -> accountValue
                index == 1 -> decryptedPassword
                else -> null
            }
            if (!fallbackValue.isNullOrBlank()) {
                filledValues[autofillId] = fallbackValue
            }
        }
        return filledValues
    }

    private suspend fun rememberLastFilledCredential(
        passwordId: Long,
        primaryIdentifier: String?,
        aliases: List<String>,
    ) {
        val normalizedIdentifiers = buildList {
            primaryIdentifier
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            aliases
                .asSequence()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .forEach(::add)
        }.distinct()
        if (normalizedIdentifiers.isEmpty()) return

        val preferences = AutofillPreferences(applicationContext)
        normalizedIdentifiers.forEach { identifier ->
            preferences.completeAutofillInteraction(identifier, passwordId)
        }
    }

    private suspend fun rememberLearnedFieldSignature(fieldSignatureKey: String?) {
        val signatureKey = fieldSignatureKey?.trim()?.lowercase().orEmpty()
        if (signatureKey.isBlank()) return
        AutofillPreferences(applicationContext).markFieldSignatureLearned(signatureKey)
    }

    private fun cancelAndFinish(reason: String) {
        AutofillLogger.w("CALLBACK", "Cancel autofill callback: $reason")
        resultPublished = true
        setResult(Activity.RESULT_CANCELED)
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        resultPublished = true
        callbackArgsToken?.let { pendingArgsByToken.remove(it) }
        finish()
        overridePendingTransition(0, 0)
    }
}

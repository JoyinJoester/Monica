package takagi.ru.monica.autofill_ng.privilege

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import takagi.ru.monica.autofill_ng.protection.AccessibilityRecoveryResult
import takagi.ru.monica.autofill_ng.protection.AutofillProtection

enum class PrivilegeBackend { SHIZUKU, ROOT }
enum class RootStatus { IDLE, AUTHORIZING, GRANTED, DENIED, UNAVAILABLE, TIMED_OUT }

/** Direct su access is opt-in; a Shizuku failure never silently requests root. */
object PrivilegeAccess {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("system_privilege", Context.MODE_PRIVATE)
    private val mutableStatus = MutableStateFlow(RootStatus.IDLE)
    val status = mutableStatus.asStateFlow()
    private val mutableChanges = MutableStateFlow(0L)
    val changes = mutableChanges.asStateFlow()
    internal val transaction = Mutex()
    private val consentLock = Any()
    private var consentRevision = 0L

    fun backend(context: Context): PrivilegeBackend =
        if (prefs(context).getBoolean("use_root", false)) PrivilegeBackend.ROOT else PrivilegeBackend.SHIZUKU

    fun hasRootConsent(context: Context): Boolean = prefs(context).getBoolean("root_consent", false)

    fun rootAllowed(context: Context): Boolean = hasRootConsent(context) &&
        status.value in setOf(RootStatus.IDLE, RootStatus.GRANTED)

    fun select(context: Context, backend: PrivilegeBackend) = synchronized(consentLock) {
        if (this.backend(context) == backend) return@synchronized
        consentRevision++
        // Switching transport needs a fresh decision to enable automatic recovery.
        AutofillProtection.setEnhancedRecoveryEnabled(context, false)
        prefs(context).edit().putBoolean("use_root", backend == PrivilegeBackend.ROOT).apply()
        mutableChanges.value++
        AutofillProtection.refresh()
    }

    suspend fun authorize(context: Context): Boolean =
        authorize(context) { RootCommand.execute("/system/bin/id -u", 30) }

    internal suspend fun authorize(context: Context, identity: () -> RootResult): Boolean {
        // Capture before waiting: revocation also invalidates requests queued behind another operation.
        val revision = synchronized(consentLock) { consentRevision }
        return transaction.withLock {
            val canStart = synchronized(consentLock) {
                if (revision != consentRevision || backend(context) != PrivilegeBackend.ROOT) false
                else { mutableStatus.value = RootStatus.AUTHORIZING; true }
            }
            if (!canStart) return@withLock false
            val result = try {
                withContext(Dispatchers.IO) {
                    try {
                        val result = identity()
                        if (result.code == 0 && result.output.trim() == "0") RootStatus.GRANTED else RootStatus.DENIED
                    } catch (_: RootTimeout) { RootStatus.TIMED_OUT }
                    catch (_: java.io.IOException) { RootStatus.UNAVAILABLE }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { RootStatus.DENIED }
                }
            } finally {
                synchronized(consentLock) {
                    if (mutableStatus.value == RootStatus.AUTHORIZING) mutableStatus.value = RootStatus.IDLE
                }
            }
            synchronized(consentLock) {
                // Check and save together so a delayed grant can never undo a concurrent disable/switch.
                if (revision != consentRevision || backend(context) != PrivilegeBackend.ROOT) return@synchronized false
                mutableStatus.value = result
                prefs(context).edit().putBoolean("root_consent", result == RootStatus.GRANTED).apply()
                mutableChanges.value++
                AutofillProtection.refresh()
                result == RootStatus.GRANTED
            }
        }
    }

    fun disable(context: Context) = synchronized(consentLock) {
        consentRevision++
        prefs(context).edit().putBoolean("root_consent", false).apply()
        mutableStatus.value = RootStatus.IDLE
        mutableChanges.value++
        if (backend(context) == PrivilegeBackend.ROOT) AutofillProtection.setEnhancedRecoveryEnabled(context, false)
        AutofillProtection.refresh()
    }

    // Caller holds transaction and has checked current user consent. The helper has no IPC server.
    internal fun restartAccessibility(context: Context): AccessibilityRecoveryResult = try {
        val result = RootCommand.execute(RootCommand.recovery(context.applicationInfo.sourceDir,
            android.os.Process.myUid() / 100_000), 60)
        check(result.code == 0)
        val response = result.output.lineSequence().singleOrNull { it.startsWith("MONICA_RECOVERY:") }
        val code = requireNotNull(response?.substringAfter(':')?.toIntOrNull())
        mutableStatus.value = RootStatus.GRANTED
        AccessibilityRecoveryResult.fromCode(code)
    } catch (failure: Exception) {
        recordFailure(failure)
        AccessibilityRecoveryResult.FAILED
    }

    private fun recordFailure(failure: Exception) {
        mutableStatus.value = when (failure) {
            is RootTimeout -> RootStatus.TIMED_OUT
            is java.io.IOException -> RootStatus.UNAVAILABLE
            else -> RootStatus.DENIED
        }
        AutofillProtection.refresh()
    }

    internal fun settings(arguments: List<String>): String {
        // Authorization is checked at transaction entry. Rollback must still be allowed
        // after a failed command or a concurrently disabled preference.
        try {
            val result = RootCommand.execute(RootCommand.settings(arguments))
            check(result.code == 0 && !Regex("(?im)^\\s*(?:error|.*exception|permission denial|permission denied|bad arguments|unknown command)").containsMatchIn(result.output))
            mutableStatus.value = RootStatus.GRANTED
            return result.output
        } catch (failure: Exception) {
            // Stop further background attempts. Do not fall back to another transport.
            recordFailure(failure)
            throw failure
        }
    }
}

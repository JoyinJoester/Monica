package takagi.ru.monica.autofill_ng.protection

enum class ProtectionRuntime { STOPPED, STARTING, RUNNING }
enum class AccessibilityConnection { DISABLED, DISCONNECTED, CONNECTED }
enum class ShizukuAccess { UNAVAILABLE, UNSUPPORTED, ROOT_MODE, NEEDS_PERMISSION, ADB_AUTHORIZED }

data class AutofillProtectionState(
    val backgroundEnabled: Boolean = false,
    val enhancedRecoveryEnabled: Boolean = false,
    val runtime: ProtectionRuntime = ProtectionRuntime.STOPPED,
    val accessibility: AccessibilityConnection = AccessibilityConnection.DISABLED,
    val batteryExempt: Boolean = false,
    val backgroundRestricted: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val shizuku: ShizukuAccess = ShizukuAccess.UNAVAILABLE,
    val useRoot: Boolean = false,
    val rootAuthorized: Boolean = false,
) {
    fun canRecoverAccessibility(): Boolean =
        backgroundEnabled && enhancedRecoveryEnabled &&
            accessibility == AccessibilityConnection.DISCONNECTED &&
            (if (useRoot) rootAuthorized else shizuku == ShizukuAccess.ADB_AUTHORIZED)

    fun shouldRecoverAutomatically(now: Long, lastAttempt: Long): Boolean =
        runtime == ProtectionRuntime.RUNNING && canRecoverAccessibility() &&
            (lastAttempt == 0L || now - lastAttempt >= RECOVERY_COOLDOWN_MS)

    companion object {
        const val RECOVERY_COOLDOWN_MS = 5 * 60 * 1000L

        fun accessibilityConnection(enabled: Boolean, connected: Boolean): AccessibilityConnection = when {
            !enabled -> AccessibilityConnection.DISABLED
            connected -> AccessibilityConnection.CONNECTED
            else -> AccessibilityConnection.DISCONNECTED
        }
    }
}

enum class AccessibilityRecoveryResult(val code: Int) {
    RESTART_REQUESTED(0),
    NOT_ENABLED(1),
    SETTINGS_CHANGED(2),
    FAILED(3),
    NOT_AUTHORIZED(4),
    COOLDOWN(5),
    CONNECTED(6),
    ;

    companion object {
        fun fromCode(code: Int): AccessibilityRecoveryResult = entries.firstOrNull { it.code == code } ?: FAILED
    }
}

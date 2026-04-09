package takagi.ru.monica.security.lock

/**
 * Main application startup authentication state.
 *
 * Scope:
 * - Used only by main-process startup and foreground restoration.
 * - `disablePasswordVerification` may affect this state only for app startup.
 * - Secondary verification points must use their own explicit password or biometric checks.
 */
data class MainAppAccessState(
    val isFirstTime: Boolean,
    val bypassEnabled: Boolean,
    val canRestoreSession: Boolean,
    val reason: String
) {
    val canEnterMainApp: Boolean
        get() = bypassEnabled || canRestoreSession
}

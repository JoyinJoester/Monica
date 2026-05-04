package takagi.ru.monica.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondarySessionImmediateLockRegressionGuardTest {

    @Test
    fun immediateAutoLockKeepsShortSecondaryWindow() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecondarySessionManager.kt"
        ).readText()

        assertTrue(
            "IME/Autofill secondary sessions need a short grace window when main app auto-lock is immediate.",
            source.contains("IMMEDIATE_LOCK_SECONDARY_GRACE_MS")
        )
        assertTrue(
            "Immediate auto-lock must not expire a secondary session in the same millisecond it is granted.",
            source.contains("autoLockMinutes <= 0 -> elapsedMillis >= IMMEDIATE_LOCK_SECONDARY_GRACE_MS")
        )
    }

    @Test
    fun mainSessionExpiryDoesNotClearSecondarySessionDuringSkipCheck() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SessionManager.kt"
        ).readText()

        assertTrue(
            "Session expiry checks for the main app must not clear isolated IME/Autofill secondary sessions.",
            source.contains("markLocked(clearSecondarySession = false)")
        )
    }

    @Test
    fun secondaryVaultAccessChecksSecondarySessionBeforeSharedSession() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val accessBody = source.substringAfter("fun canAccessVaultNow(")
            .substringBefore("fun canAccessVaultMaterialNow()")

        assertTrue(
            "Secondary entry points must check the isolated secondary session before evaluating the main app session.",
            accessBody.contains("val secondarySessionActive = hasActiveSecondarySession")
        )
        assertTrue(
            "When the secondary session is active, avoid triggering main-session expiry side effects.",
            accessBody.contains("if (secondarySessionActive)")
        )
    }

    @Test
    fun imeUnlockUsesDedicatedSingleTaskSurface() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val activityBlock = manifest.substringAfter("android:name=\".ime.ImeUnlockActivity\"")
            .substringBefore("/>")

        assertTrue(
            "IME unlock must stay out of the main app task to avoid showing the app unlock page behind it.",
            activityBlock.contains("android:taskAffinity=\":ime_unlock\"")
        )
        assertTrue(
            "IME unlock should reuse the existing verification surface instead of stacking activities.",
            activityBlock.contains("android:launchMode=\"singleTask\"")
        )
    }

    @Test
    fun imeUnlockSerializesBiometricAndPasswordSurfaces() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ime/ImeUnlockActivity.kt"
        ).readText()

        assertTrue(
            "IME unlock needs an explicit surface state so biometric and password prompts do not overlap.",
            source.contains("private enum class AuthSurface")
        )
        assertTrue(
            "Fallback to password must be scheduled only once after biometric closes.",
            source.contains("passwordFallbackScheduled")
        )
        assertTrue(
            "Password fallback should wait briefly for the biometric prompt to dismiss.",
            source.contains("postDelayed")
        )
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            candidates += File(dir, relativePath)
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to find project file: $relativePath from ${System.getProperty("user.dir")}")
    }
}

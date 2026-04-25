package takagi.ru.monica.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricUnlockRegressionGuardTest {

    @Test
    fun mainPasswordLoginUsesFullVaultUnlockPath() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()
        val authenticateBody = source.substringAfter("fun authenticate(password: String): Boolean {")
            .substringBefore("fun restoreAuthenticatedUiState()")

        assertTrue(
            "Main app password login must repair MDK/KeyStore state by using unlockVaultWithPassword.",
            authenticateBody.contains("securityManager.unlockVaultWithPassword(password)")
        )
        assertFalse(
            "Do not regress to verifyMasterPassword here; it can allow login while biometric key repair failed.",
            authenticateBody.contains("securityManager.verifyMasterPassword(password)")
        )
    }

    @Test
    fun mdkWrapperRebuildHandlesInvalidatedAndUnrecoverableKeystoreKeys() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val persistBody = source.substringAfter("private fun persistKeystoreWrappedMdk(mdk: ByteArray): Boolean {")
            .substringBefore("private fun persistCompatKeystoreWrappedMdk")
        val ensureBody = source.substringAfter("private fun ensureMdkInitializedWithPassword(")
            .substringBefore("private fun ensureMdkKeystoreWrapper()")

        assertTrue(
            "Wrapper rebuild must recover from biometric enrollment invalidating the secure key.",
            persistBody.contains("KeyPermanentlyInvalidatedException")
        )
        assertTrue(
            "Wrapper rebuild must recover from AndroidKeyStore returning an unrecoverable stale key.",
            persistBody.contains("UnrecoverableKeyException")
        )
        assertTrue(
            "Invalid secure aliases must be deleted so a fresh wrapper can be persisted.",
            persistBody.contains("deleteSecureKeyAlias(KEY_ALIAS_DATA)")
        )
        assertTrue(
            "Missing wrapper aliases must force a wrapper rebuild after password unlock.",
            ensureBody.contains("hasKeystoreBlob = false")
        )
        assertTrue(
            "Password unlock must refresh the keystore wrapper even when an old blob exists.",
            ensureBody.contains("compatibility wrapper refresh after password unlock")
        )
        assertTrue(
            "Password unlock recovery must not accidentally write a fresh auth-bound wrapper on devices with active biometric auth windows.",
            ensureBody.contains("persistCompatKeystoreWrappedMdk(actualMdk)")
        )
        assertTrue(
            "Password unlock must clear stale MDK auth cooldown after rebuilding key material.",
            ensureBody.contains("mdkAuthUnavailableUntilMillis = 0L")
        )
    }

    @Test
    fun biometricUnlockClearsPreviousMdkCooldownBeforeReadingKeyMaterial() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/security/SecurityManager.kt"
        ).readText()
        val biometricBody = source.substringAfter("fun unlockVaultWithBiometric(): Boolean {")
            .substringBefore("fun isVaultRuntimeUnlocked()")

        assertTrue(
            "A previous failed MDK read must not block a fresh successful biometric auth attempt.",
            biometricBody.contains("mdkAuthUnavailableUntilMillis = 0L")
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

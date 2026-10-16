package takagi.ru.monica.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.KeyStore
import takagi.ru.monica.attachments.storage.AttachmentKeyVault

/** Run seed against the pre-fix APK, then verify after installing the new APK with -r. */
class BiometricPolicyUpgradeTest {
    @Test fun preservesExistingVaultAcrossAuthenticationPolicyUpgrade() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(android.os.Process.myUid() / 100000 > 0 && args.getString("authUpgrade") != null)
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "auth-policy-upgrade-320-"
        val root = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getApplicationInfo() = android.content.pm.ApplicationInfo(base.applicationInfo).apply {
                dataDir = root.absolutePath
            }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(prefix + name, mode)
        }
        val manifest = File(root, "fixture.json")
        val password = "Synthetic-Upgrade-320!"
        SecurityManager.clearRuntimeUnlockCache()
        try {
            when (args.getString("authUpgrade")) {
                "seed" -> {
                    check(!manifest.exists()) { "Existing upgrade fixture must be verified first" }
                    val security = SecurityManager(context)
                    assertTrue(security.setMasterPassword(password))
                    security.setBiometricEnabled(true)
                    val item = security.encryptData("preserved-password")
                    val otp = security.encryptDataLegacyCompat("JBSWY3DPEHPK3PXP")
                    val key = ByteArray(32) { it.toByte() }
                    val attachmentKey = AttachmentKeyVault(security).wrap(key)
                    security.putProtectedString("passkey_private_key_v1_upgrade_fixture", "synthetic-private-key")
                    manifest.writeText(JSONObject().put("item", item).put("otp", otp)
                        .put("attachmentKey", attachmentKey).put("aliases", aliases()).toString())
                }
                "verify" -> {
                    check(manifest.exists())
                    val before = JSONObject(manifest.readText())
                    // Opening the upgraded app does not rotate/delete its existing aliases.
                    assertEquals(before.getString("aliases"), aliases())
                    val rawBefore = context.getSharedPreferences(SecurePreferencesStore.MONICA, 0).all.toMap()
                    assertTrue(SecureStorageStartup.prepare(context) is SecureStartupResult.Ready)
                    assertEquals(rawBefore, context.getSharedPreferences(SecurePreferencesStore.MONICA, 0).all)
                    val security = SecurityManager(context)
                    assertFalse(security.unlockVaultWithPassword("wrong-password"))
                    assertTrue(security.unlockVaultWithPassword(password))
                    assertEquals("preserved-password", security.decryptData(before.getString("item")))
                    assertEquals("JBSWY3DPEHPK3PXP", security.decryptData(before.getString("otp")))
                    assertArrayEquals(ByteArray(32) { it.toByte() }, AttachmentKeyVault(security).unwrap(before.getString("attachmentKey")))
                    assertEquals("synthetic-private-key", security.getProtectedString("passkey_private_key_v1_upgrade_fixture"))
                    assertTrue(security.isBiometricEnabled())
                    SecurityManager.clearRuntimeUnlockCache()
                    assertTrue(SecurityManager(context).unlockVaultWithPassword(password))
                    assertEquals(before.getString("aliases"), aliases())
                }
                "cleanup" -> {
                    listOf(SecurePreferencesStore.MONICA, SecurePreferencesStore.BITWARDEN).forEach {
                        base.deleteSharedPreferences(prefix + it)
                    }
                    check(root.parentFile == base.cacheDir && root.name == prefix)
                    root.deleteRecursively()
                }
                else -> error("Unknown upgrade phase")
            }
        } finally { SecurityManager.clearRuntimeUnlockCache() }
    }

    private fun aliases(): String = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .aliases().toList().sorted().joinToString("\n")
}

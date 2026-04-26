package takagi.ru.monica.autofill_ng

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillCallbackArgsRegressionGuardTest {

    @Test
    fun cipherCallbackArgsUseTokenAndBundleFallbackForVendorPendingIntentCompatibility() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/AutofillCipherCallbackActivity.kt"
        ).readText()

        assertTrue(
            "Autofill callback args need a token cache fallback because some vendor Android 10 PendingIntent auth flows drop or fail to unparcel direct Args extras.",
            source.contains("EXTRA_ARGS_TOKEN") &&
                source.contains("pendingArgsByToken[token] = args") &&
                source.contains("pendingArgsByToken[it]")
        )
        assertTrue(
            "Autofill callback args should also be bundled with an explicit classLoader, matching Bitwarden's safer callback-data pattern.",
            source.contains("EXTRA_ARGS_BUNDLE") &&
                source.contains("classLoader = Args::class.java.classLoader") &&
                source.contains("getBundleExtra(EXTRA_ARGS_BUNDLE)")
        )
        assertTrue(
            "The callback activity must refresh args from onNewIntent when launchMode=singleTop reuses the activity.",
            source.contains("override fun onNewIntent(intent: Intent)") &&
                source.contains("callbackArgs = resolveArgsFromIntent(intent)")
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

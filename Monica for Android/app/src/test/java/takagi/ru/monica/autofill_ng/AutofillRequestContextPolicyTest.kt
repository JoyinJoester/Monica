package takagi.ru.monica.autofill_ng

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillRequestContextPolicyTest {
    @Test fun localHtmlAppAllowsOnlyExplicitBindings() {
        assertFalse(AutofillRequestContextPolicy.allowPackageMatching("com.cn21.ecloud", null, true))
        assertTrue(AutofillRequestContextPolicy.allowExactPackageMatching("com.cn21.ecloud", null, true, "file"))
        for (scheme in listOf(null, "https", "http", "content", "about")) {
            assertFalse(AutofillRequestContextPolicy.allowExactPackageMatching("com.cn21.ecloud", null, true, scheme))
        }
        assertFalse(AutofillRequestContextPolicy.allowExactPackageMatching("com.android.chrome", null, true, "file"))
        assertFalse(AutofillRequestContextPolicy.allowExactPackageMatching("", null, true, "file"))
    }

    @Test
    fun knownBrowserWithoutOriginCannotUsePackageFallback() {
        assertFalse(
            AutofillRequestContextPolicy.allowPackageMatching(
                packageName = "com.android.chrome",
                webDomain = null,
                isWebView = false,
            )
        )
    }

    @Test
    fun webViewWithoutOriginCannotUsePackageFallbackEvenForUnknownHostApp() {
        assertFalse(
            AutofillRequestContextPolicy.allowPackageMatching(
                packageName = "com.example.host",
                webDomain = null,
                isWebView = true,
            )
        )
    }

    @Test
    fun nativeAppKeepsExactPackageMatching() {
        assertTrue(
            AutofillRequestContextPolicy.allowPackageMatching(
                packageName = "com.example.nativeapp",
                webDomain = null,
                isWebView = false,
            )
        )
    }
}

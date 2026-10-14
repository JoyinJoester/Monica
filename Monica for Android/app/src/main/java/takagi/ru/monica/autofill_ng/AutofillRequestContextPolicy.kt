package takagi.ru.monica.autofill_ng

import java.util.Locale

/**
 * Package matching is useful for native apps, but unsafe as a fallback for a
 * browser page whose web origin was not reported by AssistStructure.
 */
internal object AutofillRequestContextPolicy {
    private val knownBrowserPackages = setOf(
        "com.android.browser",
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.google.android.apps.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.fenix.nightly",
        "io.github.forkmaintainers.iceraven",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.mi.globalbrowser",
        "mark.via",
        "mark.via.gp",
        "com.UCMobile".lowercase(Locale.ROOT),
        "com.uc.browser.en",
        "com.uc.browser.hd",
        "com.tencent.mtt",
        "com.baidu.browser.apps",
        "com.qihoo.browser",
        "com.ijinshan.browser_fast",
        "com.opera.browser",
        "com.brave.browser",
        "com.kiwibrowser.browser",
    )

    fun isKnownBrowser(packageName: String): Boolean =
        packageName.trim().lowercase(Locale.ROOT) in knownBrowserPackages

    fun allowPackageMatching(
        packageName: String,
        webDomain: String?,
        isWebView: Boolean,
    ): Boolean {
        if (!webDomain.isNullOrBlank()) return true
        if (isWebView) return false
        return !isKnownBrowser(packageName)
    }
}

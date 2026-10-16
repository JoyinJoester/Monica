package takagi.ru.monica.autofill_ng.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillSessionGrantStoreTest {

    @Test
    fun grantIsLimitedToAppAndHostButSurvivesDynamicFieldChanges() {
        var now = 1_000L
        val store = AutofillSessionGrantStore(
            ttlMillis = 30_000L,
            elapsedRealtime = { now },
        )
        val context = AutofillGrantContext(
            packageName = "com.example.app",
            webDomain = "login.example.com",
            interactionIdentifier = "web:login.example.com",
            fieldSignatureKey = "username|password",
        )

        store.grant(context)

        assertTrue(store.isGranted(context))
        assertFalse(store.isGranted(context.copy(packageName = "com.example.other")))
        assertFalse(store.isGranted(context.copy(webDomain = "evil.example")))
        assertTrue(store.isGranted(context.copy(interactionIdentifier = null)))
        assertTrue(store.isGranted(context.copy(fieldSignatureKey = "password")))
        assertTrue(store.isGranted(context.copy(fieldSignatureKey = null)))
    }

    @Test
    fun grantExpiresUsingMonotonicTimeAndCanBeCleared() {
        var now = 5_000L
        val store = AutofillSessionGrantStore(
            ttlMillis = 30_000L,
            elapsedRealtime = { now },
        )
        val context = AutofillGrantContext(
            packageName = "com.example.app",
            webDomain = null,
            interactionIdentifier = "app:com.example.app",
            fieldSignatureKey = "password",
        )

        store.grant(context)
        now += 29_999L
        assertTrue(store.isGranted(context))

        now += 1L
        assertFalse(store.isGranted(context))

        store.grant(context)
        store.clear()
        assertFalse(store.isGranted(context))
    }

    @Test
    fun defaultWindowIsExactlyTwoMinutesAndReadsNeverRenewIt() {
        var now = 10_000L
        val store = AutofillSessionGrantStore(elapsedRealtime = { now })
        val context = AutofillGrantContext("app", null, null, "username")
        store.grant(context)
        for (offset in listOf(30_001L, 60_001L, 119_999L)) {
            now = 10_000L + offset
            assertTrue(store.isGranted(context.copy(fieldSignatureKey = "password")))
        }
        now = 130_000L
        assertFalse(store.isGranted(context))
        now = 10_000L
        assertFalse(store.isGranted(context))
    }

    @Test
    fun processRestartAndClockRollbackRequireNewVerification() {
        var now = 1000L
        val context = AutofillGrantContext("app", null, null, null)
        val store = AutofillSessionGrantStore(elapsedRealtime = { now })
        store.grant(context)
        assertFalse(AutofillSessionGrantStore(elapsedRealtime = { now }).isGranted(context))
        now = 999L
        assertFalse(store.isGranted(context))
        now = 1001L
        assertFalse(store.isGranted(context))
    }

    @Test
    fun onlyOneTargetIsGrantedAndEmptyTargetsFailClosed() {
        val store = AutofillSessionGrantStore(elapsedRealtime = { 1000L })
        val app = AutofillGrantContext("app", null, null, null)
        val web = app.copy(webDomain = "login.example.com")
        store.grant(app)
        store.grant(web)
        assertFalse(store.isGranted(app))
        assertFalse(store.isGranted(web.copy(webDomain = "other.login.example.com")))
        assertFalse(store.isGranted(web.copy(packageName = "other.browser")))
        assertTrue(store.isGranted(web))
        store.grant(app.copy(packageName = "  "))
        assertFalse(store.isGranted(web))
        assertFalse(store.isGranted(app.copy(packageName = "")))
    }

    @Test
    fun explicitReauthenticationStartsANewWindow() {
        var now = 1000L
        val store = AutofillSessionGrantStore(elapsedRealtime = { now })
        val context = AutofillGrantContext("app", null, null, null)
        store.grant(context)
        now += 110_000L
        store.grant(context)
        now += 119_999L
        assertTrue(store.isGranted(context))
        now++
        assertFalse(store.isGranted(context))
    }

    @Test
    fun requestUriFactoryDoesNotTreatAndroidAppPackageAsWebDomain() {
        val appContext = AutofillGrantContext.fromRequestUri(
            packageName = "com.example.app",
            requestUri = "androidapp://com.example.app",
            interactionIdentifier = "app:com.example.app",
            fieldSignatureKey = "password",
        )
        val webContext = AutofillGrantContext.fromRequestUri(
            packageName = "com.example.browser",
            requestUri = "https://www.example.com/login",
            interactionIdentifier = "web:example.com",
            fieldSignatureKey = "username|password",
        )

        assertTrue(appContext.webDomain == null)
        assertTrue(webContext.webDomain == "example.com")
    }
}

package takagi.ru.monica.autofill_ng

import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.*
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import java.net.InetAddress
import java.net.ServerSocket

/** Opt-in audit harness. Only synthetic fixtures; never shipped in the application APK. */
@RunWith(AndroidJUnit4::class)
class MozillaAutofillAuditSessionTest {
    @Test fun serveSyntheticFixtures() = runBlocking {
        assumeTrue("This interactive audit requires explicit opt-in",
            InstrumentationRegistry.getArguments().getString("mozillaAudit") == "true")
        val base = AutofillFlowInstrumentedTest()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val settings = takagi.ru.monica.utils.SettingsManager(context)
        val oldScreenshotProtection = settings.settingsFlow.first().screenshotProtectionEnabled
        val database = PasswordDatabase.getDatabase(context)
        val ids = mutableListOf<Long>()
        val history = PasswordHistoryManager(context)
        var oldHistory: List<PasswordGenerationHistory>? = null
        try {
            base.setUp()
            oldHistory = history.historyFlow.first()
            val security = SecurityManager(context)
            val loginId = database.passwordEntryDao().insertPasswordEntry(PasswordEntry(
                title = "MozAudit Login", website = "https://mozilla.github.io",
                username = security.encryptData("mozilla.audit@example.test"),
                password = security.encryptData("Mozilla-Audit-42!")))
            ids += loginId
            suspend fun wallet(title: String, item: SecureItem) {
                val id = database.passwordEntryDao().insertPasswordEntry(PasswordEntry(title = title, website = "", username = "", password = ""))
                ids += id
                val snapshot = EmbeddedWalletContent.create(item)
                database.customFieldDao().insert(CustomField(entryId = id,
                    title = EmbeddedWalletContent.fieldName(snapshot.kind),
                    value = security.encryptData(snapshot.encode()), isProtected = true))
            }
            wallet("MozAudit", SecureItem(itemType = ItemType.BILLING_ADDRESS, title = "Address",
                itemData = Json.encodeToString(BillingAddressData(fullName = "Jane Quinn Doe", firstName = "Independent", middleName = "Middle", lastName = "Parts", addressLine3 = "Building 7", district = "Santa Clara",
                    homeEmail = "home.audit@example.test", homePhone = "+12025550143", workEmail = "work.audit@example.test", workPhone = "+12025550144", company = "Example Test Labs",
                    streetAddress = "123 Test Street", apartment = "Suite 45", city = "Mountain View", stateProvince = "CA",
                    postalCode = "94043", country = "US", phone = "+12025550142", email = "address.audit@example.test"))))
            wallet("MozAudit", SecureItem(itemType = ItemType.BANK_CARD, title = "Card",
                itemData = Json.encodeToString(BankCardData(cardNumber = security.encryptData("4242424242424242"),
                    cardholderName = "Jane Quinn Doe", cardholderFirstName = "CardFirst", cardholderMiddleName = "CardMiddle", cardholderLastName = "CardLast", expiryMonth = "08", expiryYear = "2030", cvv = security.encryptData("123")))))
            wallet("MozAudit", SecureItem(itemType = ItemType.DOCUMENT, title = "Identity",
                itemData = Json.encodeToString(DocumentData(documentType = DocumentType.PASSPORT,
                    documentNumber = security.encryptData("TEST-PASSPORT-42"), fullName = "Jane Quinn Doe",
                    firstName = "Independent", middleName = "Middle", lastName = "Parts", district = "Santa Clara",
                    homeEmail = "home.audit@example.test", homePhone = "+12025550143", workEmail = "work.audit@example.test", workPhone = "+12025550144", address1 = "123 Test Street",
                    address2 = "Suite 45", address3 = "Building 7", city = "Mountain View", stateProvince = "CA",
                    postalCode = "94043", country = "US", company = "Example Test Labs",
                    phone = "+12025550142", email = "address.audit@example.test"))))
            val ui = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val server = ServerSocket(37915, 4, InetAddress.getByName("127.0.0.1"))
            server.soTimeout = 1000
            val end = SystemClock.elapsedRealtime() + 100 * 60_000
            var done = false
            server.use {
                while (!done && SystemClock.elapsedRealtime() < end) {
                    val client = try { server.accept() } catch (_: java.net.SocketTimeoutException) { continue }
                    client.use { socket ->
                        socket.soTimeout = 5000
                        val input = JSONObject(socket.getInputStream().bufferedReader().readLine())
                        val output = try {
                            when (input.getString("op")) {
                                "dump" -> dump(ui)
                                "structure" -> {
                                    var result = JSONObject()
                                    instrumentation.runOnMainSync {
                                        val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
                                        val rows = JSONArray()
                                        activity?.let { current ->
                                            AutofillTypedValues.fromIntent(current.intent).values.forEach { node ->
                                                val attrs = JSONObject()
                                                node.htmlInfo?.attributes?.filter { it.first in setOf("autocomplete", "ua-autofill-hints", "computed-autofill-hints", "section", "name", "id", "type") }
                                                    ?.forEach { attrs.put(it.first, it.second) }
                                                if (attrs.length() > 0 || !node.autofillHints.isNullOrEmpty()) rows.put(JSONObject()
                                                    .put("attrs", attrs).put("hints", JSONArray(node.autofillHints.orEmpty().toList()))
                                                    .put("tag", node.htmlInfo?.tag).put("type", node.autofillType)
                                                    .put("inputType", node.inputType))
                                            }
                                        }
                                        result = JSONObject().put("nodes", rows)
                                    }
                                    result
                                }

                                "refresh" -> { SessionManager.markUnlocked(); JSONObject().put("ok", true) }
                                "auth" -> {
                                    AutofillPreferences(context).setPasswordSuggestionEnabled(input.optBoolean("generator", false))
                                    AutofillPreferences(context).setRequestSaveDataEnabled(input.optBoolean("save", false))
                                    takagi.ru.monica.utils.SettingsManager(context).updateAutofillAuthRequired(input.optBoolean("required", false))
                                    if (input.has("biometric")) settings.updateBiometricEnabled(input.getBoolean("biometric"))
                                    if (input.has("keepUnlocked")) settings.updateAutofillKeepUnlocked(input.getBoolean("keepUnlocked"))
                                    if (input.optBoolean("lock")) {
                                        SessionManager.markLocked()
                                        takagi.ru.monica.autofill_ng.auth.AutofillSessionGrants.clear()
                                        SecurityManager.clearRuntimeUnlockCache()
                                    }
                                    JSONObject().put("ok", true)
                                }
                                "done" -> { done = true; JSONObject().put("ok", true) }
                                else -> JSONObject().put("error", "Unknown operation")
                            }
                        } catch (error: Exception) { JSONObject().put("error", error.javaClass.simpleName) }
                        socket.getOutputStream().write((output.toString() + "\n").toByteArray())
                    }
                }
            }
            check(done) { "Host did not finish the audit before its deadline" }
        } finally {
            try {
                ids.forEach { database.passwordEntryDao().deletePasswordEntryById(it) }
            } finally {
                try {
                    oldHistory?.let { history.importHistory(it) }
                } finally {
                    try { base.tearDown() } finally {
                        settings.updateScreenshotProtectionEnabled(oldScreenshotProtection)
                    }
                }
            }
        }
    }

    private fun dump(ui: UiAutomation): JSONObject {
        val nodes = JSONArray()
        fun visit(node: AccessibilityNodeInfo?, window: Int, depth: Int) {
            if (node == null || depth > 50 || nodes.length() > 2000) return
            if (!node.refresh()) return
            val rect = Rect().also(node::getBoundsInScreen)
            nodes.put(JSONObject().put("window", window).put("text", node.text?.toString().orEmpty())
                .put("description", node.contentDescription?.toString().orEmpty()).put("class", node.className?.toString().orEmpty())
                .put("package", node.packageName?.toString().orEmpty()).put("editable", node.isEditable)
                .put("password", node.isPassword).put("clickable", node.isClickable).put("visible", node.isVisibleToUser)
                .put("focused", node.isFocused).put("bounds", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))))
            repeat(node.childCount) { visit(node.getChild(it), window, depth + 1) }
        }
        visit(ui.rootInActiveWindow, android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION, 0)
        ui.windows.forEach { visit(it.root, it.type, 0) }
        return JSONObject().put("nodes", nodes)
    }
}

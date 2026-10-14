package takagi.ru.monica.autofill_ng

import android.app.UiAutomation
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.autofill_ng.auth.AutofillSessionGrants
import takagi.ru.monica.autofill_ng.fixture.AutofillFormFixtureActivity
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.autofill_ng.service.AutofillOtpNotificationService
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.service.MonicaAccessibilityService
import takagi.ru.monica.utils.SettingsManager

/**
 * Runs against Android's real AutofillManager and AccessibilityService binder.
 * Requires an isolated Android user; see docs/autofill-quality-validation.md.
 */
@RunWith(AndroidJUnit4::class)
class AutofillFlowInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixturePackage = instrumentation.context.packageName
    private val dao by lazy { PasswordDatabase.getDatabase(context).passwordEntryDao() }
    private val settings by lazy { SettingsManager(context) }
    private val preferences by lazy { AutofillPreferences(context) }
    private lateinit var security: SecurityManager
    private lateinit var ui: UiAutomation
    private var oldSettings: AppSettings? = null
    private var oldIme: String? = null
    private var accessibilityEnabledByTest = false
    private val insertedIds = mutableListOf<Long>()
    private val insertedValidatorIds = mutableListOf<Long>()
    private val insertedAttachments = mutableListOf<takagi.ru.monica.attachments.model.Attachment>()
    private val attachmentExecutor by lazy {
        takagi.ru.monica.attachments.executor.LocalAttachmentExecutor(context,
            takagi.ru.monica.attachments.storage.AttachmentStorage(context),
            takagi.ru.monica.attachments.storage.AttachmentKeyVault(security))
    }
    private var oldPreferences: List<Boolean>? = null
    private var oldOtpPreferences: Triple<Boolean, Boolean, Int>? = null
    private var expectedUsername: String? = null
    private var expectedPassword: String? = null
    private var notificationTestStarted = false
    private var notificationChannelExisted = false
    private val notifications by lazy { context.getSystemService(NotificationManager::class.java) }

    @Before fun setUp() = runBlocking {
        assumeTrue("Use a dedicated Android test user and opt in explicitly",
            android.os.Process.myUid() / 100000 > 0 &&
                InstrumentationRegistry.getArguments().getString("autofillIsolatedUser") == "true")
        ui = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        ui.serviceInfo = ui.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        oldIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        oldSettings = settings.settingsFlow.first()
        oldPreferences = listOf(preferences.isAutofillEnabled.first(),
            preferences.isPasswordSuggestionEnabled.first(), preferences.isV2RespectAutofillOffEnabled.first(),
            preferences.isRequestSaveDataEnabled.first())
        oldOtpPreferences = Triple(preferences.isOtpNotificationEnabled.first(), preferences.isAutoCopyOtpEnabled.first(),
            preferences.otpNotificationDuration.first())
        security = SecurityManager(context)
        if (!security.isMasterPasswordSet()) security.setMasterPassword(MASTER_PASSWORD)
        check(security.unlockVaultWithPassword(MASTER_PASSWORD)) {
            "This user has another vault. Use the dedicated empty test user; never reset it here."
        }
        settings.updateAutofillKeepUnlocked(false)
        settings.updateAutofillAuthRequired(false)
        settings.updateBiometricEnabled(false)
        settings.updateScreenshotProtectionEnabled(false)
        settings.updateAutoLockMinutes(5)
        preferences.setAutofillEnabled(true)
        preferences.setPasswordSuggestionEnabled(false)
        preferences.setV2RespectAutofillOffEnabled(true)
        preferences.setRequestSaveDataEnabled(false)
        preferences.setOtpNotificationEnabled(false)
        preferences.setAutoCopyOtpEnabled(false)
        SessionManager.attachAppContext(context)
        SessionManager.markUnlocked()
        insertedIds += dao.insertPasswordEntry(PasswordEntry(
            title = NATIVE_TITLE, website = "", appPackageName = fixturePackage,
            username = security.encryptData(AutofillFormFixtureActivity.USERNAME),
            password = security.encryptData(AutofillFormFixtureActivity.PASSWORD),
        ))
        insertedIds += dao.insertPasswordEntry(PasswordEntry(
            title = WEB_TITLE, website = AutofillFormFixtureActivity.ORIGIN,
            username = security.encryptData(AutofillFormFixtureActivity.USERNAME),
            password = security.encryptData(AutofillFormFixtureActivity.PASSWORD),
        ))
    }

    @After fun tearDown(): Unit = runBlocking {
        // Dismiss this test's Android SaveUI before another fixture opens.
        if (::ui.isInitialized) {
            repeat(2) { ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }
        }
        val notificationCleanupFailure = runCatching { if (notificationTestStarted) {
            context.stopService(Intent(context, AutofillOtpNotificationService::class.java))
            notifications.cancel(OTP_NOTIFICATION_ID)
            val deadline = SystemClock.elapsedRealtime() + 2000
            while (notifications.activeNotifications.any { it.id == OTP_NOTIFICATION_ID } && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(25)
            }
            if (!notificationChannelExisted) notifications.deleteNotificationChannel(OTP_CHANNEL_ID)
        } }.exceptionOrNull()
        insertedValidatorIds.chunked(500).forEach { PasswordDatabase.getDatabase(context).secureItemDao().deleteItemsByIds(it) }
        insertedAttachments.forEach { attachmentExecutor.delete(it) }
        insertedIds.forEach { dao.deletePasswordEntryById(it) }
        oldSettings?.let {
            settings.updateAutofillKeepUnlocked(it.autofillKeepUnlocked)
            settings.updateAutofillAuthRequired(it.autofillAuthRequired)
            settings.updateBiometricEnabled(it.biometricEnabled)
            settings.updateScreenshotProtectionEnabled(it.screenshotProtectionEnabled)
            settings.updateAutoLockMinutes(it.autoLockMinutes)
            AutofillSessionGrants.clear()
        }
        oldIme?.let { selectIme(it) }
        oldPreferences?.let {
            preferences.setAutofillEnabled(it[0])
            preferences.setPasswordSuggestionEnabled(it[1])
            preferences.setV2RespectAutofillOffEnabled(it[2])
            preferences.setRequestSaveDataEnabled(it[3])
        }
        oldOtpPreferences?.let {
            preferences.setOtpNotificationEnabled(it.first)
            preferences.setAutoCopyOtpEnabled(it.second)
            preferences.setOtpNotificationDuration(it.third)
        }
        if (accessibilityEnabledByTest) {
            val user = android.os.Process.myUid() / 100000
            val component = ComponentName(context, MonicaAccessibilityService::class.java)
            val others = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != component }
            if (others.isEmpty()) shell("settings --user $user delete secure enabled_accessibility_services")
            else shell("settings --user $user put secure enabled_accessibility_services ${componentArgument(others.joinToString(":"))}")
            val end = SystemClock.elapsedRealtime() + 2000
            while (MonicaAccessibilityService.isCredentialFillAvailable(context) && SystemClock.elapsedRealtime() < end) Thread.sleep(25)
        }
        notificationCleanupFailure?.let { throw it }
    }

    @Test fun systemPickerFillsEmbeddedBillingAddressIntoNativeForm() = runBlocking {
        embeddedWalletForm("wallet_address", takagi.ru.monica.data.SecureItem(
            itemType = takagi.ru.monica.data.ItemType.BILLING_ADDRESS, title = "Office",
            itemData = Json.encodeToString(takagi.ru.monica.data.model.BillingAddressData(
                streetAddress = "123 Main Street", city = "Shanghai", stateProvince = "Shanghai", postalCode = "200000", country = "CN"))),
            "street=OK city=OK region=OK country=OK zip=OK")
    }

    @Test fun systemPickerFillsEmbeddedBankCardIntoNativeForm() = runBlocking {
        embeddedWalletForm("wallet_card", takagi.ru.monica.data.SecureItem(
            itemType = takagi.ru.monica.data.ItemType.BANK_CARD, title = "Visa",
            itemData = Json.encodeToString(takagi.ru.monica.data.model.BankCardData(
                security.encryptData("4242424242424242"), "Test Person", "08", "2030", security.encryptData("123")))),
            "card=OK cvv=OK")
    }

    @Test fun systemPickerFillsEmbeddedDocumentIntoNativeForm() = runBlocking {
        embeddedWalletForm("wallet_document", takagi.ru.monica.data.SecureItem(
            itemType = takagi.ru.monica.data.ItemType.DOCUMENT, title = "Passport",
            itemData = Json.encodeToString(takagi.ru.monica.data.model.DocumentData(
                takagi.ru.monica.data.model.DocumentType.PASSPORT, security.encryptData("P-12345"), "Test Person"))),
            "document=OK person=OK")
    }

    private suspend fun embeddedWalletForm(scenario: String, item: takagi.ru.monica.data.SecureItem, expected: String) {
        val owner = dao.insertPasswordEntry(PasswordEntry(title = "Wallet fixture", website = "", username = "", password = ""))
        insertedIds += owner
        val snapshot = takagi.ru.monica.data.model.EmbeddedWalletContent.create(item)
        PasswordDatabase.getDatabase(context).customFieldDao().insert(takagi.ru.monica.data.CustomField(entryId = owner,
            title = takagi.ru.monica.data.model.EmbeddedWalletContent.fieldName(snapshot.kind),
            value = security.encryptData(snapshot.encode()), isProtected = true))
        open(scenario)
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_manual_entry_title) ||
            it.contentDescription?.toString() == context.getString(R.string.autofill_manual_entry_title) })
        tap(waitNode { it.text?.toString() == "Wallet fixture · ${item.title}" })
        expectStatus(expected)
        val screenshot = ui.takeScreenshot()
        if (screenshot != null) {
            val dir = java.io.File(context.filesDir, "wallet-autofill-tests").apply { mkdirs() }
            java.io.File(dir, "$scenario.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            screenshot.recycle()
        }
    }

    @Test fun groupedDropdownFillsSecondAccountsSecondPasswordAndItsOwnOtp() = runBlocking {
        groupedSystemFill(inline = false)
    }

    @Test fun groupedInlineFillsSecondAccountsSecondPasswordAndItsOwnOtp() = runBlocking {
        groupedSystemFill(inline = true)
    }

    @Test fun groupedManualPickerFillsSecondAccountsSecondPasswordAndItsOwnOtp() = runBlocking {
        groupedSystemFill(inline = false, manual = true)
    }

    private suspend fun groupedSystemFill(inline: Boolean, manual: Boolean = false) {
        prepareOtpNotificationTest()
        val project = java.util.UUID.randomUUID().toString()
        val groups = listOf(
            takagi.ru.monica.data.model.ProjectCredentialGroup.Group(username = "group-personal", otp = "JBSWY3DPEHPK3PXP",
                passwords = listOf(takagi.ru.monica.data.model.ProjectCredentialGroup.Password(value = "personal-1"))),
            takagi.ru.monica.data.model.ProjectCredentialGroup.Group(username = "group-work", otp = OTP_SECRET,
                passwords = listOf(takagi.ru.monica.data.model.ProjectCredentialGroup.Password(value = "work-1"),
                    takagi.ru.monica.data.model.ProjectCredentialGroup.Password(value = "work-2"))))
        // Remove only this test's generic native suggestion to keep inline candidates visible.
        dao.deletePasswordEntryById(insertedIds.first())
        for (row in takagi.ru.monica.data.model.ProjectCredentialGroup.rows(groups)) {
            val id = dao.insertPasswordEntry(PasswordEntry(title = "Grouped autofill fixture", username = security.encryptData(row.username),
                password = security.encryptData(row.password.value), authenticatorKey = security.encryptData(row.otp),
                passwordGroupId = project, appPackageName = fixturePackage, website = ""))
            insertedIds += id
            PasswordDatabase.getDatabase(context).customFieldDao().insert(takagi.ru.monica.data.CustomField(entryId = id,
                title = takagi.ru.monica.data.model.ProjectCredentialGroup.FIELD, value = row.metadata.forProject(project).raw.toString()))
        }
        expectedUsername = "group-work"
        expectedPassword = "work-2"
        open(if (inline) "inline" else "standard")
        focusFirstField()
        if (manual) tap(waitNode(scroll = true) { it.text?.toString() == context.getString(R.string.autofill_manual_entry_title) ||
            it.contentDescription?.toString() == context.getString(R.string.autofill_manual_entry_title) })
        val number = context.getString(R.string.project_credential_password_number, 2)
        val suggestion = waitNode(scroll = true) { node ->
            val label = "${node.text?.toString().orEmpty()} ${node.contentDescription?.toString().orEmpty()}"
            label.contains("group-work") && label.contains(number) &&
                (!inline || node.window?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        }
        ui.takeScreenshot()?.let { bitmap ->
            val dir = java.io.File(context.filesDir, "group-system-tests").apply { mkdirs() }
            java.io.File(dir, if (manual) "manual.png" else if (inline) "inline.png" else "dropdown.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        assertNoOtpNotificationFor(300)
        tap(suggestion)
        if (manual) tap(waitNode(scroll = true) { it.text?.toString() == context.getString(R.string.autofill) })
        expectStatus("user=OK password=OK")
        expectOtpNotification("Grouped autofill fixture")
    }

    @Test fun systemAutofillFillsStandardNativeLoginWithoutVerification() {
        open("standard")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
    }

    @Test fun registrationSuggestsAccountWithoutOverwritingNewPasswords() {
        open("signup")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=ABSENT")
        expectStatus("new=EMPTY confirmation=EMPTY")
    }

    @Test fun changePasswordFillsOldPasswordAndGeneratesOnlyTheNewPair() = runBlocking {
        preferences.setPasswordSuggestionEnabled(true)
        open("change")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
        expectStatus("new=EMPTY confirmation=EMPTY")
        tap(waitNode { it.contentDescription?.toString() == "fixture-field-2" })
        tap(waitNode { it.text?.toString() == context.getString(R.string.password_suggestion_title) })
        tap(waitNode { it.text?.toString() == context.getString(R.string.password_suggestion_accept) })
        expectStatus("new=MATCH confirmation=SET")
        expectStatus("user=OK password=OK")
    }

    @Test fun lockedChangePasswordKeepsNewFieldsEmptyAfterVerificationReturn() = runBlocking {
        settings.updateAutofillAuthRequired(true)
        SessionManager.markLocked()
        open("change")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        verifyAndSelectCredential()
        expectStatus("user=OK password=OK")
        expectStatus("new=EMPTY confirmation=EMPTY")
    }

    @Test fun frameworkSavesTheNewPasswordAfterChangeFormCommit() = runBlocking {
        preferences.setRequestSaveDataEnabled(true)
        open("change")
        fillFromSystem(NATIVE_TITLE)
        setFixtureText(2,"Updated-synthetic-42!")
        setFixtureText(3,"Updated-synthetic-42!")
        submitAndReviewSavedPassword()
    }

    @Test fun frameworkCarriesUsernameAcrossStepsWhenSavingNewPassword() = runBlocking {
        preferences.setRequestSaveDataEnabled(true)
        open("step")
        fillFromSystem(NATIVE_TITLE)
        tap(waitNode { it.text?.toString().equals("Next step", ignoreCase = true) })
        focusFirstField()
        waitNode { it.text?.toString() == NATIVE_TITLE }
        setFixtureText(0,"Updated-synthetic-42!")
        submitAndReviewSavedPassword()
    }

    private fun setFixtureText(index: Int, value: String) {
        assertTrue(waitNode { it.contentDescription?.toString() == "fixture-field-$index" }
            .performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)
            }))
    }

    private suspend fun submitAndReviewSavedPassword() {
        ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        tap(waitNode { it.text?.toString().equals("Submit fixture",ignoreCase=true) })
        tap(waitNode { it.viewIdResourceName == "android:id/autofill_save_yes" ||
            (it.packageName?.toString() == "android" && it.text?.toString().equals("Save",ignoreCase=true)) })
        waitNode { it.text?.toString() == context.getString(R.string.autofill_save_review_title) }
        tap(waitNode { it.text?.toString() == NATIVE_TITLE })
        val save=waitNode { (it.text?.toString() == context.getString(R.string.save) ||
            it.contentDescription?.toString() == context.getString(R.string.save)) && it.isEnabled }
        Thread.sleep(600)
        tap(save)
        val end=SystemClock.elapsedRealtime()+15_000
        while (SystemClock.elapsedRealtime()<end && dao.getPasswordEntryById(insertedIds.first())?.let {
            security.decryptData(it.password) } != "Updated-synthetic-42!") delay(100)
        assertEquals("Updated-synthetic-42!",security.decryptData(requireNotNull(dao.getPasswordEntryById(insertedIds.first())).password))
    }

    @Test fun saveReviewUpdatesChosenEntryAndPreservesNotesAndSibling() = runBlocking {
        val original=requireNotNull(dao.getPasswordEntryById(insertedIds.first()))
            .copy(username=AutofillFormFixtureActivity.USERNAME,notes="Preserve synthetic notes",passwordGroupId=java.util.UUID.randomUUID().toString())
        dao.updatePasswordEntry(original)
        val siblingId=dao.insertPasswordEntry(original.copy(id=0,password=security.encryptData("sibling-password")))
        insertedIds += siblingId
        val attachmentBytes = ByteArray(1024) { (it % 251).toByte() }
        val attachment = attachmentExecutor.writeFromBytes(
            takagi.ru.monica.attachments.model.AttachmentOwner.password(original.id),
            "synthetic-autofill.bin", "application/octet-stream", attachmentBytes)
        insertedAttachments += attachment
        val attachmentDao = PasswordDatabase.getDatabase(context).attachmentDao()
        val attachmentId = attachmentDao.insert(attachment)
        context.startActivity(Intent(context,AutofillSaveTransparentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AutofillSaveTransparentActivity.EXTRA_USERNAME,AutofillFormFixtureActivity.USERNAME)
            putExtra(AutofillSaveTransparentActivity.EXTRA_PASSWORD,"Updated-synthetic-42!")
            putExtra(AutofillSaveTransparentActivity.EXTRA_PACKAGE_NAME,fixturePackage)
        })
        waitNode { it.text?.toString() == context.getString(R.string.autofill_save_review_title) }
        tap(waitNode { it.text?.toString() == NATIVE_TITLE })
        val save=waitNode { (it.contentDescription?.toString() == context.getString(R.string.save) ||
            it.text?.toString() == context.getString(R.string.save)) && it.isEnabled }
        // The title loads before all grouped fields; wait until the new draft is exposed to the editor.
        Thread.sleep(600)
        tap(save)
        val deadline=SystemClock.elapsedRealtime()+15_000
        var updated=dao.getPasswordEntryById(original.id)
        while (updated?.let { security.decryptData(it.password) } != "Updated-synthetic-42!" && SystemClock.elapsedRealtime()<deadline) {
            delay(100);updated=dao.getPasswordEntryById(original.id)
        }
        assertEquals("Updated-synthetic-42!",security.decryptData(requireNotNull(updated).password))
        assertEquals(original.notes,updated.notes)
        assertEquals(original.appPackageName,updated.appPackageName)
        assertEquals("sibling-password",security.decryptData(requireNotNull(dao.getPasswordEntryById(siblingId)).password))
        val savedAttachment = attachmentDao.getActiveByParent(original.id).single { it.id == attachmentId }
        assertEquals(attachment.copy(id = attachmentId), savedAttachment)
        assertArrayEquals(attachmentBytes, attachmentExecutor.openDecrypted(savedAttachment).use { it.readBytes() })
    }

    @Test fun saveReviewUpdatesAnExtraProjectAccountAndPreservesMetadata() = runBlocking {
        val database = PasswordDatabase.getDatabase(context)
        val fields = database.customFieldDao()
        val categoryDao = database.categoryDao()
        val category = takagi.ru.monica.data.Category(name = "Autofill synthetic folder")
        val folderId = categoryDao.insert(category)
        try {
            val project = java.util.UUID.randomUUID().toString()
            val original = requireNotNull(dao.getPasswordEntryById(insertedIds.first())).copy(
                username = AutofillFormFixtureActivity.USERNAME, notes = "Keep project notes",
                categoryId = folderId, passwordGroupId = project, isFavorite = true)
            dao.updatePasswordEntry(original)
            val second = original.copy(id = 0, username = "second-account", password = security.encryptData("second-old"))
            val secondId = dao.insertPasswordEntry(second)
            insertedIds += secondId
            val groups = listOf(
                takagi.ru.monica.data.model.ProjectCredentialGroup.Group(username = original.username,
                    passwords = listOf(takagi.ru.monica.data.model.ProjectCredentialGroup.Password(value = AutofillFormFixtureActivity.PASSWORD))),
                takagi.ru.monica.data.model.ProjectCredentialGroup.Group(username = second.username,
                    passwords = listOf(takagi.ru.monica.data.model.ProjectCredentialGroup.Password(value = "second-old"))))
            val rows = takagi.ru.monica.data.model.ProjectCredentialGroup.rows(groups)
            listOf(original.id, secondId).forEachIndexed { index, id ->
                fields.insert(takagi.ru.monica.data.CustomField(entryId = id,
                    title = takagi.ru.monica.data.model.ProjectCredentialGroup.FIELD,
                    value = rows[index].metadata.forProject(project).raw.toString()))
                fields.insert(takagi.ru.monica.data.CustomField(entryId = id, title = "Synthetic custom field", value = "Keep custom value"))
            }
            context.startActivity(Intent(context,AutofillSaveTransparentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AutofillSaveTransparentActivity.EXTRA_USERNAME, second.username)
                putExtra(AutofillSaveTransparentActivity.EXTRA_PASSWORD, "Updated-synthetic-42!")
                putExtra(AutofillSaveTransparentActivity.EXTRA_PACKAGE_NAME, fixturePackage)
            })
            waitNode { it.text?.toString() == context.getString(R.string.autofill_save_review_title) }
            tap(waitNode { it.text?.toString() == NATIVE_TITLE })
            tap(waitNode { it.contentDescription?.toString() == context.getString(R.string.save) && it.isEnabled })
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (SystemClock.elapsedRealtime() < deadline && dao.getPasswordEntryById(secondId)?.let {
                security.decryptData(it.password) } != "Updated-synthetic-42!") delay(100)
            val saved = requireNotNull(dao.getPasswordEntryById(secondId))
            assertEquals("Updated-synthetic-42!", security.decryptData(saved.password))
            assertEquals(AutofillFormFixtureActivity.PASSWORD, security.decryptData(requireNotNull(dao.getPasswordEntryById(original.id)).password))
            assertEquals(second.username, saved.username)
            assertEquals(original.notes, saved.notes)
            assertEquals(folderId, saved.categoryId)
            assertTrue(saved.isFavorite)
            listOf(original.id, secondId).forEachIndexed { index, id ->
                val stored = fields.getFieldsByEntryIdSync(id)
                assertEquals("Keep custom value", stored.single { it.title == "Synthetic custom field" }.value)
                val meta = requireNotNull(takagi.ru.monica.data.model.ProjectCredentialGroup.parse(
                    stored.single { it.title == takagi.ru.monica.data.model.ProjectCredentialGroup.FIELD }.value))
                assertEquals(rows[index].metadata.groupId, meta.groupId)
                assertEquals(rows[index].metadata.passwordId, meta.passwordId)
            }
            assertTrue(database.passwordHistoryDao().getHistoryByEntryIdSync(secondId).any {
                security.decryptData(it.password) == "second-old" })
        } finally {
            insertedIds.forEach { dao.deletePasswordEntryById(it) }
            categoryDao.delete(category.copy(id = folderId))
        }
    }

    @Test fun lockedSaveRequiresVerificationAndCancellationDoesNotWrite() = runBlocking {
        settings.updateAutofillAuthRequired(true)
        SessionManager.markLocked()
        val original = requireNotNull(dao.getPasswordEntryById(insertedIds.first()))
        context.startActivity(Intent(context,AutofillSaveTransparentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AutofillSaveTransparentActivity.EXTRA_USERNAME, AutofillFormFixtureActivity.USERNAME)
            putExtra(AutofillSaveTransparentActivity.EXTRA_PASSWORD, "Do-not-save")
            putExtra(AutofillSaveTransparentActivity.EXTRA_PACKAGE_NAME, fixturePackage)
        })
        waitNode { it.isEditable && it.packageName?.toString() == context.packageName }
        tap(waitNode { it.text?.toString() == context.getString(R.string.cancel) })
        delay(500)
        assertEquals(original, dao.getPasswordEntryById(original.id))
        assertFalse(SessionManager.isUnlocked.value)
    }

    @Test fun smallAndLargeVaultLongFormLatency() = runBlocking {
        suspend fun measure(label: String, scenario: String) {
            val samples = mutableListOf<Long>()
            repeat(5) {
                open(scenario)
                val start = SystemClock.elapsedRealtime()
                fillFromSystem(NATIVE_TITLE)
                expectStatus("user=OK password=OK")
                samples += SystemClock.elapsedRealtime() - start
            }
            android.util.Log.i("AutofillFlowPerf", "$label samplesMs=${samples.joinToString(",")}")
        }
        measure("seededRows=2 nodes=short", "standard")
        val encrypted = security.encryptData("Synthetic unrelated password")
        PasswordDatabase.getDatabase(context).withTransaction {
            repeat(1000) { index -> insertedIds += dao.insertPasswordEntry(PasswordEntry(
                title = "Synthetic unrelated $index", website = "https://unrelated-$index.example.test",
                username = "synthetic-account", password = encrypted)) }
        }
        measure("seededRows=1002 extraNodes=300", "long")
    }

    @Test fun dropdownAllowsComposingAndTypingWithoutRefocusing() {
        repeat(10) {
            open("standard")
            focusFirstField()
            waitNode { it.text?.toString() == NATIVE_TITLE }
            typeUsernameFromKeyboard()
        }
    }

    @Test fun lockedDropdownAllowsTypingWithoutOpeningVerification() = runBlocking {
        settings.updateAutofillAuthRequired(true)
        SessionManager.markLocked()
        AutofillSessionGrants.clear()
        repeat(10) {
            open("standard")
            focusFirstField()
            waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) }
            typeUsernameFromKeyboard()
        }
    }

    @Test fun webDropdownAllowsComposingAndTypingWithoutRefocusing() {
        repeat(5) {
            open("web")
            waitNode { it.contentDescription?.toString() == "fixture-status" && it.text?.contains("web=READY") == true }
            focusFirstField(web = true)
            waitNode { it.text?.toString() == WEB_TITLE }
            typeUsernameFromKeyboard()
        }
    }

    @Test fun cancellingVerificationAllowsTypingAfterOneFieldTap() = runBlocking {
        settings.updateAutofillAuthRequired(true)
        SessionManager.markLocked()
        AutofillSessionGrants.clear()
        open("standard")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        tap(waitNode { it.text?.toString() == context.getString(R.string.cancel) })
        expectStatus("user=EMPTY password=EMPTY")
        // Cancelling authentication dismisses the IME on some Android versions.
        // One normal tap may reopen it; no second tap or explicit dismissal of
        // the returned dropdown should be necessary to type.
        waitNode { it.contentDescription?.toString() == "fixture-field-0" && it.isFocused }
        focusFirstField()
        typeUsernameFromKeyboard()
    }

    private fun tapIme(key: String) {
        tap(waitNode { it.contentDescription?.toString() == "fixture-ime-$key" })
    }

    @Test fun passwordDropdownAllowsTypingWithoutRefocusing() {
        repeat(5) {
            open("password")
            focusFirstField()
            waitNode { it.text?.toString() == NATIVE_TITLE }
            tapIme("password")
            expectStatus("user=ABSENT password=OK")
        }
    }

    private fun typeUsernameFromKeyboard() {
        // Actual keyboard taps and InputConnection composition; ACTION_SET_TEXT
        // would bypass the focus/input-connection failure reported by the user.
        tapIme("prefix")
        expectStatus("user=BAD password=EMPTY")
        tapIme("suffix")
        expectStatus("user=OK password=EMPTY")
    }

    @Test fun systemAutofillWithoutVerificationStillWorksAfterSessionLock() {
        SessionManager.markLocked()
        AutofillSessionGrants.clear()
        open("standard")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
    }

    @Test fun systemAutofillRecognizesChineseEnterpriseFields() {
        open("enterprise")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
    }

    @Test fun systemAutofillKeepsNativeLoginBesideHelpWebView() {
        open("mixed")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
    }

    @Test fun systemAutofillSupportsUsernameThenPasswordInOneActivity() {
        open("step")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=ABSENT")
        tap(waitNode { it.text?.toString()?.equals("Next step", ignoreCase = true) == true })
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=ABSENT password=OK")
    }

    @Test fun systemAutofillFillsWebViewAndDispatchesInputEvents() {
        open("web")
        waitNode { it.contentDescription?.toString() == "fixture-status" && it.text?.contains("web=READY") == true }
        fillFromSystem(WEB_TITLE, web = true)
        expectStatus("user=OK password=OK")
    }

    @Test fun systemAutofillKeepsTheOtherHtmlFormEmpty() {
        open("web_forms")
        waitNode { it.text?.contains("web=READY") == true }
        fillFromSystem(WEB_TITLE, web = true)
        expectStatus("user=OK password=OK newsletter=EMPTY")
    }

    @Test fun systemAutofillUsesComputedHtmlHintsWithoutLabels() {
        open("computed")
        fillFromSystem(WEB_TITLE)
        expectStatus("user=OK password=OK")
    }

    @Test fun systemAutofillInlineSelectionReturnsTheChosenCredential() {
        open("inline")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
    }

    @Test fun systemDropdownPublishesOtpOnlyAfterSelectingTheCredential() = runBlocking {
        prepareOtpNotificationTest()
        attachInlineOtp()
        open("standard")
        focusFirstField()
        val suggestion = waitNode { it.text?.toString() == NATIVE_TITLE }
        assertNoOtpNotificationFor(1000)
        tap(suggestion)
        expectStatus("user=OK password=OK")
        expectOtpNotification()
    }

    @Test fun systemInlineSelectionPublishesBoundValidatorOtpAfterFillingOriginalFields() = runBlocking {
        assumeTrue("Inline suggestions require Android 11 or later", Build.VERSION.SDK_INT >= 30)
        prepareOtpNotificationTest()
        attachBoundOtp()
        open("inline")
        focusFirstField()
        val suggestion = waitNode {
            (it.text?.toString() == NATIVE_TITLE || it.contentDescription?.contains(NATIVE_TITLE) == true) &&
                it.window?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        assertNoOtpNotificationFor(1000)
        tap(suggestion)
        expectStatus("user=OK password=OK")
        expectOtpNotification()
    }

    @Test fun disabledOtpNotificationKeepsSystemDropdownFillWithoutNotification() = runBlocking {
        prepareOtpNotificationTest(enabled = false)
        attachInlineOtp()
        open("standard")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
        assertNoOtpNotificationFor(2000)
    }

    @Test fun enabledOtpNotificationDoesNothingForCredentialWithoutOtp() = runBlocking {
        prepareOtpNotificationTest()
        open("standard")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
        assertNoOtpNotificationFor(2000)
    }

    @Test fun usernameOnlyStepDoesNotNotifyUntilPasswordStepIsFilled() = runBlocking {
        prepareOtpNotificationTest()
        attachInlineOtp()
        open("step")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=ABSENT")
        assertNoOtpNotificationFor(2000)
        tap(waitNode { it.text?.toString()?.equals("Next step", ignoreCase = true) == true })
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=ABSENT password=OK")
        expectOtpNotification()
    }

    @Test fun cancellingLockedOtpCredentialDoesNotPublishNotification() = runBlocking {
        prepareOtpNotificationTest()
        attachInlineOtp()
        settings.updateAutofillAuthRequired(true)
        SessionManager.markLocked()
        AutofillSessionGrants.clear()
        open("standard")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        assertNoOtpNotificationFor(750)
        tap(waitNode { it.text?.toString() == context.getString(R.string.cancel) })
        expectStatus("user=EMPTY password=EMPTY")
        assertNoOtpNotificationFor(1500)
    }

    @Test fun lockedOtpCredentialNotifiesOnlyAfterVerificationAndSelection() = runBlocking {
        prepareOtpNotificationTest()
        attachInlineOtp()
        settings.updateAutofillAuthRequired(true)
        SessionManager.markLocked()
        AutofillSessionGrants.clear()
        open("standard")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        assertNoOtpNotificationFor(750)
        verifyAndSelectCredential()
        expectStatus("user=OK password=OK")
        expectOtpNotification()
    }

    @Test fun invalidOtpNeverPreventsSelectedSystemCredentialFromFilling() = runBlocking {
        prepareOtpNotificationTest()
        attachInlineOtp("otpauth://totp/AutofillFixture?secret=!!!")
        open("standard")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
        assertNoOtpNotificationFor(2000)
    }

    @Test fun otpEligibilityKeepsLargeVaultLookupOffTheRepeatedSuggestionPath() = runBlocking {
        val database = PasswordDatabase.getDatabase(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val password = requireNotNull(dao.getPasswordEntryById(insertedIds.first()))
            val payload = security.encryptData(Json.encodeToString(TotpData(secret = OTP_SECRET, boundPasswordId = password.id)))
            database.withTransaction {
                repeat(1000) {
                    insertedValidatorIds += database.secureItemDao().insertItem(SecureItem(
                        itemType = ItemType.TOTP, title = "Synthetic OTP performance fixture", itemData = payload))
                }
            }
            val cache = AutofillOtpBindingCache(scope)
            val actions = AutofillOtpActions(context, cache)
            val candidates = listOf(password)
            preferences.setOtpNotificationEnabled(false)
            preferences.setAutoCopyOtpEnabled(false)
            suspend fun measure(): Long {
                val start = SystemClock.elapsedRealtimeNanos()
                actions.eligiblePasswordIds(candidates)
                return (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
            }
            val disabled = List(25) { measure() }.sorted()[23]
            preferences.setOtpNotificationEnabled(true)
            val cold = measure()
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (password.id !in actions.eligiblePasswordIds(candidates) && SystemClock.elapsedRealtime() < deadline) delay(25)
            assertTrue("Background index must eventually include the selected binding", password.id in actions.eligiblePasswordIds(candidates))
            val warm = List(50) { measure() }.sorted()[47]
            android.util.Log.i("AutofillOtpPerf", "validators=1000 disabledP95Ms=$disabled coldMs=$cold cachedP95Ms=$warm")
            assertTrue("Cached optional lookup must stay below the 100 ms cold-query budget", warm < 100)
        } finally { scope.cancel() }
    }

    @Test fun manualSystemRequestSupportsAnUnlabelledField() {
        open("unlabelled")
        tap(waitNode { it.text?.toString()?.equals("Request autofill", ignoreCase = true) == true })
        tap(waitNode { it.text?.toString() == NATIVE_TITLE })
        expectStatus("user=OK password=ABSENT")
    }

    private fun issue153Context() = takagi.ru.monica.autofill_ng.auth.AutofillGrantContext(
        fixturePackage, null, null, null,
    )

    private suspend fun issue153UnlockFirstStep(keepUnlocked: Boolean = true) {
        settings.updateAutofillKeepUnlocked(keepUnlocked)
        settings.updateAutofillAuthRequired(true)
        settings.updateAutoLockMinutes(0)
        SessionManager.markLocked()
        open("step")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        verifyAndSelectCredential()
        expectStatus("user=OK password=ABSENT")
        assertEquals(keepUnlocked, AutofillSessionGrants.isGranted(issue153Context()))
        assertFalse("Autofill must not unlock the main application", SessionManager.isUnlocked.value)
        assertFalse("Autofill must not authorize the IME", takagi.ru.monica.security.SecondarySessionManager.isUnlocked.value)
    }

    @Test fun issue153TwoStepsSurviveOldTimeoutAndServiceRebind() = runBlocking {
        issue153UnlockFirstStep()
        // Allow Android to unbind its idle autofill service; exceed the old 30s grant.
        delay(32_000)
        assertTrue(AutofillSessionGrants.isGranted(issue153Context()))
        tap(waitNode { it.text?.toString()?.equals("Next step", ignoreCase = true) == true })
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=ABSENT password=OK")
        // A new activity and autofill session must reuse the same scope too.
        open("username")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=OK password=ABSENT")
        issue153Screenshot("two-step-filled")
    }

    @Test fun issue153RealTwoMinuteExpiryRequiresAuthenticationAgain() = runBlocking {
        issue153UnlockFirstStep()
        delay(120_100)
        assertFalse(AutofillSessionGrants.isGranted(issue153Context()))
        open("password")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        waitNode { it.isEditable && it.packageName?.toString() == context.packageName }
        issue153Screenshot("expired-verification")
        verifyAndSelectCredential()
        expectStatus("user=ABSENT password=OK")
    }

    @Test fun issue153ManualLockRevokesEvenIfKeyIsLoadedAgain() = runBlocking {
        issue153UnlockFirstStep()
        SessionManager.markLocked()
        check(security.unlockVaultWithPassword(MASTER_PASSWORD))
        assertFalse(AutofillSessionGrants.isGranted(issue153Context()))
        open("password")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        verifyAndSelectCredential()
        expectStatus("user=ABSENT password=OK")
    }

    @Test fun issue153ScreenOffRevokesAcrossServiceLifetime() = runBlocking {
        issue153UnlockFirstStep()
        val user = android.os.Process.myUid() / 100000
        shell("cmd autofill destroy sessions --user $user")
        try {
            shell("input keyevent KEYCODE_SLEEP")
            delay(1500)
            assertFalse(context.getSystemService(android.os.PowerManager::class.java).isInteractive)
            // Do not query the grant while asleep: the receiver itself must revoke it.
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
        delay(500)
        assertFalse(AutofillSessionGrants.isGranted(issue153Context()))
        open("password")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        verifyAndSelectCredential()
        expectStatus("user=ABSENT password=OK")
    }

    @Test fun issue153PickerVerificationAndRecreationGrantFollowingSystemFill() = runBlocking {
        settings.updateAutofillKeepUnlocked(true)
        settings.updateAutofillAuthRequired(true)
        settings.updateAutoLockMinutes(0)
        SessionManager.markLocked()
        val intent = AutofillPickerActivityV2.getIntent(context, AutofillPickerActivityV2.Args(applicationId = fixturePackage))
        androidx.test.core.app.ActivityScenario.launch<AutofillPickerActivityV2>(intent).use { scenario ->
            val field = waitNode { it.isEditable && it.packageName?.toString() == context.packageName }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, MASTER_PASSWORD)
            }))
            tap(waitNode { it.text?.toString() == context.getString(R.string.unlock) })
            waitNode { it.text?.toString() == NATIVE_TITLE }
            assertTrue(AutofillSessionGrants.isGranted(issue153Context()))
            scenario.recreate()
            waitNode { it.text?.toString() == NATIVE_TITLE }
            assertFalse(SessionManager.isUnlocked.value)
            issue153Screenshot("picker-recreated")
        }
        open("password")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=ABSENT password=OK")
    }

    @Test fun issue153DatasetVerificationGrantsFollowingSystemFill() = runBlocking {
        settings.updateAutofillKeepUnlocked(true)
        settings.updateAutofillAuthRequired(true)
        settings.updateAutoLockMinutes(0)
        SessionManager.markLocked()
        val id = android.widget.EditText(context).autofillId
        val intent = AutofillCipherCallbackActivity.getIntent(context, AutofillCipherCallbackActivity.Args(
            passwordId = insertedIds.first(), applicationId = fixturePackage,
            autofillIds = arrayListOf(id), autofillHints = arrayListOf("USERNAME"), requireAuthentication = true,
        ))
        androidx.test.core.app.ActivityScenario.launchActivityForResult<AutofillCipherCallbackActivity>(intent).use { scenario ->
            val field = waitNode { it.isEditable && it.packageName?.toString() == context.packageName }
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, MASTER_PASSWORD)
            }))
            tap(waitNode { it.text?.toString() == context.getString(R.string.confirm) })
            val result = scenario.result
            assertEquals(android.app.Activity.RESULT_OK, result.resultCode)
            @Suppress("DEPRECATION")
            val dataset = result.resultData.getParcelableExtra<android.service.autofill.Dataset>(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT)
            assertNotNull(dataset)
            assertTrue(AutofillSessionGrants.isGranted(issue153Context()))
            assertFalse(SessionManager.isUnlocked.value)
        }
        open("password")
        fillFromSystem(NATIVE_TITLE)
        expectStatus("user=ABSENT password=OK")
    }

    @Test fun issue153GrantDoesNotAuthorizeAnotherWebsite() = runBlocking {
        issue153UnlockFirstStep()
        open("web")
        focusFirstField(web = true)
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        waitNode { it.isEditable && it.packageName?.toString() == context.packageName }
        tap(waitNode { it.text?.toString() == context.getString(R.string.cancel) })
        expectStatus("user=EMPTY password=EMPTY")
    }

    @Test fun issue153WrongPasswordDoesNotGrantAccessAndCanRetry() = runBlocking {
        settings.updateAutofillKeepUnlocked(true)
        settings.updateAutofillAuthRequired(true)
        SessionManager.markLocked()
        open("step")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        val field = waitNode { it.isEditable && it.packageName?.toString() == context.packageName }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "incorrect-test-password")
        }))
        tap(waitNode { it.text?.toString() == context.getString(R.string.confirm) })
        waitNode { it.text?.toString() == context.getString(R.string.password_incorrect) }
        assertFalse(AutofillSessionGrants.isGranted(issue153Context()))
        assertFalse(SessionManager.isUnlocked.value)
        verifyAndSelectCredential()
        expectStatus("user=OK password=ABSENT")
    }

    @Test fun issue153RetentionDisabledRequiresVerificationForNextStep() = runBlocking {
        assertFalse(AppSettings().autofillKeepUnlocked)
        issue153UnlockFirstStep(keepUnlocked = false)
        open("password")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        verifyAndSelectCredential()
        expectStatus("user=ABSENT password=OK")
        assertFalse(AutofillSessionGrants.isGranted(issue153Context()))
    }

    @Test fun issue153TurningOffAndOnDoesNotRestoreGrant() = runBlocking {
        issue153UnlockFirstStep()
        settings.updateAutofillKeepUnlocked(false)
        assertFalse(AutofillSessionGrants.isGranted(issue153Context()))
        settings.updateAutofillKeepUnlocked(true)
        assertFalse(AutofillSessionGrants.isGranted(issue153Context()))
        open("password")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        verifyAndSelectCredential()
        expectStatus("user=ABSENT password=OK")
    }

    private fun mainLogin() {
        val field = waitNode { it.isEditable && it.packageName?.toString() == context.packageName }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, MASTER_PASSWORD)
        }))
        tap(waitNode { it.text?.toString() == context.getString(R.string.unlock) })
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (!SessionManager.isUnlocked.value && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
        assertTrue("Main app password must unlock the real main screen", SessionManager.isUnlocked.value)
    }

    @Test fun issue153RemovingRealMainTaskRevokesBothSessions() = runBlocking {
        settings.updateAutoLockMinutes(-1)
        settings.updateAutofillAuthRequired(true)
        settings.updateAutofillKeepUnlocked(true)
        val main = androidx.test.core.app.ActivityScenario.launch<takagi.ru.monica.MainActivity>(
            Intent(context, takagi.ru.monica.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            mainLogin()
            var taskId = -1
            main.onActivity { taskId = it.taskId }
            issue153UnlockFirstStep()
            // Keep main unlock unlimited to prove removal, rather than a timeout, revokes it.
            settings.updateAutoLockMinutes(-1)
            SessionManager.updateAutoLockTimeout(-1)
            SessionManager.markUnlocked()
            val manager = context.getSystemService(android.app.ActivityManager::class.java)
            val task = manager.appTasks.single { it.taskInfo.taskId == taskId }
            task.finishAndRemoveTask()
            delay(1000)
            assertFalse(manager.appTasks.any { it.taskInfo.taskId == taskId })
            assertFalse(AutofillSessionGrants.isGranted(issue153Context()))
            assertFalse(SessionManager.canSkipVerification(context))
            open("password")
            focusFirstField()
            tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
            verifyAndSelectCredential()
            expectStatus("user=ABSENT password=OK")
            androidx.test.core.app.ActivityScenario.launch<takagi.ru.monica.MainActivity>(
                Intent(context, takagi.ru.monica.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use {
                waitNode { it.isEditable && it.packageName?.toString() == context.packageName }
                assertFalse(SessionManager.isUnlocked.value)
                issue153Screenshot("main-requires-password-after-removal")
                mainLogin()
            }
        } finally { main.close() }
    }

    @Test fun issue153BackgroundAndConfigurationKeepMainSessionButPickerCannotRefreshIt() = runBlocking {
        settings.updateAutoLockMinutes(5)
        androidx.test.core.app.ActivityScenario.launch<takagi.ru.monica.MainActivity>(
            Intent(context, takagi.ru.monica.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { main ->
            mainLogin()
            var mainTaskId = -1
            main.onActivity { mainTaskId = it.taskId }
            main.recreate()
            assertTrue(SessionManager.canSkipVerification(context))
            open("username")
            assertTrue(SessionManager.canSkipVerification(context))
            // A real picker activity is configured to use its separate authorization.
            val intent = AutofillPickerActivityV2.getIntent(context, AutofillPickerActivityV2.Args(applicationId = fixturePackage))
                // ActivityScenario defaults to CLEAR_TASK. Use a separate task so this
                // background-switch test does not accidentally remove the real main task.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            androidx.test.core.app.ActivityScenario.launch<AutofillPickerActivityV2>(intent).use { picker ->
                waitNode { it.text?.toString() == NATIVE_TITLE }
                val field = SessionManager::class.java.getDeclaredField("unlockElapsedTimestamp").apply { isAccessible = true }
                val before = field.getLong(SessionManager)
                delay(1100)
                picker.onActivity { it.onUserInteraction() }
                assertEquals("Picker touch must not extend main session", before, field.getLong(SessionManager))
            }
            context.getSystemService(android.app.ActivityManager::class.java).appTasks
                .single { it.taskInfo.taskId == mainTaskId }.moveToFront()
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (main.state != androidx.lifecycle.Lifecycle.State.RESUMED && SystemClock.elapsedRealtime() < deadline) delay(50)
            assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, main.state)
            assertTrue(SessionManager.canSkipVerification(context))
        }
    }

    private fun issue153Screenshot(name: String) {
        val screenshot = requireNotNull(ui.takeScreenshot())
        val dir = java.io.File(context.filesDir, "issue-153-screens").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    @Test fun verifiedFillReturnsToTheOriginalForm() = runBlocking {
        settings.updateAutofillAuthRequired(true)
        SessionManager.markLocked()
        AutofillSessionGrants.clear()
        open("standard")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        verifyAndSelectCredential()
        expectStatus("user=OK password=OK")
    }

    @Test fun cancellingVerificationLeavesTheFormEmptyAndAllowsRetry() = runBlocking {
        settings.updateAutofillAuthRequired(true)
        SessionManager.markLocked()
        AutofillSessionGrants.clear()
        open("standard")
        focusFirstField()
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        tap(waitNode { it.text?.toString() == context.getString(R.string.cancel) })
        expectStatus("user=EMPTY password=EMPTY")
        assertFalse(AutofillSessionGrants.isGranted(
            takagi.ru.monica.autofill_ng.auth.AutofillGrantContext(fixturePackage,null,null,null)))
        // Move focus without a coordinate tap: the returned suggestion can
        // overlap the first field and a second tap would already open it.
        assertTrue(waitNode { it.contentDescription?.toString() == "fixture-field-1" }
            .performAction(AccessibilityNodeInfo.ACTION_FOCUS))
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill_unlock_monica) })
        verifyAndSelectCredential()
        expectStatus("user=OK password=OK")
    }

    @Test fun accessibilityFillsChineseFieldsWithNonstandardInputTypes() {
        open("plain_chinese", accessibilityOnly = true)
        assertTrue(fillWithAccessibility())
        expectStatus("user=OK password=OK")
    }

    @Test fun accessibilityHandlesBothStepsAndReportsSuccess() {
        open("step", accessibilityOnly = true)
        assertTrue(fillWithAccessibility())
        expectStatus("user=OK password=ABSENT")
        tap(waitNode { it.text?.toString()?.equals("Next step", ignoreCase = true) == true })
        assertTrue(fillWithAccessibility())
        expectStatus("user=ABSENT password=OK")
    }

    @Test fun accessibilityLeavesOtpAndSearchUntouched() {
        open("otp", accessibilityOnly = true)
        assertTrue(fillWithAccessibility())
        expectStatus("user=OK password=ABSENT otp=EMPTY")
        open("search", accessibilityOnly = true)
        assertFalse(fillWithAccessibility())
        expectStatus("other=EMPTY")
    }

    @Test fun accessibilityFillsWebViewAndDispatchesInputEvents() {
        open("web", accessibilityOnly = true)
        waitNode { it.contentDescription?.toString() == "fixture-status" && it.text?.contains("web=READY") == true }
        assertTrue(fillWithAccessibility(web = true))
        expectStatus("user=OK password=OK")
    }

    @Test fun accessibilityPickerReturnsToTheAppAndFillsBothFields() {
        open("enterprise", accessibilityOnly = true)
        fillThroughAccessibilityPicker(NATIVE_TITLE)
        expectStatus("user=OK password=OK")
    }

    @Test fun accessibilityPickerHandlesAWebViewThatReplacesThePasswordField() {
        open("web_dynamic", accessibilityOnly = true)
        waitNode { it.contentDescription?.toString() == "fixture-status" && it.text?.contains("web=READY") == true }
        fillThroughAccessibilityPicker(WEB_TITLE, web = true)
        expectStatus("user=OK password=OK")
    }

    @Test fun accessibilityWillNotFillAnotherActiveApp() {
        open("standard", accessibilityOnly = true)
        focusFirstField()
        awaitAccessibility()
        val result = runBlocking {
            withContext(Dispatchers.Main) {
                MonicaAccessibilityService.requestCredentialFill(
                    "some.other.app", AutofillFormFixtureActivity.USERNAME,
                    AutofillFormFixtureActivity.PASSWORD, false)
            }
        }
        assertFalse(result)
        expectStatus("user=EMPTY password=EMPTY")
    }

    private fun open(scenario: String, accessibilityOnly: Boolean = false) {
        val instance = java.util.UUID.randomUUID().toString()
        selectIme(if (scenario == "inline") {
            requireNotNull(oldIme) { "Install an inline-capable keyboard for the inline test" }
        } else {
            "$fixturePackage/takagi.ru.monica.autofill_ng.fixture.AutofillFixtureInputMethodService"
        })
        context.startActivity(Intent().apply {
            component = ComponentName(fixturePackage,AutofillFormFixtureActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("scenario",scenario)
            putExtra("expectedUsername", expectedUsername)
            putExtra("expectedPassword", expectedPassword)
            putExtra("accessibilityOnly",accessibilityOnly)
            putExtra("fixtureInstance", instance)
        })
        // The previous activity can still expose its status/dropdown while CLEAR_TASK is
        // being processed. Never accidentally click the previous test's suggestion.
        waitNode { it.contentDescription?.toString() == "fixture-instance-$instance" }
        waitNode { it.contentDescription?.toString() == "fixture-status" }
    }

    private fun focusFirstField(web: Boolean = false) {
        tap(waitNode { if (web) it.isEditable && it.packageName?.toString() == fixturePackage
            else it.contentDescription?.toString() == "fixture-field-0" })
    }

    private fun fillFromSystem(title: String, web: Boolean = false) {
        val started = SystemClock.elapsedRealtime()
        focusFirstField(web)
        tap(waitNode { it.text?.toString() == title || it.contentDescription?.contains(title) == true })
        android.util.Log.i("AutofillFlowTest","Suggestion selection after ${SystemClock.elapsedRealtime()-started}ms")
    }

    private fun verifyAndSelectCredential() {
        val masterField = waitNode { it.isEditable && it.packageName?.toString() == context.packageName }
        assertTrue(masterField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, MASTER_PASSWORD)
        }))
        tap(waitNode { it.text?.toString() == context.getString(R.string.confirm) })
        tap(waitNode { it.text?.toString() == NATIVE_TITLE })
    }

    private fun awaitAccessibility() {
        if (!MonicaAccessibilityService.isCredentialFillAvailable(context)) {
            // Instrumentation force-stops its target process. Rebind only our
            // service in the opted-in test user after that forced restart.
            val user = android.os.Process.myUid() / 100000
            val component = ComponentName(context, MonicaAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').filter { it.isNotBlank() }
            val otherServices = enabled.filter { ComponentName.unflattenFromString(it) != component }
            if (otherServices.isEmpty()) {
                shell("settings --user $user delete secure enabled_accessibility_services")
            } else {
                shell("settings --user $user put secure enabled_accessibility_services ${componentArgument(otherServices.joinToString(":"))}")
            }
            Thread.sleep(200)
            shell("settings --user $user put secure enabled_accessibility_services " +
                componentArgument((otherServices + component.flattenToString()).joinToString(":")))
            shell("settings --user $user put secure accessibility_enabled 1")
            accessibilityEnabledByTest = true
        }
        val end = SystemClock.elapsedRealtime() + 10000
        while (!MonicaAccessibilityService.isCredentialFillAvailable(context) && SystemClock.elapsedRealtime() < end) {
            Thread.sleep(50)
        }
        assertTrue("Enable Monica accessibility for the isolated user",MonicaAccessibilityService.isCredentialFillAvailable(context))
    }

    private fun fillWithAccessibility(web: Boolean = false): Boolean {
        focusFirstField(web)
        awaitAccessibility()
        return runBlocking {
            withContext(Dispatchers.Main) {
                MonicaAccessibilityService.requestCredentialFill(
                    fixturePackage, AutofillFormFixtureActivity.USERNAME,
                    AutofillFormFixtureActivity.PASSWORD, false)
            }
        }
    }

    private fun fillThroughAccessibilityPicker(title: String, web: Boolean = false) {
        focusFirstField(web)
        awaitAccessibility()
        context.startActivity(Intent(context,AutofillPickerActivityV2::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AutofillPickerActivityV2.EXTRA_MANUAL_MODE,true)
            putExtra(AutofillPickerActivityV2.EXTRA_MANUAL_TARGET_PACKAGE,fixturePackage)
        })
        tap(waitNode { it.text?.toString() == title })
        // The regular vault list opens the entry's actions first.
        tap(waitNode { it.text?.toString() == context.getString(R.string.autofill) })
    }

    private fun expectStatus(expected: String) {
        waitNode { it.contentDescription?.toString() == "fixture-status" && it.text?.contains(expected) == true }
    }

    private suspend fun prepareOtpNotificationTest(enabled: Boolean = true) {
        assumeTrue("Do not replace an existing OTP notification in the test user",
            notifications.activeNotifications.none { it.id == OTP_NOTIFICATION_ID })
        notificationChannelExisted = notifications.getNotificationChannel(OTP_CHANNEL_ID) != null
        notificationTestStarted = true
        // Revoking this permission after a test kills the instrumentation target.
        // Prepare it in the isolated user before running, and preserve it here.
        assumeTrue("Grant notification permission in the isolated test user before running OTP tests",
            Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        assumeTrue("Preserve the user's blocked app/channel notification settings",
            notifications.areNotificationsEnabled() &&
                notifications.getNotificationChannel(OTP_CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE)
        preferences.setOtpNotificationEnabled(enabled)
        preferences.setAutoCopyOtpEnabled(false)
        preferences.setOtpNotificationDuration(30)
    }

    private suspend fun attachInlineOtp(secret: String = OTP_SECRET) {
        val entry = requireNotNull(dao.getPasswordEntryById(insertedIds.first()))
        dao.updatePasswordEntry(entry.copy(authenticatorKey = security.encryptData(secret)))
    }

    private suspend fun attachBoundOtp() {
        val data = TotpData(secret = OTP_SECRET, issuer = "Autofill fixture", accountName = "Synthetic account",
            boundPasswordId = insertedIds.first())
        insertedValidatorIds += PasswordDatabase.getDatabase(context).secureItemDao().insertItem(SecureItem(
            itemType = ItemType.TOTP, title = "Autofill fixture bound validator", itemData = security.encryptData(Json.encodeToString(data))))
    }

    private fun assertNoOtpNotificationFor(durationMs: Long) {
        val deadline = SystemClock.elapsedRealtime() + durationMs
        do {
            assertTrue("No OTP notification is allowed before eligible password selection",
                notifications.activeNotifications.none { it.id == OTP_NOTIFICATION_ID })
            Thread.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
    }

    private fun expectOtpNotification(expectedTitle: String = NATIVE_TITLE) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        var notification: Notification? = null
        while (notification == null && SystemClock.elapsedRealtime() < deadline) {
            notification = notifications.activeNotifications.firstOrNull { it.id == OTP_NOTIFICATION_ID }?.notification
            if (notification == null) Thread.sleep(50)
        }
        val actual = requireNotNull(notification) { "Selected OTP credential did not publish its notification" }
        assertEquals(OTP_CHANNEL_ID, actual.channelId)
        assertTrue(actual.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains(expectedTitle))
        val code = actual.extras.getCharSequence(Notification.EXTRA_TEXT).toString().filter(Char::isDigit)
        val now = System.currentTimeMillis() / 1000
        val expected = listOf(now - 30, now, now + 30).map { TotpGenerator.generateOtp(TotpData(secret = OTP_SECRET), currentSeconds = it) }
        assertTrue("Notification must contain the selected fixture's current OTP", code in expected)
    }

    private fun waitNode(scroll: Boolean = false, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val end = SystemClock.elapsedRealtime() + 10000
        var fixtureStatus: String? = null
        var lastScroll = 0L
        do {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            ui.rootInActiveWindow?.let(queue::add)
            ui.windows.mapNotNull { it.root }.forEach(queue::add)
            var count = 0
            val scrollable = mutableListOf<AccessibilityNodeInfo>()
            while (queue.isNotEmpty() && count++ < 600) {
                val node = queue.removeFirst()
                if (node.contentDescription?.toString() == "fixture-status") {
                    fixtureStatus = node.text?.toString()
                }
                if (predicate(node)) return node
                if (node.isScrollable && node.packageName?.toString() != fixturePackage) scrollable += node
                for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
            }
            if (scroll && SystemClock.elapsedRealtime() - lastScroll > 900) {
                scrollable.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                lastScroll = SystemClock.elapsedRealtime()
            }
            Thread.sleep(50)
        } while (SystemClock.elapsedRealtime() < end)
        runCatching {
            val nodes = ArrayDeque<AccessibilityNodeInfo>()
            ui.windows.mapNotNull { it.root }.forEach(nodes::add)
            val output = StringBuilder("expected save label=${context.getString(R.string.save)}\n")
            var count=0
            while(nodes.isNotEmpty() && count++<1000) {
                val node=nodes.removeFirst()
                output.appendLine("${node.packageName}|${node.className}|${node.text}|${node.contentDescription}|enabled=${node.isEnabled}")
                for(index in 0 until node.childCount) node.getChild(index)?.let(nodes::add)
            }
            java.io.File(context.filesDir,"autofill-failure.txt").writeText(output.toString())
            java.io.File(context.filesDir, "autofill-failure.png").outputStream().use {
                ui.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        throw AssertionError("Expected autofill UI/status did not appear within 10 seconds; " +
            "activeRoot=${ui.rootInActiveWindow?.className}, windows=${ui.windows.size}, " +
            "flags=${ui.serviceInfo.flags}, fixtureStatus=$fixtureStatus")
    }

    private fun tap(node: AccessibilityNodeInfo) {
        val rect = Rect().also(node::getBoundsInScreen)
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(ui.injectInputEvent(event,true)) } finally { event.recycle() }
        }
    }

    private fun selectIme(component: String) {
        val user = android.os.Process.myUid() / 100000
        listOf("ime enable --user $user ${componentArgument(component)}", "ime set --user $user ${componentArgument(component)}").forEach(::shell)
        check(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == component) {
            "Could not select test input method $component"
        }
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).use { stream ->
            stream.bufferedReader().readText()
        }
    }

    private fun componentArgument(value: String): String {
        // UiAutomation passes this command to Runtime.exec, not to a shell.
        require(value.matches(Regex("[A-Za-z0-9_./:$]+"))) { "Invalid test component" }
        return value
    }

    companion object {
        private const val MASTER_PASSWORD = "Monica-Autofill-Test-2026!"
        private const val NATIVE_TITLE = "Autofill native test credential"
        private const val WEB_TITLE = "Autofill web test credential"
        private const val OTP_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        private const val OTP_CHANNEL_ID = "autofill_otp"
        private const val OTP_NOTIFICATION_ID = 12001
    }
}

package takagi.ru.monica.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.ProjectCredentialGroup
import takagi.ru.monica.repository.*
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.screens.*
import takagi.ru.monica.utils.AppLocaleStringResolver
import takagi.ru.monica.viewmodel.PasswordViewModel
import java.io.File

class ProjectCredentialEditorTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: PasswordDatabase
    private lateinit var model: PasswordViewModel
    private lateinit var security: SecurityManager
    private var editId by mutableStateOf<Long?>(null)
    private var savedId: Long? = null
    private var detail by mutableStateOf(false)
    private var previousContent = false

    @Before fun setup() {
        runBlocking {
            val settings = takagi.ru.monica.utils.SettingsManager(context)
            previousContent = settings.settingsFlow.first().passwordContentEditorEnabled
            settings.updatePasswordContentEditorEnabled(true)
        }
        db = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        security = SecurityManager(context)
        model = PasswordViewModel(PasswordRepository(db.passwordEntryDao(), categoryDao = db.categoryDao()), security,
            customFieldRepository = CustomFieldRepository(db.customFieldDao()), strings = AppLocaleStringResolver(context))
    }
    @After fun cleanup() {
        model.viewModelScope.cancel(); db.close()
        runBlocking { takagi.ru.monica.utils.SettingsManager(context).updatePasswordContentEditorEnabled(previousContent) }
    }
    private fun prefix(value: String) = SemanticsMatcher("tag starts with $value") { node ->
        node.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith(value)
    }
    private fun scroll(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNodeWithTag("password_content_editor").performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun save() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("password_editor_save").performClick()
        compose.waitUntil(30000) { savedId != null }
    }
    private fun showSimpleEditor() {
        compose.setContent {
            CompositionLocalProvider(LocalUiSecurityManager provides security) {
                MaterialTheme { key(editId) {
                    AddEditPasswordScreen(model, passwordId = editId, initialStorageExplicit = true,
                        initialDraft = if (editId == null) AddEditPasswordInitialDraft(title = "Credential note",
                            username = "demo", password = "synthetic-password") else null,
                        onSaveCompleted = { savedId = it }, onNavigateBack = {})
                } }
            }
        }
    }
    @Test fun singleCredentialNoteSavesReopensAndClearsWithoutPageNotes() {
        showSimpleEditor()
        scroll(hasTestTag("password_editor_credential_note")).performTextReplacement("Only this credential\nSecond line")
        save()
        val id = requireNotNull(savedId)
        val row = runBlocking { requireNotNull(db.passwordEntryDao().getPasswordEntryById(id)) }
        val fields = runBlocking { model.getCustomFieldsByEntryIdSync(id) }.map(CustomFieldDraft::fromCustomField)
        assertEquals("", row.notes)
        assertEquals("Only this credential\nSecond line", ProjectCredentialGroup.read(fields)?.note)
        compose.runOnIdle { editId = id; savedId = null }
        scroll(hasTestTag("password_editor_credential_note")).assertTextContains("Only this credential\nSecond line")
            .performTextClearance()
        save()
        val updated = runBlocking { model.getCustomFieldsByEntryIdSync(id) }.map(CustomFieldDraft::fromCustomField)
        assertEquals("", ProjectCredentialGroup.read(updated)?.note)
    }
    @Test fun primaryAddPasswordExpandsOverAnimationFrames() {
        showSimpleEditor()
        scroll(hasTestTag("password_editor_add_password"))
        fun height() = compose.onNodeWithTag("password_editor_credentials").fetchSemanticsNode().size.height
        val before = height()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("password_editor_add_password").performClick()
            compose.mainClock.advanceTimeBy(80)
            val during = height()
            compose.mainClock.advanceTimeBy(1200)
            val after = height()
            assertTrue("New row expands instead of appearing instantly: $before < $during < $after",
                before < during && during < after)
        } finally { compose.mainClock.autoAdvance = true }
    }
    @Test fun addAccountWithTwoPasswordsAndEditFromItsOwnRowKeepsPrimaryAccount() {
        compose.setContent {
            CompositionLocalProvider(LocalUiSecurityManager provides security) {
                MaterialTheme { key(editId, detail) {
                    if (detail) PasswordDetailScreen(model, passwordId = requireNotNull(savedId), biometricEnabled = false,
                        onNavigateBack = {}, onEditPassword = {})
                    else AddEditPasswordScreen(model, passwordId = editId, initialStorageExplicit = true,
                        initialDraft = if (editId == null) AddEditPasswordInitialDraft(title = "Account groups", username = "personal", password = "primary-secret") else null,
                        onSaveCompleted = { savedId = it }, onNavigateBack = {})
                } }
            }
        }
        scroll(hasTestTag("password_editor_add_password")).performClick()
        compose.onNodeWithTag("password_editor_remove_1").assertDoesNotExist()
        scroll(hasTestTag("password_editor_value_1")).assertExists()
        scroll(hasTestTag("password_otp_secret"))
        assertTrue(compose.onNodeWithTag("password_editor_value_1").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithTag("password_otp_secret").fetchSemanticsNode().boundsInRoot.top)
        scroll(hasTestTag("password_editor_1_menu")).performClick()
        compose.onNodeWithTag("password_editor_1_generate").assertExists()
        compose.onNodeWithTag("password_editor_remove_1").performClick()
        compose.onNodeWithTag("password_editor_value_1").assertDoesNotExist()
        scroll(hasTestTag("password_editor_credential_note")).performTextReplacement("Personal note\nSecond line")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        scroll(hasTestTag("password_otp_secret")).performTextReplacement("JBSWY3DPEHPK3PXP")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        scroll(hasTestTag("password_otp_inline_preview")).assertIsDisplayed()
        scroll(hasTestTag("password_content_add"))
        compose.onNodeWithTag("password_content_editor").performTouchInput { swipeUp() }
        compose.onNodeWithTag("password_content_add").performClick()
        try { compose.waitUntil(10000) { compose.onAllNodesWithTag("add_project_credential").fetchSemanticsNodes().isNotEmpty() } }
        catch (error: Throwable) {
            File(context.filesDir, "project-credential-failure.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            throw error
        }
        compose.onNodeWithTag("add_project_credential").performClick()
        scroll(prefix("credential_username_")).performTextReplacement("work")
        val groupTag = compose.onNode(prefix("credential_username_")).fetchSemanticsNode().config[SemanticsProperties.TestTag].removePrefix("credential_username_")
        scroll(hasTestTag("credential_otp_$groupTag")).assertExists().performTextReplacement("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        scroll(hasTestTag("credential_otp_preview")).assertIsDisplayed()
        scroll(prefix("credential_password_")).performTextReplacement("work-secret-1")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        scroll(prefix("credential_add_password_")).performClick()
        scroll(hasTestTag("credential_note_$groupTag")).performTextReplacement("Work note")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        val secondTag = compose.onAllNodes(prefix("credential_password_")).fetchSemanticsNodes().last().config[SemanticsProperties.TestTag]
        scroll(hasTestTag(secondTag)).performTextReplacement("work-secret-2")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        File(context.filesDir, "project-credential-editor.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        scroll(hasTestTag("credential_otp_$groupTag"))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        File(context.filesDir, "project-credential-extra-otp.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(compose.onNodeWithTag(secondTag).fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithTag("credential_otp_$groupTag").fetchSemanticsNode().boundsInRoot.top)
        save()
        val rows = runBlocking { db.passwordEntryDao().getAllPasswordEntriesSync() }
        assertEquals(3, rows.size)
        assertEquals(setOf("personal", "work"), rows.map { it.username }.toSet())
        val savedFields = runBlocking { model.getCustomFieldsByEntryIds(rows.map { it.id }) }
            .mapValues { (_, fields) -> fields.map(CustomFieldDraft::fromCustomField) }
        val restored = ProjectCredentialGroup.restore(rows.map { it.copy(
            password = security.decryptData(it.password),
            authenticatorKey = security.decryptDataIfMonicaCiphertext(it.authenticatorKey)) }, savedFields)
        assertEquals(listOf("Personal note\nSecond line", "Work note"), restored.map { it.note })
        fun secret(row: PasswordEntry) = takagi.ru.monica.util.TotpDataResolver.fromAuthenticatorKey(
            security.decryptDataIfMonicaCiphertext(row.authenticatorKey))?.secret
        assertEquals("JBSWY3DPEHPK3PXP", secret(rows.single { it.username == "personal" }))
        assertTrue(rows.filter { it.username == "work" }.all { secret(it) == "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" })
        val additionalId = rows.first { it.username == "work" }.id
        compose.runOnIdle { editId = additionalId; savedId = null }
        compose.waitUntil(20000) { compose.onAllNodesWithText("Account groups").fetchSemanticsNodes().isNotEmpty() }
        scroll(prefix("credential_username_")).assertTextContains("work")
        scroll(hasTestTag("password_editor_credential_note")).assertTextContains("Personal note\nSecond line")
        scroll(hasTestTag("credential_note_$groupTag")).assertTextContains("Work note")
        scroll(hasTestTag("credential_otp_$groupTag")).assertTextContains("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        scroll(prefix("credential_username_")).performTextReplacement("work-edited")
        save()
        val edited = runBlocking { db.passwordEntryDao().getAllPasswordEntriesSync() }
        assertEquals(rows.map { it.id }.toSet(), edited.map { it.id }.toSet())
        assertEquals("primary-secret", security.decryptData(edited.single { it.username == "personal" }.password))
        assertEquals(setOf("work-secret-1", "work-secret-2"), edited.filter { it.username == "work-edited" }.map { security.decryptData(it.password) }.toSet())
        assertEquals("JBSWY3DPEHPK3PXP", secret(edited.single { it.username == "personal" }))
        assertTrue(edited.filter { it.username == "work-edited" }.all { secret(it) == "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" })
        compose.runOnIdle { detail = true }
        compose.waitUntil(20000) { compose.onAllNodesWithText("personal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("personal").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Personal note\nSecond line"))
        compose.onNodeWithText("Personal note\nSecond line").assertIsDisplayed()
        // Both accounts have independent detail sections; the second can be scrolled into view.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("work-edited"))
        compose.onNodeWithText("work-edited").assertIsDisplayed()
        edited.filter { it.username == "work-edited" }.forEach { row ->
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("detail_password_${row.id}"))
            compose.onNodeWithTag("detail_password_${row.id}").performScrollTo().assertIsDisplayed()
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Work note"))
        compose.onNodeWithText("Work note").assertIsDisplayed()
        File(context.filesDir, "credential-detail-grouped.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun legacySingleAccountUsesConnectedAuthenticatorWithoutRepeatingIdentity() {
        val id = runBlocking { db.passwordEntryDao().insertPasswordEntry(PasswordEntry(
            title = "Single account", username = "personal", password = security.encryptData("primary-secret"), website = "",
            authenticatorKey = "otpauth://totp/HiddenIssuer:personal?secret=JBSWY3DPEHPK3PXP&issuer=HiddenIssuer")) }
        compose.setContent { CompositionLocalProvider(LocalUiSecurityManager provides security) {
            MaterialTheme { PasswordDetailScreen(model, passwordId = id, biometricEnabled = false,
                onNavigateBack = {}, onEditPassword = {}) }
        } }
        compose.waitUntil(20000) { compose.onAllNodesWithTag("password_authenticator_card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("HiddenIssuer").assertDoesNotExist()
        compose.onNodeWithText("personal").assertExists()
        compose.onNodeWithTag("password_authenticator_card").performScrollTo().assertIsDisplayed()
        File(context.filesDir, "credential-detail-single.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("password_authenticator_card").performClick()
        compose.onNodeWithTag("auth_copy_current").assertIsDisplayed()
        compose.onNodeWithTag("auth_copy_next").assertIsDisplayed()
        compose.onNodeWithTag("auth_qr_migrate").assertIsDisplayed()
    }

    @Test fun sanitizedMetadataStillDisplaysEveryResolvedPassword() {
        val rows = listOf(501L, 502L).map {
            PasswordEntry(id = it, title = "Sanitized fixture", username = "secondary", password = "", website = "")
        }
        compose.setContent { MaterialTheme {
            ProjectCredentialDetailCard(rows = rows, metadata = emptyMap(),
                values = mapOf(501L to "first-secret", 502L to "second-secret"), unavailable = emptyMap(),
                context = context, settings = takagi.ru.monica.data.AppSettings(), onCreateSend = null,
                onEdit = {}, onDelete = {}, onResyncUnreadable = {}, isResyncingUnreadable = false)
        } }
        compose.onNodeWithTag("detail_password_501").assertIsDisplayed()
        compose.onNodeWithTag("detail_password_502").assertIsDisplayed()
    }

}

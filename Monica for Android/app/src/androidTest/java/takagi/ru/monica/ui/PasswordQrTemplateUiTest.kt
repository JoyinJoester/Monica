package takagi.ru.monica.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import takagi.ru.monica.R
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.*
import takagi.ru.monica.repository.*
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.ui.components.PasswordQrContentEditor
import takagi.ru.monica.ui.screens.PasswordDetailScreen
import takagi.ru.monica.utils.AppLocaleStringResolver
import takagi.ru.monica.viewmodel.PasswordViewModel

class PasswordQrTemplateUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: PasswordDatabase
    private lateinit var model: PasswordViewModel
    private lateinit var security: SecurityManager
    private var wasUnlocked = false
    @Before fun setup() {
        wasUnlocked = SessionManager.isUnlocked.value
        SessionManager.markUnlocked()
        db = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        security = SecurityManager(context)
        model = PasswordViewModel(PasswordRepository(db.passwordEntryDao(), categoryDao = db.categoryDao()), security,
            customFieldRepository = CustomFieldRepository(db.customFieldDao()), strings = AppLocaleStringResolver(context))
    }
    @After fun cleanup() {
        model.viewModelScope.cancel(); db.close()
        if (!wasUnlocked) SessionManager.markLocked()
    }

    @Test fun existingFieldQrRouteSavesTemplateAndResolvesUpdatedPassword() {
        val id = runBlocking { db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title = "QR fixture", username = "wifi;network",
            website = "", password = security.encryptData("first-secret"))) }
        compose.setContent { CompositionLocalProvider(LocalUiSecurityManager provides security) { MaterialTheme {
            PasswordDetailScreen(model, passwordId = id, biometricEnabled = false, onNavigateBack = {}, onEditPassword = {})
        } } }
        compose.waitUntil(15000) { compose.onAllNodesWithText("wifi;network").fetchSemanticsNodes().isNotEmpty() }
        val field = compose.onNodeWithText("wifi;network")
        compose.bringAboveFloatingActions(field, compose.onNode(hasScrollToIndexAction()))
        field.performClick()
        compose.onNodeWithText(context.getString(R.string.field_action_show_barcode)).performClick()
        compose.onNodeWithTag("qr_template_open").performScrollTo().performClick()
        compose.onNodeWithTag("block_title").performTextReplacement("Home Wi-Fi")
        closeFocusedKeyboard()
        compose.onNodeWithTag("qr_wifi_preset").performScrollTo().performClick()
        compose.onNodeWithTag("block_field_content").assertTextContains(PasswordQrTemplate.WIFI)
        compose.onNodeWithTag("qr_insert_field").performScrollTo().performClick()
        compose.onNodeWithText("first-secret").assertDoesNotExist()
        compose.onNodeWithTag("qr_insert_TITLE").performScrollTo().performClick()
        compose.onNodeWithTag("block_field_content").assertTextContains(PasswordQrTemplate.WIFI + "%TITLE%")
        compose.onNodeWithTag("qr_wifi_preset").performScrollTo().performClick()
        capture("template-editor")
        compose.onNodeWithTag("qr_template_generate").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription(context.getString(R.string.legacy_ui_qr_code)).fetchSemanticsNodes().isNotEmpty() }
        capture("template-generated-qr")
        compose.onNode(hasText(context.getString(R.string.close)) and hasAnyAncestor(
            hasAnyDescendant(hasContentDescription(context.getString(R.string.legacy_ui_qr_code))))).performClick()
        compose.onNodeWithTag("block_save").performScrollTo().performClick()
        compose.waitUntil(15000) { runBlocking { db.customFieldDao().getFieldsByEntryIdSync(id).isNotEmpty() } }
        val original = runBlocking { db.passwordEntryDao().getPasswordEntryById(id)!! }
        assertEquals("first-secret", security.decryptDataIfMonicaCiphertext(original.password))
        val fields = runBlocking { db.customFieldDao().getFieldsByEntryIdSync(id) }.map { CustomFieldDraft(
            title = it.title, value = security.decryptDataIfMonicaCiphertext(it.value), isProtected = it.isProtected) }
        val block = PasswordContentBlocks.read(fields).single().block!!
        assertEquals(PasswordQrTemplate.WIFI, block.value("content"))
        assertFalse(block.raw.toString().contains("first-secret"))
        runBlocking { db.passwordEntryDao().updatePasswordEntry(original.copy(password = security.encryptData("second;secret"))) }
        val payload = runBlocking { PasswordQrTemplate.resolve(block, model.readQrTemplateValues(id)) }
        assertEquals("WIFI:T:WPA;S:wifi\\;network;P:second\\;secret;H:false;;", payload)
        val bitmap = com.journeyapps.barcodescanner.BarcodeEncoder().encodeBitmap(payload, com.google.zxing.BarcodeFormat.QR_CODE, 400, 400)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(
            com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
        assertEquals(payload, decoded)
    }

    @Test fun missingFieldShowsErrorAndCancelDoesNotSave() {
        var saves = 0
        var dismissed = false
        val block = PasswordContentBlocks.create(PasswordContentBlocks.Kind.QR_CODE).edited("", mapOf(
            "mode" to "template", "templateVersion" to "1", "content" to "%PASSWORD%"))
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) { MaterialTheme(colorScheme = darkColorScheme()) { PasswordQrContentEditor(block,
            readValues = { PasswordQrTemplate.Values(mapOf("PASSWORD" to null)) }, onSave = { saves++ }, onDismiss = { dismissed = true }) } }
        }
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("qr_template_generate").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("qr_template_generate").performScrollTo().performClick()
        compose.onNodeWithTag("qr_template_error").performScrollTo().assertIsDisplayed()
            .assertTextContains(context.getString(R.string.qr_template_field_error, "PASSWORD"))
        capture("template-error-dark-large-text")
        compose.onNodeWithText(context.getString(R.string.cancel)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(dismissed); assertEquals(0, saves) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = java.io.File(context.filesDir, "qr-template-315").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

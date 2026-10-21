package takagi.ru.monica.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import takagi.ru.monica.R
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.*
import takagi.ru.monica.ui.components.PasswordAuthenticatorCard
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.util.TotpUriParser
import java.io.File

@RunWith(AndroidJUnit4::class)
class PasswordAuthenticatorActionsTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val data = TotpData(secret = "JBSWY3DPEHPK3PXP", issuer = "Fixture", accountName = "alice", period = 60, digits = 8, algorithm = "SHA256")
    private fun show(value: TotpData = data) {
        compose.setContent { MaterialTheme {
            PasswordAuthenticatorCard(PasswordEntry(id = 1, title = "Fixture", username = "alice", password = "", website = ""),
                value, AppSettings(validatorUnifiedProgressBar = UnifiedProgressBarMode.ENABLED), {}, connected = true)
        } }
    }
    private fun card() = compose.onNodeWithTag("password_authenticator_card")
    private fun clipboard() = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()
    @Test fun currentAndNextActionsAreExplicitAndProgressIsVisible() {
        show()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true).onFirst().assertIsDisplayed()
        var old: String? = null
        compose.runOnIdle { old = clipboard() }
        card().performClick()
        compose.runOnIdle { assertEquals(old, clipboard()) }
        val before = System.currentTimeMillis() / 1000
        compose.onNodeWithTag("auth_copy_current").performClick()
        compose.runOnIdle {
            val after = System.currentTimeMillis() / 1000
            assertTrue((before..after).any { TotpGenerator.generateOtp(data, currentSeconds = it) == clipboard() })
        }
        card().performTouchInput { longClick() }
        compose.onNodeWithTag("auth_copy_next").assertIsDisplayed()
        val nextBefore = System.currentTimeMillis() / 1000
        compose.onNodeWithTag("auth_copy_next").performClick()
        compose.runOnIdle {
            val after = System.currentTimeMillis() / 1000
            assertTrue((nextBefore..after).any { TotpGenerator.generateOtp(data, currentSeconds = it + data.period) == clipboard() })
        }
        card().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(150)
        File(context.filesDir, "authenticator-pressed.png").outputStream().use {
            compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
        card().performTouchInput { up() }
    }
    private fun readQr(): String {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(context.getString(R.string.legacy_ui_qr_code)).fetchSemanticsNodes().isNotEmpty() }
        val bitmap = compose.onNodeWithContentDescription(context.getString(R.string.legacy_ui_qr_code)).captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
    }
    @Test fun migrationQrPreservesParametersAndCurrentQrContainsOnlyCode() {
        show()
        card().performClick()
        compose.onNodeWithTag("auth_qr_migrate").performClick()
        val payload = readQr()
        val parsed = requireNotNull(TotpUriParser.parseUri(payload)).totpData
        assertEquals(data.secret, parsed.secret)
        assertEquals(data.accountName, parsed.accountName)
        assertEquals(data.period, parsed.period)
        assertEquals(data.algorithm, parsed.algorithm)
        assertEquals(data.digits, parsed.digits)
        androidx.test.espresso.Espresso.pressBack()
        card().performClick()
        val before = System.currentTimeMillis() / 1000
        compose.onNodeWithTag("auth_qr_current").performClick()
        val code = readQr()
        assertTrue(code.matches(Regex("[0-9]{8}")))
        assertTrue((before..System.currentTimeMillis()/1000).any { TotpGenerator.generateOtp(data,currentSeconds=it)==code })
    }
    @Test fun hotpDoesNotOfferTimeProgressOrNextCopy() {
        show(data.copy(otpType = OtpType.HOTP, counter = 42))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true).assertCountEquals(0)
        card().performClick()
        compose.onNodeWithTag("auth_copy_next").assertDoesNotExist()
        compose.onNodeWithTag("auth_copy_current").assertIsDisplayed()
    }
}

package takagi.ru.monica.ui.password

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.data.PasswordEntry

class PasswordListPreviewPrivacyUiTest {
    @get:Rule val compose = createComposeRule()
    private val number = "6222 0212 3456 5678"
    private val note = "恢复密钥需要离线备份"
    private val entry = PasswordEntry(
        id = 1, title = "示例银行", website = "", username = "示例银行 · $number",
        password = "secret", notes = note
    )
    private val fields = listOf(PasswordCardDisplayField.USERNAME, PasswordCardDisplayField.NOTE_PREVIEW)

    @Test fun singleCardShowsMaskedPreviewsInVisibleAndAccessibilityText() {
        compose.setContent {
            MaterialTheme {
                Column {
                    PasswordEntryCard(entry, onClick = {}, passwordCardDisplayFields = fields)
                    PasswordEntryCard(entry.copy(id = 2, title = "短笔记", username = "alice", notes = "密码"), onClick = {}, passwordCardDisplayFields = fields)
                }
            }
        }
        assertMasked()
        compose.onNodeWithText("••••••").assertIsDisplayed()
        compose.onNodeWithText("alice").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "password-preview-masked.png").outputStream().use { out ->
            compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test fun groupedCardUsesTheSameMaskedPreviews() {
        compose.setContent {
            MaterialTheme {
                MultiPasswordEntryCard(
                    passwords = listOf(entry, entry.copy(id = 2)), onClick = {},
                    passwordCardDisplayFields = fields, isInExpandedGroup = true
                )
            }
        }
        assertMasked()
    }

    private fun assertMasked() {
        compose.onNodeWithText("示例银行 · •••• 5678").assertIsDisplayed()
        compose.onNodeWithText("恢复••••••备份").assertIsDisplayed()
        for (raw in listOf(number, "6222", note, "需要离线")) {
            compose.onAllNodesWithText(raw, substring = true, useUnmergedTree = true).assertCountEquals(0)
        }
    }
}

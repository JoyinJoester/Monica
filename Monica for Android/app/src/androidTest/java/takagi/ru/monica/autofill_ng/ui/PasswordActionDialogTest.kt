package takagi.ru.monica.autofill_ng.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordActionDialogTest {
    @get:Rule val compose = createComposeRule()
    private val password = takagi.ru.monica.data.PasswordEntry(id = 42,
        title = "Example account", username = "demo@example.test", password = "never-visible-secret", website = "")
    private val actions = mutableListOf<PasswordItemAction>()

    private fun show(suggested: Boolean = false, smart: Boolean = true, fontScale: Float = 1f) {
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                if (suggested) SuggestedPasswordListItem(password, showSmartCopyOptions = smart,
                    onAction = { actions += it }, modifier = Modifier.testTag("entry"))
                else PasswordListItem(password, showDropdownMenu = true, showSmartCopyOptions = smart,
                    onAction = { actions += it }, modifier = Modifier.testTag("entry"))
            }
        } }
    }

    @Test fun ordinaryEntryDispatchesEveryActionAndClosesDialog() {
        show()
        val cases = listOf(
            "default" to PasswordItemAction.Autofill(password),
            "accessibility" to PasswordItemAction.AccessibilityFill(password),
            "bind" to PasswordItemAction.AutofillAndSaveUri(password),
            "copy_username" to PasswordItemAction.CopyUsername(password),
            "copy_password" to PasswordItemAction.CopyPassword(password),
            "smart_username" to PasswordItemAction.SmartCopyUsernameFirst(password),
            "smart_password" to PasswordItemAction.SmartCopyPasswordFirst(password),
            "details" to PasswordItemAction.ViewDetails(password),
        )
        for ((index, pair) in cases.withIndex()) {
            compose.onNodeWithTag("entry").performClick()
            compose.onNode(isDialog()).assertExists()
            compose.onNodeWithTag("autofill_action_${pair.first}").performScrollTo().performClick()
            compose.onNodeWithTag("autofill_password_actions").assertDoesNotExist()
            compose.runOnIdle { assertEquals(index + 1, actions.size); assertEquals(pair.second, actions.last()) }
        }
    }

    @Test fun suggestedEntryAlsoOffersIndependentAccessibilityAction() {
        show(suggested = true)
        compose.onNodeWithTag("entry").performClick()
        compose.onNodeWithTag("autofill_action_accessibility").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(PasswordItemAction.AccessibilityFill(password)), actions) }
    }

    @Test fun smartCopyActionsRemainHiddenWithoutNotificationPermission() {
        show(smart = false)
        compose.onNodeWithTag("entry").performClick()
        compose.onNodeWithTag("autofill_action_smart_username").assertDoesNotExist()
        compose.onNodeWithTag("autofill_action_smart_password").assertDoesNotExist()
        compose.onNodeWithTag("autofill_action_accessibility").assertExists()
    }

    @Test fun closingDoesNotFillOrCopyAndSecretIsNeverDisplayed() {
        show()
        compose.onNodeWithTag("entry").performClick()
        compose.onNodeWithText(password.password).assertDoesNotExist()
        compose.onNodeWithTag("autofill_actions_close").performClick()
        compose.onNodeWithTag("autofill_password_actions").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    @Test fun largeFontKeepsHeaderAndLastActionReachableInCenteredDialog() {
        show(fontScale = 2f)
        compose.onNodeWithTag("entry").performClick()
        compose.onNodeWithTag("autofill_action_details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("autofill_actions_close").assertIsDisplayed()
        compose.onNodeWithTag("autofill_action_accessibility").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(PasswordItemAction.AccessibilityFill(password)), actions) }
    }

    @Test fun dialogHasMarginsAndCanCaptureSyntheticPreview() {
        show()
        compose.onNodeWithTag("entry").performClick()
        val bounds = compose.onNodeWithTag("autofill_password_actions").fetchSemanticsNode().boundsInWindow
        assertTrue("Centered dialog keeps a side margin", bounds.left > 0)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.filesDir, "autofill-action-dialog.png")
        val bitmap = compose.onNodeWithTag("autofill_password_actions").captureToImage()
        file.outputStream().use { bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}

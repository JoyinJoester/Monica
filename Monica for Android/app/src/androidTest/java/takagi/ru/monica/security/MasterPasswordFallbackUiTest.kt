package takagi.ru.monica.security

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.ui.components.MonicaPasswordDialogAuthScreen

class MasterPasswordFallbackUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(verify: (String) -> Boolean, success: () -> Unit, cancel: () -> Unit) {
        compose.setContent {
            MonicaPasswordDialogAuthScreen(
                settingsFlow = flowOf(AppSettings()), appName = "Monica", title = "Verify",
                subtitle = "Existing password", passwordLabel = "Password", description = "Enter password",
                confirmText = "Confirm", cancelText = "Cancel", emptyError = "Empty",
                unsupportedCharacterError = "Unsupported", minLengthError = "Too short",
                incorrectError = "Incorrect", verifyPassword = verify, onSuccess = success, onCancel = cancel,
            )
        }
    }

    @Test fun existingPasswordIsVerifiedWithoutApplyingNewPasswordLengthRules() {
        var successes = 0
        val attempts = mutableListOf<String>()
        show({ attempts += it; it == "a" }, { successes++ }, {})
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("wrong")
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Incorrect").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, successes) }
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("a")
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle {
            assertEquals(listOf("wrong", "a"), attempts)
            assertEquals(1, successes)
        }
    }

    @Test fun cancellingDoesNotVerifyOrAuthorize() {
        var verifies = 0
        var successes = 0
        var cancellations = 0
        show({ verifies++; true }, { successes++ }, { cancellations++ })
        compose.onNode(hasSetTextAction()).performTextInput("synthetic-password")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(0, verifies)
            assertEquals(0, successes)
            assertEquals(1, cancellations)
        }
    }
}

package takagi.ru.monica.lansync

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.passkey.PasskeyPortability
import takagi.ru.monica.ui.components.NearbyPasskeyPreflightDialog

class NearbyPasskeyPreflightTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancel_does_not_confirm_and_continue_requires_a_visible_review_at_large_text() {
        val consent = DevicePasskeySyncConsent()
        val issues = (1..12).map { index -> DevicePasskeySyncIssue("fixture-$index", "Fixture $index",
            "example.test", if (index == 1) PasskeyPortability.DEVICE_ONLY else PasskeyPortability.BACKUP_RESTRICTED, skipForCounter = index == 2) }
        var review by mutableStateOf(consent.review(issues, setOf("phone")))
        var cancelled = false
        var continued = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    review?.let { current ->
                        NearbyPasskeyPreflightDialog(current,
                            onCancel = { cancelled = true; consent.clearPending(); review = null },
                            onContinue = { continued = true; consent.confirm(current); review = null })
                    }
                }
            }
        }
        compose.onNodeWithTag("nearby_passkey_preflight").assertIsDisplayed()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithTag("nearby_passkey_skip").assertTextContains(context.getString(takagi.ru.monica.R.string.device_sync_passkey_skip_continue))
        // Optional local visual evidence; functional assertions do not depend on screenshots.
        runCatching {
            val bitmap = compose.onNodeWithTag("nearby_passkey_preflight").captureToImage().asAndroidBitmap()
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            java.io.File(context.filesDir, "nearby-passkey-review.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("nearby_passkey_cancel").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(cancelled); assertFalse(continued)
            review = consent.review(issues, setOf("phone"))
            assertNotNull(review)
        }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Fixture 12"))
        compose.onNodeWithText("Fixture 12").assertIsDisplayed()
        compose.onNodeWithTag("nearby_passkey_skip").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(continued)
            assertNull(consent.review(issues, setOf("phone")))
        }
        compose.onNodeWithTag("nearby_passkey_preflight").assertDoesNotExist()
    }
}

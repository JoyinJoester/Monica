package takagi.ru.monica.ui.components

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import takagi.ru.monica.ui.scale.ProvideMonicaInterfaceScale
import takagi.ru.monica.ui.theme.MonicaTheme

class EditorActionMenuScaleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun awaitMenu() = compose.waitUntil(60_000) {
        runCatching { compose.onAllNodesWithTag("editor_action_menu").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir, "editor-menu-$name.png").outputStream().use {
            compose.onNodeWithTag("editor_action_menu").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun open_menu_scales_with_page_and_respects_font_scale() {
        var percent by mutableIntStateOf(100)
        var fontScale by mutableFloatStateOf(1f)
        var expanded by mutableStateOf(true)
        var generated = 0
        compose.setContent {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density, fontScale)) {
                ProvideMonicaInterfaceScale(percent) {
                    MonicaTheme(darkTheme = true) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopEnd) {
                            Box {
                                IconButton({ expanded = true }, Modifier.testTag("editor-menu-anchor")) {
                                    Icon(Icons.Default.MoreVert, "Open menu")
                                }
                                EditorActionMenu(expanded, { expanded = false }, listOf(
                                    EditorMenuAction("Generator", Icons.Default.AutoAwesome, { generated++ }, tag = "generate"),
                                    EditorMenuAction("Delete password", Icons.Default.DeleteOutline, {},
                                        supporting = "Keep other credentials", destructive = true, tag = "delete"),
                                ))
                            }
                        }
                    }
                }
            }
        }
        awaitMenu()
        val base = compose.onNodeWithTag("editor_action_menu").fetchSemanticsNode().size
        val baseText = compose.onNodeWithText("Generator", useUnmergedTree = true).fetchSemanticsNode().size
        for (value in listOf(80, 120, 100)) {
            compose.runOnIdle { percent = value }
            compose.waitForIdle()
            val actual = compose.onNodeWithTag("editor_action_menu").fetchSemanticsNode().size
            val text = compose.onNodeWithText("Generator", useUnmergedTree = true).fetchSemanticsNode().size
            assertEquals("Menu width must track page scale $value%", base.width * value / 100f, actual.width.toFloat(), 3f)
            assertEquals("Menu height must track page scale $value%", base.height * value / 100f, actual.height.toFloat(), 5f)
            assertEquals("Menu text must track page scale $value%", baseText.height * value / 100f, text.height.toFloat(), 3f)
            capture("$value")
        }
        compose.runOnIdle { fontScale = 1.5f }
        compose.waitForIdle()
        assertTrue("System font size must be retained", compose.onNodeWithText("Generator", useUnmergedTree = true)
            .fetchSemanticsNode().size.height > baseText.height * 1.25f)
        compose.onNodeWithTag("generate").performClick()
        compose.runOnIdle { assertEquals(1, generated); assertFalse(expanded) }
        compose.onNodeWithTag("editor_action_menu").assertDoesNotExist()
    }

    @Test fun large_scaled_menu_scrolls_and_back_dismisses_without_action() {
        var expanded by mutableStateOf(true)
        var clicked = -1
        compose.setContent {
            val system = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(system.density, 1.5f)) {
                ProvideMonicaInterfaceScale(120) {
                    MonicaTheme {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopEnd) {
                            Box {
                                IconButton({ expanded = true }, Modifier.testTag("editor-menu-anchor")) {
                                    Icon(Icons.Default.MoreVert, "Open menu")
                                }
                                EditorActionMenu(expanded, { expanded = false }, (0..19).map { index ->
                                    EditorMenuAction("Action $index", Icons.Default.AutoAwesome, { clicked = index },
                                        supporting = "A longer explanation for this action", tag = "action-$index")
                                })
                            }
                        }
                    }
                }
            }
        }
        awaitMenu()
        compose.onNodeWithTag("action-19").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(19, clicked); assertFalse(expanded) }
        compose.onNodeWithTag("editor-menu-anchor").performClick()
        awaitMenu()
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle { assertFalse(expanded); assertEquals(19, clicked) }
    }
}

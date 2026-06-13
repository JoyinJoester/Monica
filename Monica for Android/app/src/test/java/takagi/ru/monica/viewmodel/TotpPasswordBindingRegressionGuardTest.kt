package takagi.ru.monica.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TotpPasswordBindingRegressionGuardTest {

    @Test
    fun passwordEditorCreatesFirstPersistedBoundTotpForAuthenticatorPageEditing() {
        val addPasswordSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/AddEditPasswordScreen.kt"
        ).readText()
        val totpViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt"
        ).readText()
        val mainActivitySource = projectFile(
            "app/src/main/java/takagi/ru/monica/MainActivity.kt"
        ).readText()
        val screensSource = projectFile(
            "app/src/main/java/takagi/ru/monica/navigation/Screens.kt"
        ).readText()
        val saveTotpSection = addPasswordSource
            .substringAfter("// Save TOTP if authenticatorKey is provided")
            .substringBefore("} else if (currentAuthKey.isEmpty()")
        val savePasswordBoundTotpBody = totpViewModelSource
            .substringAfter("fun savePasswordBoundTotp(")
            .substringBefore("/**\n     * 根据ID获取TOTP项目")
        val addEditTotpRouteBody = mainActivitySource
            .substringAfter("route = Screen.AddEditTotp.route")
            .substringBefore("route = Screen.WalletAdd.route")

        assertTrue(
            "Password editor must save authenticator data through the bound TOTP path.",
            saveTotpSection.contains("totpViewModel.savePasswordBoundTotp(") &&
                saveTotpSection.contains("passwordId = firstPasswordId") &&
                saveTotpSection.contains("totpData = totpData")
        )
        assertTrue(
            "The bound TOTP path must update the selected persisted item first, then fall back to an existing binding.",
            savePasswordBoundTotpBody.contains("repository.getItemsByType(ItemType.TOTP).first()") &&
                savePasswordBoundTotpBody.contains("val activeStoredItems = existingStoredTotps.mapNotNull") &&
                savePasswordBoundTotpBody.contains("data.boundPasswordId == passwordId") &&
                savePasswordBoundTotpBody.contains("val preferredItem = activeStoredItems") &&
                savePasswordBoundTotpBody.contains("preferredTotpId != null && item.id == preferredTotpId") &&
                savePasswordBoundTotpBody.contains("id = preferredItem?.first?.id")
        )
        assertTrue(
            "When no persisted binding exists, saveTotpItemInternal must be allowed to create one so the authenticator page opens a real item instead of an empty virtual entry.",
            savePasswordBoundTotpBody.contains("title = preferredItem?.first?.title ?: title") &&
                savePasswordBoundTotpBody.contains("notes = preferredItem?.first?.notes ?: notes") &&
                savePasswordBoundTotpBody.contains("isFavorite = preferredItem?.first?.isFavorite ?: isFavorite")
        )
        assertFalse(
            "Do not return early to rely only on virtual password.authenticatorKey entries; virtual entries have negative ids and cannot be edited as real TOTP rows.",
            savePasswordBoundTotpBody.contains("No persisted bound TOTP for passwordId=") ||
                savePasswordBoundTotpBody.contains("return@launch\n                }\n                val boundPassword")
        )
        assertTrue(
            "New authenticator navigation must use a non-negative sentinel so negative virtual ids remain editable.",
            screensSource.contains("\"add_edit_totp/0\"") &&
                !screensSource.contains("\"add_edit_totp/-1\"")
        )
        assertTrue(
            "Compact authenticator editing must hydrate negative virtual password-derived TOTP items instead of opening an empty add form.",
            addEditTotpRouteBody.contains("val displayTotpItems by totpViewModel.allTotpItems.collectAsState()") &&
                addEditTotpRouteBody.contains("totpId < 0 -> displayTotpItems.firstOrNull { it.id == totpId }") &&
                addEditTotpRouteBody.contains("totpId = if (totpId > 0) totpId else null")
        )
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}

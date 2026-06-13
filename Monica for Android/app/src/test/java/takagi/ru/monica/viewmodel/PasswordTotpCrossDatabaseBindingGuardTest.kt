package takagi.ru.monica.viewmodel

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordTotpCrossDatabaseBindingGuardTest {

    @Test
    fun passwordEditorAddsAuthenticatorSourceDatabaseToSaveTargets() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/AddEditPasswordScreen.kt"
        ).readText()

        assertTrue(
            "Password editor must remember the selected authenticator storage target.",
            source.contains("selectedExistingTotpStorageTarget")
        )
        assertTrue(
            "Cross-database TOTP binding should compare database scope, not folder/category.",
            source.contains("target.storageScopeKey() == authenticatorTarget.storageScopeKey()")
        )
        assertTrue(
            "Cross-database TOTP binding must add the authenticator source database to password save targets.",
            source.contains("currentTargets + authenticatorTarget")
        )
    }

    @Test
    fun passwordSaveReturnsAllTargetPasswordIdsForTotpBinding() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()

        assertTrue(
            "Saving across targets must expose all saved target password ids, not only the first one.",
            source.contains("onCompleteWithIds")
        )
        assertTrue(
            "Saving across targets must collect the first password id from every target.",
            source.contains("savedTargetFirstIds += createdId")
        )
    }

    @Test
    fun boundTotpDoesNotMoveTheOriginalAuthenticatorAcrossDatabases() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt"
        ).readText()

        assertTrue(
            "Password page must bind TOTP for every saved password replica.",
            source.contains("fun savePasswordBoundTotps(")
        )
        assertTrue(
            "A selected authenticator from another database must not be updated in-place.",
            source.contains("selectedSourceItem") &&
                source.contains("takeIf { it.isInBoundPasswordStorage() }")
        )
        assertTrue(
            "TOTP binding must compare the authenticator target with the bound password target.",
            source.contains("first.toStorageTarget().storageScopeKey() == boundTargetScopeKey")
        )
        assertTrue(
            "Duplicate cleanup must preserve the actual saved TOTP item, including newly copied items.",
            source.contains("keepItemId = savedItemId")
        )
        assertTrue(
            "TOTP save internals must return the saved item id so duplicate cleanup can be exact.",
            source.contains("private suspend fun saveTotpItemInternal") &&
                source.contains("): Long?")
        )
    }

    @Test
    fun authenticatorPickerMirrorsPasswordPickerSourceFiltering() {
        val passwordScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/AddEditPasswordScreen.kt"
        ).readText()
        val passwordPicker = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/PasswordEntryPickerBottomSheet.kt"
        ).readText()

        assertTrue(
            "Password page authenticator picker must expose source filter chips.",
            passwordScreen.contains("PasswordTotpPickerSourceFilter") &&
                passwordScreen.contains("filter_keepass") &&
                passwordScreen.contains("filter_bitwarden") &&
                passwordScreen.contains("MDBX")
        )
        assertTrue(
            "Authenticator picker search field should hide the Material TextField underline.",
            passwordScreen.contains("focusedIndicatorColor = Color.Transparent") &&
                passwordScreen.contains("unfocusedIndicatorColor = Color.Transparent")
        )
        assertTrue(
            "Password binding picker should keep the same underline-free search styling.",
            passwordPicker.contains("focusedIndicatorColor = Color.Transparent") &&
                passwordPicker.contains("unfocusedIndicatorColor = Color.Transparent")
        )
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            candidates += File(dir, relativePath)
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to find project file: $relativePath from ${System.getProperty("user.dir")}")
    }
}

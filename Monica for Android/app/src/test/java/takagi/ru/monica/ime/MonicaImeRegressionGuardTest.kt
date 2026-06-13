package takagi.ru.monica.ime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonicaImeRegressionGuardTest {

    @Test
    fun multiPackageAppBindingsMatchCurrentInputPackage() {
        assertTrue(
            imeEntryMatchesPackage(
                entryPackageName = "com.example.old|com.github.android",
                website = "",
                title = "",
                activePackageName = "com.github.android"
            )
        )
    }

    @Test
    fun androidAppUriPackageBindingsMatchCurrentInputPackage() {
        assertTrue(
            imeEntryMatchesPackage(
                entryPackageName = "androidapp://com.github.android",
                website = "",
                title = "",
                activePackageName = "com.github.android"
            )
        )
    }

    @Test
    fun unrelatedPackageDoesNotMatchWithoutFallbackSignals() {
        assertFalse(
            imeEntryMatchesPackage(
                entryPackageName = "com.example.other",
                website = "https://example.com",
                title = "Example",
                activePackageName = "com.github.android"
            )
        )
    }

    @Test
    fun imePasswordRowsResolveEncryptedUsernameBeforeFiltering() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ime/MonicaInputMethodService.kt"
        ).readText()

        assertTrue(
            "IME must decrypt username before deciding whether a password row is fillable.",
            source.contains("val decryptedUsername = resolveFillableField(username)")
        )
        assertTrue(
            "IME must expose the decrypted username to the keyboard UI.",
            source.contains("username = decryptedUsername.orEmpty()")
        )
        assertFalse(
            "IME must not drop rows by checking the raw stored username, which may be encrypted.",
            source.contains("if (username.isBlank() && decryptedPassword.isNullOrBlank())")
        )
    }

    @Test
    fun imeDatabaseScopesTrackMdbxAsItsOwnSource() {
        val serviceSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ime/MonicaInputMethodService.kt"
        ).readText()
        val uiSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ime/MonicaInputMethodUi.kt"
        ).readText()

        assertTrue(
            "IME must load MDBX database options instead of treating MDBX rows as Monica-local rows.",
            serviceSource.contains("database.localMdbxDatabaseDao().getAllDatabasesSnapshot()")
        )
        assertTrue(
            "IME local scope checks need the MDBX owner id.",
            serviceSource.contains("entry.mdbxDatabaseId")
        )
        assertTrue(
            "IME UI needs an MDBX database scope for keyboard filtering.",
            uiSource.contains("data class Mdbx(val databaseId: Long) : MonicaImeDatabaseScope")
        )
    }

    @Test
    fun imePasswordPanelShowsLoadingBeforeEmptyState() {
        val serviceSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ime/MonicaInputMethodService.kt"
        ).readText()
        val uiSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ime/MonicaInputMethodUi.kt"
        ).readText()

        assertTrue(
            "IME UI state needs an explicit loading flag so an empty list is not treated as no matches while refresh is running.",
            uiSource.contains("val isAutofillLoading: Boolean = false")
        )
        val loadingBranchIndex = uiSource.indexOf("if (showAutofillLoading)")
        val loadingStateCallIndex = uiSource.indexOf("AutofillLoadingState()", loadingBranchIndex)
        val emptyStateCallIndex = uiSource.indexOf("EmptyVaultState(query = uiState.query)", loadingBranchIndex)
        val loadingStateBody = uiSource
            .substringAfter("private fun AutofillLoadingState()")
            .substringBefore("@Composable\nprivate fun EmptyVaultState")
        assertTrue(
            "Password panel must render the same loading indicator as the password list before the empty-state card.",
            loadingBranchIndex >= 0 &&
                loadingStateCallIndex > loadingBranchIndex &&
                emptyStateCallIndex > loadingStateCallIndex &&
                loadingStateBody.contains("PasswordListInitialLoadingIndicator()")
        )
        assertFalse(
            "IME must not bring back its own hand-drawn loading indicator instead of the password-list loading style.",
            uiSource.contains("MonicaImeMorphingLoadingIndicator(") ||
                uiSource.contains("ime_autofill_loading_morph")
        )
        assertTrue(
            "Password panel should keep showing loading while database filter options are still initializing.",
            uiSource.contains("uiState.databaseOptions.isEmpty()")
        )
        assertTrue(
            "IME refresh should set loading while the unlocked password panel is fetching entries.",
            serviceSource.contains("it.copy(isAutofillLoading = true)")
        )
        assertTrue(
            "IME refresh completion must clear loading so a real empty result still shows the empty state.",
            serviceSource.contains("isAutofillLoading = false")
        )
        assertTrue(
            "Cancelling a stale IME refresh is normal and must not be surfaced as an error like 'Q0 was cancelled'.",
            serviceSource.contains("catch (e: CancellationException)") &&
                serviceSource.indexOf("catch (e: CancellationException)") < serviceSource.indexOf("catch (e: Exception)")
        )
    }

    @Test
    fun imeSecondaryVaultListsUseTheSameNavigationBarAsPasswords() {
        val uiSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ime/MonicaInputMethodUi.kt"
        ).readText()
        val authenticatorPane = uiSource
            .substringAfter("private fun AuthenticatorPane(")
            .substringBefore("@Composable\nprivate fun CardWalletPane")
        val cardWalletPane = uiSource
            .substringAfter("private fun CardWalletPane(")
            .substringBefore("@Composable\nprivate fun DatabaseScopeFilterRow")

        assertTrue(
            "Authenticator IME list should keep the same right-side navigation bar behavior as the password list.",
            authenticatorPane.contains("val lazyListState = rememberLazyListState()") &&
                authenticatorPane.contains("buildImeLetterIndex(itemCount = uiState.authenticatorEntries.size)") &&
                authenticatorPane.contains("VelocityScrollBar(")
        )
        assertTrue(
            "Card wallet IME list should keep the same right-side navigation bar behavior as the password list.",
            cardWalletPane.contains("val lazyListState = rememberLazyListState()") &&
                cardWalletPane.contains("buildImeLetterIndex(itemCount = uiState.cardWalletEntries.size)") &&
                cardWalletPane.contains("VelocityScrollBar(")
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

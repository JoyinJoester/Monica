package takagi.ru.monica.autofill_ng

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.data.PasswordEntry

class LocalWebViewPackageMatchingTest {
    @Test fun localFileLoginRecommendsOnlyExplicitAppBindingsWithNativeIndex() {
        val matcher = BitwardenLikeAutofillMatcherNg()
        try {
            val entries = listOf(
                PasswordEntry(id = 1, title = "Binding", website = "", username = "", password = "", appPackageName = "com.cn21.ecloud"),
                PasswordEntry(id = 2, title = "Package URL", website = "com.cn21.ecloud", username = "", password = ""),
                PasswordEntry(id = 3, title = "天翼云盘", website = "", username = "", password = ""),
            ) + (4L..303L).map { id ->
                PasswordEntry(id = id, title = "Other $id", website = "https://unrelated.invalid", username = "", password = "")
            }
            fun matches(pkg: String, scheme: String?) = matcher.match(entries, pkg, null, "天翼云盘",
                BitwardenLikeAutofillMatcherNg.Config(
                    allowPackageMatch = AutofillRequestContextPolicy.allowPackageMatching(pkg, null, true),
                    allowExactPackageMatch = AutofillRequestContextPolicy.allowExactPackageMatching(pkg, null, true, scheme)))
            assertEquals(setOf(1L, 2L), matches("com.cn21.ecloud", "file").map { it.id }.toSet())
            assertTrue(matches("com.cn21.ecloud", "https").isEmpty())
            assertTrue(matches("com.android.chrome", "file").isEmpty())
            assertTrue("The packaged native index must participate", matcher.nativeQueryCount > 0)
        } finally { matcher.clear() }
    }
}

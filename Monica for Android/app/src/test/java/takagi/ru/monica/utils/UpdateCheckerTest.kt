package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun compareVersionTags_detectsNewerRelease() {
        assertTrue(UpdateChecker.compareVersionTags("v1.0.289", "1.0.289") > 0)
    }

    @Test
    fun compareVersionTags_treatsMatchingPrefixAsSameVersion() {
        assertEquals(0, UpdateChecker.compareVersionTags("v1.0.289", "1.0.289-preview"))
    }

    @Test
    fun compareVersionTags_ignoresReleaseLetterSuffix() {
        assertEquals(0, UpdateChecker.compareVersionTags("V1.0.289c", "1.0.289"))
        assertEquals(0, UpdateChecker.compareVersionTags("V1.0.289c", "V1.0.289b"))
    }

    @Test
    fun compareVersionTags_detectsOlderRelease() {
        assertTrue(UpdateChecker.compareVersionTags("1.0.287", "1.0.289") < 0)
    }
}

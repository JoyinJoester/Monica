package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun compareVersionTags_detectsNewerRelease() {
        assertTrue(UpdateChecker.compareVersionTags("v1.0.291", "1.0.290") > 0)
    }

    @Test
    fun compareVersionTags_treatsMatchingPrefixAsSameVersion() {
        assertEquals(0, UpdateChecker.compareVersionTags("v1.0.290", "1.0.290-preview"))
    }

    @Test
    fun compareVersionTags_ignoresReleaseLetterSuffix() {
        assertEquals(0, UpdateChecker.compareVersionTags("V1.0.290c", "1.0.290"))
        assertEquals(0, UpdateChecker.compareVersionTags("V1.0.290c", "V1.0.290b"))
    }

    @Test
    fun compareVersionTags_detectsOlderRelease() {
        assertTrue(UpdateChecker.compareVersionTags("1.0.287", "1.0.290") < 0)
    }
}

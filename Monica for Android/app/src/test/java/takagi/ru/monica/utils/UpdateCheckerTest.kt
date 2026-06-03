package takagi.ru.monica.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun compareVersionTags_detectsNewerRelease() {
        assertTrue(UpdateChecker.compareVersionTags("v1.0.289", "1.0.288") > 0)
    }

    @Test
    fun compareVersionTags_treatsMatchingPrefixAsSameVersion() {
        assertEquals(0, UpdateChecker.compareVersionTags("v1.0.288", "1.0.288-preview"))
    }

    @Test
    fun compareVersionTags_detectsOlderRelease() {
        assertTrue(UpdateChecker.compareVersionTags("1.0.287", "1.0.288") < 0)
    }
}

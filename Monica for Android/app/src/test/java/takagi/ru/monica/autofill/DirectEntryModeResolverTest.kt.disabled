package takagi.ru.monica.autofill

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectEntryModeResolverTest {

    private fun createResolver() = DirectEntryModeResolver(
        cycleTtlMs = 10 * 60 * 1000L,
        maxSize = 128
    )

    @Test
    fun `first second third focus follows trigger composite last-filled`() {
        val resolver = createResolver()
        val cycleKey = "web:github.com|sig"

        val first = resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 1,
            contextCount = 1,
            progressToken = "1|1|PASSWORD:id-1",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 1000L
        )
        val second = resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 2,
            contextCount = 1,
            progressToken = "2|1|USERNAME:id-2",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 2000L
        )
        val third = resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 3,
            contextCount = 1,
            progressToken = "3|1|PASSWORD:id-1",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 3000L
        )

        assertEquals(DirectEntryModeResolver.Mode.TRIGGER_ONLY, first.mode)
        assertEquals(0, first.stage)
        assertEquals(DirectEntryModeResolver.Mode.TRIGGER_AND_LAST_FILLED, second.mode)
        assertEquals(1, second.stage)
        assertEquals(DirectEntryModeResolver.Mode.LAST_FILLED_ONLY, third.mode)
        assertEquals(2, third.stage)
    }

    @Test
    fun `page refresh resets to first focus behavior`() {
        val resolver = createResolver()
        val cycleKey = "web:github.com|sig"

        resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 1,
            contextCount = 1,
            progressToken = "1|1|PASSWORD:id-1",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 1000L
        )
        resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 2,
            contextCount = 1,
            progressToken = "2|1|USERNAME:id-2",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 2000L
        )

        val afterRefresh = resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 1,
            contextCount = 1,
            progressToken = "1|1|PASSWORD:id-new",
            sessionMarker = "session-b",
            fieldSignatureKey = "sig",
            now = 3000L
        )

        assertEquals(DirectEntryModeResolver.Mode.TRIGGER_ONLY, afterRefresh.mode)
        assertEquals(0, afterRefresh.stage)
        assertEquals("request_ordinal_rollback", afterRefresh.reason)
    }

    @Test
    fun `duplicate token does not advance stage`() {
        val resolver = createResolver()
        val cycleKey = "web:github.com|sig"

        resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 1,
            contextCount = 1,
            progressToken = "1|1|PASSWORD:id-1",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 1000L
        )

        val duplicate = resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 1,
            contextCount = 1,
            progressToken = "1|1|PASSWORD:id-1",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 1500L
        )

        assertEquals(DirectEntryModeResolver.Mode.TRIGGER_ONLY, duplicate.mode)
        assertEquals(0, duplicate.stage)
        assertEquals("duplicate_request", duplicate.reason)
    }

    @Test
    fun `field signature changed resets stage`() {
        val resolver = createResolver()
        val cycleKey = "web:github.com|sig"

        resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 1,
            contextCount = 1,
            progressToken = "1|1|PASSWORD:id-1",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 1000L
        )
        resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 2,
            contextCount = 1,
            progressToken = "2|1|USERNAME:id-2",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 2000L
        )

        val changed = resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 3,
            contextCount = 1,
            progressToken = "3|1|PASSWORD:id-3",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig-v2",
            now = 3000L
        )

        assertEquals(DirectEntryModeResolver.Mode.TRIGGER_ONLY, changed.mode)
        assertEquals(0, changed.stage)
        assertEquals("field_signature_changed", changed.reason)
    }

    @Test
    fun `no last-filled always returns trigger only`() {
        val resolver = createResolver()
        val cycleKey = "web:github.com|sig"

        val first = resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = false,
            requestOrdinal = 1,
            contextCount = 1,
            progressToken = "1|1|PASSWORD:id-1",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 1000L
        )
        val second = resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = false,
            requestOrdinal = 2,
            contextCount = 1,
            progressToken = "2|1|PASSWORD:id-1",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 2000L
        )

        assertEquals(DirectEntryModeResolver.Mode.TRIGGER_ONLY, first.mode)
        assertEquals(DirectEntryModeResolver.Mode.TRIGGER_ONLY, second.mode)
        assertEquals("no_last_filled", second.reason)
    }

    @Test
    fun `ttl expiration resets to first stage`() {
        val resolver = DirectEntryModeResolver(
            cycleTtlMs = 1000L,
            maxSize = 16
        )
        val cycleKey = "web:github.com|sig"

        resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 1,
            contextCount = 1,
            progressToken = "1|1|PASSWORD:id-1",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 1000L
        )
        resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 2,
            contextCount = 1,
            progressToken = "2|1|USERNAME:id-2",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 1500L
        )

        val expired = resolver.resolve(
            cycleKey = cycleKey,
            hasLastFilled = true,
            requestOrdinal = 3,
            contextCount = 1,
            progressToken = "3|1|PASSWORD:id-3",
            sessionMarker = "session-a",
            fieldSignatureKey = "sig",
            now = 3001L
        )

        assertEquals(DirectEntryModeResolver.Mode.TRIGGER_ONLY, expired.mode)
        assertEquals(0, expired.stage)
        assertEquals("new_cycle", expired.reason)
    }
}
